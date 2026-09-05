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
import apap.oneverandah.ChatInitialPanel
import apap.oneverandah.Styles
import apap.oneverandah.api.ChatApi
import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SaveChatMessageRequest
import apap.oneverandah.api.SavedChatMessage
import apap.oneverandah.api.SpeechRequest
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
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.math.abs

@Composable
fun ChatScreen(
    initialMessage: String?,
    onInitialMessageConsumed: () -> Unit,
    initialPanel: ChatInitialPanel? = null,
    onInitialPanelConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenPhrases: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenStudy: (String?) -> Unit = {},
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

    fun applySavedItems(items: List<SavedChatMessage>) {
        savedItems = items
        savedBySourceId.clear()
        items.forEach { item ->
            val key = item.sourceMessageId ?: item.id
            savedBySourceId[key] = item.id
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

    fun openSavedPanel() {
        savedPanelOpen = true
        refreshSaved()
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
                ChatApi.send(ChatRequest(conversationId, text, language.tag)) { partial ->
                    conversationId = partial.conversationId
                    val existing = messages.indexOfFirst { it.id == partial.message.id }
                    if (existing >= 0) {
                        messages[existing] = partial.message
                    } else {
                        messages += partial.message
                    }
                }
            }.onSuccess { response ->
                conversationId = response.conversationId
                val existing = messages.indexOfFirst { it.id == response.message.id }
                if (existing >= 0) {
                    messages[existing] = response.message
                } else {
                    messages += response.message
                }
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

    LaunchedEffect(Unit) {
        refreshSaved(silent = true)
    }

    LaunchedEffect(initialMessage) {
        initialMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            onInitialMessageConsumed()
            sendMessage(message)
        }
    }

    LaunchedEffect(initialPanel) {
        when (initialPanel) {
            ChatInitialPanel.Saved -> {
                onInitialPanelConsumed()
                openSavedPanel()
            }
            null -> Unit
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
            SavedMessagesPanel(
                visible = savedPanelOpen,
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
                    onOpenStudy = { onOpenStudy(null) },
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
                    onStudy = {
                        val text = studyTextFromChatMessage(message)
                        if (text.isNotEmpty()) onOpenStudy(text)
                    },
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
    onOpenStudy: () -> Unit,
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
                onClick { onOpenStudy() }
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
private fun SavedMessagesPanel(
    visible: Boolean,
    items: List<SavedChatMessage>,
    loading: Boolean,
    strings: UiStrings,
    onClose: () -> Unit,
    onSend: (SavedChatMessage) -> Unit,
    onEdit: (SavedChatMessage) -> Unit,
    onRemove: (SavedChatMessage) -> Unit,
    onResidentSelected: (ResidentCardMiniApp) -> Unit,
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onClose,
        expanded = true,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.chatSavedTitle) }
        }
        Div(attrs = { classes(Styles.dialogBody, Styles.savedPanelBody) }) {
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

