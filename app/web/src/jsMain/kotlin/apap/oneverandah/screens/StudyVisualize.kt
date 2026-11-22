package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.i18n.UiStrings
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.ref
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.random.Random

internal const val STUDY_VISUALIZE_REPETITIONS = 3
internal const val STUDY_VISUALIZE_MAX_CHOICES = 8
internal const val STUDY_VISUALIZE_IMAGE_DELAY_MS = 3_000

internal data class StudyVisualizeWord(
    val text: String,
    val imageUrl: String? = null,
)

internal enum class StudyVisualizePhase { READY, IMAGE, SETUP, CHOOSING, COMPLETE }

internal data class StudyVisualizeChoice(
    val word: StudyVisualizeWord,
    val xPercent: Double,
    val yPercent: Double,
)

internal data class StudyVisualizeProgress(
    val phase: StudyVisualizePhase = StudyVisualizePhase.READY,
    val wordIndex: Int = 0,
    val choices: List<StudyVisualizeChoice> = emptyList(),
    val setupNonce: Int = 0,
)

internal fun buildStudyVisualizeDeck(
    words: List<StudyVisualizeWord>,
    random: Random = Random.Default,
): List<StudyVisualizeWord> {
    val uniqueWords = words.mapNotNull { word ->
        word.copy(text = word.text.trim()).takeIf { it.text.isNotEmpty() }
    }.distinctBy { it.text.lowercase() }
    return uniqueWords.flatMap { word -> List(STUDY_VISUALIZE_REPETITIONS) { word } }.shuffled(random)
}

internal fun chooseStudyVisualizeWords(
    words: List<StudyVisualizeWord>,
    correctWord: StudyVisualizeWord,
    random: Random = Random.Default,
): List<StudyVisualizeWord> {
    val uniqueWords = (words + correctWord).mapNotNull { word ->
        word.copy(text = word.text.trim()).takeIf { it.text.isNotEmpty() }
    }.distinctBy { it.text.lowercase() }
    val correctKey = visualizeWordKey(correctWord.text)
    val correct = uniqueWords.firstOrNull { visualizeWordKey(it.text) == correctKey } ?: return emptyList()
    val distractors = uniqueWords
        .filterNot { visualizeWordKey(it.text) == correctKey }
        .shuffled(random)
        .take(STUDY_VISUALIZE_MAX_CHOICES - 1)
    return (distractors + correct).shuffled(random)
}

internal fun startStudyVisualize(progress: StudyVisualizeProgress): StudyVisualizeProgress =
    if (progress.phase == StudyVisualizePhase.READY) progress.copy(phase = StudyVisualizePhase.IMAGE) else progress

internal fun setupStudyVisualizeChoices(
    progress: StudyVisualizeProgress,
    choices: List<StudyVisualizeChoice>,
): StudyVisualizeProgress =
    if (progress.phase == StudyVisualizePhase.SETUP) {
        progress.copy(phase = StudyVisualizePhase.CHOOSING, choices = choices)
    } else {
        progress
    }

internal fun answerStudyVisualize(
    progress: StudyVisualizeProgress,
    deckSize: Int,
    selectedWord: String,
    correctWord: String,
): StudyVisualizeProgress {
    if (progress.phase != StudyVisualizePhase.CHOOSING) return progress
    return if (visualizeWordKey(selectedWord) == visualizeWordKey(correctWord)) {
        val nextIndex = progress.wordIndex + 1
        progress.copy(
            phase = if (nextIndex >= deckSize) StudyVisualizePhase.COMPLETE else StudyVisualizePhase.IMAGE,
            wordIndex = nextIndex,
            choices = emptyList(),
        )
    } else {
        progress.copy(phase = StudyVisualizePhase.SETUP, setupNonce = progress.setupNonce + 1)
    }
}

private fun visualizeWordKey(text: String): String = text.trim().lowercase()

