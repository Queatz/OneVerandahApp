package apap.oneverandah.components

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatInputTest {
    @Test
    fun `textarea height keeps the one-line minimum`() {
        assertEquals(56, chatInputHeightPx(0))
    }

    @Test
    fun `textarea height preserves content up to three lines`() {
        assertEquals(80, chatInputHeightPx(80))
    }

    @Test
    fun `textarea height caps content at three lines`() {
        assertEquals(104, chatInputHeightPx(200))
    }

    @Test
    fun `realtime transcript preserves text that existed before the hold`() {
        val transcript = VoiceTranscript("Book a cleaner")

        assertEquals("Book a cleaner tomorrow morn", transcript.partial("tomorrow ", "morn"))
        assertEquals("Book a cleaner tomorrow morning", transcript.completed("tomorrow morning"))
    }

    @Test
    fun `hold gesture starts only after delay and stops on release`() {
        var scheduled: (() -> Unit)? = null
        var starts = 0
        var stops = 0
        val gesture = HoldToSpeakGesture(
            schedule = { action -> scheduled = action; 1 },
            cancelScheduled = { scheduled = null },
            onStart = { starts++ },
            onStop = { stops++ },
        )

        gesture.press()
        assertEquals(0, starts)
        scheduled?.invoke()
        assertEquals(1, starts)
        gesture.release()
        assertEquals(1, stops)
    }

    @Test
    fun `short press and cancelled hold never start voice input`() {
        var scheduled: (() -> Unit)? = null
        var starts = 0
        val gesture = HoldToSpeakGesture(
            schedule = { action -> scheduled = action; 1 },
            cancelScheduled = { scheduled = null },
            onStart = { starts++ },
            onStop = {},
        )

        gesture.press()
        gesture.release()
        scheduled?.invoke()
        gesture.press()
        gesture.cancel()
        scheduled?.invoke()

        assertEquals(0, starts)
    }

    @Test
    fun `Qwen session config declares realtime PCM audio`() {
        assertTrue(sessionUpdateEvent.contains("\"input_audio_format\":\"pcm\""))
    }

    @Test
    fun `releasing pointer outside input stops active voice input`() {
        var scheduled: (() -> Unit)? = null
        var stops = 0
        val gesture = HoldToSpeakGesture(
            schedule = { action -> scheduled = action; 1 },
            cancelScheduled = { scheduled = null },
            onStart = {},
            onStop = { stops++ },
        )
        val input = document.createElement("input")
        val removeListeners = installHoldListeners(input, gesture)

        input.dispatchEvent(Event("pointerdown"))
        scheduled?.invoke()
        window.dispatchEvent(Event("pointerup"))

        assertEquals(1, stops)
        removeListeners()
    }
}