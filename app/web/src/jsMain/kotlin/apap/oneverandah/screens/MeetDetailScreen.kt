package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.MeetApi
import apap.oneverandah.api.MeetApiException
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.meets.MeetDetailResponse
import apap.oneverandah.meets.MeetLogic
import apap.oneverandah.state.MeetProfileStore
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.autofocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun MeetDetailScreen(
    meetId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    var detail by remember { mutableStateOf<MeetDetailResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pitchOpen by remember { mutableStateOf(false) }

    suspend fun refresh() {
        detail = MeetApi.fetchMeet(meetId)
        loading = false
    }

    LaunchedEffect(meetId) { refresh() }

    val meet = detail?.meet
    val title = meet?.name?.ifBlank { strings.meetsUntitled } ?: strings.meetsTitle

    Scaffold(
        title = title,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "meet-$meetId",
        lockScroll = pitchOpen,
        footer = {
            val current = detail
            when {
                current == null -> Unit
                current.meet.mine -> P(attrs = { classes(Styles.homePrompt) }) {
                    Text(strings.meetsCannotPitchOwn)
                }
                current.meet.status != MeetLogic.STATUS_PUBLISHED -> Unit
                current.meet.remainingPitches <= 0 -> P(attrs = { classes(Styles.homePrompt) }) {
                    Text(strings.meetsNoRemaining)
                }
                !current.canPitch -> P(attrs = { classes(Styles.homePrompt) }) {
                    Text(strings.meetsAlreadyPitched)
                }
                else -> PrimaryButton(
                    label = strings.meetsPitchPerson(current.meet.name.ifBlank { strings.meetsUntitled }),
                    icon = "handshake",
                ) { pitchOpen = true }
            }
        },
        overlay = {
            val current = detail
            if (current != null) {
                SendPitchDialog(
                    visible = pitchOpen,
                    personName = current.meet.name.ifBlank { strings.meetsUntitled },
                    onDismiss = { pitchOpen = false },
                    onSend = { message, zalo ->
                        detail = MeetApi.sendPitch(meetId, message, zalo)
                        MeetProfileStore.save(MeetProfileStore.peekName(), zalo)
                        Toaster.show(strings.meetsPitchSent)
                        pitchOpen = false
                        runCatching {
                            sendContactMessage(
                                name = current.meet.name.ifBlank { "Neighbor" },
                                email = "meets@oneverandah.app",
                                service = "Meet pitch",
                                message = buildString {
                                    appendLine("New meet pitch")
                                    appendLine("Meet: ${current.meet.name}")
                                    appendLine("Pitcher's Zalo: $zalo")
                                    appendLine()
                                    append(message)
                                },
                            )
                        }
                    },
                )
            }
        },
    ) {
        when {
            loading -> P { Text(strings.meetsLoading) }
            meet == null -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.meetsEmpty) }
            else -> {
                if (meet.photoUrl.isNotBlank()) {
                    Img(src = meet.photoUrl, alt = title, attrs = { classes(Styles.meetDetailPhoto) })
                } else {
                    Div(attrs = { classes(Styles.storyCoverPlaceholder) }) {
                        MaterialSymbol("handshake")
                    }
                }
                P(attrs = { classes(Styles.sectionTitle) }) { Text(title) }
                P(attrs = { classes(Styles.homePrompt) }) {
                    Text(strings.meetsRemaining(meet.remainingPitches))
                }
                P { Text(meet.pitch) }
            }
        }
    }
}

@Composable
private fun SendPitchDialog(
    visible: Boolean,
    personName: String,
    onDismiss: () -> Unit,
    onSend: suspend (message: String, zalo: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var zalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            zalo = MeetProfileStore.peekZalo()
            busy = false
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.meetsPitchPerson(personName)) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.meetsPitchReplyLabel) }
                TextArea(message) {
                    classes(Styles.textArea)
                    placeholder(strings.meetsPitchReplyPlaceholder)
                    onInput { message = it.value }
                    autoFocus()
                    ref {
                        it.focus()

                        onDispose { }
                    }
                }
            }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.zaloLabel) }
                Input(type = InputType.Tel) {
                    classes(Styles.textInput)
                    value(zalo)
                    placeholder(strings.zaloPlaceholder)
                    onInput { zalo = it.value }
                }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                if (busy) attr("disabled", "")
                onClick { if (!busy) onDismiss() }
            }) { Text(strings.close) }
            PrimaryButton(
                label = strings.meetsSendPitch,
                icon = "send",
                enabled = !busy && message.isNotBlank() && zalo.isNotBlank(),
            ) {
                if (busy) return@PrimaryButton
                scope.launch {
                    busy = true
                    try {
                        onSend(message.trim(), zalo.trim())
                        message = ""
                    } catch (e: MeetApiException) {
                        Toaster.show(e.message ?: strings.meetsPitchError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.meetsPitchError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