@Composable
internal fun StudyVisualizeTab(
    session: StudySessionResponse,
    anyLoading: Boolean,
    autoStart: Boolean,
    strings: UiStrings,
) {
    val vocabulary = studyVocabularyRows(session.sentences)
    val words = remember(vocabulary, session.sentences) {
        vocabulary.mapNotNull { row ->
            val sentence = session.sentences.getOrNull(row.sentenceIndex) ?: return@mapNotNull null
            val token = sentence.tokens.getOrNull(row.tokenIndex)
            StudyVisualizeWord(
                text = row.word,
                imageUrl = token?.imageUrl?.takeIf(String::isNotBlank)
                    ?: sentence.imageUrl?.takeIf(String::isNotBlank),
            )
        }
    }
    var shuffleNonce by remember(session.text) { mutableStateOf(0) }
    val deckOrder = remember(session.text, words.map { visualizeWordKey(it.text) }, shuffleNonce) {
        buildStudyVisualizeDeck(words.map { it.copy(imageUrl = null) })
    }
    val deck = deckOrder.map { orderedWord ->
        words.firstOrNull { visualizeWordKey(it.text) == visualizeWordKey(orderedWord.text) } ?: orderedWord
    }
    var progress by remember(session.text, deckOrder, shuffleNonce) { mutableStateOf(StudyVisualizeProgress()) }
    var visualizeElement by remember { mutableStateOf<HTMLElement?>(null) }
    val preloadedImages = remember { mutableListOf<HTMLImageElement>() }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestFullscreen by rememberUpdatedState(isFullscreen)
    val latestElement by rememberUpdatedState(visualizeElement)
    val currentWord = deck.getOrNull(progress.wordIndex)
    val copy = strings.chatVisualize
    val hasPendingWords = session.sentences.any { sentence -> sentence.tokens.any { it.status == StudyTokenStatus.PENDING } }
    val preloadUrls = deck.drop(progress.wordIndex).take(4)
        .mapNotNull { it.imageUrl }
        .distinct()

    LaunchedEffect(autoStart, anyLoading, hasPendingWords, progress.phase, deckOrder) {
        if (autoStart && !anyLoading && !hasPendingWords && deckOrder.isNotEmpty() && progress.phase == StudyVisualizePhase.READY) {
            VisualizeSfx.unlock()
            progress = startStudyVisualize(progress)
        }
    }

    LaunchedEffect(preloadUrls) {
        preloadedImages.clear()
        preloadUrls.forEach { url ->
            val image = document.createElement("img") as HTMLImageElement
            preloadedImages += image
            image.src = url
        }
    }

    LaunchedEffect(progress.phase, progress.wordIndex, progress.setupNonce) {
        when (progress.phase) {
            StudyVisualizePhase.IMAGE -> {
                delay(STUDY_VISUALIZE_IMAGE_DELAY_MS.toLong())
                if (progress.phase == StudyVisualizePhase.IMAGE) {
                    progress = progress.copy(phase = StudyVisualizePhase.SETUP)
                }
            }
            StudyVisualizePhase.SETUP -> {
                if (progress.choices.isNotEmpty()) delay(180)
                val correct = deck.getOrNull(progress.wordIndex) ?: return@LaunchedEffect
                val options = chooseStudyVisualizeWords(words, correct)
                val choices = layoutStudyVisualizeChoices(options)
                VisualizeSfx.sceneSetup(choices.size)
                progress = setupStudyVisualizeChoices(progress, choices)
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == "Escape" || key == "Esc") && latestFullscreen) {
                isFullscreen = false
                exitWatchFullscreen()
            }
        }
        val onFullscreen: (Event) -> Unit = {
            val current = currentFullscreenElement()
            val element = latestElement
            when {
                element != null && current === element -> isFullscreen = true
                latestFullscreen && current == null -> isFullscreen = false
            }
        }
        window.addEventListener("keydown", onKey)
        document.addEventListener("fullscreenchange", onFullscreen)
        document.addEventListener("webkitfullscreenchange", onFullscreen)
        onDispose {
            window.removeEventListener("keydown", onKey)
            document.removeEventListener("fullscreenchange", onFullscreen)
            document.removeEventListener("webkitfullscreenchange", onFullscreen)
            if (latestElement != null && currentFullscreenElement() === latestElement) exitWatchFullscreen()
        }
    }

    Div(attrs = {
        classes(Styles.studyVisualizeShell)
        if (isFullscreen) classes(Styles.studyVisualizeFullscreen)
        ref { element ->
            visualizeElement = element
            onDispose { if (visualizeElement === element) visualizeElement = null }
        }
    }) {
        Div(attrs = { classes(Styles.studyVisualizeHeader) }) {
            Span(attrs = { classes(Styles.studyVisualizeTitle) }) { Text(copy.tab) }
            if (progress.phase in listOf(StudyVisualizePhase.IMAGE, StudyVisualizePhase.SETUP, StudyVisualizePhase.CHOOSING)) {
                Span(attrs = { classes(Styles.studyVisualizeProgress) }) {
                    Text(copy.progress((progress.wordIndex + 1).coerceAtMost(deck.size), deck.size))
                }
            }
            Button(attrs = {
                classes(Styles.studyVisualizeIconButton)
                attr("type", "button")
                attr("aria-label", if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen)
                attr("aria-pressed", isFullscreen.toString())
                onClick {
                    if (isFullscreen) {
                        isFullscreen = false
                        exitWatchFullscreen()
                    } else {
                        visualizeElement?.let(::requestWatchFullscreen)
                    }
                }
            }) { Text(if (isFullscreen) "⤢" else "⛶") }
        }

        when {
            anyLoading || hasPendingWords -> {
                Div(attrs = { classes(Styles.studyVisualizeNotice) }) { Text(copy.preparing) }
            }
            deck.isEmpty() -> {
                Div(attrs = { classes(Styles.studyVisualizeNotice) }) { Text(copy.empty) }
            }
            progress.phase == StudyVisualizePhase.READY -> {
                Div(attrs = { classes(Styles.studyVisualizeWelcome) }) {
                    P(attrs = { classes(Styles.studyVisualizeHint) }) { Text(copy.tapHint) }
                    if (!autoStart) {
                        Button(attrs = {
                            classes(Styles.studyVisualizePrimary)
                            attr("type", "button")
                            onClick {
                                VisualizeSfx.unlock()
                                progress = startStudyVisualize(progress)
                            }
                        }) { Text(copy.start) }
                    }
                }
            }
            progress.phase == StudyVisualizePhase.COMPLETE -> {
                Div(attrs = { classes(Styles.studyVisualizeComplete) }) {
                    P(attrs = { classes(Styles.studyVisualizeCompleteTitle) }) { Text(copy.complete) }
                    Button(attrs = {
                        classes(Styles.studyVisualizePrimary)
                        attr("type", "button")
                        onClick { shuffleNonce += 1 }
                    }) { Text(copy.restart) }
                }
                Div(attrs = {
                    classes(Styles.studyVisualizeGallery)
                    attr("aria-label", copy.collection)
                }) {
                    words.forEach { word ->
                        Div(attrs = { classes(Styles.studyVisualizeGalleryCard) }) {
                            if (word.imageUrl != null) {
                                Img(src = word.imageUrl, alt = "", attrs = {
                                    classes(Styles.studyVisualizeGalleryImage)
                                })
                            } else {
                                Div(attrs = { classes(Styles.studyVisualizeGalleryPlaceholder) }) { Text("✦") }
                            }
                            Span(attrs = { classes(Styles.studyVisualizeGalleryWord) }) { Text(word.text) }
                        }
                    }
                }
            }
            else -> {
                Div(attrs = {
                    classes(Styles.studyVisualizeStage)
                    attr("role", "group")
                    attr("aria-label", copy.tapHint)
                }) {
                    currentWord?.imageUrl?.let { imageUrl ->
                        key("${progress.wordIndex}-$imageUrl") {
                            Img(src = imageUrl, alt = "", attrs = {
                                classes(Styles.studyVisualizeBackground)
                            })
                        }
                    }
                    Div(attrs = { classes(Styles.studyVisualizeVignette) })
                    if (progress.phase == StudyVisualizePhase.IMAGE) {
                        Span(attrs = { classes(Styles.studyVisualizeSceneLoading) }) { Text(copy.sceneLoading) }
                    }
                    progress.choices.forEachIndexed { index, choice ->
                        key("${progress.setupNonce}-${visualizeWordKey(choice.word.text)}") {
                            Button(attrs = {
                                classes(Styles.studyVisualizeChoice)
                                if (progress.phase != StudyVisualizePhase.CHOOSING) classes(Styles.studyVisualizeChoiceLeaving)
                                attr("type", "button")
                                attr("style", "left:${choice.xPercent}%;top:${choice.yPercent}%;--float-delay:-${index * 240}ms;--reveal-delay:${index * 65}ms")
                                attr("aria-label", choice.word.text)
                                if (progress.phase != StudyVisualizePhase.CHOOSING) attr("disabled", "")
                                onClick {
                                    val correct = currentWord ?: return@onClick
                                    VisualizeSfx.unlock()
                                    val isCorrect = visualizeWordKey(choice.word.text) == visualizeWordKey(correct.text)
                                    if (isCorrect) VisualizeSfx.correct() else VisualizeSfx.incorrect()
                                    progress = answerStudyVisualize(
                                        progress = progress,
                                        deckSize = deck.size,
                                        selectedWord = choice.word.text,
                                        correctWord = correct.text,
                                    )
                                }
                            }) { Text(choice.word.text) }
                        }
                    }
                }
            }
        }
    }
}

