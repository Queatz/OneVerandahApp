package apap.oneverandah.api

private const val studyBreakdownReps = 5

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
    if (progress.completed || progress.step != 2 || chunkCount <= 0) return progress
    return when {
        progress.backchainRep + 1 < studyBreakdownReps -> progress.copy(backchainRep = progress.backchainRep + 1)
        progress.backchainRound + 1 < chunkCount -> progress.copy(
            backchainRound = progress.backchainRound + 1,
            backchainRep = 0,
        )
        else -> progress.copy(
            step = 3,
            backchainRound = chunkCount - 1,
            backchainRep = studyBreakdownReps,
            shadowRep = 0,
        )
    }
}

fun advanceStudyBreakdownShadow(progress: StudyBreakdownSentenceProgress): StudyBreakdownSentenceProgress {
    if (progress.completed || progress.step != 3) return progress
    return if (progress.shadowRep + 1 < studyBreakdownReps) {
        progress.copy(shadowRep = progress.shadowRep + 1)
    } else {
        progress.copy(step = 4, shadowRep = studyBreakdownReps)
    }
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