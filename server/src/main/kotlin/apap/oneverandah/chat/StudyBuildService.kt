package apap.oneverandah.chat

import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildJournalEntry
import apap.oneverandah.api.StudyBuildJournalOutcome
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildRetryRequest
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.config.AppConfig
import apap.oneverandah.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class StudyBuildService(
    private val repository: StudySessionsRepository,
    private val deviceService: DeviceService,
    private val sessionsService: StudySessionsService,
    studyService: StudyService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    textWorkers: Int = 4,
    imageWorkers: Int = 3,
    maxAttempts: Int = 3,
    retryBackoffMillis: List<Long> = StudyBuildQueue.DEFAULT_RETRY_BACKOFF_MILLIS,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    speechService: SpeechService? = null,
    talkAudioGenerator: StudyTalkAudioGenerator? = null,
) {
    private val store = sessionsService.sessionStore
    private val attemptLimit = maxAttempts.coerceAtLeast(1)
    private val planner = StudyBuildPlanner(
        maxAttempts = attemptLimit,
        supportsTalkImages = studyService.supportsTalkImages,
        supportsTalkAudio = talkAudioGenerator != null,
    )
    private val planningLocks = ConcurrentHashMap<String, Mutex>()
    private val recoveryStarted = AtomicBoolean(false)
    private val queue = StudyBuildQueue(
        store = store,
        executor = StudyBuildTasks(store, studyService, sessionsService, speechService, talkAudioGenerator),
        scope = scope,
        textWorkers = textWorkers,
        imageWorkers = imageWorkers,
        maxAttempts = attemptLimit,
        retryBackoffMillis = retryBackoffMillis,
        clock = clock,
        onTaskSettled = { id -> planSession(id) },
    )

    constructor(
        repository: StudySessionsRepository,
        deviceService: DeviceService,
        sessionsService: StudySessionsService,
        studyService: StudyService,
        scope: CoroutineScope,
        config: AppConfig,
        idGenerator: () -> String = { UUID.randomUUID().toString() },
        clock: () -> Long = System::currentTimeMillis,
        speechService: SpeechService? = null,
        talkAudioGenerator: StudyTalkAudioGenerator? = null,
    ) : this(
        repository = repository,
        deviceService = deviceService,
        sessionsService = sessionsService,
        studyService = studyService,
        scope = scope,
        textWorkers = config.studyBuildTextWorkers,
        imageWorkers = config.studyBuildImageWorkers,
        maxAttempts = config.studyBuildMaxAttempts,
        idGenerator = idGenerator,
        clock = clock,
        speechService = speechService,
        talkAudioGenerator = talkAudioGenerator,
    )

    suspend fun create(rawDeviceToken: String?, rawRequest: StudyBuildRequest): SavedStudySession {
        val device = deviceService.findValid(rawDeviceToken) ?: throw UnauthorizedDeviceException()
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study build requires non-blank text" }
        val existing = request.sourceMessageId?.let {
            repository.findByDeviceAndSource(device.tokenHash, it)
        }

        if (existing != null && existing.languageTag == request.languageTag) {
            ensureBuilding(existing.id)
            return requireNotNull(getSession(existing.id))
        }

        val now = clock()
        val record = newRecord(
            id = existing?.id ?: idGenerator(),
            deviceTokenHash = device.tokenHash,
            request = request,
            createdAt = existing?.createdAt ?: now,
            now = now,
        )
        val stored = if (existing == null) {
            store.create(record)
        } else {
            store.update(existing.id, reason = StudyBuildEvent.Build(record.build!!), immediate = true) {
                record.copy(deviceTokenHash = it.deviceTokenHash, createdAt = it.createdAt)
            } ?: store.create(record.copy(id = idGenerator()))
        }
        repository.deleteOldestBeyond(device.tokenHash, sessionsService.maxPerDevice)
        queue.start()
        store.publish(stored.id, StudyBuildEvent.Build(stored.build!!))
        planSession(stored.id)
        return stored.toApi()
    }

    suspend fun ensureBuilding(id: String, lowPriority: Boolean = false): SavedStudySession? {
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        var record = store.findById(key) ?: return null
        if (record.build == null) {
            record = store.update(
                key,
                reason = StudyBuildEvent.Build(
                    StudyBuildState(
                        status = StudyBuildStatus.BUILDING,
                        updatedAt = clock()
                    )
                ),
                immediate = true,
            ) { current ->
                current.copy(
                    build = StudyBuildState(status = StudyBuildStatus.BUILDING, updatedAt = clock()),
                    buildRequest = null,
                )
            } ?: return null
        }
        if (
            (record.build?.status == StudyBuildStatus.COMPLETE || record.build?.status == StudyBuildStatus.PARTIAL) &&
            planner.plan(record).any {
                it.element.kind == StudyBuildElementKind.EXAMPLE_DISSECT ||
                        it.element.kind == StudyBuildElementKind.CHUNKS ||
                        it.element.kind == StudyBuildElementKind.WORD_RECALL_SCENARIOS ||
                        it.element.kind == StudyBuildElementKind.TALK_WORLD ||
                        it.element.kind == StudyBuildElementKind.TALK_SCENE_IMAGE ||
                        it.element.kind == StudyBuildElementKind.TALK_EXPRESSION ||
                        it.element.kind == StudyBuildElementKind.TALK_AUDIO
            }
        ) {
            record = store.update(key, immediate = true) { current ->
                val build = current.build ?: return@update null
                current.copy(build = build.copy(status = StudyBuildStatus.BUILDING, updatedAt = clock()))
            } ?: return null
        }
        queue.start()
        planSession(key, lowPriority)
        return getSession(key)
    }

    suspend fun retry(
        rawDeviceToken: String?,
        id: String,
        request: StudyBuildRetryRequest,
    ): StudyBuildState? {
        val device = deviceService.findValid(rawDeviceToken) ?: throw UnauthorizedDeviceException()
        val key = id.trim().takeIf(String::isNotEmpty) ?: return null
        val record = store.findById(key) ?: return null
        if (record.deviceTokenHash != device.tokenHash) return null
        var shouldPlan = false
        val retried = planningLocks.computeIfAbsent(key) { Mutex() }.withLock {
            val current = store.findById(key) ?: return@withLock null
            val currentBuild = current.build ?: return@withLock null
            val requestedKeys = request.elements.mapTo(mutableSetOf()) { it.key }
            val candidates = (
                currentBuild.failed.filter { request.elements.isEmpty() || it.key in requestedKeys } +
                        planner.incompleteTalkElements(current)
                            .filter { request.elements.isEmpty() || it.key in requestedKeys }
                ).distinctBy { it.key }
            if (candidates.isEmpty()) {
                return@withLock currentBuild.copy(inFlight = queue.activeElements(key))
            }

            val runningKeys = queue.dropIdle(key, candidates.mapTo(mutableSetOf()) { it.key })
            val selected = candidates.filterNot { it.key in runningKeys }
            if (selected.isEmpty()) {
                return@withLock currentBuild.copy(inFlight = queue.activeElements(key))
            }
            val selectedKeys = selected.mapTo(mutableSetOf()) { it.key }
            shouldPlan = true
            val updated = store.update(key, immediate = true) { latest ->
                val build = latest.build ?: return@update null
                latest.copy(
                    build = build.copy(
                        status = if (selected.any { it.kind == StudyBuildElementKind.PREPARE }) {
                            StudyBuildStatus.PREPARING
                        } else {
                            StudyBuildStatus.BUILDING
                        },
                        failed = build.failed.filterNot { it.key in selectedKeys },
                        failureReasons = build.failureReasons - selectedKeys,
                        attempts = build.attempts - selectedKeys,
                        automaticRevives = build.automaticRevives - selectedKeys,
                        journal = (build.journal + selected.map { element ->
                            StudyBuildJournalEntry(
                                timestamp = clock(),
                                element = element,
                                outcome = StudyBuildJournalOutcome.REVIVED,
                                attempt = build.attempts[element.key] ?: 0,
                                detail = "Manually retried by the user.",
                            )
                        }).takeLast(StudyBuildQueue.MAX_JOURNAL_ENTRIES),
                        updatedAt = clock(),
                    ),
                )
            } ?: return@withLock null
            updated.build!!.copy(inFlight = queue.activeElements(key))
        }
        if (shouldPlan) {
            queue.start()
            retried?.let { store.publish(key, StudyBuildEvent.Build(it)) }
            planSession(key)
        }
        return retried?.let { getSession(key)?.build ?: it }
    }

    suspend fun resumeAll() {
        queue.start()
        resumeIncompleteSessions()
        if (recoveryStarted.compareAndSet(false, true)) {
            scope.launch {
                while (isActive) {
                    delay(RECOVERY_INTERVAL_MILLIS)
                    runCatching { resumeIncompleteSessions() }
                }
            }
        }
    }

    private suspend fun resumeIncompleteSessions() {
        repository.listIncompleteBuilds().forEach { id -> ensureBuilding(id, lowPriority = true) }
    }

    suspend fun getSession(id: String): SavedStudySession? {
        val record = store.findById(id) ?: return null
        val build = record.build?.copy(inFlight = queue.activeElements(id))
        return record.toApi().copy(build = build)
    }

    suspend fun awaitSkeleton(id: String): StudySessionResponse? = coroutineScope {
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            events(id).first { it is StudyBuildEvent.Skeleton || it == StudyBuildEvent.Done }
        }
        val snapshot = getSession(id)
        when {
            snapshot == null -> {
                next.cancel()
                null
            }
            snapshot.session.sentences.isNotEmpty() -> {
                next.cancel()
                snapshot.session
            }
            snapshot.build?.status == StudyBuildStatus.COMPLETE || snapshot.build?.status == StudyBuildStatus.PARTIAL -> {
                next.cancel()
                null
            }
            else -> when (val event = next.await()) {
                is StudyBuildEvent.Skeleton -> event.session
                StudyBuildEvent.Done -> getSession(id)?.session?.takeIf { it.sentences.isNotEmpty() }
                else -> null
            }
        }
    }

    fun events(id: String): Flow<StudyBuildEvent> = store.events(id)

    private suspend fun planSession(id: String, lowPriority: Boolean = false) {
        val lock = planningLocks.computeIfAbsent(id) { Mutex() }
        lock.withLock {
            var record = store.findById(id) ?: return@withLock
            record = reviveRetryableTalkFailures(id, record) ?: return@withLock
            val activeKeys = queue.activeElements(id).mapTo(mutableSetOf()) { it.key }
            val exhausted = planner.exhausted(record).filterNot { it.key in activeKeys }
            if (exhausted.isNotEmpty()) {
                record = store.update(id) { current ->
                    val build = current.build ?: return@update null
                    current.copy(
                        build = build.copy(
                            failed = (build.failed + exhausted).distinctBy { it.key },
                            failureReasons = build.failureReasons + exhausted
                                .filterNot { it.key in build.failureReasons }
                                .associate { element ->
                                    element.key to "Automatic retries were exhausted before a failure detail was recorded."
                                },
                            updatedAt = clock(),
                        ),
                    )
                } ?: return@withLock
                store.publish(
                    id,
                    StudyBuildEvent.Build(record.build!!.copy(inFlight = queue.activeElements(id))),
                )
                record = reviveRetryableTalkFailures(id, record) ?: return@withLock
            }
            val tasks = planner.plan(record)
            if (tasks.isNotEmpty() || queue.hasActive(id)) {
                val build = record.build ?: return@withLock
                if (build.status == StudyBuildStatus.COMPLETE || build.status == StudyBuildStatus.PARTIAL) {
                    record = store.update(id, immediate = true) { current ->
                        val currentBuild = current.build ?: return@update null
                        current.copy(
                            build = currentBuild.copy(
                                status = if (tasks.any { it.element.kind == StudyBuildElementKind.PREPARE }) {
                                    StudyBuildStatus.PREPARING
                                } else {
                                    StudyBuildStatus.BUILDING
                                },
                                updatedAt = clock(),
                            ),
                        )
                    } ?: return@withLock
                }
            }
            queue.enqueue(
                tasks,
                priorityOverride = if (lowPriority) StudyBuildTaskPriority.LOW else null,
            )
            if (tasks.isNotEmpty() || queue.hasActive(id)) {
                publishBuild(record.id)
                return@withLock
            }
            val build = record.build ?: return@withLock
            val terminalStatus = if (build.failed.isEmpty()) StudyBuildStatus.COMPLETE else StudyBuildStatus.PARTIAL
            if (build.status != terminalStatus) {
                val terminal = store.update(id, immediate = true) { current ->
                    val currentBuild = current.build ?: return@update null
                    current.copy(
                        build = currentBuild.copy(status = terminalStatus, inFlight = emptyList(), updatedAt = clock()),
                    )
                } ?: return@withLock
                store.publish(id, StudyBuildEvent.Build(terminal.build!!.copy(inFlight = queue.activeElements(id))))
                store.publish(id, StudyBuildEvent.Done)
            }
        }
    }

    private suspend fun reviveRetryableTalkFailures(
        id: String,
        record: SavedStudySessionRecord,
    ): SavedStudySessionRecord? {
        val build = record.build ?: return record
        val missingKeys = planner.incompleteTalkElements(record).mapTo(mutableSetOf()) { it.key }
        val activeKeys = queue.activeElements(id).mapTo(mutableSetOf()) { it.key }
        val eligible = build.failed.filter { element ->
            element.key in missingKeys && element.key !in activeKeys &&
                    element.kind.isTalkBuildElement() &&
                    (build.automaticRevives[element.key] ?: 0) < MAX_AUTOMATIC_TALK_REVIVES &&
                    !isPermanentTalkFailure(build.failureReasons[element.key].orEmpty())
        }
        if (eligible.isEmpty()) return record

        return store.update(id, immediate = true) { current ->
            val currentBuild = current.build ?: return@update null
            val currentMissingKeys = planner.incompleteTalkElements(current).mapTo(mutableSetOf()) { it.key }
            val currentActiveKeys = queue.activeElements(id).mapTo(mutableSetOf()) { it.key }
            val revive = eligible.filter { element ->
                element.key in currentMissingKeys && element.key !in currentActiveKeys &&
                        (currentBuild.automaticRevives[element.key] ?: 0) < MAX_AUTOMATIC_TALK_REVIVES &&
                        !isPermanentTalkFailure(currentBuild.failureReasons[element.key].orEmpty())
            }
            if (revive.isEmpty()) return@update current
            val keys = revive.mapTo(mutableSetOf()) { it.key }
            val revivalEntries = revive.map { element ->
                val count = (currentBuild.automaticRevives[element.key] ?: 0) + 1
                StudyBuildJournalEntry(
                    timestamp = clock(),
                    element = element,
                    outcome = StudyBuildJournalOutcome.REVIVED,
                    attempt = currentBuild.attempts[element.key] ?: 0,
                    detail = "Automatic Talk retry $count of $MAX_AUTOMATIC_TALK_REVIVES scheduled.",
                )
            }
            current.copy(
                build = currentBuild.copy(
                    status = StudyBuildStatus.BUILDING,
                    failed = currentBuild.failed.filterNot { it.key in keys },
                    failureReasons = currentBuild.failureReasons - keys,
                    attempts = currentBuild.attempts - keys,
                    automaticRevives = currentBuild.automaticRevives + revive.associate { element ->
                        element.key to (currentBuild.automaticRevives[element.key] ?: 0) + 1
                    },
                    journal = (currentBuild.journal + revivalEntries).takeLast(StudyBuildQueue.MAX_JOURNAL_ENTRIES),
                    updatedAt = clock(),
                ),
            )
        }
    }

    private fun isPermanentTalkFailure(reason: String): Boolean =
        listOf("did not produce", "not configured").any { it in reason.lowercase() }

    private fun apap.oneverandah.api.StudyBuildElementKind.isTalkBuildElement(): Boolean = when (this) {
        StudyBuildElementKind.TALK_WORLD,
        StudyBuildElementKind.TALK_SCENE_IMAGE,
        StudyBuildElementKind.TALK_EXPRESSION,
        StudyBuildElementKind.TALK_AUDIO -> true

        else -> false
    }

    private suspend fun publishBuild(id: String) {
        val record = store.findById(id) ?: return
        val state = record.build ?: return
        store.publish(id, StudyBuildEvent.Build(state.copy(inFlight = queue.activeElements(id))))
    }

    private fun newRecord(
        id: String,
        deviceTokenHash: String,
        request: StudyBuildRequest,
        createdAt: Long,
        now: Long,
    ): SavedStudySessionRecord {
        val textLanguageTag = Language.normalizeContentTag(
            if (request.mode == StudyBuildMode.TRANSLATE) {
                requireNotNull(request.targetLanguageTag)
            } else {
                request.sourceLanguageTag ?: request.languageTag
            },
        )
        val status = if (request.mode == StudyBuildMode.EXACT) StudyBuildStatus.BUILDING else StudyBuildStatus.PREPARING
        val session = apap.oneverandah.api.StudySessionResponse(
            text = request.text,
            textLanguageTag = textLanguageTag,
            sentences = emptyList(),
        )
        return SavedStudySessionRecord(
            id = id,
            deviceTokenHash = deviceTokenHash,
            sourceMessageId = request.sourceMessageId,
            languageTag = request.languageTag,
            text = request.text,
            textLanguageTag = textLanguageTag,
            session = session,
            createdAt = createdAt,
            updatedAt = now,
            build = StudyBuildState(status = status, updatedAt = now),
            buildRequest = request.takeIf { it.mode != StudyBuildMode.EXACT },
        )
    }

    companion object {
        const val MAX_AUTOMATIC_TALK_REVIVES = 2
        const val RECOVERY_INTERVAL_MILLIS = 60_000L
    }
}
