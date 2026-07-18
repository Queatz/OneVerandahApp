package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatApiTest {
    @Test
    fun `chat endpoints use backend domain`() {
        assertEquals("https://api.oneverandah.app/api/chat", chatApiUrl())
        assertEquals("https://api.oneverandah.app/api/chat/conversation-1", chatApiUrl("/conversation-1"))
        assertEquals("https://api.oneverandah.app/api/chat/speech", chatApiUrl("/speech"))
    }
}