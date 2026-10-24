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
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechTimingAligner
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayHistoryMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlayScenariosRequest
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudyPlayTurnRequest
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.StudyWriteCheckRequest
import apap.oneverandah.api.StudyWordPlaygroundResponse
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.ChatInput
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.state.Toaster
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.js.Date

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
    var studySessionVisible by remember { mutableStateOf(false) }
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
    // sourceMessageId (or session id fallback) -> persisted study row id
    val studyPersistedBySourceId = remember { mutableStateMapOf<String, String>() }
    var studySessionItems by remember { mutableStateOf<List<SavedStudySession>>(emptyList()) }
    var studySessionsLoading by remember { mutableStateOf(false) }
    var communitySessionItems by remember { mutableStateOf<List<SavedStudySession>>(emptyList()) }
    var communitySessionsLoading by remember { mutableStateOf(false) }
    var communitySessionsLoadingMore by remember { mutableStateOf(false) }
    var communityNextCursor by remember { mutableStateOf<String?>(null) }
    // Exact = current behavior (study the typed text). Off (default from the list) parses first.
    var studyStartExact by remember { mutableStateOf(true) }

    fun applyStudySessionItems(items: List<SavedStudySession>) {
        studySessionItems = items
        studyPersistedBySourceId.clear()
        items.forEach { item ->
            val key = item.sourceMessageId ?: item.id
            studyPersistedBySourceId[key] = item.id
            // Warm in-memory cache for matching UI language so reopen is instant.
            if (item.languageTag == language.tag) {
                studyCache[key] = item.session
            }
        }
    }

    fun refreshStudySessions(silent: Boolean = false) {
        scope.launch {
            if (!silent) studySessionsLoading = true
            runCatching { ChatApi.listStudySessions() }
                .onSuccess { applyStudySessionItems(it.items) }
                .onFailure {
                    if (!silent) Toaster.show(strings.chatStudySessionsError)
                }
            studySessionsLoading = false
        }
    }

    fun loadCommunitySessions(reset: Boolean = false) {
        if (!reset) {
            if (communitySessionsLoadingMore) return
            val cursor = communityNextCursor ?: return
            communitySessionsLoadingMore = true
            scope.launch {
                runCatching {
                    ChatApi.listCommunityStudySessions(
                        cursor = cursor,
                        limit = STUDY_COMMUNITY_PAGE_SIZE,
                    )
                }.onSuccess { page ->
                    communitySessionItems = mergeCommunityStudySessions(communitySessionItems, page.items)
                    communityNextCursor = page.nextCursor
                }.onFailure {
                    Toaster.show(strings.chatStudySessionsError)
                }
                communitySessionsLoadingMore = false
            }
            return
        }
        scope.launch {
            if (communitySessionItems.isEmpty()) communitySessionsLoading = true
            runCatching {
                ChatApi.listCommunityStudySessions(limit = STUDY_COMMUNITY_PAGE_SIZE)
            }.onSuccess { page ->
                communitySessionItems = page.items
                communityNextCursor = page.nextCursor
            }.onFailure {
                Toaster.show(strings.chatStudySessionsError)
            }
            communitySessionsLoading = false
        }
    }

    fun persistStudySession(messageId: String, session: StudySessionResponse) {
        scope.launch {
            runCatching {
                ChatApi.saveStudySession(
                    SaveStudySessionRequest(
                        sourceMessageId = messageId,
                        languageTag = language.tag,
                        session = session,
                    ),
                )
            }.onSuccess { saved ->
                studyPersistedBySourceId[messageId] = saved.id
                studySessionItems = (listOf(saved) + studySessionItems.filterNot {
                    it.id == saved.id || (saved.sourceMessageId != null && it.sourceMessageId == saved.sourceMessageId)
                }).sortedByDescending { it.updatedAt }
                if (studyTarget?.id == messageId && sessionId.isNullOrBlank()) {
                    onOpenSession(saved.id)
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

    // Assigned once loadMissingStudyWordImages is defined (local fns cannot forward-ref).
    var warmStudyWordImages: (messageId: String) -> Unit = {}

    suspend fun dissectStudySentence(messageId: String, sentenceIndex: Int) {
        val session = studyCache[messageId] ?: return
        val sentence = session.sentences.getOrNull(sentenceIndex) ?: return
        if (!sentenceNeedsDissect(sentence)) {
            if (studyTarget?.id == messageId) {
                studySentenceStatus[sentenceIndex] = StudySentenceLoadState.READY
            }
            return
        }
        if (studyTarget?.id == messageId) {
            studySentenceStatus[sentenceIndex] = StudySentenceLoadState.LOADING
        }
        runCatching {
            ChatApi.studyDissect(
                StudyDissectSentenceRequest(
                    text = session.text,
                    sentenceText = sentence.text,
                    tokens = sentence.tokens.map { it.text },
                    textLanguageTag = session.textLanguageTag,
                    languageTag = language.tag,
                ),
            )
        }.onSuccess { response ->
            val current = studyCache[messageId] ?: return@onSuccess
            if (sentenceIndex !in current.sentences.indices) return@onSuccess
            val existingSentence = current.sentences[sentenceIndex]
            // Prefer already-persisted word visuals when re-dissecting the same texts.
            val mergedTokens = response.tokens.mapIndexed { tokenIndex, token ->
                val existing = existingSentence.tokens.getOrNull(tokenIndex)
                if (
                    existing != null &&
                    existing.text == token.text &&
                    !existing.imageUrl.isNullOrBlank()
                ) {
                    token.copy(
                        imageId = existing.imageId ?: token.imageId,
                        imageUrl = existing.imageUrl,
                    )
                } else {
                    token
                }
            }
            val updatedSentences = current.sentences.toMutableList()
            updatedSentences[sentenceIndex] = StudySentence(
                text = response.sentenceText.ifBlank { existingSentence.text },
                tokens = mergedTokens,
                // Keep Study-tab visuals across dissection merges.
                imageId = existingSentence.imageId,
                imageUrl = existingSentence.imageUrl,
            )
            val updated = current.copy(sentences = updatedSentences)
            studyCache[messageId] = updated
            persistStudySession(messageId, updated)
            if (studyTarget?.id == messageId) {
                studySession = updated
                studySentenceStatus[sentenceIndex] = StudySentenceLoadState.READY
            }
            warmStudyWordImages(messageId)
        }.onFailure {
            if (studyTarget?.id == messageId) {
                studySentenceStatus[sentenceIndex] = StudySentenceLoadState.ERROR
            }
        }
    }

    fun loadMissingStudyDissections(messageId: String, session: StudySessionResponse) {
        val pendingIndices = session.sentences.indices.filter { index ->
            studySentenceStatus[index] != StudySentenceLoadState.READY
        }
        if (pendingIndices.isEmpty()) {
            warmStudyWordImages(messageId)
            return
        }
        scope.launch {
            val semaphore = Semaphore(permits = STUDY_DISSECT_PARALLELISM)
            coroutineScope {
                pendingIndices.map { index ->
                    async {
                        semaphore.withPermit {
                            dissectStudySentence(messageId, index)
                        }
                    }
                }.awaitAll()
            }
            warmStudyWordImages(messageId)
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
    }

    fun applyPlayScenarios(messageId: String, scenarios: List<StudyPlayScenario>) {
        val current = studyCache[messageId] ?: return
        if (scenarios.isEmpty()) return
        // Never wipe an already-started conversation with a fresh generation.
        if (current.playScenarios.isNotEmpty()) return
        val updated = current.copy(playScenarios = scenarios)
        studyCache[messageId] = updated
        studyPlayFailed.remove(messageId)
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
        persistStudySession(messageId, updated)
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

    fun applyPlayScenarioImage(
        messageId: String,
        scenarioId: String,
        imageId: String?,
        imageUrl: String?,
    ) {
        val nextId = imageId?.trim()?.takeIf { it.isNotEmpty() }
        val nextUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        updatePlayScenario(messageId, scenarioId) { scenario ->
            // Never clobber a completed cover URL.
            val coverId = scenario.imageId ?: nextId
            val coverUrl = scenario.imageUrl?.takeIf { it.isNotBlank() } ?: nextUrl
            val messages = scenario.messages.toMutableList()
            if (messages.isNotEmpty()) {
                val first = messages[0]
                if (first.speaker == StudyPlaySpeaker.OTHER && first.imageUrl.isNullOrBlank()) {
                    messages[0] = first.copy(
                        imageId = first.imageId ?: nextId ?: coverId,
                        imageUrl = nextUrl ?: coverUrl,
                    )
                }
            }
            scenario.copy(imageId = coverId, imageUrl = coverUrl, messages = messages)
        }
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

    suspend fun pollPlayImageUntilSettled(imageId: String): Pair<String?, Boolean> {
        val key = imageId.trim()
        if (key.isEmpty()) return null to true
        var attempts = 0
        var missingJobStreak = 0
        while (attempts < STUDY_IMAGE_POLL_MAX_ATTEMPTS) {
            val image = runCatching { ChatApi.studyImage(key) }.getOrNull()
            if (image != null) {
                missingJobStreak = 0
                when (image.status) {
                    StudyPlaygroundImageStatus.COMPLETED -> {
                        val url = image.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                        return url to (url == null)
                    }
                    StudyPlaygroundImageStatus.FAILED -> return null to true
                    StudyPlaygroundImageStatus.PENDING,
                    StudyPlaygroundImageStatus.GENERATING,
                    -> Unit
                }
            } else {
                missingJobStreak += 1
                if (missingJobStreak >= STUDY_IMAGE_MISSING_JOB_LIMIT) {
                    return null to true
                }
            }
            attempts += 1
            delay(STUDY_IMAGE_POLL_DELAY_MS)
        }
        return null to true
    }

    suspend fun pollMissingPlayImages(messageId: String, scenarios: List<StudyPlayScenario>) {
        coroutineScope {
            scenarios.map { scenario ->
                async {
                    // Cover / opening beat
                    if (scenario.imageUrl.isNullOrBlank()) {
                        val coverId = scenario.imageId?.trim().orEmpty()
                        if (coverId.isNotEmpty()) {
                            val (url, _) = pollPlayImageUntilSettled(coverId)
                            if (!url.isNullOrBlank()) {
                                applyPlayScenarioImage(messageId, scenario.id, coverId, url)
                            }
                        }
                    }
                    // Later turn images that still have an imageId but no URL
                    val latest = studyCache[messageId]?.playScenarios
                        ?.firstOrNull { it.id == scenario.id }
                        ?: scenario
                    latest.messages.forEachIndexed { index, message ->
                        if (message.speaker != StudyPlaySpeaker.OTHER) return@forEachIndexed
                        if (!message.imageUrl.isNullOrBlank()) return@forEachIndexed
                        val msgImageId = message.imageId?.trim().orEmpty()
                        if (msgImageId.isEmpty()) return@forEachIndexed
                        // Opening cover already handled above.
                        if (index == 0 && msgImageId == latest.imageId) return@forEachIndexed
                        val (url, _) = pollPlayImageUntilSettled(msgImageId)
                        if (!url.isNullOrBlank()) {
                            applyPlayMessageImage(messageId, scenario.id, msgImageId, url)
                        }
                    }
                }
            }.awaitAll()
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
        persistStudySession(messageId, updated)
    }

    fun applyStudyCharacterPortrait(messageId: String, characterId: String, imageId: String?, imageUrl: String?) {
        val current = studyCache[messageId] ?: return
        val index = current.characters.indexOfFirst { it.id == characterId }
        if (index < 0) return
        val character = current.characters[index]
        if (!character.imageUrl.isNullOrBlank()) return
        val nextId = imageId?.trim()?.takeIf { it.isNotEmpty() } ?: character.imageId
        val nextUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (character.imageId == nextId && character.imageUrl == nextUrl) return
        val updatedCharacters = current.characters.toMutableList()
        updatedCharacters[index] = character.copy(imageId = nextId, imageUrl = nextUrl)
        val updated = current.copy(characters = updatedCharacters)
        studyCache[messageId] = updated
        if (studyTarget?.id == messageId) {
            studySession = updated
        }
        persistStudySession(messageId, updated)
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
        persistStudySession(messageId, updated)
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
        persistStudySession(messageId, updated)
    }

    suspend fun ensureStudyWordImage(messageId: String, sentenceIndex: Int, tokenIndex: Int) {
        val key = studyWordImageKey(messageId, sentenceIndex, tokenIndex)
        if (studyWordImageInFlight[key] == true) return
        val session = studyCache[messageId] ?: return
        val token = session.sentences.getOrNull(sentenceIndex)?.tokens?.getOrNull(tokenIndex) ?: return
        if (!token.isDissectable) return
        if (!token.imageUrl.isNullOrBlank()) {
            studyWordImageFailed.remove(key)
            return
        }
        studyWordImageInFlight[key] = true
        studyWordImageFailed.remove(key)
        try {
            var imageId = token.imageId?.trim().orEmpty()
            if (imageId.isNotEmpty()) {
                val existing = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                when {
                    existing == null -> {
                        // Job lost (e.g. server restart) — start a fresh one.
                        imageId = ""
                    }
                    existing.status == StudyPlaygroundImageStatus.COMPLETED &&
                        !existing.imageUrl.isNullOrBlank() -> {
                        applyStudyWordImage(messageId, sentenceIndex, tokenIndex, imageId, existing.imageUrl)
                        return
                    }
                    existing.status == StudyPlaygroundImageStatus.FAILED -> {
                        studyWordImageFailed[key] = true
                        return
                    }
                    else -> Unit // PENDING / GENERATING — fall through to poll
                }
            }
            if (imageId.isEmpty()) {
                val latestToken = studyCache[messageId]
                    ?.sentences?.getOrNull(sentenceIndex)
                    ?.tokens?.getOrNull(tokenIndex)
                    ?: token
                val started = runCatching {
                    ChatApi.studyWordImage(
                        StudyWordImageRequest(
                            word = latestToken.text,
                            contextualMeaning = latestToken.contextualMeaning,
                            exampleSentences = latestToken.exampleSentences,
                        ),
                    )
                }.getOrNull()
                if (started == null) {
                    studyWordImageFailed[key] = true
                    return
                }
                imageId = started.imageId
                applyStudyWordImage(messageId, sentenceIndex, tokenIndex, imageId, null)
            }
            var attempts = 0
            var missingJobStreak = 0
            while (attempts < STUDY_IMAGE_POLL_MAX_ATTEMPTS) {
                val latest = studyCache[messageId]
                    ?.sentences?.getOrNull(sentenceIndex)
                    ?.tokens?.getOrNull(tokenIndex)
                if (!latest?.imageUrl.isNullOrBlank()) return
                val image = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                if (image != null) {
                    missingJobStreak = 0
                    when (image.status) {
                        StudyPlaygroundImageStatus.COMPLETED -> {
                            if (!image.imageUrl.isNullOrBlank()) {
                                applyStudyWordImage(
                                    messageId,
                                    sentenceIndex,
                                    tokenIndex,
                                    imageId,
                                    image.imageUrl,
                                )
                            } else {
                                studyWordImageFailed[key] = true
                            }
                            return
                        }
                        StudyPlaygroundImageStatus.FAILED -> {
                            studyWordImageFailed[key] = true
                            return
                        }
                        StudyPlaygroundImageStatus.PENDING,
                        StudyPlaygroundImageStatus.GENERATING,
                        -> Unit
                    }
                } else {
                    missingJobStreak += 1
                    if (missingJobStreak >= STUDY_IMAGE_MISSING_JOB_LIMIT) {
                        studyWordImageFailed[key] = true
                        return
                    }
                }
                attempts += 1
                delay(STUDY_IMAGE_POLL_DELAY_MS)
            }
            studyWordImageFailed[key] = true
        } finally {
            studyWordImageInFlight.remove(key)
        }
    }

    fun loadMissingStudyWordImages(messageId: String, session: StudySessionResponse) {
        val pending = mutableListOf<Pair<Int, Int>>()
        session.sentences.forEachIndexed { sentenceIndex, sentence ->
            sentence.tokens.forEachIndexed { tokenIndex, token ->
                if (
                    token.isDissectable &&
                    token.imageUrl.isNullOrBlank() &&
                    studyWordImageFailed[studyWordImageKey(messageId, sentenceIndex, tokenIndex)] != true
                ) {
                    pending += sentenceIndex to tokenIndex
                }
            }
        }
        if (pending.isEmpty()) return
        scope.launch {
            val semaphore = Semaphore(permits = STUDY_IMAGE_PARALLELISM)
            coroutineScope {
                pending.map { (sentenceIndex, tokenIndex) ->
                    async {
                        semaphore.withPermit {
                            ensureStudyWordImage(messageId, sentenceIndex, tokenIndex)
                        }
                    }
                }.awaitAll()
            }
        }
    }
    warmStudyWordImages = { messageId ->
        studyCache[messageId]?.let { loadMissingStudyWordImages(messageId, it) }
    }

    /**
     * Ensures the study session has a designed cast and waits for portrait URLs
     * (or portrait failure) so sentence panels can use named reference images.
     * Returns the latest cast from the cache (may be empty if design failed).
     */
    suspend fun ensureStudyCharacters(messageId: String): List<StudyStoryCharacter> {
        val session = studyCache[messageId] ?: return emptyList()
        val existing = session.characters
        // Prefer starting scenes once the main portrait is ready; secondary can lag.
        if (existing.isNotEmpty() && studyCastReadyForScenes(existing)) return existing

        // Another coroutine may already be designing/polling this cast.
        if (studyCharactersInFlight[messageId] == true) {
            var wait = 0
            while (studyCharactersInFlight[messageId] == true && wait < STUDY_MAIN_PORTRAIT_WAIT_ATTEMPTS) {
                delay(STUDY_IMAGE_POLL_DELAY_MS)
                wait += 1
                val latest = studyCache[messageId]?.characters.orEmpty()
                if (latest.isNotEmpty() && studyCastReadyForScenes(latest)) return latest
            }
            return studyCache[messageId]?.characters.orEmpty()
        }

        studyCharactersInFlight[messageId] = true
        try {
            var cast = studyCache[messageId]?.characters.orEmpty()
            if (cast.isEmpty()) {
                val designed = runCatching {
                    ChatApi.studyStoryCharacters(
                        StudyStoryCharactersRequest(
                            text = session.text,
                            textLanguageTag = session.textLanguageTag,
                        ),
                    )
                }.getOrNull()?.characters.orEmpty()
                if (designed.isEmpty()) return emptyList()
                applyStudyCharacters(messageId, designed)
                cast = studyCache[messageId]?.characters.orEmpty()
            }

            // Poll portraits in parallel so a slow secondary does not block the main.
            val pendingPortraits = cast.filter {
                it.imageUrl.isNullOrBlank() && !it.imageId.isNullOrBlank()
            }
            if (pendingPortraits.isNotEmpty()) {
                coroutineScope {
                    pendingPortraits.map { character ->
                        async {
                            val imageId = character.imageId?.trim().orEmpty()
                            if (imageId.isEmpty()) return@async
                            val existingJob = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                            when {
                                existingJob == null -> return@async
                                existingJob.status == StudyPlaygroundImageStatus.COMPLETED &&
                                    !existingJob.imageUrl.isNullOrBlank() -> {
                                    applyStudyCharacterPortrait(
                                        messageId,
                                        character.id,
                                        imageId,
                                        existingJob.imageUrl,
                                    )
                                    return@async
                                }
                                existingJob.status == StudyPlaygroundImageStatus.FAILED -> return@async
                                else -> Unit
                            }
                            var attempts = 0
                            var missingJobStreak = 0
                            // Main portrait gets a longer wait; secondary is best-effort and shorter.
                            val isMain = character.role.equals("main", ignoreCase = true) ||
                                character.id.equals("main", ignoreCase = true)
                            val portraitAttempts = if (isMain) {
                                STUDY_MAIN_PORTRAIT_WAIT_ATTEMPTS
                            } else {
                                (STUDY_MAIN_PORTRAIT_WAIT_ATTEMPTS / 2).coerceAtLeast(12)
                            }
                            while (attempts < portraitAttempts) {
                                val latestUrl = studyCache[messageId]?.characters
                                    ?.firstOrNull { it.id == character.id }
                                    ?.imageUrl
                                if (!latestUrl.isNullOrBlank()) break
                                val image = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                                if (image != null) {
                                    missingJobStreak = 0
                                    when (image.status) {
                                        StudyPlaygroundImageStatus.COMPLETED -> {
                                            if (!image.imageUrl.isNullOrBlank()) {
                                                applyStudyCharacterPortrait(
                                                    messageId,
                                                    character.id,
                                                    imageId,
                                                    image.imageUrl,
                                                )
                                            }
                                            break
                                        }
                                        StudyPlaygroundImageStatus.FAILED -> break
                                        StudyPlaygroundImageStatus.PENDING,
                                        StudyPlaygroundImageStatus.GENERATING,
                                        -> Unit
                                    }
                                } else {
                                    missingJobStreak += 1
                                    if (missingJobStreak >= STUDY_IMAGE_MISSING_JOB_LIMIT) break
                                }
                                attempts += 1
                                delay(STUDY_IMAGE_POLL_DELAY_MS)
                            }
                        }
                    }.awaitAll()
                }
            }
            return studyCache[messageId]?.characters.orEmpty()
        } finally {
            studyCharactersInFlight.remove(messageId)
        }
    }

    suspend fun ensureStudySentenceImage(messageId: String, sentenceIndex: Int) {
        val key = studyImageKey(messageId, sentenceIndex)
        if (studyImageInFlight[key] == true) return
        val session = studyCache[messageId] ?: return
        val sentence = session.sentences.getOrNull(sentenceIndex) ?: return
        if (!sentence.imageUrl.isNullOrBlank()) {
            studyImageFailed.remove(key)
            return
        }
        studyImageInFlight[key] = true
        studyImageFailed.remove(key)
        try {
            var imageId = sentence.imageId?.trim().orEmpty()
            if (imageId.isNotEmpty()) {
                val existing = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                when {
                    existing == null -> {
                        // Job lost (e.g. server restart) — start a fresh one.
                        imageId = ""
                    }
                    existing.status == StudyPlaygroundImageStatus.COMPLETED &&
                        !existing.imageUrl.isNullOrBlank() -> {
                        applyStudySentenceImage(messageId, sentenceIndex, imageId, existing.imageUrl)
                        return
                    }
                    existing.status == StudyPlaygroundImageStatus.FAILED -> {
                        // Previous job failed — drop it so retry can enqueue a new one.
                        imageId = ""
                    }
                    else -> Unit // PENDING / GENERATING — fall through to poll
                }
            }
            if (imageId.isEmpty()) {
                val latestSession = studyCache[messageId] ?: session
                val started = runCatching {
                    ChatApi.studySentenceImage(
                        StudySentenceImageRequest(
                            sentenceText = sentence.text,
                            fullText = latestSession.text,
                            sentenceIndex = sentenceIndex,
                            sentenceCount = latestSession.sentences.size,
                            characters = latestSession.characters,
                        ),
                    )
                }.getOrNull()
                if (started == null) {
                    studyImageFailed[key] = true
                    return
                }
                imageId = started.imageId
                applyStudySentenceImage(messageId, sentenceIndex, imageId, null)
            }
            var attempts = 0
            var missingJobStreak = 0
            while (attempts < STUDY_IMAGE_POLL_MAX_ATTEMPTS) {
                // Session may have received the URL from another path.
                val latest = studyCache[messageId]?.sentences?.getOrNull(sentenceIndex)
                if (!latest?.imageUrl.isNullOrBlank()) return
                val image = runCatching { ChatApi.studyImage(imageId) }.getOrNull()
                if (image != null) {
                    missingJobStreak = 0
                    when (image.status) {
                        StudyPlaygroundImageStatus.COMPLETED -> {
                            if (!image.imageUrl.isNullOrBlank()) {
                                applyStudySentenceImage(messageId, sentenceIndex, imageId, image.imageUrl)
                            } else {
                                studyImageFailed[key] = true
                            }
                            return
                        }
                        StudyPlaygroundImageStatus.FAILED -> {
                            studyImageFailed[key] = true
                            return
                        }
                        StudyPlaygroundImageStatus.PENDING,
                        StudyPlaygroundImageStatus.GENERATING,
                        -> Unit
                    }
                } else {
                    missingJobStreak += 1
                    if (missingJobStreak >= STUDY_IMAGE_MISSING_JOB_LIMIT) {
                        // Job vanished from the in-memory store — do not spin for minutes.
                        studyImageFailed[key] = true
                        return
                    }
                }
                attempts += 1
                delay(STUDY_IMAGE_POLL_DELAY_MS)
            }
            studyImageFailed[key] = true
        } finally {
            studyImageInFlight.remove(key)
        }
    }

    fun loadMissingStudyImages(messageId: String, session: StudySessionResponse) {
        val pendingIndices = session.sentences.indices.filter { index ->
            val sentence = session.sentences[index]
            sentence.imageUrl.isNullOrBlank() &&
                studyImageFailed[studyImageKey(messageId, index)] != true
        }
        if (pendingIndices.isEmpty()) return
        scope.launch {
            // Design + portrait references first so sentence panels share one cast.
            ensureStudyCharacters(messageId)
            val latest = studyCache[messageId] ?: return@launch
            val indices = latest.sentences.indices.filter { index ->
                val sentence = latest.sentences[index]
                sentence.imageUrl.isNullOrBlank() &&
                    studyImageFailed[studyImageKey(messageId, index)] != true
            }
            if (indices.isEmpty()) return@launch
            val semaphore = Semaphore(permits = STUDY_IMAGE_PARALLELISM)
            coroutineScope {
                indices.map { index ->
                    async {
                        semaphore.withPermit {
                            ensureStudySentenceImage(messageId, index)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Ensures Play-tab scenarios (and cover images) are generated as soon as the
     * study session is ready, then persisted so the Play tab opens pre-warmed.
     */
    fun loadMissingPlayScenarios(messageId: String, session: StudySessionResponse) {
        if (studyPlayInFlight[messageId] == true) return
        scope.launch {
            if (studyPlayInFlight[messageId] == true) return@launch
            studyPlayInFlight[messageId] = true
            try {
                val cached = studyCache[messageId] ?: session
                val existing = cached.playScenarios
                if (existing.isNotEmpty()) {
                    studyPlayFailed.remove(messageId)
                    // Older saves may only have scenario metadata; hydrate opening beats once.
                    val hydrated = existing.map { scenario ->
                        if (scenario.messages.isNotEmpty()) return@map scenario
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
                    if (hydrated != existing) {
                        val updated = cached.copy(playScenarios = hydrated)
                        studyCache[messageId] = updated
                        if (studyTarget?.id == messageId) {
                            studySession = updated
                        }
                        persistStudySession(messageId, updated)
                        pollMissingPlayImages(messageId, hydrated)
                    } else {
                        pollMissingPlayImages(messageId, existing)
                    }
                    return@launch
                }
                // Prefer character portraits as image references when available.
                ensureStudyCharacters(messageId)
                val latest = studyCache[messageId] ?: return@launch
                if (latest.playScenarios.isNotEmpty()) {
                    studyPlayFailed.remove(messageId)
                    pollMissingPlayImages(messageId, latest.playScenarios)
                    return@launch
                }
                val response = runCatching {
                    ChatApi.studyPlayScenarios(
                        StudyPlayScenariosRequest(
                            text = latest.text,
                            textLanguageTag = latest.textLanguageTag,
                            languageTag = language.tag,
                            characters = latest.characters,
                        ),
                    )
                }.getOrNull()
                if (response == null || response.scenarios.isEmpty()) {
                    studyPlayFailed[messageId] = true
                    return@launch
                }
                val scenarios = response.scenarios.map { scenario ->
                    val opening = scenario.openingLine?.trim()?.takeIf { it.isNotEmpty() }
                    val imageId = scenario.imageId?.trim()?.takeIf { it.isNotEmpty() }
                    val imageUrl = scenario.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    val openingMessage = StudyPlayChatMessage(
                        speaker = StudyPlaySpeaker.OTHER,
                        text = opening.orEmpty(),
                        description = scenario.description,
                        imageId = imageId,
                        imageUrl = imageUrl,
                    )
                    StudyPlayScenario(
                        id = scenario.id,
                        description = scenario.description,
                        openingLine = opening,
                        imageId = imageId,
                        imageUrl = imageUrl,
                        messages = listOf(openingMessage),
                    )
                }
                applyPlayScenarios(messageId, scenarios)
                pollMissingPlayImages(messageId, scenarios)
            } finally {
                studyPlayInFlight.remove(messageId)
            }
        }
    }

    fun openStudy(message: ChatMessage) {
        val text = studyTextFromChatMessage(message)
        if (text.isEmpty()) return
        studyTarget = message
        studySessionVisible = true
        studyError = null
        clearStudyImageTracking(message.id)
        val cached = studyCache[message.id]
        if (cached != null) {
            studySession = cached
            studyLoading = false
            syncStudySentenceStatuses(cached)
            loadMissingStudyDissections(message.id, cached)
            loadMissingStudyImages(message.id, cached)
            loadMissingStudyWordImages(message.id, cached)
            loadMissingPlayScenarios(message.id, cached)
            return
        }
        studySession = null
        studySentenceStatus.clear()
        studyLoading = true
        scope.launch {
            runCatching {
                ChatApi.study(StudySessionRequest(text = text, languageTag = language.tag))
            }.onSuccess { session ->
                studyCache[message.id] = session
                persistStudySession(message.id, session)
                if (studyTarget?.id == message.id) {
                    studySession = session
                    studyError = null
                    studyLoading = false
                    syncStudySentenceStatuses(session)
                    loadMissingStudyDissections(message.id, session)
                    loadMissingStudyImages(message.id, session)
                    loadMissingStudyWordImages(message.id, session)
                    loadMissingPlayScenarios(message.id, session)
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
        // Meanings are in the learner language stored with the session; regenerate if UI language changed.
        if (item.languageTag == language.tag) {
            studyCache[key] = item.session
        } else {
            studyCache.remove(key)
        }
        if (!fromCommunity) {
            studyPersistedBySourceId[key] = item.id
        }
        openStudy(
            ChatMessage(
                id = key,
                sender = ChatSender.AI,
                text = item.session.text,
                createdAt = item.createdAt,
            ),
        )
    }

    fun startStudyFromText(raw: String?, exact: Boolean = true) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        studyStartExact = exact
        if (exact) {
            openStudy(
                ChatMessage(
                    id = "study-${Date.now().toLong()}",
                    sender = ChatSender.YOU,
                    text = text,
                    createdAt = Date.now().toLong(),
                ),
            )
            return
        }
        val messageId = "study-${Date.now().toLong()}"
        val createdAt = Date.now().toLong()
        studyTarget = ChatMessage(
            id = messageId,
            sender = ChatSender.YOU,
            text = text,
            createdAt = createdAt,
        )
        studySessionVisible = true
        studyError = null
        studySession = null
        studySentenceStatus.clear()
        studyLoading = true
        scope.launch {
            runCatching {
                ChatApi.studyIntent(StudySessionRequest(text = text, languageTag = language.tag))
            }.onSuccess { intent ->
                val studyText = intent.text.trim().ifEmpty { text }
                if (studyTarget?.id != messageId || !studySessionVisible) return@onSuccess
                studyStartExact = true
                openStudy(
                    ChatMessage(
                        id = messageId,
                        sender = ChatSender.YOU,
                        text = studyText,
                        createdAt = createdAt,
                    ),
                )
            }.onFailure {
                if (studyTarget?.id == messageId && studySessionVisible) {
                    studyError = strings.chatStudyError
                    studyLoading = false
                }
            }
        }
    }

    fun closeStudy() {
        studySessionVisible = false
        stopActiveStudyAudio()
        if (!sessionId.isNullOrBlank()) {
            onCloseSession()
        }
    }

    fun removeStudySession(item: SavedStudySession) {
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

    fun retryStudySentence(sentenceIndex: Int) {
        val messageId = studyTarget?.id ?: return
        scope.launch {
            dissectStudySentence(messageId, sentenceIndex)
        }
    }

    fun retryStudySentenceImage(sentenceIndex: Int) {
        val messageId = studyTarget?.id ?: return
        val key = studyImageKey(messageId, sentenceIndex)
        if (studyImageInFlight[key] == true) return
        studyImageFailed.remove(key)
        scope.launch {
            ensureStudyCharacters(messageId)
            ensureStudySentenceImage(messageId, sentenceIndex)
        }
    }


    LaunchedEffect(Unit) {
        refreshStudySessions(silent = true)
        loadCommunitySessions(reset = true)
    }

    LaunchedEffect(language.tag) {
        // Definitions are localized to the active UI language, so drop stale sessions.
        studyCache.clear()
        studySentenceStatus.clear()
        // Re-warm cache from server snapshots that match the new UI language.
        studySessionItems.filter { it.languageTag == language.tag }.forEach { item ->
            val key = item.sourceMessageId ?: item.id
            studyCache[key] = item.session
        }
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
        val ownItems = runCatching { ChatApi.listStudySessions() }.getOrNull()?.items
        if (ownItems != null) applyStudySessionItems(ownItems)
        val local = studySessionItems.find { it.id == id }
        if (local != null) {
            openSavedStudySession(local, fromCommunity = false)
            return@LaunchedEffect
        }
        runCatching { ChatApi.getStudySession(id) }
            .onSuccess { item ->
                openSavedStudySession(
                    item,
                    fromCommunity = studySessionItems.none { owned -> owned.id == item.id },
                )
            }
            .onFailure {
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
        title = if (studySessionVisible) strings.chatStudyTitle else strings.studyTitle,
        onBack = {
            if (studySessionVisible) closeStudy() else onBack()
        },
        onHome = onHome,
    ) {
        if (studySessionVisible) {
            StudySessionPane(
                loading = studyLoading,
                error = studyError,
                session = studySession,
                sentenceStatus = studySentenceStatus.toMap(),
                messageId = studyTarget?.id,
                imageFailed = studyImageFailed.toMap(),
                wordImageFailed = studyWordImageFailed.toMap(),
                playLoading = studyTarget?.id?.let { studyPlayInFlight[it] == true } == true,
                playFailed = studyTarget?.id?.let { studyPlayFailed[it] == true } == true,
                strings = strings,
                onRetry = {
                    val target = studyTarget ?: return@StudySessionPane
                    if (studyStartExact) {
                        openStudy(target)
                    } else {
                        startStudyFromText(target.text, exact = false)
                    }
                },
                onRetrySentence = ::retryStudySentence,
                onRetryImage = ::retryStudySentenceImage,
                onRetryPlay = {
                    val target = studyTarget ?: return@StudySessionPane
                    val session = studyCache[target.id] ?: studySession ?: return@StudySessionPane
                    studyPlayFailed.remove(target.id)
                    loadMissingPlayScenarios(target.id, session)
                },
                onPlayScenarioUpdate = { scenarioId, transform ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    updatePlayScenario(targetId, scenarioId, transform)
                },
                onPlayMessageImage = { scenarioId, imageId, imageUrl ->
                    val targetId = studyTarget?.id ?: return@StudySessionPane
                    applyPlayMessageImage(targetId, scenarioId, imageId, imageUrl)
                },
            )
        } else {
            StudySessionsList(
                items = studySessionItems,
                loading = studySessionsLoading,
                communityItems = communitySessionItems,
                communityLoading = communitySessionsLoading,
                communityLoadingMore = communitySessionsLoadingMore,
                communityHasMore = communityNextCursor != null,
                strings = strings,
                onStart = ::startStudyFromText,
                onOpen = { onOpenSession(it.id) },
                onOpenCommunity = { onOpenSession(it.id) },
                onRemove = ::removeStudySession,
                onLoadMoreCommunity = { loadCommunitySessions() },
            )
        }
    }
}


@Composable
private fun StudySessionsList(
    items: List<SavedStudySession>,
    loading: Boolean,
    communityItems: List<SavedStudySession>,
    communityLoading: Boolean,
    communityLoadingMore: Boolean,
    communityHasMore: Boolean,
    strings: UiStrings,
    onStart: (text: String?, exact: Boolean) -> Unit,
    onOpen: (SavedStudySession) -> Unit,
    onOpenCommunity: (SavedStudySession) -> Unit,
    onRemove: (SavedStudySession) -> Unit,
    onLoadMoreCommunity: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var exact by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<SavedStudySession?>(null) }
    val loadMoreCommunity by rememberUpdatedState(onLoadMoreCommunity)

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
        P(attrs = { classes(Styles.studySessionSectionTitle) }) {
            Text(strings.chatStudySessionsMine)
        }
        when {
            loading && items.isEmpty() -> {
                Div(attrs = { classes(Styles.chatStatus) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatThinking)
                }
            }
            items.isEmpty() -> {
                Div(attrs = { classes(Styles.savedPanelEmpty) }) {
                    MaterialSymbol("menu_book")
                    P { Text(strings.chatStudySessionsEmpty) }
                }
            }
            else -> items.forEach { item ->
                StudySessionCard(
                    item = item,
                    strings = strings,
                    removable = true,
                    onOpen = { onOpen(item) },
                    onRemove = { pendingRemove = item },
                )
            }
        }
        P(attrs = { classes(Styles.studySessionSectionTitle) }) {
            Text(strings.chatStudySessionsCommunity)
        }
        when {
            communityLoading && communityItems.isEmpty() -> {
                Div(attrs = { classes(Styles.chatStatus) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatThinking)
                }
            }
            communityItems.isEmpty() -> {
                Div(attrs = { classes(Styles.savedPanelEmpty) }) {
                    MaterialSymbol("groups")
                    P { Text(strings.chatStudySessionsCommunityEmpty) }
                }
            }
            else -> communityItems.forEach { item ->
                StudySessionCard(
                    item = item,
                    strings = strings,
                    removable = false,
                    onOpen = { onOpenCommunity(item) },
                    onRemove = {},
                )
            }
        }
        if (communityLoadingMore) {
            Div(attrs = { classes(Styles.chatStatus) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatThinking)
            }
        }
        if (communityHasMore) {
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
private fun StudySessionCard(
    item: SavedStudySession,
    strings: UiStrings,
    removable: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Div(attrs = { classes(Styles.savedItem, Styles.studySessionCard) }) {
        if (removable) {
            Button(attrs = {
                classes(Styles.studySessionRemoveBtn)
                attr("type", "button")
                attr("aria-label", strings.chatStudySessionsRemove)
                onClick { onRemove() }
            }) {
                MaterialSymbol("delete")
            }
        }
        val phraseImages = studySessionPhraseImageUrls(item.session)
        if (phraseImages.isNotEmpty()) {
            Div(attrs = { classes(Styles.studySessionPhraseRow) }) {
                phraseImages.forEach { url ->
                    Img(
                        src = url,
                        alt = strings.chatStudySentenceImageAlt,
                        attrs = { classes(Styles.studySessionPhraseImage) },
                    )
                }
            }
        }
        Span(attrs = { classes(Styles.savedItemSender) }) {
            Text(strings.chatStudyTitle)
        }
        P(attrs = { classes(Styles.savedItemText) }) { Text(item.text) }
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
private fun StudySessionRemoveConfirmDialog(
    item: SavedStudySession?,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: (SavedStudySession) -> Unit,
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
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    playLoading: Boolean,
    playFailed: Boolean,
    strings: UiStrings,
    onRetry: () -> Unit,
    onRetrySentence: (Int) -> Unit,
    onRetryImage: (Int) -> Unit,
    onRetryPlay: () -> Unit,
    onPlayScenarioUpdate: (String, (StudyPlayScenario) -> StudyPlayScenario) -> Unit,
    onPlayMessageImage: (scenarioId: String, imageId: String, imageUrl: String?) -> Unit,
) {
    Div(attrs = { classes(Styles.studySessionBody) }) {
            when {
                loading && session == null -> {
                    Div(attrs = { classes(Styles.chatStatus) }) {
                        MaterialSymbol("progress_activity")
                        Text(strings.chatStudyLoading)
                    }
                }
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
                session != null -> StudySessionContent(
                    session = session,
                    sentenceStatus = sentenceStatus,
                    messageId = messageId,
                    imageFailed = imageFailed,
                    wordImageFailed = wordImageFailed,
                    playLoading = playLoading,
                    playFailed = playFailed,
                    strings = strings,
                    onRetrySentence = onRetrySentence,
                    onRetryImage = onRetryImage,
                    onRetryPlay = onRetryPlay,
                    onPlayScenarioUpdate = onPlayScenarioUpdate,
                    onPlayMessageImage = onPlayMessageImage,
                )
                else -> {
                    Div(attrs = { classes(Styles.chatStatus) }) {
                        MaterialSymbol("progress_activity")
                        Text(strings.chatStudyLoading)
                    }
                }
            }
        }
}

@Composable
private fun StudySessionContent(
    session: StudySessionResponse,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    messageId: String?,
    imageFailed: Map<String, Boolean>,
    wordImageFailed: Map<String, Boolean>,
    playLoading: Boolean,
    playFailed: Boolean,
    strings: UiStrings,
    onRetrySentence: (Int) -> Unit,
    onRetryImage: (Int) -> Unit,
    onRetryPlay: () -> Unit,
    onPlayScenarioUpdate: (String, (StudyPlayScenario) -> StudyPlayScenario) -> Unit,
    onPlayMessageImage: (scenarioId: String, imageId: String, imageUrl: String?) -> Unit,
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
    var mode by remember(session.text) { mutableStateOf(StudySessionMode.ALL) }
    var highlightedWord by remember(session.text) { mutableStateOf<String?>(null) }
    val speechItems = remember(session.text, session.textLanguageTag, session.sentences.map { it.text }) {
        session.sentences.mapIndexed { index, sentence -> "sentence-$index" to sentence.text }
    }
    val anyLoading = sentenceStatus.values.any { it == StudySentenceLoadState.LOADING }

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
                withSpeechRetry {
                    ChatApi.speechDataUrl(SpeechRequest(tokenText, session.textLanguageTag))
                }
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
                            val speech = withSpeechRetry {
                                ChatApi.speech(SpeechRequest(text, session.textLanguageTag))
                            }
                            key to StudySentenceAudio(
                                url = "data:${speech.contentType};base64,${speech.audioBase64}",
                                words = speech.words,
                            )
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull().forEach { (key, clip) -> sentenceAudio[key] = clip }
            }
        }
    }

    Div(attrs = { classes(Styles.studyModeTabs) }) {
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.ALL) classes(Styles.chipActive)
            attr("type", "button")
            onClick { mode = StudySessionMode.ALL }
        }) { Text(strings.chatAllTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.WATCH) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.WATCH
                selectedRef = null
            }
        }) { Text(strings.chatWatchTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.STORY) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.STORY
                selectedRef = null
            }
        }) { Text(strings.chatStoryTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.STUDY) classes(Styles.chipActive)
            attr("type", "button")
            onClick { mode = StudySessionMode.STUDY }
        }) { Text(strings.chatStudyTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.READ) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.READ
                selectedRef = null
            }
        }) { Text(strings.chatReadTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.WRITE) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.WRITE
                selectedRef = null
            }
        }) { Text(strings.chatWriteTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.EXPRESS) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.EXPRESS
                selectedRef = null
            }
        }) { Text(strings.chatExpressTab) }
        Button(attrs = {
            classes(Styles.chip)
            if (mode == StudySessionMode.PLAY) classes(Styles.chipActive)
            attr("type", "button")
            onClick {
                mode = StudySessionMode.PLAY
                selectedRef = null
            }
        }) { Text(strings.chatPlayTab) }
    }

    val modesToRender = if (mode == StudySessionMode.ALL) {
        StudySessionMode.values().filter { it != StudySessionMode.ALL }
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
                            )
                        }
                    }
                }
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
                            playAudioSequence(readClipUrls)
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
            }
            StudySessionMode.EXPRESS -> {
                StudyExpressTab(
                    session = session,
                    anyLoading = anyLoading,
                    strings = strings,
                    autoFocus = mode != StudySessionMode.ALL,
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
private fun StudySentenceVisual(
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

private enum class StudySessionMode { ALL, WATCH, STORY, STUDY, READ, WRITE, EXPRESS, PLAY }

private fun StudySessionMode.tabLabel(strings: UiStrings): String = when (this) {
    StudySessionMode.ALL -> strings.chatAllTab
    StudySessionMode.WATCH -> strings.chatWatchTab
    StudySessionMode.STORY -> strings.chatStoryTab
    StudySessionMode.STUDY -> strings.chatStudyTab
    StudySessionMode.READ -> strings.chatReadTab
    StudySessionMode.WRITE -> strings.chatWriteTab
    StudySessionMode.EXPRESS -> strings.chatExpressTab
    StudySessionMode.PLAY -> strings.chatPlayTab
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

private fun currentFullscreenElement(): Element? {
    val doc = document.asDynamic()
    return (
        doc.fullscreenElement
            ?: doc.webkitFullscreenElement
            ?: doc.mozFullScreenElement
            ?: doc.msFullscreenElement
        ) as? Element
}

private fun requestWatchFullscreen(element: Element) {
    val el = element.asDynamic()
    val request = el.requestFullscreen
        ?: el.webkitRequestFullscreen
        ?: el.webkitRequestFullScreen
        ?: el.mozRequestFullScreen
        ?: el.msRequestFullscreen
    if (request == null) return
    request.call(element)
}

private fun exitWatchFullscreen() {
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

private enum class StudyStoryPhase { WORDS, SENTENCES }

/** Ordered story-walkthrough steps for non-plain study tokens. */
internal data class StoryWordStep(
    val sentenceIndex: Int,
    val tokenIndex: Int,
)

/** Collects every non-plain token in reading order for the Story tab. */
internal fun collectStoryWordSteps(session: StudySessionResponse): List<StoryWordStep> {
    val steps = mutableListOf<StoryWordStep>()
    session.sentences.forEachIndexed { sentenceIndex, sentence ->
        sentence.tokens.forEachIndexed { tokenIndex, token ->
            if (token.status != StudyTokenStatus.PLAIN && token.text.isNotBlank()) {
                steps += StoryWordStep(sentenceIndex = sentenceIndex, tokenIndex = tokenIndex)
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

/** Completed sentence visuals for a saved study card, in reading order. */
internal fun studySessionPhraseImageUrls(session: StudySessionResponse): List<String> =
    session.sentences.mapNotNull { sentence ->
        sentence.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    }

internal const val STUDY_COMMUNITY_PAGE_SIZE = 20

/** Client route for a persisted study session, e.g. `/study/{id}`. */
internal fun studySessionPath(id: String): String = "/study/${id.trim()}"

/** Appends newly fetched community sessions, skipping ids already on screen. */
internal fun mergeCommunityStudySessions(
    existing: List<SavedStudySession>,
    incoming: List<SavedStudySession>,
): List<SavedStudySession> {
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
            if (key == " " || key == "Spacebar") {
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

    LaunchedEffect(sentenceAudio.keys.joinToString()) {
        sentenceAudio.forEach { (key, clip) ->
            if (!measuredDurations.containsKey(key)) {
                measuredDurations[key] = watchClipDurationMs(clip.words)
            }
            probeAudioDurationMs(clip.url) { ms ->
                if (ms > 0) measuredDurations[key] = ms
            }
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
    val wordSteps = remember(session) { collectStoryWordSteps(session) }
    val sentenceCount = session.sentences.size
    var phase by remember(session.text) {
        mutableStateOf(
            if (wordSteps.isNotEmpty()) StudyStoryPhase.WORDS else StudyStoryPhase.SENTENCES,
        )
    }
    var stepIndex by remember(session.text) { mutableStateOf(0) }
    var lastAutoplayKey by remember(session.text) { mutableStateOf<String?>(null) }
    // Learner-language meaning clips (literal / context) keyed by spoken text.
    val meaningAudio = remember(session.text) { mutableStateMapOf<String, String>() }
    val learnerLanguageTag = Localization.language.tag

    fun advanceStory() {
        // Start each next story step (or the Study tab handoff) from the top.
        if (scrollOnContinue) scrollStudyContentToTop()
        when (phase) {
            StudyStoryPhase.WORDS -> {
                if (stepIndex < wordSteps.lastIndex) {
                    stepIndex += 1
                } else if (sentenceCount > 0) {
                    phase = StudyStoryPhase.SENTENCES
                    stepIndex = 0
                } else {
                    onFinished()
                }
            }
            StudyStoryPhase.SENTENCES -> {
                if (stepIndex < sentenceCount - 1) {
                    stepIndex += 1
                } else {
                    onFinished()
                }
            }
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
    val totalSteps = wordSteps.size + sentenceCount
    val currentOrdinal = when (phase) {
        StudyStoryPhase.WORDS -> safeWordIndex + 1
        StudyStoryPhase.SENTENCES -> wordSteps.size + safeSentenceIndex + 1
    }.coerceAtLeast(1)
    val progressLabel = if (totalSteps > 0) "$currentOrdinal / $totalSteps" else ""

    when (phase) {
        StudyStoryPhase.WORDS -> {
            val step = wordSteps.getOrNull(safeWordIndex)
            if (step == null) {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatStudyPreparingMeanings)
                }
                return
            }
            val token = session.sentences
                .getOrNull(step.sentenceIndex)
                ?.tokens
                ?.getOrNull(step.tokenIndex)
            if (token == null) {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatStudyPreparingMeanings)
                }
                return
            }
            val wordFailKey = messageId?.let { mid ->
                "$mid#${step.sentenceIndex}#${step.tokenIndex}"
            }
            val wordFailed = wordFailKey != null && wordImageFailed[wordFailKey] == true
            val sentenceClipUrl = sentenceAudio["sentence-${step.sentenceIndex}"]?.url
            val meaningTexts = collectStoryMeaningNarrationTexts(
                literalMeaning = token.literalMeaning,
                contextualMeaning = token.contextualMeaning,
            )
            // Include clip url so the first timed segment can replace a missing-timing attempt.
            // Meaning texts are part of the key so late-arriving dissections also get spoken.
            val wordAutoplayKey =
                "word-${step.sentenceIndex}-${step.tokenIndex}-${token.text}|${sentenceClipUrl.orEmpty()}"
            val autoplayKey = "$wordAutoplayKey#${meaningTexts.joinToString(separator = "\u0001")}"
            LaunchedEffect(autoplayKey) {
                if (lastAutoplayKey == autoplayKey) return@LaunchedEffect
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
                        withSpeechRetry {
                            ChatApi.speechDataUrl(SpeechRequest(text, learnerLanguageTag))
                        }
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
                if (progressLabel.isNotEmpty()) {
                    Span(attrs = { classes(Styles.studyStoryProgress) }) { Text(progressLabel) }
                }
                StudyStoryVisual(
                    imageUrl = token.imageUrl,
                    failed = wordFailed,
                    loadingLabel = strings.chatStudyWordImageLoading,
                    errorLabel = strings.chatStudyWordImageError,
                    alt = strings.chatStudyWordImageAlt,
                )
                P(attrs = { classes(Styles.studyStoryWord) }) { Text(token.text) }
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
                Button(attrs = {
                    classes(Styles.btnPrimary, Styles.studyStoryContinue)
                    attr("type", "button")
                    onClick { advanceStory() }
                }) {
                    Text(strings.chatStoryContinue)
                }
            }
        }
        StudyStoryPhase.SENTENCES -> {
            if (sentenceCount <= 0) {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatStudyPreparingMeanings)
                }
                return
            }
            val sentence = session.sentences[safeSentenceIndex]
            val imageFailKey = messageId?.let { "$it#$safeSentenceIndex" }
            val visualFailed = imageFailKey != null && imageFailed[imageFailKey] == true
            val sentenceClip = sentenceAudio["sentence-$safeSentenceIndex"]
            val autoplayKey = "sentence-$safeSentenceIndex-${sentenceClip?.url.orEmpty()}"
            LaunchedEffect(autoplayKey) {
                val url = sentenceClip?.url ?: return@LaunchedEffect
                if (lastAutoplayKey == autoplayKey) return@LaunchedEffect
                lastAutoplayKey = autoplayKey
                playAudio(url, restart = true)
            }

            Div(attrs = { classes(Styles.studyStoryCard) }) {
                if (progressLabel.isNotEmpty()) {
                    Span(attrs = { classes(Styles.studyStoryProgress) }) { Text(progressLabel) }
                }
                StudyStoryVisual(
                    imageUrl = sentence.imageUrl,
                    failed = visualFailed,
                    loadingLabel = strings.chatStudySentenceImageLoading,
                    errorLabel = strings.chatStudySentenceImageError,
                    alt = strings.chatStudySentenceImageAlt,
                    retryLabel = strings.chatStudyRetrySentence,
                    onRetry = { onRetryImage(safeSentenceIndex) },
                )
                P(attrs = { classes(Styles.studyStorySentence) }) { Text(sentence.text) }
                Button(attrs = {
                    classes(Styles.btnPrimary, Styles.studyStoryContinue)
                    attr("type", "button")
                    onClick { advanceStory() }
                }) {
                    Text(strings.chatStoryContinue)
                }
            }
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
) {
    val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    when {
        url != null -> {
            Img(src = url, alt = alt, attrs = {
                classes(Styles.studyStoryImage)
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

@Composable
private fun StudyExampleSentenceText(
    sentence: String,
    word: String,
) {
    P {
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

/**
 * Collects Express prompts from dissected token example sentences.
 * Prefer [StudyExampleSentence.translation] (learner language) as the shown meaning.
 */
internal fun collectExpressPracticeSentences(session: StudySessionResponse): List<StudyExampleSentence> {
    val seen = linkedSetOf<String>()
    val result = mutableListOf<StudyExampleSentence>()
    session.sentences.forEach { sentence ->
        sentence.tokens.forEach { token ->
            token.exampleSentences.forEach { example ->
                val text = example.text.trim()
                val translation = example.translation.trim()
                if (text.isEmpty() && translation.isEmpty()) return@forEach
                // Prompt must be in the learner language when possible.
                val meaning = translation.ifEmpty { text }
                val reference = text
                val key = "${meaning.lowercase()}|${reference.lowercase()}"
                if (!seen.add(key)) return@forEach
                result += StudyExampleSentence(text = reference, translation = meaning)
            }
        }
    }
    return result
}

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
) {
    val scope = rememberCoroutineScope()
    val languageTag = Localization.language.tag
    val sessionExamples = remember(session) { collectExpressPracticeSentences(session) }
    val vocabulary = remember(session) { collectExpressVocabulary(session) }

    var queue by remember(session.text) { mutableStateOf(sessionExamples) }
    var index by remember(session.text) { mutableStateOf(0) }
    var draft by remember(session.text, index) { mutableStateOf("") }
    var checking by remember(session.text, index) { mutableStateOf(false) }
    var feedback by remember(session.text, index) { mutableStateOf<String?>(null) }
    var justCorrect by remember(session.text, index) { mutableStateOf(false) }
    var generating by remember(session.text) { mutableStateOf(false) }
    var generateError by remember(session.text) { mutableStateOf<String?>(null) }
    var seededFromSession by remember(session.text) { mutableStateOf(false) }

    val meaningsStillLoading = anyLoading || session.sentences.any { sentence ->
        sentence.tokens.any { it.status == StudyTokenStatus.PENDING }
    }

    // Merge newly dissected examples as they arrive (without resetting progress).
    LaunchedEffect(sessionExamples) {
        if (sessionExamples.isEmpty()) return@LaunchedEffect
        if (!seededFromSession && queue.isEmpty()) {
            queue = sessionExamples
            index = 0
            seededFromSession = true
            return@LaunchedEffect
        }
        val existingKeys = queue.map {
            "${it.translation.trim().lowercase()}|${it.text.trim().lowercase()}"
        }.toMutableSet()
        val additions = sessionExamples.filter { example ->
            val key = "${example.translation.trim().lowercase()}|${example.text.trim().lowercase()}"
            existingKeys.add(key)
        }
        if (additions.isNotEmpty()) {
            queue = queue + additions
            seededFromSession = true
        }
    }

    fun generateMore(resumeAt: Int) {
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
                    queue = queue + fresh
                    index = resumeAt.coerceIn(0, queue.lastIndex)
                    justCorrect = false
                    feedback = null
                    draft = ""
                } else {
                    generateError = strings.chatExpressFeedbackError
                }
            }.onFailure {
                generateError = strings.chatExpressFeedbackError
            }
            generating = false
        }
    }

    fun advanceAfterCorrect() {
        justCorrect = false
        feedback = null
        draft = ""
        val next = index + 1
        if (next < queue.size) {
            index = next
        } else {
            // Exhausted local queue — request a fresh batch and resume at the first new item.
            generateMore(resumeAt = queue.size)
        }
    }

    // When nothing is available and meanings are done, generate the first endless batch.
    LaunchedEffect(session.text, queue.size, meaningsStillLoading, generating, generateError) {
        if (queue.isEmpty() && !meaningsStillLoading && !generating && generateError == null) {
            generateMore(resumeAt = 0)
        }
    }

    fun check(value: String?) {
        val candidate = value?.trim().orEmpty()
        val currentItem = queue.getOrNull(index) ?: return
        if (candidate.isEmpty() || checking || generating || justCorrect) return
        checking = true
        feedback = null
        draft = candidate
        scope.launch {
            runCatching {
                ChatApi.studyExpressCheck(
                    StudyExpressCheckRequest(
                        meaning = currentItem.translation.ifBlank { currentItem.text },
                        attempt = candidate,
                        textLanguageTag = session.textLanguageTag,
                        languageTag = languageTag,
                        referenceText = currentItem.text,
                    ),
                )
            }.onSuccess { response ->
                if (response.correct) {
                    justCorrect = true
                    feedback = null
                    // Brief success beat, then advance.
                    delay(650)
                    advanceAfterCorrect()
                } else {
                    justCorrect = false
                    feedback = response.feedback.trim().takeIf { it.isNotEmpty() }
                        ?: strings.chatExpressFeedbackError
                }
            }.onFailure {
                justCorrect = false
                feedback = strings.chatExpressFeedbackError
            }
            checking = false
        }
    }

    Span(attrs = { classes(Styles.studySessionHint) }) { Text(strings.chatExpressHint) }

    val current = queue.getOrNull(index)
    when {
        current != null -> {
            Div(attrs = { classes(Styles.studyWriteBlock) }) {
                // Disabled, not needed because new expressions are auto-generated
                if (false && queue.isNotEmpty()) {
                    Span(attrs = { classes(Styles.studySessionHint) }) {
                        Text("${(index + 1).coerceAtMost(queue.size)} / ${queue.size}")
                    }
                }
                P(attrs = { classes(Styles.studyWritePrompt) }) {
                    Text(current.translation.ifBlank { current.text })
                }
                if (justCorrect) {
                    Div(attrs = {
                        classes(Styles.studyWriteCorrect)
                        attr("role", "status")
                    }) {
                        MaterialSymbol("check_circle")
                        Span { Text(strings.chatExpressCorrect) }
                    }
                } else {
                    ChatInput(
                        value = draft,
                        onValueChange = {
                            draft = it
                            if (feedback != null) feedback = null
                        },
                        onSend = { check(it) },
                        placeholder = strings.chatExpressPlaceholder,
                        languageTag = session.textLanguageTag,
                        multiline = (current.translation.ifBlank { current.text }).length > 48,
                        autoFocus = autoFocus,
                    )
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
        generating || meaningsStillLoading -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(
                    when {
                        generating -> strings.chatExpressGenerating
                        else -> strings.chatExpressLoading
                    },
                )
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
                        generateMore(resumeAt = queue.size)
                    }
                }) { Text(strings.chatPlayRetry) }
            }
        }
        else -> {
            Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                MaterialSymbol("progress_activity")
                Text(strings.chatExpressEmpty)
            }
        }
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

private const val SPEECH_BATCH_SIZE = 3
private const val SPEECH_RETRY_MAX_ATTEMPTS = 4
private const val SPEECH_RETRY_INITIAL_DELAY_MS = 2_500L
/** Concurrent scene generations per study session (server handles provider load). */
private const val STUDY_IMAGE_PARALLELISM = 4
/** ~6 minutes: reference-to-image + optional text-only fallback can exceed 2 minutes. */
private const val STUDY_IMAGE_POLL_MAX_ATTEMPTS = 180
private const val STUDY_IMAGE_POLL_DELAY_MS = 1_500L
/** Consecutive missing job polls before treating the imageId as lost. */
private const val STUDY_IMAGE_MISSING_JOB_LIMIT = 5
/**
 * How long to wait for the main character portrait before starting scene panels.
 * Scenes can proceed with description-only cast if portraits are slow.
 */
private const val STUDY_MAIN_PORTRAIT_WAIT_ATTEMPTS = 40

/** True when scenes can start: cast exists and main portrait is ready (or no main portrait job). */
private fun studyCastReadyForScenes(characters: List<StudyStoryCharacter>): Boolean {
    if (characters.isEmpty()) return false
    val main = characters.firstOrNull {
        it.role.equals("main", ignoreCase = true) || it.id.equals("main", ignoreCase = true)
    } ?: characters.first()
    if (!main.imageUrl.isNullOrBlank()) return true
    // No portrait job enqueued — proceed with description-only.
    return main.imageId.isNullOrBlank()
}

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

@Composable
private fun StudyTokenDetail(
    token: StudyToken,
    textLanguageTag: String,
    strings: UiStrings,
    imageFailed: Boolean = false,
) {
    Div(attrs = { classes(Styles.wordDefinition, Styles.studyTokenDetail) }) {
        // Word visual sits at the top of the dissection panel.
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
                }
            }
        }
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
                if (token.exampleSentences.isNotEmpty()) {
                    token.exampleSentences.forEach { example ->
                        Div(attrs = { classes(Styles.studyExampleSentence) }) {
                            StudyExampleSentenceText(
                                sentence = example.text,
                                word = token.text,
                            )
                            if (example.translation.isNotBlank()) {
                                P(attrs = { classes(Styles.studyExampleTranslation) }) {
                                    Text(example.translation)
                                }
                            }
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

private enum class StudySentenceLoadState {
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

private const val STUDY_DISSECT_PARALLELISM = 3

/** Small pad so clipped words are not cut off mid-phoneme. */
private const val WORD_AUDIO_END_PAD_MS = 40

private fun sentenceNeedsDissect(sentence: StudySentence): Boolean =
    sentence.tokens.any { it.status == StudyTokenStatus.PENDING }

private var activeStudyAudio: HTMLAudioElement? = null
private var activeStudyAudioUrl: String? = null

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
    element.asDynamic().onended = null
    element.asDynamic().onpause = null
    element.asDynamic().onplay = null
}

private fun stopActiveStudyAudio(clearPlaylist: Boolean = true) {
    activeStudyAudio?.let { current ->
        clearStudyAudioHandlers(current)
        runCatching { current.pause() }
    }
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
    onEnded: (() -> Unit)? = null,
) {
    element.asDynamic().onplay = {
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = url
        }
    }
    element.asDynamic().onpause = {
        if (activeStudyAudio === element) {
            studyAudioPlayingUrl = null
        }
    }
    element.asDynamic().onended = {
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

private fun probeAudioDurationMs(url: String, onResult: (Int) -> Unit) {
    val element = document.createElement("audio") as HTMLAudioElement
    element.preload = "metadata"
    element.src = url
    element.asDynamic().onloadedmetadata = {
        val duration = element.duration
        if (duration.isFinite() && duration > 0) {
            onResult((duration * 1000.0).toInt().coerceAtLeast(1))
        }
    }
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
        (document.createElement("audio") as HTMLAudioElement).also { created ->
            activeStudyAudio = created
            activeStudyAudioUrl = url
            created.src = url
        }
    }
    clearStudyAudioHandlers(element)
    if (play) {
        studyAudioPlayingUrl = url
    }
    bindStudyAudioPlaybackState(element, url, onEnded)
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

internal fun playAudio(url: String, restart: Boolean = false) {
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
    val element = document.createElement("audio") as HTMLAudioElement
    activeStudyAudio = element
    activeStudyAudioUrl = url
    studyAudioPlayingUrl = url
    bindStudyAudioPlaybackState(element, url)
    element.src = url
    element.play()
}

/**
 * Plays sentence clips back-to-back (used by the Read tab full-text control).
 * Tapping again while the playlist is playing stops playback.
 */
private fun playAudioSequence(urls: List<String>) {
    if (urls.isEmpty()) return
    val current = activeStudyAudio
    if (
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
    val element = document.createElement("audio") as HTMLAudioElement
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
    element.src = url
    element.play()
}

/**
 * Plays a slice of existing sentence audio using TTS word timings (seconds).
 * Avoids a second synthesis call when the provider returned usable timings.
 */
private fun playAudioSegment(url: String, beginMs: Int, endMs: Int) {
    stopActiveStudyAudio()
    val startSec = (beginMs.coerceAtLeast(0)) / 1000.0
    val endSec = (endMs.coerceAtLeast(beginMs + 60) + WORD_AUDIO_END_PAD_MS) / 1000.0
    val element = document.createElement("audio") as HTMLAudioElement
    activeStudyAudio = element
    activeStudyAudioUrl = url
    studyAudioPlayingUrl = url
    bindStudyAudioPlaybackState(element, url)
    element.src = url
    element.asDynamic().onloadedmetadata = {
        // Clamp if metadata duration is shorter than requested end.
        val duration = element.duration
        val safeStart = if (duration.isFinite() && duration > 0) startSec.coerceAtMost(duration) else startSec
        element.currentTime = safeStart
        element.play()
    }
    element.asDynamic().ontimeupdate = {
        val duration = element.duration
        val stopAt = if (duration.isFinite() && duration > 0) endSec.coerceAtMost(duration) else endSec
        if (element.currentTime >= stopAt) {
            element.pause()
            element.asDynamic().ontimeupdate = null
        }
    }
    // Some browsers fire loadedmetadata before the handler is attached for cached data URLs.
    if (element.readyState >= 1) {
        val duration = element.duration
        val safeStart = if (duration.isFinite() && duration > 0) startSec.coerceAtMost(duration) else startSec
        element.currentTime = safeStart
        element.play()
    }
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
