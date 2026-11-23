package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChatApi
import apap.oneverandah.api.ChatApiException
import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyAdventureProgress
import apap.oneverandah.api.StudyBreakdownProgress
import apap.oneverandah.api.mergeStudyBreakdownProgress
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.SpeechTimingAligner
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamBeatScore
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.api.StudyExamScoreAnswer
import apap.oneverandah.api.StudyExamScoreRequest
import apap.oneverandah.api.StudyExamScoreResponse
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyListenCheckRequest
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayHistoryMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudyPlayTurnRequest
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.api.sentenceImageUrls
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.StudyWriteCheckRequest
import apap.oneverandah.api.StudyWordPlaygroundResponse
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.ChatInput
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.model.Language
import apap.oneverandah.state.Toaster
import apap.oneverandah.state.StudyAudioCache
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Ol
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Tr
import org.w3c.dom.Element
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import kotlin.js.Date

private val studySessionImageSlots = Semaphore(permits = 2)

@Composable
fun StudyScreen(
    sessionId: String? = null,
    initialText: String? = null,
    onInitialTextConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSession: (String) -> Unit = {},
    onCloseSession: () -> Unit = {},
) {
    val strings = Localization.strings
    val language = Localization.language
    val scope = rememberCoroutineScope()
    var studyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var studySessionVisible by remember { mutableStateOf(!sessionId.isNullOrBlank()) }
    var studyLoading by remember { mutableStateOf(false) }
    var studyError by remember { mutableStateOf<String?>(null) }
    var studySession by remember { mutableStateOf<StudySessionResponse?>(null) }
    val studySentenceStatus = remember { mutableStateMapOf<Int, StudySentenceLoadState>() }
    val studyCache = remember { mutableStateMapOf<String, StudySessionResponse>() }
    // "$messageId#$sentenceIndex" -> in-flight / settled sentence-image job
    val studyImageInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // "$messageId#$sentenceIndex" -> generation failed (no URL)
    val studyImageFailed = remember { mutableStateMapOf<String, Boolean>() }
    // "$messageId#$sentenceIndex#$tokenIndex" -> in-flight word-image job
    val studyWordImageInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // "$messageId#$sentenceIndex#$tokenIndex" -> word-image generation failed
    val studyWordImageFailed = remember { mutableStateMapOf<String, Boolean>() }
    // messageId -> story cast design / portrait polling in flight
    val studyCharactersInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // messageId -> play scenario generation / image polling in flight
    val studyPlayInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // messageId -> play scenario generation failed (empty scenarios)
    val studyPlayFailed = remember { mutableStateMapOf<String, Boolean>() }
    // messageId -> exam scene generation in flight
    val studyExamInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // messageId -> exam scene generation failed
    val studyExamFailed = remember { mutableStateMapOf<String, Boolean>() }
    val studyBuildStateByMessageId = remember { mutableStateMapOf<String, StudyBuildState>() }
    val studyAdventureProgressBySessionId = remember { mutableStateMapOf<String, StudyAdventureProgress>() }
    val studyBreakdownProgressBySessionId = remember { mutableStateMapOf<String, StudyBreakdownProgress>() }
    var studyBuildStreamJob by remember { mutableStateOf<Job?>(null) }
    // sourceMessageId (or session id fallback) -> persisted study row id
    val studyPersistedBySourceId = remember { mutableStateMapOf<String, String>() }
    var studySessionItems by remember { mutableStateOf<List<SavedStudySessionSummary>>(emptyList()) }
    var studySessionsLoading by remember { mutableStateOf(false) }
    var studySessionsError by remember { mutableStateOf(false) }
    var studySessionsJob by remember { mutableStateOf<Job?>(null) }
    var communitySessionItems by remember { mutableStateOf<List<SavedStudySessionSummary>>(emptyList()) }
    var communitySessionsLoading by remember { mutableStateOf(false) }
    var communitySessionsError by remember { mutableStateOf(false) }
    var communitySessionsLoadingMore by remember { mutableStateOf(false) }
    var communitySessionsJob by remember { mutableStateOf<Job?>(null) }
    var communityNextCursor by remember { mutableStateOf<String?>(null) }
    var activeStudySessionItem by remember { mutableStateOf<SavedStudySessionSummary?>(null) }
    var activeStudySessionOwned by remember { mutableStateOf(false) }
    var pendingStudySessionRename by remember { mutableStateOf<SavedStudySessionSummary?>(null) }
    var pendingStudyLanguageClone by remember { mutableStateOf(false) }
    val studyLanguageByMessageId = remember { mutableStateMapOf<String, String>() }
    // Exact = current behavior (study the typed text). Off (default from the list) parses first.
    var studyStartExact by remember { mutableStateOf(true) }

    fun applyStudySessionItems(items: List<SavedStudySessionSummary>) {
        val refreshedItems = mergeStudySessionItems(studySessionItems, items)
        studySessionItems = refreshedItems
        activeStudySessionItem = refreshedActiveStudySessionItem(activeStudySessionItem, refreshedItems)
        activeStudySessionOwned = activeStudySessionItem?.let { active ->
            refreshedItems.any { it.id == active.id }
        } == true
        studyPersistedBySourceId.clear()
        refreshedItems.forEach { item ->
            val key = item.sourceMessageId ?: item.id
            studyPersistedBySourceId[key] = item.id
        }
    }

    fun refreshStudySessions(silent: Boolean = false) {
        studySessionsJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = ChatApi.listStudySessions()
                studySessionsError = false
                applyStudySessionItems(response.items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                studySessionsError = studySessionItems.isEmpty()
                if (!silent) Toaster.show(strings.chatStudySessionsError)
            } finally {
                if (studySessionsJob === currentCoroutineContext()[Job]) {
                    studySessionsLoading = false
                    studySessionsJob = null
                }
            }
        }
        studySessionsJob = job
        studySessionsLoading = !silent || studySessionItems.isEmpty()
        studySessionsError = false
        job.start()
    }

    fun loadCommunitySessions(reset: Boolean = false) {
        if (!reset) {
            if (communitySessionsLoadingMore || communitySessionsLoading || communitySessionsJob?.isActive == true) return
            val cursor = communityNextCursor ?: return
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val page = ChatApi.listCommunityStudySessions(
                        cursor = cursor,
                        limit = STUDY_COMMUNITY_PAGE_SIZE,
                    )
                    communitySessionItems = mergeCommunityStudySessions(communitySessionItems, page.items)
                    communityNextCursor = page.nextCursor
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    Toaster.show(strings.chatStudySessionsError)
                } finally {
                    if (communitySessionsJob === currentCoroutineContext()[Job]) {
                        communitySessionsLoadingMore = false
                        communitySessionsJob = null
                    }
                }
            }
            communitySessionsJob = job
            communitySessionsLoadingMore = true
            job.start()
            return
        }
        communitySessionsJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = ChatApi.listCommunityStudySessions(limit = STUDY_COMMUNITY_PAGE_SIZE)
                communitySessionsError = false
                communitySessionItems = page.items
                communityNextCursor = page.nextCursor
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                communitySessionsError = communitySessionItems.isEmpty()
                Toaster.show(strings.chatStudySessionsError)
            } finally {
                if (communitySessionsJob === currentCoroutineContext()[Job]) {
                    communitySessionsLoading = false
                    communitySessionsLoadingMore = false
                    communitySessionsJob = null
                }
            }
        }
        communitySessionsJob = job
        communitySessionsLoading = communitySessionItems.isEmpty()
        communitySessionsError = false
        communitySessionsLoadingMore = false
        job.start()
    }

    fun persistStudySession(messageId: String, session: StudySessionResponse) {
        scope.launch {
            runCatching {
                withContext(NonCancellable) {
                    ChatApi.saveStudySession(
                        SaveStudySessionRequest(
                            sourceMessageId = messageId,
                            languageTag = studyLanguageByMessageId[messageId] ?: language.tag,
                            session = session,
                        ),
                    )
                }
            }.onSuccess { saved ->
                studyPersistedBySourceId[messageId] = saved.id
                val savedSummary = mergeStudySessionSummary(
                    current = studySessionItems.firstOrNull { it.id == saved.id },
                    incoming = saved,
                )
                studySessionItems = (listOf(savedSummary) + studySessionItems.filterNot {
                    it.id == saved.id || (saved.sourceMessageId != null && it.sourceMessageId == saved.sourceMessageId)
                }).sortedByDescending { it.updatedAt }
                if (studyTarget?.id == messageId) {
                    activeStudySessionItem = mergeStudySessionSummary(activeStudySessionItem, saved)
                    activeStudySessionOwned = true
                }
                if (studyTarget?.id == messageId && sessionId.isNullOrBlank()) {
                    onOpenSession(savedSummary.id)
                }
            }
        }
    }

    fun syncStudySentenceStatuses(session: StudySessionResponse) {
        studySentenceStatus.clear()
        session.sentences.forEachIndexed { index, sentence ->
            studySentenceStatus[index] = if (sentenceNeedsDissect(sentence)) {
                StudySentenceLoadState.LOADING
            } else {
                StudySentenceLoadState.READY
            }
        }
    }


    fun studyImageKey(messageId: String, sentenceIndex: Int): String = "$messageId#$sentenceIndex"

    fun clearStudyImageTracking(messageId: String) {
        val prefix = "$messageId#"
        studyImageInFlight.keys.filter { it.startsWith(prefix) }.forEach { studyImageInFlight.remove(it) }
        studyImageFailed.keys.filter { it.startsWith(prefix) }.forEach { studyImageFailed.remove(it) }
        studyWordImageInFlight.keys.filter { it.startsWith(prefix) }.forEach { studyWordImageInFlight.remove(it) }
        studyWordImageFailed.keys.filter { it.startsWith(prefix) }.forEach { studyWordImageFailed.remove(it) }
        studyCharactersInFlight.remove(messageId)
        studyPlayInFlight.remove(messageId)
        studyPlayFailed.remove(messageId)
        studyExamInFlight.remove(messageId)
        studyExamFailed.remove(messageId)
    }

    fun hydrateStudyPlayScenarios(scenarios: List<StudyPlayScenario>): List<StudyPlayScenario> =
        scenarios.map { scenario ->
            if (scenario.messages.isNotEmpty()) {
                scenario
            } else {
                scenario.copy(
                    messages = listOf(
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.OTHER,
                            text = scenario.openingLine.orEmpty(),
                            description = scenario.description,
                            imageId = scenario.imageId,
                            imageUrl = scenario.imageUrl,
                        ),
                    ),
                )
            }
        }

    fun applyPlayScenarios(messageId: String, scenarios: List<StudyPlayScenario>) {
        val current = studyCache[messageId] ?: return
        if (scenarios.isEmpty()) return
        val currentById = current.playScenarios.associateBy { it.id }
        val merged = hydrateStudyPlayScenarios(scenarios).map { incoming ->
            val existing = currentById[incoming.id] ?: return@map incoming
            incoming.copy(
                imageId = incoming.imageId?.takeIf { it.isNotBlank() } ?: existing.imageId,
                imageUrl = incoming.imageUrl?.takeIf { it.isNotBlank() } ?: existing.imageUrl,
                messages = if (existing.messages.size > incoming.messages.size) existing.messages else incoming.messages,
            )
        }
        val updated = current.copy(playScenarios = merged)
        studyCache[messageId] = updated
        studyPlayFailed.remove(messageId)
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
    }

    fun updatePlayScenario(
        messageId: String,
        scenarioId: String,
        transform: (StudyPlayScenario) -> StudyPlayScenario,
    ) {
        val current = studyCache[messageId] ?: return
        val index = current.playScenarios.indexOfFirst { it.id == scenarioId }
        if (index < 0) return
        val existing = current.playScenarios[index]
        val next = transform(existing)
        if (next == existing) return
        val updatedScenarios = current.playScenarios.toMutableList()
        updatedScenarios[index] = next
        val updated = current.copy(playScenarios = updatedScenarios)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
        persistStudySession(messageId, updated)
    }

    fun applyPlayMessageImage(
        messageId: String,
        scenarioId: String,
        imageId: String,
        imageUrl: String?,
    ) {
        val key = imageId.trim().takeIf { it.isNotEmpty() } ?: return
        val nextUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        updatePlayScenario(messageId, scenarioId) { scenario ->
            val messages = scenario.messages.toMutableList()
            val index = messages.indexOfLast {
                it.speaker == StudyPlaySpeaker.OTHER && it.imageId == key
            }
            if (index < 0) return@updatePlayScenario scenario
            val existing = messages[index]
            if (!existing.imageUrl.isNullOrBlank()) return@updatePlayScenario scenario
            messages[index] = existing.copy(imageId = key, imageUrl = nextUrl)
            // Keep scenario cover in sync when this is the opening beat.
            val coverUrl = if (index == 0) {
                scenario.imageUrl?.takeIf { it.isNotBlank() } ?: nextUrl
            } else {
                scenario.imageUrl
            }
            val coverId = if (index == 0) scenario.imageId ?: key else scenario.imageId
            scenario.copy(imageId = coverId, imageUrl = coverUrl, messages = messages)
        }
    }


    fun applyStudyCharacters(messageId: String, characters: List<StudyStoryCharacter>) {
        val current = studyCache[messageId] ?: return
        if (characters.isEmpty()) return
        // Merge by id so completed portrait URLs are never clobbered by older snapshots.
        val byId = linkedMapOf<String, StudyStoryCharacter>()
        current.characters.forEach { byId[it.id] = it }
        characters.forEach { incoming ->
            val existing = byId[incoming.id]
            if (existing == null) {
                byId[incoming.id] = incoming
                return@forEach
            }
            byId[incoming.id] = existing.copy(
                name = incoming.name.ifBlank { existing.name },
                role = incoming.role.ifBlank { existing.role },
                description = incoming.description.ifBlank { existing.description },
                imageId = incoming.imageId?.takeIf { it.isNotBlank() } ?: existing.imageId,
                // Never drop a completed portrait URL.
                imageUrl = existing.imageUrl?.takeIf { it.isNotBlank() }
                    ?: incoming.imageUrl?.takeIf { it.isNotBlank() },
            )
        }
        val merged = byId.values.toList()
        if (merged == current.characters) return
        val updated = current.copy(characters = merged)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
    }

    fun applyStudyTalkWorld(messageId: String, incoming: StudyTalkWorld) {
        val current = studyCache[messageId] ?: return
        val existing = current.talkWorld
        val oldScenes = existing?.scenes.orEmpty().associateBy { it.id }
        val oldExpressions = existing?.expressions.orEmpty().associateBy { it.id }
        val merged = incoming.copy(
            setting = incoming.setting.ifBlank { existing?.setting.orEmpty() },
            scenes = incoming.scenes.map { scene ->
                val oldScene = oldScenes[scene.id]
                scene.copy(
                    backgroundImageUrl = oldScene?.backgroundImageUrl?.takeIf(String::isNotBlank)
                        ?: scene.backgroundImageUrl,
                    turns = scene.turns.map { turn ->
                        val oldTurn = oldScene?.turns?.firstOrNull { it.id == turn.id }
                        turn.copy(
                            characterAudioUrl = oldTurn?.characterAudioUrl?.takeIf(String::isNotBlank)
                                ?: turn.characterAudioUrl,
                            replyAudioUrl = oldTurn?.replyAudioUrl?.takeIf(String::isNotBlank)
                                ?: turn.replyAudioUrl,
                        )
                    },
                )
            },
            expressions = incoming.expressions.map { expression ->
                expression.copy(
                    imageUrl = oldExpressions[expression.id]?.imageUrl?.takeIf(String::isNotBlank)
                        ?: expression.imageUrl,
                )
            },
        )
        if (merged == existing) return
        val updated = current.copy(talkWorld = merged)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) studySession = updated
    }


    fun applyStudySentenceImage(
        messageId: String,
        sentenceIndex: Int,
        imageId: String?,
        imageUrl: String?,
    ) {
        val current = studyCache[messageId] ?: return
        if (sentenceIndex !in current.sentences.indices) return
        val sentence = current.sentences[sentenceIndex]
        // Never regenerate or clobber a completed visual.
        if (!sentence.imageUrl.isNullOrBlank()) return
        val nextId = imageId?.trim()?.takeIf { it.isNotEmpty() } ?: sentence.imageId
        val nextUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (sentence.imageId == nextId && sentence.imageUrl == nextUrl) return
        val updatedSentences = current.sentences.toMutableList()
        updatedSentences[sentenceIndex] = sentence.copy(imageId = nextId, imageUrl = nextUrl)
        val updated = current.copy(sentences = updatedSentences)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
    }

    fun studyWordImageKey(messageId: String, sentenceIndex: Int, tokenIndex: Int): String =
        "$messageId#$sentenceIndex#$tokenIndex"

    fun applyStudyWordImage(
        messageId: String,
        sentenceIndex: Int,
        tokenIndex: Int,
        imageId: String?,
        imageUrl: String?,
    ) {
        val current = studyCache[messageId] ?: return
        if (sentenceIndex !in current.sentences.indices) return
        val sentence = current.sentences[sentenceIndex]
        if (tokenIndex !in sentence.tokens.indices) return
        val token = sentence.tokens[tokenIndex]
        // Never regenerate or clobber a completed word visual.
        if (!token.imageUrl.isNullOrBlank()) return
        val nextId = imageId?.trim()?.takeIf { it.isNotEmpty() } ?: token.imageId
        val nextUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (token.imageId == nextId && token.imageUrl == nextUrl) return
        val updatedTokens = sentence.tokens.toMutableList()
        updatedTokens[tokenIndex] = token.copy(imageId = nextId, imageUrl = nextUrl)
        val updatedSentences = current.sentences.toMutableList()
        updatedSentences[sentenceIndex] = sentence.copy(tokens = updatedTokens)
        val updated = current.copy(sentences = updatedSentences)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
    }





    fun applyExamScene(messageId: String, scene: StudyExamScene) {
        val current = studyCache[messageId]
            ?: studySession?.takeIf { studyTarget?.id == messageId }
            ?: return
        if (current.examScene?.isTenRoundExam() == true) return
        val updated = current.copy(examScene = scene)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) studySession = updated
    }


    fun replaceStudySession(messageId: String, session: StudySessionResponse) {
        studyCache[messageId] = session
        if (studyTarget?.id == messageId) studySession = session
    }

    fun updateSavedStudySummary(saved: SavedStudySession) {
        val incoming = SavedStudySessionSummary(
            id = saved.id,
            sourceMessageId = saved.sourceMessageId,
            languageTag = saved.languageTag,
            text = saved.text,
            textLanguageTag = saved.textLanguageTag,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
            sentenceImageUrls = saved.session.sentenceImageUrls(),
            name = saved.name,
            examGrade = saved.examGrade,
        )
        val current = studySessionItems.firstOrNull { it.id == saved.id }
            ?: communitySessionItems.firstOrNull { it.id == saved.id }
            ?: activeStudySessionItem?.takeIf { it.id == saved.id }
        val summary = mergeStudySessionSummary(current, incoming)
        if (activeStudySessionOwned || studySessionItems.any { it.id == saved.id }) {
            studySessionItems = (listOf(summary) + studySessionItems.filterNot { it.id == saved.id })
                .sortedByDescending { it.updatedAt }
        } else if (communitySessionItems.any { it.id == saved.id }) {
            communitySessionItems = communitySessionItems.map { if (it.id == saved.id) summary else it }
        }
        if (activeStudySessionItem?.id == saved.id || activeStudySessionOwned) {
            activeStudySessionItem = summary
        }
    }

    fun applyStudyBuildState(messageId: String, state: StudyBuildState) {
        studyBuildStateByMessageId[messageId] = state
        val prefix = "$messageId#"
        studyImageInFlight.keys.filter { it.startsWith(prefix) }.forEach { studyImageInFlight.remove(it) }
        studyImageFailed.keys.filter { it.startsWith(prefix) }.forEach { studyImageFailed.remove(it) }
        studyWordImageInFlight.keys.filter { it.startsWith(prefix) }.forEach { studyWordImageInFlight.remove(it) }
        studyWordImageFailed.keys.filter { it.startsWith(prefix) }.forEach { studyWordImageFailed.remove(it) }
        studyCharactersInFlight.remove(messageId)
        studyPlayInFlight.remove(messageId)
        studyPlayFailed.remove(messageId)
        studyExamInFlight.remove(messageId)
        studyExamFailed.remove(messageId)

        val session = studyCache[messageId]
        studySentenceStatus.clear()
        session?.sentences?.forEachIndexed { index, sentence ->
            studySentenceStatus[index] = if (sentenceNeedsDissect(sentence)) {
                StudySentenceLoadState.LOADING
            } else {
                StudySentenceLoadState.READY
            }
        }

        state.inFlight.forEach { element ->
            when (element.kind) {
                StudyBuildElementKind.DISSECT -> element.sentenceIndex?.let {
                    studySentenceStatus[it] = StudySentenceLoadState.LOADING
                }
                StudyBuildElementKind.WORD_IMAGE -> {
                    val sentenceIndex = element.sentenceIndex
                    val tokenIndex = element.tokenIndex
                    if (sentenceIndex != null && tokenIndex != null) {
                        studyWordImageInFlight[studyWordImageKey(messageId, sentenceIndex, tokenIndex)] = true
                    }
                }
                StudyBuildElementKind.SENTENCE_IMAGE -> element.sentenceIndex?.let {
                    studyImageInFlight[studyImageKey(messageId, it)] = true
                }
                StudyBuildElementKind.CHARACTERS,
                StudyBuildElementKind.PORTRAIT,
                -> studyCharactersInFlight[messageId] = true
                StudyBuildElementKind.PLAY_SCENARIOS,
                StudyBuildElementKind.PLAY_COVER,
                -> studyPlayInFlight[messageId] = true
                StudyBuildElementKind.EXAM -> studyExamInFlight[messageId] = true
                StudyBuildElementKind.TALK_WORLD,
                StudyBuildElementKind.TALK_SCENE_IMAGE,
                StudyBuildElementKind.TALK_EXPRESSION,
                StudyBuildElementKind.TALK_AUDIO,
                StudyBuildElementKind.PREPARE,
                StudyBuildElementKind.SKELETON,
                StudyBuildElementKind.NAME,
                StudyBuildElementKind.CHUNKS,
                StudyBuildElementKind.EXAMPLE_DISSECT,
                StudyBuildElementKind.WORD_RECALL_SCENARIOS,
                -> Unit
            }
        }
        state.failed.forEach { element ->
            when (element.kind) {
                StudyBuildElementKind.DISSECT -> element.sentenceIndex?.let {
                    studySentenceStatus[it] = StudySentenceLoadState.ERROR
                }
                StudyBuildElementKind.WORD_IMAGE -> {
                    val sentenceIndex = element.sentenceIndex
                    val tokenIndex = element.tokenIndex
                    if (sentenceIndex != null && tokenIndex != null) {
                        studyWordImageFailed[studyWordImageKey(messageId, sentenceIndex, tokenIndex)] = true
                    }
                }
                StudyBuildElementKind.SENTENCE_IMAGE -> element.sentenceIndex?.let {
                    studyImageFailed[studyImageKey(messageId, it)] = true
                }
                StudyBuildElementKind.PLAY_SCENARIOS,
                StudyBuildElementKind.PLAY_COVER,
                StudyBuildElementKind.CHARACTERS,
                StudyBuildElementKind.PORTRAIT,
                -> studyPlayFailed[messageId] = true
                StudyBuildElementKind.EXAM -> studyExamFailed[messageId] = true
                StudyBuildElementKind.TALK_WORLD,
                StudyBuildElementKind.TALK_SCENE_IMAGE,
                StudyBuildElementKind.TALK_EXPRESSION,
                StudyBuildElementKind.TALK_AUDIO,
                StudyBuildElementKind.PREPARE,
                StudyBuildElementKind.SKELETON,
                -> if (session?.sentences.isNullOrEmpty() && studyTarget?.id == messageId) {
                    studyError = strings.chatStudyError
                }
                StudyBuildElementKind.NAME,
                StudyBuildElementKind.CHUNKS,
                StudyBuildElementKind.EXAMPLE_DISSECT,
                StudyBuildElementKind.WORD_RECALL_SCENARIOS,
                -> Unit
            }
        }
        if (studyTarget?.id == messageId) {
            val hasSkeleton = !session?.sentences.isNullOrEmpty()
            val terminal = state.status == StudyBuildStatus.COMPLETE || state.status == StudyBuildStatus.PARTIAL
            studyLoading = !hasSkeleton && !terminal
            if (terminal) studyLoading = false
        }
    }

    fun applyStudyBuildEvent(messageId: String, event: StudyBuildEvent) {
        when (event) {
            is StudyBuildEvent.Snapshot -> {
                val previous = studyCache[messageId]
                val session = event.session.session
                val oldAdventureProgress = studyAdventureProgressBySessionId[event.session.id]
                    ?: studyAdventureProgressBySessionId[messageId]
                val adventureProgress = mergeAdventureProgress(oldAdventureProgress, event.session.adventureProgress)
                if (adventureProgress != null) {
                    studyAdventureProgressBySessionId[event.session.id] = adventureProgress
                    if (event.session.adventureProgress == null) {
                        scope.launch {
                            runCatching { ChatApi.saveStudyAdventureProgress(event.session.id, adventureProgress) }
                                .getOrNull()?.let { persisted ->
                                    studyAdventureProgressBySessionId[event.session.id] =
                                        mergeAdventureProgress(studyAdventureProgressBySessionId[event.session.id], persisted)
                                            ?: persisted
                                }
                        }
                    }
                }
                val savedSessionBreakdown = studyBreakdownProgressBySessionId[event.session.id]
                val oldBreakdownProgress = savedSessionBreakdown ?: studyBreakdownProgressBySessionId[messageId]
                val breakdownProgress = mergeStudyBreakdownProgress(oldBreakdownProgress, event.session.breakdownProgress)
                if (breakdownProgress != null) {
                    studyBreakdownProgressBySessionId[event.session.id] = breakdownProgress
                    if (event.session.breakdownProgress == null && savedSessionBreakdown == null) {
                        scope.launch {
                            runCatching { ChatApi.saveStudyBreakdownProgress(event.session.id, breakdownProgress) }
                                .getOrNull()?.let { persisted ->
                                    studyBreakdownProgressBySessionId[event.session.id] =
                                        mergeStudyBreakdownProgress(
                                            studyBreakdownProgressBySessionId[event.session.id],
                                            persisted,
                                        ) ?: persisted
                                }
                        }
                    }
                }
                val scenarios = hydrateStudyPlayScenarios(session.playScenarios).map { incoming ->
                    val existing = previous?.playScenarios?.firstOrNull { it.id == incoming.id }
                    if (existing != null && existing.messages.size > incoming.messages.size) {
                        incoming.copy(messages = existing.messages)
                    } else {
                        incoming
                    }
                }
                replaceStudySession(
                    messageId,
                    session.copy(
                        playScenarios = scenarios,
                        practiceSentences = if (previous != null && previous.practiceSentences.size > session.practiceSentences.size) {
                            previous.practiceSentences
                        } else {
                            session.practiceSentences
                        },
                    ),
                )
                studyPersistedBySourceId[messageId] = event.session.id
                studyLanguageByMessageId[messageId] = event.session.languageTag
                updateSavedStudySummary(event.session)
                val buildState = event.session.build
                if (buildState == null) {
                    studyBuildStateByMessageId.remove(messageId)
                    syncStudySentenceStatuses(event.session.session)
                    if (studyTarget?.id == messageId) studyLoading = false
                } else {
                    applyStudyBuildState(messageId, buildState)
                }
            }
            is StudyBuildEvent.Skeleton -> {
                val current = studyCache[messageId]
                replaceStudySession(
                    messageId,
                    event.session.copy(
                        practiceSentences = current?.practiceSentences.orEmpty(),
                        playScenarios = current?.playScenarios?.takeIf { it.isNotEmpty() }
                            ?: event.session.playScenarios,
                    ),
                )
                if (studyTarget?.id == messageId) studyLoading = false
                studyBuildStateByMessageId[messageId]?.let { applyStudyBuildState(messageId, it) }
            }
            is StudyBuildEvent.SentenceDissected -> {
                val current = studyCache[messageId] ?: return
                val existing = current.sentences.getOrNull(event.index) ?: return
                val tokens = event.sentence.tokens.mapIndexed { index, token ->
                    val old = existing.tokens.getOrNull(index)
                    if (old != null && old.text == token.text) {
                        token.copy(
                            imageId = old.imageId ?: token.imageId,
                            imageUrl = old.imageUrl ?: token.imageUrl,
                            needConversation = token.needConversation.ifEmpty { old.needConversation },
                        )
                    } else {
                        token
                    }
                }
                val sentences = current.sentences.toMutableList()
                sentences[event.index] = event.sentence.copy(
                    tokens = tokens,
                    imageId = existing.imageId ?: event.sentence.imageId,
                    imageUrl = existing.imageUrl ?: event.sentence.imageUrl,
                    chunks = event.sentence.chunks.ifEmpty { existing.chunks },
                    breakdownSuffixes = event.sentence.breakdownSuffixes.ifEmpty { existing.breakdownSuffixes },
                    breakdownAudioReady = event.sentence.breakdownAudioReady || existing.breakdownAudioReady,
                )
                replaceStudySession(messageId, current.copy(sentences = sentences))
                studySentenceStatus[event.index] = StudySentenceLoadState.READY
            }
            is StudyBuildEvent.WordImage -> applyStudyWordImage(
                messageId,
                event.sentenceIndex,
                event.tokenIndex,
                imageId = null,
                imageUrl = event.imageUrl,
            )
            is StudyBuildEvent.SentenceImage -> applyStudySentenceImage(
                messageId,
                event.index,
                imageId = null,
                imageUrl = event.imageUrl,
            )
            is StudyBuildEvent.Characters -> applyStudyCharacters(messageId, event.characters)
            is StudyBuildEvent.PlayScenarios -> applyPlayScenarios(messageId, event.scenarios)
            is StudyBuildEvent.ExamScene -> applyExamScene(messageId, event.scene)
            is StudyBuildEvent.TalkWorld -> applyStudyTalkWorld(messageId, event.world)
            is StudyBuildEvent.Renamed -> {
                val id = studyPersistedBySourceId[messageId] ?: return
                studySessionItems = studySessionItems.map { if (it.id == id) it.copy(name = event.name) else it }
                communitySessionItems = communitySessionItems.map { if (it.id == id) it.copy(name = event.name) else it }
                if (activeStudySessionItem?.id == id) activeStudySessionItem = activeStudySessionItem?.copy(name = event.name)
            }
            is StudyBuildEvent.Build -> applyStudyBuildState(messageId, event.state)
            StudyBuildEvent.Heartbeat -> Unit
            StudyBuildEvent.Done -> {
                if (studyTarget?.id == messageId) studyLoading = false
                refreshStudySessions(silent = true)
                if (!activeStudySessionOwned) loadCommunitySessions(reset = true)
            }
        }
    }

    fun subscribeToStudyBuild(messageId: String, sessionId: String) {
        studyBuildStreamJob?.cancel()
        studyBuildStreamJob = scope.launch {
            try {
                ChatApi.studySessionEvents(sessionId) { event ->
                    if (studyTarget?.id == messageId && studySessionVisible) {
                        applyStudyBuildEvent(messageId, event)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (studyTarget?.id == messageId) {
                    studyError = strings.chatStudyError
                    studyLoading = false
                }
            }
        }
    }

    fun retryTalkBuildAndSubscribe(messageId: String) {
        val sessionId = studyPersistedBySourceId[messageId] ?: return
        val session = studyCache[messageId]
            ?: studySession?.takeIf { studyTarget?.id == messageId }
        val failedTalk = studyBuildStateByMessageId[messageId]?.failed.orEmpty()
            .filter { it.kind.isStudyTalkKind() }
        val elements = (failedTalk + session?.talkWorld.missingTalkBuildElements().orEmpty())
            .distinctBy { it.key }
        if (elements.isEmpty()) {
            subscribeToStudyBuild(messageId, sessionId)
            return
        }
        scope.launch {
            runCatching { ChatApi.retryStudyBuild(sessionId, elements) }
                .onSuccess { build ->
                    if (studyTarget?.id == messageId) {
                        applyStudyBuildState(messageId, build)
                        if (studySessionVisible) subscribeToStudyBuild(messageId, sessionId)
                    }
                }
                .onFailure {
                    if (studyTarget?.id == messageId) studyError = strings.chatStudyError
                }
        }
    }

    fun retryStudyBuildElements(messageId: String, elements: List<StudyBuildElement> = emptyList()) {
        val sessionId = studyPersistedBySourceId[messageId] ?: return
        scope.launch {
            runCatching { ChatApi.retryStudyBuild(sessionId, elements) }
                .onSuccess { applyStudyBuildState(messageId, it) }
                .onFailure { if (studyTarget?.id == messageId) studyError = strings.chatStudyError }
        }
    }

    fun openStudy(
        message: ChatMessage,
        learnerLanguageTag: String = language.tag,
        buildMode: StudyBuildMode = StudyBuildMode.EXACT,
        targetLanguageTag: String? = null,
        sourceLanguageTag: String? = null,
        existingSession: SavedStudySession? = null,
    ) {
        val text = studyTextFromChatMessage(message)
        if (text.isEmpty()) return
        val mode = buildMode
        studyBuildStreamJob?.cancel()
        studyBuildStreamJob = null
        studyTarget = message
        studySessionVisible = true
        studyError = null
        clearStudyImageTracking(message.id)
        studyLanguageByMessageId[message.id] = learnerLanguageTag
        studyStartExact = mode != StudyBuildMode.INTENT
        if (existingSession != null) {
            applyStudyBuildEvent(message.id, StudyBuildEvent.Snapshot(existingSession))
            studyPersistedBySourceId[message.id] = existingSession.id
            subscribeToStudyBuild(message.id, existingSession.id)
            return
        }
        activeStudySessionItem = null
        activeStudySessionOwned = false
        studyCache.remove(message.id)
        // Provisional state until the create response arrives, so prep shows as running.
        studyBuildStateByMessageId[message.id] = StudyBuildState(
            status = if (mode == StudyBuildMode.EXACT) StudyBuildStatus.BUILDING else StudyBuildStatus.PREPARING,
        )
        studySession = null
        studySentenceStatus.clear()
        studyLoading = true
        scope.launch {
            runCatching {
                ChatApi.createStudySession(
                    StudyBuildRequest(
                        text = text,
                        languageTag = learnerLanguageTag,
                        sourceMessageId = message.id,
                        mode = mode,
                        targetLanguageTag = targetLanguageTag,
                        sourceLanguageTag = sourceLanguageTag,
                    ),
                )
            }.onSuccess { saved ->
                studyPersistedBySourceId[message.id] = saved.id
                if (studyTarget?.id == message.id) {
                    activeStudySessionOwned = true
                    applyStudyBuildEvent(message.id, StudyBuildEvent.Snapshot(saved))
                    studyError = null
                    if (studySessionVisible) {
                        subscribeToStudyBuild(message.id, saved.id)
                        if (sessionId.isNullOrBlank()) onOpenSession(saved.id)
                    }
                }
            }.onFailure {
                if (studyTarget?.id == message.id) {
                    studyError = strings.chatStudyError
                    studyLoading = false
                }
            }
        }
    }

    fun openSavedStudySession(item: SavedStudySession, fromCommunity: Boolean = false) {
        val key = if (fromCommunity) item.id else (item.sourceMessageId ?: item.id)
        val restoredProgress = mergeAdventureProgress(studyAdventureProgressBySessionId[item.id], item.adventureProgress)
        if (restoredProgress != null) {
            studyAdventureProgressBySessionId[item.id] = restoredProgress
            studyAdventureProgressBySessionId[key] = mergeAdventureProgress(
                studyAdventureProgressBySessionId[key],
                restoredProgress,
            ) ?: restoredProgress
        }
        mergeStudyBreakdownProgress(studyBreakdownProgressBySessionId[item.id], item.breakdownProgress)?.let { progress ->
            studyBreakdownProgressBySessionId[item.id] = progress
            studyBreakdownProgressBySessionId[key] =
                mergeStudyBreakdownProgress(studyBreakdownProgressBySessionId[key], progress) ?: progress
        }
        activeStudySessionItem = studySessionItems.firstOrNull { it.id == item.id }
            ?: communitySessionItems.firstOrNull { it.id == item.id }
            ?: SavedStudySessionSummary(
                id = item.id,
                sourceMessageId = item.sourceMessageId,
                languageTag = item.languageTag,
                text = item.text,
                textLanguageTag = item.textLanguageTag,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                sentenceImageUrls = item.session.sentenceImageUrls(),
                name = item.name,
                examGrade = item.examGrade,
            )
        activeStudySessionOwned = !fromCommunity && studySessionItems.any { it.id == item.id }
        studyStartExact = true
        openStudy(
            ChatMessage(
                id = key,
                sender = ChatSender.AI,
                text = item.session.text,
                createdAt = item.createdAt,
            ),
            learnerLanguageTag = item.languageTag,
            buildMode = StudyBuildMode.EXACT,
            existingSession = item,
        )
    }

    fun appendPracticeSentences(messageId: String, extras: List<StudyExampleSentence>) {
        val current = studyCache[messageId] ?: studySession?.takeIf { studyTarget?.id == messageId } ?: return
        val merged = mergePracticeSentences(current.practiceSentences, extras)
        if (merged == current.practiceSentences) return
        val updated = current.copy(practiceSentences = merged)
        studyCache[messageId] = updated
        persistStudySession(messageId, updated)
        if (studyTarget?.id == messageId) studySession = updated
    }

    fun startStudyInAnotherLanguage(rawLanguage: String) {
        val session = studySession
        val text = session?.text?.trim().orEmpty().ifEmpty {
            studyTarget?.let(::studyTextFromChatMessage).orEmpty()
        }
        if (text.isEmpty()) return
        pendingStudyLanguageClone = false
        activeStudySessionItem = null
        activeStudySessionOwned = false
        studyStartExact = true
        val message = ChatMessage(
            id = "study-${Date.now().toLong()}",
            sender = ChatSender.YOU,
            text = text,
            createdAt = Date.now().toLong(),
        )
        val targetLanguageTag = Language.normalizeLearnerLanguageTag(rawLanguage)
        openStudy(
            message,
            learnerLanguageTag = language.tag,
            buildMode = StudyBuildMode.TRANSLATE,
            targetLanguageTag = targetLanguageTag,
            sourceLanguageTag = session?.textLanguageTag,
        )
    }

    fun startStudyFromText(raw: String?, exact: Boolean = true) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        activeStudySessionItem = null
        activeStudySessionOwned = false
        studyStartExact = exact
        openStudy(
            ChatMessage(
                id = "study-${Date.now().toLong()}",
                sender = ChatSender.YOU,
                text = text,
                createdAt = Date.now().toLong(),
            ),
            buildMode = if (exact) StudyBuildMode.EXACT else StudyBuildMode.INTENT,
        )
    }

    fun closeStudy() {
        studyBuildStreamJob?.cancel()
        studyBuildStreamJob = null
        studySessionVisible = false
        activeStudySessionItem = null
        activeStudySessionOwned = false
        pendingStudySessionRename = null
        stopActiveStudyAudio()
        if (!sessionId.isNullOrBlank()) {
            onCloseSession()
        }
    }

    fun removeStudySession(item: SavedStudySessionSummary) {
        scope.launch {
            runCatching { ChatApi.deleteStudySession(item.id) }
                .onSuccess {
                    studySessionItems = studySessionItems.filterNot { it.id == item.id }
                    val keysToRemove = studyPersistedBySourceId.filterValues { it == item.id }.keys.toList()
                    keysToRemove.forEach { key ->
                        studyPersistedBySourceId.remove(key)
                        studyCache.remove(key)
                    }
                    Toaster.show(strings.removedToast)
                    if (sessionId == item.id) {
                        closeStudy()
                    }
                }
                .onFailure { Toaster.show(strings.chatStudySessionsError) }
        }
    }

    fun renameStudySession(item: SavedStudySessionSummary, name: String) {
        scope.launch {
            runCatching { ChatApi.renameStudySession(item.id, name) }
                .onSuccess { renamed ->
                    studySessionItems = studySessionItems.map {
                        if (it.id == renamed.id) mergeStudySessionSummary(it, renamed) else it
                    }
                    communitySessionItems = communitySessionItems.map {
                        if (it.id == renamed.id) mergeStudySessionSummary(it, renamed) else it
                    }
                    if (activeStudySessionItem?.id == renamed.id) {
                        activeStudySessionItem = mergeStudySessionSummary(activeStudySessionItem, renamed)
                    }
                    refreshStudySessions(silent = true)
                }
                .onFailure { Toaster.show(strings.chatStudySessionsError) }
        }
    }

    fun retryStudySentence(sentenceIndex: Int) {
        val messageId = studyTarget?.id ?: return
        retryStudyBuildElements(
            messageId,
            listOf(StudyBuildElement(StudyBuildElementKind.DISSECT, sentenceIndex = sentenceIndex)),
        )
    }

    fun retryStudySentenceImage(sentenceIndex: Int) {
        val messageId = studyTarget?.id ?: return
        val key = studyImageKey(messageId, sentenceIndex)
        if (studyImageInFlight[key] == true) return
        retryStudyBuildElements(
            messageId,
            listOf(StudyBuildElement(StudyBuildElementKind.SENTENCE_IMAGE, sentenceIndex = sentenceIndex)),
        )
    }

    fun retryStudyWordImage(sentenceIndex: Int, tokenIndex: Int) {
        val messageId = studyTarget?.id ?: return
        val key = studyWordImageKey(messageId, sentenceIndex, tokenIndex)
        if (studyWordImageInFlight[key] == true) return
        retryStudyBuildElements(
            messageId,
            listOf(
                StudyBuildElement(
                    StudyBuildElementKind.WORD_IMAGE,
                    sentenceIndex = sentenceIndex,
                    tokenIndex = tokenIndex,
                ),
            ),
        )
    }


    val latestStudySessionVisible by rememberUpdatedState(studySessionVisible)
    val latestStudySessionsError by rememberUpdatedState(studySessionsError)
    val latestCommunitySessionsError by rememberUpdatedState(communitySessionsError)

    DisposableEffect(Unit) {
        val onPageShow: (Event) -> Unit = { event ->
            if (event.asDynamic().persisted == true) {
                refreshStudySessions(silent = true)
                if (!latestStudySessionVisible) {
                    loadCommunitySessions(reset = true)
                }
            }
        }
        val onOnline: (Event) -> Unit = {
            if (!latestStudySessionVisible && (latestStudySessionsError || latestCommunitySessionsError)) {
                refreshStudySessions(silent = true)
                loadCommunitySessions(reset = true)
            }
        }
        window.addEventListener("pageshow", onPageShow)
        window.addEventListener("online", onOnline)
        onDispose {
            window.removeEventListener("pageshow", onPageShow)
            window.removeEventListener("online", onOnline)
        }
    }

    LaunchedEffect(studySessionVisible) {
        if (!studySessionVisible) {
            refreshStudySessions(silent = true)
            loadCommunitySessions(reset = true)
        }
    }

    LaunchedEffect(language.tag) {
        // Definitions are localized to the active UI language, so drop stale sessions.
        studyCache.clear()
        studySentenceStatus.clear()
        studyLanguageByMessageId.clear()
        if (studySessionVisible) {
            studyTarget?.let(::openStudy)
        } else {
            studySession = null
        }
    }


    LaunchedEffect(sessionId) {
        val id = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (studySessionVisible && (studyPersistedBySourceId[studyTarget?.id] == id || studyTarget?.id == id)) {
            return@LaunchedEffect
        }
        studySessionVisible = true
        studyLoading = true
        studyError = null
        refreshStudySessions(silent = true)
        try {
            val item = ChatApi.getStudySession(id)
            openSavedStudySession(
                item,
                fromCommunity = studySessionItems.none { owned -> owned.id == item.id },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            studyLoading = false
            studySessionVisible = false
            Toaster.show(strings.chatStudySessionsError)
            onCloseSession()
        }
    }

    LaunchedEffect(initialText, sessionId) {
        if (!sessionId.isNullOrBlank()) return@LaunchedEffect
        initialText?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            onInitialTextConsumed()
            startStudyFromText(text)
        }
    }

    Scaffold(
        title = if (studySessionVisible) {
            activeStudySessionItem?.let(::studySessionDisplayName)?.takeIf { it.isNotBlank() }
                ?: strings.chatStudyTitle
        } else {
            strings.studyTitle
        },
        onBack = {
            if (studySessionVisible) closeStudy() else onBack()
        },
        onHome = onHome,
        appBarAction = if (studySessionVisible) {
            {
                activeStudySessionItem?.examGrade?.let { grade ->
                    val summary = "${grade.overall}/5"
                    Span(attrs = {
                        classes(Styles.studySessionHeaderExamGradeBadge)
                        attr("aria-label", "${strings.chatTestGradeLabel}: $summary")
                    }) { Text(summary) }
                }
                Span(attrs = {
                    classes(Styles.materialSymbols, Styles.appBarIcon)
                    attr("role", "button")
                    attr("aria-label", strings.chatStudyAnotherLanguage)
                    onClick { pendingStudyLanguageClone = true }
                }) { Text("translate") }
                if (activeStudySessionOwned && activeStudySessionItem != null) {
                    Span(attrs = {
                        classes(Styles.materialSymbols, Styles.appBarIcon)
                        attr("role", "button")
                        attr("aria-label", strings.chatStudySessionsRename)
                        onClick { pendingStudySessionRename = activeStudySessionItem }
                    }) { Text("edit") }
                }
            }
        } else {
            null
        },
    ) {
        if (studySessionVisible) {
            StudySessionPane(
                loading = studyLoading,
                error = studyError,
                session = studySession,
                sentenceStatus = studySentenceStatus.toMap(),
                messageId = studyTarget?.id,
                examScoreSessionId = activeStudySessionItem?.id
                    ?: studyTarget?.id?.let { studyPersistedBySourceId[it] },
                savedExamGrade = activeStudySessionItem?.examGrade,
                adventureProgress = activeStudySessionItem?.id?.let { studyAdventureProgressBySessionId[it] }
                    ?: studyTarget?.id?.let { targetId ->
                        studyPersistedBySourceId[targetId]?.let { studyAdventureProgressBySessionId[it] }
                            ?: studyAdventureProgressBySessionId[targetId]
                    },
                breakdownProgress = activeStudySessionItem?.id?.let { studyBreakdownProgressBySessionId[it] }
                    ?: studyTarget?.id?.let { targetId ->
                        studyPersistedBySourceId[targetId]?.let { studyBreakdownProgressBySessionId[it] }
                            ?: studyBreakdownProgressBySessionId[targetId]
                    },
                onBreakdownProgress = { incoming ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    val savedId = activeStudySessionItem?.id ?: studyPersistedBySourceId[targetId]
                    val key = savedId ?: targetId
                    val merged = mergeStudyBreakdownProgress(studyBreakdownProgressBySessionId[key], incoming)
                        ?: incoming
                    studyBreakdownProgressBySessionId[key] = merged
                    if (savedId != null) {
                        scope.launch {
                            runCatching { ChatApi.saveStudyBreakdownProgress(savedId, merged) }
                                .getOrNull()?.let { persisted ->
                                    studyBreakdownProgressBySessionId[savedId] =
                                        mergeStudyBreakdownProgress(studyBreakdownProgressBySessionId[savedId], persisted)
                                            ?: persisted
                                }
                        }
                    }
                },
                onAdventureProgress = { incoming ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    val savedId = activeStudySessionItem?.id ?: studyPersistedBySourceId[targetId]
                    val key = savedId ?: targetId
                    val merged = mergeAdventureProgress(studyAdventureProgressBySessionId[key], incoming) ?: incoming
                    studyAdventureProgressBySessionId[key] = merged
                    if (savedId != null) {
                        scope.launch {
                            runCatching { ChatApi.saveStudyAdventureProgress(savedId, merged) }
                                .getOrNull()?.let { persisted ->
                                    studyAdventureProgressBySessionId[savedId] =
                                        mergeAdventureProgress(studyAdventureProgressBySessionId[savedId], persisted)
                                            ?: persisted
                                }
                        }
                    }
                },
                imageFailed = studyImageFailed.toMap(),
                wordImageFailed = studyWordImageFailed.toMap(),
                charactersLoading = studyTarget?.id?.let { studyCharactersInFlight[it] == true } == true,
                examFailed = studyTarget?.id?.let { studyExamFailed[it] == true } == true,
                talkBuildState = studyTarget?.id?.let { studyBuildStateByMessageId[it] },
                talkFailed = studyTarget?.id?.let { targetId ->
                    val build = studyBuildStateByMessageId[targetId]
                    val world = studyCache[targetId]?.talkWorld
                        ?: studySession?.takeIf { studyTarget?.id == targetId }?.talkWorld
                    val terminal = build?.status == StudyBuildStatus.COMPLETE || build?.status == StudyBuildStatus.PARTIAL
                    build?.failed.orEmpty().any { it.kind.isStudyTalkKind() } ||
                            (terminal && world?.hasAllTalkAssets() != true)
                } == true,
                talkCanRetry = studyTarget?.id?.let { targetId ->
                    val build = studyBuildStateByMessageId[targetId]
                    val world = studyCache[targetId]?.talkWorld
                        ?: studySession?.takeIf { studyTarget?.id == targetId }?.talkWorld
                    val terminal = build?.status == StudyBuildStatus.COMPLETE || build?.status == StudyBuildStatus.PARTIAL
                    build?.failed.orEmpty().any { it.kind.isStudyTalkKind() } ||
                            (terminal && world.missingTalkBuildElements().isNotEmpty())
                } == true,
                exactText = studyStartExact,
                buildStatus = studyTarget?.id?.let { studyBuildStateByMessageId[it]?.status },
                playLoading = studyTarget?.id?.let { studyPlayInFlight[it] == true } == true,
                playFailed = studyTarget?.id?.let { studyPlayFailed[it] == true } == true,
                strings = strings,
                onRetry = {
                    val target = studyTarget ?: return@StudySessionPane
                    if (!studyBuildStateByMessageId[target.id]?.failed.isNullOrEmpty()) {
                        retryStudyBuildElements(target.id)
                    } else {
                        openStudy(
                            target,
                            buildMode = if (studyStartExact) StudyBuildMode.EXACT else StudyBuildMode.INTENT,
                        )
                    }
                },
                onRetrySentence = ::retryStudySentence,
                onRetryImage = ::retryStudySentenceImage,
                onRetryWordImage = ::retryStudyWordImage,
                onRetryPlay = {
                    val target = studyTarget ?: return@StudySessionPane
                    val failed = studyBuildStateByMessageId[target.id]?.failed.orEmpty()
                        .filter {
                            it.kind == StudyBuildElementKind.PLAY_SCENARIOS ||
                                it.kind == StudyBuildElementKind.PLAY_COVER ||
                                it.kind == StudyBuildElementKind.CHARACTERS ||
                                it.kind == StudyBuildElementKind.PORTRAIT
                        }
                    retryStudyBuildElements(target.id, failed)
                },
                onRetryExam = {
                    val target = studyTarget ?: return@StudySessionPane
                    retryStudyBuildElements(
                        target.id,
                        listOf(StudyBuildElement(StudyBuildElementKind.EXAM)),
                    )
                },
                onRetryTalk = {
                    val target = studyTarget ?: return@StudySessionPane
                    retryTalkBuildAndSubscribe(target.id)
                },
                onOpenTalk = {
                    val target = studyTarget ?: return@StudySessionPane
                    retryTalkBuildAndSubscribe(target.id)
                },
                onPlayScenarioUpdate = { scenarioId, transform ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    updatePlayScenario(targetId, scenarioId, transform)
                },
                onPlayMessageImage = { scenarioId, imageId, imageUrl ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    applyPlayMessageImage(targetId, scenarioId, imageId, imageUrl)
                },
                onPracticeSentences = { extras ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    appendPracticeSentences(targetId, extras)
                },
                onExamScoreFinished = { grade ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    val savedId = activeStudySessionItem?.id
                        ?: studyPersistedBySourceId[targetId]
                        ?: return@StudySessionPane
                    val current = activeStudySessionItem?.takeIf { it.id == savedId }
                        ?: studySessionItems.firstOrNull { it.id == savedId }
                        ?: communitySessionItems.firstOrNull { it.id == savedId }
                        ?: return@StudySessionPane
                    val updated = current.copy(examGrade = grade, updatedAt = grade.scoredAt)
                    activeStudySessionItem = updated
                    studySessionItems = studySessionItems.map { item ->
                        if (item.id == savedId) updated else item
                    }
                    communitySessionItems = communitySessionItems.map { item ->
                        if (item.id == savedId) updated else item
                    }
                },
            )
        } else {
            StudySessionsList(
                items = studySessionItems,
                loading = studySessionsLoading,
                error = studySessionsError,
                communityItems = communitySessionItems,
                communityLoading = communitySessionsLoading,
                communityError = communitySessionsError,
                communityLoadingMore = communitySessionsLoadingMore,
                communityHasMore = communityNextCursor != null,
                strings = strings,
                onStart = ::startStudyFromText,
                onOpen = { onOpenSession(it.id) },
                onOpenCommunity = { onOpenSession(it.id) },
                onRemove = ::removeStudySession,
                onRename = { pendingStudySessionRename = it },
                onLoadMoreCommunity = { loadCommunitySessions() },
                onRetryLoad = {
                    refreshStudySessions()
                    loadCommunitySessions(reset = true)
                },
            )
        }
    }
    StudySessionRenameDialog(
        item = pendingStudySessionRename,
        strings = strings,
        onDismiss = { pendingStudySessionRename = null },
        onConfirm = { item, name ->
            pendingStudySessionRename = null
            renameStudySession(item, name)
        },
    )
    StudySessionLanguageDialog(
        visible = pendingStudyLanguageClone,
        strings = strings,
        onDismiss = { pendingStudyLanguageClone = false },
        onConfirm = ::startStudyInAnotherLanguage,
    )
}


