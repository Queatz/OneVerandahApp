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
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.advanceStudyBreakdownBackchain
import apap.oneverandah.api.advanceStudyBreakdownShadow
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.i18n.UiStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date

private const val BREAKDOWN_SHADOW_REPS = 5

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
    val chunks = sentence.chunks
    val suffixes = sentence.breakdownSuffixes
    val scope = rememberCoroutineScope()
    val audioUrls = remember(session.textLanguageTag, sentence.text) { mutableStateMapOf<String, String>() }
    var drillProgress by remember(sentenceIndex) {
        mutableStateOf(progress?.sentences?.get(sentenceIndex) ?: StudyBreakdownSentenceProgress())
    }
    var savedProgress by remember(sentenceIndex) { mutableStateOf(progress ?: StudyBreakdownProgress()) }
    var selectedChunkIndex by remember(sentenceIndex) { mutableStateOf<Int?>(null) }
    var expandedChunkIndex by remember(sentenceIndex) { mutableStateOf<Int?>(null) }
    var answerVisible by remember(sentenceIndex) { mutableStateOf(false) }
    var slowAudio by remember(sentenceIndex) { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (progress != null && progress.updatedAt >= savedProgress.updatedAt) {
            savedProgress = progress
            drillProgress = progress.sentences[sentenceIndex] ?: StudyBreakdownSentenceProgress()
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

    LaunchedEffect(drillProgress.step, drillProgress.backchainRound, chunks, suffixes) {
        if (drillProgress.step != 2 || chunks.isEmpty()) return@LaunchedEffect
        val round = drillProgress.backchainRound.coerceIn(0, chunks.lastIndex)
        val suffix = suffixes.getOrNull(round) ?: buildSuffix(chunks, round)
        audioUrl(suffix)?.let { playAudio(it, restart = true) }
    }

    LaunchedEffect(drillProgress.step, drillProgress.shadowRep, slowAudio, sentence.text) {
        if (drillProgress.step != 3) return@LaunchedEffect
        val url = audioUrl(sentence.text) ?: return@LaunchedEffect
        var playbackEnded = false
        playAudio(
            url,
            restart = true,
            onEnded = { playbackEnded = true },
            playbackRate = if (slowAudio) 0.75 else 1.0,
        )
        withTimeoutOrNull(90_000) {
            while (!playbackEnded) delay(80)
        }
        if (playbackEnded) {
            delay((sentence.text.length * 60L).coerceIn(1_500L, 5_000L))
            saveProgress(advanceStudyBreakdownShadow(drillProgress))
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
    val selectedChunk = selectedChunkIndex?.let(chunks::getOrNull)
    val selectedChunkExpanded = expandedChunkIndex == selectedChunkIndex

    BottomDialog(
        visible = true,
        closeLabel = strings.close,
        onDismiss = onDismiss,
        expanded = true,
        footer = {
            Div(attrs = { classes(Styles.studyBreakdownActions) }) {
                when {
                    drillProgress.completed -> BreakdownAction(label = strings.close, onClick = onDismiss)
                    drillProgress.step == 1 -> BreakdownAction(
                        label = strings.chatStudyBreakdownGotIt,
                        enabled = chunks.isNotEmpty() && sentence.breakdownAudioReady,
                        onClick = { saveProgress(drillProgress.copy(step = 2, backchainRound = 0)) },
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
            Div(attrs = { classes(Styles.studyBreakdownActions) }) {
                Span { Text(strings.chatStudyBreakdown) }
                Button(attrs = {
                    classes(Styles.studyBreakdownButton)
                    attr("type", "button")
                    attr("aria-label", strings.close)
                    onClick { onDismiss() }
                }) { MaterialSymbol("close") }
            }
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
                    if (chunks.isEmpty() || !sentence.breakdownAudioReady) {
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
                            P { Text(chunk.gloss) }
                            if (chunk.grammarNote.isNotBlank()) {
                                P(attrs = { classes(Styles.studyBreakdownPrompt) }) { Text(chunk.grammarNote) }
                            }
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
                    Div(attrs = { classes(Styles.studyBreakdownActions) }) {
                        chunks.drop(newChunkIndex).forEachIndexed { offset, chunk ->
                            BreakdownChunkButton(
                                chunk = chunk,
                                active = offset == 0,
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                }
                drillProgress.step == 3 -> {
                    P(attrs = { classes(Styles.studyBreakdownSentence) }) { Text(sentence.text) }
                    P(attrs = { classes(Styles.studyBreakdownPrompt) }) {
                        Text("${strings.chatStudyBreakdownRep} ${drillProgress.shadowRep + 1} / $BREAKDOWN_SHADOW_REPS")
                    }
                    Button(attrs = {
                        classes(Styles.studyBreakdownButton, Styles.studyBreakdownAudioToggle)
                        attr("type", "button")
                        onClick { slowAudio = !slowAudio }
                    }) {
                        MaterialSymbol("slow_motion_video")
                        Text(strings.chatStudyBreakdownSlow)
                    }
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

private fun buildSuffix(chunks: List<StudyChunk>, round: Int): String {
    if (chunks.isEmpty()) return ""
    val start = (chunks.lastIndex - round).coerceIn(0, chunks.lastIndex)
    return chunks.drop(start).joinToString(" ") { it.text.trim() }
}