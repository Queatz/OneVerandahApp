package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StudyBreakdownProgress
import apap.oneverandah.api.StudyBreakdownSentenceProgress
import apap.oneverandah.api.StudyChunk
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.advanceStudyBreakdownBackchain
import apap.oneverandah.api.advanceStudyBreakdownShadow
import apap.oneverandah.api.alignStudyChunks
import apap.oneverandah.api.studyBreakdownSentenceAhead
import apap.oneverandah.api.studyBreakdownSuffixes
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.i18n.UiStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date

private const val BREAKDOWN_REPS = 5
private const val BREAKDOWN_SHADOW_SLOW_PLAYBACK_RATE = 0.6
private const val BREAKDOWN_SHADOW_MEDIUM_PLAYBACK_RATE = 0.8
private const val BREAKDOWN_YOUR_TURN_MULTIPLIER = 1.5
private const val BREAKDOWN_YOUR_TURN_FALLBACK_MS = 2_000L

@Composable
internal fun StudyBreakdown(
    session: StudySessionResponse,
    sentenceIndex: Int,
    progress: StudyBreakdownProgress?,
    strings: UiStrings,
    onProgressChange: (StudyBreakdownProgress) -> Unit,
    onRetryWordImage: (sentenceIndex: Int, tokenIndex: Int) -> Unit,
    wordImageFailed: Map<String, Boolean>,
    sentenceImageFailed: Boolean,
    messageId: String?,
    onDismiss: () -> Unit,
) {
    val sentence = session.sentences.getOrNull(sentenceIndex) ?: return
    val chunks = sentence.breakdownChunks()
    val suffixes = sentence.breakdownSuffixes.ifEmpty {
        studyBreakdownSuffixes(sentence.text, sentence.tokens.map { it.text }, chunks)
    }
    val scope = rememberCoroutineScope()
    val audioUrls = remember(session.textLanguageTag, sentence.text) { mutableStateMapOf<String, String>() }
    var drillProgress by remember(sentenceIndex) {
        mutableStateOf(progress?.sentences?.get(sentenceIndex) ?: StudyBreakdownSentenceProgress())
    }
    var savedProgress by remember(sentenceIndex) { mutableStateOf(progress ?: StudyBreakdownProgress()) }
    var selectedChunkIndex by remember(sentenceIndex) { mutableStateOf(0) }
    var expandedChunkIndex by remember(sentenceIndex) { mutableStateOf<Int?>(null) }
    var answerVisible by remember(sentenceIndex) { mutableStateOf(false) }
    var userTurn by remember(sentenceIndex) { mutableStateOf(false) }

    LaunchedEffect(progress) {
        val incoming = progress ?: return@LaunchedEffect
        val incomingSentence = incoming.sentences[sentenceIndex] ?: return@LaunchedEffect
        val current = drillProgress
        val accept = when {
            incomingSentence.restartedAt > current.restartedAt -> true
            incomingSentence.restartedAt < current.restartedAt -> false
            incomingSentence.completed && !current.completed -> true
            current.completed && !incomingSentence.completed -> false
            studyBreakdownSentenceAhead(incomingSentence, current) -> true
            incomingSentence == current -> incoming.updatedAt > savedProgress.updatedAt
            else -> false
        }
        if (accept) {
            savedProgress = incoming
            drillProgress = incomingSentence
        }
    }

    suspend fun audioUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        audioUrls[trimmed]?.let { return it }
        return runCatching {
            loadStudySpeechDataUrl(SpeechRequest(trimmed, session.textLanguageTag))
        }.getOrNull()?.also { audioUrls[trimmed] = it }
    }

    fun saveProgress(next: StudyBreakdownSentenceProgress) {
        drillProgress = next
        val updated = savedProgress.copy(
            sentences = savedProgress.sentences + (sentenceIndex to next),
            updatedAt = Date.now().toLong(),
        )
        savedProgress = updated
        onProgressChange(updated)
    }

    fun restartDrill() {
        answerVisible = false
        selectedChunkIndex = 0
        expandedChunkIndex = null
        userTurn = false
        stopActiveStudyAudio()
        saveProgress(StudyBreakdownSentenceProgress(restartedAt = Date.now().toLong()))
    }

    val backchainSuffix = suffixes.getOrNull(
        drillProgress.backchainRound.coerceIn(0, chunks.lastIndex.coerceAtLeast(0)),
    ) ?: buildSuffix(chunks, drillProgress.backchainRound)

    LaunchedEffect(drillProgress.step, drillProgress.backchainRound, drillProgress.backchainRep, backchainSuffix) {
        userTurn = false
        if (drillProgress.step != 2 || chunks.isEmpty() || backchainSuffix.isBlank()) return@LaunchedEffect
        val url = audioUrl(backchainSuffix) ?: return@LaunchedEffect
        val (playbackEnded, durationMs) = playBreakdownClip(url)
        if (!playbackEnded) return@LaunchedEffect
        userTurn = true
        delay(breakdownYourTurnDelayMs(durationMs))
    }

    LaunchedEffect(drillProgress.step, drillProgress.shadowRep, sentence.text) {
        userTurn = false
        if (drillProgress.step != 3) return@LaunchedEffect
        val url = audioUrl(sentence.text) ?: return@LaunchedEffect
        val playbackRate = breakdownShadowPlaybackRate(drillProgress.shadowRep)
        val (playbackEnded, durationMs) = playBreakdownClip(url, playbackRate)
        if (!playbackEnded) return@LaunchedEffect
        userTurn = true
        try {
            delay(breakdownYourTurnDelayMs(durationMs, playbackRate))
            saveProgress(advanceStudyBreakdownShadow(drillProgress))
        } finally {
            userTurn = false
        }
    }

    DisposableEffect(sentenceIndex) {
        onDispose { stopActiveStudyAudio() }
    }

    val stepLabels = listOf(
        strings.chatStudyBreakdownDissect,
        strings.chatStudyBreakdownBackchain,
        strings.chatStudyBreakdownShadow,
        strings.chatStudyBreakdownRecall,
    )
    val currentRound = if (chunks.isNotEmpty()) drillProgress.backchainRound.coerceIn(0, chunks.lastIndex) else 0
    val displayedSuffix = suffixes.getOrNull(currentRound) ?: buildSuffix(chunks, currentRound)
    val newChunkIndex = (chunks.lastIndex - currentRound).coerceAtLeast(0)
    val selectedChunk = chunks.getOrNull(selectedChunkIndex)
    val selectedChunkExpanded = expandedChunkIndex == selectedChunkIndex

    BottomDialog(
        visible = true,
        closeLabel = strings.close,
        onDismiss = onDismiss,
        expanded = true,
        footer = {
            Div(attrs = { classes(Styles.studyBreakdownFooter) }) {
                when {
                    drillProgress.completed -> {
                        BreakdownAction(label = strings.chatStudyBreakdownRestart, onClick = ::restartDrill)
                        BreakdownAction(label = strings.close, onClick = onDismiss)
                    }
                    drillProgress.step == 1 -> BreakdownAction(
                        label = strings.chatStudyBreakdownGotIt,
                        enabled = chunks.isNotEmpty(),
                        onClick = {
                            saveProgress(drillProgress.copy(step = 2, backchainRound = 0, backchainRep = 0))
                        },
                    )
                    drillProgress.step == 2 -> BreakdownAction(
                        strings.chatStudyBreakdownContinue,
                        onClick = { saveProgress(advanceStudyBreakdownBackchain(drillProgress, chunks.size)) },
                    )
                    drillProgress.step == 4 && !answerVisible -> BreakdownAction(
                        strings.chatStudyBreakdownCheck,
                        onClick = {
                            answerVisible = true
                            scope.launch { audioUrl(sentence.text)?.let { playAudio(it, restart = true) } }
                        },
                    )
                    drillProgress.step == 4 -> {
                        BreakdownAction(
                            strings.chatStudyBreakdownNotYet,
                            onClick = { answerVisible = false },
                        )
                        BreakdownAction(
                            strings.chatStudyBreakdownGotIt,
                            onClick = {
                                answerVisible = false
                                saveProgress(drillProgress.copy(step = 4, completed = true))
                            },
                        )
                    }
                }
            }
        },
    ) {
        Div(attrs = { classes(Styles.studyBreakdownSheet) }) {
            Div(attrs = { classes(Styles.studyBreakdownSteps) }) {
                stepLabels.forEachIndexed { index, label ->
                    Span(attrs = {
                        classes(Styles.studyBreakdownStep)
                        if (drillProgress.step == index + 1 || drillProgress.completed) {
                            classes(Styles.studyBreakdownStepActive)
                        }
                    }) {
                        Text("${index + 1}. $label")
                    }
                }
            }

            when {
                drillProgress.completed -> {
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text(strings.chatStudyBreakdownComplete)
                    }
                }
                drillProgress.step == 1 -> {
                    if (chunks.isEmpty()) {
                        P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                            Text(strings.chatStudyBreakdownLoading)
                        }
                    } else {
                        Div(attrs = { classes(Styles.studyBreakdownActions) }) {
                            chunks.forEachIndexed { index, chunk ->
                                BreakdownChunkButton(
                                    chunk = chunk,
                                    active = selectedChunkIndex == index,
                                    onClick = {
                                        selectedChunkIndex = index
                                        expandedChunkIndex = null
                                        scope.launch { audioUrl(chunk.text)?.let { playAudio(it, restart = true) } }
                                    },
                                )
                            }
                        }
                        selectedChunk?.let { chunk ->
                            if (chunk.gloss.isNotBlank()) {
                                P(attrs = { classes(Styles.studyBreakdownMeaning) }) { Text(chunk.gloss) }
                            }
                            if (chunk.grammarNote.isNotBlank() && !chunk.grammarNote.isPlaceholderGrammarNote()) {
                                P(attrs = { classes(Styles.studyBreakdownPrompt) }) { Text(chunk.grammarNote) }
                            }
                            BreakdownWordPhotos(
                                sentence = sentence,
                                chunk = chunk,
                                imageAlt = strings.chatStudyWordImageAlt,
                            )
                            Button(attrs = {
                                classes(Styles.studyBreakdownButton)
                                attr("type", "button")
                                onClick {
                                    expandedChunkIndex = if (selectedChunkExpanded) null else selectedChunkIndex
                                }
                            }) { Text(strings.chatStudyBreakdownWords) }
                            if (selectedChunkExpanded) {
                                chunk.wordIndices.forEach { tokenIndex ->
                                    val token = sentence.tokens.getOrNull(tokenIndex)
                                        ?.takeIf { it.isDissectable } ?: return@forEach
                                    val wordFailKey = messageId?.let { "$it#$sentenceIndex#$tokenIndex" }
                                    StudyTokenDetail(
                                        token = token,
                                        textLanguageTag = session.textLanguageTag,
                                        strings = strings,
                                        imageFailed = wordFailKey != null && wordImageFailed[wordFailKey] == true,
                                        onRetryImage = { onRetryWordImage(sentenceIndex, tokenIndex) },
                                    )
                                }
                            }
                        }
                    }
                }
                drillProgress.step == 2 -> {
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text(strings.chatStudyBreakdownSayAloud)
                    }
                    P(attrs = { classes(Styles.studyBreakdownSentence) }) { Text(displayedSuffix) }
                    BreakdownWordPhotos(
                        sentence = sentence,
                        chunk = chunks.getOrNull(newChunkIndex),
                        imageAlt = strings.chatStudyWordImageAlt,
                    )
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text("${drillProgress.backchainRep + 1} / $BREAKDOWN_REPS")
                    }
                    BreakdownYourTurn(
                        label = strings.chatStudyBreakdownYourTurn,
                        listenLabel = strings.chatSpeakListen,
                        userTurn = userTurn,
                    )
                    Div(attrs = { classes(Styles.studyBreakdownActions) }) {
                        chunks.drop(newChunkIndex).forEachIndexed { offset, chunk ->
                            BreakdownChunkButton(
                                chunk = chunk,
                                active = offset == 0,
                                onClick = {
                                    scope.launch { audioUrl(chunk.text)?.let { playAudio(it, restart = true) } }
                                },
                            )
                        }
                    }
                }
                drillProgress.step == 3 -> {
                    P(attrs = { classes(Styles.studyBreakdownSentence) }) { Text(sentence.text) }
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text("${drillProgress.shadowRep + 1} / $BREAKDOWN_REPS")
                    }
                    BreakdownYourTurn(
                        label = strings.chatStudyBreakdownYourTurn,
                        listenLabel = strings.chatSpeakListen,
                        userTurn = userTurn,
                    )
                }
                else -> {
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text(strings.chatStudyBreakdownPicturePrompt)
                    }
                    StudySentenceVisual(
                        imageUrl = sentence.imageUrl,
                        failed = sentenceImageFailed,
                        strings = strings,
                    )
                    if (answerVisible) {
                        P(attrs = { classes(Styles.studyBreakdownSentence) }) { Text(sentence.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownYourTurn(
    label: String,
    listenLabel: String,
    userTurn: Boolean,
) {
    if (userTurn) {
        Div(attrs = {
            classes(Styles.studyBreakdownYourTurn)
            attr("role", "status")
            attr("aria-live", "assertive")
        }) {
            MaterialSymbol("mic")
            Text(label)
        }
    } else {
        P(attrs = { classes(Styles.studyBreakdownPrompt, Styles.studyBreakdownListen) }) {
            Text(listenLabel)
        }
    }
}

@Composable
private fun BreakdownWordPhotos(
    sentence: StudySentence,
    chunk: StudyChunk?,
    imageAlt: String,
) {
    val imageUrls = chunk?.wordIndices.orEmpty().mapNotNull { tokenIndex ->
        sentence.tokens.getOrNull(tokenIndex)?.imageUrl?.takeIf { it.isNotBlank() }
    }
    if (imageUrls.isEmpty()) return

    Div(attrs = { classes(Styles.studyBreakdownPhotos) }) {
        Div(attrs = { classes(Styles.studyBreakdownPhotoRow) }) {
            imageUrls.forEach { imageUrl ->
                Img(src = imageUrl, alt = imageAlt, attrs = {
                    classes(Styles.studyBreakdownPhoto)
                    attr("loading", "lazy")
                })
            }
        }
    }
}

@Composable
private fun BreakdownAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes(Styles.studyBreakdownButton)
        attr("type", "button")
        if (!enabled) attr("disabled", "")
        onClick { onClick() }
    }) { Text(label) }
}

