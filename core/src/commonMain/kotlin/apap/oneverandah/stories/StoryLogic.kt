package apap.oneverandah.stories

import apap.oneverandah.model.Language

/**
 * Pure Mini Story rules shared by the server and (via tests) the client.
 *
 * Keep this free of I/O so publish, listing, and reader resume stay deterministic.
 */
object StoryLogic {
    const val MAX_CAST_PHOTOS = 3
    const val MIN_CAST_PHOTOS = 1
    const val IMAGE_JOB_PENDING = "pending"
    const val IMAGE_JOB_READY = "ready"
    const val IMAGE_JOB_FAILED = "failed"
    const val PUBLISH_JOB_PENDING = "pending"
    const val PUBLISH_JOB_READY = "ready"
    const val PUBLISH_JOB_FAILED = "failed"
    const val LIST_STATUS_DRAFT = "draft"
    const val LIST_STATUS_PUBLISHING = "publishing"
    const val LIST_STATUS_PUBLISHED = "published"

    /** Public shelf: published only, newest release first, then newest update. */
    fun publishedNewestFirst(stories: List<Story>): List<Story> =
        stories
            .filter { it.published }
            .sortedWith(
                compareByDescending<Story> { it.releaseDate ?: it.updatedAt }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.name.lowercase() },
            )

    /** Admin list: newest edits first so drafts stay easy to find. */
    fun adminNewestFirst(stories: List<Story>): List<Story> =
        stories.sortedWith(
            compareByDescending<Story> { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase() },
        )

    fun canPublish(story: Story): Boolean = publishError(story) == null

    fun canStartPublish(story: Story): Boolean = !story.publishing && canPublish(story)

    fun adminListStatus(story: Story): String = when {
        story.publishing -> LIST_STATUS_PUBLISHING
        story.published -> LIST_STATUS_PUBLISHED
        else -> LIST_STATUS_DRAFT
    }

    fun publishError(story: Story): String? {
        if (story.name.isBlank()) return "A book name is required"
        if (story.pages.none { it.text.isNotBlank() }) return "Add at least one page with text before publishing"
        return null
    }

    /** Drop heavy page payloads from the public grid. */
    fun toPublicSummary(story: Story): Story = story.copy(pages = emptyList())

    /**
     * Page the reader should open for [savedIndex].
     *
     * [savedIndex] is the next unread page (0-based). A value of [pageCount]
     * means the reader finished the book.
     */
    fun resumePageIndex(savedIndex: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return savedIndex.coerceIn(0, pageCount)
    }

    fun isFinished(savedIndex: Int, pageCount: Int): Boolean =
        pageCount <= 0 || savedIndex >= pageCount

    fun nextPageIndex(current: Int, pageCount: Int): Int? {
        val next = current + 1
        return next.takeIf { it < pageCount }
    }

    fun previousPageIndex(current: Int): Int? =
        (current - 1).takeIf { it >= 0 }

    fun clampCastPhotoCount(count: Int): Int =
        count.coerceIn(0, MAX_CAST_PHOTOS)

    fun hasRequiredCastPhotos(count: Int): Boolean =
        count in MIN_CAST_PHOTOS..MAX_CAST_PHOTOS

    /**
     * Best-effort language guess used when AI detection is unavailable.
     * Prefers script-specific matches over English.
     */
    fun detectLanguageFallback(text: String): String {
        val sample = text.trim()
        if (sample.isEmpty()) return Language.ENGLISH.tag
        if (sample.any { it in '\u0400'..'\u04FF' }) return Language.RUSSIAN.tag
        if (sample.any { it in '\u3040'..'\u30FF' }) return Language.JAPANESE.tag
        if (sample.any { it in '\uAC00'..'\uD7AF' }) return Language.KOREAN.tag
        if (sample.any { it in '\u4E00'..'\u9FFF' }) return Language.CHINESE.tag
        if (sample.any { it in VIETNAMESE_MARKS }) return Language.VIETNAMESE.tag
        return Language.ENGLISH.tag
    }

    fun normalizeLanguageTag(tag: String?): String = Language.fromTag(tag).tag

    /**
     * Study-session roles reuse ids like `"main"`. Mix in the portrait URL so
     * harvested characters stay independently selectable in the photo picker.
     */
    fun studyCharacterId(studyId: String, imageUrl: String): String {
        val role = studyId.trim().ifBlank { "character" }
        val digest = imageUrl.trim().hashCode().toLong() and 0xffffffffL
        return "study-$role-${digest.toString(16)}"
    }

    private const val VIETNAMESE_MARKS =
        "ăâêôơưáàảãạéèẻẽẹíìỉĩịóòỏõọúùủũụýỳỷỹỵđĂÂÊÔƠƯÁÀẢÃẠÉÈẺẼẸÍÌỈĨỊÓÒỎÕỌÚÙỦŨỤÝỲỶỸỴĐ"
}
