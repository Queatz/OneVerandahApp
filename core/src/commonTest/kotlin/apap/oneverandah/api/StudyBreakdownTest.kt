package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        var progress = StudyBreakdownSentenceProgress(step = 2)
        repeat(14) { progress = advanceStudyBreakdownBackchain(progress, chunkCount = 3) }
        assertEquals(StudyBreakdownSentenceProgress(step = 2, backchainRound = 2, backchainRep = 4), progress)
        progress = advanceStudyBreakdownBackchain(progress, chunkCount = 3)
        assertEquals(3, progress.step)
        assertEquals(5, progress.backchainRep)
        repeat(4) { progress = advanceStudyBreakdownShadow(progress) }
        assertEquals(StudyBreakdownSentenceProgress(step = 3, backchainRound = 2, backchainRep = 5, shadowRep = 4), progress)
        progress = advanceStudyBreakdownShadow(progress)
        assertEquals(4, progress.step)
        assertEquals(5, progress.shadowRep)
    }

    @Test
    fun `progress merge preserves completed sentence and newest active pointer`() {
        val merged = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(
                sentences = mapOf(
                    0 to StudyBreakdownSentenceProgress(step = 4, completed = true),
                    1 to StudyBreakdownSentenceProgress(step = 2, backchainRound = 1),
                ),
                updatedAt = 10,
            ),
            incoming = StudyBreakdownProgress(
                sentences = mapOf(
                    0 to StudyBreakdownSentenceProgress(step = 1),
                    1 to StudyBreakdownSentenceProgress(step = 3, shadowRep = 2),
                ),
                updatedAt = 20,
            ),
        )

        assertNotNull(merged)
        assertTrue(merged.sentences.getValue(0).completed)
        assertEquals(3, merged.sentences.getValue(1).step)
        assertEquals(2, merged.sentences.getValue(1).shadowRep)
    }

    @Test
    fun `explicit restart clears completion even when an older save is newer`() {
        val restarted = StudyBreakdownSentenceProgress(step = 1, restartedAt = 30)
        val merged = mergeStudyBreakdownProgress(
            current = StudyBreakdownProgress(
                sentences = mapOf(0 to StudyBreakdownSentenceProgress(step = 4, completed = true)),
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