private fun layoutStudyVisualizeChoices(words: List<StudyVisualizeWord>): List<StudyVisualizeChoice> {
    val slots = (0..1).flatMap { row ->
        (0..3).map { column -> (column to row) }
    }.shuffled()
    return words.take(STUDY_VISUALIZE_MAX_CHOICES).mapIndexed { index, word ->
        val (column, row) = slots[index]
        StudyVisualizeChoice(
            word = word,
            xPercent = (12.5 + column * 25 + Random.nextDouble(-0.5, 0.5)).coerceIn(10.0, 90.0),
            yPercent = (31.0 + row * 38 + Random.nextDouble(-2.0, 2.0)).coerceIn(25.0, 75.0),
        )
    }
}

private object VisualizeSfx {
    private var context: dynamic = null

    fun unlock() {
        runCatching {
            if (context == null) context = newVisualizeAudioContext()
            context.resume()
        }
    }

    private fun tone(frequency: Double, durationMs: Int, volume: Double, startDelayMs: Int = 0) {
        runCatching {
            if (context == null) context = newVisualizeAudioContext()
            val oscillator = context.createOscillator()
            val gain = context.createGain()
            val now = (context.currentTime as Double) + startDelayMs / 1_000.0
            oscillator.type = "sine"
            oscillator.frequency.setValueAtTime(frequency, now)
            gain.gain.setValueAtTime(volume, now)
            gain.gain.exponentialRampToValueAtTime(0.001, now + durationMs / 1_000.0)
            oscillator.connect(gain)
            gain.connect(context.destination)
            oscillator.start(now)
            oscillator.stop(now + durationMs / 1_000.0)
        }
    }

    fun correct() = tone(660.0, 150, 0.035)
    fun incorrect() = tone(220.0, 170, 0.025)
    fun sceneSetup(count: Int) {
        repeat(count.coerceIn(0, STUDY_VISUALIZE_MAX_CHOICES)) { index ->
            tone(410.0 + index * 24.0, 140, 0.012, startDelayMs = index * 75)
        }
    }
}

private fun newVisualizeAudioContext(): dynamic = js("new (window.AudioContext || window.webkitAudioContext)()")