@Composable
private fun StudySessionsList(
    items: List<SavedStudySessionSummary>,
    loading: Boolean,
    error: Boolean,
    communityItems: List<SavedStudySessionSummary>,
    communityLoading: Boolean,
    communityError: Boolean,
    communityLoadingMore: Boolean,
    communityHasMore: Boolean,
    strings: UiStrings,
    onStart: (text: String?, exact: Boolean) -> Unit,
    onOpen: (SavedStudySessionSummary) -> Unit,
    onOpenCommunity: (SavedStudySessionSummary) -> Unit,
    onRemove: (SavedStudySessionSummary) -> Unit,
    onRename: (SavedStudySessionSummary) -> Unit,
    onLoadMoreCommunity: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var exact by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<SavedStudySessionSummary?>(null) }
    var filter by remember { mutableStateOf(StudySessionsFilter.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sourceLanguageTag by remember { mutableStateOf("") }
    var targetLanguageTag by remember { mutableStateOf("") }
    val loadMoreCommunity by rememberUpdatedState(onLoadMoreCommunity)
    val searchLanguageTags = (items + communityItems)
        .flatMap { listOf(it.textLanguageTag, it.languageTag) }
        .map(Language::normalizeContentTag)
        .distinct()
        .sorted()

    Div(attrs = { classes(Styles.savedPanelBody) }) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.studyPrompt)
        }
        ChatInput(
            value = draft,
            onValueChange = { draft = it },
            onSend = { value ->
                val text = value?.trim().orEmpty()
                if (text.isEmpty()) return@ChatInput
                draft = ""
                onStart(text, exact)
            },
            placeholder = strings.chatStudySessionsPlaceholder,
            multiline = true,
            autoFocus = true,
        )
        Label(attrs = { classes(Styles.studyExactRow) }) {
            Input(type = InputType.Checkbox) {
                if (exact) attr("checked", "true")
                attr("aria-label", strings.chatStudyExactToggle)
                onChange {
                    val el = it.nativeEvent.target as? HTMLInputElement
                    exact = el?.checked == true
                }
            }
            Text(strings.chatStudyExactToggle)
        }
        Div(attrs = { classes(Styles.studyModeTabs) }) {
            Button(attrs = {
                classes(Styles.chip)
                if (filter == StudySessionsFilter.ALL) classes(Styles.chipActive)
                attr("type", "button")
                attr("aria-pressed", (filter == StudySessionsFilter.ALL).toString())
                onClick { filter = StudySessionsFilter.ALL }
            }) { Text(strings.chatAllTab) }
            Button(attrs = {
                classes(Styles.chip)
                if (filter == StudySessionsFilter.MINE) classes(Styles.chipActive)
                attr("type", "button")
                attr("aria-pressed", (filter == StudySessionsFilter.MINE).toString())
                onClick { filter = StudySessionsFilter.MINE }
            }) { Text(strings.chatStudySessionsMine) }
            Button(attrs = {
                classes(Styles.chip)
                if (filter == StudySessionsFilter.COMMUNITY) classes(Styles.chipActive)
                attr("type", "button")
                attr("aria-pressed", (filter == StudySessionsFilter.COMMUNITY).toString())
                onClick { filter = StudySessionsFilter.COMMUNITY }
            }) { Text(strings.chatStudySessionsCommunity) }
            Button(attrs = {
                classes(Styles.studySearchToggle)
                if (searchOpen || searchQuery.isNotBlank() || sourceLanguageTag.isNotBlank() || targetLanguageTag.isNotBlank()) {
                    classes(Styles.chipActive)
                }
                attr("type", "button")
                attr("aria-label", strings.chatStudySearchAria)
                attr("aria-expanded", searchOpen.toString())
                onClick { searchOpen = !searchOpen }
            }) {
                MaterialSymbol("search")
            }
        }
        if (searchOpen) {
            Div(attrs = { classes(Styles.studySearchPanel) }) {
                Div(attrs = { classes(Styles.studySearchFields) }) {
                    Label(attrs = { classes(Styles.studySearchField) }) {
                        Span { Text(strings.chatStudySearchTitle) }
                        Input(type = InputType.Text) {
                            classes(Styles.textInput)
                            attr("type", "search")
                            attr("aria-label", strings.chatStudySearchPlaceholder)
                            placeholder(strings.chatStudySearchPlaceholder)
                            value(searchQuery)
                            onInput { searchQuery = it.value }
                        }
                    }
                    Label(attrs = { classes(Styles.studySearchField) }) {
                        Span { Text(strings.chatStudySearchSource) }
                        Select(attrs = {
                            classes(Styles.textInput)
                            onChange {
                                val element = it.nativeEvent.target as? HTMLSelectElement
                                sourceLanguageTag = element?.value.orEmpty()
                            }
                        }) {
                            Option("", attrs = { if (sourceLanguageTag.isBlank()) selected() }) {
                                Text(strings.chatStudySearchAllLanguages)
                            }
                            searchLanguageTags.forEach { tag ->
                                Option(tag, attrs = { if (sourceLanguageTag == tag) selected() }) {
                                    Text(Language.entries.firstOrNull { it.tag == tag }?.displayName ?: tag)
                                }
                            }
                        }
                    }
                    Label(attrs = { classes(Styles.studySearchField) }) {
                        Span { Text(strings.chatStudySearchTarget) }
                        Select(attrs = {
                            classes(Styles.textInput)
                            onChange {
                                val element = it.nativeEvent.target as? HTMLSelectElement
                                targetLanguageTag = element?.value.orEmpty()
                            }
                        }) {
                            Option("", attrs = { if (targetLanguageTag.isBlank()) selected() }) {
                                Text(strings.chatStudySearchAllLanguages)
                            }
                            searchLanguageTags.forEach { tag ->
                                Option(tag, attrs = { if (targetLanguageTag == tag) selected() }) {
                                    Text(Language.entries.firstOrNull { it.tag == tag }?.displayName ?: tag)
                                }
                            }
                        }
                    }
                }
            }
        }
        val visibleItems = studySessionsForSearch(
            entries = studySessionsForFilter(items, communityItems, filter),
            query = searchQuery,
            sourceLanguageTag = sourceLanguageTag,
            targetLanguageTag = targetLanguageTag,
        )
        val hasActiveSearch = searchQuery.isNotBlank() || sourceLanguageTag.isNotBlank() || targetLanguageTag.isNotBlank()
        val viewState = studySessionsListViewState(
            filter = filter,
            visibleItemsEmpty = visibleItems.isEmpty(),
            hasActiveSearch = hasActiveSearch,
            loading = loading,
            error = error,
            communityLoading = communityLoading,
            communityError = communityError,
        )
        when (viewState) {
            StudySessionsListViewState.LOADING -> {
                Div(attrs = { classes(Styles.chatStatus) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatThinking)
                }
            }
            StudySessionsListViewState.ERROR -> {
                Div(attrs = { classes(Styles.studySessionError) }) {
                    MaterialSymbol("error")
                    P { Text(strings.chatStudySessionsError) }
                    Button(attrs = {
                        classes(Styles.savedItemAction)
                        attr("type", "button")
                        onClick { onRetryLoad() }
                    }) {
                        MaterialSymbol("refresh")
                        Text(strings.chatTestRetry)
                    }
                }
            }
            StudySessionsListViewState.EMPTY -> {
                Div(attrs = { classes(Styles.savedPanelEmpty) }) {
                    MaterialSymbol(
                        when {
                            hasActiveSearch -> "search_off"
                            filter == StudySessionsFilter.COMMUNITY -> "groups"
                            else -> "menu_book"
                        },
                    )
                    P {
                        Text(
                            when {
                                hasActiveSearch -> strings.chatStudySearchNoResults
                                filter == StudySessionsFilter.COMMUNITY -> strings.chatStudySessionsCommunityEmpty
                                else -> strings.chatStudySessionsEmpty
                            },
                        )
                    }
                }
            }
            StudySessionsListViewState.CONTENT -> visibleItems.forEach { entry ->
                StudySessionCard(
                    item = entry.item,
                    strings = strings,
                    removable = entry.removable,
                    onOpen = {
                        if (entry.removable) onOpen(entry.item) else onOpenCommunity(entry.item)
                    },
                    onRemove = { if (entry.removable) pendingRemove = entry.item },
                    onRename = { if (entry.removable) onRename(entry.item) },
                )
            }
        }
        val showCommunityPaging = filter != StudySessionsFilter.MINE
        if (showCommunityPaging && communityLoadingMore) {
            Div(attrs = { classes(Styles.chatStatus) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatThinking)
            }
        }
        if (showCommunityPaging && communityHasMore) {
            key(communityItems.size) {
                Div(attrs = {
                    classes(Styles.serviceFeedSentinel)
                    ref { element ->
                        val root = document.querySelector(".${Styles.content}") as? Element
                        val observer = observeStudyListNearViewport(element, root) {
                            loadMoreCommunity()
                        }
                        onDispose { observer.disconnect() }
                    }
                })
            }
        }
    }

    StudySessionRemoveConfirmDialog(
        item = pendingRemove,
        strings = strings,
        onDismiss = { pendingRemove = null },
        onConfirm = { item ->
            pendingRemove = null
            onRemove(item)
        },
    )
}

