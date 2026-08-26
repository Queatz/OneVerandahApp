package apap.oneverandah.state

import kotlinx.browser.window

/**
 * Remembers the neighbor's Meet name and Zalo number on this device so
 * create and pitch forms can be prefilled.
 */
object MeetProfileStore {
    private const val NAME_KEY = "oneverandah.meetName"
    private const val ZALO_KEY = "oneverandah.meetZalo"

    fun peekName(): String =
        runCatching { window.localStorage.getItem(NAME_KEY)?.trim().orEmpty() }
            .getOrDefault("")
            .ifBlank { ChallengerNameStore.peek() }

    fun peekZalo(): String =
        runCatching { window.localStorage.getItem(ZALO_KEY)?.trim().orEmpty() }
            .getOrDefault("")

    fun save(name: String, zalo: String) {
        write(NAME_KEY, name)
        write(ZALO_KEY, zalo)
        val cleanedName = name.trim()
        if (cleanedName.isNotEmpty()) ChallengerNameStore.save(cleanedName)
    }

    private fun write(key: String, value: String) {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) {
            runCatching { window.localStorage.removeItem(key) }
        } else {
            runCatching { window.localStorage.setItem(key, cleaned) }
        }
    }
}
