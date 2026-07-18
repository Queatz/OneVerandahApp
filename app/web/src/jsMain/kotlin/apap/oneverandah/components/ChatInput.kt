package apap.oneverandah.components

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input

/**
 * A single-line text input styled like the standard [Styles.textInput] field,
 * but with a circular "send" icon button that appears on the right as soon as
 * the user types something.
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
) {
    val hasText = value.isNotEmpty()
    Div(attrs = { classes(Styles.chatInput) }) {
        Input(type = InputType.Text) {
            classes(Styles.textInput, Styles.chatInputField)
            if (hasText) classes(Styles.chatInputFieldFilled)
            value(value)
            placeholder(placeholder)
            onInput { onValueChange(it.value) }
            onKeyDown { event ->
                if (event.key == "Enter") {
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
