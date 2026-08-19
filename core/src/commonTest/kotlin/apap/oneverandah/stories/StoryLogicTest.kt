package apap.oneverandah.stories

import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryLogicTest {
    @Test
    fun publishedNewestFirstHidesDraftsAndSortsByReleaseDate() {
        val older = story("old", published = true, releaseDate = 100, updatedAt = 500)
        val newer = story("new", published = true, releaseDate = 200, updatedAt = 200)
        val draft = story("draft", published = false, releaseDate = 300, updatedAt = 900)

        val listed = StoryLogic.publishedNewestFirst(listOf(older, draft, newer))

        assertEquals(listOf("new", "old"), listed.map { it.id })
    }

    @Test
    fun publishedNewestFirstFallsBackToUpdatedAtWhenReleaseMissing() {
        val a = story("a", published = true, releaseDate = null, updatedAt = 10)
        val b = story("b", published = true, releaseDate = null, updatedAt = 30)

        assertEquals(listOf("b", "a"), StoryLogic.publishedNewestFirst(listOf(a, b)).map { it.id })
    }

    @Test
    fun adminNewestFirstIncludesDrafts() {
        val draft = story("draft", published = false, updatedAt = 80)
        val live = story("live", published = true, updatedAt = 20)

        assertEquals(listOf("draft", "live"), StoryLogic.adminNewestFirst(listOf(live, draft)).map { it.id })
    }

    @Test
    fun adminListStatusPrefersPublishingOverDraft() {
        val draft = story("draft", published = false)
        val publishing = draft.copy(publishing = true)
        val live = story("live", published = true)

        assertEquals(StoryLogic.LIST_STATUS_DRAFT, StoryLogic.adminListStatus(draft))
        assertEquals(StoryLogic.LIST_STATUS_PUBLISHING, StoryLogic.adminListStatus(publishing))
        assertEquals(StoryLogic.LIST_STATUS_PUBLISHED, StoryLogic.adminListStatus(live))
        assertFalse(StoryLogic.canStartPublish(publishing))
        assertTrue(StoryLogic.canStartPublish(draft.copy(pages = listOf(StoryPage("p1", text = "Once")))))
    }

    @Test
    fun cannotPublishWithoutNameOrPageText() {
        assertEquals("A book name is required", StoryLogic.publishError(story("x", name = "  ")))
        assertEquals(
            "Add at least one page with text before publishing",
            StoryLogic.publishError(story("x", pages = listOf(StoryPage("p1", text = "   ")))),
        )
        assertTrue(
            StoryLogic.canPublish(
                story("x", pages = listOf(StoryPage("p1", text = "Once upon a verandah"))),
            ),
        )
    }

    @Test
    fun publicSummaryStripsPages() {
        val full = story(
            "s",
            published = true,
            pages = listOf(
                StoryPage(
                    id = "p1",
                    text = "Hello",
                    session = StudySessionResponse(
                        text = "Hello",
                        textLanguageTag = "en",
                        sentences = listOf(
                            StudySentence(
                                text = "Hello",
                                tokens = listOf(StudyToken("Hello", StudyTokenStatus.READY)),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val summary = StoryLogic.toPublicSummary(full)
        assertTrue(summary.pages.isEmpty())
        assertEquals("s", summary.id)
    }

    @Test
    fun resumeAndNavigationClampToBookBounds() {
        assertEquals(0, StoryLogic.resumePageIndex(-3, 4))
        assertEquals(2, StoryLogic.resumePageIndex(2, 4))
        assertEquals(4, StoryLogic.resumePageIndex(99, 4))
        assertTrue(StoryLogic.isFinished(4, 4))
        assertTrue(StoryLogic.isFinished(0, 0))
        assertFalse(StoryLogic.isFinished(1, 4))
        assertEquals(2, StoryLogic.nextPageIndex(1, 4))
        assertNull(StoryLogic.nextPageIndex(3, 4))
        assertEquals(0, StoryLogic.previousPageIndex(1))
        assertNull(StoryLogic.previousPageIndex(0))
    }

    @Test
    fun castPhotoCountIsCappedAtThree() {
        assertEquals(3, StoryLogic.clampCastPhotoCount(8))
        assertTrue(StoryLogic.hasRequiredCastPhotos(1))
        assertTrue(StoryLogic.hasRequiredCastPhotos(3))
        assertFalse(StoryLogic.hasRequiredCastPhotos(0))
        assertFalse(StoryLogic.hasRequiredCastPhotos(4))
    }

    @Test
    fun studyCharacterIdsStayUniqueWhenStudyRolesCollide() {
        val lan = StoryLogic.studyCharacterId("main", "https://example.test/lan.png")
        val minh = StoryLogic.studyCharacterId("main", "https://example.test/minh.png")
        val sameLan = StoryLogic.studyCharacterId("main", "https://example.test/lan.png")

        assertTrue(lan.startsWith("study-"))
        assertTrue(minh.startsWith("study-"))
        assertTrue(lan != minh)
        assertEquals(lan, sameLan)
    }

    @Test
    fun languageFallbackPrefersScriptSpecificMatches() {
        assertEquals("en", StoryLogic.detectLanguageFallback("  "))
        assertEquals("en", StoryLogic.detectLanguageFallback("A quiet garden story"))
        assertEquals("vi", StoryLogic.detectLanguageFallback("Một buổi chiều trên hiên nhà"))
        assertEquals("zh", StoryLogic.detectLanguageFallback("阳台上的小故事"))
        assertEquals("ja", StoryLogic.detectLanguageFallback("ベランダの短い物語"))
        assertEquals("ko", StoryLogic.detectLanguageFallback("베란다의 짧은 이야기"))
        assertEquals("ru", StoryLogic.detectLanguageFallback("Короткая история на веранде"))
        assertEquals("vi", StoryLogic.normalizeLanguageTag("vi-VN"))
        assertEquals("en", StoryLogic.normalizeLanguageTag("nope"))
    }

    private fun story(
        id: String,
        name: String = "Book $id",
        published: Boolean = false,
        releaseDate: Long? = null,
        updatedAt: Long = 0,
        pages: List<StoryPage> = listOf(StoryPage("p-$id", text = "Hello")),
    ) = Story(
        id = id,
        name = name,
        category = "Mystery",
        published = published,
        releaseDate = releaseDate,
        updatedAt = updatedAt,
        pages = pages,
    )
}
