package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChatApi
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StoryApi
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.StoryProgressStore
import apap.oneverandah.stories.Story
import apap.oneverandah.stories.StoryLogic
import apap.oneverandah.stories.StoryPage
import kotlinx.browser.document
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLInputElement

private enum class ReaderPhase { COVER, PAGE, END }

@Composable
fun StoryReaderScreen(
    storyId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    var story by remember(storyId) { mutableStateOf<Story?>(null) }
    var loading by remember(storyId) { mutableStateOf(true) }
    var phase by remember(storyId) { mutableStateOf(ReaderPhase.COVER) }
    var pageIndex by remember(storyId) { mutableStateOf(0) }
    var learningMode by remember(storyId) { mutableStateOf(StoryProgressStore.learningMode) }
    var selectedToken by remember(storyId, pageIndex) { mutableStateOf<StudyToken?>(null) }

    LaunchedEffect(storyId) {
        loading = true
        story = StoryApi.fetchStory(storyId)
        loading = false
        phase = ReaderPhase.COVER
    }

    DisposableEffect(storyId) {
        onDispose { stopStoryAudio() }
    }

    val current = story
    Scaffold(
        title = current?.name ?: strings.storiesTitle,
        onBack = {
            when (phase) {
                ReaderPhase.COVER -> onBack()
                else -> {
                    stopStoryAudio()
                    phase = ReaderPhase.COVER
                }
            }
        },
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "story-$storyId-$phase-$pageIndex",
        footer = if (current != null && phase != ReaderPhase.COVER && current.pages.isNotEmpty()) {
            {
                Div(attrs = { classes(Styles.storyReaderNav) }) {
                    Button(attrs = {
                        classes(Styles.btnText)
                        attr("type", "button")
                        attr("aria-label", strings.storiesPreviousPage)
                        if (phase == ReaderPhase.PAGE && StoryLogic.previousPageIndex(pageIndex) == null) {
                            attr("disabled", "")
                        }
                        onClick {
                            if (phase == ReaderPhase.END) {
                                pageIndex = current.pages.lastIndex
                                phase = ReaderPhase.PAGE
                            } else {
                                val previous = StoryLogic.previousPageIndex(pageIndex)
                                if (previous != null) {
                                    pageIndex = previous
                                    StoryProgressStore.savePage(storyId, previous)
                                } else {
                                    phase = ReaderPhase.COVER
                                }
                            }
                        }
                    }) { MaterialSymbol("arrow_back") }
                    PrimaryButton(
                        label = if (phase == ReaderPhase.END) strings.storiesReadAgain else strings.storiesContinue,
                        icon = "auto_stories",
                    ) {
                        if (phase == ReaderPhase.END) {
                            StoryProgressStore.clear(storyId)
                            pageIndex = 0
                            phase = ReaderPhase.PAGE
                        } else {
                            val next = StoryLogic.nextPageIndex(pageIndex, current.pages.size)
                            if (next == null) {
                                StoryProgressStore.savePage(storyId, current.pages.size)
                                phase = ReaderPhase.END
                            } else {
                                pageIndex = next
                                StoryProgressStore.savePage(storyId, next)
                            }
                        }
                    }
                }
            }
        } else null,
    ) {
        when {
            loading -> P { Text(strings.storiesLoading) }
            current == null -> P { Text(strings.storiesEmpty) }
            phase == ReaderPhase.COVER -> StoryCover(
                story = current,
                learningMode = learningMode,
                onLearningMode = {
                    learningMode = it
                    StoryProgressStore.learningMode = it
                },
                onContinue = {
                    val resume = StoryProgressStore.resumeIndex(storyId, current.pages.size)
                    if (current.pages.isEmpty() || StoryLogic.isFinished(resume, current.pages.size)) {
                        phase = if (current.pages.isEmpty()) ReaderPhase.END else ReaderPhase.PAGE
                        pageIndex = 0
                    } else {
                        pageIndex = resume
                        phase = ReaderPhase.PAGE
                    }
                },
                onReadAgain = {
                    StoryProgressStore.clear(storyId)
                    pageIndex = 0
                    phase = if (current.pages.isEmpty()) ReaderPhase.END else ReaderPhase.PAGE
                },
            )
            phase == ReaderPhase.END -> {
                Div(attrs = { classes(Styles.storiesSoon) }) {
                    MaterialSymbol("menu_book")
                    H2 { Text(strings.storiesTheEnd) }
                }
            }
            else -> {
                val page = current.pages.getOrNull(pageIndex)
                if (page == null) {
                    P { Text(strings.storiesTheEnd) }
                } else {
                    StoryPageView(
                        story = current,
                        page = page,
                        pageIndex = pageIndex,
                        learningMode = learningMode,
                        selectedToken = selectedToken,
                        onSelectToken = { selectedToken = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryCover(
    story: Story,
    learningMode: Boolean,
    onLearningMode: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onReadAgain: () -> Unit,
) {
    val strings = Localization.strings
    val hasProgress = StoryProgressStore.pageIndex(story.id) > 0
    if (!story.coverImageUrl.isNullOrBlank()) {
        Img(src = story.coverImageUrl!!, alt = story.name, attrs = {
            classes(Styles.storyReaderCover)
        })
    }
    if (story.category.isNotBlank()) {
        P(attrs = { classes(Styles.storyCoverCategory) }) { Text(story.category) }
    }
    Label(attrs = {
        classes(Styles.storyLearningRow)
        attr("role", "checkbox")
        attr("aria-checked", learningMode.toString())
        onClick { onLearningMode(!learningMode) }
    }) {
        org.jetbrains.compose.web.dom.Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox) {
            if (learningMode) attr("checked", "true")
            onChange {
                val el = it.nativeEvent.target as? HTMLInputElement
                onLearningMode(el?.checked == true)
            }
        }
        Text(strings.storiesLearningMode)
    }
    PrimaryButton(label = strings.storiesContinue, icon = "play_arrow", onClick = onContinue)
    if (hasProgress) {
        Button(attrs = {
            classes(Styles.btnText)
            attr("type", "button")
            onClick { onReadAgain() }
        }) { Text(strings.storiesReadAgain) }
    }
}

@Composable
private fun StoryPageView(
    story: Story,
    page: StoryPage,
    pageIndex: Int,
    learningMode: Boolean,
    selectedToken: StudyToken?,
    onSelectToken: (StudyToken?) -> Unit,
) {
    val strings = Localization.strings
    LaunchedEffect(story.id, page.id, page.text) {
        speakStoryText(page.text, story.languageTag)
    }

    P(attrs = { classes(Styles.storyPageMeta) }) {
        Text(strings.storiesPageOf(pageIndex + 1, story.pages.size))
    }
    if (!page.photoUrl.isNullOrBlank()) {
        Img(src = page.photoUrl!!, alt = "", attrs = { classes(Styles.storyPagePhoto) })
    }

    val session = page.session
    if (learningMode && session != null && session.sentences.isNotEmpty()) {
        Div(attrs = { classes(Styles.languageSentence) }) {
            session.sentences.forEach { sentence ->
                sentence.tokens.forEach { token ->
                    if (token.status == StudyTokenStatus.PLAIN) {
                        Span(attrs = { classes(Styles.studyStoryWord) }) { Text(token.text) }
                    } else {
                        Button(attrs = {
                            classes(Styles.languageToken)
                            if (selectedToken == token) classes(Styles.languageTokenSelected)
                            attr("type", "button")
                            onClick { onSelectToken(if (selectedToken == token) null else token) }
                        }) { Text(token.text) }
                    }
                    Text(" ")
                }
            }
        }
        selectedToken?.let { token ->
            Div(attrs = { classes(Styles.wordDefinition) }) {
                Span(attrs = { classes(Styles.wordDefinitionTitle) }) { Text(token.text) }
                if (token.literalMeaning.isNotBlank()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyLiteral) }
                    P { Text(token.literalMeaning) }
                }
                if (token.contextualMeaning.isNotBlank()) {
                    Span(attrs = { classes(Styles.wordDefinitionLabel) }) { Text(strings.chatStudyContext) }
                    P { Text(token.contextualMeaning) }
                }
            }
        }
    } else {
        P(attrs = { classes(Styles.storyPageText) }) { Text(page.text) }
    }
}

private var activeStoryAudio: HTMLAudioElement? = null

private fun stopStoryAudio() {
    try {
        activeStoryAudio?.pause()
    } catch (_: Throwable) {
    }
    activeStoryAudio = null
}

private suspend fun speakStoryText(text: String, languageTag: String) {
    val spoken = text.trim()
    if (spoken.isEmpty()) return
    stopStoryAudio()
    val url = runCatching {
        ChatApi.speechDataUrl(SpeechRequest(spoken, languageTag))
    }.getOrNull() ?: return
    val element = document.createElement("audio") as HTMLAudioElement
    activeStoryAudio = element
    element.src = url
    runCatching { element.play() }
}