@Composable
private fun BreakdownChunkButton(
    chunk: StudyChunk,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes(Styles.studyBreakdownChunk)
        if (active) classes(Styles.studyBreakdownChunkActive)
        attr("type", "button")
        if (!enabled) attr("disabled", "")
        onClick { onClick() }
    }) { Text(chunk.text.trim()) }
}

private suspend fun playBreakdownClip(
    url: String,
    playbackRate: Double = 1.0,
): Pair<Boolean, Int> {
    var playbackEnded = false
    var durationMs = 0
    playAudio(
        url,
        restart = true,
        playbackRate = playbackRate,
        onEnded = { playbackEnded = true },
        onDurationMs = { durationMs = it },
    )
    withTimeoutOrNull(90_000) {
        while (!playbackEnded) delay(80)
    }
    return playbackEnded to durationMs
}

private fun breakdownYourTurnDelayMs(
    audioDurationMs: Int,
    playbackRate: Double = 1.0,
): Long {
    val spokenMs = when {
        audioDurationMs <= 0 -> BREAKDOWN_YOUR_TURN_FALLBACK_MS.toDouble()
        playbackRate > 0.0 -> audioDurationMs / playbackRate
        else -> audioDurationMs.toDouble()
    }
    return (spokenMs * BREAKDOWN_YOUR_TURN_MULTIPLIER).toLong().coerceIn(1_500L, 30_000L)
}

