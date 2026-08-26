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
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.meets.MeetCard
import apap.oneverandah.meets.MeetLogic
import apap.oneverandah.meets.MeetsFeedResponse
import apap.oneverandah.state.MeetProfileStore
import apap.oneverandah.state.Toaster
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun MeetScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenMeet: (String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf(MeetsFeedResponse()) }
    var loading by remember { mutableStateOf(true) }
    var composerOpen by remember { mutableStateOf(false) }

    suspend fun refresh() {
        feed = MeetApi.fetchFeed()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        title = strings.meetsTitle,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "meets",
        lockScroll = composerOpen,
        overlay = {
            CreateMeetDialog(
                visible = composerOpen,
                onDismiss = { composerOpen = false },
                onCreated = {
                    composerOpen = false
                    scope.launch { refresh() }
                },
            )
        },
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.meetsPrompt)
        }

        when {
            loading -> P { Text(strings.meetsLoading) }
            else -> {
                if (feed.inReview.isNotEmpty()) {
                    P(attrs = { classes(Styles.sectionTitle) }) { Text(strings.meetsInReview) }
                    Div(attrs = { classes(Styles.challengesReviewList) }) {
                        feed.inReview.forEach { item ->
                            PendingMeetCard(item)
                        }
                    }
                }

                Div(attrs = { classes(Styles.storiesGrid) }) {
                    Button(attrs = {
                        classes(Styles.challengeStartCard)
                        attr("type", "button")
                        onClick { composerOpen = true }
                    }) {
                        MaterialSymbol("add")
                        Span(attrs = { classes(Styles.storyCoverTitle) }) { Text(strings.meetsStartNew) }
                    }
                    feed.meets.forEach { card ->
                        MeetCardButton(card) { onOpenMeet(card.id) }
                    }
                }

                if (feed.meets.isEmpty() && feed.inReview.isEmpty()) {
                    P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.meetsEmpty) }
                }
            }
        }
    }
}

@Composable
private fun MeetCardButton(card: MeetCard, onClick: () -> Unit) {
    val strings = Localization.strings
    val title = card.name.ifBlank { strings.meetsUntitled }
    Button(attrs = {
        classes(Styles.storyCoverCard)
        attr("type", "button")
        onClick { onClick() }
    }) {
        if (card.photoUrl.isNotBlank()) {
            Img(src = card.photoUrl, alt = title, attrs = { classes(Styles.storyCoverThumb) })
        } else {
            Div(attrs = { classes(Styles.storyCoverPlaceholder) }) {
                MaterialSymbol("handshake")
            }
        }
        Span(attrs = { classes(Styles.storyCoverTitle) }) { Text(title) }
        Span(attrs = { classes(Styles.storyCoverMeta) }) {
            Text(strings.meetsRemaining(card.remainingPitches))
        }
        P(attrs = { classes(Styles.meetPitchClamp) }) {
            Text(MeetLogic.ellipsize(card.pitch))
        }
    }
}

@Composable
private fun PendingMeetCard(item: MeetCard) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.challengeReviewCard) }) {
        Div(attrs = { classes(Styles.challengeReviewThumb) }) {
            if (item.photoUrl.isNotBlank()) {
                Img(src = item.photoUrl, alt = "", attrs = { classes(Styles.challengeReviewThumbImg) })
            } else {
                MaterialSymbol("hourglass_top")
            }
        }
        Div {
            Span(attrs = { classes(Styles.storyCoverTitle) }) {
                Text(item.name.ifBlank { strings.meetsUntitled })
            }
            P(attrs = { classes(Styles.homePrompt) }) { Text(strings.meetsInReview) }
        }
    }
}

@Composable
private fun CreateMeetDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(MeetProfileStore.peekName()) }
    var zalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var pitch by remember { mutableStateOf("") }
    var pitchLimit by remember { mutableStateOf(MeetLogic.DEFAULT_PITCH_LIMIT.toString()) }
    var file by remember { mutableStateOf<File?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            name = MeetProfileStore.peekName()
            zalo = MeetProfileStore.peekZalo()
        }
    }

    fun setPicked(picked: File?) {
        previewUrl?.let { window.asDynamic().URL.revokeObjectURL(it) }
        file = picked
        previewUrl = picked?.let { window.asDynamic().URL.createObjectURL(it) as String }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
        expanded = true,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.meetsStartNew) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Label(attrs = {
                classes(Styles.challengePickVideo)
                attr("for", "meet-photo-input")
            }) {
                val preview = previewUrl
                if (preview != null) {
                    Img(src = preview, alt = "", attrs = { classes(Styles.meetPickPhoto) })
                } else {
                    MaterialSymbol("add_a_photo")
                    Span { Text(strings.meetsPickPhoto) }
                }
                Input(type = InputType.File) {
                    attr("id", "meet-photo-input")
                    attr("accept", "image/*")
                    style { property("display", "none") }
                    onChange {
                        setPicked((it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull())
                    }
                }
            }
            P(attrs = { classes(Styles.homePrompt) }) { Text(strings.meetsYourPhoto) }

            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.meetsYourName) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(name)
                    placeholder(strings.meetsYourNamePlaceholder)
                    onInput { name = it.value }
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
            P(attrs = { classes(Styles.homePrompt) }) { Text(strings.meetsZaloHint) }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.meetsPitchLimit) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(pitchLimit)
                    attr("inputmode", "numeric")
                    onInput { pitchLimit = it.value }
                }
            }
            P(attrs = { classes(Styles.homePrompt) }) { Text(strings.meetsPitchLimitHint) }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.meetsPitchLabel) }
                TextArea(pitch) {
                    classes(Styles.textArea)
                    placeholder(strings.meetsPitchPlaceholder)
                    onInput { pitch = it.value }
                }
            }
            if (busy) {
                Div(attrs = { classes(Styles.challengeProgressTrack) }) {
                    Div(attrs = {
                        classes(Styles.challengeProgressFill)
                        style { property("width", "${progress.coerceIn(0, 100)}%") }
                    })
                }
                P(attrs = { classes(Styles.homePrompt) }) { Text("${strings.meetsUploading} $progress%") }
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
                label = strings.meetsSubmit,
                icon = "handshake",
                enabled = !busy && name.isNotBlank() && zalo.isNotBlank() && pitch.isNotBlank() && file != null,
            ) {
                val picked = file ?: return@PrimaryButton
                scope.launch {
                    busy = true
                    progress = 4
                    try {
                        MeetApi.create(
                            name = name.trim(),
                            pitch = pitch.trim(),
                            zaloNumber = zalo.trim(),
                            pitchLimit = MeetLogic.parsePitchLimit(pitchLimit),
                            file = picked,
                            onProgress = { progress = it },
                        )
                        MeetProfileStore.save(name.trim(), zalo.trim())
                        Toaster.show(strings.meetsInReview)
                        setPicked(null)
                        pitch = ""
                        onCreated()
                    } catch (e: MeetApiException) {
                        Toaster.show(e.message ?: strings.meetsSubmitError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.meetsSubmitError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
