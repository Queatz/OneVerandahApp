package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudySessionResponse
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
        val queue = StudyBuildQueue(
            store = store,
            executor = StudyBuildTaskExecutor {
                if (calls.incrementAndGet() == 1) error("temporary")
                succeeded.complete(Unit)
            },
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            maxAttempts = 3,
            retryBackoffMillis = listOf(5L, 5L, 5L),
        )
        val task = StudyBuildTask("study-1", StudyBuildElement(StudyBuildElementKind.NAME))

        try {
            queue.start()
            queue.enqueue(listOf(task))
            withTimeout(2_000L) { succeeded.await() }
            assertEquals(2, store.findById("study-1")?.build?.attempts?.get(task.element.key))
            assertTrue(store.findById("study-1")?.build?.failed.isNullOrEmpty())
        } finally {
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
            val build = store.findById("study-1")?.build
            assertEquals(2, calls.get())
            assertTrue(build?.failed?.any { it.key == task.element.key } == true)
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