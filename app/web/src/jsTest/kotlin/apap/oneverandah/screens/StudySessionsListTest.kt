package apap.oneverandah.screens

import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionsListTest {
    @Test
    fun `phrase image urls keep sentence order and skip blanks`() {
        val item = sampleSaved("images", "Hello. Hi.").copy(
            sentenceImageUrls = listOf(" https://img/a ", "  ", "https://img/b"),
        )
        assertEquals(
            listOf("https://img/a", "https://img/b"),
            studySessionPhraseImageUrls(item),
        )
        assertEquals(
            emptyList(),
            studySessionPhraseImageUrls(sampleSaved("empty", "Hi")),
        )
    }

    @Test
    fun `session display name falls back to source text when unnamed`() {
        val named = sampleSaved("named", "Original text").copy(name = "  Order coffee  ")
        val unnamed = sampleSaved("unnamed", "Original text")

        assertEquals("Order coffee", studySessionDisplayName(named))
        assertEquals("Original text", studySessionDisplayName(unnamed))
    }

    @Test
    fun `session card keeps source text beneath the name header`() {
        val named = sampleSaved("named", "Original text").copy(name = "  Order coffee  ")
        val unnamed = sampleSaved("unnamed", "Original text")

        assertEquals(StudySessionCardContent("Order coffee", "Original text"), studySessionCardContent(named, "Study"))
        assertEquals(StudySessionCardContent("Study", "Original text"), studySessionCardContent(unnamed, "Study"))
    }

    @Test
    fun `exam grade badge is visible only on owned cards`() {
        val grade = StudyExamGrade(4, 5, 3, "A strong attempt.", 100L)
        val graded = sampleSaved("graded", "Hello.").copy(examGrade = grade)

        assertEquals(4, studySessionExamGradeBadge(graded, owned = true))
        assertNull(studySessionExamGradeBadge(graded, owned = false))
        assertNull(studySessionExamGradeBadge(sampleSaved("ungraded", "Hello."), owned = true))
    }

    @Test
    fun `session refresh updates the active title and keeps newer rename over stale data`() {
        val renamed = sampleSaved("session", "Original text").copy(name = "New title", updatedAt = 200L)
        val stale = renamed.copy(name = "Old title", updatedAt = 100L)
        val refreshedItems = mergeStudySessionItems(listOf(renamed), listOf(stale))
        val activeAfterRefresh = refreshedActiveStudySessionItem(stale, refreshedItems)

        assertEquals("New title", refreshedItems.single().name)
        assertEquals("New title", activeAfterRefresh?.name)
    }

    @Test
    fun `stale session refresh preserves a newer exam grade`() {
        val grade = StudyExamGrade(4, 5, 3, "A strong attempt.", 200L)
        val current = sampleSaved("session", "Hello.").copy(updatedAt = 200L, examGrade = grade)
        val stale = sampleSaved("session", "Hello.").copy(updatedAt = 100L)

        assertEquals(grade, mergeStudySessionSummary(current, stale).examGrade)
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

    @Test
    fun `parse mode shows text running before the skeleton exists`() {
        val progress = studyCreationProgress(
            session = null,
            loading = true,
            exactText = false,
        )
        assertEquals(StudyCreationStepStatus.RUNNING, progress.text.status)
        assertEquals(StudyCreationStepStatus.UPCOMING, progress.structure.status)
        assertEquals(StudyCreationStepStatus.UPCOMING, progress.dissections.status)
        assertEquals(StudyCreationStepStatus.UPCOMING, progress.images.status)
        assertEquals(0, progress.percent)
        assertTrue(progress.visible)
    }

    @Test
    fun `exact mode shows structure running before the skeleton exists`() {
        val progress = studyCreationProgress(
            session = null,
            loading = true,
            exactText = true,
        )
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.text.status)
        assertEquals(StudyCreationStepStatus.RUNNING, progress.structure.status)
        assertEquals(50, progress.percent)
        assertTrue(progress.visible)
    }

    @Test
    fun `skeleton reports dissection and image counts while work is in flight`() {
        val session = StudySessionResponse(
            text = "Hello world.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Hello world.",
                    tokens = listOf(
                        StudyToken(text = "Hello", status = StudyTokenStatus.PENDING),
                        StudyToken(text = "world", status = StudyTokenStatus.PENDING),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                    ),
                ),
            ),
        )
        val progress = studyCreationProgress(
            session = session,
            loading = false,
            exactText = true,
            sentenceStatus = mapOf(0 to StudySentenceLoadState.LOADING),
            messageId = "msg",
        )
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.text.status)
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.structure.status)
        assertEquals(StudyCreationStepStatus.RUNNING, progress.dissections.status)
        assertEquals(0, progress.dissections.done)
        assertEquals(1, progress.dissections.total)
        assertEquals(StudyCreationStepStatus.RUNNING, progress.images.status)
        assertEquals(0, progress.images.done)
        assertEquals(3, progress.images.total)
        assertEquals(33, progress.percent)
        assertTrue(progress.visible)
    }

    @Test
    fun `failed images count as settled so progress can finish`() {
        val session = StudySessionResponse(
            text = "Hello.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Hello.",
                    tokens = listOf(
                        StudyToken(
                            text = "Hello",
                            status = StudyTokenStatus.READY,
                            literalMeaning = "hello",
                            contextualMeaning = "greeting",
                        ),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                    ),
                ),
            ),
        )
        val progress = studyCreationProgress(
            session = session,
            loading = false,
            exactText = true,
            sentenceStatus = mapOf(0 to StudySentenceLoadState.READY),
            messageId = "msg",
            imageFailed = mapOf("msg#0" to true),
            wordImageFailed = mapOf("msg#0#0" to true),
        )
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.dissections.status)
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.images.status)
        assertEquals(2, progress.images.done)
        assertEquals(2, progress.images.total)
        assertEquals(100, progress.percent)
        assertFalse(progress.visible)
    }

    @Test
    fun `dissection errors settle the sentence and skip pending word images`() {
        val session = StudySessionResponse(
            text = "Hello.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Hello.",
                    imageUrl = "https://img/s",
                    tokens = listOf(
                        StudyToken(text = "Hello", status = StudyTokenStatus.PENDING),
                    ),
                ),
            ),
        )
        val progress = studyCreationProgress(
            session = session,
            loading = false,
            exactText = true,
            sentenceStatus = mapOf(0 to StudySentenceLoadState.ERROR),
            messageId = "msg",
        )
        assertEquals(StudyCreationStepStatus.COMPLETED, progress.dissections.status)
        assertEquals(1, progress.images.total)
        assertEquals(100, progress.percent)
        assertFalse(progress.visible)
    }

    @Test
    fun `fully built session hides creation progress`() {
        val session = StudySessionResponse(
            text = "Hello.",
            textLanguageTag = "en",
            characters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "A woman",
                    imageUrl = "https://img/p",
                ),
            ),
            sentences = listOf(
                StudySentence(
                    text = "Hello.",
                    imageUrl = "https://img/s",
                    tokens = listOf(
                        StudyToken(
                            text = "Hello",
                            status = StudyTokenStatus.READY,
                            literalMeaning = "hello",
                            contextualMeaning = "greeting",
                            imageUrl = "https://img/w",
                        ),
                    ),
                ),
            ),
        )
        val progress = studyCreationProgress(
            session = session,
            loading = false,
            exactText = true,
            sentenceStatus = mapOf(0 to StudySentenceLoadState.READY),
            messageId = "msg",
        )
        assertEquals(100, progress.percent)
        assertFalse(progress.visible)
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
