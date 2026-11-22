package apap.oneverandah.api

import kotlin.random.Random

private const val studyBreakdownReps = 5
private const val studyWordRecallCorrectToPass = 3

/** Makes generated chunk boundaries exact spans over the sentence's existing word tokens. */
fun alignStudyChunks(
    sentenceText: String,
    tokenTexts: List<String>,
    proposed: List<StudyChunk>,
): List<StudyChunk> {
    val spans = SpeechTimingAligner.tokenCharSpans(sentenceText, tokenTexts)
    val wordIndices = tokenTexts.indices.filter { index ->
        tokenTexts[index].any { it.isLetterOrDigit() || it.category == CharCategory.OTHER_LETTER } &&
                spans.getOrNull(index) != null
    }
    if (wordIndices.isEmpty()) return emptyList()

    val proposedIndices = proposed.flatMap { it.wordIndices }
    val validCount = if (wordIndices.size == 1) proposed.size == 1 else proposed.size in 2..4
    val validGroups = validCount && proposed.all { it.wordIndices.isNotEmpty() } &&
            proposedIndices == wordIndices && proposed.all { chunk ->
                val positions = chunk.wordIndices.map(wordIndices::indexOf)
                positions.all { it >= 0 } && positions.zipWithNext().all { (left, right) -> right == left + 1 }
            }
    val aligned = if (validGroups) {
        proposed
    } else {
        fallbackStudyChunks(wordIndices)
    }
    return aligned.mapIndexed { index, chunk ->
        val firstWordIndex = chunk.wordIndices.first()
        val start = spans[firstWordIndex]!!.first
        val nextStart = aligned.getOrNull(index + 1)?.wordIndices?.firstOrNull()
            ?.let { spans[it]?.first }
            ?: sentenceText.length
        chunk.copy(text = sentenceText.substring(start, nextStart).trim())
    }
}

/** Suffix strings are exact slices of the source sentence, ordered from the last chunk to the first. */
fun studyBreakdownSuffixes(
    sentenceText: String,
    tokenTexts: List<String>,
    chunks: List<StudyChunk>,
): List<String> {
    val spans = SpeechTimingAligner.tokenCharSpans(sentenceText, tokenTexts)
    return chunks.asReversed().mapNotNull { chunk ->
        val start = chunk.wordIndices.firstOrNull()?.let { spans.getOrNull(it)?.first } ?: return@mapNotNull null
        sentenceText.substring(start).trim()
    }
}

fun advanceStudyBreakdownBackchain(
    progress: StudyBreakdownSentenceProgress,
    chunkCount: Int,
): StudyBreakdownSentenceProgress {
    if (progress.completed || progress.step != 3 || chunkCount <= 0) return progress
    return when {
        progress.backchainRep + 1 < studyBreakdownReps -> progress.copy(
            schemaVersion = 2,
            backchainRep = progress.backchainRep + 1,
        )
        progress.backchainRound + 1 < chunkCount -> progress.copy(
            schemaVersion = 2,
            backchainRound = progress.backchainRound + 1,
            backchainRep = 0,
        )
        else -> progress.copy(
            schemaVersion = 2,
            step = 4,
            backchainRound = chunkCount - 1,
            backchainRep = studyBreakdownReps,
            shadowRep = 0,
        )
    }
}

fun advanceStudyBreakdownShadow(progress: StudyBreakdownSentenceProgress): StudyBreakdownSentenceProgress {
    if (progress.completed || progress.step != 4) return progress
    return if (progress.shadowRep + 1 < studyBreakdownReps) {
        progress.copy(schemaVersion = 2, shadowRep = progress.shadowRep + 1)
    } else {
        progress.copy(schemaVersion = 2, step = 5, shadowRep = studyBreakdownReps)
    }
}

/** Starts Word Recall in randomized token order; queue positions encode intervening-item gaps. */
fun startStudyBreakdownWordRecall(
    progress: StudyBreakdownSentenceProgress,
    tokenIndices: List<Int>,
    random: Random = Random.Default,
): StudyBreakdownSentenceProgress {
    val queue = tokenIndices.distinct().filter { it >= 0 }.shuffled(random)
    return progress.copy(
        schemaVersion = 2,
        step = if (queue.isEmpty()) 3 else 2,
        backchainRound = 0,
        backchainRep = 0,
        wordRecallQueue = queue,
        wordRecallCorrect = emptyMap(),
        wordRecallKnown = emptySet(),
        wordRecallRevealed = false,
    )
}

fun revealStudyBreakdownWordRecall(
    progress: StudyBreakdownSentenceProgress,
): StudyBreakdownSentenceProgress = if (
    !progress.completed && progress.step == 2 && progress.wordRecallQueue.isNotEmpty()
) {
    progress.copy(schemaVersion = 2, wordRecallRevealed = true)
} else {
    progress
}

/** Grades the revealed head item; successful recalls use expanding 1/3-item gaps and misses use 1. */
fun gradeStudyBreakdownWordRecall(
    progress: StudyBreakdownSentenceProgress,
    correct: Boolean,
): StudyBreakdownSentenceProgress {
    if (progress.completed || progress.step != 2 || !progress.wordRecallRevealed) return progress
    val wordIndex = progress.wordRecallQueue.firstOrNull() ?: return progress
    val remaining = progress.wordRecallQueue.drop(1)
    val correctCount = progress.wordRecallCorrect[wordIndex] ?: 0
    val updatedCorrectCount = if (correct) (correctCount + 1).coerceAtMost(studyWordRecallCorrectToPass) else correctCount
    val passed = correct && updatedCorrectCount >= studyWordRecallCorrectToPass
    val queue = if (passed) {
        remaining
    } else {
        val gap = if (!correct) 1 else if (updatedCorrectCount == 1) 1 else 3
        remaining.toMutableList().apply { add(gap.coerceAtMost(size), wordIndex) }
    }
    return progress.copy(
        schemaVersion = 2,
        step = if (queue.isEmpty()) 3 else 2,
        wordRecallQueue = queue,
        wordRecallCorrect = progress.wordRecallCorrect + (wordIndex to updatedCorrectCount),
        wordRecallRevealed = false,
    )
}

