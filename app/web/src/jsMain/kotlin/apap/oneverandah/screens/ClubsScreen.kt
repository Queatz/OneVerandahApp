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
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.clubs.ClubCard
import apap.oneverandah.clubs.ClubLogic
import apap.oneverandah.clubs.ClubsFeedResponse
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.components.Spacer
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.MeetProfileStore
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun ClubsScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenClub: (String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf(ClubsFeedResponse()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }
    var createTagline by remember { mutableStateOf("") }
    var createDescription by remember { mutableStateOf("") }
    var createHostName by remember { mutableStateOf(MeetProfileStore.peekName()) }
    var createZalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var submitting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        feed = ClubApi.fetchFeed()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val visible = feed.clubs
        .filter { category == null || it.category == category }
        .filter { ClubLogic.matchesQuery(it, query) }

    Scaffold(
        title = strings.clubsTitle,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "clubs",
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.clubsPrompt)
        }

        Div(attrs = { classes(Styles.textField) }) {
            Input(type = InputType.Search) {
                classes(Styles.textInput)
                value(query)
                placeholder(strings.clubsSearchPlaceholder)
                onInput { query = it.value }
            }
        }

        Div(attrs = { classes(Styles.chipGroup) }) {
            Button(attrs = {
                classes(Styles.chip)
                if (category == null) classes(Styles.chipActive)
                attr("type", "button")
                onClick { category = null }
            }) { Text(strings.clubsAllCategories) }
            feed.categories.forEach { key ->
                Button(attrs = {
                    classes(Styles.chip)
                    if (category == key) classes(Styles.chipActive)
                    attr("type", "button")
                    onClick { category = key }
                }) { Text(key) }
            }
        }

        Spacer()

        when {
            loading -> P { Text(strings.clubsLoading) }
            visible.isEmpty() -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.clubsEmpty) }
            else -> visible.forEach { card ->
                ClubFeedCard(card = card, onOpen = { onOpenClub(card.id) })
            }
        }

        SectionTitle(strings.clubsCreateTitle)
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.clubsCreateHint) }
        ClubField(strings.clubsCreateName, createName) { createName = it }
        ClubField(strings.clubsCreateTagline, createTagline) { createTagline = it }
        ClubArea(strings.clubsCreateDescription, createDescription) { createDescription = it }
        ClubField(strings.clubsCreateHostName, createHostName) { createHostName = it }
        Div(attrs = { classes(Styles.textField) }) {
            Label(attrs = { classes(Styles.textLabel) }) { Text(strings.zaloLabel) }
            Input(type = InputType.Tel) {
                classes(Styles.textInput)
                value(createZalo)
                placeholder(strings.zaloPlaceholder)
                onInput { createZalo = it.value }
            }
        }
        PrimaryButton(
            label = if (submitting) strings.helpSubmit else strings.clubsCreateSubmit,
            icon = "send",
            enabled = !submitting && createName.isNotBlank() && createHostName.isNotBlank() && createZalo.isNotBlank(),
        ) {
            scope.launch {
                submitting = true
                try {
                    ClubApi.requestCreate(
                        name = createName.trim(),
                        tagline = createTagline.trim(),
                        description = createDescription.trim(),
                        hostName = createHostName.trim(),
                        hostZalo = createZalo.trim(),
                    )
                    MeetProfileStore.save(createHostName.trim(), createZalo.trim())
                    runCatching {
                        sendContactMessage(
                            name = createHostName.trim(),
                            email = "clubs@oneverandah.app",
                            service = "Create a club",
                            message = buildString {
                                appendLine("Create a club")
                                appendLine("Name: ${createName.trim()}")
                                appendLine("Tagline: ${createTagline.trim()}")
                                appendLine("Host: ${createHostName.trim()}")
                                appendLine("Zalo: ${createZalo.trim()}")
                                appendLine()
                                append(createDescription.trim())
                            },
                        )
                    }
                    createName = ""
                    createTagline = ""
                    createDescription = ""
                    Toaster.show(strings.clubsCreateSuccess)
                } catch (t: Throwable) {
                    Toaster.show(t.message ?: strings.clubsCreateError)
                } finally {
                    submitting = false
                }
            }
        }
    }
}

@Composable
private fun ClubField(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(value)
            onInput { onChange(it.value) }
        }
    }
}

@Composable
private fun ClubArea(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        TextArea(value) {
            classes(Styles.textArea)
            placeholder(label)
            onInput { onChange(it.value) }
        }
    }
}

@Composable
internal fun ClubFeedCard(
    card: ClubCard,
    onOpen: () -> Unit,
) {
    val strings = Localization.strings
    val photos = card.photoUrls.ifEmpty { emptyList() }

    Div(attrs = {
        classes(Styles.locationCard)
        attr("role", "button")
        onClick { onOpen() }
    }) {
        Div(attrs = { classes(Styles.locationCardMain) }) {
            Div(attrs = { classes(Styles.locationCardPhotos) }) {
                if (photos.isEmpty()) {
                    Div(attrs = { classes(Styles.locationCardPhotoFallback) }) {
                        MaterialSymbol("groups")
                    }
                } else {
                    Div(attrs = { classes(Styles.locationCardPhotosTrack) }) {
                        photos.forEach { url ->
                            Img(src = url, alt = card.name, attrs = { classes(Styles.locationCardPhoto) })
                        }
                    }
                }
                if (card.category.isNotBlank()) {
                    Span(attrs = { classes(Styles.locationCardCategory) }) { Text(card.category) }
                }
            }

            Div(attrs = { classes(Styles.locationCardBody) }) {
                H2(attrs = { classes(Styles.locationCardName) }) {
                    Text(card.name.ifBlank { strings.clubsUntitled })
                }
                if (card.tagline.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardType) }) { Text(card.tagline) }
                }
                if (card.hostName.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardMeta) }) {
                        Text("${strings.clubsHostedBy} ${card.hostName}")
                    }
                }
                LocationHighlights(card.activities)
            }
        }
    }
}
