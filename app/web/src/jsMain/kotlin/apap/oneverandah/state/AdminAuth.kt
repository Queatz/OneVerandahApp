package apap.oneverandah.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window

/**
 * Holds the never-expiring admin JWT returned by `/api/v1/admin/signin`.
 *
 * Persisted in `localStorage` so a successful password entry unlocks the admin
 * portal on every later visit from the same browser.
 */
object AdminAuth {
    private const val STORAGE_KEY = "oneverandah.admin.jwt"

    var token: String? by mutableStateOf(readStored())
        private set

    val isSignedIn: Boolean get() = !token.isNullOrBlank()

    fun setToken(value: String) {
        token = value
        runCatching { window.localStorage.setItem(STORAGE_KEY, value) }
    }

    fun clear() {
        token = null
        runCatching { window.localStorage.removeItem(STORAGE_KEY) }
    }

    private fun readStored(): String? =
        runCatching { window.localStorage.getItem(STORAGE_KEY)?.takeIf { it.isNotBlank() } }
            .getOrNull()
}