@Composable
internal fun StudySessionCard(
    item: SavedStudySessionSummary,
    strings: UiStrings,
    removable: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
) {
    Div(attrs = { classes(Styles.savedItem, Styles.studySessionCard) }) {
        if (removable) {
            Div(attrs = { classes(Styles.studySessionCardActions) }) {
                Button(attrs = {
                    classes(Styles.studySessionRemoveBtn)
                    attr("type", "button")
                    attr("aria-label", strings.chatStudySessionsRename)
                    onClick { onRename() }
                }) {
                    MaterialSymbol("edit")
                }
                Button(attrs = {
                    classes(Styles.studySessionRemoveBtn)
                    attr("type", "button")
                    attr("aria-label", strings.chatStudySessionsRemove)
                    onClick { onRemove() }
                }) {
                    MaterialSymbol("delete")
                }
            }
        }
        val cardContent = studySessionCardContent(item, strings.chatStudyTitle)
        P(attrs = { classes(Styles.studySessionCardTitle) }) { Text(cardContent.title) }
        studySessionExamGradeBadge(item)?.let { grade ->
            val summary = studyExamGradeSummary(grade, studyExamLevelLabel(grade, strings))
            Div(attrs = {
                classes(Styles.studySessionExamGradeBadge)
                attr("aria-label", "${strings.chatTestGradeLabel}: $summary")
            }) {
                MaterialSymbol("workspace_premium")
                Text(summary)
            }
        }
        val phraseImages = studySessionPhraseImageUrls(item)
        if (phraseImages.isNotEmpty()) {
            Div(attrs = { classes(Styles.studySessionPhraseRow) }) {
                phraseImages.forEach { url ->
                    StudySessionPhraseImage(url, strings.chatStudySentenceImageAlt)
                }
            }
        }
        P(attrs = { classes(Styles.savedItemText) }) { Text(cardContent.text) }
        Div(attrs = { classes(Styles.savedItemActions) }) {
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick { onOpen() }
            }) {
                MaterialSymbol("menu_book")
                Text(strings.chatStudySessionsOpen)
            }
        }
    }
}

@Composable
private fun StudySessionPhraseImage(url: String, alt: String) {
    var visible by remember(url) { mutableStateOf(false) }
    val loadComplete = remember(url) { CompletableDeferred<Unit>() }
    LaunchedEffect(url) {
        studySessionImageSlots.withPermit {
            visible = true
            loadComplete.await()
        }
    }
    if (visible) {
        Img(
            src = url,
            alt = alt,
            attrs = {
                classes(Styles.studySessionPhraseImage)
                ref { element ->
                    val image = element
                    image.asDynamic().onload = { loadComplete.complete(Unit) }
                    image.asDynamic().onerror = { loadComplete.complete(Unit) }
                    if (image.complete) loadComplete.complete(Unit)
                    onDispose {
                        image.asDynamic().onload = null
                        image.asDynamic().onerror = null
                    }
                }
            },
        )
    }
}

@Composable
private fun StudySessionRenameDialog(
    item: SavedStudySessionSummary?,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (SavedStudySessionSummary, String) -> Unit,
) {
    var name by remember(item?.id, item?.name) { mutableStateOf(item?.name.orEmpty()) }
    var nameInput by remember(item?.id) { mutableStateOf<HTMLInputElement?>(null) }
    LaunchedEffect(item?.id) {
        if (item != null) {
            delay(32)
            nameInput?.focus()
            nameInput?.select()
        }
    }
    BottomDialog(
        visible = item != null,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.chatStudySessionsRename) }
        }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(name)
            attr("placeholder", strings.chatStudySessionsNamePlaceholder)
            attr("aria-label", strings.chatStudySessionsNamePlaceholder)
            onInput { event -> name = event.value }
            autoFocus()
            ref {
                val input = it
                nameInput = input
                onDispose { if (nameInput === input) nameInput = null }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                if (name.trim().isEmpty()) attr("disabled", "")
                onClick { item?.let { onConfirm(it, name.trim()) } }
            }) { Text(strings.chatStudySessionsSaveName) }
        }
    }
}

@Composable
private fun StudySessionLanguageDialog(
    visible: Boolean,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var languageName by remember(visible) { mutableStateOf("") }
    var languageInput by remember(visible) { mutableStateOf<HTMLInputElement?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(32)
            languageInput?.focus()
            languageInput?.select()
        }
    }
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.chatStudyAnotherLanguage) }
        }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(languageName)
            attr("placeholder", strings.chatStudyAnotherLanguagePlaceholder)
            attr("aria-label", strings.chatStudyAnotherLanguagePlaceholder)
            onInput { event -> languageName = event.value }
            autoFocus()
            ref {
                val input = it
                languageInput = input
                onDispose { if (languageInput === input) languageInput = null }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                if (languageName.trim().isEmpty()) attr("disabled", "")
                onClick { languageName.trim().takeIf { it.isNotEmpty() }?.let(onConfirm) }
            }) { Text(strings.chatStudyAnotherLanguageConfirm) }
        }
    }
}

@Composable
private fun StudySessionRemoveConfirmDialog(
    item: SavedStudySessionSummary?,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (SavedStudySessionSummary) -> Unit,
) {
    BottomDialog(
        visible = item != null,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.chatStudySessionsRemove) }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick { item?.let(onConfirm) }
            }) { Text(strings.confirm) }
        }
    }
}

@Composable
private fun StudySessionPane(
    loading: Boolean,
    error: String?,
    session: StudySessionResponse?,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    messageId: String?,
    examScoreSessionId: String?,
    savedExamGrade: StudyExamGrade?,
    breakdownProgress: StudyBreakdownProgress?,
    onBreakdownProgress: (StudyBreakdownProgress) -> Unit,
    adventureProgress: StudyAdventureProgress?,
    onAdventureProgress: (StudyAdventureProgress) -> Unit,
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    charactersLoading: Boolean,
    examFailed: Boolean,
    talkBuildState: StudyBuildState?,
    exactText: Boolean,
    buildStatus: StudyBuildStatus?,
    playLoading: Boolean,
    playFailed: Boolean,
    strings: UiStrings,
    onRetry: () -> Unit,
    onRetrySentence: (Int) -> Unit,
    onRetryImage: (Int) -> Unit,
    onRetryWordImage: (Int, Int) -> Unit,
    onRetryPlay: () -> Unit,
    onRetryExam: () -> Unit,
    talkFailed: Boolean,
    talkCanRetry: Boolean,
    onRetryTalk: () -> Unit,
    onOpenTalk: () -> Unit,
    onPlayScenarioUpdate: (String, (StudyPlayScenario) -> StudyPlayScenario) -> Unit,
    onPlayMessageImage: (scenarioId: String, imageId: String, imageUrl: String?) -> Unit,
    onPracticeSentences: (List<StudyExampleSentence>) -> Unit,
    onExamScoreFinished: (StudyExamGrade) -> Unit,
) {
    // A server build in PREPARING has a placeholder record without sentences; it is not a session yet.
    val session = studySessionWithStructure(session)
    val progress = studyCreationProgress(
        session = session,
        loading = loading,
        exactText = exactText,
        buildStatus = buildStatus,
        sentenceStatus = sentenceStatus,
        messageId = messageId,
        imageFailed = imageFailed,
        wordImageFailed = wordImageFailed,
        charactersLoading = charactersLoading,
        examFailed = examFailed,
    )
    Div(attrs = { classes(Styles.studySessionBody) }) {
        when {
            error != null && session == null -> {
                Div(attrs = { classes(Styles.studySessionError) }) {
                    MaterialSymbol("error")
                    P { Text(error) }
                    Button(attrs = {
                        classes(Styles.savedItemAction)
                        attr("type", "button")
                        onClick { onRetry() }
                    }) {
                        MaterialSymbol("refresh")
                        Text(strings.chatStudyMessage)
                    }
                }
            }
            else -> {
                if (progress.visible) {
                    StudySessionCreationProgress(progress = progress, strings = strings)
                }
                if (session != null) {
                    StudySessionContent(
                        session = session,
                        sentenceStatus = sentenceStatus,
                        messageId = messageId,
                        charactersLoading = charactersLoading,
                        examScoreSessionId = examScoreSessionId,
                        savedExamGrade = savedExamGrade,
                        breakdownProgress = breakdownProgress,
                        onBreakdownProgress = onBreakdownProgress,
                        adventureProgress = adventureProgress,
                        onAdventureProgress = onAdventureProgress,
                        imageFailed = imageFailed,
                        wordImageFailed = wordImageFailed,
                        playLoading = playLoading,
                        playFailed = playFailed,
                        examFailed = examFailed,
                        talkBuildState = talkBuildState,
                        talkFailed = talkFailed,
                        talkCanRetry = talkCanRetry,
                        onRetryTalk = onRetryTalk,
                        onOpenTalk = onOpenTalk,
                        onRetryExam = onRetryExam,
                        strings = strings,
                        onRetrySentence = onRetrySentence,
                        onRetryImage = onRetryImage,
                        onRetryWordImage = onRetryWordImage,
                        onRetryPlay = onRetryPlay,
                        onPlayScenarioUpdate = onPlayScenarioUpdate,
                        onPlayMessageImage = onPlayMessageImage,
                        onPracticeSentences = onPracticeSentences,
                        onExamScoreFinished = onExamScoreFinished,
                    )
                } else if (!progress.visible) {
                    Div(attrs = { classes(Styles.chatStatus) }) {
                        MaterialSymbol("progress_activity")
                        Text(strings.chatStudyLoading)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudySessionCreationProgress(
    progress: StudyCreationProgress,
    strings: UiStrings,
) {
    Div(attrs = {
        classes(Styles.studyCreationProgress)
        attr("role", "status")
        attr("aria-live", "polite")
        attr("aria-label", "${strings.chatStudyCreationTitle} ${progress.percent}%")
    }) {
        Div(attrs = { classes(Styles.studyCreationProgressHeader) }) {
            Span(attrs = { classes(Styles.studyCreationProgressTitle) }) {
                Text(strings.chatStudyCreationTitle)
            }
            Span(attrs = { classes(Styles.studyCreationProgressPercent) }) {
                Text("${progress.percent}%")
            }
        }
        Div(attrs = { classes(Styles.studyCreationProgressBar) }) {
            Div(attrs = {
                classes(Styles.studyCreationProgressFill)
                style { property("width", "${progress.percent}%") }
            })
        }
        Div(attrs = { classes(Styles.studyCreationProgressSteps) }) {
            StudyCreationProgressStepRow(
                step = progress.text,
                label = strings.chatStudyCreationText,
                strings = strings,
            )
            StudyCreationProgressStepRow(
                step = progress.structure,
                label = strings.chatStudyCreationStructure,
                strings = strings,
            )
            StudyCreationProgressStepRow(
                step = progress.dissections,
                label = strings.chatStudyCreationDissections,
                strings = strings,
            )
            StudyCreationProgressStepRow(
                step = progress.images,
                label = strings.chatStudyCreationImages,
                strings = strings,
            )
            StudyCreationProgressStepRow(
                step = progress.test,
                label = strings.chatStudyCreationTest,
                strings = strings,
            )
        }
    }
}

@Composable
private fun StudyCreationProgressStepRow(
    step: StudyCreationStepProgress,
    label: String,
    strings: UiStrings,
) {
    val statusClass = when (step.status) {
        StudyCreationStepStatus.COMPLETED -> Styles.studyCreationProgressStepDone
        StudyCreationStepStatus.RUNNING -> Styles.studyCreationProgressStepRunning
        StudyCreationStepStatus.UPCOMING -> Styles.studyCreationProgressStepUpcoming
    }
    val icon = when (step.status) {
        StudyCreationStepStatus.COMPLETED -> "check_circle"
        StudyCreationStepStatus.RUNNING -> "progress_activity"
        StudyCreationStepStatus.UPCOMING -> "radio_button_unchecked"
    }
    Div(attrs = { classes(Styles.studyCreationProgressStep, statusClass) }) {
        MaterialSymbol(icon)
        Span(attrs = { classes(Styles.studyCreationProgressStepLabel) }) { Text(label) }
        if (step.total > 0) {
            Span(attrs = { classes(Styles.studyCreationProgressStepCount) }) {
                Text(strings.chatStudyCreationCount(step.done, step.total))
            }
        }
    }
}

@Composable
private fun StudySessionContent(
    session: StudySessionResponse,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    messageId: String?,
    charactersLoading: Boolean,
    examScoreSessionId: String?,
    savedExamGrade: StudyExamGrade?,
    breakdownProgress: StudyBreakdownProgress?,
    onBreakdownProgress: (StudyBreakdownProgress) -> Unit,
    adventureProgress: StudyAdventureProgress?,
    onAdventureProgress: (StudyAdventureProgress) -> Unit,
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    playLoading: Boolean,
    playFailed: Boolean,
    examFailed: Boolean,
    talkBuildState: StudyBuildState?,
    talkFailed: Boolean,
    talkCanRetry: Boolean,
    onRetryExam: () -> Unit,
    onRetryTalk: () -> Unit,
    onOpenTalk: () -> Unit,
    strings: UiStrings,
    onRetrySentence: (Int) -> Unit,
    onRetryImage: (Int) -> Unit,
    onRetryWordImage: (Int, Int) -> Unit,
    onRetryPlay: () -> Unit,
    onPlayScenarioUpdate: (String, (StudyPlayScenario) -> StudyPlayScenario) -> Unit,
    onPlayMessageImage: (scenarioId: String, imageId: String, imageUrl: String?) -> Unit,
    onPracticeSentences: (List<StudyExampleSentence>) -> Unit,
    onExamScoreFinished: (StudyExamGrade) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sentenceAudio = remember(session.text, session.textLanguageTag) {
        mutableStateMapOf<String, StudySentenceAudio>()
    }
    // Fallback single-token clips only when sentence word timings are unavailable.
    val tokenAudio = remember(session.text, session.textLanguageTag) {
        mutableStateMapOf<String, String>()
    }
    var selectedRef by remember(session.text) { mutableStateOf<StudyTokenRef?>(null) }
    var breakdownSentenceIndex by remember(session.text) { mutableStateOf<Int?>(null) }
    var mode by remember(session.text) { mutableStateOf(StudySessionMode.ALL) }
    var highlightedWord by remember(session.text) { mutableStateOf<String?>(null) }
    var examAttempt by remember(session.text) { mutableStateOf(StudyExamAttemptState()) }
    val practiceSentencesForSpeech = remember(session.text, session.textLanguageTag) {
        collectExpressPracticeSentences(session).map { it.text }
    }
    val speechItems = remember(session.text, session.textLanguageTag, session.sentences.map { it.text }, practiceSentencesForSpeech) {
        val mainItems = session.sentences.mapIndexed { index, sentence -> "sentence-$index" to sentence.text }
        val exampleItems = practiceSentencesForSpeech.mapIndexed { index, text -> "example-$index" to text }
        mainItems + exampleItems
    }
    val anyLoading = sentenceStatus.values.any { it == StudySentenceLoadState.LOADING }

    fun finishExam(scene: StudyExamScene, answers: Map<String, String>) {
        val sessionId = examScoreSessionId?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionId == null) {
            examAttempt = examAttempt.copy(error = strings.chatTestScoreError, isScoring = false)
            return
        }
        examAttempt = examAttempt.copy(error = null, isScoring = true)
        val request = StudyExamScoreRequest(
            sessionId = sessionId,
            answers = studyExamAnswersForScore(scene, answers),
        )
        scope.launch {
            runCatching { ChatApi.studyExamScore(request) }
                .onSuccess { response ->
                    examAttempt = examAttempt.copy(result = response, error = null, isScoring = false)
                    onExamScoreFinished(response.grade)
                }
                .onFailure {
                    examAttempt = examAttempt.copy(error = strings.chatTestScoreError, isScoring = false)
                }
        }
    }

    fun answerExamBeat(rawTranscript: String?) {
        val scene = session.examScene ?: return
        val beat = scene.beats.getOrNull(examAttempt.beatIndex) ?: return
        val transcript = rawTranscript.orEmpty().trim()
        if (transcript.isEmpty() || examAttempt.isScoring) return
        val answers = examAttempt.answers + (beat.id to transcript)
        if (examAttempt.beatIndex < scene.beats.lastIndex) {
            val nextIndex = examAttempt.beatIndex + 1
            val nextBeat = scene.beats[nextIndex]
            examAttempt = examAttempt.copy(
                beatIndex = nextIndex,
                draft = answers[nextBeat.id].orEmpty(),
                answers = answers,
                error = null,
            )
        } else {
            examAttempt = examAttempt.copy(answers = answers, draft = "", error = null)
            finishExam(scene, answers)
        }
    }

    fun playStudyToken(sentenceIndex: Int, tokenIndex: Int, tokenText: String) {
        val sentence = session.sentences.getOrNull(sentenceIndex) ?: return
        val clip = sentenceAudio["sentence-$sentenceIndex"]
        val timing = clip?.let {
            SpeechTimingAligner.timingForToken(
                sentenceText = sentence.text,
                tokenTexts = sentence.tokens.map { token -> token.text },
                tokenIndex = tokenIndex,
                words = it.words,
            )
        }
        if (clip != null && timing != null) {
            playAudioSegment(clip.url, beginMs = timing.first, endMs = timing.second)
            return
        }
        // Prefer not to call TTS per word; only fall back when timings are missing.
        val tokenKey = "token-$sentenceIndex-$tokenIndex"
        tokenAudio[tokenKey]?.let {
            playAudio(it)
            return
        }
        scope.launch {
            val url = runCatching {
                loadStudySpeechDataUrl(SpeechRequest(tokenText, session.textLanguageTag))
            }.getOrNull() ?: return@launch
            tokenAudio[tokenKey] = url
            // Still selected on the same token after the async round-trip.
            if (selectedRef?.sentenceIndex == sentenceIndex && selectedRef?.tokenIndex == tokenIndex) {
                playAudio(url)
            }
        }
    }

    // Prefetch sentence audio in batches of 3; retry rate-limit errors with backoff.
    LaunchedEffect(speechItems, session.textLanguageTag) {
        speechItems.chunked(SPEECH_BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { (key, text) ->
                    async {
                        if (sentenceAudio.containsKey(key)) return@async null
                        runCatching {
                            val speech = loadStudySpeech(SpeechRequest(text, session.textLanguageTag))
                            key to StudySentenceAudio(
                                url = StudyAudioCache.dataUrl(speech),
                                words = speech.words,
                            )
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull().forEach { (key, clip) -> sentenceAudio[key] = clip }
            }
        }
    }

    Div(attrs = { classes(Styles.studyModeTabs) }) {
        StudySessionMode.entries.forEach { tabMode ->
            Button(attrs = {
                classes(Styles.chip)
                if (mode == tabMode) classes(Styles.chipActive)
                attr("type", "button")
                style {
                    property("display", "inline-flex")
                    property("flex-direction", "column")
                    property("align-items", "center")
                    property("justify-content", "center")
                    property("line-height", "1.2")
                }
                onClick {
                    mode = tabMode
                    if (tabMode == StudySessionMode.TALK) onOpenTalk()
                    if (tabMode != StudySessionMode.ALL &&
                        tabMode != StudySessionMode.STUDY &&
                        tabMode != StudySessionMode.VISUALIZE &&
                        tabMode != StudySessionMode.WORDS
                    ) {
                        selectedRef = null
                    }
                }
            }) {
                Span { Text(tabMode.tabLabel(strings)) }
                Span(attrs = {
                    style {
                        property("font-size", "11px")
                        property("font-weight", "400")
                        property("opacity", "0.5")
                        property("margin-top", "2px")
                    }
                }) {
                    Text(tabMode.skillPracticed(strings))
                }
            }
        }
        Button(attrs = {
            classes(Styles.chip, Styles.studyWorksheetPrint)
            attr("type", "button")
            onClick {
                val worksheet = buildStudyWorksheetHtml(
                    session = session,
                    labels = StudyWorksheetLabels(
                        title = strings.chatWorksheetTitle,
                        instructions = strings.chatWorksheetInstructions,
                        vocabulary = strings.chatWorksheetVocabulary,
                        phrases = strings.chatWorksheetPhrases,
                        answerKey = strings.chatWorksheetAnswerKey,
                    ),
                )
                if (worksheet == null) {
                    Toaster.show(strings.chatWorksheetEmpty)
                } else if (!openStudyWorksheet(worksheet)) {
                    Toaster.show(strings.chatWorksheetPopupBlocked)
                }
            }
        }) {
            MaterialSymbol("print")
            Text(strings.chatWorksheetPrint)
        }
    }

    val modesToRender = if (mode == StudySessionMode.ALL) {
        StudySessionMode.entries.filter { it != StudySessionMode.ALL }
    } else {
        listOf(mode)
    }
    modesToRender.forEach { contentMode ->
        if (mode == StudySessionMode.ALL) {
            P(attrs = { classes(Styles.studySessionSectionTitle) }) {
                Text(contentMode.tabLabel(strings))
            }
        }
        when (contentMode) {
            StudySessionMode.WATCH -> {
                StudyWatchTab(
                    session = session,
                    messageId = messageId,
                    imageFailed = imageFailed,
                    sentenceAudio = sentenceAudio,
                    strings = strings,
                    onRetryImage = onRetryImage,
                    autoStart = mode != StudySessionMode.ALL,
                    keyboardEnabled = mode == StudySessionMode.WATCH,
                )
            }
            StudySessionMode.STORY -> {
                StudyStoryTab(
                    session = session,
                    messageId = messageId,
                    imageFailed = imageFailed,
                    wordImageFailed = wordImageFailed,
                    sentenceAudio = sentenceAudio,
                    strings = strings,
                    onRetryImage = onRetryImage,
                    scrollOnContinue = mode != StudySessionMode.ALL,
                    playStudyToken = { sentenceIndex, tokenIndex, tokenText ->
                        // Keep selection aligned so async token-TTS fallback still plays.
                        selectedRef = StudyTokenRef(sentenceIndex, tokenIndex)
                        playStudyToken(sentenceIndex, tokenIndex, tokenText)
                    },
                    onFinished = {
                        if (mode != StudySessionMode.ALL) {
                            selectedRef = null
                            mode = StudySessionMode.STUDY
                        }
                    },
                )
            }
            StudySessionMode.ADVENTURE -> {
                StudyAdventureTab(
                    session = session,
                    progress = adventureProgress,
                    onAdventureProgress = onAdventureProgress,
                    strings = strings,
                )
            }
            StudySessionMode.VISUALIZE -> {
                StudyVisualizeTab(
                    session = session,
                    anyLoading = anyLoading,
                    autoStart = mode != StudySessionMode.ALL,
                    strings = strings,
                )
            }
            StudySessionMode.STUDY -> {
                Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatStudyTapHint) }
                if (anyLoading) {
                    Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                        MaterialSymbol("progress_activity")
                        Text(strings.chatStudyPreparingMeanings)
                    }
                }

                session.sentences.forEachIndexed { sentenceIndex, sentence ->
                    val loadState = sentenceStatus[sentenceIndex] ?: StudySentenceLoadState.LOADING
                    val selectedToken = selectedRef
                        ?.takeIf { it.sentenceIndex == sentenceIndex }
                        ?.let { ref -> sentence.tokens.getOrNull(ref.tokenIndex) }
                    val imageFailKey = messageId?.let { "$it#$sentenceIndex" }
                    val visualFailed = imageFailKey != null && imageFailed[imageFailKey] == true
                    Div(attrs = { classes(Styles.studySentenceBlock) }) {
                        StudySentenceVisual(
                            imageUrl = sentence.imageUrl,
                            failed = visualFailed,
                            strings = strings,
                            onRetry = { onRetryImage(sentenceIndex) },
                        )
                        val sentenceClipUrl = sentenceAudio["sentence-$sentenceIndex"]?.url
                        val sentencePlaying = isStudyAudioPlaying(sentenceClipUrl)
                        Button(attrs = {
                            classes(Styles.sentencePlay)
                            attr("type", "button")
                            if (sentenceClipUrl == null) attr("disabled", "")
                            attr(
                                "aria-label",
                                if (sentencePlaying) strings.chatStudyPauseSentence else strings.chatStudyPlaySentence,
                            )
                            onClick { sentenceClipUrl?.let(::playAudio) }
                        }) {
                            MaterialSymbol(if (sentencePlaying) "pause_circle" else "play_circle")
                            Text(
                                if (sentencePlaying) strings.chatStudyPauseSentence else strings.chatStudyPlaySentence,
                            )
                        }
                        Button(attrs = {
                            classes(Styles.studyBreakdownButton)
                            attr("type", "button")
                            onClick { breakdownSentenceIndex = sentenceIndex }
                        }) {
                            MaterialSymbol("account_tree")
                            Text(
                                if (breakdownProgress?.sentences?.get(sentenceIndex)?.completed == true) {
                                    strings.chatStudyBreakdownComplete
                                } else {
                                    strings.chatStudyBreakdown
                                },
                            )
                        }
                        Div(attrs = { classes(Styles.languageSentence) }) {
                            sentence.tokens.forEachIndexed { tokenIndex, token ->
                                when {
                                    token.isDissectable -> {
                                        val selected = selectedRef?.sentenceIndex == sentenceIndex &&
                                            selectedRef?.tokenIndex == tokenIndex
                                        Button(attrs = {
                                            classes(Styles.languageToken)
                                            if (selected) classes(Styles.languageTokenSelected)
                                            attr("type", "button")
                                            onClick {
                                                selectedRef = StudyTokenRef(sentenceIndex, tokenIndex)
                                                playStudyToken(sentenceIndex, tokenIndex, token.text)
                                            }
                                        }) { Text(token.text) }
                                    }
                                    token.status == StudyTokenStatus.PENDING -> {
                                        val selected = selectedRef?.sentenceIndex == sentenceIndex &&
                                            selectedRef?.tokenIndex == tokenIndex
                                        Button(attrs = {
                                            classes(Styles.studyTokenPending)
                                            if (selected) classes(Styles.languageTokenSelected)
                                            attr("type", "button")
                                            onClick {
                                                selectedRef = StudyTokenRef(sentenceIndex, tokenIndex)
                                                playStudyToken(sentenceIndex, tokenIndex, token.text)
                                            }
                                        }) { Text(token.text) }
                                    }
                                    else -> {
                                        Span(attrs = { classes(Styles.studyTokenPlain) }) { Text(token.text) }
                                    }
                                }
                            }
                        }
                        if (loadState == StudySentenceLoadState.ERROR) {
                            Div(attrs = { classes(Styles.studySentenceError) }) {
                                MaterialSymbol("error")
                                Span { Text(strings.chatStudySentenceError) }
                                Button(attrs = {
                                    classes(Styles.savedItemAction)
                                    attr("type", "button")
                                    onClick { onRetrySentence(sentenceIndex) }
                                }) {
                                    MaterialSymbol("refresh")
                                    Text(strings.chatStudyRetrySentence)
                                }
                            }
                        }
                        // Keep the dissection panel under this sentence so learners don't scroll
                        // past later sentences to read meanings for a word above.
                        selectedToken?.let { token ->
                            val wordFailKey = messageId?.let { mid ->
                                selectedRef?.let { ref ->
                                    "$mid#${ref.sentenceIndex}#${ref.tokenIndex}"
                                }
                            }
                            val wordFailed = wordFailKey != null && wordImageFailed[wordFailKey] == true
                            StudyTokenDetail(
                                token = token,
                                textLanguageTag = session.textLanguageTag,
                                strings = strings,
                                imageFailed = wordFailed,
                                onRetryImage = {
                                    selectedRef?.let { ref -> onRetryWordImage(ref.sentenceIndex, ref.tokenIndex) }
                                },
                            )
                        }
                        if (breakdownSentenceIndex == sentenceIndex) {
                            StudyBreakdown(
                                session = session,
                                sentenceIndex = sentenceIndex,
                                progress = breakdownProgress,
                                strings = strings,
                                onProgressChange = onBreakdownProgress,
                                onRetryWordImage = onRetryWordImage,
                                wordImageFailed = wordImageFailed,
                                sentenceImageFailed = visualFailed,
                                messageId = messageId,
                                onDismiss = { breakdownSentenceIndex = null },
                            )
                        }
                    }
                }
            }
            StudySessionMode.WORDS -> {
                StudyWordsTab(
                    rows = studyVocabularyRows(session.sentences),
                    strings = strings,
                    onWordClick = { row ->
                        selectedRef = StudyTokenRef(row.sentenceIndex, row.tokenIndex)
                        playStudyToken(row.sentenceIndex, row.tokenIndex, row.word)
                    },
                )
            }
            StudySessionMode.LISTEN -> {
                StudyListenTab(
                    session = session,
                    anyLoading = anyLoading,
                    strings = strings,
                    autoFocus = mode != StudySessionMode.ALL,
                    onPracticeSentences = onPracticeSentences,
                )
            }
            StudySessionMode.SPEAK -> {
                StudySpeakTab(
                    session = session,
                    anyLoading = anyLoading,
                    strings = strings,
                    onPracticeSentences = onPracticeSentences,
                )
            }
            StudySessionMode.RECALL -> {
                StudyRecallTab(
                    session = session,
                    anyLoading = anyLoading,
                    strings = strings,
                )
            }
            StudySessionMode.READ -> {
                val readClipUrls = speechItems.mapNotNull { (key, _) -> sentenceAudio[key]?.url }
                val readAudioReady = speechItems.isNotEmpty() && readClipUrls.size == speechItems.size
                val readPlaying = isStudyAudioPlaylistPlaying()
                Button(attrs = {
                    classes(Styles.sentencePlay, Styles.studyReadPlay)
                    attr("type", "button")
                    if (!readAudioReady) attr("disabled", "")
                    attr(
                        "aria-label",
                        if (readPlaying) strings.chatReadPauseText else strings.chatReadPlayText,
                    )
                    onClick {
                        if (readAudioReady) {
                            playAudioSequence(readClipUrls, toggleIfPlaying = true)
                        }
                    }
                }) {
                    MaterialSymbol(if (readPlaying) "pause_circle" else "play_circle")
                    Text(if (readPlaying) strings.chatReadPauseText else strings.chatReadPlayText)
                }
                Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatReadTapHint) }
                Div(attrs = { classes(Styles.studyReadBlock) }) {
                    // One flex row per sentence so passages keep a light gap between sentences.
                    session.sentences.forEachIndexed { sentenceIndex, sentence ->
                        Div(attrs = { classes(Styles.languageSentence, Styles.studyReadSentence) }) {
                            sentence.tokens.forEachIndexed { tokenIndex, token ->
                                val clickable = token.status != StudyTokenStatus.PLAIN && token.text.isNotBlank()
                                if (clickable) {
                                    val selected = highlightedWord != null &&
                                        token.text.equals(highlightedWord, ignoreCase = true)
                                    Button(attrs = {
                                        classes(Styles.languageToken)
                                        if (selected) classes(Styles.languageTokenSelected)
                                        attr("type", "button")
                                        onClick {
                                            highlightedWord =
                                                if (
                                                    highlightedWord != null &&
                                                    token.text.equals(highlightedWord, ignoreCase = true)
                                                ) {
                                                    null
                                                } else {
                                                    token.text
                                                }
                                            playStudyToken(sentenceIndex, tokenIndex, token.text)
                                        }
                                    }) { Text(token.text) }
                                } else {
                                    Span(attrs = { classes(Styles.studyTokenPlain) }) { Text(token.text) }
                                }
                            }
                        }
                    }
                    // Example sentences from the main sentence dissection.
                    collectExpressPracticeSentences(session).forEachIndexed { exampleIndex, example ->
                        val exampleKey = "example-$exampleIndex"
                        val exampleAudioUrl = sentenceAudio[exampleKey]?.url
                        Div(attrs = { classes(Styles.languageSentence, Styles.studyReadSentence) }) {
                            Button(attrs = {
                                classes(Styles.languageToken)
                                attr("type", "button")
                                onClick {
                                    exampleAudioUrl?.let { playAudio(it) }
                                }
                            }) {
                                Text(example.text)
                            }
                        }
                    }
                }
            }
            StudySessionMode.WRITE -> {
                Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatWriteHint) }
                session.sentences.forEachIndexed { sentenceIndex, sentence ->
                    StudyWriteSentenceCard(
                        sentenceIndex = sentenceIndex,
                        sentenceText = sentence.text,
                        textLanguageTag = session.textLanguageTag,
                        strings = strings,
                        autoFocus = mode != StudySessionMode.ALL,
                    )
                }
                // Example sentences from the main sentence dissection.
                collectExpressPracticeSentences(session).forEachIndexed { exampleIndex, example ->
                    StudyWriteSentenceCard(
                        sentenceIndex = session.sentences.size + exampleIndex,
                        sentenceText = example.text,
                        textLanguageTag = session.textLanguageTag,
                        strings = strings,
                        autoFocus = false,
                    )
                }
            }
            StudySessionMode.EXPRESS -> {
                StudyExpressTab(
                    session = session,
                    anyLoading = anyLoading,
                    strings = strings,
                    autoFocus = mode != StudySessionMode.ALL,
                    onPracticeSentences = onPracticeSentences,
                )
            }
            StudySessionMode.PLAY -> {
                StudyPlayTab(
                    session = session,
                    playLoading = playLoading,
                    playFailed = playFailed,
                    strings = strings,
                    onRetryLoad = onRetryPlay,
                    onScenarioUpdate = onPlayScenarioUpdate,
                    onMessageImage = onPlayMessageImage,
                )
            }
            StudySessionMode.TALK -> {
                StudyTalkTab(
                    session = session,
                    buildState = talkBuildState,
                    failed = talkFailed,
                    canRetry = talkCanRetry,
                    strings = strings,
                    onRetry = onRetryTalk,
                )
            }
            StudySessionMode.TEST -> {
                StudyTestTab(
                    session = session,
                    strings = strings,
                    learnerLanguageTag = Localization.language.tag,
                    sceneLoading = charactersLoading && session.examScene == null,
                    sceneError = examFailed,
                    savedGrade = savedExamGrade,
                    attempt = examAttempt,
                    autoPlayListen = true,
                    autoFocus = mode == StudySessionMode.TEST,
                    onRetryScene = onRetryExam,
                    onDraftChange = { value -> examAttempt = examAttempt.copy(draft = value, error = null) },
                    onSubmitAnswer = ::answerExamBeat,
                    onStart = { examAttempt = examAttempt.copy(started = true) },
                    onRetake = { examAttempt = StudyExamAttemptState(isRetaking = true) },
                )
            }
            StudySessionMode.ALL -> Unit
            }
    }
}

