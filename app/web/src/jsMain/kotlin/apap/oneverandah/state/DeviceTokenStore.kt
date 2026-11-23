package apap.oneverandah.state

import apap.oneverandah.api.ChatApi
import apap.oneverandah.api.ChatApiException
import kotlinx.browser.window
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the server-issued device token used to scope saved chat messages.
 *
 * Only the opaque token is kept in `localStorage`; message bodies live on the backend.
 */
object DeviceTokenStore {
    private const val STORAGE_KEY = "oneverandah.deviceToken"

    private val mutex = Mutex()
    private var cached: String? = readStored()

    /** Returns the cached token if one is already available locally. */
    fun peek(): String? = cached ?: readStored().also { cached = it }

    /**
     * Ensures a device token exists, issuing one from the server when missing.
     *
     * @throws ChatApiException when the server cannot issue a token
     */
    suspend fun ensureToken(): String {
        peek()?.let { return it }
        return mutex.withLock {
            peek()?.let { return@withLock it }
            val issued = ChatApi.createDeviceToken()
            persist(issued)
            cached = issued
            issued
        }
    }

    /** Clears the local token only if it still matches [expected], avoiding wiping a freshly issued token. */
    fun clearIfMatches(expected: String) {
        if (peek() == expected) {
            clear()
        }
    }

    /** Clears the local token (e.g. after a forced reset). */
    fun clear() {
        cached = null
        runCatching { window.localStorage.removeItem(STORAGE_KEY) }
    }

    private fun persist(token: String) {
        runCatching { window.localStorage.setItem(STORAGE_KEY, token) }
    }

    private fun readStored(): String? =
        runCatching { window.localStorage.getItem(STORAGE_KEY)?.takeIf { it.isNotBlank() } }
            .getOrNull()
}
