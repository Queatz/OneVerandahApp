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
    var listening by remember { mutableStateOf(false) }
    var voiceInput by remember { mutableStateOf<BrowserVoiceInput?>(null) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val holdGesture = remember {
        HoldToSpeakGesture(
            schedule = { action -> window.setTimeout(action, 350) },
            cancelScheduled = window::clearTimeout,
            onStart = {
                voiceInput?.dispose()
                voiceInput = BrowserVoiceInput(
                    initialValue = currentValue.value,
                    language = Localization.language.tag,
                    onValueChange = { currentOnValueChange.value(it) },
                    onListeningChange = { listening = it },
                    onError = { console.error(it) },
                ).also(BrowserVoiceInput::start)
            },
            onStop = { voiceInput?.stop() },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            holdGesture.cancel()
            voiceInput?.dispose()
        }
    }
    if (multiline) {
        LaunchedEffect(value, textAreaRef) {
            textAreaRef?.let(::resizeChatInput)
        }
    }
    Div(attrs = {
        classes(Styles.chatInput)
        if (listening) classes(Styles.chatInputListening)
    }) {
        if (multiline) {
            TextArea(value = value) {
                classes(Styles.textInput, Styles.chatInputField, Styles.chatInputFieldMultiline)
                if (hasText) classes(Styles.chatInputFieldFilled)
                placeholder(if (listening) "Listening..." else placeholder)
                attr("aria-label", if (listening) "Listening for speech" else placeholder)
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
                placeholder(if (listening) "Listening..." else placeholder)
                attr("aria-label", if (listening) "Listening for speech" else placeholder)
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
        if (listening) {
            Div(attrs = {
                classes(Styles.chatVoiceIndicator)
                attr("aria-hidden", "true")
            }) {
                MaterialSymbol("mic")
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
