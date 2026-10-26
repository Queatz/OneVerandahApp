package apap.oneverandah.chat

import apap.oneverandah.api.CommunityStudySessionsResponse
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.SavedStudySessionsResponse
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.sentenceImageUrls
import kotlinx.coroutines.CancellationException
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
    val name: String = "",
    val examGrade: StudyExamGrade? = null,
) {
    fun toApi(includeExamGrade: Boolean = true) = SavedStudySession(
        id = id,
        sourceMessageId = sourceMessageId,
        languageTag = languageTag,
        text = text,
        textLanguageTag = textLanguageTag,
        session = session,
        createdAt = createdAt,
        updatedAt = updatedAt,
        name = name,
        examGrade = examGrade.takeIf { includeExamGrade },
    )

    fun toSummaryApi() = SavedStudySessionSummary(
        id = id,
        sourceMessageId = sourceMessageId,
        languageTag = languageTag,
        text = text,
        textLanguageTag = textLanguageTag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sentenceImageUrls = session.sentenceImageUrls(),
        name = name,
        examGrade = examGrade,
    )
}

data class SavedStudySessionSummaryRecord(
    val id: String,
    val sourceMessageId: String? = null,
    val languageTag: String,
    val text: String,
    val textLanguageTag: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sentenceImageUrls: List<String> = emptyList(),
    val name: String = "",
    val examGrade: StudyExamGrade? = null,
) {
    fun toApi(includeExamGrade: Boolean = true) = SavedStudySessionSummary(
        id = id,
        sourceMessageId = sourceMessageId,
        languageTag = languageTag,
        text = text,
        textLanguageTag = textLanguageTag,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sentenceImageUrls = sentenceImageUrls,
        name = name,
        examGrade = examGrade.takeIf { includeExamGrade },
    )
}

private fun SavedStudySessionRecord.toSummaryRecord(includeExamGrade: Boolean = true) = SavedStudySessionSummaryRecord(
    id = id,
    sourceMessageId = sourceMessageId,
    languageTag = languageTag,
    text = text,
    textLanguageTag = textLanguageTag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sentenceImageUrls = session.sentenceImageUrls(),
    name = name,
    examGrade = examGrade.takeIf { includeExamGrade },
)

interface StudySessionsRepository {
    suspend fun listByDevice(deviceTokenHash: String): List<SavedStudySessionSummaryRecord>
    suspend fun findByDeviceAndSource(deviceTokenHash: String, sourceMessageId: String): SavedStudySessionRecord?
    suspend fun findById(id: String): SavedStudySessionRecord?
    suspend fun upsert(record: SavedStudySessionRecord): SavedStudySessionRecord
    suspend fun delete(id: String): Boolean
    suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int)
    suspend fun listRecent(limit: Int = 40): List<SavedStudySessionRecord>
    suspend fun listExceptDevice(
        excludeDeviceTokenHash: String,
        limit: Int,
        afterUpdatedAt: Long? = null,
        afterId: String? = null,
    ): List<SavedStudySessionSummaryRecord>
}

class InMemoryStudySessionsRepository : StudySessionsRepository {
    private val items = LinkedHashMap<String, SavedStudySessionRecord>()
    private val mutex = Mutex()

    override suspend fun listByDevice(deviceTokenHash: String): List<SavedStudySessionSummaryRecord> =
        mutex.withLock {
            items.values
                .filter { it.deviceTokenHash == deviceTokenHash }
                .sortedByDescending { it.updatedAt }
                .map { it.toSummaryRecord() }
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

    override suspend fun listRecent(limit: Int): List<SavedStudySessionRecord> =
        mutex.withLock {
            items.values.sortedByDescending { it.updatedAt }.take(limit.coerceAtLeast(0))
        }

    override suspend fun listExceptDevice(
        excludeDeviceTokenHash: String,
        limit: Int,
        afterUpdatedAt: Long?,
        afterId: String?,
    ): List<SavedStudySessionSummaryRecord> =
        mutex.withLock {
            items.values
                .filter { it.deviceTokenHash != excludeDeviceTokenHash }
                .filter { record ->
                    afterUpdatedAt == null ||
                        record.updatedAt < afterUpdatedAt ||
                        (record.updatedAt == afterUpdatedAt && afterId != null && record.id < afterId)
                }
                .sortedWith(
                    compareByDescending<SavedStudySessionRecord> { it.updatedAt }
                        .thenByDescending { it.id },
                )
                .take(limit.coerceAtLeast(0))
                .map { it.toSummaryRecord(includeExamGrade = false) }
        }
}

class StudySessionsService(
    private val repository: StudySessionsRepository,
    private val deviceService: DeviceService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val maxPerDevice: Int = DEFAULT_MAX_PER_DEVICE,
    private val studyAiClient: StudyAiClient? = null,
) {
    suspend fun list(rawDeviceToken: String?): SavedStudySessionsResponse {
        val device = requireDevice(rawDeviceToken)
        val items = repository.listByDevice(device.tokenHash).map { it.toApi() }
        return SavedStudySessionsResponse(items)
    }

    /** Any saved session can be opened by id so `/study/{id}` links are shareable. */
    suspend fun get(rawDeviceToken: String?, id: String): SavedStudySession? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim()
        if (key.isEmpty()) return null
        val record = repository.findById(key) ?: return null
        return record.toApi(includeExamGrade = record.deviceTokenHash == device.tokenHash)
    }

