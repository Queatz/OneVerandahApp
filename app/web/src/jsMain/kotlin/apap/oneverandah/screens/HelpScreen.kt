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
import apap.oneverandah.components.OptionChip
import apap.oneverandah.model.Sex
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.TextArea
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

    var showListServiceDialog by remember { mutableStateOf(false) }
    var serviceDescription by remember { mutableStateOf("") }
    var residentName by remember { mutableStateOf("") }
    var zaloNumber by remember { mutableStateOf("") }
    var residentSex by remember { mutableStateOf(Sex.MALE) }
    var blockAddress by remember { mutableStateOf("") }

    Scaffold(
        title = strings.menuHelp,
        onBack = onBack,
        lockScroll = (activeDialogTitle != null) || showListServiceDialog || (phase == HelpPhase.SUCCESS) || (phase == HelpPhase.SUCCESS_LIST_SERVICE),
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
            ListServiceBottomSheet(
                visible = showListServiceDialog,
                strings = strings,
                description = serviceDescription,
                onDescriptionChange = { serviceDescription = it },
                name = residentName,
                onNameChange = { residentName = it },
                zalo = zaloNumber,
                onZaloChange = { zaloNumber = it },
                sex = residentSex,
                onSexChange = { residentSex = it },
                blockAddress = blockAddress,
                onBlockAddressChange = { blockAddress = it },
                onDismiss = { showListServiceDialog = false },
                onSubmit = { desc, name, zalo, s, block ->
                    showListServiceDialog = false
                    phase = HelpPhase.SUBMITTING
                    scope.launch {
                        try {
                            sendContactMessage(
                                name = name,
                                email = "zalo@oneverandah.app",
                                service = "List a service",
                                message = "Zalo: $zalo\nSex: ${if (s == Sex.MALE) "Male" else "Female"}\nBlock/Address: $block\n\nDescription:\n$desc"
                            )
                        } catch (t: Throwable) {
                            console.log("Failed to list service: $t")
                        } finally {
                            phase = HelpPhase.SUCCESS_LIST_SERVICE
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
            SuccessOverlay(
                visible = phase == HelpPhase.SUCCESS_LIST_SERVICE,
                title = strings.listServiceSuccessTitle,
                message = strings.listServiceSuccessMessage,
                doneLabel = strings.done,
                onDone = {
                    phase = HelpPhase.IDLE
                    serviceDescription = ""
                    residentName = ""
                    zaloNumber = ""
                    residentSex = Sex.MALE
                    blockAddress = ""
                    onBack()
                }
            )
        }
    ) {
        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { showListServiceDialog = true }
        }) {
            MaterialSymbol("add_business", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpListService) }
                P { Text(strings.helpListServiceSubtitle) }
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

private enum class HelpPhase { IDLE, SUBMITTING, SUCCESS, SUCCESS_LIST_SERVICE }

@Composable
private fun ListServiceBottomSheet(
    visible: Boolean,
    strings: apap.oneverandah.i18n.UiStrings,
    description: String,
    onDescriptionChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    zalo: String,
    onZaloChange: (String) -> Unit,
    sex: Sex,
    onSexChange: (Sex) -> Unit,
    blockAddress: String,
    onBlockAddressChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (description: String, name: String, zalo: String, sex: Sex, blockAddress: String) -> Unit,
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
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.listServiceDialogTitle) }
        }

        Div(attrs = { classes(Styles.dialogBody) }) {
            // Describe the service
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.serviceDescriptionLabel)
                }
                TextArea(value = description) {
                    classes(Styles.textArea)
                    placeholder(strings.serviceDescriptionPlaceholder)
                    onInput {
                        onDescriptionChange(it.value)
                        showError = false
                    }
                }
                if (showError && description.isBlank()) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(strings.serviceDescriptionError)
                    }
                }
            }

            // Your name
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.yourNameLabel)
                }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(name)
                    placeholder(strings.yourNamePlaceholder)
                    onInput {
                        onNameChange(it.value)
                        showError = false
                    }
                }
                if (showError && name.isBlank()) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(strings.yourNameError)
                    }
                }
            }

            // Sex Selection
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.providerSexLabel)
                }
                Div(attrs = { classes(Styles.chipGroup) }) {
                    OptionChip(
                        label = strings.male,
                        priceHint = "",
                        active = sex == Sex.MALE,
                        onClick = { onSexChange(Sex.MALE) }
                    )
                    OptionChip(
                        label = strings.female,
                        priceHint = "",
                        active = sex == Sex.FEMALE,
                        onClick = { onSexChange(Sex.FEMALE) }
                    )
                }
            }

            // Block/Address
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.providerBlockLabel)
                }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(blockAddress)
                    placeholder(strings.blockAddressPlaceholder)
                    onInput {
                        onBlockAddressChange(it.value)
                        showError = false
                    }
                }
                if (showError && blockAddress.isBlank()) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(strings.blockAddressError)
                    }
                }
            }

            // Your Zalo number
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.zaloLabel)
                }
                Input(type = InputType.Tel) {
                    classes(Styles.textInput)
                    value(zalo)
                    placeholder(strings.zaloPlaceholder)
                    onInput {
                        onZaloChange(it.value)
                        showError = false
                    }
                }
                if (showError && zalo.isBlank()) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(strings.zaloError)
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
                if (description.isNotBlank() && name.isNotBlank() && zalo.isNotBlank() && blockAddress.isNotBlank()) {
                    onSubmit(description.trim(), name.trim(), zalo.trim(), sex, blockAddress.trim())
                } else {
                    showError = true
                }
            }
        }
    }
}
