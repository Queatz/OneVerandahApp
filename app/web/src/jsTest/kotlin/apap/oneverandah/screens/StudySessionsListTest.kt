package apap.oneverandah.screens

import apap.oneverandah.api.SavedStudySessionSummary
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
    fun `all filter combines mine before community and keeps mine on duplicate ids`() {
        val mine = sampleSaved("mine", "Mine")
        val duplicate = sampleSaved("shared", "My copy")
        val communityDuplicate = sampleSaved("shared", "Community copy")
        val community = sampleSaved("community", "Community")

        val result = studySessionsForFilter(
            mine = listOf(mine, duplicate),
            community = listOf(communityDuplicate, community),
            filter = StudySessionsFilter.ALL,
        )

        assertEquals(listOf("mine", "shared", "community"), result.map { it.item.id })
        assertEquals(listOf(true, true, false), result.map { it.removable })
        assertEquals("My copy", result[1].item.text)
    }

    @Test
    fun `mine and community filters return only their own removable entries`() {
        val mine = sampleSaved("mine", "Mine")
        val community = sampleSaved("community", "Community")

        val mineResult = studySessionsForFilter(listOf(mine), listOf(community), StudySessionsFilter.MINE)
        val communityResult = studySessionsForFilter(listOf(mine), listOf(community), StudySessionsFilter.COMMUNITY)

        assertEquals(listOf("mine"), mineResult.map { it.item.id })
        assertEquals(listOf(true), mineResult.map { it.removable })
        assertEquals(listOf("community"), communityResult.map { it.item.id })
        assertEquals(listOf(false), communityResult.map { it.removable })
    }

    @Test
    fun `study session path is shareable by id`() {
        assertEquals("/study/abc-123", studySessionPath("abc-123"))
        assertEquals("/study/abc-123", studySessionPath(" abc-123 "))
    }

    private fun sampleSaved(id: String, text: String) = SavedStudySessionSummary(
        id = id,
        languageTag = "en",
        text = text,
        textLanguageTag = "en",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
