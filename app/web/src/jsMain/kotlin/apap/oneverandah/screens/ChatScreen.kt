package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChatApi
import apap.oneverandah.api.ChatApiException
import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SaveChatMessageRequest
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SavedChatMessage
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechTimingAligner
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.ChatInput
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.ProviderDetailsDialog
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.model.ServiceProvider
import apap.oneverandah.state.Toaster
import kotlinx.browser.document
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.math.abs

@Composable
fun ChatScreen(
    initialMessage: String?,
    onInitialMessageConsumed: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenPhrases: () -> Unit = {},
    onOpenStories: () -> Unit = {},
) {
    val strings = Localization.strings
    val language = Localization.language
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedProvider by remember { mutableStateOf<ServiceProvider?>(null) }
    var showProviderDetails by remember { mutableStateOf(false) }
    var latestAiMessageId by remember { mutableStateOf<String?>(null) }
    var savedPanelOpen by remember { mutableStateOf(false) }
    var savedItems by remember { mutableStateOf<List<SavedChatMessage>>(emptyList()) }
    var savedLoading by remember { mutableStateOf(false) }
    // sourceMessageId (or saved id fallback) -> saved row id
    val savedBySourceId = remember { mutableStateMapOf<String, String>() }
    var studyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var studySessionVisible by remember { mutableStateOf(false) }
    var studyLoading by remember { mutableStateOf(false) }
    var studyError by remember { mutableStateOf<String?>(null) }
    var studySession by remember { mutableStateOf<StudySessionResponse?>(null) }
    val studySentenceStatus = remember { mutableStateMapOf<Int, StudySentenceLoadState>() }
    val studyCache = remember { mutableStateMapOf<String, StudySessionResponse>() }
    // sourceMessageId (or session id fallback) -> persisted study row id
    val studyPersistedBySourceId = remember { mutableStateMapOf<String, String>() }
    var studySessionsPanelOpen by remember { mutableStateOf(false) }
    var studySessionItems by remember { mutableStateOf<List<SavedStudySession>>(emptyList()) }
    var studySessionsLoading by remember { mutableStateOf(false) }

    fun applySavedItems(items: List<SavedChatMessage>) {
        savedItems = items
        savedBySourceId.clear()
        items.forEach { item ->
            val key = item.sourceMessageId ?: item.id
            savedBySourceId[key] = item.id
        }
    }

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

    fun refreshSaved(silent: Boolean = false) {
        scope.launch {
            if (!silent) savedLoading = true
            runCatching { ChatApi.listSaved() }
                .onSuccess { applySavedItems(it.items) }
                .onFailure {
                    if (!silent) Toaster.show(strings.chatSavedError)
                }
            savedLoading = false
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

    fun openSavedPanel() {
        savedPanelOpen = true
        refreshSaved()
    }

    fun openStudySessionsPanel() {
        studySessionsPanelOpen = true
        refreshStudySessions()
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
            }
        }
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || sending) return
        draft = ""
        error = null
        sending = true
        latestAiMessageId = null
        messages += ChatMessage(
            id = "client-${Date.now()}",
            sender = ChatSender.YOU,
            text = text,
            createdAt = Date.now().toLong(),
        )
        scope.launch {
            runCatching {
                ChatApi.send(ChatRequest(conversationId, text, language.tag))
            }.onSuccess { response ->
                conversationId = response.conversationId
                messages += response.message
                latestAiMessageId = response.message.id
            }.onFailure {
                error = strings.chatError
            }
            sending = false
        }
    }

    fun startFreshConversation(text: String, autoSend: Boolean) {
        conversationId = null
        messages.clear()
        latestAiMessageId = null
        error = null
        if (autoSend) {
            sendMessage(text)
        } else {
            draft = text
        }
    }

    fun toggleSave(message: ChatMessage) {
        val existingId = savedBySourceId[message.id]
        scope.launch {
            if (existingId != null) {
                runCatching { ChatApi.deleteSaved(existingId) }
                    .onSuccess {
                        savedBySourceId.remove(message.id)
                        savedItems = savedItems.filterNot { it.id == existingId }
                        Toaster.show(strings.removedToast)
                    }
                    .onFailure { Toaster.show(strings.chatSavedError) }
            } else {
                runCatching {
                    ChatApi.saveMessage(
                        SaveChatMessageRequest(
                            sourceMessageId = message.id,
                            sender = message.sender,
                            text = message.text,
                            createdAt = message.createdAt,
                            miniApp = message.miniApp,
                            followUpQuestions = message.followUpQuestions,
                        ),
                    )
                }.onSuccess { saved ->
                    savedBySourceId[message.id] = saved.id
                    savedItems = (listOf(saved) + savedItems.filterNot { it.id == saved.id })
                        .sortedByDescending { it.savedAt }
                    Toaster.show(strings.savedToast)
                }.onFailure {
                    Toaster.show(strings.chatSavedError)
                }
            }
        }
    }

    fun removeSaved(item: SavedChatMessage) {
        scope.launch {
            runCatching { ChatApi.deleteSaved(item.id) }
                .onSuccess {
                    savedItems = savedItems.filterNot { it.id == item.id }
                    val keysToRemove = savedBySourceId.filterValues { it == item.id }.keys.toList()
                    keysToRemove.forEach { savedBySourceId.remove(it) }
                    Toaster.show(strings.removedToast)
                }
                .onFailure { Toaster.show(strings.chatSavedError) }
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
            val updatedSentences = current.sentences.toMutableList()
            updatedSentences[sentenceIndex] = StudySentence(
                text = response.sentenceText.ifBlank { sentence.text },
                tokens = response.tokens,
            )
            val updated = current.copy(sentences = updatedSentences)
            studyCache[messageId] = updated
            persistStudySession(messageId, updated)
            if (studyTarget?.id == messageId) {
                studySession = updated
                studySentenceStatus[sentenceIndex] = StudySentenceLoadState.READY
            }
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
        if (pendingIndices.isEmpty()) return
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
        }
    }

    fun openStudy(message: ChatMessage) {
        val text = message.text.trim()
        if (text.isEmpty()) return
        studyTarget = message
        studySessionVisible = true
        studyError = null
        val cached = studyCache[message.id]
        if (cached != null) {
            studySession = cached
            studyLoading = false
            syncStudySentenceStatuses(cached)
            loadMissingStudyDissections(message.id, cached)
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
                }
            }.onFailure {
                if (studyTarget?.id == message.id) {
                    studyError = strings.chatStudyError
                    studyLoading = false
                }
            }
        }
    }

    fun openSavedStudySession(item: SavedStudySession) {
        studySessionsPanelOpen = false
        val key = item.sourceMessageId ?: item.id
        // Meanings are in the learner language stored with the session; regenerate if UI language changed.
        if (item.languageTag == language.tag) {
            studyCache[key] = item.session
        } else {
            studyCache.remove(key)
        }
        studyPersistedBySourceId[key] = item.id
        openStudy(
            ChatMessage(
                id = key,
                sender = ChatSender.AI,
                text = item.session.text,
                createdAt = item.createdAt,
            ),
        )
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

    fun closeStudy() {
        studySessionVisible = false
    }

    LaunchedEffect(Unit) {
        refreshSaved(silent = true)
        refreshStudySessions(silent = true)
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

    LaunchedEffect(initialMessage) {
        initialMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            onInitialMessageConsumed()
            sendMessage(message)
        }
    }

    LaunchedEffect(latestAiMessageId) {
        val messageId = latestAiMessageId ?: return@LaunchedEffect
        // Wait a frame so the new message is in the DOM and laid out before measuring.
        repeat(5) {
            if (scrollChatMessageIntoView(messageId)) return@LaunchedEffect
            delay(16)
        }
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider != null && !showProviderDetails) {
            delay(16)
            showProviderDetails = true
        }
    }

    Scaffold(
        title = strings.chatTitle,
        onBack = onBack,
        showChatAction = false,
        showPhrasesAction = false,
        onHome = onHome,
        appBarAction = {
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.chatSavedTitle)
                onClick { openSavedPanel() }
            }) { Text("bookmarks") }
        },
        footer = {
            ChatInput(
                value = draft,
                onValueChange = { draft = it },
                onSend = { it?.let(::sendMessage) },
                placeholder = "Hold to speak...",
                autoFocus = initialMessage == null,
                multiline = true,
            )
        },
        overlay = {
            selectedProvider?.let { provider ->
                ProviderDetailsDialog(
                    visible = showProviderDetails,
                    provider = provider,
                    language = language,
                    strings = strings,
                    onDismiss = { showProviderDetails = false },
                )
            }
            if (savedPanelOpen) {
                SavedMessagesPanel(
                    items = savedItems,
                    loading = savedLoading,
                    strings = strings,
                    onClose = { savedPanelOpen = false },
                    onSend = { item ->
                        savedPanelOpen = false
                        startFreshConversation(item.text, autoSend = true)
                    },
                    onEdit = { item ->
                        savedPanelOpen = false
                        startFreshConversation(item.text, autoSend = false)
                    },
                    onRemove = ::removeSaved,
                    onResidentSelected = { card ->
                        val provider = Catalog.services.asSequence()
                            .map { it.provider }
                            .firstOrNull { it.id == card.residentId }
                        if (provider != null) {
                            savedPanelOpen = false
                            if (provider == selectedProvider) {
                                showProviderDetails = true
                            } else {
                                showProviderDetails = false
                                selectedProvider = provider
                            }
                        }
                    },
                )
            }
            if (studySessionsPanelOpen) {
                StudySessionsPanel(
                    items = studySessionItems,
                    loading = studySessionsLoading,
                    strings = strings,
                    onClose = { studySessionsPanelOpen = false },
                    onOpen = ::openSavedStudySession,
                    onRemove = ::removeStudySession,
                )
            }
            StudySessionDialog(
                visible = studySessionVisible,
                loading = studyLoading,
                error = studyError,
                session = studySession,
                sentenceStatus = studySentenceStatus.toMap(),
                strings = strings,
                onDismiss = ::closeStudy,
                onRetry = { studyTarget?.let(::openStudy) },
                onRetrySentence = ::retryStudySentence,
            )
        },
    ) {
        Div(attrs = { classes(Styles.aiChat) }) {
            if (messages.isEmpty() && !sending) {
                ChatEmptyState(
                    suggestions = strings.chatSuggestions,
                    onSuggestion = ::sendMessage,
                    onOpenPhrases = onOpenPhrases,
                    onOpenStories = onOpenStories,
                    onOpenSaved = ::openSavedPanel,
                    onOpenStudySessions = ::openStudySessionsPanel,
                )
            }
            messages.forEach { message ->
                ChatMessageView(
                    message = message,
                    strings = strings,
                    saved = savedBySourceId.containsKey(message.id),
                    showFollowUpQuestions = !sending && message.id == latestAiMessageId,
                    onFollowUpQuestion = ::sendMessage,
                    onToggleSave = { toggleSave(message) },
                    onStudy = { openStudy(message) },
                    onResidentSelected = { card ->
                        val provider = Catalog.services.asSequence()
                            .map { it.provider }
                            .firstOrNull { it.id == card.residentId }
                        if (provider == selectedProvider) {
                            showProviderDetails = true
                        } else {
                            showProviderDetails = false
                            selectedProvider = provider
                        }
                    },
                )
            }
            if (sending) {
                Div(attrs = { classes(Styles.chatStatus) }) {
                    MaterialSymbol("progress_activity")
                    Text(strings.chatThinking)
                }
            }
            error?.let {
                Div(attrs = { classes(Styles.chatError) }) {
                    MaterialSymbol("error")
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    onOpenPhrases: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenStudySessions: () -> Unit,
) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.chatEmptyState) }) {
        Div(attrs = { classes(Styles.chatEmptyIcon) }) {
            MaterialSymbol("auto_awesome")
        }
        P(attrs = { classes(Styles.chatEmptyLead) }) {
            Text(strings.chatEmptyLead)
        }

        Div(attrs = { classes(Styles.chatEmptyActions) }) {
            Button(attrs = {
                classes(Styles.chatExploreCard)
                attr("type", "button")
                onClick { onOpenStudySessions() }
            }) {
                MaterialSymbol("menu_book")
                Div {
                    Span(attrs = { classes(Styles.chatExploreTitle) }) { Text(strings.chatExploreStudy) }
                    Span(attrs = { classes(Styles.chatExploreSub) }) { Text(strings.chatExploreStudySub) }
                }
                MaterialSymbol("north_east")
            }
            Button(attrs = {
                classes(Styles.chatExploreCard)
                attr("type", "button")
                onClick { onOpenSaved() }
            }) {
                MaterialSymbol("bookmarks")
                Div {
                    Span(attrs = { classes(Styles.chatExploreTitle) }) { Text(strings.chatExploreSaved) }
                    Span(attrs = { classes(Styles.chatExploreSub) }) { Text(strings.chatExploreSavedSub) }
                }
                MaterialSymbol("north_east")
            }
            Button(attrs = {
                classes(Styles.chatExploreCard)
                attr("type", "button")
                onClick { onOpenPhrases() }
            }) {
                MaterialSymbol("import_contacts")
                Div {
                    Span(attrs = { classes(Styles.chatExploreTitle) }) { Text(strings.chatExplorePhrases) }
                    Span(attrs = { classes(Styles.chatExploreSub) }) { Text(strings.chatExplorePhrasesSub) }
                }
                MaterialSymbol("north_east")
            }
            Button(attrs = {
                classes(Styles.chatExploreCard)
                attr("type", "button")
                onClick { onOpenStories() }
            }) {
                MaterialSymbol("auto_stories")
                Div {
                    Span(attrs = { classes(Styles.chatExploreTitle) }) { Text(strings.chatExploreStories) }
                    Span(attrs = { classes(Styles.chatExploreSub) }) { Text(strings.chatExploreStoriesSub) }
                }
                MaterialSymbol("north_east")
            }
        }

        Span(attrs = { classes(Styles.chatEmptyOr) }) { Text(strings.chatEmptyOrAsk) }

        suggestions.forEach { suggestion ->
            Button(attrs = {
                classes(Styles.chatSuggestion)
                attr("type", "button")
                onClick { onSuggestion(suggestion) }
            }) {
                Text(suggestion)
                MaterialSymbol("north_east")
            }
        }
    }
}

