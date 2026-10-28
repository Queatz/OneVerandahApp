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
                StudyVocabularyRow(0, 0, "Hola", "greeting"),
                StudyVocabularyRow(0, 3, "mundo", "world"),
            ),
            rows,
        )
    }
}
