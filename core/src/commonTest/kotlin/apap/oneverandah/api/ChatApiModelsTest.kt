package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ChatApiModelsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `chat request trims content and resolves supported language`() {
        val request = ChatRequest(message = "  Xin chào  ", languageTag = "vi-VN")

        assertEquals("Xin chào", request.normalizedOrNull()?.message)
        assertEquals("vi", request.normalizedOrNull()?.languageTag)
    }

    @Test
    fun `blank chat request is rejected`() {
        assertNull(ChatRequest(message = "  ", languageTag = "en").normalizedOrNull())
    }

    @Test
    fun `language study payload round trips as structured JSON`() {
        val response = ChatResponse(
            conversationId = "conversation-1",
            message = ChatMessage(
                id = "message-1",
                sender = ChatSender.AI,
                text = "Let's study this sentence.",
                createdAt = 42,
                miniApp = LanguageStudyMiniApp(
                    sentence = "Tôi đang học tiếng Việt.",
                    targetLanguageTag = "vi",
                    tokens = listOf(
                        LanguageStudyToken(
                            text = "Tôi",
                            definition = "A first-person pronoun.",
                            contextualMeaning = "The speaker of the sentence.",
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<ChatResponse>(json.encodeToString(response))

        assertEquals(response, decoded)
        assertIs<LanguageStudyMiniApp>(decoded.message.miniApp)
    }

    @Test
    fun `resident card payload round trips as structured JSON`() {
        val miniApp: ChatMiniApp = ResidentCardMiniApp(
            residentId = "ha",
            name = "Ha",
            address = "A-12-03",
            phone = "0900000000",
            photo = "person",
            sex = "FEMALE",
            intro = "Neighbour and service provider.",
        )

        val decoded = json.decodeFromString<ChatMiniApp>(json.encodeToString(miniApp))

        assertEquals(miniApp, decoded)
        assertIs<ResidentCardMiniApp>(decoded)
    }
}