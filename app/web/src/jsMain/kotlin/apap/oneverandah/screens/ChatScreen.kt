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
import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.components.ChatInput
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.ProviderDetailsDialog
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.model.ServiceProvider
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
import org.w3c.dom.HTMLAudioElement
import kotlin.js.Date

@Composable
fun ChatScreen(
    initialMessage: String?,
    onInitialMessageConsumed: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
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

    LaunchedEffect(initialMessage) {
        initialMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            onInitialMessageConsumed()
            sendMessage(message)
        }
    }

    LaunchedEffect(latestAiMessageId) {
        latestAiMessageId?.let { messageId ->
            document.getElementById(chatMessageElementId(messageId))
                ?.asDynamic()
                ?.scrollIntoView(js("({ behavior: 'smooth', block: 'start' })"))
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
        onHome = onHome,
        footer = {
            ChatInput(
                value = draft,
                onValueChange = { draft = it },
                onSend = { it?.let(::sendMessage) },
                placeholder = strings.chatPlaceholder,
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
        },
    ) {
        Div(attrs = { classes(Styles.aiChat) }) {
            if (messages.isEmpty() && !sending) {
                ChatEmptyState(strings.chatSuggestions, ::sendMessage)
            }
            messages.forEach { message ->
                ChatMessageView(
                    message = message,
                    strings = strings,
                    showFollowUpQuestions = !sending && message.id == latestAiMessageId,
                    onFollowUpQuestion = ::sendMessage,
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
private fun ChatEmptyState(suggestions: List<String>, onSuggestion: (String) -> Unit) {
    Div(attrs = { classes(Styles.chatEmptyState) }) {
        Div(attrs = { classes(Styles.chatEmptyIcon) }) {
            MaterialSymbol("auto_awesome")
        }
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
private fun ChatMessageView(
    message: ChatMessage,
    strings: UiStrings,
    showFollowUpQuestions: Boolean,
    onFollowUpQuestion: (String) -> Unit,
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
        Button(attrs = {
            classes(Styles.sentencePlay)
            attr("type", "button")
            if (audio["sentence"] == null) attr("disabled", "")
            onClick { audio["sentence"]?.let(::playAudio) }
        }) {
            MaterialSymbol("play_circle")
            Text(strings.chatPlaySentence)
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

private fun playAudio(url: String) {
    (document.createElement("audio") as HTMLAudioElement).apply {
        src = url
        play()
    }
}
