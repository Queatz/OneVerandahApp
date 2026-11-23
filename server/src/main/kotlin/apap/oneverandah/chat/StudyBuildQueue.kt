package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.logging.SafeFailureDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

internal object StudyBuildFailureReason {
    const val MAX_DISPLAY_LENGTH = 240

    fun display(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(6).toList()
        val messages = chain.mapNotNull { it.message?.takeIf(String::isNotBlank) }
        val combined = messages.joinToString(" ")
        val lower = combined.lowercase()
        if (
            chain.any { it.javaClass.simpleName in setOf("SocketException", "SocketTimeoutException", "EOFException", "ClosedChannelException") } ||
            listOf("prematurely closed", "connection reset", "broken pipe", "connection abort").any(lower::contains)
        ) {
            return "The provider connection was interrupted before the request completed."
        }
        if (chain.any { it.javaClass.simpleName.contains("Timeout", ignoreCase = true) }) {
            return "The provider did not respond before the request timed out."
        }
        if (chain.any { it.javaClass.simpleName == "QwenException" }) {
            val status = Regex("\\((4\\d\\d|5\\d\\d)(?:\\s+[^)]*)?\\)").find(combined)?.groupValues?.get(1)
            val qwenMessage = messages.firstOrNull().orEmpty().lowercase()
            return when {
                status != null -> "The image provider rejected the request (HTTP $status)."
                "cannot load reference image" in qwenMessage -> "The image provider could not load its reference image."
                "upload policy" in qwenMessage || "temp upload failed" in qwenMessage ->
                    "The temporary image upload to the provider failed."
                "returned no image" in qwenMessage -> "The image provider returned no image for this task."
                "returned no task_id" in qwenMessage -> "The image provider did not return a task identifier."
                "did not complete within timeout" in qwenMessage -> "The image provider task exceeded its time limit."
                "task " in qwenMessage && " failed" in qwenMessage ->
                    "The image provider reported that image generation failed."
                "returned no output" in qwenMessage -> "The image provider response did not contain output data."
                else -> "The image provider rejected or could not complete the request."
            }
        }
        if (chain.any { it.javaClass.simpleName.contains("Serialization") || it.javaClass.simpleName.contains("JsonDecoding") }) {
            return "The provider returned a response that could not be parsed."
        }

        val safeMessage = combined
            .replace(Regex("(?i)data:[^\\s,]+;base64,[^\\s]+"), "[image data]")
            .replace(Regex("(?i)(?:https?|oss)://[^\\s]+"), "[URL]")
            .replace(Regex("(?i)(authorization|api[_-]?key|token|secret|signature)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")
            .replace(Regex("(?s)\\s*:\\s*(?:\\{|\\[|<html\\b).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_DISPLAY_LENGTH)
        return if (safeMessage.isBlank()) {
            "${error.javaClass.simpleName.ifBlank { "BuildException"}}: no safe error detail was available."
        } else {
            "${error.javaClass.simpleName.ifBlank { "BuildException"}}: $safeMessage"
        }.take(MAX_DISPLAY_LENGTH)
    }
}

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
        val attemptStartedAt = System.nanoTime()
        try {
            withContext(CoroutineName("study-build session=${task.sessionId} element=${task.element.key} attempt=$attempt")) {
                executor.execute(task)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error
        } finally {
            running.remove(task.key)
        }
        val elapsedMs = (System.nanoTime() - attemptStartedAt) / 1_000_000

        if (failure == null) {
            finish(task)
        } else {
            handleFailure(task, attempt, elapsedMs, failure)
        }
        publishBuild(task.sessionId)
        if (scope.isActive) {
            runCatching { onTaskSettled(task.sessionId) }
                .onFailure { error ->
                    log.error(
                        "Study build planning failed for {}: diagnostics={}",
                        task.sessionId,
                        SafeFailureDiagnostics.describe(error),
                    )
                }
        }
    }

    private suspend fun handleFailure(task: StudyBuildTask, attempt: Int, elapsedMs: Long, error: Exception) {
        if (attempt < attemptLimit) {
            val delayMillis = retryBackoffMillis.getOrElse(attempt - 1) {
                retryBackoffMillis.lastOrNull() ?: 0L
            }
            log.warn(
                "Study build element {} ({}) for {} failed on attempt {}/{}; elapsedMs={}; retrying in {}ms; failure={}; diagnostics={}",
                task.element.key,
                task.element.kind,
                task.sessionId,
                attempt,
                attemptLimit,
                elapsedMs,
                delayMillis,
                StudyBuildFailureReason.display(error),
                SafeFailureDiagnostics.describe(error),
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
                    failureReasons = build.failureReasons +
                            (task.element.key to StudyBuildFailureReason.display(error)),
                    updatedAt = clock(),
                ),
            )
        }
        log.error(
            "Study build element {} ({}) for {} failed after {}/{} attempts; elapsedMs={}; failure={}; diagnostics={}",
            task.element.key,
            task.element.kind,
            task.sessionId,
            attempt,
            attemptLimit,
            elapsedMs,
            StudyBuildFailureReason.display(error),
            SafeFailureDiagnostics.describe(error),
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
