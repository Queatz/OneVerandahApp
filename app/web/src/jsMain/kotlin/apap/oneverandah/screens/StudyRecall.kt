package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.i18n.UiStrings
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.ref
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.random.Random

internal enum class StudyRecallCardKind { WORD, PHRASE }

internal data class StudyRecallCard(
    val kind: StudyRecallCardKind,
    val prompt: String,
    val answer: String,
    val sentenceIndex: Int? = null,
    val tokenIndex: Int? = null,
)

internal enum class StudyRecallPhase { READY, PROMPT, REVEAL, COMPLETE }

internal data class StudyRecallProgress(
    val phase: StudyRecallPhase = StudyRecallPhase.READY,
    val cardIndex: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    val timedOut: Boolean = false,
)

internal const val STUDY_RECALL_WORD_REPETITIONS = 3
internal const val STUDY_RECALL_WORD_TIME_MS = 10_000
internal const val STUDY_RECALL_PHRASE_TIME_MS = 30_000

internal fun buildStudyRecallDeck(
    vocabulary: List<StudyVocabularyRow>,
    phrases: List<StudyExampleSentence>,
    random: Random = Random.Default,
): List<StudyRecallCard> {
    val words = vocabulary.mapNotNull { row ->
        val prompt = row.meaning.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        StudyRecallCard(
            kind = StudyRecallCardKind.WORD,
            prompt = prompt,
            answer = row.word.trim(),
            sentenceIndex = row.sentenceIndex,
            tokenIndex = row.tokenIndex,
        ).takeIf { it.answer.isNotEmpty() }
    }
    val repeatedWords = words.flatMap { card -> List(STUDY_RECALL_WORD_REPETITIONS) { card } }
        .shuffled(random)
    val phraseCards = phrases.mapNotNull { phrase ->
        val answer = phrase.text.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val prompt = phrase.translation.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        StudyRecallCard(kind = StudyRecallCardKind.PHRASE, prompt = prompt, answer = answer)
    }.shuffled(random)
    return repeatedWords + phraseCards
}

internal fun studyRecallTimeLimitMs(kind: StudyRecallCardKind): Int = when (kind) {
    StudyRecallCardKind.WORD -> STUDY_RECALL_WORD_TIME_MS
    StudyRecallCardKind.PHRASE -> STUDY_RECALL_PHRASE_TIME_MS
}

internal fun startStudyRecall(progress: StudyRecallProgress): StudyRecallProgress =
    if (progress.phase == StudyRecallPhase.READY) progress.copy(phase = StudyRecallPhase.PROMPT) else progress

internal fun revealStudyRecallAnswer(progress: StudyRecallProgress): StudyRecallProgress =
    if (progress.phase == StudyRecallPhase.PROMPT) progress.copy(phase = StudyRecallPhase.REVEAL) else progress

internal fun gradeStudyRecall(
    progress: StudyRecallProgress,
    deckSize: Int,
    correct: Boolean,
): StudyRecallProgress {
    if (progress.phase != StudyRecallPhase.REVEAL || progress.timedOut) return progress
    return progress.copy(
        phase = nextStudyRecallPhase(progress.cardIndex + 1, deckSize),
        cardIndex = progress.cardIndex + 1,
        correct = progress.correct + if (correct) 1 else 0,
        answered = progress.answered + 1,
        timedOut = false,
    )
}

internal fun timeoutStudyRecall(progress: StudyRecallProgress): StudyRecallProgress =
    if (progress.phase == StudyRecallPhase.PROMPT) {
        progress.copy(phase = StudyRecallPhase.REVEAL, answered = progress.answered + 1, timedOut = true)
    } else {
        progress
    }

internal fun advanceTimedOutStudyRecall(
    progress: StudyRecallProgress,
    deckSize: Int,
): StudyRecallProgress {
    if (progress.phase != StudyRecallPhase.REVEAL || !progress.timedOut) return progress
    val nextIndex = progress.cardIndex + 1
    return progress.copy(
        phase = nextStudyRecallPhase(nextIndex, deckSize),
        cardIndex = nextIndex,
        timedOut = false,
    )
}

