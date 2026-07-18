package apap.oneverandah.chat

import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.model.ServiceProvider
import apap.oneverandah.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class ChatServiceTest {
    private val resident = ServiceProvider(
        name = "Lan Nguyen",
        sex = Sex.FEMALE,
        blockAddress = "A-12-03",
        phone = "0900000000",
        photo = "person",
        intro = LocalizedText("A helpful neighbour.", "Một người hàng xóm tốt bụng."),
    )

    @Test
    fun `send persists both messages and propagates active language`() = runBlocking {
        val repository = InMemoryChatRepository()
        val ai = FakeChatAiClient(GeneratedChatReply(text = "Xin chào!"))
        val service = service(repository, ai)

        val response = service.send(ChatRequest(message = "Hello", languageTag = "vi-VN"))
        val history = repository.history(response.conversationId)

        assertEquals("vi", ai.lastRequest?.languageTag)
        assertEquals(listOf(ChatSender.YOU, ChatSender.AI), history.map { it.sender })
        assertEquals("Hello", history.first().text)
        assertEquals("Xin chào!", response.message.text)
    }

    @Test
    fun `matching a resident address returns a safe resident card`() = runBlocking {
        val service = service(
            InMemoryChatRepository(),
            FakeChatAiClient(GeneratedChatReply(text = "I found your neighbour.")),
        )

        val response = service.send(
            ChatRequest(message = "Who lives at A-12-03?", languageTag = "en"),
        )

        val card = assertIs<ResidentCardMiniApp>(response.message.miniApp)
        assertEquals(resident.id, card.residentId)
        assertEquals(resident.phone, card.phone)
    }

    @Test
    fun `structured language reply becomes language study mini app`() = runBlocking {
        val study = GeneratedLanguageStudy(
            sentence = "Tôi đang học tiếng Việt.",
            targetLanguageTag = "vi",
            tokens = listOf(LanguageStudyToken("Tôi", "I", "The speaker")),
        )
        val service = service(
            InMemoryChatRepository(),
            FakeChatAiClient(GeneratedChatReply(text = "Let's practise.", languageStudy = study)),
        )

        val response = service.send(
            ChatRequest(message = "Help me study Vietnamese", languageTag = "en"),
        )

        val miniApp = assertIs<LanguageStudyMiniApp>(response.message.miniApp)
        assertEquals(study.sentence, miniApp.sentence)
        assertEquals("Tôi", miniApp.tokens.single().text)
    }

    @Test
    fun `speech service designs one voice and reuses it for synthesis`() = runBlocking {
        val client = FakeSpeechClient()
        val service = SpeechService(client)

        service.startup()
        val speech = service.synthesize(SpeechRequest(" xin chào ", "vi-VN"))
        service.synthesize(SpeechRequest("bạn", "vi"))

        assertEquals(1, client.designCalls)
        assertEquals("one-verandah-voice", client.lastVoiceId)
        assertEquals("xin chào", client.lastRequests.first().text)
        assertEquals("vi", client.lastRequests.first().languageTag)
        assertNotNull(speech)
        assertEquals("audio/mpeg", speech.contentType)
    }

    @Test
    fun `Qwen structured reply parser accepts language study JSON`() {
        val result = QwenStructuredReplyParser.parse(
            """
            {
              "text": "Let's practise.",
              "languageStudy": {
                "sentence": "Tôi đang học.",
                "targetLanguageTag": "vi",
                "tokens": [
                  {"text":"Tôi","definition":"I","contextualMeaning":"The speaker"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("Let's practise.", result.text)
        assertEquals("Tôi", result.languageStudy?.tokens?.single()?.text)
    }

    @Test
    fun `Qwen structured reply parser tolerates fenced regular response`() {
        val result = QwenStructuredReplyParser.parse("```json\n{\"text\":\"Hello\",\"languageStudy\":null}\n```")

        assertEquals("Hello", result.text)
        assertNull(result.languageStudy)
    }

    private fun service(
        repository: ChatRepository,
        ai: ChatAiClient,
    ) = ChatService(
        repository = repository,
        aiClient = ai,
        residentDirectory = ResidentDirectory(listOf(resident)),
        clock = { 1_000L },
        idGenerator = sequenceOf("conversation-1", "user-1", "ai-1").iterator()::next,
    )
}

private class FakeChatAiClient(
    private val reply: GeneratedChatReply,
) : ChatAiClient {
    var lastRequest: ChatGenerationRequest? = null

    override suspend fun generate(request: ChatGenerationRequest): GeneratedChatReply {
        lastRequest = request
        return reply
    }
}

private class FakeSpeechClient : SpeechClient {
    var designCalls = 0
    var lastVoiceId: String? = null
    val lastRequests = mutableListOf<SpeechRequest>()

    override suspend fun designVoice(): String {
        designCalls += 1
        return "one-verandah-voice"
    }

    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
        lastRequests += request
        lastVoiceId = voiceId
        return GeneratedSpeech(byteArrayOf(1, 2, 3), "audio/mpeg")
    }
}