@Composable
private fun StudySessionsPanel(
    items: List<SavedStudySession>,
    loading: Boolean,
    strings: UiStrings,
    onClose: () -> Unit,
    onOpen: (SavedStudySession) -> Unit,
    onRemove: (SavedStudySession) -> Unit,
) {
    Div(attrs = {
        classes(Styles.savedPanelScrim)
        onClick { onClose() }
    })
    Div(attrs = {
        classes(Styles.savedPanel)
        attr("role", "dialog")
        attr("aria-label", strings.chatStudySessionsTitle)
    }) {
        Div(attrs = { classes(Styles.savedPanelHeader) }) {
            Span(attrs = { classes(Styles.savedPanelTitle) }) { Text(strings.chatStudySessionsTitle) }
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.close)
                onClick { onClose() }
            }) { Text("close") }
        }
        Div(attrs = { classes(Styles.savedPanelBody) }) {
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
                    Div(attrs = { classes(Styles.savedItem) }) {
                        Span(attrs = { classes(Styles.savedItemSender) }) {
                            Text(strings.chatStudyTitle)
                        }
                        P(attrs = { classes(Styles.savedItemText) }) { Text(item.text) }
                        Div(attrs = { classes(Styles.savedItemActions) }) {
                            Button(attrs = {
                                classes(Styles.savedItemAction)
                                attr("type", "button")
                                onClick { onOpen(item) }
                            }) {
                                MaterialSymbol("menu_book")
                                Text(strings.chatStudySessionsOpen)
                            }
                            Button(attrs = {
                                classes(Styles.savedItemAction, Styles.savedItemActionDanger)
                                attr("type", "button")
                                onClick { onRemove(item) }
                            }) {
                                MaterialSymbol("delete")
                                Text(strings.chatStudySessionsRemove)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMessagesPanel(
    items: List<SavedChatMessage>,
    loading: Boolean,
    strings: UiStrings,
    onClose: () -> Unit,
    onSend: (SavedChatMessage) -> Unit,
    onEdit: (SavedChatMessage) -> Unit,
    onRemove: (SavedChatMessage) -> Unit,
    onResidentSelected: (ResidentCardMiniApp) -> Unit,
) {
    Div(attrs = {
        classes(Styles.savedPanelScrim)
        onClick { onClose() }
    })
    Div(attrs = {
        classes(Styles.savedPanel)
        attr("role", "dialog")
        attr("aria-label", strings.chatSavedTitle)
    }) {
        Div(attrs = { classes(Styles.savedPanelHeader) }) {
            Span(attrs = { classes(Styles.savedPanelTitle) }) { Text(strings.chatSavedTitle) }
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.close)
                onClick { onClose() }
            }) { Text("close") }
        }
        Div(attrs = { classes(Styles.savedPanelBody) }) {
            when {
                loading && items.isEmpty() -> {
                    Div(attrs = { classes(Styles.chatStatus) }) {
                        MaterialSymbol("progress_activity")
                        Text(strings.chatThinking)
                    }
                }
                items.isEmpty() -> {
                    Div(attrs = { classes(Styles.savedPanelEmpty) }) {
                        MaterialSymbol("bookmark_border")
                        P { Text(strings.chatSavedEmpty) }
                    }
                }
                else -> items.forEach { item ->
                    Div(attrs = { classes(Styles.savedItem) }) {
                        Span(attrs = { classes(Styles.savedItemSender) }) {
                            Text(
                                if (item.sender == ChatSender.YOU) strings.chatYouSender
                                else strings.chatAiSender,
                            )
                        }
                        P(attrs = { classes(Styles.savedItemText) }) { Text(item.text) }
                        when (val miniApp = item.miniApp) {
                            is LanguageStudyMiniApp -> LanguageStudyCard(miniApp, strings)
                            is ResidentCardMiniApp -> ResidentCard(miniApp, strings) {
                                onResidentSelected(miniApp)
                            }
                            is FactMiniApp -> FactCard(miniApp)
                            null -> Unit
                        }
                        Div(attrs = { classes(Styles.savedItemActions) }) {
                            Button(attrs = {
                                classes(Styles.savedItemAction)
                                attr("type", "button")
                                onClick { onSend(item) }
                            }) {
                                MaterialSymbol("send")
                                Text(strings.chatSavedSend)
                            }
                            Button(attrs = {
                                classes(Styles.savedItemAction)
                                attr("type", "button")
                                onClick { onEdit(item) }
                            }) {
                                MaterialSymbol("edit")
                                Text(strings.chatSavedEdit)
                            }
                            Button(attrs = {
                                classes(Styles.savedItemAction, Styles.savedItemActionDanger)
                                attr("type", "button")
                                onClick { onRemove(item) }
                            }) {
                                MaterialSymbol("delete")
                                Text(strings.chatSavedRemove)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageView(
    message: ChatMessage,
    strings: UiStrings,
    saved: Boolean,
    showFollowUpQuestions: Boolean,
    onFollowUpQuestion: (String) -> Unit,
    onToggleSave: () -> Unit,
    onStudy: () -> Unit,
    onResidentSelected: (ResidentCardMiniApp) -> Unit,
) {
    val fromUser = message.sender == ChatSender.YOU
    Div(attrs = {
        classes(Styles.chatMessageRow)
        if (fromUser) classes(Styles.chatMessageRowYou)
        if (!fromUser) id(chatMessageElementId(message.id))
    }) {
        Div(attrs = { classes(Styles.chatMessageMeta) }) {
            Text(if (fromUser) strings.chatYouSender else strings.chatAiSender)
            Button(attrs = {
                classes(Styles.chatBookmarkBtn)
                attr("type", "button")
                attr("aria-label", strings.chatStudyMessage)
                onClick { onStudy() }
            }) {
                Span(attrs = { classes(Styles.materialSymbols) }) { Text("menu_book") }
            }
            Button(attrs = {
                classes(Styles.chatBookmarkBtn)
                attr("type", "button")
                attr("aria-label", if (saved) strings.chatUnsaveMessage else strings.chatSaveMessage)
                attr("aria-pressed", saved.toString())
                onClick { onToggleSave() }
            }) {
                Span(attrs = {
                    classes(Styles.materialSymbols)
                    if (saved) style { property("font-variation-settings", "'FILL' 1") }
                }) { Text("bookmark") }
            }
        }
        Div(attrs = {
            classes(Styles.chatBubble)
            if (fromUser) classes(Styles.chatBubbleYou)
        }) {
            P { Text(message.text) }
        }
        when (val miniApp = message.miniApp) {
            is LanguageStudyMiniApp -> LanguageStudyCard(miniApp, strings)
            is ResidentCardMiniApp -> ResidentCard(miniApp, strings) { onResidentSelected(miniApp) }
            is FactMiniApp -> FactCard(miniApp)
            null -> Unit
        }
        if (showFollowUpQuestions) {
            FollowUpQuestions(message.followUpQuestions, onFollowUpQuestion)
        }
    }
}

@Composable
private fun FollowUpQuestions(questions: List<String>, onQuestion: (String) -> Unit) {
    if (questions.isEmpty()) return
    Div(attrs = { classes(Styles.chatFollowUps) }) {
        questions.forEach { question ->
            Button(attrs = {
                classes(Styles.chatFollowUp)
                attr("type", "button")
                onClick { onQuestion(question) }
            }) {
                Text(question)
            }
        }
    }
}

private fun chatMessageElementId(messageId: String): String = "chat-message-$messageId"

/**
 * Scrolls an AI message to the top of the scaffold messages pane only.
 *
 * Avoids [org.w3c.dom.Element.scrollIntoView], which walks all scrollable
 * ancestors and can move the whole page/frame. Returns `true` once scrolling
 * was applied or the message is already aligned; `false` if the DOM/layout is
 * not ready yet (caller should retry). Does nothing when the messages pane is
 * not scrollable.
 */
private fun scrollChatMessageIntoView(messageId: String): Boolean {
    val message = document.getElementById(chatMessageElementId(messageId)) as? HTMLElement
        ?: return false
    val container = message.closest(".${Styles.content}") as? HTMLElement
        ?: return false

    // Only scroll the messages view, and only once it is actually scrollable.
    // Returning false lets the caller retry while layout is still settling.
    if (container.scrollHeight <= container.clientHeight + 1) return false

    val delta = message.getBoundingClientRect().top - container.getBoundingClientRect().top
    if (abs(delta) < 1.0) return true

    val targetTop = container.scrollTop + delta
    val options = js("({ behavior: 'smooth', top: 0.0 })")
    options.top = targetTop
    container.asDynamic().scrollTo(options)
    return true
}

@Composable
private fun FactCard(app: FactMiniApp) {
    Div(attrs = { classes(Styles.miniAppCard, Styles.factCard) }) {
        Div(attrs = { classes(Styles.factIcon) }) {
            MaterialSymbol(app.icon)
        }
        Div(attrs = { classes(Styles.factBody) }) {
            Div(attrs = { classes(Styles.factTitle) }) { Text(app.title) }
            P { Text(app.body) }
        }
    }
}

@Composable
private fun LanguageStudyCard(app: LanguageStudyMiniApp, strings: UiStrings) {
    val audio = remember(app) { mutableStateMapOf<String, String>() }
    var selectedToken by remember(app) { mutableStateOf<LanguageStudyToken?>(null) }
    val speechItems = remember(app) {
        listOf("sentence" to app.sentence) + app.tokens.mapIndexed { index, token -> "token-$index" to token.text }
    }

    LaunchedEffect(app) {
        coroutineScope {
            speechItems.map { (key, text) ->
                async {
                    runCatching {
                        key to ChatApi.speechDataUrl(SpeechRequest(text, app.targetLanguageTag))
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().forEach { (key, url) -> audio[key] = url }
        }
    }

    Div(attrs = { classes(Styles.miniAppCard, Styles.languageStudyCard) }) {
        Div(attrs = { classes(Styles.languageSentence) }) {
            app.tokens.forEachIndexed { index, token ->
                Button(attrs = {
                    classes(Styles.languageToken)
                    if (selectedToken == token) classes(Styles.languageTokenSelected)
                    attr("type", "button")
                    onClick {
                        selectedToken = token
                        audio["token-$index"]?.let(::playAudio)
                    }
                }) { Text(token.text) }
            }
        }
        val sentenceUrl = audio["sentence"]
        val sentencePlaying = isStudyAudioPlaying(sentenceUrl)
        Button(attrs = {
            classes(Styles.sentencePlay)
            attr("type", "button")
            if (sentenceUrl == null) attr("disabled", "")
            attr("aria-label", if (sentencePlaying) strings.chatPauseSentence else strings.chatPlaySentence)
            onClick { sentenceUrl?.let(::playAudio) }
        }) {
            MaterialSymbol(if (sentencePlaying) "pause_circle" else "play_circle")
            Text(if (sentencePlaying) strings.chatPauseSentence else strings.chatPlaySentence)
        }
        selectedToken?.let { token ->
            Div(attrs = { classes(Styles.wordDefinition) }) {
                Span(attrs = { classes(Styles.wordDefinitionTitle) }) { Text(token.text) }
                Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatDefinition) }
                P { Text(token.definition) }
                Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatInContext) }
                P { Text(token.contextualMeaning) }
            }
        }
    }
}

@Composable
private fun ResidentCard(app: ResidentCardMiniApp, strings: UiStrings, onClick: () -> Unit) {
    Button(attrs = {
        classes(Styles.miniAppCard, Styles.residentChatCard)
        attr("type", "button")
        onClick { onClick() }
    }) {
        Div(attrs = { classes(Styles.residentChatAvatar) }) { MaterialSymbol(app.photo) }
        Div(attrs = { classes(Styles.residentChatBody) }) {
            Span(attrs = { classes(Styles.residentChatName) }) { Text(app.name) }
            Span { Text(app.address) }
            Span(attrs = { classes(Styles.residentChatAction) }) { Text(strings.chatResidentDetails) }
        }
        MaterialSymbol("chevron_right")
    }
}

@Composable
private fun StudySessionDialog(
    visible: Boolean,
    loading: Boolean,
    error: String?,
    session: StudySessionResponse?,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRetrySentence: (Int) -> Unit,
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.chatStudyTitle) }
        }
        Div(attrs = { classes(Styles.dialogBody, Styles.studySessionBody) }) {
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
                    strings = strings,
                    onRetrySentence = onRetrySentence,
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
}

@Composable
private fun StudySessionContent(
    session: StudySessionResponse,
    sentenceStatus: Map<Int, StudySentenceLoadState>,
    strings: UiStrings,
    onRetrySentence: (Int) -> Unit,
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
    var mode by remember(session.text) { mutableStateOf(StudySessionMode.STUDY) }
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
    }

    when (mode) {
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
                Div(attrs = { classes(Styles.studySentenceBlock) }) {
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
                        StudyTokenDetail(token = token, strings = strings)
                    }
                }
            }
        }
        StudySessionMode.READ -> {
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
    }
}

private enum class StudySessionMode { STUDY, READ }

private const val SPEECH_BATCH_SIZE = 3
private const val SPEECH_RETRY_MAX_ATTEMPTS = 4
private const val SPEECH_RETRY_INITIAL_DELAY_MS = 2_500L

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
private fun StudyTokenDetail(token: StudyToken, strings: UiStrings) {
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
                if (token.exampleSentences.isNotEmpty()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyExamples) }
                    token.exampleSentences.forEach { example ->
                        Div(attrs = { classes(Styles.studyExampleSentence) }) {
                            P { Text(example.text) }
                            if (example.translation.isNotBlank()) {
                                P(attrs = { classes(Styles.studyExampleTranslation) }) {
                                    Text(example.translation)
                                }
                            }
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

private fun isStudyAudioPlaying(url: String?): Boolean =
    url != null && studyAudioPlayingUrl == url

private fun clearStudyAudioHandlers(element: HTMLAudioElement) {
    element.asDynamic().ontimeupdate = null
    element.asDynamic().onloadedmetadata = null
    element.asDynamic().onended = null
    element.asDynamic().onpause = null
    element.asDynamic().onplay = null
}

private fun stopActiveStudyAudio() {
    activeStudyAudio?.let { current ->
        clearStudyAudioHandlers(current)
        runCatching { current.pause() }
    }
    activeStudyAudio = null
    activeStudyAudioUrl = null
    studyAudioPlayingUrl = null
}

private fun bindStudyAudioPlaybackState(element: HTMLAudioElement, url: String) {
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
        }
    }
}

private fun playAudio(url: String) {
    val current = activeStudyAudio
    // Toggle: tapping the same sentence again pauses instead of restarting.
    if (current != null && activeStudyAudioUrl == url && !current.paused && !current.ended) {
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
