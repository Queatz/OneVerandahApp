package apap.oneverandah.chat

import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionsResponse
import apap.oneverandah.api.StudySessionResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SavedStudySessionRecord(
    val id: String,
    val deviceTokenHash: String,
    val sourceMessageId: String? = null,
    val languageTag: String,
    val text: String,
    val textLanguageTag: String,
    val session: StudySessionResponse,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toApi() = SavedStudySession(
        id = id,
        sourceMessageId = sourceMessageId,
        languageTag = languageTag,
        text = text,
        textLanguageTag = textLanguageTag,
        session = session,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

interface StudySessionsRepository {
    suspend fun listByDevice(deviceTokenHash: String): List<SavedStudySessionRecord>
    suspend fun findByDeviceAndSource(deviceTokenHash: String, sourceMessageId: String): SavedStudySessionRecord?
    suspend fun findById(id: String): SavedStudySessionRecord?
    suspend fun upsert(record: SavedStudySessionRecord): SavedStudySessionRecord
    suspend fun delete(id: String): Boolean
    suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int)
}

class InMemoryStudySessionsRepository : StudySessionsRepository {
    private val items = LinkedHashMap<String, SavedStudySessionRecord>()
    private val mutex = Mutex()

    override suspend fun listByDevice(deviceTokenHash: String): List<SavedStudySessionRecord> =
        mutex.withLock {
            items.values
                .filter { it.deviceTokenHash == deviceTokenHash }
                .sortedByDescending { it.updatedAt }
        }

    override suspend fun findByDeviceAndSource(
        deviceTokenHash: String,
        sourceMessageId: String,
    ): SavedStudySessionRecord? =
        mutex.withLock {
            items.values.firstOrNull {
                it.deviceTokenHash == deviceTokenHash && it.sourceMessageId == sourceMessageId
            }
        }

    override suspend fun findById(id: String): SavedStudySessionRecord? =
        mutex.withLock { items[id] }

    override suspend fun upsert(record: SavedStudySessionRecord): SavedStudySessionRecord =
        mutex.withLock {
            items[record.id] = record
            record
        }

    override suspend fun delete(id: String): Boolean =
        mutex.withLock { items.remove(id) != null }

    override suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int) {
        mutex.withLock {
            val owned = items.values
                .filter { it.deviceTokenHash == deviceTokenHash }
                .sortedByDescending { it.updatedAt }
            owned.drop(keepNewest).forEach { items.remove(it.id) }
        }
    }
}

class StudySessionsService(
    private val repository: StudySessionsRepository,
    private val deviceService: DeviceService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val maxPerDevice: Int = DEFAULT_MAX_PER_DEVICE,
) {
    suspend fun list(rawDeviceToken: String?): SavedStudySessionsResponse {
        val device = requireDevice(rawDeviceToken)
        val items = repository.listByDevice(device.tokenHash).map { it.toApi() }
        return SavedStudySessionsResponse(items)
    }

    suspend fun save(rawDeviceToken: String?, rawRequest: SaveStudySessionRequest): SavedStudySession {
        val device = requireDevice(rawDeviceToken)
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study session requires non-blank text and at least one sentence"
        }
        val now = clock()
        val existing = request.sourceMessageId?.let {
            repository.findByDeviceAndSource(device.tokenHash, it)
        }
        val record = SavedStudySessionRecord(
            id = existing?.id ?: idGenerator(),
            deviceTokenHash = device.tokenHash,
            sourceMessageId = request.sourceMessageId,
            languageTag = request.languageTag,
            text = request.session.text,
            textLanguageTag = request.session.textLanguageTag,
            session = request.session,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val saved = repository.upsert(record)
        repository.deleteOldestBeyond(device.tokenHash, maxPerDevice)
        return saved.toApi()
    }

    /**
     * @return true when deleted, false when the id is missing or owned by another device.
     */
    suspend fun delete(rawDeviceToken: String?, id: String): Boolean {
        val device = requireDevice(rawDeviceToken)
        val existing = repository.findById(id) ?: return false
        if (existing.deviceTokenHash != device.tokenHash) return false
        return repository.delete(id)
    }

    private suspend fun requireDevice(rawDeviceToken: String?): DeviceRecord {
        return deviceService.findValid(rawDeviceToken)
            ?: throw UnauthorizedDeviceException()
    }

    companion object {
        const val DEFAULT_MAX_PER_DEVICE = 50
    }
}