private fun breakdownShadowPlaybackRate(shadowRep: Int): Double = when (shadowRep) {
    0, 1 -> BREAKDOWN_SHADOW_SLOW_PLAYBACK_RATE
    2, 3 -> BREAKDOWN_SHADOW_MEDIUM_PLAYBACK_RATE
    else -> 1.0
}

private fun String.isPlaceholderGrammarNote(): Boolean =
    trim().trimEnd('.', '\u3002').trim().equals("Phrase structure", ignoreCase = true)

private fun buildSuffix(chunks: List<StudyChunk>, round: Int): String {
    if (chunks.isEmpty()) return ""
    val start = (chunks.lastIndex - round).coerceIn(0, chunks.lastIndex)
    return chunks.drop(start).joinToString(" ") { it.text.trim() }
}

/** Server chunks are preferred; word spans are enough to start the drill while generation is still running. */
private fun StudySentence.breakdownChunks(): List<StudyChunk> {
    if (chunks.isNotEmpty()) return chunks
    return alignStudyChunks(text, tokens.map { it.text }, emptyList()).map { chunk ->
        val gloss = chunk.wordIndices.mapNotNull { index ->
            tokens.getOrNull(index)?.let { token ->
                token.contextualMeaning.ifBlank { token.literalMeaning }.takeIf(String::isNotBlank)
            }
        }.joinToString(" ")
        chunk.copy(gloss = gloss)
    }
}