@Composable
private fun StudyImageRetryButton(
    label: String,
    onRetry: () -> Unit,
) {
    Button(attrs = {
        classes(Styles.savedItemAction)
        attr("type", "button")
        onClick { onRetry() }
    }) {
        MaterialSymbol("refresh")
        Text(label)
    }
}

@Composable
internal fun StudySentenceVisual(
    imageUrl: String?,
    failed: Boolean,
    strings: UiStrings,
    onRetry: (() -> Unit)? = null,
) {
    val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    when {
        url != null -> {
            Img(src = url, alt = strings.chatStudySentenceImageAlt, attrs = {
                classes(Styles.studySentenceImage)
            })
        }
        failed -> {
            Div(attrs = { classes(Styles.studySentenceImageStatus) }) {
                MaterialSymbol("image_not_supported")
                Span { Text(strings.chatStudySentenceImageError) }
                if (onRetry != null) {
                    StudyImageRetryButton(
                        label = strings.chatStudyRetrySentence,
                        onRetry = onRetry,
                    )
                }
            }
        }
        else -> {
            Div(attrs = {
                classes(Styles.studySentenceImagePlaceholder, Styles.studySentenceImageStatusBusy)
            }) {
                MaterialSymbol("progress_activity")
                Span { Text(strings.chatStudySentenceImageLoading) }
            }
        }
    }
}

private enum class StudySessionMode { ALL, WATCH, STUDY, STORY, ADVENTURE, VISUALIZE, WORDS, LISTEN, SPEAK, RECALL, READ, WRITE, EXPRESS, PLAY, TALK, TEST }

internal enum class StudySpeakPhase { READY, LISTENING, SPEAKING, REVIEW }

internal data class StudySpeakProgress(
    val phase: StudySpeakPhase = StudySpeakPhase.READY,
    val roundIndex: Int = 0,
)

internal const val STUDY_SPEAK_ROUNDS = 3

internal fun startStudySpeak(progress: StudySpeakProgress): StudySpeakProgress =
    if (progress.phase == StudySpeakPhase.READY) {
        progress.copy(phase = StudySpeakPhase.LISTENING, roundIndex = 0)
    } else {
        progress
    }

internal fun finishStudySpeakListen(progress: StudySpeakProgress): StudySpeakProgress =
    if (progress.phase == StudySpeakPhase.LISTENING) {
        progress.copy(phase = StudySpeakPhase.SPEAKING)
    } else {
        progress
    }

internal fun finishStudySpeakRecording(progress: StudySpeakProgress): StudySpeakProgress =
    if (progress.phase != StudySpeakPhase.SPEAKING) {
        progress
    } else if (progress.roundIndex < STUDY_SPEAK_ROUNDS - 1) {
        progress.copy(phase = StudySpeakPhase.LISTENING, roundIndex = progress.roundIndex + 1)
    } else {
        progress.copy(phase = StudySpeakPhase.REVIEW)
    }

internal fun studySpeakReviewPlaylist(targetAudioUrl: String, recordingUrls: List<String>): List<String> =
    recordingUrls.flatMap { recordingUrl -> listOf(targetAudioUrl, recordingUrl) }

internal fun resetStudySpeak(progress: StudySpeakProgress): StudySpeakProgress =
    progress.copy(phase = StudySpeakPhase.READY, roundIndex = 0)

internal fun studySpeakPlayingAttemptIndex(
    targetAudioUrl: String?,
    recordingUrls: List<String>,
    playlistUrls: List<String>,
    playlistIndex: Int,
    isPlaying: Boolean,
): Int? {
    if (!isPlaying || targetAudioUrl == null || playlistIndex !in playlistUrls.indices) return null
    val reviewPlaylist = studySpeakReviewPlaylist(targetAudioUrl, recordingUrls)
    if (playlistUrls == reviewPlaylist) return playlistIndex / 2
    return recordingUrls.indexOfFirst { recordingUrl ->
        playlistUrls == listOf(targetAudioUrl, recordingUrl)
    }.takeIf { attemptIndex -> attemptIndex >= 0 && playlistIndex in 0..1 }
}

internal data class StudyVocabularyRow(
    val sentenceIndex: Int,
    val tokenIndex: Int,
    val word: String,
    val meaning: String,
    val literalMeaning: String,
)

internal fun studyVocabularyMeaningLines(literalMeaning: String, contextualMeaning: String): List<String> {
    val literal = literalMeaning.trim()
    val contextual = contextualMeaning.trim()
    val primary = literal.ifBlank { contextual }
    return listOfNotNull(
        primary.takeIf(String::isNotBlank),
        contextual.takeIf { it.isNotBlank() && it != primary },
    )
}

