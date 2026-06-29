package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.Styles
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SuccessOverlay
import apap.oneverandah.components.OptionChip
import apap.oneverandah.model.Sex
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun ListServiceScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    var phase by remember { mutableStateOf(ListServicePhase.IDLE) }
    val scope = rememberCoroutineScope()

    var serviceDescription by remember { mutableStateOf("") }
    var residentName by remember { mutableStateOf("") }
    var zaloNumber by remember { mutableStateOf("") }
    var residentSex by remember { mutableStateOf(Sex.MALE) }
    var blockAddress by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        title = strings.helpListService,
        onBack = onBack,
        lockScroll = phase == ListServicePhase.SUCCESS,
        footer = {
            PrimaryButton(
                label = strings.helpSubmit,
                icon = "send",
                enabled = phase == ListServicePhase.IDLE,
            ) {
                if (serviceDescription.isNotBlank() && residentName.isNotBlank() &&
                    zaloNumber.isNotBlank() && blockAddress.isNotBlank()
                ) {
                    val desc = serviceDescription.trim()
                    val name = residentName.trim()
                    val zalo = zaloNumber.trim()
                    val s = residentSex
                    val block = blockAddress.trim()
                    phase = ListServicePhase.SUBMITTING
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
                            phase = ListServicePhase.SUCCESS
                        }
                    }
                } else {
                    showError = true
                }
            }
        },
        overlay = {
            SuccessOverlay(
                visible = phase == ListServicePhase.SUCCESS,
                title = strings.listServiceSuccessTitle,
                message = strings.listServiceSuccessMessage,
                doneLabel = strings.done,
                onDone = {
                    phase = ListServicePhase.IDLE
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
        // Describe the service
        Div(attrs = { classes(Styles.textField) }) {
            Label(attrs = { classes(Styles.textLabel) }) {
                Text(strings.serviceDescriptionLabel)
            }
            TextArea(value = serviceDescription) {
                classes(Styles.textArea)
                placeholder(strings.serviceDescriptionPlaceholder)
                onInput {
                    serviceDescription = it.value
                    showError = false
                }
            }
            if (showError && serviceDescription.isBlank()) {
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
                value(residentName)
                placeholder(strings.yourNamePlaceholder)
                onInput {
                    residentName = it.value
                    showError = false
                }
            }
            if (showError && residentName.isBlank()) {
                Span(attrs = { classes(Styles.textError) }) {
                    Text(strings.yourNameError)
                }
            }
        }

        // Sex Selection
        Div(attrs = { classes(Styles.textField) }) {
            Label(attrs = { classes(Styles.textLabel) }) {
                Text(strings.yourSexLabel)
            }
            Div(attrs = { classes(Styles.chipGroup) }) {
                OptionChip(
                    label = strings.male,
                    priceHint = "",
                    active = residentSex == Sex.MALE,
                    onClick = { residentSex = Sex.MALE }
                )
                OptionChip(
                    label = strings.female,
                    priceHint = "",
                    active = residentSex == Sex.FEMALE,
                    onClick = { residentSex = Sex.FEMALE }
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
                    blockAddress = it.value
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
                value(zaloNumber)
                placeholder(strings.zaloPlaceholder)
                onInput {
                    zaloNumber = it.value
                    showError = false
                }
            }
            if (showError && zaloNumber.isBlank()) {
                Span(attrs = { classes(Styles.textError) }) {
                    Text(strings.zaloError)
                }
            }
        }
    }
}

private enum class ListServicePhase { IDLE, SUBMITTING, SUCCESS }