/** Marks the current word as known without revealing its form, meaning, image, or audio. */
fun skipStudyBreakdownWordRecall(
    progress: StudyBreakdownSentenceProgress,
): StudyBreakdownSentenceProgress {
    if (progress.completed || progress.step != 2) return progress
    val wordIndex = progress.wordRecallQueue.firstOrNull() ?: return progress.copy(step = 3)
    val queue = progress.wordRecallQueue.drop(1)
    return progress.copy(
        schemaVersion = 2,
        step = if (queue.isEmpty()) 3 else 2,
        wordRecallQueue = queue,
        wordRecallKnown = progress.wordRecallKnown + wordIndex,
        wordRecallRevealed = false,
    )
}

/** Skips the entire Word Recall step while its generated conversation is unavailable. */
fun skipStudyBreakdownWordRecallStep(
    progress: StudyBreakdownSentenceProgress,
): StudyBreakdownSentenceProgress = if (!progress.completed && progress.step == 2) {
    progress.copy(schemaVersion = 2, step = 3, wordRecallRevealed = false)
} else {
    progress
}

/** True when [candidate] is further through the drill than [baseline], ignoring restarts. */
fun studyBreakdownSentenceAhead(
    candidate: StudyBreakdownSentenceProgress,
    baseline: StudyBreakdownSentenceProgress,
): Boolean {
    if (candidate.completed && !baseline.completed) return true
    if (baseline.completed || candidate.completed) return false
    val candidateStep = candidate.currentStudyBreakdownStep()
    val baselineStep = baseline.currentStudyBreakdownStep()
    return when {
        candidateStep != baselineStep -> candidateStep > baselineStep
        candidateStep == 2 -> {
            val candidateScore = candidate.wordRecallCorrect.values.sum() + candidate.wordRecallKnown.size
            val baselineScore = baseline.wordRecallCorrect.values.sum() + baseline.wordRecallKnown.size
            when {
                candidateScore != baselineScore -> candidateScore > baselineScore
                candidate.wordRecallCorrect != baseline.wordRecallCorrect ->
                    candidate.wordRecallCorrect.all { (index, count) -> count >= (baseline.wordRecallCorrect[index] ?: 0) } &&
                            candidate.wordRecallCorrect.any { (index, count) -> count > (baseline.wordRecallCorrect[index] ?: 0) }
                candidate.wordRecallKnown != baseline.wordRecallKnown ->
                    candidate.wordRecallKnown.containsAll(baseline.wordRecallKnown)
                candidate.wordRecallQueue != baseline.wordRecallQueue -> true
                else -> candidate.wordRecallRevealed && !baseline.wordRecallRevealed
            }
        }
        candidate.backchainRound != baseline.backchainRound -> candidate.backchainRound > baseline.backchainRound
        candidate.backchainRep != baseline.backchainRep -> candidate.backchainRep > baseline.backchainRep
        else -> candidate.shadowRep > baseline.shadowRep
    }
}

private fun StudyBreakdownSentenceProgress.currentStudyBreakdownStep(): Int = when {
    schemaVersion == 1 && completed -> 5
    schemaVersion == 1 && step >= 2 -> step + 1
    else -> step
}

/** Progress merge is monotonic for completion, while the newest incomplete pointer remains resumable. */
fun mergeStudyBreakdownProgress(
    current: StudyBreakdownProgress?,
    incoming: StudyBreakdownProgress?,
): StudyBreakdownProgress? {
    val old = current?.normalizedOrNull()
    val new = incoming?.normalizedOrNull()
    if (old == null) return new
    if (new == null) return old
    val selected = if (new.updatedAt >= old.updatedAt) new else old
    val merged = (old.sentences.keys + new.sentences.keys).associateWith { index ->
        val oldSentence = old.sentences[index]
        val newSentence = new.sentences[index]
        when {
            oldSentence == null -> newSentence!!
            newSentence == null -> oldSentence
            newSentence.restartedAt > oldSentence.restartedAt -> newSentence
            oldSentence.restartedAt > newSentence.restartedAt -> oldSentence
            oldSentence.completed -> oldSentence
            newSentence.completed -> newSentence
            studyBreakdownSentenceAhead(newSentence, oldSentence) -> newSentence
            studyBreakdownSentenceAhead(oldSentence, newSentence) -> oldSentence
            selected === new -> newSentence
            else -> oldSentence
        }
    }
    return selected.copy(sentences = merged)
}

private fun fallbackStudyChunks(wordIndices: List<Int>): List<StudyChunk> {
    val chunkCount = if (wordIndices.size == 1) 1 else ((wordIndices.size + 2) / 3).coerceIn(2, 4)
    val baseSize = wordIndices.size / chunkCount
    val extra = wordIndices.size % chunkCount
    var cursor = 0
    return List(chunkCount) { index ->
        val size = baseSize + if (index < extra) 1 else 0
        val group = wordIndices.subList(cursor, cursor + size)
        cursor += size
        StudyChunk(text = "", gloss = "", grammarNote = "", wordIndices = group)
    }
}