internal fun studyVocabularyRows(sentences: List<StudySentence>): List<StudyVocabularyRow> {
    val seenWords = mutableSetOf<String>()
    return buildList {
        sentences.forEachIndexed { sentenceIndex, sentence ->
            sentence.tokens.forEachIndexed { tokenIndex, token ->
                val word = token.text.trim()
                if (token.isDissectable && word.isNotEmpty() && seenWords.add(word.lowercase())) {
                    add(
                        StudyVocabularyRow(
                            sentenceIndex = sentenceIndex,
                            tokenIndex = tokenIndex,
                            word = word,
                            meaning = token.contextualMeaning.ifBlank { token.literalMeaning },
                            literalMeaning = token.literalMeaning,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyWordsTab(
    rows: List<StudyVocabularyRow>,
    strings: UiStrings,
    onWordClick: (StudyVocabularyRow) -> Unit,
) {
    if (rows.isEmpty()) {
        Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
            MaterialSymbol("progress_activity")
            Text(strings.chatStudyPreparingMeanings)
        }
        return
    }

    Table(attrs = {
        classes(Styles.studyWordsTable)
        attr("aria-label", strings.chatWordsTab)
    }) {
        Thead {
            Tr {
                Th { Text(strings.chatWordsWord) }
                Th { Text(strings.chatWordsMeaning) }
            }
        }
        Tbody {
            rows.forEach { row ->
                Tr {
                    Th(attrs = { attr("scope", "row") }) {
                        Button(attrs = {
                            classes(Styles.studyWordsWord)
                            attr("type", "button")
                            onClick { onWordClick(row) }
                        }) { Text(row.word) }
                    }
                    Td {
                        studyVocabularyMeaningLines(row.literalMeaning, row.meaning).forEachIndexed { index, meaning ->
                            P {
                                if (index == 0) {
                                    B { Text(meaning) }
                                } else {
                                    Text(meaning)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class StudyExamAttemptState(
    val beatIndex: Int = 0,
    val draft: String = "",
    val answers: Map<String, String> = emptyMap(),
    val result: StudyExamScoreResponse? = null,
    val isScoring: Boolean = false,
    val error: String? = null,
    val isRetaking: Boolean = false,
    val started: Boolean = false,
)

internal fun studyExamTranscriptsForScore(
    beatIds: List<String>,
    answers: Map<String, String>,
): List<Pair<String, String>> = beatIds.map { beatId ->
    beatId to answers[beatId].orEmpty().trim()
}

internal fun studyExamAnswersForScore(
    scene: StudyExamScene,
    answers: Map<String, String>,
): List<StudyExamScoreAnswer> = studyExamTranscriptsForScore(scene.beats.map { it.id }, answers)
    .map { (beatId, transcript) -> StudyExamScoreAnswer(beatId = beatId, transcript = transcript) }

private fun StudySessionMode.tabLabel(strings: UiStrings): String = when (this) {
    StudySessionMode.ALL -> strings.chatAllTab
    StudySessionMode.WATCH -> strings.chatWatchTab
    StudySessionMode.STORY -> strings.chatStoryTab
    StudySessionMode.ADVENTURE -> strings.chatAdventure.tab
    StudySessionMode.VISUALIZE -> strings.chatVisualize.tab
    StudySessionMode.STUDY -> strings.chatStudyTab
    StudySessionMode.WORDS -> strings.chatWordsTab
    StudySessionMode.LISTEN -> strings.chatListenTab
    StudySessionMode.SPEAK -> strings.chatSpeakTab
    StudySessionMode.RECALL -> strings.chatAdventure.recall.tab
    StudySessionMode.READ -> strings.chatReadTab
    StudySessionMode.WRITE -> strings.chatWriteTab
    StudySessionMode.EXPRESS -> strings.chatExpressTab
    StudySessionMode.PLAY -> strings.chatPlayTab
    StudySessionMode.TALK -> strings.chatTalk.tab
    StudySessionMode.TEST -> strings.chatTestTab
}

private fun StudySessionMode.skillPracticed(strings: UiStrings): String = when (this) {
    StudySessionMode.ALL -> strings.chatAllSkill
    StudySessionMode.WATCH -> strings.chatWatchSkill
    StudySessionMode.STORY -> strings.chatStorySkill
    StudySessionMode.ADVENTURE -> strings.chatAdventureSkill
    StudySessionMode.VISUALIZE -> strings.chatVisualizeSkill
    StudySessionMode.STUDY -> strings.chatStudySkill
    StudySessionMode.WORDS -> strings.chatWordsSkill
    StudySessionMode.LISTEN -> strings.chatListenSkill
    StudySessionMode.SPEAK -> strings.chatSpeakSkill
    StudySessionMode.RECALL -> strings.chatRecallSkill
    StudySessionMode.READ -> strings.chatReadSkill
    StudySessionMode.WRITE -> strings.chatWriteSkill
    StudySessionMode.EXPRESS -> strings.chatExpressSkill
    StudySessionMode.PLAY -> strings.chatPlaySkill
    StudySessionMode.TALK -> strings.chatTalkSkill
    StudySessionMode.TEST -> strings.chatTestSkill
}

private fun studyExamLevelLabel(level: Int, strings: UiStrings): String = when (level) {
    1 -> strings.chatTestLevelBeginner
    2 -> strings.chatTestLevelElementary
    3 -> strings.chatTestLevelIntermediate
    4 -> strings.chatTestLevelAdvanced
    else -> strings.chatTestLevelNative
}

internal data class WatchSceneClip(
    val sentenceIndex: Int,
    val url: String,
    val durationMs: Int,
)

internal data class WatchTimeline(
    val clips: List<WatchSceneClip>,
    val startsMs: List<Int>,
    val totalMs: Int,
)

/** Prefers TTS word timings; falls back when the provider omitted them. */
internal fun watchClipDurationMs(
    words: List<SpeechWordTiming>,
    fallbackMs: Int = 2_000,
): Int {
    if (words.isEmpty()) return fallbackMs.coerceAtLeast(1)
    return words.maxOf { it.endMs }.coerceAtLeast(1)
}

internal fun buildWatchTimeline(clips: List<WatchSceneClip>): WatchTimeline {
    var cursor = 0
    val starts = ArrayList<Int>(clips.size)
    val normalized = clips.map { clip ->
        val duration = clip.durationMs.coerceAtLeast(1)
        starts += cursor
        cursor += duration
        clip.copy(durationMs = duration)
    }
    return WatchTimeline(clips = normalized, startsMs = starts, totalMs = cursor)
}

internal fun watchSceneIndexAt(timeline: WatchTimeline, positionMs: Int): Int {
    if (timeline.clips.isEmpty()) return 0
    val pos = positionMs.coerceAtLeast(0)
    timeline.clips.indices.forEach { index ->
        val start = timeline.startsMs[index]
        val end = start + timeline.clips[index].durationMs
        if (pos < end) return index
    }
    return timeline.clips.lastIndex
}

internal fun watchLocalOffsetMs(timeline: WatchTimeline, positionMs: Int): Int {
    if (timeline.clips.isEmpty()) return 0
    val index = watchSceneIndexAt(timeline, positionMs)
    val start = timeline.startsMs[index]
    val duration = timeline.clips[index].durationMs
    return (positionMs - start).coerceIn(0, duration)
}

/** Fractions along the seekbar for every scene after the first. */
internal fun watchSceneMarkerFractions(timeline: WatchTimeline): List<Double> {
    if (timeline.totalMs <= 0) return emptyList()
    return timeline.startsMs.drop(1).map { start ->
        (start.toDouble() / timeline.totalMs.toDouble()).coerceIn(0.0, 1.0)
    }
}

internal fun formatWatchClock(ms: Int): String {
    val totalSec = ms.coerceAtLeast(0) / 1_000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal const val WATCH_IMAGE_LOOKAHEAD = 3

/** Current scene plus the next [lookahead] scenes so Watch can decode them before they appear. */
internal fun watchPreloadIndexes(
    currentIndex: Int,
    sceneCount: Int,
    lookahead: Int = WATCH_IMAGE_LOOKAHEAD,
): List<Int> {
    if (sceneCount <= 0) return emptyList()
    val current = currentIndex.coerceIn(0, sceneCount - 1)
    val last = (current + lookahead).coerceAtMost(sceneCount - 1)
    return (current..last).toList()
}

private fun watchImageUrl(url: String?): String? =
    url?.trim()?.takeIf { it.isNotEmpty() }

private fun preloadWatchImages(
    urls: Collection<String>,
    holders: MutableMap<String, HTMLImageElement>,
    decoded: MutableMap<String, Boolean>,
) {
    urls.forEach { url ->
        if (url in holders) return@forEach
        val img = document.createElement("img") as HTMLImageElement
        img.onload = { _: Event -> decoded[url] = true }
        img.src = url
        holders[url] = img
        if (img.complete && img.naturalWidth > 0) decoded[url] = true
    }
}

internal fun currentFullscreenElement(): Element? {
    val doc = document.asDynamic()
    return (
        doc.fullscreenElement
            ?: doc.webkitFullscreenElement
            ?: doc.mozFullScreenElement
            ?: doc.msFullscreenElement
        ) as? Element
}

internal fun requestWatchFullscreen(element: Element) {
    val el = element.asDynamic()
    val request = el.requestFullscreen
        ?: el.webkitRequestFullscreen
        ?: el.webkitRequestFullScreen
        ?: el.mozRequestFullScreen
        ?: el.msRequestFullscreen
    if (request == null) return
    request.call(element)
}

internal fun exitWatchFullscreen() {
    if (currentFullscreenElement() == null) return
    val doc = document.asDynamic()
    val exit = doc.exitFullscreen
        ?: doc.webkitExitFullscreen
        ?: doc.webkitCancelFullScreen
        ?: doc.mozCancelFullScreen
        ?: doc.msExitFullscreen
    if (exit == null) return
    exit.call(document)
}

private fun watchKeyboardTargetIsEditable(target: HTMLElement?): Boolean {
    val tag = target?.tagName?.lowercase() ?: return false
    if (tag == "input" || tag == "textarea" || tag == "select" || tag == "button") return true
    return target.isContentEditable
}

internal enum class StudyStoryPhase { WORDS, SENTENCES }

internal fun nextStoryContinuePosition(
    phase: StudyStoryPhase,
    stepIndex: Int,
    wordStepCount: Int,
    sentenceCount: Int,
): Pair<StudyStoryPhase, Int> = when (phase) {
    StudyStoryPhase.WORDS -> when {
        stepIndex < wordStepCount - 1 -> StudyStoryPhase.WORDS to (stepIndex + 1)
        sentenceCount > 0 -> StudyStoryPhase.SENTENCES to 0
        else -> StudyStoryPhase.WORDS to 0
    }
    StudyStoryPhase.SENTENCES -> when {
        stepIndex < sentenceCount - 1 -> StudyStoryPhase.SENTENCES to (stepIndex + 1)
        wordStepCount > 0 -> StudyStoryPhase.WORDS to 0
        else -> StudyStoryPhase.SENTENCES to 0
    }
}

/** Ordered story-walkthrough steps for non-plain study tokens and their examples. */
internal data class StoryWordStep(
    val sentenceIndex: Int,
    val tokenIndex: Int,
    val exampleIndex: Int? = null,
)

/** Collects every non-plain token and its examples in reading order for the Story tab. */
internal fun collectStoryWordSteps(session: StudySessionResponse): List<StoryWordStep> {
    val steps = mutableListOf<StoryWordStep>()
    session.sentences.forEachIndexed { sentenceIndex, sentence ->
        sentence.tokens.forEachIndexed { tokenIndex, token ->
            if (token.status != StudyTokenStatus.PLAIN && token.text.isNotBlank()) {
                steps += StoryWordStep(sentenceIndex = sentenceIndex, tokenIndex = tokenIndex)
                token.exampleSentences.forEachIndexed { exampleIndex, example ->
                    if (example.text.isNotBlank()) {
                        steps += StoryWordStep(
                            sentenceIndex = sentenceIndex,
                            tokenIndex = tokenIndex,
                            exampleIndex = exampleIndex,
                        )
                    }
                }
            }
        }
    }
    return steps
}

/**
 * Learner-language meaning lines to speak after the target word in Story mode.
 * Skips blanks and de-duplicates identical literal/context glosses.
 */
internal fun collectStoryMeaningNarrationTexts(
    literalMeaning: String,
    contextualMeaning: String,
): List<String> {
    val literal = literalMeaning.trim()
    val contextual = contextualMeaning.trim()
    val lines = mutableListOf<String>()
    if (literal.isNotEmpty()) lines += literal
    if (contextual.isNotEmpty() && !contextual.equals(literal, ignoreCase = true)) {
        lines += contextual
    }
    return lines
}

internal enum class StudyCreationStepStatus {
    UPCOMING,
    RUNNING,
    COMPLETED,
}

internal data class StudyCreationStepProgress(
    val status: StudyCreationStepStatus,
    val done: Int = 0,
    val total: Int = 0,
)

internal data class StudyCreationProgress(
    val percent: Int,
    val text: StudyCreationStepProgress,
    val structure: StudyCreationStepProgress,
    val dissections: StudyCreationStepProgress,
    val images: StudyCreationStepProgress,
    val test: StudyCreationStepProgress,
) {
    val visible: Boolean get() = percent < 100
}

internal fun studyCreationProgress(
    session: StudySessionResponse?,
    loading: Boolean,
    exactText: Boolean,
    sentenceStatus: Map<Int, StudySentenceLoadState> = emptyMap(),
    messageId: String? = null,
    imageFailed: Map<String, Boolean> = emptyMap(),
    wordImageFailed: Map<String, Boolean> = emptyMap(),
    charactersLoading: Boolean = false,
    examFailed: Boolean = false,
    buildStatus: StudyBuildStatus? = null,
): StudyCreationProgress {
    val session = studySessionWithStructure(session)
    val textCompleted = session != null || when (buildStatus) {
        null -> exactText
        StudyBuildStatus.PREPARING -> false
        StudyBuildStatus.BUILDING,
        StudyBuildStatus.COMPLETE,
        StudyBuildStatus.PARTIAL,
        -> true
    }
    val text = StudyCreationStepProgress(
        status = when {
            textCompleted -> StudyCreationStepStatus.COMPLETED
            loading -> StudyCreationStepStatus.RUNNING
            else -> StudyCreationStepStatus.UPCOMING
        },
    )

    val structureCompleted = session != null
    val structure = StudyCreationStepProgress(
        status = when {
            structureCompleted -> StudyCreationStepStatus.COMPLETED
            loading && textCompleted -> StudyCreationStepStatus.RUNNING
            else -> StudyCreationStepStatus.UPCOMING
        },
    )

    val sentences = session?.sentences.orEmpty()
    val dissectionTotal = sentences.size
    var dissectionDone = 0
    var dissectionRunning = false
    sentences.forEachIndexed { index, sentence ->
        when (studyCreationSentenceState(sentence, sentenceStatus[index])) {
            StudySentenceLoadState.READY,
            StudySentenceLoadState.ERROR,
            -> dissectionDone += 1
            StudySentenceLoadState.LOADING -> dissectionRunning = true
        }
    }
    val dissectionsCompleted = session != null && dissectionDone >= dissectionTotal
    val dissections = StudyCreationStepProgress(
        status = when {
            session == null -> StudyCreationStepStatus.UPCOMING
            dissectionsCompleted -> StudyCreationStepStatus.COMPLETED
            dissectionRunning || dissectionDone < dissectionTotal -> StudyCreationStepStatus.RUNNING
            else -> StudyCreationStepStatus.UPCOMING
        },
        done = dissectionDone,
        total = dissectionTotal,
    )

    val (imageSettled, imageExpected) = studyCreationImageCounts(
        session = session,
        messageId = messageId,
        imageFailed = imageFailed,
        wordImageFailed = wordImageFailed,
        sentenceStatus = sentenceStatus,
        charactersLoading = charactersLoading,
    )
    val imagesCompleted = session != null && imageSettled >= imageExpected
    val images = StudyCreationStepProgress(
        status = when {
            session == null -> StudyCreationStepStatus.UPCOMING
            imagesCompleted -> StudyCreationStepStatus.COMPLETED
            else -> StudyCreationStepStatus.RUNNING
        },
        done = imageSettled.coerceAtMost(imageExpected),
        total = imageExpected,
    )

    val testReady = session?.examScene != null || examFailed
    val testStep = StudyCreationStepProgress(
        status = when {
            session == null -> StudyCreationStepStatus.UPCOMING
            testReady -> StudyCreationStepStatus.COMPLETED
            else -> StudyCreationStepStatus.RUNNING
        },
    )

    val completedUnits = (if (textCompleted) 1 else 0) +
        (if (structureCompleted) 1 else 0) +
        dissectionDone +
        imageSettled.coerceAtMost(imageExpected) +
        (if (session != null && testReady) 1 else 0)
    val totalUnits = 3 + dissectionTotal + imageExpected
    val percent = if (textCompleted && structureCompleted && dissectionsCompleted && imagesCompleted && testReady) {
        100
    } else if (totalUnits <= 0) {
        100
    } else {
        ((completedUnits * 100) / totalUnits).coerceIn(0, 99)
    }

    return StudyCreationProgress(
        percent = percent,
        text = text,
        structure = structure,
        dissections = dissections,
        images = images,
        test = testStep,
    )
}

/** Returns [session] only once the skeleton exists; server PREPARING placeholders have no sentences. */
internal fun studySessionWithStructure(session: StudySessionResponse?): StudySessionResponse? =
    session?.takeIf { it.sentences.isNotEmpty() }

private fun studyCreationSentenceState(
    sentence: StudySentence,
    status: StudySentenceLoadState?,
): StudySentenceLoadState = status ?: if (sentenceNeedsDissect(sentence)) {
    StudySentenceLoadState.LOADING
} else {
    StudySentenceLoadState.READY
}

private fun studyCreationImageCounts(
    session: StudySessionResponse?,
    messageId: String?,
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    charactersLoading: Boolean,
): Pair<Int, Int> {
    if (session == null) return 0 to 0
    var expected = 0
    var settled = 0
    val prefix = messageId.orEmpty()

    val characters = session.characters
    if (characters.isNotEmpty()) {
        expected += characters.size
        val ready = characters.count { !it.imageUrl.isNullOrBlank() }
        settled += if (charactersLoading) ready else characters.size
    }

    session.sentences.forEachIndexed { index, sentence ->
        expected += 1
        val sentenceKey = "$prefix#$index"
        if (!sentence.imageUrl.isNullOrBlank() || imageFailed[sentenceKey] == true) {
            settled += 1
        }

        val sentenceError = studyCreationSentenceState(sentence, sentenceStatus[index]) ==
            StudySentenceLoadState.ERROR
        sentence.tokens.forEachIndexed { tokenIndex, token ->
            if (token.status == StudyTokenStatus.PLAIN) return@forEachIndexed
            val wordKey = "$prefix#$index#$tokenIndex"
            val wordFailed = wordImageFailed[wordKey] == true
            val hasUrl = !token.imageUrl.isNullOrBlank()
            val expects = when {
                sentenceError -> hasUrl || !token.imageId.isNullOrBlank() || wordFailed
                token.status == StudyTokenStatus.PENDING -> true
                token.isDissectable -> true
                hasUrl || !token.imageId.isNullOrBlank() -> true
                else -> false
            }
            if (!expects) return@forEachIndexed
            expected += 1
            if (hasUrl || wordFailed || sentenceError) settled += 1
        }
    }
    return settled to expected
}

/** Completed sentence visuals for a saved study card, in reading order. */
internal fun studySessionPhraseImageUrls(item: SavedStudySessionSummary): List<String> =
    item.sentenceImageUrls.mapNotNull { it.trim().takeIf(String::isNotEmpty) }

internal fun studySessionDisplayName(item: SavedStudySessionSummary): String =
    item.name.trim().ifBlank { item.text }

internal data class StudySessionCardContent(
    val title: String,
    val text: String,
)

internal fun studySessionCardContent(item: SavedStudySessionSummary, fallbackTitle: String): StudySessionCardContent =
    StudySessionCardContent(
        title = item.name.trim().ifBlank { fallbackTitle },
        text = item.text,
    )

internal fun studySessionExamGradeBadge(item: SavedStudySessionSummary): Int? =
    item.examGrade?.overall

internal fun studyExamGradeSummary(overall: Int, levelName: String): String =
    "$overall/5 · $levelName"

internal fun mergeStudySessionSummary(
    current: SavedStudySessionSummary?,
    incoming: SavedStudySessionSummary,
): SavedStudySessionSummary {
    if (current == null) return incoming
    val currentNameIsNewer = current.updatedAt > incoming.updatedAt ||
        (current.updatedAt == incoming.updatedAt && current.name != incoming.name)
    val currentGrade = current.examGrade
    val incomingGrade = incoming.examGrade
    val currentGradeIsNewer = currentGrade != null &&
        (incomingGrade == null || currentGrade.scoredAt > incomingGrade.scoredAt)
    return incoming.copy(
        name = if (currentNameIsNewer) current.name else incoming.name,
        updatedAt = if (currentNameIsNewer) current.updatedAt else incoming.updatedAt,
        examGrade = if (currentGradeIsNewer) currentGrade else incomingGrade,
    )
}

internal fun mergeStudySessionItems(
    currentItems: List<SavedStudySessionSummary>,
    refreshedItems: List<SavedStudySessionSummary>,
): List<SavedStudySessionSummary> = refreshedItems.map { refreshed ->
    mergeStudySessionSummary(currentItems.firstOrNull { it.id == refreshed.id }, refreshed)
}

internal fun refreshedActiveStudySessionItem(
    activeItem: SavedStudySessionSummary?,
    refreshedItems: List<SavedStudySessionSummary>,
): SavedStudySessionSummary? = activeItem?.let { active ->
    refreshedItems.firstOrNull { it.id == active.id }?.let { refreshed ->
        mergeStudySessionSummary(active, refreshed)
    } ?: active
}

internal const val STUDY_COMMUNITY_PAGE_SIZE = 20

internal enum class StudySessionsFilter {
    ALL,
    MINE,
    COMMUNITY,
}

internal enum class StudySessionsListViewState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

internal fun studySessionsListViewState(
    filter: StudySessionsFilter,
    visibleItemsEmpty: Boolean,
    hasActiveSearch: Boolean,
    loading: Boolean,
    error: Boolean,
    communityLoading: Boolean,
    communityError: Boolean,
): StudySessionsListViewState {
    if (!visibleItemsEmpty) return StudySessionsListViewState.CONTENT
    val isLoading = when (filter) {
        StudySessionsFilter.ALL -> loading || communityLoading
        StudySessionsFilter.MINE -> loading
        StudySessionsFilter.COMMUNITY -> communityLoading
    }
    if (isLoading) return StudySessionsListViewState.LOADING
    if (!hasActiveSearch) {
        val isError = when (filter) {
            StudySessionsFilter.ALL -> error || communityError
            StudySessionsFilter.MINE -> error
            StudySessionsFilter.COMMUNITY -> communityError
        }
        if (isError) return StudySessionsListViewState.ERROR
    }
    return StudySessionsListViewState.EMPTY
}

internal data class StudySessionListEntry(
    val item: SavedStudySessionSummary,
    val removable: Boolean,
)

internal fun studySessionsForFilter(
    mine: List<SavedStudySessionSummary>,
    community: List<SavedStudySessionSummary>,
    filter: StudySessionsFilter,
): List<StudySessionListEntry> = when (filter) {
    StudySessionsFilter.MINE -> mine.map { StudySessionListEntry(it, removable = true) }
    StudySessionsFilter.COMMUNITY -> community.map { StudySessionListEntry(it, removable = false) }
    StudySessionsFilter.ALL -> {
        val seen = mine.map { it.id }.toHashSet()
        mine.map { StudySessionListEntry(it, removable = true) } +
            community.filter { seen.add(it.id) }.map { StudySessionListEntry(it, removable = false) }
    }
}

internal fun studySessionsForSearch(
    entries: List<StudySessionListEntry>,
    query: String,
    sourceLanguageTag: String,
    targetLanguageTag: String,
): List<StudySessionListEntry> {
    val normalizedQuery = query.trim()
    val normalizedSourceTag = sourceLanguageTag.trim().takeIf(String::isNotEmpty)
        ?.let(Language::normalizeContentTag)
    val normalizedTargetTag = targetLanguageTag.trim().takeIf(String::isNotEmpty)
        ?.let(Language::normalizeContentTag)
    return entries.filter { entry ->
        val item = entry.item
        (normalizedQuery.isEmpty() || item.name.contains(normalizedQuery, ignoreCase = true) ||
            item.text.contains(normalizedQuery, ignoreCase = true)) &&
            (normalizedSourceTag == null || Language.normalizeContentTag(item.languageTag) == normalizedSourceTag) &&
            (normalizedTargetTag == null || Language.normalizeContentTag(item.textLanguageTag) == normalizedTargetTag)
    }
}

/** Client route for a persisted study session, e.g. `/study/{id}`. */
internal fun studySessionPath(id: String): String = "/study/${id.trim()}"

/** Appends newly fetched community sessions, skipping ids already on screen. */
internal fun mergeCommunityStudySessions(
    existing: List<SavedStudySessionSummary>,
    incoming: List<SavedStudySessionSummary>,
): List<SavedStudySessionSummary> {
    if (incoming.isEmpty()) return existing
    val seen = existing.map { it.id }.toHashSet()
    return existing + incoming.filter { seen.add(it.id) }
}

private fun observeStudyListNearViewport(
    element: Element,
    root: Element?,
    onIntersect: () -> Unit,
): StudyListIntersectionObserverHandle {
    val options = js("({ rootMargin: '200px 0px', threshold: 0 })")
    options.root = root
    val callback: (Array<dynamic>) -> Unit = { entries ->
        if (entries.any { it.isIntersecting == true }) {
            onIntersect()
        }
    }
    val observer = StudyListJsIntersectionObserver(callback, options)
    observer.observe(element)
    return StudyListIntersectionObserverHandle(observer)
}

private class StudyListIntersectionObserverHandle(private val observer: StudyListJsIntersectionObserver) {
    fun disconnect() {
        try {
            observer.disconnect()
        } catch (_: Throwable) {
            // Best-effort cleanup; ignore if the browser already tore it down.
        }
    }
}

@JsName("IntersectionObserver")
private external class StudyListJsIntersectionObserver(
    callback: (Array<dynamic>) -> Unit,
    options: dynamic,
) {
    fun observe(target: Element)
    fun disconnect()
}

/**
 * Source text for a study session opened from a chat message.
 *
 * Prefers the language-study widget (already in the target language). When the
 * widget has not arrived yet, the full message is sent so the server can extract
 * study-language material with AI for any language.
 */
internal fun studyTextFromChatMessage(message: ChatMessage): String {
    val widget = message.miniApp as? LanguageStudyMiniApp
    val widgetSentence = widget?.sentence?.trim().orEmpty()
    if (widgetSentence.isNotEmpty()) return widgetSentence
    val widgetTokens = widget?.tokens
        ?.joinToString(separator = "") { it.text }
        ?.trim()
        .orEmpty()
    if (widgetTokens.isNotEmpty()) return widgetTokens
    return message.text.trim()
}

@Composable
private fun StudyWatchTab(
    session: StudySessionResponse,
    messageId: String?,
    imageFailed: Map<String, Boolean>,
    sentenceAudio: Map<String, StudySentenceAudio>,
    strings: UiStrings,
    onRetryImage: (Int) -> Unit,
    autoStart: Boolean = true,
    keyboardEnabled: Boolean = true,
) {
    val measuredDurations = remember(session.text) { mutableStateMapOf<String, Int>() }
    var sceneIndex by remember(session.text) { mutableStateOf(0) }
    var positionMs by remember(session.text) { mutableStateOf(0) }
    var wantPlaying by remember(session.text) { mutableStateOf(false) }
    var didAutostart by remember(session.text) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var seekStartMs by remember(session.text) { mutableStateOf(0) }
    var seekGeneration by remember(session.text) { mutableStateOf(0) }
    val dragMsRef = remember { mutableStateOf(0) }
    var playerElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestIsFullscreen by rememberUpdatedState(isFullscreen)
    val latestKeyboardEnabled by rememberUpdatedState(keyboardEnabled)
    val latestPlayerElement by rememberUpdatedState(playerElement)

    val clips = session.sentences.mapIndexedNotNull { index, _ ->
        val key = "sentence-$index"
        val clip = sentenceAudio[key] ?: return@mapIndexedNotNull null
        val duration = measuredDurations[key] ?: watchClipDurationMs(clip.words)
        WatchSceneClip(sentenceIndex = index, url = clip.url, durationMs = duration)
    }
    val timeline = remember(clips) { buildWatchTimeline(clips) }
    val audioReady = session.sentences.isNotEmpty() &&
        session.sentences.indices.all { index -> sentenceAudio.containsKey("sentence-$index") }
    val latestTimeline by rememberUpdatedState(timeline)
    val latestDragging by rememberUpdatedState(dragging)

    fun previewSeek(ms: Int) {
        val clamped = ms.coerceIn(0, timeline.totalMs.coerceAtLeast(0))
        positionMs = clamped
        dragMsRef.value = clamped
    }

    fun commitSeek(ms: Int) {
        val clamped = ms.coerceIn(0, timeline.totalMs.coerceAtLeast(0))
        sceneIndex = watchSceneIndexAt(timeline, clamped)
        positionMs = clamped
        seekStartMs = watchLocalOffsetMs(timeline, clamped)
        seekGeneration += 1
    }

    val latestCommit = remember { mutableStateOf<(Int) -> Unit>({}) }
    latestCommit.value = { ms -> commitSeek(ms) }

    fun togglePlay() {
        if (!audioReady || timeline.clips.isEmpty()) return
        if (wantPlaying) {
            val start = timeline.startsMs.getOrNull(sceneIndex) ?: 0
            seekStartMs = (positionMs - start).coerceAtLeast(0)
            wantPlaying = false
            return
        }
        if (timeline.totalMs > 0 && positionMs >= timeline.totalMs) {
            sceneIndex = 0
            positionMs = 0
            seekStartMs = 0
        } else {
            seekStartMs = watchLocalOffsetMs(timeline, positionMs)
        }
        wantPlaying = true
        seekGeneration += 1
    }

    fun setWatchFullscreen(enabled: Boolean) {
        if (enabled) {
            isFullscreen = true
            playerElement?.let(::requestWatchFullscreen)
        } else {
            isFullscreen = false
            exitWatchFullscreen()
        }
    }

    val latestTogglePlay = rememberUpdatedState { togglePlay() }
    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == " " || key == "Spacebar") && latestKeyboardEnabled) {
                val target = event.target as? HTMLElement
                if (!watchKeyboardTargetIsEditable(target)) {
                    event.preventDefault()
                    latestTogglePlay.value()
                }
            } else if ((key == "Escape" || key == "Esc") && latestIsFullscreen) {
                isFullscreen = false
                exitWatchFullscreen()
            }
        }
        val onFullscreen: (Event) -> Unit = {
            val current = currentFullscreenElement()
            val el = latestPlayerElement
            when {
                el != null && current === el -> isFullscreen = true
                latestIsFullscreen && current == null -> {
                    isFullscreen = false
                }
            }
        }
        window.addEventListener("keydown", onKey)
        document.addEventListener("fullscreenchange", onFullscreen)
        document.addEventListener("webkitfullscreenchange", onFullscreen)
        onDispose {
            window.removeEventListener("keydown", onKey)
            document.removeEventListener("fullscreenchange", onFullscreen)
            document.removeEventListener("webkitfullscreenchange", onFullscreen)
            exitWatchFullscreen()
        }
    }

    DisposableEffect(Unit) {
        stopActiveStudyAudio()
        onDispose { stopActiveStudyAudio() }
    }

    LaunchedEffect(audioReady, autoStart) {
        if (!autoStart) {
            wantPlaying = false
        } else if (audioReady && !didAutostart && timeline.clips.isNotEmpty()) {
            didAutostart = true
            wantPlaying = true
        }
    }

    val currentClip = timeline.clips.getOrNull(sceneIndex) ?: timeline.clips.firstOrNull()
    val latestSceneIndex by rememberUpdatedState(sceneIndex)

    LaunchedEffect(wantPlaying, currentClip?.url, sceneIndex, seekGeneration, audioReady) {
        val clip = currentClip ?: return@LaunchedEffect
        if (!audioReady) return@LaunchedEffect
        val startSec = seekStartMs / 1000.0
        loadWatchAudioAt(
            url = clip.url,
            startSec = startSec,
            play = wantPlaying,
            onTime = { currentSec, durationSec ->
                if (latestDragging) return@loadWatchAudioAt
                if (durationSec > 0.0) {
                    val durMs = (durationSec * 1000.0).toInt().coerceAtLeast(1)
                    val key = "sentence-${clip.sentenceIndex}"
                    if (measuredDurations[key] != durMs) {
                        measuredDurations[key] = durMs
                    }
                }
                val latest = latestTimeline
                val index = latest.clips.indexOfFirst { it.sentenceIndex == clip.sentenceIndex }
                    .takeIf { it >= 0 }
                    ?: latestSceneIndex
                val start = latest.startsMs.getOrNull(index) ?: 0
                positionMs = start + (currentSec * 1000.0).toInt()
            },
            onEnded = {
                val latest = latestTimeline
                val index = latest.clips.indexOfFirst { it.sentenceIndex == clip.sentenceIndex }
                    .takeIf { it >= 0 }
                    ?: latestSceneIndex
                val next = index + 1
                if (next in latest.clips.indices) {
                    sceneIndex = next
                    positionMs = latest.startsMs.getOrNull(next) ?: positionMs
                    seekStartMs = 0
                } else {
                    wantPlaying = false
                    positionMs = latest.totalMs
                    seekStartMs = latest.clips.lastOrNull()?.durationMs ?: 0
                }
            },
        )
    }

    DisposableEffect(dragging) {
        if (!dragging) {
            return@DisposableEffect onDispose { }
        }
        val onUp: (org.w3c.dom.events.Event) -> Unit = {
            dragging = false
            latestCommit.value(dragMsRef.value)
        }
        document.addEventListener("mouseup", onUp)
        document.addEventListener("touchend", onUp)
        onDispose {
            document.removeEventListener("mouseup", onUp)
            document.removeEventListener("touchend", onUp)
        }
    }

    val displayIndex = if (dragging) {
        watchSceneIndexAt(timeline, positionMs).let { index ->
            timeline.clips.getOrNull(index)?.sentenceIndex
        }
    } else {
        currentClip?.sentenceIndex
    } ?: sceneIndex.coerceIn(0, (session.sentences.size - 1).coerceAtLeast(0))
    val sentence = session.sentences.getOrNull(displayIndex)
    val imageFailKey = messageId?.let { "$it#$displayIndex" }
    val visualFailed = imageFailKey != null && imageFailed[imageFailKey] == true
    val targetImageUrl = watchImageUrl(sentence?.imageUrl)
    val preloadUrls = watchPreloadIndexes(displayIndex, session.sentences.size).mapNotNull { index ->
        watchImageUrl(session.sentences.getOrNull(index)?.imageUrl)
    }
    val decodedWatchImages = remember(session.text) { mutableStateMapOf<String, Boolean>() }
    val watchImageHolders = remember(session.text) { mutableMapOf<String, HTMLImageElement>() }
    var lastReadyImageUrl by remember(session.text) { mutableStateOf<String?>(null) }

    LaunchedEffect(preloadUrls.joinToString(), targetImageUrl) {
        val urls = buildList {
            if (targetImageUrl != null) add(targetImageUrl)
            addAll(preloadUrls)
        }.distinct()
        preloadWatchImages(urls, watchImageHolders, decodedWatchImages)
    }

    val targetDecoded = targetImageUrl != null && decodedWatchImages[targetImageUrl] == true
    LaunchedEffect(targetImageUrl, targetDecoded, visualFailed) {
        when {
            targetDecoded -> lastReadyImageUrl = targetImageUrl
            visualFailed && targetImageUrl == null -> lastReadyImageUrl = null
        }
    }

    val progressPercent = if (timeline.totalMs <= 0) {
        0.0
    } else {
        (positionMs.toDouble() / timeline.totalMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
    }
    val markers = watchSceneMarkerFractions(timeline)
    val overlayImageUrl = targetImageUrl?.takeIf { url ->
        url != lastReadyImageUrl && decodedWatchImages[url] == true
    }
    val showWatchError = visualFailed && targetImageUrl == null && lastReadyImageUrl == null
    val showWatchLoading = !showWatchError && lastReadyImageUrl == null && !targetDecoded

    fun seekFromClientX(clientX: Double, element: HTMLElement) {
        previewSeek(watchSeekMsFromClientX(clientX, element, timeline.totalMs))
    }

    Div(attrs = {
        classes(Styles.studyWatchPlayer)
        if (isFullscreen) classes(Styles.studyWatchPlayerFullscreen)
        ref { element ->
            playerElement = element
            onDispose { if (playerElement === element) playerElement = null }
        }
    }) {
        Div(attrs = { classes(Styles.studyWatchStage) }) {
            lastReadyImageUrl?.let { url ->
                Img(src = url, alt = strings.chatStudySentenceImageAlt, attrs = {
                    classes(Styles.studyWatchImage, Styles.studyWatchImageBkg)
                })
            }
            overlayImageUrl?.let { url ->
                Img(src = url, alt = strings.chatStudySentenceImageAlt, attrs = {
                    classes(Styles.studyWatchImage, Styles.studyWatchImageBkg)
                })
            }
            lastReadyImageUrl?.let { url ->
                Img(src = url, alt = strings.chatStudySentenceImageAlt, attrs = {
                    classes(Styles.studyWatchImage)
                })
            }
            overlayImageUrl?.let { url ->
                Img(src = url, alt = strings.chatStudySentenceImageAlt, attrs = {
                    classes(Styles.studyWatchImage)
                })
            }
            if (showWatchError) {
                Div(attrs = { classes(Styles.studyWatchStageStatus) }) {
                    MaterialSymbol("image_not_supported")
                    Span { Text(strings.chatStudySentenceImageError) }
                    StudyImageRetryButton(
                        label = strings.chatStudyRetrySentence,
                        onRetry = { onRetryImage(displayIndex) },
                    )
                }
            } else if (showWatchLoading) {
                Div(attrs = {
                    classes(Styles.studyWatchStageStatus, Styles.studySentenceImageStatusBusy)
                }) {
                    MaterialSymbol("progress_activity")
                    Span { Text(strings.chatStudySentenceImageLoading) }
                }
            }
        }
        Div(attrs = {
            classes(Styles.studyWatchSeek)
            attr("role", "slider")
            attr("aria-label", strings.chatWatchSeekLabel)
            attr("aria-valuemin", "0")
            attr("aria-valuemax", timeline.totalMs.toString())
            attr("aria-valuenow", positionMs.toString())
            if (!audioReady) attr("aria-disabled", "true")
            onMouseDown { event ->
                if (!audioReady) return@onMouseDown
                event.preventDefault()
                val el = event.nativeEvent.currentTarget as? HTMLElement ?: return@onMouseDown
                dragging = true
                seekFromClientX(event.clientX.toDouble(), el)
            }
            onMouseMove { event ->
                if (!dragging) return@onMouseMove
                val el = event.nativeEvent.currentTarget as? HTMLElement ?: return@onMouseMove
                seekFromClientX(event.clientX.toDouble(), el)
            }
            onTouchStart { event ->
                if (!audioReady) return@onTouchStart
                event.preventDefault()
                val touch = event.touches.item(0) ?: return@onTouchStart
                val el = event.nativeEvent.currentTarget as? HTMLElement ?: return@onTouchStart
                dragging = true
                seekFromClientX(touch.clientX.toDouble(), el)
            }
            onTouchMove { event ->
                if (!dragging) return@onTouchMove
                event.preventDefault()
                val touch = event.touches.item(0) ?: return@onTouchMove
                val el = event.nativeEvent.currentTarget as? HTMLElement ?: return@onTouchMove
                seekFromClientX(touch.clientX.toDouble(), el)
            }
        }) {
            Div(attrs = { classes(Styles.studyWatchSeekTrack) }) {
                Div(attrs = {
                    classes(Styles.studyWatchSeekFill)
                    style { property("width", "$progressPercent%") }
                })
                markers.forEach { fraction ->
                    Div(attrs = {
                        classes(Styles.studyWatchSeekMarker)
                        style { property("left", "${fraction * 100.0}%") }
                    })
                }
                Div(attrs = {
                    classes(Styles.studyWatchSeekThumb)
                    style { property("left", "$progressPercent%") }
                })
            }
        }
        if (!audioReady) {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatWatchPreparing)
            }
        }
        Div(attrs = { classes(Styles.studyWatchControls) }) {
            Button(attrs = {
                classes(Styles.sentencePlay)
                attr("type", "button")
                if (!audioReady) attr("disabled", "")
                attr(
                    "aria-label",
                    if (wantPlaying) strings.chatWatchPause else strings.chatWatchPlay,
                )
                onClick { togglePlay() }
            }) {
                MaterialSymbol(if (wantPlaying) "pause_circle" else "play_circle")
                Text(if (wantPlaying) strings.chatWatchPause else strings.chatWatchPlay)
            }
            Span(attrs = { classes(Styles.studyWatchTime) }) {
                Text("${formatWatchClock(positionMs)} / ${formatWatchClock(timeline.totalMs)}")
            }
            Button(attrs = {
                classes(Styles.sentencePlay, Styles.studyWatchFullscreen)
                attr("type", "button")
                attr(
                    "aria-label",
                    if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen,
                )
                attr("aria-pressed", isFullscreen.toString())
                onClick { setWatchFullscreen(!isFullscreen) }
            }) {
                MaterialSymbol(if (isFullscreen) "fullscreen_exit" else "fullscreen")
            }
        }
    }
}

@Composable
private fun StudyStoryTab(
    session: StudySessionResponse,
    messageId: String?,
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    sentenceAudio: Map<String, StudySentenceAudio>,
    strings: UiStrings,
    onRetryImage: (Int) -> Unit,
    scrollOnContinue: Boolean,
    playStudyToken: (sentenceIndex: Int, tokenIndex: Int, tokenText: String) -> Unit,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wordSteps = remember(session) { collectStoryWordSteps(session) }
    val sentenceCount = session.sentences.size
    var phase by remember(session.text) {
        mutableStateOf(
            if (wordSteps.isNotEmpty()) StudyStoryPhase.WORDS else StudyStoryPhase.SENTENCES,
        )
    }
    var stepIndex by remember(session.text) { mutableStateOf(0) }
    var lastAutoplayKey by remember(session.text) { mutableStateOf<String?>(null) }
    var replayNonce by remember(session.text) { mutableStateOf(0) }
    var expandedExampleKey by remember(session.text) { mutableStateOf<String?>(null) }
    var storyElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestIsFullscreen by rememberUpdatedState(isFullscreen)
    val latestStoryElement by rememberUpdatedState(storyElement)
    // Learner-language meaning clips (literal / context) keyed by spoken text.
    val meaningAudio = remember(session.text) { mutableStateMapOf<String, String>() }
    val exampleAudio = remember(session.text) { mutableStateMapOf<String, String>() }
    val learnerLanguageTag = Localization.language.tag

    fun speakExampleToken(text: String) {
        val spokenText = text.trim()
        if (spokenText.isEmpty()) return
        val cacheKey = "${session.textLanguageTag}|$spokenText"
        scope.launch {
            val url = exampleAudio[cacheKey] ?: runCatching {
                loadStudySpeechDataUrl(SpeechRequest(spokenText, session.textLanguageTag))
            }.getOrNull()?.also { exampleAudio[cacheKey] = it } ?: return@launch
            playAudio(url)
        }
    }

    fun advanceStory() {
        // Start each next story step from the top, wrapping to the first card at the end.
        if (scrollOnContinue) scrollStudyContentToTop()
        val (nextPhase, nextIndex) = nextStoryContinuePosition(
            phase = phase,
            stepIndex = stepIndex,
            wordStepCount = wordSteps.size,
            sentenceCount = sentenceCount,
        )
        phase = nextPhase
        stepIndex = nextIndex
        lastAutoplayKey = null
        replayNonce += 1
    }

    fun replayStoryAudio() {
        stopActiveStudyAudio()
        lastAutoplayKey = null
        replayNonce += 1
    }

    fun setStoryFullscreen(enabled: Boolean) {
        if (enabled) {
            isFullscreen = true
            storyElement?.let(::requestWatchFullscreen)
        } else {
            isFullscreen = false
            exitWatchFullscreen()
        }
    }

    // Keep the cursor valid as the session skeleton fills in.
    LaunchedEffect(phase, wordSteps.size, sentenceCount, stepIndex) {
        when (phase) {
            StudyStoryPhase.WORDS -> {
                if (wordSteps.isEmpty()) {
                    if (sentenceCount > 0) {
                        phase = StudyStoryPhase.SENTENCES
                        stepIndex = 0
                    } else {
                        onFinished()
                    }
                } else if (stepIndex > wordSteps.lastIndex) {
                    stepIndex = wordSteps.lastIndex
                }
            }
            StudyStoryPhase.SENTENCES -> {
                if (sentenceCount <= 0) {
                    onFinished()
                } else if (stepIndex > sentenceCount - 1) {
                    stepIndex = sentenceCount - 1
                }
            }
        }
    }

    val safeWordIndex = if (wordSteps.isEmpty()) 0 else stepIndex.coerceIn(0, wordSteps.lastIndex)
    val safeSentenceIndex =
        if (sentenceCount <= 0) 0 else stepIndex.coerceIn(0, sentenceCount - 1)
    val canGoBack = when (phase) {
        StudyStoryPhase.WORDS -> safeWordIndex > 0
        StudyStoryPhase.SENTENCES -> safeSentenceIndex > 0 || wordSteps.isNotEmpty()
    }

    fun goBackStory() {
        if (!canGoBack) return
        if (scrollOnContinue) scrollStudyContentToTop()
        when (phase) {
            StudyStoryPhase.WORDS -> stepIndex = safeWordIndex - 1
            StudyStoryPhase.SENTENCES -> {
                if (safeSentenceIndex > 0) {
                    stepIndex = safeSentenceIndex - 1
                } else {
                    phase = StudyStoryPhase.WORDS
                    stepIndex = wordSteps.lastIndex
                }
            }
        }
    }

    val totalSteps = wordSteps.size + sentenceCount
    val currentOrdinal = when (phase) {
        StudyStoryPhase.WORDS -> safeWordIndex + 1
        StudyStoryPhase.SENTENCES -> wordSteps.size + safeSentenceIndex + 1
    }.coerceAtLeast(1)
    val progressLabel = if (totalSteps > 0) "$currentOrdinal / $totalSteps" else ""

    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == "Escape" || key == "Esc") && latestIsFullscreen) {
                isFullscreen = false
                exitWatchFullscreen()
            }
        }
        val onFullscreen: (Event) -> Unit = {
            val current = currentFullscreenElement()
            val el = latestStoryElement
            when {
                el != null && current === el -> isFullscreen = true
                latestIsFullscreen && current == null -> isFullscreen = false
            }
        }
        window.addEventListener("keydown", onKey)
        document.addEventListener("fullscreenchange", onFullscreen)
        document.addEventListener("webkitfullscreenchange", onFullscreen)
        onDispose {
            window.removeEventListener("keydown", onKey)
            document.removeEventListener("fullscreenchange", onFullscreen)
            document.removeEventListener("webkitfullscreenchange", onFullscreen)
            exitWatchFullscreen()
        }
    }

    Div(attrs = {
        classes(Styles.studyStoryShell)
        if (isFullscreen) classes(Styles.studyStoryCardFullscreen)
        ref { element ->
            storyElement = element
            if (latestIsFullscreen) requestWatchFullscreen(element)
            onDispose { if (storyElement === element) storyElement = null }
        }
    }) {
    when (phase) {
        StudyStoryPhase.WORDS -> {
            val step = wordSteps.getOrNull(safeWordIndex)
            val token = step?.let {
                session.sentences.getOrNull(it.sentenceIndex)?.tokens?.getOrNull(it.tokenIndex)
            }
            if (step == null || token == null) {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatStudyPreparingMeanings)
                }
            } else {
            val wordFailKey = messageId?.let { mid ->
                "$mid#${step.sentenceIndex}#${step.tokenIndex}"
            }
            val wordFailed = wordFailKey != null && wordImageFailed[wordFailKey] == true
            val example = step.exampleIndex?.let(token.exampleSentences::getOrNull)
            val exampleKey = "${step.sentenceIndex}-${step.tokenIndex}-${step.exampleIndex}"
            val isExampleBreakdownVisible = expandedExampleKey == exampleKey
            val sentenceClipUrl = sentenceAudio["sentence-${step.sentenceIndex}"]?.url
            val meaningTexts = collectStoryMeaningNarrationTexts(
                literalMeaning = token.literalMeaning,
                contextualMeaning = token.contextualMeaning,
            )
            // Include clip url so the first timed segment can replace a missing-timing attempt.
            // Meaning texts are part of the key so late-arriving dissections also get spoken.
            val wordAutoplayKey =
                "word-${step.sentenceIndex}-${step.tokenIndex}-${token.text}|${sentenceClipUrl.orEmpty()}|$replayNonce"
            val autoplayKey = if (example != null) {
                "example-${step.sentenceIndex}-${step.tokenIndex}-${step.exampleIndex}|${example.text}|${example.translation}|$replayNonce"
            } else {
                "$wordAutoplayKey#${meaningTexts.joinToString(separator = "\u0001")}"
            }
            LaunchedEffect(autoplayKey) {
                if (lastAutoplayKey == autoplayKey) return@LaunchedEffect
                if (example != null) {
                    stopActiveStudyAudio()
                    lastAutoplayKey = autoplayKey
                    val clips = listOf(
                        example.text.trim() to session.textLanguageTag,
                        example.translation.trim() to learnerLanguageTag,
                    ).filter { (text, _) -> text.isNotEmpty() }
                    val urls = mutableListOf<String>()
                    for ((text, languageTag) in clips) {
                        val audioKey = "$languageTag|$text"
                        val cached = exampleAudio[audioKey]
                        if (cached != null) {
                            urls += cached
                            continue
                        }
                        val url = runCatching {
                            loadStudySpeechDataUrl(SpeechRequest(text, languageTag))
                        }.getOrNull()
                        if (url != null) {
                            exampleAudio[audioKey] = url
                            urls += url
                        }
                    }
                    if (lastAutoplayKey == autoplayKey) playAudioSequence(urls)
                    return@LaunchedEffect
                }
                val previousKey = lastAutoplayKey
                lastAutoplayKey = autoplayKey
                val previousWordKey = previousKey?.substringBefore('#')
                val shouldPlayWord = previousWordKey != wordAutoplayKey
                if (shouldPlayWord) {
                    playStudyToken(step.sentenceIndex, step.tokenIndex, token.text)
                }
                if (meaningTexts.isEmpty()) return@LaunchedEffect

                // Prefetch meaning TTS while the word clip plays (learner UI language).
                val meaningUrls = mutableListOf<String>()
                for (text in meaningTexts) {
                    val cached = meaningAudio[text]
                    if (cached != null) {
                        meaningUrls += cached
                        continue
                    }
                    val url = runCatching {
                        loadStudySpeechDataUrl(SpeechRequest(text, learnerLanguageTag))
                    }.getOrNull()
                    if (url != null) {
                        meaningAudio[text] = url
                        meaningUrls += url
                    }
                }
                if (meaningUrls.isEmpty()) return@LaunchedEffect

                // Wait for the word clip: allow slow token-TTS fallback to start, then
                // drain whatever is playing so meanings do not overlap it.
                if (shouldPlayWord) {
                    var waitedForStartMs = 0
                    while (studyAudioPlayingUrl == null && waitedForStartMs < 4_000) {
                        delay(40)
                        waitedForStartMs += 40
                    }
                } else {
                    delay(80)
                }
                while (studyAudioPlayingUrl != null) {
                    delay(40)
                }
                if (lastAutoplayKey != autoplayKey) return@LaunchedEffect
                playAudioSequence(meaningUrls)
            }

            Div(attrs = { classes(Styles.studyStoryCard) }) {
                StudyStoryHeader(
                    progressLabel = progressLabel,
                    strings = strings,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { setStoryFullscreen(!isFullscreen) },
                )
                StudyStoryVisual(
                    imageUrl = token.imageUrl,
                    failed = wordFailed,
                    loadingLabel = strings.chatStudyWordImageLoading,
                    errorLabel = strings.chatStudyWordImageError,
                    alt = strings.chatStudyWordImageAlt,
                    onReplay = { replayStoryAudio() },
                )
                if (example != null) {
                    Div(attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(1.cssRem)
                        }
                    }) {
                        StudyExampleSentenceText(
                            sentence = example.text,
                            word = token.text,
                            paragraphClass = Styles.studyStoryWord,
                            expanded = isExampleBreakdownVisible,
                            onToggleBreakdown = {
                                expandedExampleKey = toggleStudyExampleBreakdown(expandedExampleKey, exampleKey)
                            },
                        )
                        if (example.translation.isNotBlank()) {
                            P(attrs = { classes(Styles.studyStoryMeaning) }) {
                                Text(example.translation)
                            }
                        }
                        if (isExampleBreakdownVisible && example.tokens.isNotEmpty()) {
                            StudyExampleBreakdown(example, strings, ::speakExampleToken)
                        }
                    }
                } else {
                    P(attrs = {
                        classes(Styles.studyStoryWord)
                        onClick { replayStoryAudio() }
                    }) { Text(token.text) }
                    when {
                        token.status == StudyTokenStatus.PENDING || !token.isDissectable -> {
                            P(attrs = { classes(Styles.studyStoryMeaning) }) {
                                Text(strings.chatStudyMeaningLoading)
                            }
                        }
                        else -> {
                            if (token.literalMeaning.isNotBlank()) {
                                Span(attrs = { classes(Styles.studyStoryMeaningLabel) }) {
                                    Text(strings.chatStudyLiteral)
                                }
                                P(attrs = { classes(Styles.studyStoryMeaning) }) {
                                    Text(token.literalMeaning)
                                }
                            }
                            if (token.contextualMeaning.isNotBlank()) {
                                Span(attrs = { classes(Styles.studyStoryMeaningLabel) }) {
                                    Text(strings.chatStudyContext)
                                }
                                P(attrs = { classes(Styles.studyStoryMeaning) }) {
                                    Text(token.contextualMeaning)
                                }
                            }
                            if (token.literalMeaning.isBlank() && token.contextualMeaning.isBlank()) {
                                P(attrs = { classes(Styles.studyStoryMeaning) }) {
                                    Text(strings.chatStudyMeaningLoading)
                                }
                            }
                        }
                    }
                }
                StudyStoryNavRow(
                    strings = strings,
                    canGoBack = canGoBack,
                    onBack = { goBackStory() },
                    onContinue = { advanceStory() },
                )
            }
            }
        }
        StudyStoryPhase.SENTENCES -> {
            if (sentenceCount <= 0) {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatStudyPreparingMeanings)
                }
            } else {
            val sentence = session.sentences[safeSentenceIndex]
            val imageFailKey = messageId?.let { "$it#$safeSentenceIndex" }
            val visualFailed = imageFailKey != null && imageFailed[imageFailKey] == true
            val sentenceClip = sentenceAudio["sentence-$safeSentenceIndex"]
            val autoplayKey = "sentence-$safeSentenceIndex-${sentenceClip?.url.orEmpty()}|$replayNonce"
            LaunchedEffect(autoplayKey) {
                val url = sentenceClip?.url ?: return@LaunchedEffect
                if (lastAutoplayKey == autoplayKey) return@LaunchedEffect
                lastAutoplayKey = autoplayKey
                playAudio(url, restart = true)
            }

            Div(attrs = { classes(Styles.studyStoryCard) }) {
                StudyStoryHeader(
                    progressLabel = progressLabel,
                    strings = strings,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { setStoryFullscreen(!isFullscreen) },
                )
                StudyStoryVisual(
                    imageUrl = sentence.imageUrl,
                    failed = visualFailed,
                    loadingLabel = strings.chatStudySentenceImageLoading,
                    errorLabel = strings.chatStudySentenceImageError,
                    alt = strings.chatStudySentenceImageAlt,
                    retryLabel = strings.chatStudyRetrySentence,
                    onRetry = { onRetryImage(safeSentenceIndex) },
                    onReplay = { replayStoryAudio() },
                )
                P(attrs = {
                    classes(Styles.studyStorySentence)
                    onClick { replayStoryAudio() }
                }) { Text(sentence.text) }
                StudyStoryNavRow(
                    strings = strings,
                    canGoBack = canGoBack,
                    onBack = { goBackStory() },
                    onContinue = { advanceStory() },
                )
            }
            }
        }
    }
    }
}

@Composable
private fun StudyStoryHeader(
    progressLabel: String,
    strings: UiStrings,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    Div(attrs = { classes(Styles.studyStoryHeader) }) {
        if (progressLabel.isNotEmpty()) {
            Span(attrs = { classes(Styles.studyStoryProgress) }) { Text(progressLabel) }
        }
        Button(attrs = {
            classes(Styles.sentencePlay, Styles.studyStoryFullscreen)
            attr("type", "button")
            attr(
                "aria-label",
                if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen,
            )
            attr("aria-pressed", isFullscreen.toString())
            onClick { onToggleFullscreen() }
        }) {
            MaterialSymbol(if (isFullscreen) "fullscreen_exit" else "fullscreen")
        }
    }
}

@Composable
private fun StudyStoryNavRow(
    strings: UiStrings,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Div(attrs = { classes(Styles.studyStoryActions) }) {
        Button(attrs = {
            classes(Styles.btnSecondary, Styles.studyStoryBack)
            attr("type", "button")
            if (!canGoBack) attr("disabled", "")
            onClick { onBack() }
        }) {
            Text(strings.chatStoryBack)
        }
        Button(attrs = {
            classes(Styles.btnPrimary, Styles.studyStoryContinue)
            attr("type", "button")
            onClick { onContinue() }
        }) {
            Text(strings.chatStoryContinue)
        }
    }
}

@Composable
private fun StudyStoryVisual(
    imageUrl: String?,
    failed: Boolean,
    loadingLabel: String,
    errorLabel: String,
    alt: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    onReplay: (() -> Unit)? = null,
) {
    val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    when {
        url != null -> {
            Img(src = url, alt = alt, attrs = {
                classes(Styles.studyStoryImage)
                onClick { onReplay?.invoke() }
            })
        }
        failed -> {
            Div(attrs = { classes(Styles.studyStoryImagePlaceholder) }) {
                MaterialSymbol("image_not_supported")
                Span { Text(errorLabel) }
                if (onRetry != null && retryLabel != null) {
                    StudyImageRetryButton(label = retryLabel, onRetry = onRetry)
                }
            }
        }
        else -> {
            Div(attrs = { classes(Styles.studyStoryImagePlaceholder) }) {
                MaterialSymbol("progress_activity")
                Span { Text(loadingLabel) }
            }
        }
    }
}

/** Compares learner input to a study sentence after light whitespace normalization. */
internal fun studyWriteMatches(expected: String, actual: String): Boolean {
    fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
    return normalize(expected) == normalize(actual)
}

/**
 * Case-insensitive ranges of [word] inside [sentence] for example-sentence highlighting.
 * Non-overlapping left-to-right matches; empty when either side is blank or nothing matches.
 */
internal fun studyExampleHighlightRanges(sentence: String, word: String): List<IntRange> {
    val needle = word.trim()
    if (needle.isEmpty() || sentence.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = 0
    while (start <= sentence.length - needle.length) {
        val idx = sentence.indexOf(needle, startIndex = start, ignoreCase = true)
        if (idx < 0) break
        ranges += idx until (idx + needle.length)
        start = idx + needle.length
    }
    return ranges
}

internal fun toggleStudyExampleBreakdown(expandedKey: String?, selectedKey: String): String? =
    if (expandedKey == selectedKey) null else selectedKey

@Composable
private fun StudyExampleSentenceText(
    sentence: String,
    word: String,
    paragraphClass: String? = null,
    expanded: Boolean = false,
    onToggleBreakdown: (() -> Unit)? = null,
) {
    P(attrs = {
        paragraphClass?.let { classes(it) }
        if (onToggleBreakdown != null) {
            attr("role", "button")
            attr("tabindex", "0")
            attr("aria-expanded", expanded.toString())
            onClick { onToggleBreakdown() }
            onKeyDown { event ->
                if (event.key == "Enter" || event.key == " ") {
                    event.preventDefault()
                    onToggleBreakdown()
                }
            }
        }
    }) {
        val ranges = studyExampleHighlightRanges(sentence, word)
        if (ranges.isEmpty()) {
            Text(sentence)
            return@P
        }
        var cursor = 0
        ranges.forEach { range ->
            if (cursor < range.first) {
                Text(sentence.substring(cursor, range.first))
            }
            B(attrs = { classes(Styles.studyExampleWord) }) {
                Text(sentence.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < sentence.length) {
            Text(sentence.substring(cursor))
        }
    }
}

@Composable
private fun StudyExampleBreakdown(
    example: StudyExampleSentence,
    strings: UiStrings,
    onWordClick: (String) -> Unit,
) {
    Table(attrs = {
        classes(Styles.studyWordsTable)
        attr("aria-label", strings.chatWordsTab)
    }) {
        Thead {
            Tr {
                Th { Text(strings.chatWordsWord) }
                Th { Text(strings.chatWordsMeaning) }
            }
        }
        Tbody {
            example.tokens.forEach { token ->
                Tr {
                    Td {
                        Button(attrs = {
                            classes(Styles.studyWordsWord)
                            attr("type", "button")
                            onClick { onWordClick(token.text) }
                        }) { Text(token.text) }
                    }
                    Td {
                        P {
                            B {
                                Text(token.translation)
                            }
                        }
                        if (token.contextualMeaning != token.translation) {
                            P {
                                Text(token.contextualMeaning)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collects Express prompts from dissected token example sentences.
 * Prefer [StudyExampleSentence.translation] (learner language) as the shown meaning.
 */
internal fun collectExpressPracticeSentences(session: StudySessionResponse): List<StudyExampleSentence> {
    val fromTokens = session.sentences.flatMap { sentence ->
        sentence.tokens.flatMap { it.exampleSentences }
    }
    return mergePracticeSentences(fromTokens, session.practiceSentences)
}

internal fun mergePracticeSentences(
    existing: List<StudyExampleSentence>,
    additions: List<StudyExampleSentence>,
): List<StudyExampleSentence> {
    val seen = linkedSetOf<String>()
    val result = mutableListOf<StudyExampleSentence>()
    (existing + additions).forEach { example ->
        val text = example.text.trim()
        val translation = example.translation.trim()
        if (text.isEmpty() && translation.isEmpty()) return@forEach
        val meaning = translation.ifEmpty { text }
        val key = "${meaning.lowercase()}|${text.lowercase()}"
        if (!seen.add(key)) return@forEach
        result += example.copy(text = text, translation = meaning)
    }
    return result
}

internal fun studyPracticeHintText(
    example: StudyExampleSentence,
    vocabulary: List<StudyToken> = emptyList(),
): String {
    val exampleMeanings = example.tokens.mapNotNull { token ->
        if (token.text.none(Char::isLetterOrDigit)) return@mapNotNull null
        studyPracticeCombinedMeaning(token.translation, token.contextualMeaning)
    }
    val meanings = exampleMeanings.ifEmpty {
        studyPracticeVocabularyHintMeanings(example.text, vocabulary)
    }
    return meanings.joinToString(" • ")
}

private data class StudyPracticeHintMatch(
    val startIndex: Int,
    val endIndex: Int,
    val meaning: String,
)

private fun studyPracticeVocabularyHintMeanings(
    sentence: String,
    vocabulary: List<StudyToken>,
): List<String> {
    val allowUnboundedMatches = sentence.none(Char::isWhitespace) && sentence.any { char ->
        char in '\u0e00'..'\u0e7f' ||
            char in '\u1000'..'\u109f' ||
            char in '\u3040'..'\u30ff' ||
            char in '\u3400'..'\u9fff' ||
            char in '\uac00'..'\ud7af'
    }
    val matches = vocabulary.flatMap { token ->
        val word = token.text.trim().takeIf(String::isNotEmpty) ?: return@flatMap emptyList()
        if (token.status == StudyTokenStatus.PLAIN) return@flatMap emptyList()
        val meaning = studyPracticeCombinedMeaning(token.literalMeaning, token.contextualMeaning)
            ?: return@flatMap emptyList()
        val wordMatches = mutableListOf<StudyPracticeHintMatch>()
        var searchFrom = 0
        while (searchFrom < sentence.length) {
            val startIndex = sentence.indexOf(word, searchFrom, ignoreCase = true)
            if (startIndex < 0) break
            val endIndex = startIndex + word.length
            val hasBoundaries = (startIndex == 0 || !sentence[startIndex - 1].isLetterOrDigit()) &&
                (endIndex == sentence.length || !sentence[endIndex].isLetterOrDigit())
            if (allowUnboundedMatches || hasBoundaries) {
                wordMatches += StudyPracticeHintMatch(startIndex, endIndex, meaning)
            }
            searchFrom = startIndex + 1
        }
        wordMatches
    }

    val selectedMeanings = mutableListOf<String>()
    var cursor = 0
    matches.sortedWith(
        compareBy<StudyPracticeHintMatch> { it.startIndex }
            .thenByDescending { it.endIndex - it.startIndex },
    ).forEach { match ->
        if (match.startIndex >= cursor) {
            selectedMeanings += match.meaning
            cursor = match.endIndex
        }
    }
    return selectedMeanings
}

private fun studyPracticeCombinedMeaning(literal: String, contextual: String): String? =
    listOf(literal.trim(), contextual.trim())
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
        .joinToString("; ")
        .takeIf(String::isNotEmpty)

private fun List<StudyExampleSentence>.shuffledPracticeSentences(): List<StudyExampleSentence> =
    if (size < 2) this else shuffled()

internal fun collectExpressVocabulary(session: StudySessionResponse): List<String> =
    session.sentences
        .flatMap { it.tokens }
        .filter { it.status != StudyTokenStatus.PLAIN && it.text.isNotBlank() }
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

@Composable
private fun StudyExpressTab(
    session: StudySessionResponse,
    anyLoading: Boolean,
    strings: UiStrings,
    autoFocus: Boolean,
    onPracticeSentences: (List<StudyExampleSentence>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    val sessionExamples = remember(session) { collectExpressPracticeSentences(session) }
    val vocabulary = remember(session) { collectExpressVocabulary(session) }
    val hintVocabulary = remember(session) { session.sentences.flatMap { it.tokens } }

    var queue by remember(session.text) { mutableStateOf(sessionExamples.shuffledPracticeSentences()) }
    var correctItems by remember(session.text) { mutableStateOf(emptySet<Int>()) }
    var generating by remember(session.text) { mutableStateOf(false) }
    var generateError by remember(session.text) { mutableStateOf<String?>(null) }

    val meaningsStillLoading = anyLoading || session.sentences.any { sentence ->
        sentence.tokens.any { it.status == StudyTokenStatus.PENDING }
    }

    // Merge newly dissected examples as they arrive (without resetting progress).
    LaunchedEffect(sessionExamples) {
        if (sessionExamples.isEmpty()) return@LaunchedEffect
        val existingKeys = queue.map {
            "${it.translation.trim().lowercase()}|${it.text.trim().lowercase()}"
        }.toMutableSet()
        val additions = sessionExamples.filter { example ->
            val key = "${example.translation.trim().lowercase()}|${example.text.trim().lowercase()}"
            existingKeys.add(key)
        }
        if (additions.isNotEmpty()) {
            queue = queue + additions.shuffledPracticeSentences()
        }
    }

    fun generateMore() {
        if (generating) return
        generating = true
        generateError = null
        scope.launch {
            runCatching {
                ChatApi.studyExpressGenerate(
                    StudyExpressGenerateRequest(
                        text = session.text,
                        textLanguageTag = session.textLanguageTag,
                        languageTag = languageTag,
                        vocabulary = vocabulary,
                        previousSentences = queue.map { it.text }.filter { it.isNotBlank() },
                    ),
                )
            }.onSuccess { response ->
                val fresh = response.sentences.mapNotNull { item ->
                    val text = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val translation = item.translation.trim().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    StudyExampleSentence(text = text, translation = translation)
                }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh.shuffledPracticeSentences()
                    onPracticeSentences(fresh)
                } else {
                    generateError = strings.chatExpressFeedbackError
                }
            }.onFailure {
                generateError = strings.chatExpressFeedbackError
            }
            generating = false
        }
    }

    val correctCount = correctItems.count { it in queue.indices }
    LaunchedEffect(session.text, queue.size, correctCount, meaningsStillLoading, generating, generateError) {
        if (
            !meaningsStillLoading && !generating && generateError == null &&
            (queue.isEmpty() || correctCount == queue.size)
        ) {
            generateMore()
        }
    }

    Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatExpressHint) }
    queue.forEachIndexed { index, item ->
        StudyExpressPracticeCard(
            item = item,
            itemIndex = index,
            sessionText = session.text,
            textLanguageTag = session.textLanguageTag,
            languageTag = languageTag,
            strings = strings,
            hintVocabulary = hintVocabulary,
            autoFocus = autoFocus && index == 0,
            onCorrect = { correctItems = correctItems + index },
        )
    }
    when {
        generating -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressGenerating)
            }
        }
        queue.isEmpty() && meaningsStillLoading -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressLoading)
            }
        }
        generateError != null -> {
            Div(attrs = { classes(Styles.studyWriteBlock) }) {
                P(attrs = {
                    classes(Styles.studyWriteFeedback)
                    attr("role", "status")
                }) { Text(generateError!!) }
                Button(attrs = {
                    classes(Styles.chip)
                    attr("type", "button")
                    onClick {
                        generateError = null
                        generateMore()
                    }
                }) { Text(strings.chatPlayRetry) }
            }
        }
        queue.isEmpty() -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressEmpty)
            }
        }
    }
}

@Composable
private fun StudyExpressPracticeCard(
    item: StudyExampleSentence,
    itemIndex: Int,
    sessionText: String,
    textLanguageTag: String,
    languageTag: String,
    strings: UiStrings,
    hintVocabulary: List<StudyToken>,
    autoFocus: Boolean,
    onCorrect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val exampleAudio = remember(sessionText, textLanguageTag) { mutableStateMapOf<String, String>() }
    var hintVisible by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    val hintText = remember(item, hintVocabulary) { studyPracticeHintText(item, hintVocabulary) }
    var draft by remember(sessionText, itemIndex, item.text, item.translation) { mutableStateOf("") }
    var checking by remember(sessionText, itemIndex, item.text, item.translation) { mutableStateOf(false) }
    var feedback by remember(sessionText, itemIndex, item.text, item.translation) { mutableStateOf<String?>(null) }
    var correct by remember(sessionText, itemIndex, item.text, item.translation) { mutableStateOf(false) }

    fun speakTargetSentence() {
        val spokenText = item.text.trim()
        if (spokenText.isEmpty()) return
        val cacheKey = "$textLanguageTag|$spokenText"
        scope.launch {
            val url = exampleAudio[cacheKey] ?: runCatching {
                loadStudySpeechDataUrl(SpeechRequest(spokenText, textLanguageTag))
            }.getOrNull()?.also { exampleAudio[cacheKey] = it } ?: return@launch
            playAudio(url, restart = true)
        }
    }

    fun check(value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || checking || correct) return
        draft = candidate
        feedback = null
        checking = true
        scope.launch {
            val result = runCatching {
                ChatApi.studyExpressCheck(
                    StudyExpressCheckRequest(
                        meaning = item.translation.ifBlank { item.text },
                        attempt = candidate,
                        textLanguageTag = textLanguageTag,
                        languageTag = languageTag,
                        referenceText = item.text,
                    ),
                )
            }
            if (draft.trim() == candidate) {
                result.onSuccess { response ->
                    if (response.correct) {
                        correct = true
                        feedback = null
                        onCorrect()
                    } else {
                        feedback = response.feedback.trim().takeIf { it.isNotEmpty() }
                            ?: strings.chatExpressFeedbackError
                    }
                }.onFailure {
                    feedback = strings.chatExpressFeedbackError
                }
            }
            checking = false
        }
    }

    Div(attrs = { classes(Styles.studyWriteBlock) }) {
        P(attrs = { classes(Styles.studyWritePrompt) }) {
            Text(item.translation.ifBlank { item.text })
        }
        if (correct) {
            Div(attrs = {
                classes(Styles.studyWriteCorrect)
                attr("role", "status")
            }) {
                MaterialSymbol("check_circle")
                Span { Text(strings.chatExpressCorrect) }
            }
        } else {
            if (hintVisible && hintText.isNotEmpty()) {
                P(attrs = { classes(Styles.studyPracticeHint) }) { Text(hintText) }
            }
            Div(attrs = { classes(Styles.studyPracticeInputRow) }) {
                ChatInput(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (feedback != null) feedback = null
                    },
                    onSend = { check(it) },
                    placeholder = strings.chatExpressPlaceholder,
                    languageTag = textLanguageTag,
                    multiline = item.translation.length > 48,
                    autoFocus = autoFocus,
                )
                StudyPracticeRevealButton(strings) {
                    draft = item.text
                    feedback = null
                    speakTargetSentence()
                }
                if (hintText.isNotEmpty()) {
                    StudyPracticeHintButton(strings, hintVisible) { hintVisible = !hintVisible }
                }
            }
            when {
                checking -> {
                    Div(attrs = {
                        classes(Styles.studyWriteChecking)
                        attr("role", "status")
                    }) {
                        MaterialSymbol("progress_activity")
                        Span { Text(strings.chatExpressChecking) }
                    }
                }
                feedback != null -> {
                    P(attrs = {
                        classes(Styles.studyWriteFeedback)
                        attr("role", "status")
                    }) { Text(feedback!!) }
                }
            }
        }
    }
}

