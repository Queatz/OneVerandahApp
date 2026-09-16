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
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.InfoList
import apap.oneverandah.components.Spacer
import apap.oneverandah.i18n.Localization
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
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
    val router = Router.current

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
            onClick { activeDialogTitle = strings.helpSuggestFeature }
        }) {
            MaterialSymbol("lightbulb", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpSuggestFeature) }
                P { Text(strings.helpSuggestFeatureSubtitle) }
            }
        }

        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { activeDialogTitle = strings.helpFeedback }
        }) {
            MaterialSymbol("feedback", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpFeedback) }
                P { Text(strings.helpFeedbackSubtitle) }
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
                P { Text(strings.helpReportProblemSubtitle) }
            }
        }

        Spacer()

        P(attrs = { classes(Styles.homePrompt) }) {
            Text(strings.aboutText)
        }
        InfoList(
            items = listOf(
                "workspace_premium" to strings.aboutExclusive,
                "verified" to strings.aboutTrusted,
                "redeem" to strings.aboutFree,
            )
        )
        // Hidden admin entry: a tiny, low-contrast control at the bottom of About.
        Div(attrs = { classes(Styles.hiddenAdminWrap) }) {
            Button(attrs = {
                classes(Styles.hiddenAdminButton)
                attr("type", "button")
                attr("aria-label", strings.adminTitle)
                onClick { router.navigate("/admin") }
            }) {
                Text("·")
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

    LaunchedEffect(visible) {
        if (!visible) {
            showError = false
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss
    ) {
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

private enum class HelpPhase { IDLE, SUBMITTING, SUCCESS }
