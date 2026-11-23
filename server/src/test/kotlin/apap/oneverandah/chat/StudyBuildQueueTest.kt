package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
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
            assertEquals(2, store.findById("study-1")?.build?.attempts?.get(task.element.key))
            assertTrue(store.findById("study-1")?.build?.failed.isNullOrEmpty())
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
                while (store.findById("study-1")?.build?.failed.isNullOrEmpty()) delay(5L)
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