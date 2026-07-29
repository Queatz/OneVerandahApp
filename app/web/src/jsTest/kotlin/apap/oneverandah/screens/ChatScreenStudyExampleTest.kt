package apap.oneverandah.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatScreenStudyExampleTest {
    @Test
    fun `highlights single case-insensitive match`() {
        assertEquals(
            listOf(0 until 5),
            studyExampleHighlightRanges("Hello there.", "hello"),
        )
    }

    @Test
    fun `highlights every non-overlapping occurrence`() {
        assertEquals(
            listOf(0 until 3, 10 until 13),
            studyExampleHighlightRanges("the cat is the best", "the"),
        )
    }

    @Test
    fun `highlights multi-word phrases`() {
        assertEquals(
            listOf(4 until 10),
            studyExampleHighlightRanges("Tôi đi học sớm.", "đi học"),
        )
    }

    @Test
    fun `returns empty when word is missing or blank`() {
        assertTrue(studyExampleHighlightRanges("Hello friend.", "bye").isEmpty())
        assertTrue(studyExampleHighlightRanges("Hello friend.", "   ").isEmpty())
        assertTrue(studyExampleHighlightRanges("", "Hello").isEmpty())
    }
}
