package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.random.Random

class StudyBreakdownTest {
    @Test
    fun `aligns AI chunks to exact word spans and derives suffixes from sentence`() {
        val sentence = "We are ready!"
        val tokens = listOf("We", "are", "ready", "!")
        val chunks = alignStudyChunks(
            sentence,
            tokens,
            listOf(
                StudyChunk("changed", "we", "subject", listOf(0)),
                StudyChunk("changed", "are", "verb", listOf(1)),
                StudyChunk("changed", "ready", "complement", listOf(2)),
            ),
        )

        assertEquals(listOf("We", "are", "ready!"), chunks.map { it.text })
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), chunks.map { it.wordIndices })
        assertEquals(listOf("ready!", "are ready!", "We are ready!"), studyBreakdownSuffixes(sentence, tokens, chunks))
    }

    @Test
    fun `invalid chunk indices fall back to complete contiguous word groups`() {
        val chunks = alignStudyChunks(
            "I really like coffee.",
            listOf("I", "really", "like", "coffee", "."),
            listOf(StudyChunk("coffee", "", "", listOf(3))),
        )

        assertEquals(2, chunks.size)
        assertEquals(listOf(0, 1, 2, 3), chunks.flatMap { it.wordIndices })
        assertTrue(chunks.all { it.text.isNotBlank() })
    }

    @Test
    fun `backchain and shadow advance sequentially through all rounds`() {
        var progress = StudyBreakdownSentenceProgress(step = 3, schemaVersion = 2)
        repeat(14) { progress = advanceStudyBreakdownBackchain(progress, chunkCount = 3) }
        assertEquals(StudyBreakdownSentenceProgress(step = 3, backchainRound = 2, backchainRep = 4, schemaVersion = 2), progress)
        progress = advanceStudyBreakdownBackchain(progress, chunkCount = 3)
        assertEquals(4, progress.step)
        assertEquals(5, progress.backchainRep)
        repeat(4) { progress = advanceStudyBreakdownShadow(progress) }
        assertEquals(StudyBreakdownSentenceProgress(step = 4, backchainRound = 2, backchainRep = 5, shadowRep = 4, schemaVersion = 2), progress)
        progress = advanceStudyBreakdownShadow(progress)
        assertEquals(5, progress.step)
        assertEquals(5, progress.shadowRep)
    }

    @Test
    fun `legacy progress migrates old backchain shadow and recall steps`() {
        val normalized = StudyBreakdownProgress(
            sentences = mapOf(
                0 to StudyBreakdownSentenceProgress(step = 1),
                1 to StudyBreakdownSentenceProgress(step = 2),
                2 to StudyBreakdownSentenceProgress(step = 3),
                3 to StudyBreakdownSentenceProgress(step = 4),
                4 to StudyBreakdownSentenceProgress(step = 4, completed = true),
            ),
        ).normalizedOrNull()

        assertNotNull(normalized)
        assertEquals(listOf(1, 3, 4, 5, 5), normalized.sentences.toSortedMap().values.map { it.step })
        assertTrue(normalized.sentences.getValue(4).completed)
        assertTrue(normalized.sentences.values.all { it.schemaVersion == 2 })
        assertEquals(null, StudyBreakdownProgress(
            sentences = mapOf(0 to StudyBreakdownSentenceProgress(step = 6, schemaVersion = 2)),
        ).normalizedOrNull())
    }

    @Test
    fun `word recall interleaves after one then three items and passes after three correct recalls`() {
        val started = startStudyBreakdownWordRecall(
            StudyBreakdownSentenceProgress(schemaVersion = 2),
            (0..7).toList(),
            Random(12),
        )
        val firstWord = started.wordRecallQueue.first()

        val afterFirst = gradeStudyBreakdownWordRecall(revealStudyBreakdownWordRecall(started), correct = true)
        assertEquals(started.wordRecallQueue[1], afterFirst.wordRecallQueue[0])
        assertEquals(firstWord, afterFirst.wordRecallQueue[1])
        assertEquals(1, afterFirst.wordRecallCorrect[firstWord])

        val secondReview = afterFirst.copy(
            wordRecallQueue = listOf(firstWord) + afterFirst.wordRecallQueue.filterNot { it == firstWord },
            wordRecallRevealed = true,
        )
        val afterSecond = gradeStudyBreakdownWordRecall(secondReview, correct = true)
        assertEquals(firstWord, afterSecond.wordRecallQueue[3])
        assertEquals(2, afterSecond.wordRecallCorrect[firstWord])

        val thirdReview = afterSecond.copy(
            wordRecallQueue = listOf(firstWord) + afterSecond.wordRecallQueue.filterNot { it == firstWord },
            wordRecallRevealed = true,
        )
        val afterThird = gradeStudyBreakdownWordRecall(thirdReview, correct = true)
        assertFalse(firstWord in afterThird.wordRecallQueue)
        assertEquals(3, afterThird.wordRecallCorrect[firstWord])
    }

    @Test
    fun `miss requeues sooner and known skips the active word`() {
        val progress = StudyBreakdownSentenceProgress(
            step = 2,
            schemaVersion = 2,
            wordRecallQueue = listOf(4, 2, 8),
            wordRecallCorrect = mapOf(4 to 1),
            wordRecallRevealed = true,
        )

        val missed = gradeStudyBreakdownWordRecall(progress, correct = false)
        assertEquals(listOf(2, 4, 8), missed.wordRecallQueue)
        assertEquals(1, missed.wordRecallCorrect[4])

        val skipped = skipStudyBreakdownWordRecall(progress.copy(wordRecallRevealed = false))
        assertEquals(listOf(2, 8), skipped.wordRecallQueue)
        assertTrue(4 in skipped.wordRecallKnown)
    }

    @Test
    fun `word recall step skip advances to backchain and is a no-op elsewhere`() {
        val progress = StudyBreakdownSentenceProgress(
            step = 2,
            schemaVersion = 2,
            wordRecallQueue = listOf(4, 2),
            wordRecallCorrect = mapOf(4 to 1),
            wordRecallRevealed = true,
        )

        val skipped = skipStudyBreakdownWordRecallStep(progress)
        assertEquals(3, skipped.step)
        assertEquals(progress.wordRecallQueue, skipped.wordRecallQueue)
        assertEquals(progress.wordRecallCorrect, skipped.wordRecallCorrect)
        assertFalse(skipped.wordRecallRevealed)
        val backchain = progress.copy(step = 3)
        assertEquals(backchain, skipStudyBreakdownWordRecallStep(backchain))
    }

    @Test
    fun `word recall merge keeps a miss requeue and a newly revealed answer`() {
        val baseline = StudyBreakdownSentenceProgress(
            step = 2,
            schemaVersion = 2,
            wordRecallQueue = listOf(4, 2, 8),
            wordRecallCorrect = mapOf(4 to 1),
        )
        val missed = gradeStudyBreakdownWordRecall(baseline.copy(wordRecallRevealed = true), correct = false)
        val mergedMiss = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(sentences = mapOf(0 to baseline), updatedAt = 10),
            incoming = StudyBreakdownProgress(sentences = mapOf(0 to missed), updatedAt = 20),
        )
        assertEquals(missed, mergedMiss?.sentences?.getValue(0))

        val revealed = revealStudyBreakdownWordRecall(baseline)
        val mergedReveal = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(sentences = mapOf(0 to baseline), updatedAt = 10),
            incoming = StudyBreakdownProgress(sentences = mapOf(0 to revealed), updatedAt = 20),
        )
        assertTrue(mergedReveal?.sentences?.getValue(0)?.wordRecallRevealed == true)
    }

    @Test
    fun `word recall with no words advances directly to backchain and final correct completes the step`() {
        val empty = startStudyBreakdownWordRecall(StudyBreakdownSentenceProgress(schemaVersion = 2), emptyList())
        assertEquals(3, empty.step)

        val single = startStudyBreakdownWordRecall(StudyBreakdownSentenceProgress(schemaVersion = 2), listOf(7))
        val completed = (1..3).fold(single) { progress, _ ->
            gradeStudyBreakdownWordRecall(revealStudyBreakdownWordRecall(progress), correct = true)
        }
        assertEquals(3, completed.step)
        assertTrue(completed.wordRecallQueue.isEmpty())
    }

    @Test
    fun `progress merge preserves completed sentence and newest active pointer`() {
        val merged = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(
                sentences = mapOf(
                    0 to StudyBreakdownSentenceProgress(step = 5, completed = true, schemaVersion = 2),
                    1 to StudyBreakdownSentenceProgress(step = 3, backchainRound = 1, schemaVersion = 2),
                ),
                updatedAt = 10,
            ),
            incoming = StudyBreakdownProgress(
                sentences = mapOf(
                    0 to StudyBreakdownSentenceProgress(step = 1, schemaVersion = 2),
                    1 to StudyBreakdownSentenceProgress(step = 4, shadowRep = 2, schemaVersion = 2),
                ),
                updatedAt = 20,
            ),
        )

        assertNotNull(merged)
        assertTrue(merged.sentences.getValue(0).completed)
        assertEquals(4, merged.sentences.getValue(1).step)
        assertEquals(2, merged.sentences.getValue(1).shadowRep)
    }

    @Test
    fun `later stale save does not rewind backchain reps`() {
        val merged = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(
                sentences = mapOf(0 to StudyBreakdownSentenceProgress(step = 3, backchainRep = 2, schemaVersion = 2)),
                updatedAt = 20,
            ),
            incoming = StudyBreakdownProgress(
                sentences = mapOf(0 to StudyBreakdownSentenceProgress(step = 3, backchainRep = 0, schemaVersion = 2)),
                updatedAt = 30,
            ),
        )

        assertEquals(2, merged?.sentences?.getValue(0)?.backchainRep)
        assertEquals(30, merged?.updatedAt)
    }

    @Test
    fun `explicit restart clears completion even when an older save is newer`() {
        val restarted = StudyBreakdownSentenceProgress(step = 1, restartedAt = 30, schemaVersion = 2)
        val merged = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(
                sentences = mapOf(0 to StudyBreakdownSentenceProgress(step = 5, completed = true, schemaVersion = 2)),
                updatedAt = 40,
            ),
            incoming = StudyBreakdownProgress(
                sentences = mapOf(0 to restarted),
                updatedAt = 30,
            ),
        )

        assertEquals(restarted, merged?.sentences?.getValue(0))
    }
}