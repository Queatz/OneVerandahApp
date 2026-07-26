package apap.oneverandah.chat

import apap.oneverandah.api.ChatMiniApp
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.SaveChatMessageRequest
import apap.oneverandah.api.SavedChatMessage
import apap.oneverandah.api.SavedChatMessagesResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SavedChatMessageRecord(
    val id: String,
    val deviceTokenHash: String,
    val sourceMessageId: String? = null,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val savedAt: Long,
    val miniApp: ChatMiniApp? = null,
    val followUpQuestions: List<String> = emptyList(),
) {
    fun toApi() = SavedChatMessage(
        id = id,
        sourceMessageId = sourceMessageId,
        sender = sender,
        text = text,
        createdAt = createdAt,
        savedAt = savedAt,
        miniApp = miniApp,
        followUpQuestions = followUpQuestions,
    )
}

interface SavedMessagesRepository {
    suspend fun listByDevice(deviceTokenHash: String): List<SavedChatMessageRecord>
    suspend fun findByDeviceAndSource(deviceTokenHash: String, sourceMessageId: String): SavedChatMessageRecord?
    suspend fun findById(id: String): SavedChatMessageRecord?
    suspend fun upsert(record: SavedChatMessageRecord): SavedChatMessageRecord
    suspend fun delete(id: String): Boolean
    suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int)
}

class InMemorySavedMessagesRepository : SavedMessagesRepository {
    private val items = LinkedHashMap<String, SavedChatMessageRecord>()
    private val mutex = Mutex()

    override suspend fun listByDevice(deviceTokenHash: String): List<SavedChatMessageRecord> =
        mutex.withLock {
            items.values
                .filter { it.deviceTokenHash == deviceTokenHash }
                .sortedByDescending { it.savedAt }
        }

    override suspend fun findByDeviceAndSource(
        deviceTokenHash: String,
        sourceMessageId: String,
    ): SavedChatMessageRecord? =
        mutex.withLock {
            items.values.firstOrNull {
                it.deviceTokenHash == deviceTokenHash && it.sourceMessageId == sourceMessageId
            }
        }

    override suspend fun findById(id: String): SavedChatMessageRecord? =
        mutex.withLock { items[id] }

    override suspend fun upsert(record: SavedChatMessageRecord): SavedChatMessageRecord =
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
                .sortedByDescending { it.savedAt }
            owned.drop(keepNewest).forEach { items.remove(it.id) }
        }
    }
}

class SavedMessagesService(
    private val repository: SavedMessagesRepository,
    private val deviceService: DeviceService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val maxPerDevice: Int = DEFAULT_MAX_PER_DEVICE,
) {
    suspend fun list(rawDeviceToken: String?): SavedChatMessagesResponse {
        val device = requireDevice(rawDeviceToken)
        val items = repository.listByDevice(device.tokenHash).map { it.toApi() }
        return SavedChatMessagesResponse(items)
    }

    suspend fun save(rawDeviceToken: String?, rawRequest: SaveChatMessageRequest): SavedChatMessage {
        val device = requireDevice(rawDeviceToken)
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Message text must not be blank" }
        val now = clock()
        val existing = request.sourceMessageId?.let {
            repository.findByDeviceAndSource(device.tokenHash, it)
        }
        val record = SavedChatMessageRecord(
            id = existing?.id ?: idGenerator(),
            deviceTokenHash = device.tokenHash,
            sourceMessageId = request.sourceMessageId,
            sender = request.sender,
            text = request.text,
            createdAt = request.createdAt,
            savedAt = now,
            miniApp = request.miniApp,
            followUpQuestions = request.followUpQuestions,
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
        const val DEFAULT_MAX_PER_DEVICE = 100
    }
}

class UnauthorizedDeviceException : RuntimeException("Valid device token required")
