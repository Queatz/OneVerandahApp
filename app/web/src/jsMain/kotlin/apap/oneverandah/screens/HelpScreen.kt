package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SuccessOverlay
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlinx.browser.window

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(HelpPhase.IDLE) }
    val scope = rememberCoroutineScope()

    Scaffold(
        title = strings.menuHelp,
        onBack = onBack,
        lockScroll = (activeDialogTitle != null) || (phase == HelpPhase.SUCCESS),
        overlay = {
            HelpBottomSheet(
                visible = activeDialogTitle != null,
                title = activeDialogTitle.orEmpty(),
                strings = strings,
                messageText = messageText,
                onMessageChange = { messageText = it },
                onDismiss = { activeDialogTitle = null },
                onSubmit = { text ->
                    val serviceTitle = activeDialogTitle.orEmpty()
                    activeDialogTitle = null
                    phase = HelpPhase.SUBMITTING
                    scope.launch {
                        try {
                            sendContactMessage(
                                name = "Guest",
                                email = "guest@oneverandah.app",
                                service = serviceTitle,
                                message = text
                            )
                        } catch (t: Throwable) {
                            console.log("Failed to send feedback: $t")
                        } finally {
                            phase = HelpPhase.SUCCESS
                        }
                    }
                }
            )
            SuccessOverlay(
                visible = phase == HelpPhase.SUCCESS,
                title = strings.helpSuccessTitle,
                message = strings.helpSuccessMessage,
                doneLabel = strings.done,
                onDone = {
                    phase = HelpPhase.IDLE
                    messageText = ""
                    onBack()
                }
            )
        }
    ) {
        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { activeDialogTitle = strings.helpFeedback }
        }) {
            MaterialSymbol("feedback", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpFeedback) }
            }
        }

        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { activeDialogTitle = strings.helpReportProblem }
        }) {
            MaterialSymbol("report_problem", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpReportProblem) }
            }
        }
    }
}

@Composable
private fun HelpBottomSheet(
    visible: Boolean,
    title: String,
    strings: apap.oneverandah.i18n.UiStrings,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var showError by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf<Double?>(null) }
    var dragY by remember { mutableStateOf(0.0) }
    val dismissThreshold = 120.0

    LaunchedEffect(visible) {
        if (!visible) {
            dragStart = null
            dragY = 0.0
            showError = false
        }
    }

    Div(attrs = {
        classes(Styles.dialogScrim)
        if (visible) classes(Styles.dialogScrimVisible)
        attr("role", "presentation")
        onClick { onDismiss() }
    }) {
        Div(attrs = {
            classes(Styles.dialogSheet)
            attr("role", "dialog")
            attr("aria-modal", "true")
            if (dragStart != null) {
                style {
                    property("transform", "translateY(${dragY}px)")
                    property("transition", "none")
                }
            }
            onClick { it.stopPropagation() }
            onMouseMove { e ->
                if (dragStart != null) dragY = (e.clientY - dragStart!!).coerceAtLeast(0.0)
            }
            onTouchMove { e ->
                val start = dragStart ?: return@onTouchMove
                val touch = e.touches.item(0) ?: return@onTouchMove
                dragY = (touch.clientY - start).coerceAtLeast(0.0)
            }
            onMouseUp {
                if (dragStart != null) {
                    if (dragY > dismissThreshold) onDismiss()
                    dragStart = null
                    dragY = 0.0
                }
            }
            onTouchEnd {
                if (dragStart != null) {
                    if (dragY > dismissThreshold) onDismiss()
                    dragStart = null
                    dragY = 0.0
                }
            }
        }) {
            // Grip handle
            Div(attrs = {
                classes(Styles.dialogGrip)
                attr("role", "button")
                attr("aria-label", strings.close)
                onMouseDown { e ->
                    dragStart = e.clientY.toDouble()
                    dragY = 0.0
                }
                onTouchStart { e ->
                    val touch = e.touches.item(0)
                    if (touch != null) {
                        dragStart = touch.clientY.toDouble()
                        dragY = 0.0
                    }
                }
            }) {
                Div(attrs = { classes(Styles.dialogHandle) })
            }

            Div(attrs = { classes(Styles.dialogHeader) }) {
                P(attrs = { classes(Styles.dialogTitle) }) { Text(title) }
            }

            Div(attrs = { classes(Styles.dialogBody) }) {
                Div(attrs = { classes(Styles.textField) }) {
                    Input(type = InputType.Text) {
                        classes(Styles.textInput)
                        value(messageText)
                        placeholder(strings.helpPlaceholder)
                        onInput {
                            onMessageChange(it.value)
                            showError = false
                        }
                    }
                    if (showError && messageText.isBlank()) {
                        Span(attrs = { classes(Styles.textError) }) {
                            Text(strings.helpErrorRequired)
                        }
                    }
                }
            }

            Div(attrs = { classes(Styles.dialogActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    onClick { onDismiss() }
                }) { Text(strings.close) }

                PrimaryButton(
                    label = strings.helpSubmit,
                    icon = "send",
                    enabled = true,
                ) {
                    if (messageText.isNotBlank()) {
                        onSubmit(messageText.trim())
                    } else {
                        showError = true
                    }
                }
            }
        }
    }
}

private enum class HelpPhase { IDLE, SUBMITTING, SUCCESS }