@Composable
private fun StudyListenTab(
    session: StudySessionResponse,
    anyLoading: Boolean,
    strings: UiStrings,
    autoFocus: Boolean,
    onPracticeSentences: (List<StudyExampleSentence>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    val sessionExamples = remember(session) { collectExpressPracticeSentences(session) }
    val vocabulary = remember(session) { collectExpressVocabulary(session) }
    val hintVocabulary = remember(session) { session.sentences.flatMap { it.tokens } }
    val audioByText = remember(session.text) { mutableStateMapOf<String, String>() }
    val audioLoading = remember(session.text) { mutableStateMapOf<String, Boolean>() }
    val audioFailed = remember(session.text) { mutableStateMapOf<String, Boolean>() }

    var queue by remember(session.text) { mutableStateOf(sessionExamples.shuffledPracticeSentences()) }
    var correctItems by remember(session.text) { mutableStateOf(emptySet<Int>()) }
    var generating by remember(session.text) { mutableStateOf(false) }
    var generateError by remember(session.text) { mutableStateOf<String?>(null) }

    val meaningsStillLoading = anyLoading || session.sentences.any { sentence ->
        sentence.tokens.any { it.status == StudyTokenStatus.PENDING }
    }

    LaunchedEffect(sessionExamples) {
        if (sessionExamples.isEmpty()) return@LaunchedEffect
        val existingKeys = queue.map {
            "${it.translation.trim().lowercase()}|${it.text.trim().lowercase()}"
        }.toMutableSet()
        val additions = sessionExamples.filter { example ->
            val key = "${example.translation.trim().lowercase()}|${example.text.trim().lowercase()}"
            existingKeys.add(key)
        }
        if (additions.isNotEmpty()) queue = queue + additions.shuffledPracticeSentences()
    }

    fun loadAudio(text: String, onReady: ((String) -> Unit)? = null) {
        audioByText[text]?.let {
            onReady?.invoke(it)
            return
        }
        if (audioLoading[text] == true) return
        audioFailed.remove(text)
        audioLoading[text] = true
        scope.launch {
            val result = runCatching {
                loadStudySpeechDataUrl(SpeechRequest(text, session.textLanguageTag))
            }
            result.onSuccess { url ->
                audioByText[text] = url
                audioFailed.remove(text)
                onReady?.invoke(url)
            }.onFailure {
                audioFailed[text] = true
            }
            audioLoading[text] = false
        }
    }

    LaunchedEffect(queue.map { it.text }, session.textLanguageTag) {
        queue.map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .forEach { text -> loadAudio(text) }
    }

    fun generateMore() {
        if (generating) return
        generating = true
        generateError = null
        scope.launch {
            runCatching {
                ChatApi.studyExpressGenerate(
                    StudyExpressGenerateRequest(
                        text = session.text,
                        textLanguageTag = session.textLanguageTag,
                        languageTag = languageTag,
                        vocabulary = vocabulary,
                        previousSentences = queue.map { it.text }.filter { it.isNotBlank() },
                    ),
                )
            }.onSuccess { response ->
                val fresh = response.sentences.mapNotNull { item ->
                    val text = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val translation = item.translation.trim().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    StudyExampleSentence(text = text, translation = translation)
                }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh.shuffledPracticeSentences()
                    onPracticeSentences(fresh)
                } else {
                    generateError = strings.chatExpressFeedbackError
                }
            }.onFailure {
                generateError = strings.chatExpressFeedbackError
            }
            generating = false
        }
    }

    val correctCount = correctItems.count { it in queue.indices }
    LaunchedEffect(session.text, queue.size, correctCount, meaningsStillLoading, generating, generateError) {
        if (
            !meaningsStillLoading && !generating && generateError == null &&
            (queue.isEmpty() || correctCount == queue.size)
        ) {
            generateMore()
        }
    }

    Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatListenHint) }
    queue.forEachIndexed { index, item ->
        val audioText = item.text.trim()
        key(index, item.text, item.translation) {
            StudyListenPracticeCard(
                item = item,
                itemIndex = index,
                sessionText = session.text,
                textLanguageTag = session.textLanguageTag,
                audioUrl = audioByText[audioText],
                audioLoading = audioLoading[audioText] == true,
                audioFailed = audioFailed[audioText] == true,
                languageTag = languageTag,
                strings = strings,
                hintVocabulary = hintVocabulary,
                autoFocus = autoFocus && index == 0,
                onRequestAudio = { onReady -> loadAudio(audioText, onReady) },
                onCorrect = { correctItems = correctItems + index },
            )
        }
    }
    when {
        generating -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressGenerating)
            }
        }
        queue.isEmpty() && meaningsStillLoading -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressLoading)
            }
        }
        generateError != null -> {
            Div(attrs = { classes(Styles.studyWriteBlock) }) {
                P(attrs = {
                    classes(Styles.studyWriteFeedback)
                    attr("role", "status")
                }) { Text(generateError!!) }
                Button(attrs = {
                    classes(Styles.chip)
                    attr("type", "button")
                    onClick {
                        generateError = null
                        generateMore()
                    }
                }) { Text(strings.chatPlayRetry) }
            }
        }
        queue.isEmpty() -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressEmpty)
            }
        }
    }
}

@Composable
private fun StudySpeakTab(
    session: StudySessionResponse,
    anyLoading: Boolean,
    strings: UiStrings,
    onPracticeSentences: (List<StudyExampleSentence>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    val sessionExamples = remember(session) { collectExpressPracticeSentences(session) }
    val vocabulary = remember(session) { collectExpressVocabulary(session) }
    val hintVocabulary = remember(session) { session.sentences.flatMap { it.tokens } }
    var queue by remember(session.text) { mutableStateOf(sessionExamples.shuffledPracticeSentences()) }
    var completedItems by remember(session.text) { mutableStateOf(emptySet<Int>()) }
    var activeCardKey by remember(session.text) { mutableStateOf<String?>(null) }
    var generating by remember(session.text) { mutableStateOf(false) }
    var generateError by remember(session.text) { mutableStateOf<String?>(null) }

    val meaningsStillLoading = anyLoading || session.sentences.any { sentence ->
        sentence.tokens.any { it.status == StudyTokenStatus.PENDING }
    }

    LaunchedEffect(sessionExamples) {
        if (sessionExamples.isEmpty()) return@LaunchedEffect
        val existingKeys = queue.map {
            "${it.translation.trim().lowercase()}|${it.text.trim().lowercase()}"
        }.toMutableSet()
        val additions = sessionExamples.filter { example ->
            val key = "${example.translation.trim().lowercase()}|${example.text.trim().lowercase()}"
            existingKeys.add(key)
        }
        if (additions.isNotEmpty()) queue = queue + additions.shuffledPracticeSentences()
    }

    fun generateMore() {
        if (generating) return
        generating = true
        generateError = null
        scope.launch {
            runCatching {
                ChatApi.studyExpressGenerate(
                    StudyExpressGenerateRequest(
                        text = session.text,
                        textLanguageTag = session.textLanguageTag,
                        languageTag = languageTag,
                        vocabulary = vocabulary,
                        previousSentences = queue.map { it.text }.filter { it.isNotBlank() },
                    ),
                )
            }.onSuccess { response ->
                val fresh = response.sentences.mapNotNull { item ->
                    val text = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val translation = item.translation.trim().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    StudyExampleSentence(text = text, translation = translation)
                }
                if (fresh.isNotEmpty()) {
                    queue = queue + fresh.shuffledPracticeSentences()
                    onPracticeSentences(fresh)
                } else {
                    generateError = strings.chatExpressFeedbackError
                }
            }.onFailure {
                generateError = strings.chatExpressFeedbackError
            }
            generating = false
        }
    }

    val completedCount = completedItems.count { it in queue.indices }
    LaunchedEffect(session.text, queue.size, completedCount, meaningsStillLoading, generating, generateError) {
        if (
            !meaningsStillLoading && !generating && generateError == null &&
            (queue.isEmpty() || completedCount == queue.size)
        ) {
            generateMore()
        }
    }

    Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatSpeakHint) }
    queue.forEachIndexed { index, item ->
        val cardKey = "$index|${item.translation}|${item.text}"
        key(index, item.text, item.translation) {
            StudySpeakPracticeCard(
                item = item,
                itemIndex = index,
                sessionText = session.text,
                textLanguageTag = session.textLanguageTag,
                strings = strings,
                hintVocabulary = hintVocabulary,
                canStart = activeCardKey == null || activeCardKey == cardKey,
                onActiveChange = { active ->
                    activeCardKey = if (active) cardKey else activeCardKey.takeUnless { it == cardKey }
                },
                onComplete = { completedItems = completedItems + index },
            )
        }
    }
    when {
        generating -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressGenerating)
            }
        }
        queue.isEmpty() && meaningsStillLoading -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressLoading)
            }
        }
        generateError != null -> {
            Div(attrs = { classes(Styles.studyWriteBlock) }) {
                P(attrs = {
                    classes(Styles.studyWriteFeedback)
                    attr("role", "status")
                }) { Text(generateError!!) }
                Button(attrs = {
                    classes(Styles.chip)
                    attr("type", "button")
                    onClick {
                        generateError = null
                        generateMore()
                    }
                }) { Text(strings.chatPlayRetry) }
            }
        }
        queue.isEmpty() -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressEmpty)
            }
        }
    }
}

