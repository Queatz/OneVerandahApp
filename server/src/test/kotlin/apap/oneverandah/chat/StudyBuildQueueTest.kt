package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildJournalOutcome
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudySessionResponse
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class StudyBuildQueueTest {
    @Test
    fun `queue prefers high priority work and deduplicates queued elements`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(InMemoryStudySessionsRepository(), scope = scope)
        store.create(record())
        val completed = CompletableDeferred<List<StudyBuildElementKind>>()
        val calls = mutableListOf<StudyBuildElementKind>()
        val executor = StudyBuildTaskExecutor { task ->
            synchronized(calls) {
                calls += task.element.kind
                if (calls.size == 2) completed.complete(calls.toList())
            }
        }
        val queue = StudyBuildQueue(
            store = store,
            executor = executor,
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
        )
        val lowPriority = StudyBuildTask(
            "study-1",
            StudyBuildElement(StudyBuildElementKind.WORD_IMAGE, sentenceIndex = 0, tokenIndex = 0),
        )
        val highPriority = StudyBuildTask(
            "study-1",
            StudyBuildElement(StudyBuildElementKind.PORTRAIT, characterId = "main"),
        )

        try {
            queue.enqueue(listOf(lowPriority, highPriority, lowPriority))
            queue.start()

            val order = withTimeout(2_000L) { completed.await() }
            assertEquals(listOf(StudyBuildElementKind.PORTRAIT, StudyBuildElementKind.WORD_IMAGE), order)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `dropping idle work preserves running tasks and allows a fresh enqueue`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(InMemoryStudySessionsRepository(), scope = scope)
        store.create(record())
        val runningTask = StudyBuildTask(
            "study-1",
            StudyBuildElement(StudyBuildElementKind.TALK_EXPRESSION, expressionId = "main-neutral"),
        )
        val idleTask = StudyBuildTask(
            "study-1",
            StudyBuildElement(StudyBuildElementKind.TALK_SCENE_IMAGE, sceneIndex = 0),
        )
        val runningStarted = CompletableDeferred<Unit>()
        val releaseRunning = CompletableDeferred<Unit>()
        val bothCompleted = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val queue = StudyBuildQueue(
            store = store,
            executor = StudyBuildTaskExecutor { task ->
                if (task.element.key == runningTask.element.key) {
                    runningStarted.complete(Unit)
                    releaseRunning.await()
                }
                if (calls.incrementAndGet() == 2) bothCompleted.complete(Unit)
            },
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
        )

        try {
            queue.start()
            queue.enqueue(listOf(runningTask))
            withTimeout(2_000L) { runningStarted.await() }
            queue.enqueue(listOf(idleTask))

            assertEquals(
                setOf(runningTask.element.key),
                queue.dropIdle("study-1", setOf(runningTask.element.key, idleTask.element.key)),
            )
            assertEquals(listOf(runningTask.element), queue.activeElements("study-1"))

            queue.enqueue(listOf(idleTask.copy()))
            releaseRunning.complete(Unit)
            withTimeout(2_000L) { bothCompleted.await() }
            assertEquals(2, calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `transient task failure retries and succeeds without marking failed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(InMemoryStudySessionsRepository(), scope = scope)
        store.create(record())
        val calls = AtomicInteger()
        val succeeded = CompletableDeferred<Unit>()
        val logger = LoggerFactory.getLogger(StudyBuildQueue::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        val queue = StudyBuildQueue(
            store = store,
            executor = StudyBuildTaskExecutor {
                if (calls.incrementAndGet() == 1) {
                    throw java.io.EOFException(
                        "Failed to parse response: the server prematurely closed connection https://provider.example.test/image.png?signature=do-not-log",
                    )
                }
                succeeded.complete(Unit)
            },
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            maxAttempts = 3,
            retryBackoffMillis = listOf(5L, 5L, 5L),
        )
        val task = StudyBuildTask(
            "study-1",
            StudyBuildElement(StudyBuildElementKind.TALK_EXPRESSION, expressionId = "main-neutral"),
        )

        try {
            queue.start()
            queue.enqueue(listOf(task))
            withTimeout(2_000L) { succeeded.await() }
            withTimeout(2_000L) {
                while (store.findById("study-1")?.build?.journal?.lastOrNull()?.outcome != StudyBuildJournalOutcome.SUCCEEDED) {
                    delay(5L)
                }
            }
            assertEquals(2, store.findById("study-1")?.build?.attempts?.get(task.element.key))
            assertTrue(store.findById("study-1")?.build?.failed.isNullOrEmpty())
            assertEquals(
                listOf(
                    StudyBuildJournalOutcome.STARTED,
                    StudyBuildJournalOutcome.RETRYING,
                    StudyBuildJournalOutcome.STARTED,
                    StudyBuildJournalOutcome.SUCCEEDED,
                ),
                store.findById("study-1")?.build?.journal?.map { it.outcome },
            )
            assertTrue(store.findById("study-1")?.build?.journal?.last()?.elapsedMs != null)
            val retryLog = appender.list.first { it.formattedMessage.contains("retrying in") }.formattedMessage
            assertTrue(retryLog.contains("TALK_EXPRESSION:main-neutral"), retryLog)
            assertTrue(retryLog.contains("attempt 1/3"), retryLog)
            assertTrue(retryLog.contains("elapsedMs="), retryLog)
            assertTrue(retryLog.contains("EOFException"), retryLog)
            assertTrue(retryLog.contains("[URL]"), retryLog)
            assertTrue(!retryLog.contains("do-not-log"), retryLog)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            scope.cancel()
        }
    }

    @Test
    fun `permanent task failure is marked after configured attempts`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(InMemoryStudySessionsRepository(), scope = scope)
        store.create(record())
        val calls = AtomicInteger()
        val task = StudyBuildTask("study-1", StudyBuildElement(StudyBuildElementKind.NAME))
        val logger = LoggerFactory.getLogger(StudyBuildQueue::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        val queue = StudyBuildQueue(
            store = store,
            executor = StudyBuildTaskExecutor {
                calls.incrementAndGet()
                error("permanent")
            },
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            maxAttempts = 2,
            retryBackoffMillis = listOf(5L, 5L),
        )

        try {
            queue.start()
            queue.enqueue(listOf(task))
            withTimeout(2_000L) {
                while (
                    store.findById("study-1")?.build?.failed.isNullOrEmpty() ||
                    appender.list.none { it.formattedMessage.contains("failed after") }
                ) delay(5L)
            }
            val build = requireNotNull(store.findById("study-1")?.build)
            assertEquals(2, calls.get())
            assertTrue(build.failed.any { it.key == task.element.key })
            val failureLog = appender.list.first { it.formattedMessage.contains("failed after") }.formattedMessage
            assertTrue(failureLog.contains("attempts; elapsedMs=") && failureLog.contains("diagnostics="), failureLog)
            val storedReason = requireNotNull(build.failureReasons[task.element.key])
            assertTrue(failureLog.contains(storedReason), failureLog)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            scope.cancel()
        }
    }

    @Test
    fun `timed out task is failed and does not kill its worker`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(InMemoryStudySessionsRepository(), scope = scope)
        store.create(record())
        val nextTaskCompleted = CompletableDeferred<Unit>()
        val queue = StudyBuildQueue(
            store = store,
            executor = StudyBuildTaskExecutor { task ->
                if (task.element.kind == StudyBuildElementKind.NAME) {
                    CompletableDeferred<Unit>().await()
                } else {
                    nextTaskCompleted.complete(Unit)
                }
            },
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            maxAttempts = 1,
            retryBackoffMillis = listOf(1L),
            taskTimeoutMillis = 25L,
        )
        val timedOutTask = StudyBuildTask("study-1", StudyBuildElement(StudyBuildElementKind.NAME))
        val followingTask = StudyBuildTask("study-1", StudyBuildElement(StudyBuildElementKind.SKELETON))

        try {
            queue.start()
            queue.enqueue(listOf(timedOutTask, followingTask))

            withTimeout(2_000L) { nextTaskCompleted.await() }

            val build = requireNotNull(store.findById("study-1")?.build)
            assertTrue(build.failed.any { it.key == timedOutTask.element.key })
            assertEquals(1, build.attempts[timedOutTask.element.key])
        } finally {
            scope.cancel()
        }
    }

    private fun record() = SavedStudySessionRecord(
        id = "study-1",
        deviceTokenHash = "device-hash",
        languageTag = "en",
        text = "Hello.",
        textLanguageTag = "en",
        session = StudySessionResponse(text = "Hello.", textLanguageTag = "en", sentences = emptyList()),
        createdAt = 1L,
        updatedAt = 1L,
        build = StudyBuildState(status = StudyBuildStatus.BUILDING),
    )
}