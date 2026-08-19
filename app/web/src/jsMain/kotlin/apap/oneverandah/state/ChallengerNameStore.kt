package apap.oneverandah.state

import kotlinx.browser.window

/**
 * Remembers the neighbor's challenger name on this device so they don't
 * retype it for every upload.
 */
object ChallengerNameStore {
    private const val STORAGE_KEY = "oneverandah.challengerName"

    fun peek(): String =
        runCatching { window.localStorage.getItem(STORAGE_KEY)?.trim().orEmpty() }
            .getOrDefault("")

    fun save(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) {
            runCatching { window.localStorage.removeItem(STORAGE_KEY) }
        } else {
            runCatching { window.localStorage.setItem(STORAGE_KEY, cleaned) }
        }
    }
}