@Composable
private fun StudySpeakPracticeCard(
    item: StudyExampleSentence,
    itemIndex: Int,
    sessionText: String,
    textLanguageTag: String,
    strings: UiStrings,
    hintVocabulary: List<StudyToken>,
    canStart: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember(sessionText, itemIndex, item.text) { mutableStateOf(StudySpeakProgress()) }
    var hintVisible by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    val hintText = remember(item, hintVocabulary) { studyPracticeHintText(item, hintVocabulary) }
    var attempts by remember(sessionText, itemIndex, item.text) { mutableStateOf(emptyList<String>()) }
    var targetAudioUrl by remember(sessionText, itemIndex, item.text) { mutableStateOf<String?>(null) }
    var error by remember(sessionText, itemIndex, item.text) { mutableStateOf<String?>(null) }
    var starting by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    val streamRef = remember { mutableStateOf<dynamic>(null) }
    val recorderRef = remember { mutableStateOf<dynamic>(null) }
    val disposed = remember { mutableStateOf(false) }
    val ownedRecordingUrls = remember { mutableListOf<String>() }
    val latestPhase = rememberUpdatedState(progress.phase)
    val playingAttemptIndex = studySpeakPlayingAttemptIndex(
        targetAudioUrl = targetAudioUrl,
        recordingUrls = attempts,
        playlistUrls = studyAudioPlaylistUrls,
        playlistIndex = studyAudioPlaylistIndex,
        isPlaying = studyAudioPlaylistActive && studyAudioPlayingUrl != null,
    )

    fun releaseStream() {
        releaseStudySpeakStream(streamRef.value)
        streamRef.value = null
    }

    fun failPractice() {
        val recorder = recorderRef.value
        if (recorder != null) {
            recorder.ondataavailable = null
            recorder.onstop = null
            if (recorder.state == "recording") runCatching { recorder.stop() }
        }
        recorderRef.value = null
        releaseStream()
        stopActiveStudyAudio()
        ownedRecordingUrls.forEach { url -> window.asDynamic().URL.revokeObjectURL(url) }
        ownedRecordingUrls.clear()
        attempts = emptyList()
        progress = StudySpeakProgress()
        starting = false
        error = strings.chatSpeakError
        onActiveChange(false)
    }

    fun beginRecording() {
        if (disposed.value || progress.phase != StudySpeakPhase.LISTENING) return
        val activeStream = streamRef.value ?: run {
            failPractice()
            return
        }
        try {
            val chunks = js("[]")
            val recorder = js("new MediaRecorder(activeStream)")
            recorder.ondataavailable = { event: dynamic ->
                if (event.data != null && event.data.size > 0) chunks.push(event.data)
            }
            recorder.onstop = {
                recorder.ondataavailable = null
                recorder.onstop = null
                if (!disposed.value) {
                    val result = runCatching {
                        val blob = js("new Blob(chunks, { type: recorder.mimeType || 'audio/webm' })")
                        window.asDynamic().URL.createObjectURL(blob) as String
                    }
                    result.onSuccess { recordingUrl ->
                        ownedRecordingUrls += recordingUrl
                        attempts = attempts + recordingUrl
                        recorderRef.value = null
                        val nextProgress = finishStudySpeakRecording(progress)
                        progress = nextProgress
                        if (nextProgress.phase == StudySpeakPhase.REVIEW) {
                            releaseStream()
                            onActiveChange(false)
                            onComplete()
                            targetAudioUrl?.let { targetUrl ->
                                playAudioSequence(studySpeakReviewPlaylist(targetUrl, attempts))
                            }
                        } else {
                            val nextAudioUrl = targetAudioUrl
                            if (nextAudioUrl == null) {
                                failPractice()
                            } else {
                                playAudio(nextAudioUrl, restart = true) { beginRecording() }
                            }
                        }
                    }.onFailure { failPractice() }
                }
            }
            recorderRef.value = recorder
            recorder.start()
            progress = finishStudySpeakListen(progress)
        } catch (_: Throwable) {
            failPractice()
        }
    }

    fun finishRecording() {
        val recorder = recorderRef.value ?: return
        if (progress.phase == StudySpeakPhase.SPEAKING && recorder.state == "recording") {
            runCatching { recorder.stop() }.onFailure { failPractice() }
        }
    }

    fun restartPractice() {
        val recorder = recorderRef.value
        if (recorder != null) {
            recorder.ondataavailable = null
            recorder.onstop = null
            if (recorder.state == "recording") runCatching { recorder.stop() }
        }
        recorderRef.value = null
        releaseStream()
        stopActiveStudyAudio()
        ownedRecordingUrls.forEach { url -> window.asDynamic().URL.revokeObjectURL(url) }
        ownedRecordingUrls.clear()
        attempts = emptyList()
        targetAudioUrl = null
        progress = resetStudySpeak(progress)
        starting = false
        error = null
        onActiveChange(false)
    }

    fun startPractice() {
        if (!canStart || starting || progress.phase != StudySpeakPhase.READY) return
        onActiveChange(true)
        starting = true
        error = null
        val mediaDevices = window.navigator.asDynamic().mediaDevices
        if (mediaDevices == null) {
            failPractice()
            return
        }
        val request = try {
            mediaDevices.getUserMedia(js("({audio:{echoCancellation:true,noiseSuppression:true},video:false})"))
        } catch (_: Throwable) {
            failPractice()
            return
        }
        request.then(
            { stream: dynamic ->
                if (disposed.value) {
                    releaseStudySpeakStream(stream)
                    return@then null
                }
                streamRef.value = stream
                scope.launch {
                    val audioUrl = runCatching {
                        loadStudySpeechDataUrl(SpeechRequest(item.text, textLanguageTag))
                    }.getOrNull()
                    if (disposed.value) return@launch
                    if (audioUrl == null) {
                        failPractice()
                    } else {
                        targetAudioUrl = audioUrl
                        progress = startStudySpeak(progress)
                        starting = false
                        playAudio(audioUrl, restart = true) { beginRecording() }
                    }
                }
                null
            },
            { _: dynamic ->
                if (!disposed.value) failPractice()
                null
            },
        )
    }

    val latestFinishRecording = rememberUpdatedState { finishRecording() }
    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == " " || key == "Spacebar") && latestPhase.value == StudySpeakPhase.SPEAKING) {
                val target = event.target as? HTMLElement
                if (!watchKeyboardTargetIsEditable(target)) {
                    event.preventDefault()
                    latestFinishRecording.value()
                }
            }
        }
        window.addEventListener("keydown", onKey)
        onDispose {
            disposed.value = true
            window.removeEventListener("keydown", onKey)
            val recorder = recorderRef.value
            if (recorder != null) {
                recorder.ondataavailable = null
                recorder.onstop = null
                if (recorder.state == "recording") runCatching { recorder.stop() }
            }
            recorderRef.value = null
            releaseStream()
            ownedRecordingUrls.forEach { url -> window.asDynamic().URL.revokeObjectURL(url) }
            ownedRecordingUrls.clear()
            stopActiveStudyAudio()
        }
    }

    Div(attrs = { classes(Styles.studySpeakBlock) }) {
        P(attrs = { classes(Styles.studySpeakPrompt) }) {
            Text(item.translation.ifBlank { item.text })
        }
        if (progress.phase == StudySpeakPhase.READY) {
            if (hintVisible && hintText.isNotEmpty()) {
                P(attrs = { classes(Styles.studyPracticeHint) }) { Text(hintText) }
            }
            Div(attrs = { classes(Styles.studySpeakActions) }) {
                Button(attrs = {
                    classes(Styles.btnPrimary)
                    attr("type", "button")
                    if (!canStart || starting) attr("disabled", "")
                    onClick { startPractice() }
                }) {
                    MaterialSymbol(if (starting) "progress_activity" else "play_arrow")
                    Text(strings.chatSpeakStart)
                }
                if (hintText.isNotEmpty()) {
                    StudyPracticeHintButton(strings, hintVisible) { hintVisible = !hintVisible }
                }
            }
        } else if (progress.phase == StudySpeakPhase.REVIEW) {
            Div(attrs = { classes(Styles.studySpeakActions) }) {
                Button(attrs = {
                    classes(Styles.btnPrimary)
                    attr("type", "button")
                    onClick { restartPractice() }
                }) {
                    MaterialSymbol("replay")
                    Text(strings.chatSpeakRestart)
                }
            }
            P(attrs = { classes(Styles.studySpeakPrompt) }) { Text(item.text) }
            Div(attrs = { classes(Styles.studySpeakAttempts) }) {
                attempts.forEachIndexed { attemptIndex, recordingUrl ->
                    val isPlaying = playingAttemptIndex == attemptIndex
                    Button(attrs = {
                        classes(Styles.studySpeakAttemptButton)
                        attr("type", "button")
                        attr(
                            "aria-label",
                            if (isPlaying) strings.chatSpeakStopAttempt(attemptIndex + 1)
                            else strings.chatSpeakPlayAttempt(attemptIndex + 1),
                        )
                        onClick {
                            if (isPlaying) {
                                stopActiveStudyAudio()
                            } else {
                                targetAudioUrl?.let { targetUrl ->
                                    playAudioSequence(listOf(targetUrl, recordingUrl))
                                }
                            }
                        }
                    }) {
                        MaterialSymbol(if (isPlaying) "stop_circle" else "play_circle")
                        Text(
                            if (isPlaying) strings.chatSpeakStopAttempt(attemptIndex + 1)
                            else strings.chatSpeakPlayAttempt(attemptIndex + 1),
                        )
                    }
                }
            }
        } else {
            Div(attrs = {
                classes(Styles.studySpeakPhases)
                attr("aria-live", "polite")
            }) {
                val listening = progress.phase == StudySpeakPhase.LISTENING
                val speaking = progress.phase == StudySpeakPhase.SPEAKING
                Span(attrs = {
                    classes(Styles.studySpeakPhase)
                    if (listening) classes(Styles.studySpeakPhaseActive)
                    attr("aria-pressed", listening.toString())
                }) {
                    MaterialSymbol("hearing")
                    Text(strings.chatSpeakListen)
                }
                Span(attrs = {
                    classes(Styles.studySpeakPhase)
                    if (speaking) classes(Styles.studySpeakPhaseActive)
                    attr("aria-pressed", speaking.toString())
                }) {
                    MaterialSymbol("mic")
                    Text(strings.chatSpeakSpeak)
                }
                Span(attrs = { classes(Styles.studySpeakRound) }) {
                    Text(strings.chatSpeakRound(progress.roundIndex + 1, STUDY_SPEAK_ROUNDS))
                }
            }
            if (progress.phase == StudySpeakPhase.SPEAKING) {
                Div(attrs = { classes(Styles.studySpeakActions) }) {
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick { finishRecording() }
                    }) {
                        MaterialSymbol("stop_circle")
                        Text(strings.chatSpeakFinish)
                    }
                }
            }
        }
        if (error != null) {
            P(attrs = {
                classes(Styles.studyWriteFeedback)
                attr("role", "alert")
            }) { Text(error!!) }
        }
    }
}

private fun releaseStudySpeakStream(stream: dynamic) {
    if (stream == null) return
    runCatching {
        stream.getTracks().forEach { track: dynamic -> track.stop() }
    }
}

@Composable
private fun StudyListenPracticeCard(
    item: StudyExampleSentence,
    itemIndex: Int,
    sessionText: String,
    textLanguageTag: String,
    audioUrl: String?,
    audioLoading: Boolean,
    audioFailed: Boolean,
    languageTag: String,
    strings: UiStrings,
    hintVocabulary: List<StudyToken>,
    autoFocus: Boolean,
    onRequestAudio: (onReady: (String) -> Unit) -> Unit,
    onCorrect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var played by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    var hintVisible by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    val hintText = remember(item, hintVocabulary) { studyPracticeHintText(item, hintVocabulary) }
    var draft by remember(sessionText, itemIndex, item.text) { mutableStateOf("") }
    var checking by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    var feedback by remember(sessionText, itemIndex, item.text) { mutableStateOf<String?>(null) }
    var correct by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    var focusNonce by remember(sessionText, itemIndex, item.text) { mutableStateOf(0) }
    var playWhenReady by remember(sessionText, itemIndex, item.text) { mutableStateOf(false) }
    val audioReady = audioUrl != null
    val playing = audioUrl?.let(::isStudyAudioPlaying) == true
    val playLabel = when {
        correct -> item.text
        playing -> strings.chatStudyPauseSentence
        else -> strings.chatStudyPlaySentence
    }

    LaunchedEffect(audioUrl, audioFailed, playWhenReady) {
        if (audioFailed) {
            playWhenReady = false
        } else if (playWhenReady && audioUrl != null) {
            playWhenReady = false
            playAudio(audioUrl, restart = true)
        }
    }

    fun check(value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || !played || checking || correct) return
        draft = candidate
        feedback = null
        checking = true
        scope.launch {
            val result = runCatching {
                ChatApi.studyListenCheck(
                    StudyListenCheckRequest(
                        sourceText = item.text,
                        expectedMeaning = item.translation.ifBlank { item.text },
                        attempt = candidate,
                        textLanguageTag = textLanguageTag,
                        languageTag = languageTag,
                    ),
                )
            }
            result.onSuccess { response ->
                if (draft.trim() == candidate) {
                    if (response.correct) {
                        correct = true
                        feedback = null
                        onCorrect()
                    } else {
                        feedback = response.feedback.trim().takeIf { it.isNotEmpty() }
                            ?: strings.chatExpressFeedbackError
                    }
                }
            }.onFailure {
                if (draft.trim() == candidate) feedback = strings.chatExpressFeedbackError
            }
            checking = false
        }
    }

    Div(attrs = { classes(Styles.studyWriteBlock) }) {
        Button(attrs = {
            classes(Styles.sentencePlay)
            attr("type", "button")
            if (!audioReady && (audioLoading || !audioFailed)) attr("disabled", "")
            attr("aria-label", playLabel)
            onClick {
                if (audioUrl != null) {
                    played = true
                    focusNonce += 1
                    playAudio(audioUrl)
                } else if (!audioLoading) {
                    onRequestAudio { url ->
                        played = true
                        focusNonce += 1
                        playAudio(url)
                    }
                }
            }
        }) {
            MaterialSymbol(
                when {
                    audioLoading -> "progress_activity"
                    playing -> "pause_circle"
                    else -> "play_circle"
                },
            )
            Text(playLabel)
        }
        if (audioFailed) {
            P(attrs = {
                classes(Styles.studyWriteFeedback)
                attr("role", "status")
            }) { Text(strings.chatStudySentenceError) }
        }
        if (correct) {
            Div(attrs = {
                classes(Styles.studyWriteCorrect)
                attr("role", "status")
            }) {
                MaterialSymbol("check_circle")
                Span { Text(strings.chatExpressCorrect) }
            }
        } else {
            if (hintVisible && hintText.isNotEmpty()) {
                P(attrs = { classes(Styles.studyPracticeHint) }) { Text(hintText) }
            }
            Div(attrs = { classes(Styles.studyPracticeInputRow) }) {
                ChatInput(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (feedback != null) feedback = null
                    },
                    onSend = { check(it) },
                    placeholder = strings.chatListenPlaceholder,
                    languageTag = languageTag,
                    multiline = item.translation.length > 48,
                    autoFocus = autoFocus,
                    highlighted = played,
                    focusNonce = focusNonce,
                )
                StudyPracticeRevealButton(strings) {
                    draft = item.translation.ifBlank { item.text }
                    feedback = null
                    played = true
                    focusNonce += 1
                    if (audioUrl != null) {
                        playWhenReady = false
                        playAudio(audioUrl, restart = true)
                    } else if (audioLoading) {
                        playWhenReady = true
                    } else {
                        playWhenReady = false
                        onRequestAudio { url ->
                            playAudio(url, restart = true)
                        }
                    }
                }
                if (hintText.isNotEmpty()) {
                    StudyPracticeHintButton(strings, hintVisible) { hintVisible = !hintVisible }
                }
            }
            when {
                checking -> {
                    Div(attrs = {
                        classes(Styles.studyWriteChecking)
                        attr("role", "status")
                    }) {
                        MaterialSymbol("progress_activity")
                        Span { Text(strings.chatListenChecking) }
                    }
                }
                feedback != null -> {
                    P(attrs = {
                        classes(Styles.studyWriteFeedback)
                        attr("role", "status")
                    }) { Text(feedback!!) }
                }
            }
        }
    }
}

@Composable
private fun StudyPracticeRevealButton(strings: UiStrings, onClick: () -> Unit) {
    Button(attrs = {
        classes(Styles.studyPracticeRevealButton)
        attr("type", "button")
        attr("aria-label", strings.chatPracticeReveal)
        attr("title", strings.chatPracticeReveal)
        onClick { onClick() }
    }) {
        MaterialSymbol("visibility")
    }
}

@Composable
private fun StudyPracticeHintButton(
    strings: UiStrings,
    hintVisible: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes(Styles.studyPracticeRevealButton)
        attr("type", "button")
        attr("aria-label", strings.chatPracticeHint)
        attr("title", strings.chatPracticeHint)
        attr("aria-pressed", hintVisible.toString())
        onClick { onClick() }
    }) {
        MaterialSymbol("lightbulb")
    }
}

@Composable
private fun StudyWriteSentenceCard(
    sentenceIndex: Int,
    sentenceText: String,
    textLanguageTag: String,
    strings: UiStrings,
    autoFocus: Boolean,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    var draft by remember(sentenceText) { mutableStateOf("") }
    var correct by remember(sentenceText) { mutableStateOf(false) }
    var checking by remember(sentenceText) { mutableStateOf(false) }
    var feedback by remember(sentenceText) { mutableStateOf<String?>(null) }

    fun check(value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || checking) return
        if (studyWriteMatches(sentenceText, candidate)) {
            correct = true
            draft = sentenceText
            feedback = null
            checking = false
            return
        }
        correct = false
        draft = candidate
        feedback = null
        checking = true
        scope.launch {
            runCatching {
                ChatApi.studyWriteCheck(
                    StudyWriteCheckRequest(
                        expected = sentenceText,
                        attempt = candidate,
                        textLanguageTag = textLanguageTag,
                        languageTag = languageTag,
                    ),
                )
            }.onSuccess { response ->
                if (response.correct || studyWriteMatches(sentenceText, candidate)) {
                    correct = true
                    draft = sentenceText
                    feedback = null
                } else {
                    feedback = response.feedback.trim().takeIf { it.isNotEmpty() }
                        ?: strings.chatWriteFeedbackError
                }
            }.onFailure {
                feedback = strings.chatWriteFeedbackError
            }
            checking = false
        }
    }

    Div(attrs = { classes(Styles.studyWriteBlock) }) {
        P(attrs = { classes(Styles.studyWritePrompt) }) { Text(sentenceText) }
        if (correct) {
            Div(attrs = {
                classes(Styles.studyWriteCorrect)
                attr("role", "status")
            }) {
                MaterialSymbol("check_circle")
                Span { Text(strings.chatWriteCorrect) }
            }
        } else {
            ChatInput(
                value = draft,
                onValueChange = {
                    draft = it
                    if (feedback != null) feedback = null
                },
                onSend = { check(it) },
                placeholder = strings.chatWritePlaceholder,
                languageTag = textLanguageTag,
                multiline = sentenceText.length > 48,
                autoFocus = sentenceIndex == 0 && autoFocus,
            )
            if (checking) {
                Div(attrs = {
                    classes(Styles.studyWriteChecking)
                    attr("role", "status")
                }) {
                    MaterialSymbol("progress_activity")
                    Span { Text(strings.chatWriteChecking) }
                }
            } else {
                feedback?.let { message ->
                    P(attrs = {
                        classes(Styles.studyWriteFeedback)
                        attr("role", "status")
                    }) { Text(message) }
                }
            }
        }
    }
}

/** Ephemeral Play-tab UI state (drafts/sending); conversation lives on the session. */
private data class StudyPlayLocalState(
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    /** imageId -> failed while waiting for a beat visual */
    val failedImageIds: Set<String> = emptySet(),
)

@Composable
private fun StudyPlayTab(
    session: StudySessionResponse,
    playLoading: Boolean,
    playFailed: Boolean,
    strings: UiStrings,
    onRetryLoad: () -> Unit,
    onScenarioUpdate: (String, (StudyPlayScenario) -> StudyPlayScenario) -> Unit,
    onMessageImage: (scenarioId: String, imageId: String, imageUrl: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    val scenarios = session.playScenarios
    val localState = remember(session.text) { mutableStateMapOf<String, StudyPlayLocalState>() }

    fun local(id: String): StudyPlayLocalState = localState[id] ?: StudyPlayLocalState()

    fun setLocal(id: String, transform: (StudyPlayLocalState) -> StudyPlayLocalState) {
        localState[id] = transform(local(id))
    }

    fun pollImage(imageId: String, onResult: (String?, Boolean) -> Unit) {
        scope.launch {
            var attempts = 0
            var url: String? = null
            var failed = false
            var missingJobStreak = 0
            while (attempts < STUDY_IMAGE_POLL_MAX_ATTEMPTS) {
                val image = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                if (image != null) {
                    missingJobStreak = 0
                    when (image.status) {
                        StudyPlaygroundImageStatus.COMPLETED -> {
                            url = image.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                            failed = url == null
                            break
                        }
                        StudyPlaygroundImageStatus.FAILED -> {
                            failed = true
                            break
                        }
                        StudyPlaygroundImageStatus.PENDING,
                        StudyPlaygroundImageStatus.GENERATING,
                        -> Unit
                    }
                } else {
                    missingJobStreak += 1
                    if (missingJobStreak >= STUDY_IMAGE_MISSING_JOB_LIMIT) {
                        failed = true
                        break
                    }
                }
                attempts += 1
                delay(STUDY_IMAGE_POLL_DELAY_MS)
            }
            if (url == null && !failed) failed = true
            onResult(url, failed)
        }
    }

    // Resume any in-flight beat images when reopening a persisted session.
    LaunchedEffect(scenarios.map { scenario ->
        scenario.id to scenario.messages.map { it.imageId to it.imageUrl }
    }) {
        scenarios.forEach { scenario ->
            scenario.messages.forEach { message ->
                if (message.speaker != StudyPlaySpeaker.OTHER) return@forEach
                if (!message.imageUrl.isNullOrBlank()) return@forEach
                val imageId = message.imageId?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                pollImage(imageId) { url, failed ->
                    if (!url.isNullOrBlank()) {
                        onMessageImage(scenario.id, imageId, url)
                    } else if (failed) {
                        setLocal(scenario.id) { state ->
                            state.copy(failedImageIds = state.failedImageIds + imageId)
                        }
                    }
                }
            }
        }
    }

    Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatPlayHint) }

    when {
        scenarios.isEmpty() && (playLoading || !playFailed) -> {
            Div(attrs = { classes(Styles.studyPlayStatus) }) {
                MaterialSymbol("progress_activity")
                Span { Text(strings.chatPlayLoading) }
            }
        }
        scenarios.isEmpty() && playFailed -> {
            Div(attrs = { classes(Styles.studySessionError) }) {
                MaterialSymbol("error")
                P { Text(strings.chatPlayError) }
                Button(attrs = {
                    classes(Styles.savedItemAction)
                    attr("type", "button")
                    onClick { onRetryLoad() }
                }) {
                    MaterialSymbol("refresh")
                    Text(strings.chatPlayRetry)
                }
            }
        }
        else -> {
            scenarios.forEach { scenario ->
                val ui = local(scenario.id)
                StudyPlayScenarioCard(
                    scenario = scenario,
                    draft = ui.draft,
                    sending = ui.sending,
                    error = ui.error,
                    failedImageIds = ui.failedImageIds,
                    textLanguageTag = session.textLanguageTag,
                    strings = strings,
                    onDraftChange = { value ->
                        setLocal(scenario.id) { it.copy(draft = value, error = null) }
                    },
                    onSend = { raw ->
                        val message = raw?.trim().orEmpty()
                        if (message.isEmpty() || ui.sending) return@StudyPlayScenarioCard
                        val userMessage = StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.USER,
                            text = message,
                        )
                        // Snapshot history before the new user turn for the AI request.
                        val history = scenario.messages.mapNotNull { item ->
                            val text = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                            StudyPlayHistoryMessage(
                                speaker = item.speaker,
                                text = text,
                                description = item.description,
                            )
                        }
                        setLocal(scenario.id) {
                            it.copy(draft = "", sending = true, error = null)
                        }
                        onScenarioUpdate(scenario.id) { current ->
                            current.copy(messages = current.messages + userMessage)
                        }
                        scope.launch {
                            runCatching {
                                ChatApi.studyPlayTurn(
                                    StudyPlayTurnRequest(
                                        text = session.text,
                                        textLanguageTag = session.textLanguageTag,
                                        languageTag = languageTag,
                                        scenarioDescription = scenario.description,
                                        userMessage = message,
                                        history = history,
                                        characters = session.characters,
                                    ),
                                )
                            }.onSuccess { response ->
                                val imageId = response.imageId.trim().takeIf { it.isNotEmpty() }
                                val replyMessage = StudyPlayChatMessage(
                                    speaker = StudyPlaySpeaker.OTHER,
                                    text = response.reply,
                                    description = response.description,
                                    imageId = imageId,
                                    imageUrl = null,
                                )
                                onScenarioUpdate(scenario.id) { current ->
                                    current.copy(messages = current.messages + replyMessage)
                                }
                                setLocal(scenario.id) { it.copy(sending = false) }
                                if (imageId != null) {
                                    pollImage(imageId) { url, failed ->
                                        if (!url.isNullOrBlank()) {
                                            onMessageImage(scenario.id, imageId, url)
                                        } else if (failed) {
                                            setLocal(scenario.id) { state ->
                                                state.copy(failedImageIds = state.failedImageIds + imageId)
                                            }
                                        }
                                    }
                                }
                            }.onFailure {
                                // Roll back the optimistic user message on failure.
                                onScenarioUpdate(scenario.id) { current ->
                                    val messages = current.messages.toMutableList()
                                    val last = messages.lastOrNull()
                                    if (
                                        last != null &&
                                        last.speaker == StudyPlaySpeaker.USER &&
                                        last.text == message
                                    ) {
                                        messages.removeAt(messages.lastIndex)
                                    }
                                    current.copy(messages = messages)
                                }
                                setLocal(scenario.id) {
                                    it.copy(
                                        sending = false,
                                        draft = message,
                                        error = strings.chatPlayError,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StudyPlayScenarioCard(
    scenario: StudyPlayScenario,
    draft: String,
    sending: Boolean,
    error: String?,
    failedImageIds: Set<String>,
    textLanguageTag: String,
    strings: UiStrings,
    onDraftChange: (String) -> Unit,
    onSend: (String?) -> Unit,
) {
    Div(attrs = { classes(Styles.studyPlayBlock) }) {
        scenario.messages.forEach { message ->
            val imageFailed = message.imageId?.let { it in failedImageIds } == true &&
                message.imageUrl.isNullOrBlank()
            val showVisual = message.speaker == StudyPlaySpeaker.OTHER &&
                (
                    message.description.isNotBlank() ||
                        !message.imageUrl.isNullOrBlank() ||
                        imageFailed ||
                        !message.imageId.isNullOrBlank() ||
                        message.text.isNotBlank()
                    )
            if (showVisual) {
                if (message.description.isNotBlank()) {
                    P(attrs = { classes(Styles.studyPlayDescription) }) { Text(message.description) }
                }
                StudySentenceVisual(
                    imageUrl = message.imageUrl,
                    failed = imageFailed,
                    strings = strings,
                )
            }
            if (message.text.isNotBlank()) {
                Div(attrs = {
                    classes(Styles.studyPlayLine)
                    if (message.speaker == StudyPlaySpeaker.USER) {
                        classes(Styles.studyPlayLineYou)
                    } else {
                        classes(Styles.studyPlayLineOther)
                    }
                }) {
                    Span(attrs = { classes(Styles.studyPlaySpeaker) }) {
                        Text(
                            if (message.speaker == StudyPlaySpeaker.USER) {
                                strings.chatPlayYouLabel
                            } else {
                                strings.chatPlayOtherLabel
                            },
                        )
                    }
                    P { Text(message.text) }
                }
            }
        }
        if (sending) {
            Div(attrs = { classes(Styles.studyPlayStatus) }) {
                MaterialSymbol("progress_activity")
                Span { Text(strings.chatPlaySending) }
            }
        } else {
            ChatInput(
                value = draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                placeholder = strings.chatPlayPlaceholder,
                languageTag = textLanguageTag,
                multiline = true,
            )
        }
        error?.let { message ->
            Div(attrs = { classes(Styles.studyPlaygroundError) }) {
                MaterialSymbol("error")
                Span { Text(message) }
            }
        }
    }
}

@Composable
private fun StudyExamResult(
    grade: StudyExamGrade,
    beatNotes: List<StudyExamBeatScore>,
    strings: UiStrings,
    onRetake: () -> Unit,
) {
    val levelLabel = studyExamLevelLabel(grade.overall, strings)
    Div(attrs = {
        classes(Styles.studyExamResult)
        attr("data-level", grade.overall.toString())
    }) {
        Div(attrs = { classes(Styles.studyExamHero) }) {
            Div(attrs = { classes(Styles.studyExamGradeMark) }) {
                Span(attrs = { classes(Styles.studyExamGradeValue) }) {
                    Span(attrs = { classes(Styles.studyExamGradeNumber) }) { Text(grade.overall.toString()) }
                    Span(attrs = { classes(Styles.studyExamGradeOutOf) }) { Text("/5") }
                }
            }
            Div(attrs = { classes(Styles.studyExamHeroCopy) }) {
                P(attrs = { classes(Styles.studyExamLevel) }) { Text(levelLabel) }
                P(attrs = { classes(Styles.studyExamCheer) }) { Text(strings.chatTestResultCheer(grade.overall)) }
            }
        }
        Div(attrs = { classes(Styles.studyExamSkills) }) {
            StudyExamSkillMeter(strings.chatTestListeningLabel, grade.listening)
            StudyExamSkillMeter(strings.chatTestSpeakingLabel, grade.speaking)
        }
        val notes = beatNotes.filter { it.feedback.isNotBlank() }
        if (notes.isNotEmpty()) {
            Div(attrs = { classes(Styles.studyExamMoments) }) {
                notes.forEach { row ->
                    Div(attrs = { classes(Styles.studyExamMoment) }) {
                        Span(attrs = { classes(Styles.studyExamMomentPoints) }) { Text("${row.points}/2") }
                        Span { Text(row.feedback) }
                    }
                }
            }
        }
        Button(attrs = {
            classes(Styles.btnPrimary)
            attr("type", "button")
            onClick {
                primeStudyAudioPlayback()
                onRetake()
            }
        }) { Text(strings.chatTestRetake) }
    }
}

@Composable
private fun StudyExamSkillMeter(label: String, level: Int) {
    val score = level.coerceIn(0, 5)
    Div(attrs = { classes(Styles.studyExamSkill) }) {
        Span { Text(label) }
        Span(attrs = { classes(Styles.studyExamSkillScore) }) { Text("$score") }
        Div(attrs = { classes(Styles.studyExamSkillPips) }) {
            (1..5).forEach { pip ->
                Span(attrs = {
                    classes(Styles.studyExamSkillPip)
                    if (pip <= score) classes(Styles.studyExamSkillPipOn)
                })
            }
        }
    }
}

@Composable
private fun StudyTestTab(
    session: StudySessionResponse,
    strings: UiStrings,
    learnerLanguageTag: String,
    sceneLoading: Boolean,
    sceneError: Boolean,
    savedGrade: StudyExamGrade?,
    attempt: StudyExamAttemptState,
    autoPlayListen: Boolean,
    autoFocus: Boolean,
    onRetryScene: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitAnswer: (String?) -> Unit,
    onStart: () -> Unit,
    onRetake: () -> Unit,
) {
    val audioScope = rememberCoroutineScope()
    val audioCache = remember { mutableStateMapOf<String, String>() }
    val audioRequests = remember { mutableStateMapOf<String, Deferred<String>>() }

    suspend fun listenAudioUrl(key: String, line: String, languageTag: String): String {
        audioCache[key]?.let { return it }
        val request = audioRequests[key] ?: audioScope.async {
            loadStudySpeechDataUrl(SpeechRequest(line, languageTag)).also { audioCache[key] = it }
        }.also { created ->
            audioRequests[key] = created
            created.invokeOnCompletion {
                if (audioRequests[key] === created) audioRequests.remove(key)?.cancel()
            }
        }
        return request.await()
    }

    val scene = session.examScene?.takeIf { it.isTenRoundExam() }
    when {
        scene == null && (sceneLoading || !sceneError) -> {
            Div(attrs = { classes(Styles.studyPlayStatus) }) {
                MaterialSymbol("progress_activity")
                Span { Text(strings.chatTestSceneLoading) }
            }
        }
        scene == null -> {
            Div(attrs = { classes(Styles.studySessionError) }) {
                MaterialSymbol("error")
                P { Text(strings.chatTestSceneError) }
                Button(attrs = {
                    classes(Styles.chip)
                    attr("type", "button")
                    onClick { onRetryScene() }
                }) { Text(strings.chatTestRetry) }
            }
        }
        else -> {
            val beat = scene.beats.getOrNull(attempt.beatIndex)
            val resultGrade = attempt.result?.grade ?: savedGrade.takeUnless { attempt.isRetaking }
            if (resultGrade != null) {
                StudyExamResult(
                    grade = resultGrade,
                    beatNotes = attempt.result?.beats.orEmpty(),
                    strings = strings,
                    onRetake = onRetake,
                )
            } else if (!attempt.started) {
                Div(attrs = { classes(Styles.studyExamBriefing) }) {
                    P(attrs = { classes(Styles.studyPlayDescription) }) { Text(scene.setting) }
                    P(attrs = { classes(Styles.studyExamOutlineTitle) }) { Text(strings.chatTestOutlineTitle) }
                    Ol(attrs = { classes(Styles.studyExamOutline) }) {
                        scene.outline.forEach { item ->
                            Li(attrs = { classes(Styles.studyExamOutlineItem) }) {
                                Text(item)
                            }
                        }
                    }
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick {
                            primeStudyAudioPlayback()
                            onStart()
                        }
                    }) { Text(strings.chatTestStart) }
                }
            } else if (beat == null) {
                Div(attrs = { classes(Styles.studySessionError) }) {
                    MaterialSymbol("error")
                    P { Text(strings.chatTestSceneError) }
                    Button(attrs = {
                        classes(Styles.chip)
                        attr("type", "button")
                        onClick { onRetryScene() }
                    }) { Text(strings.chatTestRetry) }
                }
            } else {
                val audioKey = "${scene.id}|${beat.id}|${session.textLanguageTag}"
                val currentAudioKey = rememberUpdatedState(audioKey)
                val currentScoring = rememberUpdatedState(attempt.isScoring)
                var audioUrl by remember(audioKey) { mutableStateOf(audioCache[audioKey]) }
                var audioLoading by remember(audioKey) { mutableStateOf(false) }
                var audioError by remember(audioKey) { mutableStateOf(false) }
                val character = session.characters.firstOrNull { it.id == beat.speakerId }
                val isSpeakBeat = beat.kind == StudyExamBeatKind.SPEAK
                val speakerLabel = if (isSpeakBeat) strings.chatPlayYouLabel else beat.speakerName
                val portraitUrl = if (isSpeakBeat) null else character?.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

                fun playListenLine() {
                    if (beat.kind != StudyExamBeatKind.LISTEN || audioLoading) return
                    (audioUrl ?: audioCache[audioKey])?.let { url ->
                        audioUrl = url
                        playAudio(url, restart = true)
                        return
                    }
                    val line = beat.line?.trim()?.takeIf { it.isNotEmpty() } ?: return
                    audioLoading = true
                    audioError = false
                    audioScope.launch {
                        runCatching {
                            listenAudioUrl(audioKey, line, session.textLanguageTag)
                        }.onSuccess { url ->
                            if (currentAudioKey.value != audioKey || currentScoring.value) return@onSuccess
                            audioUrl = url
                            playAudio(url, restart = true)
                        }.onFailure {
                            if (currentAudioKey.value == audioKey) audioError = true
                        }
                        if (currentAudioKey.value == audioKey) audioLoading = false
                    }
                }

                LaunchedEffect(scene.id, attempt.beatIndex, session.textLanguageTag) {
                    val upcomingListenBeat = scene.beats.drop(attempt.beatIndex + 1)
                        .firstOrNull { it.kind == StudyExamBeatKind.LISTEN }
                    val upcomingLine = upcomingListenBeat?.line?.trim()?.takeIf { it.isNotEmpty() }
                    if (upcomingListenBeat != null && upcomingLine != null) {
                        val upcomingKey = "${scene.id}|${upcomingListenBeat.id}|${session.textLanguageTag}"
                        runCatching { listenAudioUrl(upcomingKey, upcomingLine, session.textLanguageTag) }
                    }
                }

                LaunchedEffect(scene.id, beat.id, session.textLanguageTag, attempt.isScoring) {
                    if (autoPlayListen && !attempt.isScoring && beat.kind == StudyExamBeatKind.LISTEN) playListenLine()
                }

                Div(attrs = { classes(Styles.studyPlayBlock) }) {
                    P(attrs = { classes(Styles.studyPlayDescription) }) { Text(scene.setting) }
                    P(attrs = { classes(Styles.studySessionHint) }) {
                        Text(strings.chatTestBeatProgress(attempt.beatIndex / 2 + 1, scene.outline.size))
                    }
                    Div(attrs = {
                        classes(Styles.studyPlayLine)
                        classes(if (isSpeakBeat) Styles.studyPlayLineYou else Styles.studyPlayLineOther)
                    }) {
                        if (portraitUrl != null) {
                            Img(src = portraitUrl, alt = speakerLabel, attrs = {
                                classes(Styles.studyStoryImage)
                                style {
                                    property("width", "64px")
                                    property("height", "64px")
                                    property("border-radius", "50%")
                                    property("object-fit", "cover")
                                }
                            })
                        } else {
                            Div(attrs = {
                                classes(Styles.studySentenceImagePlaceholder)
                                style {
                                    property("width", "64px")
                                    property("height", "64px")
                                    property("min-height", "64px")
                                    property("border-radius", "50%")
                                    property("margin", "0 auto")
                                    property("flex", "none")
                                }
                            }) { MaterialSymbol("person") }
                        }
                        Span(attrs = { classes(Styles.studyPlaySpeaker) }) { Text(speakerLabel) }
                        P { Text(beat.prompt) }
                        if (beat.kind == StudyExamBeatKind.LISTEN) {
                            Button(attrs = {
                                classes(Styles.sentencePlay)
                                attr("type", "button")
                                if (audioLoading) attr("disabled", "")
                                attr("aria-label", strings.chatTestPlayLine)
                                onClick {
                                    primeStudyAudioPlayback()
                                    playListenLine()
                                }
                            }) {
                                MaterialSymbol(if (audioLoading) "progress_activity" else "replay")
                                Text(strings.chatTestPlayLine)
                            }
                            if (audioError) {
                                P(attrs = { classes(Styles.studyPlaygroundError) }) {
                                    Text(strings.chatTestAudioError)
                                }
                            }
                        }
                    }
                    if (attempt.isScoring) {
                        Div(attrs = { classes(Styles.studyPlayStatus) }) {
                            MaterialSymbol("progress_activity")
                            Span { Text(strings.chatTestScoring) }
                        }
                    } else {
                        P(attrs = { classes(Styles.studySessionHint) }) {
                            Text(
                                if (beat.kind == StudyExamBeatKind.LISTEN) {
                                    strings.chatTestListenHint(beat.speakerName)
                                } else {
                                    strings.chatTestSpeakHint
                                },
                            )
                        }
                        ChatInput(
                            value = attempt.draft,
                            onValueChange = onDraftChange,
                            onSend = { answer ->
                                primeStudyAudioPlayback()
                                onSubmitAnswer(answer)
                            },
                            placeholder = strings.chatTestInputPlaceholder,
                            languageTag = if (beat.kind == StudyExamBeatKind.LISTEN) {
                                learnerLanguageTag
                            } else {
                                session.textLanguageTag
                            },
                            multiline = true,
                            autoFocus = autoFocus,
                            focusNonce = if (autoFocus) attempt.beatIndex + 1 else 0,
                        )
                        Button(attrs = {
                            classes(Styles.chip)
                            attr("type", "button")
                            if (attempt.draft.trim().isEmpty()) attr("disabled", "")
                            onClick {
                                primeStudyAudioPlayback()
                                onSubmitAnswer(attempt.draft)
                            }
                        }) {
                            Text(if (attempt.beatIndex == scene.beats.lastIndex) strings.chatTestFinish else strings.chatTestNext)
                        }
                    }
                    if (attempt.error != null) {
                        Div(attrs = { classes(Styles.studyPlaygroundError) }) {
                            MaterialSymbol("error")
                            Span { Text(attempt.error) }
                        }
                    }
                }
            }
        }
    }
}

private const val SPEECH_BATCH_SIZE = 3
private const val SPEECH_RETRY_MAX_ATTEMPTS = 4
private const val SPEECH_RETRY_INITIAL_DELAY_MS = 2_500L
/** ~6 minutes: reference-to-image + optional text-only fallback can exceed 2 minutes. */
private const val STUDY_IMAGE_POLL_MAX_ATTEMPTS = 180
private const val STUDY_IMAGE_POLL_DELAY_MS = 1_500L
/** Consecutive missing job polls before treating the imageId as lost. */
private const val STUDY_IMAGE_MISSING_JOB_LIMIT = 5

/**
 * Retries TTS calls on rate-limit / transient errors with short exponential backoff.
 */
private suspend fun <T> withSpeechRetry(
    maxAttempts: Int = SPEECH_RETRY_MAX_ATTEMPTS,
    initialDelayMs: Long = SPEECH_RETRY_INITIAL_DELAY_MS,
    block: suspend () -> T,
): T {
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        try {
            return block()
        } catch (error: ChatApiException) {
            attempt += 1
            val message = error.message.orEmpty()
            val retryable = error.statusCode == 429 ||
                error.statusCode == 503 ||
                error.statusCode == 502 ||
                message.contains("rate", ignoreCase = true) ||
                message.contains("Throttling", ignoreCase = true) ||
                message.contains("try again", ignoreCase = true)
            if (!retryable || attempt >= maxAttempts) throw error
            delay(delayMs)
            delayMs = (delayMs * 3 / 2).coerceAtMost(12_000L)
        }
    }
}

private suspend fun loadStudySpeech(request: SpeechRequest): SpeechResponse =
    StudyAudioCache.load(request) { speechRequest ->
        withSpeechRetry { ChatApi.speech(speechRequest) }
    }

internal suspend fun loadStudySpeechDataUrl(request: SpeechRequest): String =
    StudyAudioCache.dataUrl(loadStudySpeech(request))

@Composable
internal fun StudyTokenDetail(
    token: StudyToken,
    textLanguageTag: String,
    strings: UiStrings,
    imageFailed: Boolean = false,
    onRetryImage: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var expandedExampleKey by remember(token.text, textLanguageTag) { mutableStateOf<String?>(null) }
    val exampleAudio = remember(token.text, textLanguageTag) { mutableStateMapOf<String, String>() }
    val pendingExampleAudio = remember(token.text, textLanguageTag) { mutableStateMapOf<String, Boolean>() }

    fun speakExampleToken(text: String) {
        val spokenText = text.trim()
        if (spokenText.isEmpty()) return
        val cacheKey = "$textLanguageTag|$spokenText"
        scope.launch {
            val url = exampleAudio[cacheKey] ?: runCatching {
                loadStudySpeechDataUrl(SpeechRequest(spokenText, textLanguageTag))
            }.getOrNull()?.also { exampleAudio[cacheKey] = it } ?: return@launch
            playAudio(url)
        }
    }

    fun clearExampleLoading(sentence: String) {
        pendingExampleAudio.remove(sentence)
    }

    fun clearExampleLoadingWhenPlaying(sentence: String) {
        val element = activeStudyAudio
        if (element == null || (!element.paused && !element.ended)) {
            clearExampleLoading(sentence)
            return
        }
        val finish: (Event) -> Unit = { clearExampleLoading(sentence) }
        element.addEventListener("playing", finish)
        element.addEventListener("error", finish)
        element.addEventListener("abort", finish)
    }

    fun playExampleSentence(rawText: String) {
        val sentence = rawText.trim()
        if (sentence.isEmpty()) return
        exampleAudio[sentence]?.let { url ->
            val wasPlaying = isStudyAudioPlaying(url)
            playAudio(url)
            if (wasPlaying) {
                clearExampleLoading(sentence)
            } else {
                pendingExampleAudio[sentence] = true
                clearExampleLoadingWhenPlaying(sentence)
            }
            return
        }
        if (pendingExampleAudio[sentence] == true) return
        pendingExampleAudio[sentence] = true
        scope.launch {
            val url = runCatching {
                loadStudySpeechDataUrl(SpeechRequest(sentence, textLanguageTag))
            }.getOrNull()
            if (url == null) {
                clearExampleLoading(sentence)
                return@launch
            }
            exampleAudio[sentence] = url
            playAudio(url)
            clearExampleLoadingWhenPlaying(sentence)
        }
    }

    Div(attrs = { classes(Styles.wordDefinition, Styles.studyTokenDetail) }) {
        Span(attrs = { classes(Styles.wordDefinitionTitle) }) { Text(token.text) }
        when {
            token.status == StudyTokenStatus.PENDING || !token.isDissectable -> {
                P { Text(strings.chatStudyMeaningLoading) }
            }
            else -> {
                if (token.literalMeaning.isNotBlank()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyLiteral) }
                    P { Text(token.literalMeaning) }
                }
                if (token.register.isNotBlank()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyRegister) }
                    P { Text(token.register) }
                }
                if (token.contextualMeaning.isNotBlank()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyContext) }
                    P { Text(token.contextualMeaning) }
                }
                if (token.nearSynonyms.isNotEmpty()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudySynonyms) }
                    P { Text(token.nearSynonyms.joinToString(separator = " · ")) }
                }
                Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyExamples) }
                when {
                    !token.imageUrl.isNullOrBlank() -> {
                        Img(src = token.imageUrl!!, alt = strings.chatStudyWordImageAlt, attrs = {
                            classes(Styles.studyWordImage)
                        })
                    }
                    token.isDissectable && !imageFailed -> {
                        Div(attrs = { classes(Styles.studyWordImagePlaceholder) }) {
                            MaterialSymbol("progress_activity")
                            Span { Text(strings.chatStudyWordImageLoading) }
                        }
                    }
                    token.isDissectable && imageFailed -> {
                        Div(attrs = { classes(Styles.studyWordImagePlaceholder) }) {
                            MaterialSymbol("broken_image")
                            Span { Text(strings.chatStudyWordImageError) }
                            Button(attrs = {
                                classes(Styles.savedItemAction)
                                attr("type", "button")
                                attr("aria-label", strings.chatPlayRetry)
                                onClick { onRetryImage() }
                            }) {
                                MaterialSymbol("refresh")
                                Text(strings.chatPlayRetry)
                            }
                        }
                    }
                }
                if (token.exampleSentences.isNotEmpty()) {
                    token.exampleSentences.forEachIndexed { exampleIndex, example ->
                        val exampleKey = "${token.text}-$exampleIndex"
                        val isExampleBreakdownVisible = expandedExampleKey == exampleKey
                        Div(attrs = {
                            classes(Styles.studyExampleSentence)
                            val sentence = example.text.trim()
                            if (pendingExampleAudio[sentence] == true) {
                                classes(Styles.studyExampleSentenceLoading)
                                attr("aria-busy", "true")
                            }
                            onClick {
                                playExampleSentence(example.text)
                                expandedExampleKey = toggleStudyExampleBreakdown(expandedExampleKey, exampleKey)
                            }
                        }) {
                            StudyExampleSentenceText(
                                sentence = example.text,
                                word = token.text,
                                expanded = isExampleBreakdownVisible,
                            )
                            if (example.translation.isNotBlank()) {
                                P(attrs = { classes(Styles.studyExampleTranslation) }) {
                                    Text(example.translation)
                                }
                            }
                        }
                        if (isExampleBreakdownVisible && example.tokens.isNotEmpty()) {
                            StudyExampleBreakdown(example, strings, ::speakExampleToken)
                        }
                    }
                }
                StudyWordPlayground(
                    token = token,
                    textLanguageTag = textLanguageTag,
                    strings = strings,
                )
            }
        }
    }
}

