package apap.oneverandah.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.events.Event

/** Controls the app's chrome-free fullscreen layout and browser fullscreen state. */
object FullscreenStore {
    var active: Boolean by mutableStateOf(false)
        private set

    private var listenerAttached = false

    fun enter() {
        active = true
        val root = document.documentElement ?: return
        val element = root.asDynamic()
        val request = element.requestFullscreen
            ?: element.webkitRequestFullscreen
            ?: element.webkitRequestFullScreen
            ?: element.mozRequestFullScreen
            ?: element.msRequestFullscreen
        if (request == null) return

        val result = runCatching { request.call(root) }.getOrNull()
        if (result != null) runCatching { result.catch { } }
    }

    fun exit() {
        active = false
        if (currentFullscreenElement() == null) return
        val doc = document.asDynamic()
        val exit = doc.exitFullscreen
            ?: doc.webkitExitFullscreen
            ?: doc.webkitCancelFullScreen
            ?: doc.mozCancelFullScreen
            ?: doc.msExitFullscreen
        if (exit != null) runCatching { exit.call(document) }
    }

    /** Starts listening once so browser/Escape exits restore the regular app chrome. */
    fun ensureFullscreenChangeListener() {
        if (listenerAttached) return
        listenerAttached = true
        val listener: (Event) -> Unit = {
            if (active && currentFullscreenElement() == null) active = false
        }
        document.addEventListener("fullscreenchange", listener)
        document.addEventListener("webkitfullscreenchange", listener)
        document.addEventListener("mozfullscreenchange", listener)
        document.addEventListener("MSFullscreenChange", listener)
    }

    private fun currentFullscreenElement(): Element? {
        val doc = document.asDynamic()
        return (
            doc.fullscreenElement
                ?: doc.webkitFullscreenElement
                ?: doc.mozFullScreenElement
                ?: doc.msFullscreenElement
            ) as? Element
    }
}
