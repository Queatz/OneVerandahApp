package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ChatApiTest {
    @Test
    fun `chat endpoints use backend domain`() {
        assertEquals("https://api.oneverandah.app/api/chat", chatApiUrl())
        assertEquals("https://api.oneverandah.app/api/chat/conversation-1", chatApiUrl("/conversation-1"))
        assertEquals("https://api.oneverandah.app/api/chat/speech", chatApiUrl("/speech"))
        assertEquals("https://api.oneverandah.app/api/chat/study", chatApiUrl("/study"))
        assertEquals("https://api.oneverandah.app/api/chat/study/dissect", chatApiUrl("/study/dissect"))
        assertEquals("https://api.oneverandah.app/api/chat/study/sessions", chatApiUrl("/study/sessions"))
        assertEquals(
            "https://api.oneverandah.app/api/chat/study/sessions/abc-123",
            chatApiUrl("/study/sessions/abc-123"),
        )
        assertEquals(
            "https://api.oneverandah.app/api/chat/study/sessions/community",
            chatApiUrl("/study/sessions/community"),
        )
    }

    @Test
    fun `study build NDJSON event lines decode with the type discriminator`() {
        val event: StudyBuildEvent = StudyBuildEvent.WordImage(
            sentenceIndex = 2,
            tokenIndex = 3,
            imageUrl = "https://img/word",
        )
        val line = Json { classDiscriminator = "type" }
            .encodeToString(StudyBuildEvent.serializer(), event)

        assertEquals(event, ChatApi.decodeStudyBuildEventLine(line))
    }
}