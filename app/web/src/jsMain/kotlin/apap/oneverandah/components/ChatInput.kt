package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLTextAreaElement

private const val chatInputMinHeightPx = 56
private const val chatInputMaxHeightPx = 104

internal fun chatInputHeightPx(scrollHeight: Int): Int =
    scrollHeight.coerceIn(chatInputMinHeightPx, chatInputMaxHeightPx)

private fun resizeChatInput(textArea: HTMLTextAreaElement) {
    textArea.style.height = "auto"
    textArea.style.height = "${chatInputHeightPx(textArea.scrollHeight)}px"
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
    placeholder: String = "",
    autoFocus: Boolean = false,
    multiline: Boolean = false,
) {
    val hasText = value.isNotEmpty()
    var textAreaRef by remember { mutableStateOf<HTMLTextAreaElement?>(null) }
    if (multiline) {
        LaunchedEffect(value, textAreaRef) {
            textAreaRef?.let(::resizeChatInput)
        }
    }
    Div(attrs = { classes(Styles.chatInput) }) {
        if (multiline) {
            TextArea(value = value) {
                classes(Styles.textInput, Styles.chatInputField, Styles.chatInputFieldMultiline)
                if (hasText) classes(Styles.chatInputFieldFilled)
                placeholder(placeholder)
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
                    textAreaRef = textArea
                    resizeChatInput(textArea)
                    if (autoFocus) {
                        textArea.focus()
                    }
                    onDispose {
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
                placeholder(placeholder)
                onInput { onValueChange(it.value) }
                onKeyDown { event ->
                    if (event.key == "Enter" && !event.shiftKey) {
                        event.preventDefault()
                        onSend(value)
                    }
                }
                if (autoFocus) {
                    ref { inputRef ->
                        inputRef.focus()
                        onDispose { }
                    }
                }
            }
        }
        if (hasText) {
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
