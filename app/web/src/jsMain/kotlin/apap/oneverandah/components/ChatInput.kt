package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.i18n.Localization
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.Element
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

private const val chatInputMinHeightPx = 56
private const val chatInputMaxHeightPx = 104

internal fun chatInputHeightPx(scrollHeight: Int): Int =
    scrollHeight.coerceIn(chatInputMinHeightPx, chatInputMaxHeightPx)

private fun resizeChatInput(textArea: HTMLTextAreaElement) {
    textArea.style.height = "auto"
    textArea.style.height = "${chatInputHeightPx(textArea.scrollHeight)}px"
}

internal fun installHoldListeners(element: Element, gesture: HoldToSpeakGesture): () -> Unit {
    val press: (Event) -> Unit = { gesture.press() }
    val release: (Event) -> Unit = { gesture.release() }
    val cancel: (Event) -> Unit = { gesture.cancel() }
    element.addEventListener("pointerdown", press)
    window.addEventListener("pointerup", release)
    window.addEventListener("pointercancel", cancel)
    window.addEventListener("blur", cancel)
    return {
        element.removeEventListener("pointerdown", press)
        window.removeEventListener("pointerup", release)
        window.removeEventListener("pointercancel", cancel)
        window.removeEventListener("blur", cancel)
    }
}

/**
 * A text input styled like the standard [Styles.textInput] field, but with a
 * circular "send" icon button that appears on the right as soon as the user
 * types something. When [multiline] is true, the input grows with its content
 * up to three lines.
 *
 * The control is fully controlled: it renders [value] and reports every
 * keystroke through [onValueChange]. When the user presses `Enter` or taps the
 * send button, [onSend] is invoked with the current [value] (which may be
 * `null`/blank, matching the `(value: String?) -> Unit` contract).
 *
 * Hold-to-speak warms the mic/socket after ~150ms and only enters listening
 * after ~350ms, so short clicks never request microphone permission.
 *
 * @param value the current text to display.
 * @param onValueChange called on every input change with the new text.
 * @param onSend called with the current value when the user submits (Enter key
 *   or the send button).
 * @param placeholder placeholder text shown while the field is empty.
 * @param autoFocus when `true`, the field grabs focus once it is mounted.
 */
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String?) -> Unit,
    placeholder: String = "Hold to speak...",
    autoFocus: Boolean = false,
    multiline: Boolean = false,
) {
    val hasText = value.isNotEmpty()
    var textAreaRef by remember { mutableStateOf<HTMLTextAreaElement?>(null) }
    var voiceState by remember { mutableStateOf(VoiceInputUiState.Idle) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentLanguage = rememberUpdatedState(Localization.language.tag)
    val voiceSession = remember {
        VoiceSessionController(
            onValueChange = { currentOnValueChange.value(it) },
            onStateChange = { voiceState = it },
            onError = { console.error(it) },
        )
    }
    val activeVoice = voiceState != VoiceInputUiState.Idle
    val statusPlaceholder = when (voiceState) {
        VoiceInputUiState.Connecting -> "Connecting..."
        VoiceInputUiState.Listening -> "Listening..."
        VoiceInputUiState.Idle -> placeholder
    }
    val statusAria = when (voiceState) {
        VoiceInputUiState.Connecting -> "Connecting voice input"
        VoiceInputUiState.Listening -> "Listening for speech"
        VoiceInputUiState.Idle -> placeholder
    }

    val holdGesture = remember(voiceSession) {
        HoldToSpeakGesture(
            schedule = { delayMs, action -> window.setTimeout(action, delayMs) },
            cancelScheduled = window::clearTimeout,
            onWarmUp = { voiceSession.warmUp(currentLanguage.value) },
            onStart = {
                voiceSession.beginListening(
                    initialValue = currentValue.value,
                    language = currentLanguage.value,
                )
            },
            onStop = { voiceSession.endListening() },
            onWarmUpCancel = { voiceSession.park() },
        )
    }
    DisposableEffect(voiceSession) {
        onDispose {
            holdGesture.cancel()
            voiceSession.dispose()
        }
    }
    if (multiline) {
        LaunchedEffect(value, textAreaRef) {
            textAreaRef?.let(::resizeChatInput)
        }
    }
    Div(attrs = {
        classes(Styles.chatInput)
        if (activeVoice) classes(Styles.chatInputListening)
        if (voiceState == VoiceInputUiState.Connecting) classes(Styles.chatInputConnecting)
    }) {
        if (multiline) {
            TextArea(value = value) {
                classes(Styles.textInput, Styles.chatInputField, Styles.chatInputFieldMultiline)
                if (hasText) classes(Styles.chatInputFieldFilled)
                placeholder(statusPlaceholder)
                attr("aria-label", statusAria)
                onInput {
                    resizeChatInput(it.target)
                    onValueChange(it.value)
                }
                onKeyDown { event ->
                    if (event.key == "Enter" && !event.shiftKey) {
                        event.preventDefault()
                        onSend(value)
                    }
                }
                ref { textArea ->
                    val removeHoldListeners = installHoldListeners(textArea, holdGesture)
                    textAreaRef = textArea
                    resizeChatInput(textArea)
                    if (autoFocus) {
                        textArea.focus()
                    }
                    onDispose {
                        removeHoldListeners()
                        if (textAreaRef === textArea) {
                            textAreaRef = null
                        }
                    }
                }
            }
        } else {
            Input(type = InputType.Text) {
                classes(Styles.textInput, Styles.chatInputField)
                if (hasText) classes(Styles.chatInputFieldFilled)
                value(value)
                placeholder(statusPlaceholder)
                attr("aria-label", statusAria)
                onInput { onValueChange(it.value) }
                onKeyDown { event ->
                    if (event.key == "Enter" && !event.shiftKey) {
                        event.preventDefault()
                        onSend(value)
                    }
                }
                ref { inputRef ->
                    val removeHoldListeners = installHoldListeners(inputRef, holdGesture)
                    if (autoFocus) inputRef.focus()
                    onDispose { removeHoldListeners() }
                }
            }
        }
        if (activeVoice) {
            Div(attrs = {
                classes(Styles.chatVoiceIndicator)
                attr("aria-hidden", "true")
            }) {
                MaterialSymbol(if (voiceState == VoiceInputUiState.Connecting) "progress_activity" else "mic")
            }
        } else if (hasText) {
            Button(attrs = {
                classes(Styles.chatSend)
                attr("type", "button")
                attr("aria-label", "send")
                onClick { onSend(value) }
            }) {
                MaterialSymbol("send")
            }
        }
    }
}

/** Owns a long-lived [BrowserVoiceInput] for the lifetime of [ChatInput]. */
private class VoiceSessionController(
    private val onValueChange: (String) -> Unit,
    private val onStateChange: (VoiceInputUiState) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var session: BrowserVoiceInput? = null

    fun warmUp(language: String) {
        ensure(language).warmUp(language)
    }

    fun beginListening(initialValue: String, language: String) {
        ensure(language).beginListening(initialValue, language)
    }

    fun endListening() {
        session?.endListening()
    }

    fun park() {
        session?.park()
    }

    fun dispose() {
        session?.dispose()
        session = null
    }

    private fun ensure(language: String): BrowserVoiceInput {
        val existing = session
        if (existing != null) return existing
        return BrowserVoiceInput(
            language = language,
            onValueChange = onValueChange,
            onStateChange = onStateChange,
            onError = onError,
        ).also { session = it }
    }
}
