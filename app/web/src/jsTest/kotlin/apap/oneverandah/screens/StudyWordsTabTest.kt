package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyWordsTabTest {
    @Test
    fun `vocabulary rows use dissected main tokens in order and keep first duplicate`() {
        val rows = studyVocabularyRows(
            listOf(
                StudySentence(
                    text = "Hola, mundo",
                    tokens = listOf(
                        StudyToken(
                            text = "Hola",
                            literalMeaning = "hello",
                            contextualMeaning = "greeting",
                            exampleSentences = listOf(StudyExampleSentence("Hola, ejemplo", "Example greeting")),
                        ),
                        StudyToken(text = ",", status = StudyTokenStatus.PLAIN),
                        StudyToken(text = "pending", status = StudyTokenStatus.PENDING, literalMeaning = "not ready"),
                        StudyToken(text = "mundo", literalMeaning = "world"),
                    ),
                ),
                StudySentence(
                    text = "Hola otra vez",
                    tokens = listOf(
                        StudyToken(text = "hola", contextualMeaning = "later meaning"),
                        StudyToken(text = "empty", status = StudyTokenStatus.READY),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                StudyVocabularyRow(0, 0, "Hola", "greeting", "hello"),
                StudyVocabularyRow(0, 3, "mundo", "world", "world"),
            ),
            rows,
        )
    }

    @Test
    fun `vocabulary meaning lines prefer literal and include a distinct contextual meaning`() {
        assertEquals(listOf("to leave", "to go away"), studyVocabularyMeaningLines("to leave", "to go away"))
        assertEquals(listOf("hello"), studyVocabularyMeaningLines("hello", "hello"))
        assertEquals(listOf("greeting"), studyVocabularyMeaningLines("", "greeting"))
    }

    @Test
    fun `example breakdown toggles closed and switches to a different example`() {
        assertEquals("sentence-0-word-0-example-0", toggleStudyExampleBreakdown(null, "sentence-0-word-0-example-0"))
        assertEquals(null, toggleStudyExampleBreakdown("sentence-0-word-0-example-0", "sentence-0-word-0-example-0"))
        assertEquals("sentence-0-word-1-example-0", toggleStudyExampleBreakdown(
            "sentence-0-word-0-example-0",
            "sentence-0-word-1-example-0",
        ))
        assertEquals("Hola-0", toggleStudyExampleBreakdown(null, "Hola-0"))
        assertEquals(null, toggleStudyExampleBreakdown("Hola-0", "Hola-0"))
        assertEquals("Hola-1", toggleStudyExampleBreakdown("Hola-0", "Hola-1"))
    }
}
