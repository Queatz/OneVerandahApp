package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.StoryApi
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.model.Language
import apap.oneverandah.state.Toaster
import apap.oneverandah.stories.Story
import apap.oneverandah.stories.StoryLogic
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
fun StoriesScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenStory: (String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var role by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var vibe by remember { mutableStateOf("") }
    var want by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var idea by remember { mutableStateOf("") }
    var submittingFeatured by remember { mutableStateOf(false) }
    var submittingIdea by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        stories = StoryApi.fetchStories()
        loading = false
    }

    Scaffold(
        title = strings.storiesTitle,
        onBack = onBack,
        onHome = onHome,
        scrollKey = "stories",
        showPhrasesAction = false,
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.storiesShelfHint)
        }

        when {
            loading -> P { Text(strings.storiesLoading) }
            stories.isEmpty() -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.storiesEmpty) }
            else -> {
                Div(attrs = { classes(Styles.storiesGrid) }) {
                    stories.forEach { story ->
                        Button(attrs = {
                            classes(Styles.storyCoverCard)
                            attr("type", "button")
                            onClick { onOpenStory(story.id) }
                        }) {
                            if (!story.coverImageUrl.isNullOrBlank()) {
                                Img(src = story.coverImageUrl!!, alt = story.name, attrs = {
                                    classes(Styles.storyCoverThumb)
                                })
                            } else {
                                Div(attrs = { classes(Styles.storyCoverPlaceholder) }) {
                                    MaterialSymbol("auto_stories")
                                }
                            }
                            Span(attrs = { classes(Styles.storyCoverTitle) }) { Text(story.name) }
                            if (story.category.isNotBlank()) {
                                Span(attrs = { classes(Styles.storyCoverCategory) }) { Text(story.category) }
                            }
                            Span(attrs = { classes(Styles.storyCoverMeta) }) {
                                Text(
                                    listOf(
                                        strings.storiesPages(StoryLogic.pageCount(story)),
                                        Language.fromTag(story.languageTag).displayName,
                                    ).joinToString(" · "),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionTitle(strings.storiesFeaturedTitle)
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.storiesFeaturedHint) }
        StoryField(strings.storiesFeaturedRole, role) { role = it }
        StoryArea(strings.storiesFeaturedAbout, about) { about = it }
        StoryArea(strings.storiesFeaturedVibe, vibe) { vibe = it }
        StoryArea(strings.storiesFeaturedWant, want) { want = it }
        Div(attrs = { classes(Styles.textField) }) {
            Label(attrs = { classes(Styles.textLabel) }) { Text(strings.storiesFeaturedPhotos) }
            Input(type = InputType.File) {
                classes(Styles.textInput)
                attr("accept", "image/*")
                attr("multiple", "true")
                onChange {
                    val el = it.nativeEvent.target as? HTMLInputElement
                    val picked = mutableListOf<File>()
                    val list = el?.files
                    if (list != null) {
                        for (index in 0 until list.length) {
                            list.item(index)?.let(picked::add)
                        }
                    }
                    photos = picked.take(StoryLogic.MAX_CAST_PHOTOS)
                }
            }
            if (photos.isNotEmpty()) {
                P(attrs = { classes(Styles.homePrompt) }) {
                    Text(photos.joinToString { it.name })
                }
            }
        }
        PrimaryButton(
            label = if (submittingFeatured) strings.helpSubmit else strings.storiesFeaturedSubmit,
            icon = "send",
            enabled = !submittingFeatured && role.isNotBlank() && StoryLogic.hasRequiredCastPhotos(photos.size),
        ) {
            scope.launch {
                submittingFeatured = true
                try {
                    val urls = photos.map { StoryApi.uploadCastPhoto(it) }
                    sendContactMessage(
                        name = role.trim(),
                        email = "stories@oneverandah.app",
                        service = "Mini Story cast request",
                        message = buildString {
                            appendLine("Featured in the next Mini Story")
                            appendLine("Desired role: ${role.trim()}")
                            appendLine("About me: ${about.trim()}")
                            appendLine("What I'm like: ${vibe.trim()}")
                            appendLine("What I want to do: ${want.trim()}")
                            appendLine("Photos:")
                            urls.forEach { appendLine("- $it") }
                        },
                    )
                    role = ""; about = ""; vibe = ""; want = ""; photos = emptyList()
                    Toaster.show(strings.storiesFeaturedSuccess)
                } catch (t: Throwable) {
                    Toaster.show(t.message ?: strings.storiesSubmitError)
                } finally {
                    submittingFeatured = false
                }
            }
        }

        SectionTitle(strings.storiesSuggestTitle)
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.storiesSuggestHint) }
        StoryArea(strings.storiesSuggestPlaceholder, idea) { idea = it }
        PrimaryButton(
            label = if (submittingIdea) strings.helpSubmit else strings.storiesSuggestSubmit,
            icon = "lightbulb",
            enabled = !submittingIdea && idea.isNotBlank(),
        ) {
            scope.launch {
                submittingIdea = true
                try {
                    sendContactMessage(
                        name = "Neighbor",
                        email = "stories@oneverandah.app",
                        service = "Mini Story idea",
                        message = idea.trim(),
                    )
                    idea = ""
                    Toaster.show(strings.storiesSuggestSuccess)
                } catch (t: Throwable) {
                    Toaster.show(t.message ?: strings.storiesSubmitError)
                } finally {
                    submittingIdea = false
                }
            }
        }
    }
}

@Composable
private fun StoryField(label: String, value: String, onChange: (String) -> Unit) {
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
private fun StoryArea(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        TextArea(value) {
            classes(Styles.textArea)
            placeholder(label)
            onInput { onChange(it.value) }
        }
    }
}
