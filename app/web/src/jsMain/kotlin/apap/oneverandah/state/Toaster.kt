package apap.oneverandah.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tiny global toast notification bus.
 *
 * [message] holds the currently visible text (or `null`). [token] increments on
 * every [show] call so an identical message shown twice in a row still
 * re-triggers the auto-dismiss timer in the host composable.
 */
object Toaster {
    var message: String? by mutableStateOf(null)
        private set

    var token: Int by mutableStateOf(0)
        private set

    fun show(text: String) {
        message = text
        token += 1
    }

    fun dismiss() {
        message = null
    }
}
