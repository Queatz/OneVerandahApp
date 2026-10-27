package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class StudySessionStore(
    private val repository: StudySessionsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
    private val writeCoalesceMillis: Long = WRITE_COALESCE_MILLIS,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val records = ConcurrentHashMap<String, SavedStudySessionRecord>()
    private val flushJobs = ConcurrentHashMap<String, Job>()
    private val dirtyIds = ConcurrentHashMap.newKeySet<String>()
    private val eventBuses = ConcurrentHashMap<String, MutableSharedFlow<StudyBuildEvent>>()

    suspend fun create(record: SavedStudySessionRecord): SavedStudySessionRecord =
        lockFor(record.id).withLock {
            flushJobs.remove(record.id)?.cancel()
            repository.upsert(record).also {
                records[record.id] = it
                dirtyIds.remove(record.id)
            }
        }

    suspend fun findById(id: String): SavedStudySessionRecord? = lockFor(id).withLock {
        val stored = repository.findById(id)
        if (stored == null) {
            records.remove(id)
            dirtyIds.remove(id)
            flushJobs.remove(id)?.cancel()
            null
        } else {
            records[id] ?: stored.also { records[id] = it }
        }
    }

    suspend fun update(
        id: String,
        reason: StudyBuildEvent? = null,
        immediate: Boolean = false,
        transform: (SavedStudySessionRecord) -> SavedStudySessionRecord?,
    ): SavedStudySessionRecord? = lockFor(id).withLock {
        val stored = repository.findById(id)
        if (stored == null) {
            records.remove(id)
            dirtyIds.remove(id)
            flushJobs.remove(id)?.cancel()
            return@withLock null
        }
        val current = records[id] ?: stored
        val updated = transform(current)?.copy(updatedAt = clock()) ?: return@withLock null
        records[id] = updated
        dirtyIds.add(id)
        reason?.let { eventBus(id).tryEmit(it) }

        val terminal = updated.build?.status in setOf(StudyBuildStatus.COMPLETE, StudyBuildStatus.PARTIAL)
        if (immediate || terminal) {
            flushLocked(id, updated)
        } else {
            scheduleFlush(id)
            updated
        }
    }

    suspend fun delete(id: String): Boolean = lockFor(id).withLock {
        flushJobs.remove(id)?.cancel()
        records.remove(id)
        dirtyIds.remove(id)
        repository.delete(id)
    }

    fun events(id: String): Flow<StudyBuildEvent> = eventBus(id).asSharedFlow()

    fun publish(id: String, event: StudyBuildEvent) {
        eventBus(id).tryEmit(event)
    }

    private suspend fun flushLocked(id: String, record: SavedStudySessionRecord): SavedStudySessionRecord? {
        flushJobs.remove(id)?.cancel()
        if (repository.findById(id) == null) {
            records.remove(id)
            dirtyIds.remove(id)
            return null
        }
        return repository.upsert(record).also {
            records[id] = it
            dirtyIds.remove(id)
        }
    }

    private fun scheduleFlush(id: String) {
        if (flushJobs[id]?.isActive == true) return
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                delay(writeCoalesceMillis)
                lockFor(id).withLock {
                    val record = records[id] ?: run {
                        dirtyIds.remove(id)
                        return@withLock
                    }
                    if (repository.findById(id) == null) {
                        records.remove(id)
                        dirtyIds.remove(id)
                        return@withLock
                    }
                    repository.upsert(record)
                    dirtyIds.remove(id)
                }
            } finally {
                flushJobs.remove(id, job)
                if (id in dirtyIds) scheduleFlush(id)
            }
        }
        flushJobs[id] = job
        job.start()
    }

    private fun lockFor(id: String): Mutex = locks.computeIfAbsent(id) { Mutex() }

    private fun eventBus(id: String): MutableSharedFlow<StudyBuildEvent> =
        eventBuses.computeIfAbsent(id) { MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY) }

    companion object {
        const val WRITE_COALESCE_MILLIS = 750L
        private const val EVENT_BUFFER_CAPACITY = 64
    }
}