@Composable
private fun StudyWordPlayground(
    token: StudyToken,
    textLanguageTag: String,
    strings: UiStrings,
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    var draft by remember(token.text, textLanguageTag) { mutableStateOf("") }
    var submitting by remember(token.text, textLanguageTag) { mutableStateOf(false) }
    var error by remember(token.text, textLanguageTag) { mutableStateOf<String?>(null) }
    var result by remember(token.text, textLanguageTag) { mutableStateOf<StudyWordPlaygroundResponse?>(null) }
    var imageStatus by remember(token.text, textLanguageTag) {
        mutableStateOf<StudyPlaygroundImageStatus?>(null)
    }
    var imageUrl by remember(token.text, textLanguageTag) { mutableStateOf<String?>(null) }

    fun submitPlayground() {
        val sentence = draft.trim()
        if (sentence.isEmpty() || submitting) return
        submitting = true
        error = null
        result = null
        imageStatus = StudyPlaygroundImageStatus.PENDING
        imageUrl = null
        scope.launch {
            runCatching {
                ChatApi.studyPlayground(
                    StudyWordPlaygroundRequest(
                        word = token.text,
                        sentence = sentence,
                        textLanguageTag = textLanguageTag,
                        languageTag = languageTag,
                        literalMeaning = token.literalMeaning,
                        contextualMeaning = token.contextualMeaning,
                    ),
                )
            }.onSuccess { response ->
                result = response
                imageStatus = StudyPlaygroundImageStatus.GENERATING
                submitting = false
                // Poll comic generation until it settles; keep judgment visible immediately.
                var attempts = 0
                while (attempts < 60) {
                    val image = runCatching { ChatApi.studyPlaygroundImage(response.imageId) }.getOrNull()
                    if (image != null) {
                        imageStatus = image.status
                        imageUrl = image.imageUrl
                        if (
                            image.status == StudyPlaygroundImageStatus.COMPLETED ||
                            image.status == StudyPlaygroundImageStatus.FAILED
                        ) {
                            break
                        }
                    }
                    attempts += 1
                    delay(1_500)
                }
                if (
                    imageStatus != StudyPlaygroundImageStatus.COMPLETED &&
                    imageStatus != StudyPlaygroundImageStatus.FAILED
                ) {
                    imageStatus = StudyPlaygroundImageStatus.FAILED
                }
            }.onFailure {
                error = strings.chatStudyPlaygroundError
                imageStatus = null
                submitting = false
            }
        }
    }

    Div(attrs = { classes(Styles.studyPlayground) }) {
        Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyPlayground) }
        P(attrs = { classes(Styles.studyPlaygroundHint) }) { Text(strings.chatStudyPlaygroundHint) }
        Div(attrs = { classes(Styles.studyPlaygroundRow) }) {
            Input(type = InputType.Text) {
                classes(Styles.studyPlaygroundInput)
                value(draft)
                attr("placeholder", strings.chatStudyPlaygroundPlaceholder)
                attr("aria-label", strings.chatStudyPlayground)
                if (submitting) attr("disabled", "")
                onInput { event -> draft = event.value }
                onKeyDown { event ->
                    if (event.key == "Enter") {
                        event.preventDefault()
                        submitPlayground()
                    }
                }
            }
            Button(attrs = {
                classes(Styles.studyPlaygroundSend)
                if (submitting) classes(Styles.studyPlaygroundSendBusy)
                attr("type", "button")
                attr("aria-label", strings.chatStudyPlaygroundSubmit)
                if (submitting || draft.trim().isEmpty()) attr("disabled", "")
                onClick { submitPlayground() }
            }) {
                MaterialSymbol(if (submitting) "progress_activity" else "send")
            }
        }
        error?.let {
            Div(attrs = { classes(Styles.studyPlaygroundError) }) {
                MaterialSymbol("error")
                Span { Text(it) }
            }
        }
        result?.let { judgment ->
            Div(attrs = { classes(Styles.studyPlaygroundResult) }) {
                Div(attrs = {
                    classes(Styles.studyPlaygroundBadge)
                    if (judgment.natural) {
                        classes(Styles.studyPlaygroundBadgeNatural)
                    } else {
                        classes(Styles.studyPlaygroundBadgeUnnatural)
                    }
                }) {
                    MaterialSymbol(if (judgment.natural) "check_circle" else "cancel")
                    Text(
                        if (judgment.natural) {
                            strings.chatStudyPlaygroundNatural
                        } else {
                            strings.chatStudyPlaygroundUnnatural
                        },
                    )
                }
                P(attrs = { classes(Styles.studyPlaygroundSummary) }) { Text(judgment.summary) }
                when (imageStatus) {
                    StudyPlaygroundImageStatus.COMPLETED -> {
                        val url = imageUrl
                        if (!url.isNullOrBlank()) {
                            Img(src = url, alt = strings.chatStudyPlaygroundImageAlt, attrs = {
                                classes(Styles.studyPlaygroundImage)
                            })
                        }
                    }
                    StudyPlaygroundImageStatus.FAILED -> {
                        Div(attrs = { classes(Styles.studyPlaygroundImageStatus) }) {
                            MaterialSymbol("image_not_supported")
                            Span { Text(strings.chatStudyPlaygroundImageError) }
                        }
                    }
                    StudyPlaygroundImageStatus.PENDING,
                    StudyPlaygroundImageStatus.GENERATING,
                    null,
                    -> {
                        Div(attrs = {
                            classes(Styles.studyPlaygroundImageStatus, Styles.studyPlaygroundImageStatusBusy)
                        }) {
                            MaterialSymbol("progress_activity")
                            Span { Text(strings.chatStudyPlaygroundImageLoading) }
                        }
                    }
                }
            }
        }
    }
}

internal enum class StudySentenceLoadState {
    LOADING,
    READY,
    ERROR,
}

private data class StudyTokenRef(
    val sentenceIndex: Int,
    val tokenIndex: Int,
)

private data class StudySentenceAudio(
    val url: String,
    val words: List<SpeechWordTiming>,
)

/** Small pad so clipped words are not cut off mid-phoneme. */
private const val WORD_AUDIO_END_PAD_MS = 40

private fun sentenceNeedsDissect(sentence: StudySentence): Boolean =
    sentence.tokens.any { it.status == StudyTokenStatus.PENDING }

private var sharedStudyAudio: HTMLAudioElement? = null
private var activeStudyAudio: HTMLAudioElement? = null
private var activeStudyAudioUrl: String? = null
private var primedStudyAudio: HTMLAudioElement? = null

private const val SILENT_STUDY_AUDIO_DATA_URL =
    "data:audio/wav;base64,UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQIAAAAAAA=="

/** URL of the clip currently playing; drives play/pause button state in Compose. */
private var studyAudioPlayingUrl by mutableStateOf<String?>(null)

/** True while a multi-clip playlist (full Read-tab text) is active. */
private var studyAudioPlaylistActive by mutableStateOf(false)
private var studyAudioPlaylistUrls: List<String> = emptyList()
private var studyAudioPlaylistIndex: Int = 0

internal fun isStudyAudioPlaying(url: String?): Boolean =
    url != null && studyAudioPlayingUrl == url

private fun isStudyAudioPlaylistPlaying(): Boolean =
    studyAudioPlaylistActive && studyAudioPlayingUrl != null

private fun clearStudyAudioPlaylist() {
    studyAudioPlaylistActive = false
    studyAudioPlaylistUrls = emptyList()
    studyAudioPlaylistIndex = 0
}

private fun clearStudyAudioHandlers(element: HTMLAudioElement) {
    element.asDynamic().ontimeupdate = null
    element.asDynamic().onloadedmetadata = null
    element.asDynamic().onratechange = null
    element.asDynamic().oncanplay = null
    element.asDynamic().onended = null
    element.asDynamic().onerror = null
    element.asDynamic().onpause = null
    element.asDynamic().onplay = null
}

private var activeStudyAudioRate = 1.0

private fun applyActiveStudyAudioRate(element: HTMLAudioElement) {
    element.asDynamic().preservesPitch = true
    if (element.asDynamic().defaultPlaybackRate != activeStudyAudioRate) {
        element.asDynamic().defaultPlaybackRate = activeStudyAudioRate
    }
    if (element.playbackRate != activeStudyAudioRate) {
        element.playbackRate = activeStudyAudioRate
    }
}

private fun releaseStudyAudioElement(element: HTMLAudioElement) {
    clearStudyAudioHandlers(element)
    activeStudyAudioRate = 1.0
    runCatching { element.pause() }
    element.asDynamic().defaultPlaybackRate = 1.0
    element.playbackRate = 1.0
    runCatching {
        element.removeAttribute("src")
        element.load()
    }
}

private fun sharedStudyAudioElement(): HTMLAudioElement =
    sharedStudyAudio ?: (document.createElement("audio") as HTMLAudioElement).also {
        sharedStudyAudio = it
    }

private fun tryPlayStudyAudio(element: HTMLAudioElement) {
    applyActiveStudyAudioRate(element)
    val playback = element.asDynamic().play() ?: return
    playback.then { applyActiveStudyAudioRate(element) }
    playback.catch { }
}

internal fun primeStudyAudioPlayback() {
    if (primedStudyAudio != null) return
    val active = activeStudyAudio
    if (active != null && !active.paused && !active.ended) return
    stopActiveStudyAudio()
    val element = sharedStudyAudioElement()
    releaseStudyAudioElement(element)
    primedStudyAudio = element
    element.src = SILENT_STUDY_AUDIO_DATA_URL
    element.asDynamic().play()?.catch {
        if (primedStudyAudio === element) {
            releaseStudyAudioElement(element)
            primedStudyAudio = null
        }
    }
}

private fun startStudyAudioPlayback(
    element: HTMLAudioElement,
    url: String,
    onDurationMs: ((Int) -> Unit)? = null,
) {
    var durationNotified = false
    fun notifyDuration() {
        if (durationNotified || onDurationMs == null) return
        val duration = element.duration
        if (!duration.isFinite() || duration <= 0) return
        durationNotified = true
        onDurationMs((duration * 1000.0).toInt().coerceAtLeast(1))
    }
    element.asDynamic().oncanplay = {
        notifyDuration()
        tryPlayStudyAudio(element)
    }
    element.asDynamic().onloadedmetadata = {
        applyActiveStudyAudioRate(element)
        notifyDuration()
    }
    element.src = url
    applyActiveStudyAudioRate(element)
    notifyDuration()
    if (element.readyState >= 2) tryPlayStudyAudio(element)
}

internal fun stopActiveStudyAudio(clearPlaylist: Boolean = true) {
    activeStudyAudio?.let(::releaseStudyAudioElement)
    activeStudyAudio = null
    activeStudyAudioUrl = null
    studyAudioPlayingUrl = null
    if (clearPlaylist) {
        clearStudyAudioPlaylist()
    }
}

private fun bindStudyAudioPlaybackState(
    element: HTMLAudioElement,
    url: String,
    onStarted: (() -> Unit)? = null,
    onEnded: (() -> Unit)? = null,
) {
    element.asDynamic().onplay = {
        applyActiveStudyAudioRate(element)
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = url
            onStarted?.invoke()
        }
    }
    element.asDynamic().onpause = {
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = null
        }
    }
    element.asDynamic().onended = {
        element.playbackRate = 1.0
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = null
            if (onEnded != null) {
                onEnded()
            }
        }
    }
    element.asDynamic().onerror = {
        element.playbackRate = 1.0
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = null
            if (onEnded != null) {
                onEnded()
            }
        }
    }
}

private fun watchSeekMsFromClientX(
    clientX: Double,
    element: HTMLElement,
    totalMs: Int,
): Int {
    if (totalMs <= 0) return 0
    val rect = element.getBoundingClientRect()
    val width = rect.width
    if (width <= 0.0) return 0
    val fraction = ((clientX - rect.left) / width).coerceIn(0.0, 1.0)
    return (fraction * totalMs).toInt()
}

private fun loadWatchAudioAt(
    url: String,
    startSec: Double,
    play: Boolean,
    onTime: (currentSec: Double, durationSec: Double) -> Unit,
    onEnded: () -> Unit,
) {
    val existing = activeStudyAudio
    val element = if (existing != null && activeStudyAudioUrl == url) {
        existing
    } else {
        stopActiveStudyAudio()
        val created = sharedStudyAudioElement()
        primedStudyAudio = null
        releaseStudyAudioElement(created)
        activeStudyAudio = created
        activeStudyAudioUrl = url
        created.src = url
        created
    }
    clearStudyAudioHandlers(element)
    if (play) {
        studyAudioPlayingUrl = url
    }
    bindStudyAudioPlaybackState(element, url, onEnded = onEnded)
    element.asDynamic().ontimeupdate = {
        if (activeStudyAudio === element) {
            val duration = element.duration
            val dur = if (duration.isFinite() && duration > 0) duration else 0.0
            onTime(element.currentTime, dur)
        }
    }
    fun applyStartAndMaybePlay() {
        val duration = element.duration
        val safeStart = if (duration.isFinite() && duration > 0) {
            startSec.coerceIn(0.0, duration)
        } else {
            startSec.coerceAtLeast(0.0)
        }
        element.currentTime = safeStart
        if (duration.isFinite() && duration > 0) {
            onTime(safeStart, duration)
        }
        if (play) {
            element.play()
        } else {
            element.pause()
            studyAudioPlayingUrl = null
        }
    }
    element.asDynamic().onloadedmetadata = {
        applyStartAndMaybePlay()
    }
    if (element.readyState >= 1) {
        applyStartAndMaybePlay()
    }
}

internal fun playAudio(
    url: String,
    restart: Boolean = false,
    playbackRate: Double = 1.0,
    onEnded: (() -> Unit)? = null,
    onDurationMs: ((Int) -> Unit)? = null,
    onStarted: (() -> Unit)? = null,
) {
    val current = activeStudyAudio
    // Toggle: tapping the same sentence again pauses instead of restarting.
    if (
        !restart &&
        current != null &&
        activeStudyAudioUrl == url &&
        !studyAudioPlaylistActive &&
        !current.paused &&
        !current.ended
    ) {
        current.pause()
        return
    }
    stopActiveStudyAudio()
    val element = sharedStudyAudioElement()
    primedStudyAudio = null
    releaseStudyAudioElement(element)
    activeStudyAudioRate = playbackRate
    activeStudyAudio = element
    activeStudyAudioUrl = url
    applyActiveStudyAudioRate(element)
    studyAudioPlayingUrl = url
    bindStudyAudioPlaybackState(element, url, onStarted = onStarted, onEnded = onEnded)
    startStudyAudioPlayback(element, url, onDurationMs)
}

/**
 * Plays sentence clips back-to-back (used by the Read tab full-text control).
 * Tapping again while the playlist is playing stops playback.
 */
internal fun playAudioSequence(urls: List<String>, toggleIfPlaying: Boolean = false) {
    if (urls.isEmpty()) return
    val current = activeStudyAudio
    if (
        toggleIfPlaying &&
        studyAudioPlaylistActive &&
        current != null &&
        !current.paused &&
        !current.ended
    ) {
        stopActiveStudyAudio()
        return
    }
    stopActiveStudyAudio()
    studyAudioPlaylistUrls = urls
    studyAudioPlaylistIndex = 0
    studyAudioPlaylistActive = true
    playStudyAudioPlaylistItem(0)
}

private fun playStudyAudioPlaylistItem(index: Int) {
    val urls = studyAudioPlaylistUrls
    if (!studyAudioPlaylistActive || index !in urls.indices) {
        stopActiveStudyAudio()
        return
    }
    studyAudioPlaylistIndex = index
    val url = urls[index]
    stopActiveStudyAudio(clearPlaylist = false)
    val element = sharedStudyAudioElement()
    primedStudyAudio = null
    releaseStudyAudioElement(element)
    activeStudyAudio = element
    activeStudyAudioUrl = url
    studyAudioPlayingUrl = url
    bindStudyAudioPlaybackState(element, url) {
        if (!studyAudioPlaylistActive) return@bindStudyAudioPlaybackState
        val next = studyAudioPlaylistIndex + 1
        if (next in studyAudioPlaylistUrls.indices) {
            playStudyAudioPlaylistItem(next)
        } else {
            stopActiveStudyAudio()
        }
    }
    startStudyAudioPlayback(element, url)
}

/**
 * Plays a slice of existing sentence audio using TTS word timings (seconds).
 * Avoids a second synthesis call when the provider returned usable timings.
 */
private fun playAudioSegment(url: String, beginMs: Int, endMs: Int) {
    stopActiveStudyAudio()
    val startSec = (beginMs.coerceAtLeast(0)) / 1000.0
    val endSec = (endMs.coerceAtLeast(beginMs + 60) + WORD_AUDIO_END_PAD_MS) / 1000.0
    val element = sharedStudyAudioElement()
    primedStudyAudio = null
    releaseStudyAudioElement(element)
    activeStudyAudio = element
    activeStudyAudioUrl = url
    studyAudioPlayingUrl = url
    bindStudyAudioPlaybackState(element, url) {
        if (activeStudyAudio === element) stopActiveStudyAudio()
    }
    fun applyStartAndPlay() {
        val duration = element.duration
        val safeStart = if (duration.isFinite() && duration > 0) startSec.coerceAtMost(duration) else startSec
        element.currentTime = safeStart
        tryPlayStudyAudio(element)
    }
    element.asDynamic().onloadedmetadata = { applyStartAndPlay() }
    element.asDynamic().oncanplay = { applyStartAndPlay() }
    element.src = url
    element.asDynamic().ontimeupdate = {
        val duration = element.duration
        val stopAt = if (duration.isFinite() && duration > 0) endSec.coerceAtMost(duration) else endSec
        if (element.currentTime >= stopAt) {
            if (activeStudyAudio === element) stopActiveStudyAudio()
        }
    }
    // Some browsers fire loadedmetadata before the handler is attached for cached data URLs.
    if (element.readyState >= 1) applyStartAndPlay()
}


/**
 * Smooth-scrolls the study screen content pane back to the top.
 *
 * Story Continue can leave the user mid-page after a taller step; the next
 * card should start from the top of the study screen.
 */
private fun scrollStudyContentToTop() {
    val container = document.querySelector(".${Styles.content}") as? HTMLElement ?: return
    if (container.scrollTop <= 0.5) return
    val options = js("({ behavior: 'smooth', top: 0.0 })")
    container.asDynamic().scrollTo(options)
}
