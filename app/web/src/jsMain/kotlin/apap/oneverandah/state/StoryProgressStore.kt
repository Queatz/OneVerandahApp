package apap.oneverandah.state

import apap.oneverandah.stories.StoryLogic
import kotlinx.browser.window

/**
 * Remembers learning-mode and per-book page progress on this device.
 *
 * The saved index is the next unread page (0-based). A value equal to the
 * page count means the reader finished the book.
 */
object StoryProgressStore {
    private const val LEARNING_KEY = "stories.learningMode"

    var learningMode: Boolean
        get() = window.localStorage.getItem(LEARNING_KEY) == "1"
        set(value) {
            if (value) window.localStorage.setItem(LEARNING_KEY, "1")
            else window.localStorage.removeItem(LEARNING_KEY)
        }

    fun pageIndex(storyId: String): Int =
        window.localStorage.getItem(pageKey(storyId))?.toIntOrNull() ?: 0

    fun savePage(storyId: String, index: Int) {
        window.localStorage.setItem(pageKey(storyId), index.toString())
    }

    fun clear(storyId: String) {
        window.localStorage.removeItem(pageKey(storyId))
    }

    fun resumeIndex(storyId: String, pageCount: Int): Int =
        StoryLogic.resumePageIndex(pageIndex(storyId), pageCount)

    fun finished(storyId: String, pageCount: Int): Boolean =
        StoryLogic.isFinished(pageIndex(storyId), pageCount)

    private fun pageKey(storyId: String) = "stories.progress.$storyId"
}
