package apap.oneverandah.screens

import apap.oneverandah.api.SavedStudySession
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

    @Test
    fun `community pages append unique sessions`() {
        val first = sampleSaved("a", "One")
        val second = sampleSaved("b", "Two")
        val merged = mergeCommunityStudySessions(listOf(first), listOf(first, second))
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals(20, STUDY_COMMUNITY_PAGE_SIZE)
    }

    @Test
    fun `study session path is shareable by id`() {
        assertEquals("/study/abc-123", studySessionPath("abc-123"))
        assertEquals("/study/abc-123", studySessionPath(" abc-123 "))
    }

    private fun sampleSaved(id: String, text: String) = SavedStudySession(
        id = id,
        languageTag = "en",
        text = text,
        textLanguageTag = "en",
        session = StudySessionResponse(text = text, textLanguageTag = "en", sentences = emptyList()),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
