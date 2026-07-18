package apap.oneverandah.components

import kotlin.test.Test
import kotlin.test.assertEquals

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
}