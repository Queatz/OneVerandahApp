package apap.oneverandah.screens

import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class StudySessionsListTest {
    @Test
    fun `phrase image urls keep sentence order and skip blanks`() {
        val session = StudySessionResponse(
            text = "Hello. Hi.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(text = "Hello.", tokens = emptyList(), imageUrl = " https://img/a "),
                StudySentence(text = "Hi.", tokens = emptyList(), imageUrl = "  "),
                StudySentence(text = "Bye.", tokens = emptyList(), imageUrl = null),
                StudySentence(text = "Later.", tokens = emptyList(), imageUrl = "https://img/b"),
            ),
        )
        assertEquals(
            listOf("https://img/a", "https://img/b"),
            studySessionPhraseImageUrls(session),
        )
        assertEquals(
            emptyList(),
            studySessionPhraseImageUrls(
                StudySessionResponse(text = "Hi", textLanguageTag = "en", sentences = emptyList()),
            ),
        )
    }
}
