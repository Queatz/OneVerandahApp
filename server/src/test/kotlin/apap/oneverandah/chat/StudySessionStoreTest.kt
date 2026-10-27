package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudySessionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudySessionStoreTest {
    @Test
    fun `updates for one session coalesce and publish build events`() = runBlocking {
        val repository = CountingStudySessionsRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = StudySessionStore(repository, scope = scope, writeCoalesceMillis = 40L)
        try {
            store.create(record())
            val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
                store.events("study-1").first()
            }

            store.update("study-1", reason = StudyBuildEvent.Renamed("Updated")) {
                it.copy(name = "Updated")
            }
            store.update("study-1") {
                it.copy(session = it.session.copy(practiceSentences = emptyList()))
            }

            assertEquals(StudyBuildEvent.Renamed("Updated"), nextEvent.await())
            assertEquals(1, repository.upsertCount)
            delay(100L)
            assertEquals(2, repository.upsertCount)
            assertEquals("Updated", repository.findById("study-1")?.name)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `updates drop when the record was deleted outside the store`() = runBlocking {
        val repository = CountingStudySessionsRepository()
        val store = StudySessionStore(repository)
        store.create(record())
        repository.delete("study-1")

        val update = store.update("study-1") { it.copy(name = "Not recreated") }

        assertNull(update)
        assertNull(repository.findById("study-1"))
    }

    private fun record() = SavedStudySessionRecord(
        id = "study-1",
        deviceTokenHash = "device-hash",
        languageTag = "en",
        text = "Hello.",
        textLanguageTag = "en",
        session = StudySessionResponse(text = "Hello.", textLanguageTag = "en", sentences = emptyList()),
        createdAt = 10L,
        updatedAt = 10L,
    )

    private class CountingStudySessionsRepository(
        private val backing: InMemoryStudySessionsRepository = InMemoryStudySessionsRepository(),
    ) : StudySessionsRepository by backing {
        var upsertCount = 0
            private set

        override suspend fun upsert(record: SavedStudySessionRecord): SavedStudySessionRecord {
            upsertCount++
            return backing.upsert(record)
        }
    }
}