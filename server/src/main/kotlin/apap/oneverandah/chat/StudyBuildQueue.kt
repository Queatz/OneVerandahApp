package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

class StudyBuildQueue(
    private val store: StudySessionStore,
    private val executor: StudyBuildTaskExecutor,
    private val scope: CoroutineScope,
    private val textWorkers: Int = 4,
    private val imageWorkers: Int = 3,
    maxAttempts: Int = 3,
    private val retryBackoffMillis: List<Long> = DEFAULT_RETRY_BACKOFF_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onTaskSettled: suspend (String) -> Unit = {},
) {
    private val log = LoggerFactory.getLogger(StudyBuildQueue::class.java)
    private val attemptLimit = maxAttempts.coerceAtLeast(1)
    private val textHigh =
        kotlinx.coroutines.channels.Channel<StudyBuildTask>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val textLow =
        kotlinx.coroutines.channels.Channel<StudyBuildTask>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val imageHigh =
        kotlinx.coroutines.channels.Channel<StudyBuildTask>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val imageLow =
        kotlinx.coroutines.channels.Channel<StudyBuildTask>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val queued = ConcurrentHashMap<String, StudyBuildTask>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        repeat(textWorkers.coerceAtLeast(1)) { worker ->
            scope.launch { workerLoop(StudyBuildTaskPool.TEXT, worker) }
        }
        repeat(imageWorkers.coerceAtLeast(1)) { worker ->
            scope.launch { workerLoop(StudyBuildTaskPool.IMAGE, worker) }
        }
    }

    suspend fun enqueue(
        tasks: List<StudyBuildTask>,
        priorityOverride: StudyBuildTaskPriority? = null,
    ) {
        tasks.forEach { task ->
            val queuedTask = if (priorityOverride == null) task else task.copy(priorityOverride = priorityOverride)
            if (queued.putIfAbsent(queuedTask.key, queuedTask) == null) send(queuedTask)
        }
    }

    fun activeElements(sessionId: String): List<StudyBuildElement> = queued.values
        .asSequence()
        .filter { it.sessionId == sessionId }
        .map { it.element }
        .distinctBy { it.key }
        .toList()

    fun hasActive(sessionId: String): Boolean = queued.values.any { it.sessionId == sessionId }

    private suspend fun workerLoop(pool: StudyBuildTaskPool, worker: Int) {
        log.info("Study build {} worker #{} started", pool.name.lowercase(), worker)
        while (scope.isActive) {
            val task = receive(pool)
            process(task)
        }
    }

    private suspend fun receive(pool: StudyBuildTaskPool): StudyBuildTask = select {
        when (pool) {
            StudyBuildTaskPool.TEXT -> {
                textHigh.onReceive { it }
                textLow.onReceive { it }
            }

            StudyBuildTaskPool.IMAGE -> {
                imageHigh.onReceive { it }
                imageLow.onReceive { it }
            }
        }
    }

    private suspend fun process(task: StudyBuildTask) {
        if (queued[task.key] != task || !running.add(task.key)) return
        var attempt = 0
        if (store.findById(task.sessionId) == null) {
            finish(task)
            return
        }
        val attempted = store.update(task.sessionId) { current ->
            val build = current.build ?: return@update null
            attempt = (build.attempts[task.element.key] ?: 0) + 1
            val status = if (task.element.kind == StudyBuildElementKind.PREPARE) {
                StudyBuildStatus.PREPARING
            } else {
                StudyBuildStatus.BUILDING
            }
            current.copy(
                build = build.copy(
                    status = status,
                    attempts = build.attempts + (task.element.key to attempt),
                    updatedAt = clock(),
                ),
            )
        }
        if (attempted == null) {
            running.remove(task.key)
            finish(task)
            return
        }
        publishBuild(task.sessionId)

        var failure: Exception? = null
        try {
            executor.execute(task)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error
        } finally {
            running.remove(task.key)
        }

        if (failure == null) {
            finish(task)
        } else {
            handleFailure(task, attempt, failure)
        }
        publishBuild(task.sessionId)
        if (scope.isActive) {
            runCatching { onTaskSettled(task.sessionId) }
                .onFailure { error ->
                    log.error(
                        "Study build planning failed for {}: {}",
                        task.sessionId,
                        error.message
                    )
                }
        }
    }

    private suspend fun handleFailure(task: StudyBuildTask, attempt: Int, error: Exception) {
        if (attempt < attemptLimit) {
            val delayMillis = retryBackoffMillis.getOrElse(attempt - 1) {
                retryBackoffMillis.lastOrNull() ?: 0L
            }
            log.warn(
                "Study build element {} for {} failed on attempt {}; retrying in {}ms: {}",
                task.element.key,
                task.sessionId,
                attempt,
                delayMillis,
                error.message,
            )
            scope.launch {
                delay(delayMillis.coerceAtLeast(0L))
                if (scope.isActive && queued[task.key] == task) send(task)
            }
            return
        }

        store.update(task.sessionId) { current ->
            val build = current.build ?: return@update null
            val failed = (build.failed + task.element).distinctBy { it.key }
            current.copy(
                build = build.copy(
                    failed = failed,
                    updatedAt = clock(),
                ),
            )
        }
        log.error(
            "Study build element {} for {} failed after {} attempts: {}",
            task.element.key,
            task.sessionId,
            attempt,
            error.message,
        )
        finish(task)
    }

    private suspend fun publishBuild(sessionId: String) {
        val record = store.findById(sessionId) ?: return
        val state = record.build ?: return
        store.publish(
            sessionId,
            StudyBuildEvent.Build(state.copy(inFlight = activeElements(sessionId))),
        )
    }

    private fun finish(task: StudyBuildTask) {
        queued.remove(task.key, task)
    }

    private suspend fun send(task: StudyBuildTask) {
        when (task.pool to task.priority) {
            StudyBuildTaskPool.TEXT to StudyBuildTaskPriority.HIGH -> textHigh.send(task)
            StudyBuildTaskPool.TEXT to StudyBuildTaskPriority.LOW -> textLow.send(task)
            StudyBuildTaskPool.IMAGE to StudyBuildTaskPriority.HIGH -> imageHigh.send(task)
            StudyBuildTaskPool.IMAGE to StudyBuildTaskPriority.LOW -> imageLow.send(task)
        }
    }

    companion object {
        val DEFAULT_RETRY_BACKOFF_MILLIS = listOf(2_000L, 10_000L, 30_000L)
    }
}
