package apap.oneverandah.chat

import apap.oneverandah.api.CommunityStudySessionsResponse
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.SavedStudySessionsResponse
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudyAdventureProgress
import apap.oneverandah.api.StudyBreakdownProgress
import apap.oneverandah.api.mergeStudyBreakdownProgress
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.sentenceImageUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val visitorExamGrades: Map<String, StudyExamGrade> = emptyMap(),
    val adventureProgress: StudyAdventureProgress? = null,
    val visitorAdventureProgress: Map<String, StudyAdventureProgress> = emptyMap(),
    val breakdownProgress: StudyBreakdownProgress? = null,
    val visitorBreakdownProgress: Map<String, StudyBreakdownProgress> = emptyMap(),
    val build: StudyBuildState? = null,
    val buildRequest: StudyBuildRequest? = null,
) {
    fun examGradeFor(deviceTokenHash: String): StudyExamGrade? =
        if (this.deviceTokenHash == deviceTokenHash) examGrade else visitorExamGrades[deviceTokenHash]

    fun adventureProgressFor(deviceTokenHash: String): StudyAdventureProgress? =
        if (this.deviceTokenHash == deviceTokenHash) adventureProgress else visitorAdventureProgress[deviceTokenHash]

    fun breakdownProgressFor(deviceTokenHash: String): StudyBreakdownProgress? =
        if (this.deviceTokenHash == deviceTokenHash) breakdownProgress else visitorBreakdownProgress[deviceTokenHash]

    fun toApi(
        viewerExamGrade: StudyExamGrade? = examGrade,
        viewerAdventureProgress: StudyAdventureProgress? = adventureProgress,
        viewerBreakdownProgress: StudyBreakdownProgress? = breakdownProgress,
    ) = SavedStudySession(
        id = id,
        sourceMessageId = sourceMessageId,
        languageTag = languageTag,
        text = text,
        textLanguageTag = textLanguageTag,
        session = session,
        createdAt = createdAt,
        updatedAt = updatedAt,
        name = name,
        examGrade = viewerExamGrade,
        build = build,
        adventureProgress = viewerAdventureProgress,
        breakdownProgress = viewerBreakdownProgress,
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

private fun SavedStudySessionRecord.toSummaryRecord(viewerExamGrade: StudyExamGrade? = examGrade) = SavedStudySessionSummaryRecord(
    id = id,
    sourceMessageId = sourceMessageId,
    languageTag = languageTag,
    text = text,
    textLanguageTag = textLanguageTag,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sentenceImageUrls = session.sentenceImageUrls(),
    name = name,
    examGrade = viewerExamGrade,
)

interface StudySessionsRepository {
    suspend fun listByDevice(deviceTokenHash: String): List<SavedStudySessionSummaryRecord>
    suspend fun findByDeviceAndSource(deviceTokenHash: String, sourceMessageId: String): SavedStudySessionRecord?
    suspend fun findById(id: String): SavedStudySessionRecord?
    suspend fun upsert(record: SavedStudySessionRecord): SavedStudySessionRecord
    suspend fun delete(id: String): Boolean
    suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int)
    suspend fun listRecent(limit: Int = 40): List<SavedStudySessionRecord>
    suspend fun listIncompleteBuilds(): List<String>
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

    override suspend fun listIncompleteBuilds(): List<String> =
        mutex.withLock {
            items.values
                .filter {
                    it.build?.status == apap.oneverandah.api.StudyBuildStatus.PREPARING ||
                            it.build?.status == apap.oneverandah.api.StudyBuildStatus.BUILDING ||
                            (
                                (it.build?.status == apap.oneverandah.api.StudyBuildStatus.COMPLETE ||
                                        it.build?.status == apap.oneverandah.api.StudyBuildStatus.PARTIAL) &&
                                        StudyBuildPlanner().incompleteTalkElements(it).isNotEmpty()
                                )
                }
                .map { it.id }
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
                .map { it.toSummaryRecord(viewerExamGrade = it.examGradeFor(excludeDeviceTokenHash)) }
        }
}

class StudySessionsService(
    private val repository: StudySessionsRepository,
    private val deviceService: DeviceService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    val maxPerDevice: Int = DEFAULT_MAX_PER_DEVICE,
    private val studyAiClient: StudyAiClient? = null,
    val sessionStore: StudySessionStore = StudySessionStore(repository, clock = clock),
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
        val record = sessionStore.findById(key) ?: return null
        return record.toApi(
            viewerExamGrade = record.examGradeFor(device.tokenHash),
            viewerAdventureProgress = record.adventureProgressFor(device.tokenHash),
            viewerBreakdownProgress = record.breakdownProgressFor(device.tokenHash),
        )
    }

    suspend fun getOwned(rawDeviceToken: String?, id: String): SavedStudySession? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val record = sessionStore.findById(key) ?: return null
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
        val record = sessionStore.update(key, immediate = true) { stored ->
            if (stored.deviceTokenHash == device.tokenHash) {
                stored.copy(examGrade = grade)
            } else {
                stored.copy(visitorExamGrades = stored.visitorExamGrades + (device.tokenHash to grade))
            }
        } ?: return null
        return record.toApi(
            viewerExamGrade = record.examGradeFor(device.tokenHash),
            viewerAdventureProgress = record.adventureProgressFor(device.tokenHash),
            viewerBreakdownProgress = record.breakdownProgressFor(device.tokenHash),
        )
    }

    suspend fun saveAdventureProgress(
        rawDeviceToken: String?,
        id: String,
        progress: StudyAdventureProgress,
    ): StudyAdventureProgress? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val normalized = progress.normalizedOrNull() ?: return null
        val record = sessionStore.update(key, immediate = true) { stored ->
            if (stored.deviceTokenHash == device.tokenHash) {
                stored.copy(adventureProgress = mergeAdventureProgress(stored.adventureProgress, normalized))
            } else {
                val current = stored.visitorAdventureProgress[device.tokenHash]
                stored.copy(
                    visitorAdventureProgress = stored.visitorAdventureProgress +
                            (device.tokenHash to mergeAdventureProgress(current, normalized)),
                )
            }
        } ?: return null
        return record.adventureProgressFor(device.tokenHash)
    }

    suspend fun saveBreakdownProgress(
        rawDeviceToken: String?,
        id: String,
        progress: StudyBreakdownProgress,
    ): StudyBreakdownProgress? {
        val device = requireDevice(rawDeviceToken)
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val normalized = progress.normalizedOrNull() ?: return null
        val record = sessionStore.update(key, immediate = true) { stored ->
            if (stored.deviceTokenHash == device.tokenHash) {
                stored.copy(breakdownProgress = mergeStudyBreakdownProgress(stored.breakdownProgress, normalized))
            } else {
                val current = stored.visitorBreakdownProgress[device.tokenHash]
                stored.copy(
                    visitorBreakdownProgress = stored.visitorBreakdownProgress +
                            (device.tokenHash to mergeStudyBreakdownProgress(current, normalized)!!),
                )
            }
        } ?: return null
        return record.breakdownProgressFor(device.tokenHash)
    }

    private fun mergeAdventureProgress(
        current: StudyAdventureProgress?,
        incoming: StudyAdventureProgress,
    ): StudyAdventureProgress {
        if (current == null) return incoming
        val completedLevels = maxOf(current.completedLevels, incoming.completedLevels)
        val selected = when {
            incoming.completedLevels > current.completedLevels -> incoming
            incoming.completedLevels < current.completedLevels -> current
            incoming.updatedAt >= current.updatedAt -> incoming
            else -> current
        }
        return selected.copy(completedLevels = completedLevels)
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
            items = page.map { it.toApi() },
            nextCursor = nextCursor,
        )
    }

    /**
     * Generate a study skeleton and persist it even if the caller is cancelled
     * after the request body has been received.
     */
    suspend fun createFromStudyRequest(
        rawDeviceToken: String?,
        rawRequest: StudySessionRequest,
        generate: suspend (StudySessionRequest) -> StudySessionResponse,
    ): StudySessionResponse = withContext(NonCancellable) {
        val session = generate(rawRequest)
        val persist = SaveStudySessionRequest(
            sourceMessageId = rawRequest.sourceMessageId,
            languageTag = rawRequest.languageTag,
            session = session,
        )
        if (!rawDeviceToken.isNullOrBlank() && persist.normalizedOrNull() != null) {
            try {
                save(rawDeviceToken, persist)
            } catch (_: UnauthorizedDeviceException) {
                // Generation still succeeds; the client can retry persist later.
            }
        }
        session
    }

    suspend fun save(rawDeviceToken: String?, rawRequest: SaveStudySessionRequest): SavedStudySessionSummary =
        withContext(NonCancellable) {
            val device = requireDevice(rawDeviceToken)
            val request = requireNotNull(rawRequest.normalizedOrNull()) {
                "Study session requires non-blank text and at least one sentence"
            }
            val now = clock()
            val existing = request.sourceMessageId?.let {
                repository.findByDeviceAndSource(device.tokenHash, it)
            }?.let { stored -> sessionStore.findById(stored.id) ?: stored }
            val session = request.session
            val fallbackName = existing?.name?.takeIf(String::isNotBlank) ?: fallbackSessionName(session.text)
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
                name = fallbackName,
            )
            val saved = if (existing == null) {
                sessionStore.create(record)
            } else {
                sessionStore.update(existing.id, immediate = true) { stored ->
                    StudySessionMerge.mergeClientSave(stored, record)
                } ?: sessionStore.create(record.copy(id = idGenerator(), createdAt = now))
            }
            repository.deleteOldestBeyond(device.tokenHash, maxPerDevice)
            if (existing?.name.isNullOrBlank()) {
                val generatedName = generateSessionName(session.text, request.languageTag)
                if (generatedName != saved.name) {
                    val renamed = sessionStore.update(
                        saved.id,
                        reason = StudyBuildEvent.Renamed(generatedName),
                        immediate = true,
                    ) { current ->
                        if (current.deviceTokenHash == device.tokenHash && current.name == fallbackName) {
                            current.copy(name = generatedName)
                        } else {
                            null
                        }
                    }
                    return@withContext (renamed ?: sessionStore.findById(saved.id) ?: saved).toSummaryApi()
                }
            }
            saved.toSummaryApi()
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
        val existing = sessionStore.findById(id) ?: return false
        if (existing.deviceTokenHash != device.tokenHash) return false
        return sessionStore.delete(id)
    }

    /** Renames a session only when it belongs to the current device. */
    suspend fun rename(rawDeviceToken: String?, id: String, rawName: String): SavedStudySessionSummary? {
        val device = requireDevice(rawDeviceToken)
        val name = rawName.trim().takeIf { it.isNotEmpty() } ?: return null
        val renamed = sessionStore.update(
            id = id.trim(),
            reason = StudyBuildEvent.Renamed(name),
            immediate = true,
        ) { existing ->
            if (existing.deviceTokenHash != device.tokenHash) return@update null
            existing.copy(name = name)
        } ?: return null
        return renamed.toSummaryApi()
    }

    internal suspend fun generateName(text: String, languageTag: String): String =
        generateSessionName(text, languageTag)

    private suspend fun generateSessionName(text: String, languageTag: String): String {
        val generated = try {
            studyAiClient?.generateStudySessionName(text, languageTag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }?.trim()?.takeIf { it.isNotEmpty() }
        if (generated != null) return generated.take(MAX_STUDY_SESSION_NAME_LENGTH).trimEnd()
        return fallbackSessionName(text)
    }

    private fun fallbackSessionName(text: String): String {
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