    suspend fun getOwned(rawDeviceToken: String?, id: String): SavedStudySession? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val record = repository.findById(key) ?: return null
        if (record.deviceTokenHash != device.tokenHash) return null
        return record.toApi()
    }

    suspend fun saveExamGrade(
        rawDeviceToken: String?,
        id: String,
        grade: StudyExamGrade,
    ): SavedStudySession? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val record = repository.findById(key) ?: return null
        if (record.deviceTokenHash != device.tokenHash) return null
        return repository.upsert(record.copy(examGrade = grade, updatedAt = clock())).toApi()
    }

    suspend fun listCommunity(
        rawDeviceToken: String?,
        cursor: String? = null,
        limit: Int = DEFAULT_COMMUNITY_PAGE_SIZE,
    ): CommunityStudySessionsResponse {
        val device = requireDevice(rawDeviceToken)
        val pageSize = limit.coerceIn(1, MAX_COMMUNITY_PAGE_SIZE)
        val parsed = parseStudyCommunityCursor(cursor)
        val records = repository.listExceptDevice(
            excludeDeviceTokenHash = device.tokenHash,
            limit = pageSize + 1,
            afterUpdatedAt = parsed?.first,
            afterId = parsed?.second,
        )
        val page = records.take(pageSize)
        val nextCursor = if (records.size > pageSize) {
            page.lastOrNull()?.let { studyCommunityCursor(it.updatedAt, it.id) }
        } else {
            null
        }
        return CommunityStudySessionsResponse(
            items = page.map { it.toApi(includeExamGrade = false) },
            nextCursor = nextCursor,
        )
    }

    suspend fun save(rawDeviceToken: String?, rawRequest: SaveStudySessionRequest): SavedStudySessionSummary {
        val device = requireDevice(rawDeviceToken)
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study session requires non-blank text and at least one sentence"
        }
        val now = clock()
        val existing = request.sourceMessageId?.let {
            repository.findByDeviceAndSource(device.tokenHash, it)
        }
        val session = request.session.copy(
            examScene = request.session.examScene ?: existing?.session?.examScene,
        )
        val record = SavedStudySessionRecord(
            id = existing?.id ?: idGenerator(),
            deviceTokenHash = device.tokenHash,
            sourceMessageId = request.sourceMessageId,
            languageTag = request.languageTag,
            text = session.text,
            textLanguageTag = session.textLanguageTag,
            session = session,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            name = existing?.name ?: generateSessionName(request.session.text, request.languageTag),
            examGrade = existing?.examGrade,
        )
        val saved = repository.upsert(record)
        repository.deleteOldestBeyond(device.tokenHash, maxPerDevice)
        return saved.toSummaryApi()
    }

    /** Harvest named portraits from recent study sessions for Mini Story photo generation. */
    suspend fun listRecentCharacters(limit: Int = 40): List<apap.oneverandah.api.StudyStoryCharacter> =
        repository.listRecent(limit)
            .flatMap { it.session.characters }
            .filter { !it.imageUrl.isNullOrBlank() }
            .distinctBy { it.imageUrl }

    /**
     * @return true when deleted, false when the id is missing or owned by another device.
     */
    suspend fun delete(rawDeviceToken: String?, id: String): Boolean {
        val device = requireDevice(rawDeviceToken)
        val existing = repository.findById(id) ?: return false
        if (existing.deviceTokenHash != device.tokenHash) return false
        return repository.delete(id)
    }

    /** Renames a session only when it belongs to the current device. */
    suspend fun rename(rawDeviceToken: String?, id: String, rawName: String): SavedStudySessionSummary? {
        val device = requireDevice(rawDeviceToken)
        val name = rawName.trim().takeIf { it.isNotEmpty() } ?: return null
        val existing = repository.findById(id.trim()) ?: return null
        if (existing.deviceTokenHash != device.tokenHash) return null
        return repository.upsert(existing.copy(name = name, updatedAt = clock())).toSummaryApi()
    }

    private suspend fun generateSessionName(text: String, languageTag: String): String {
        val generated = try {
            studyAiClient?.generateStudySessionName(text, languageTag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }?.trim()?.takeIf { it.isNotEmpty() }
        if (generated != null) return generated.take(MAX_STUDY_SESSION_NAME_LENGTH).trimEnd()
        val fallback = text.trim()
        return if (fallback.length > MAX_STUDY_SESSION_NAME_LENGTH) {
            fallback.take(MAX_STUDY_SESSION_NAME_LENGTH - 1).trimEnd() + "…"
        } else {
            fallback
        }
    }

    private suspend fun requireDevice(rawDeviceToken: String?): DeviceRecord {
        return deviceService.findValid(rawDeviceToken)
            ?: throw UnauthorizedDeviceException()
    }

    companion object {
        private const val MAX_STUDY_SESSION_NAME_LENGTH = 80
        const val DEFAULT_MAX_PER_DEVICE = 50
        const val DEFAULT_COMMUNITY_PAGE_SIZE = 20
        const val MAX_COMMUNITY_PAGE_SIZE = 50
    }
}

internal fun studyCommunityCursor(updatedAt: Long, id: String): String = "$updatedAt:$id"

internal fun parseStudyCommunityCursor(raw: String?): Pair<Long, String>? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val sep = value.indexOf(':')
    if (sep <= 0 || sep == value.lastIndex) return null
    val updatedAt = value.substring(0, sep).toLongOrNull() ?: return null
    val id = value.substring(sep + 1)
    if (id.isEmpty()) return null
    return updatedAt to id
}
