package apap.oneverandah.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTestTabTest {
    @Test
    fun `score transcripts preserve beat order and blank unanswered beats`() {
        val transcripts = studyExamTranscriptsForScore(
            beatIds = listOf("listen-1", "speak-1", "speak-2"),
            answers = mapOf("listen-1" to "  Hello.  ", "speak-1" to "Chào bạn.", "unknown" to "ignored"),
        )

        assertEquals(
            listOf("listen-1" to "Hello.", "speak-1" to "Chào bạn.", "speak-2" to ""),
            transcripts,
        )
    }
}
