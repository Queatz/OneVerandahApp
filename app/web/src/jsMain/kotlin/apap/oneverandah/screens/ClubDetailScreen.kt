package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ClubApi
import apap.oneverandah.api.ClubApiException
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.clubs.ClubContentBlock
import apap.oneverandah.clubs.ClubDetailResponse
import apap.oneverandah.clubs.ClubLogic
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.FullscreenPhotoDialog
import apap.oneverandah.components.MarkdownView
import apap.oneverandah.components.PhotoScroller
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.components.ZaloContactButton
import apap.oneverandah.components.ZaloQrDialog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.MeetProfileStore
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun ClubDetailScreen(
    clubId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    var detail by remember { mutableStateOf<ClubDetailResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var joinOpen by remember { mutableStateOf(false) }
    var zaloOpen by remember { mutableStateOf(false) }
    var previewPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableStateOf(0) }

    suspend fun refresh() {
        detail = ClubApi.fetchClub(clubId)
        loading = false
    }

    LaunchedEffect(clubId) { refresh() }

    val card = detail?.club
    val title = card?.name?.ifBlank { strings.clubsUntitled } ?: strings.clubsTitle
    val dialogOpen = joinOpen || zaloOpen || previewPhotos.isNotEmpty()

    Scaffold(
        title = title,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "club-$clubId",
        lockScroll = dialogOpen,
        footer = {
            if (detail == null) return@Scaffold
            Div(attrs = { classes(Styles.locationFooter) }) {
                PrimaryButton(
                    label = strings.clubsJoin,
                    icon = "group_add",
                ) { joinOpen = true }
            }
        },
        overlay = {
            val current = detail
            if (current != null) {
                ZaloQrDialog(
                    visible = zaloOpen,
                    phone = current.club.hostZalo,
                    onDismiss = { zaloOpen = false },
                )
                JoinClubDialog(
                    visible = joinOpen,
                    clubName = title,
                    questions = current.joinQuestions,
                    onDismiss = { joinOpen = false },
                    onSend = { name, zalo, answers, message ->
                        detail = ClubApi.join(clubId, name, zalo, answers, message)
                        MeetProfileStore.save(name, zalo)
                        Toaster.show(strings.clubsJoinSent)
                        joinOpen = false
                        runCatching {
                            sendContactMessage(
                                name = name,
                                email = "clubs@oneverandah.app",
                                service = "Join club",
                                message = buildString {
                                    appendLine("Join club")
                                    appendLine("Club: $title")
                                    appendLine("Name: $name")
                                    appendLine("Zalo: $zalo")
                                    current.joinQuestions.forEachIndexed { index, question ->
                                        appendLine("$question: ${answers.getOrNull(index).orEmpty()}")
                                    }
                                    appendLine()
                                    append(message)
                                },
                            )
                        }
                    },
                )
            }
            FullscreenPhotoDialog(
                visible = previewPhotos.isNotEmpty(),
                imageUrl = previewPhotos.getOrNull(previewIndex),
                alt = title,
                photos = previewPhotos,
                selectedIndex = previewIndex,
                onSelectedIndexChange = { previewIndex = it },
                onDismiss = { previewPhotos = emptyList() },
            )
        },
    ) {
        when {
            loading -> P { Text(strings.clubsLoading) }
            card == null -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.clubsEmpty) }
            else -> {
                if (card.photoUrls.isNotEmpty()) {
                    PhotoScroller(
                        photos = card.photoUrls,
                        alt = title,
                        onPhotoClick = { i ->
                            previewPhotos = card.photoUrls
                            previewIndex = i
                        },
                    )
                }
                H2({
                    style {
                        marginBottom(0.px)
                    }
                }) { Text(title) }
                if (card.tagline.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardType) }) { Text(card.tagline) }
                }
                if (card.hostName.isNotBlank()) {
                    SectionTitle(strings.clubsHostedBy)
                    P(attrs = { classes(Styles.homePrompt) }) { Text(card.hostName) }
                }
                ZaloContactButton(card.hostZalo) { zaloOpen = true }
                if (card.description.isNotBlank()) {
                    SectionTitle(strings.clubsDetails)
                    P(attrs = { classes(Styles.homePrompt) }) { Text(card.description) }
                }
                LocationHighlights(card.activities)
                detail?.content.orEmpty().forEach { block ->
                    ClubContentBlockView(block) { url ->
                        previewPhotos = listOf(url)
                        previewIndex = 0
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubContentBlockView(
    block: ClubContentBlock,
    onPhoto: (String) -> Unit,
) {
    when (block.type) {
        ClubLogic.BLOCK_IMAGE -> if (block.imageUrl.isNotBlank()) {
            Img(src = block.imageUrl, alt = "", attrs = {
                classes(Styles.locationContentPhoto)
                onClick { onPhoto(block.imageUrl) }
            })
        }
        else -> if (block.text.isNotBlank()) MarkdownView(block.text)
    }
}

@Composable
private fun JoinClubDialog(
    visible: Boolean,
    clubName: String,
    questions: List<String>,
    onDismiss: () -> Unit,
    onSend: suspend (name: String, zalo: String, answers: List<String>, message: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(MeetProfileStore.peekName()) }
    var zalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var answers by remember { mutableStateOf(List(questions.size) { "" }) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible, questions) {
        if (visible) {
            name = MeetProfileStore.peekName()
            zalo = MeetProfileStore.peekZalo()
            answers = List(questions.size) { answers.getOrNull(it).orEmpty() }
            busy = false
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.clubsJoin) }
            P(attrs = { classes(Styles.dialogService) }) { Text(clubName) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.clubsYourName) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(name)
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
            questions.forEachIndexed { index, question ->
                Div(attrs = { classes(Styles.textField) }) {
                    Label(attrs = { classes(Styles.textLabel) }) { Text(question) }
                    TextArea(answers.getOrNull(index).orEmpty()) {
                        classes(Styles.textArea)
                        onInput { event ->
                            answers = answers.toMutableList().also { list ->
                                while (list.size <= index) list.add("")
                                list[index] = event.value
                            }
                        }
                    }
                }
            }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.clubsYourMessage) }
                TextArea(message) {
                    classes(Styles.textArea)
                    placeholder(strings.clubsYourMessagePlaceholder)
                    onInput { message = it.value }
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
                label = strings.clubsJoinSubmit,
                icon = "group_add",
                enabled = !busy && name.isNotBlank() && zalo.isNotBlank(),
            ) {
                scope.launch {
                    busy = true
                    try {
                        onSend(name.trim(), zalo.trim(), answers.map { it.trim() }, message.trim())
                        message = ""
                    } catch (e: ClubApiException) {
                        Toaster.show(e.message ?: strings.clubsJoinError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.clubsJoinError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
