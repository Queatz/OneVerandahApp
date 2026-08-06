package apap.oneverandah.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Controls whether the app uses the full desktop shell or the phone-frame
 * mobile chrome.
 *
 * Preference is remembered in `localStorage`. On viewports narrower than
 * [DESKTOP_MIN_WIDTH_PX] the layout is always mobile regardless of preference.
 * On wide viewports the user can toggle between desktop and the existing
 * phone-frame mobile layout.
 */
object LayoutModeStore {
    private const val STORAGE_KEY = "oneverandah.layout.preference"
    private const val DESKTOP_MIN_WIDTH_PX = 900
    private const val DESKTOP_MEDIA = "(min-width: ${DESKTOP_MIN_WIDTH_PX}px)"

    /** User preference when the viewport is wide enough for desktop. */
    enum class Preference {
        DESKTOP,
        MOBILE,
    }

    var preference: Preference by mutableStateOf(readStored())
        private set

    var viewportWide: Boolean by mutableStateOf(isViewportWide())
        private set

    /** True when the full-width desktop shell should be shown. */
    val isDesktopLayout: Boolean
        get() = viewportWide && preference == Preference.DESKTOP

    private var listenerAttached = false

    fun setPreference(value: Preference) {
        if (preference == value) return
        preference = value
        runCatching { window.localStorage.setItem(STORAGE_KEY, value.name.lowercase()) }
    }

    /** Flips between desktop and mobile preference (wide viewports only). */
    fun toggle() {
        setPreference(
            if (preference == Preference.DESKTOP) Preference.MOBILE else Preference.DESKTOP,
        )
    }

    /**
     * Starts listening for viewport width changes. Safe to call repeatedly —
     * only the first call attaches the listener.
     */
    fun ensureViewportListener() {
        if (listenerAttached) return
        listenerAttached = true
        val mql = window.matchMedia(DESKTOP_MEDIA)
        val apply: (Event?) -> Unit = {
            viewportWide = mql.matches
        }
        viewportWide = mql.matches
        // Modern browsers expose addEventListener on MediaQueryList.
        mql.addEventListener("change", apply)
    }

    private fun isViewportWide(): Boolean =
        runCatching { window.matchMedia(DESKTOP_MEDIA).matches }.getOrDefault(false)

    private fun readStored(): Preference {
        val raw = runCatching { window.localStorage.getItem(STORAGE_KEY) }.getOrNull()
        return when (raw?.lowercase()) {
            "mobile" -> Preference.MOBILE
            "desktop" -> Preference.DESKTOP
            else -> Preference.DESKTOP
        }
    }
}