private fun nextStudyRecallPhase(nextIndex: Int, deckSize: Int): StudyRecallPhase =
    if (nextIndex >= deckSize) StudyRecallPhase.COMPLETE else StudyRecallPhase.PROMPT

@Composable
internal fun StudyRecallTab(
    session: StudySessionResponse,
    anyLoading: Boolean,
    strings: UiStrings,
) {
    val vocabulary = studyVocabularyRows(session.sentences)
    val phrases = remember(session.text, session.sentences, session.practiceSentences) {
        collectExpressPracticeSentences(session)
    }
    var shuffleNonce by remember(session.text) { mutableStateOf(0) }
    val deck = remember(session.text, vocabulary, phrases, shuffleNonce) {
        buildStudyRecallDeck(vocabulary, phrases)
    }
    var progress by remember(session.text, deck, shuffleNonce) { mutableStateOf(StudyRecallProgress()) }
    var remainingMs by remember(session.text, deck, shuffleNonce) { mutableStateOf(0) }
    val audio = remember(session.text, session.textLanguageTag) { mutableStateMapOf<String, String>() }
    var recallElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestFullscreen by rememberUpdatedState(isFullscreen)
    val latestElement by rememberUpdatedState(recallElement)
    val currentCard = deck.getOrNull(progress.cardIndex)
    val recallStrings = strings.chatAdventure.recall
    val hasPendingWords = session.sentences.any { sentence -> sentence.tokens.any { it.status == StudyTokenStatus.PENDING } }

    LaunchedEffect(progress.phase, progress.cardIndex, deck) {
        if (progress.phase != StudyRecallPhase.PROMPT) return@LaunchedEffect
        val card = currentCard ?: return@LaunchedEffect
        remainingMs = studyRecallTimeLimitMs(card.kind)
        while (remainingMs > 0) {
            delay(100)
            remainingMs = (remainingMs - 100).coerceAtLeast(0)
        }
        progress = timeoutStudyRecall(progress)
    }

    LaunchedEffect(progress.phase, progress.cardIndex, progress.timedOut) {
        if (progress.phase != StudyRecallPhase.REVEAL) return@LaunchedEffect
        val card = currentCard ?: return@LaunchedEffect
        val audioUrl = audio[card.answer] ?: try {
            loadStudySpeechDataUrl(SpeechRequest(card.answer, session.textLanguageTag))
                .also { audio[card.answer] = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (audioUrl != null) {
            playAudio(audioUrl, restart = true)
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
            stopActiveStudyAudio()
        }
    }

    Div(attrs = {
        classes(Styles.studyRecallShell)
        if (isFullscreen) classes(Styles.studyRecallFullscreen)
        ref { element ->
            recallElement = element
            onDispose { if (recallElement === element) recallElement = null }
        }
    }) {
        Div(attrs = { classes(Styles.studyRecallHeader) }) {
            Span(attrs = { classes(Styles.studyRecallTitle) }) { Text(recallStrings.tab) }
            Button(attrs = {
                classes(Styles.studyRecallIconButton)
                attr("type", "button")
                attr("aria-label", if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen)
                attr("aria-pressed", isFullscreen.toString())
                onClick {
                    if (isFullscreen) {
                        isFullscreen = false
                        exitWatchFullscreen()
                    } else {
                        recallElement?.let(::requestWatchFullscreen)
                    }
                }
            }) { Text(if (isFullscreen) "⤢" else "⛶") }
        }

        when {
            anyLoading || hasPendingWords -> {
                Div(attrs = { classes(Styles.studyPreparingMeanings) }) {
                    Span { Text(recallStrings.preparing) }
                }
            }
            deck.isEmpty() -> {
                Div(attrs = { classes(Styles.studyRecallBlock) }) {
                    P { Text(recallStrings.empty) }
                }
            }
            else -> Div(attrs = { classes(Styles.studyRecallBlock) }) {
                when (progress.phase) {
                    StudyRecallPhase.READY -> {
                        val firstCard = deck.first()
                        P(attrs = { classes(Styles.studyRecallPrompt) }) {
                            Text(if (firstCard.kind == StudyRecallCardKind.WORD) recallStrings.wordHint else recallStrings.phraseHint)
                        }
                        Button(attrs = {
                            classes(Styles.studyRecallButton)
                            attr("type", "button")
                            onClick { progress = startStudyRecall(progress) }
                        }) { Text(recallStrings.start) }
                    }
                    StudyRecallPhase.PROMPT, StudyRecallPhase.REVEAL -> {
                        val card = currentCard ?: return@Div
                        P(attrs = { classes(Styles.studyRecallProgress) }) {
                            Text(recallStrings.progress(progress.cardIndex + 1, deck.size))
                        }
                        P(attrs = { classes(Styles.studyRecallPrompt) }) {
                            Text(if (card.kind == StudyRecallCardKind.WORD) recallStrings.wordHint else recallStrings.phraseHint)
                        }
                        if (card.kind == StudyRecallCardKind.WORD) {
                            card.sentenceIndex?.let { sentenceIndex ->
                                card.tokenIndex?.let { tokenIndex ->
                                    session.sentences.getOrNull(sentenceIndex)?.tokens?.getOrNull(tokenIndex)?.imageUrl
                                        ?.takeIf(String::isNotBlank)?.let { imageUrl ->
                                            Img(src = imageUrl, alt = strings.chatStudyWordImageAlt, attrs = {
                                                classes(Styles.studyRecallImage)
                                            })
                                        }
                                }
                            }
                        }
                        P(attrs = { classes(Styles.studyRecallSource) }) { Text(card.prompt) }
                        if (progress.phase == StudyRecallPhase.PROMPT) {
                            P(attrs = { classes(Styles.studyRecallTimer) }) {
                                Text(recallStrings.timeLeft((remainingMs + 999) / 1_000))
                            }
                            Button(attrs = {
                                classes(Styles.studyRecallButton)
                                attr("type", "button")
                                onClick { progress = revealStudyRecallAnswer(progress) }
                            }) { Text(recallStrings.reveal) }
                        } else {
                            if (progress.timedOut) {
                                P(attrs = { classes(Styles.studyRecallTimeUp) }) { Text(recallStrings.timeUp) }
                            }
                            P(attrs = { classes(Styles.studyRecallAnswerLabel) }) { Text(recallStrings.answerLabel) }
                            P(attrs = { classes(Styles.studyRecallAnswer) }) { Text(card.answer) }
                            if (progress.timedOut) {
                                Button(attrs = {
                                    classes(Styles.studyRecallButton)
                                    attr("type", "button")
                                    onClick { progress = advanceTimedOutStudyRecall(progress, deck.size) }
                                }) { Text(recallStrings.next) }
                            } else {
                                Div(attrs = { classes(Styles.studyRecallActions) }) {
                                    Button(attrs = {
                                        classes(Styles.studyRecallButton, Styles.studyRecallIncorrect)
                                        attr("type", "button")
                                        onClick { progress = gradeStudyRecall(progress, deck.size, correct = false) }
                                    }) {
                                        Span { Text("✕") }
                                        Text(recallStrings.incorrect)
                                    }
                                    Button(attrs = {
                                        classes(Styles.studyRecallButton, Styles.studyRecallCorrect)
                                        attr("type", "button")
                                        onClick { progress = gradeStudyRecall(progress, deck.size, correct = true) }
                                    }) {
                                        Span { Text("✓") }
                                        Text(recallStrings.correct)
                                    }
                                }
                            }
                        }
                    }
                    StudyRecallPhase.COMPLETE -> {
                        P(attrs = { classes(Styles.studyRecallPrompt) }) { Text(recallStrings.finished) }
                        P(attrs = { classes(Styles.studyRecallScore) }) {
                            Text(recallStrings.score(progress.correct, deck.size))
                        }
                        Button(attrs = {
                            classes(Styles.studyRecallButton)
                            attr("type", "button")
                            onClick { shuffleNonce += 1 }
                        }) { Text(recallStrings.retake) }
                    }
                }
            }
        }
    }
}
