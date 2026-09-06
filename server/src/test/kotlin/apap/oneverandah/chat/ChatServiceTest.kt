package apap.oneverandah.chat

import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.config.QwenChatConfig
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.model.ServiceProvider
import apap.oneverandah.model.Sex
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        val followUpQuestions = listOf("Bạn muốn biết thêm gì?", "Tôi có thể giúp gì?", "Tiếp theo là gì?")
        val ai = FakeChatAiClient(
            GeneratedChatReply(text = "Xin chào!", followUpQuestions = followUpQuestions),
        )
        val service = service(repository, ai)

        val response = service.send(ChatRequest(message = "Hello", languageTag = "vi-VN"))
        val history = repository.history(response.conversationId)

        assertEquals("vi", ai.lastRequest?.languageTag)
        assertEquals(listOf(ChatSender.YOU, ChatSender.AI), history.map { it.sender })
        assertEquals("Hello", history.first().text)
        assertEquals("Xin chào!", response.message.text)
        assertEquals(followUpQuestions, response.message.followUpQuestions)
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
    fun `catalog fact reply becomes localized fact mini app`() = runBlocking {
        val ai = FakeChatAiClient(
            GeneratedChatReply(text = "Here is a useful apartment fact.", factId = "prime-location"),
        )
        val service = service(InMemoryChatRepository(), ai)

        val response = service.send(
            ChatRequest(message = "Where is One Verandah located?", languageTag = "vi"),
        )

        val miniApp = assertIs<FactMiniApp>(response.message.miniApp)
        assertEquals("prime-location", miniApp.factId)
        assertEquals("Vị trí ven sông đắc địa", miniApp.title)
        assertEquals(true, ai.lastRequest?.facts?.any { it.id == "prime-location" })
    }

    @Test
    fun `unknown fact reply cannot create a card outside the catalog`() = runBlocking {
        val service = service(
            InMemoryChatRepository(),
            FakeChatAiClient(GeneratedChatReply(text = "No matching fact.", factId = "invented-fact")),
        )

        val response = service.send(ChatRequest(message = "Tell me something", languageTag = "en"))

        assertNull(response.message.miniApp)
    }

    @Test
    fun `Qwen receives multilingual semantic language study instructions`() = runBlocking {
        val requestBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"choices":[{"message":{"content":"{\"text\":\"Let's learn.\",\"languageStudy\":null,\"factId\":null,\"followUpQuestions\":[\"Practise another phrase\",\"Show me another example\",\"Explain the grammar\"]}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeChatAiClient(qwenConfig(), client)

        ai.generate(
            ChatGenerationRequest(
                message = "Dạy tôi cách nói về độ dính bằng tiếng Việt.",
                languageTag = "vi",
                history = emptyList(),
                matchedResident = null,
                facts = emptyList(),
            ),
        )

        // Dual short+long prompts fire in parallel.
        assertEquals(2, requestBodies.size)
        val systemPrompts = requestBodies.map { bodyText ->
            val body = Json.parseToJsonElement(bodyText).jsonObject
            assertEquals("qwen3.8-flash", body["model"]?.jsonPrimitive?.content)
            body["messages"]?.jsonArray
                ?.first()?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
        }
        val shortPrompt = systemPrompts.first { it.contains("1–2 concise sentences") || it.contains("1-2 concise sentences") }
        val systemPrompt = systemPrompts.first { it.contains("Earth-Mystical Parlance") }
        assertContains(shortPrompt, "Keep your response to 1–2 concise sentences")
        assertContains(shortPrompt, "{\"text\":\"helpful response\"}")
        assertContains(systemPrompt, "Recognize language-learning intent from meaning in every language")
        assertContains(systemPrompt, "never rely on English keywords")
        assertContains(systemPrompt, "Teach me how to talk about stickiness in Vietnamese")
        assertContains(systemPrompt, "Dạy tôi cách nói về độ dính bằng tiếng Việt")
        assertContains(systemPrompt, "教我如何用越南语谈论黏性")
        assertContains(systemPrompt, "Always provide exactly three concise, distinct followUpQuestions")
        assertContains(systemPrompt, "in the user's active language")
        assertContains(systemPrompt, "written from the user's perspective")
        assertContains(systemPrompt, "They may be questions or commands")
        assertContains(systemPrompt, "Never phrase them from the assistant's perspective")
        assertContains(systemPrompt, "Prefer short messages")
        assertContains(systemPrompt, "Earth-Mystical Parlance")
        assertContains(systemPrompt, "Lời Huyền Linh")
        assertContains(systemPrompt, "You fully understand Earth-Mystical Parlance")
        assertContains(systemPrompt, "Never claim you lack access")
        assertContains(systemPrompt, "This child's shoulders hum for healing hands")
        assertContains(systemPrompt, "Dạ, con mong tay lành chạm vai đang ngân")
        assertContains(systemPrompt, "This child")
        assertContains(systemPrompt, "Dạ, con")
        client.close()
    }

    @Test
    fun `send surfaces partial reply then persists merged final`() = runBlocking {
        val followUps = listOf("Tell me more", "Show an example", "What else")
        val ai = FakeChatAiClient(
            reply = GeneratedChatReply(
                text = "Quick take.\n\nFuller detail about One Verandah.",
                followUpQuestions = followUps,
            ),
            partialReply = GeneratedChatReply(text = "Quick take."),
        )
        val service = service(InMemoryChatRepository(), ai)
        val partials = mutableListOf<String>()

        val response = service.send(ChatRequest(message = "Hello", languageTag = "en")) { partial ->
            partials += partial.message.text
            assertEquals(true, partial.partial)
            assertEquals(emptyList(), partial.message.followUpQuestions)
        }

        assertEquals(listOf("Quick take."), partials)
        assertEquals(false, response.partial)
        assertEquals("Quick take.\n\nFuller detail about One Verandah.", response.message.text)
        assertEquals(followUps, response.message.followUpQuestions)
        assertEquals(1, ai.partialEmissions)
    }

    @Test
    fun `mergeChatReplies keeps short text first and long structured fields`() {
        val short = GeneratedChatReply(
            text = "Short answer.",
            followUpQuestions = listOf("A", "B", "C"),
        )
        val long = GeneratedChatReply(
            text = "Longer detail.",
            factId = "prime-location",
            followUpQuestions = listOf("D", "E", "F"),
        )

        val merged = mergeChatReplies(short, long)
        assertEquals("Short answer.\n\nLonger detail.", merged?.text)
        assertEquals("prime-location", merged?.factId)
        assertEquals(listOf("D", "E", "F"), merged?.followUpQuestions)
    }

    @Test
    fun `Qwen dual prompts emit partial then merge short before long`() = runBlocking {
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val system = Json.parseToJsonElement(body).jsonObject["messages"]?.jsonArray
                ?.first()?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
            val isShort = system.contains("1–2 concise sentences") || system.contains("1-2 concise sentences")
            val text = if (isShort) "Short hello." else "Longer hello with detail."
            // Make the long prompt slower so the short reply arrives first.
            if (!isShort) delay(40)
            val contentInner = if (isShort) {
                """{\"text\":\"$text\"}"""
            } else {
                """{\"text\":\"$text\",\"languageStudy\":null,\"factId\":null,\"followUpQuestions\":[\"One\",\"Two\",\"Three\"]}"""
            }
            respond(
                content = """{"choices":[{"message":{"content":"$contentInner"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeChatAiClient(qwenConfig(), client)
        val partials = mutableListOf<String>()

        val result = ai.generate(
            ChatGenerationRequest(
                message = "Hi",
                languageTag = "en",
                history = emptyList(),
                matchedResident = null,
                facts = emptyList(),
            ),
        ) { partial ->
            partials += partial.text
        }

        assertEquals(listOf("Short hello."), partials)
        assertEquals("Short hello.\n\nLonger hello with detail.", result.text)
        client.close()
    }

    @Test
    fun `speech service designs one voice and reuses it for synthesis`() = runBlocking {
        val client = FakeSpeechClient()
        val service = SpeechService(client, cache = NoOpTtsCache)

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
    fun `speech service returns cached audio for exact text language and voice`() = runBlocking {
        val client = FakeSpeechClient()
        val service = SpeechService(client, cache = InMemoryTtsCache())

        val first = service.synthesize(SpeechRequest("xin chào", "vi"))
        val second = service.synthesize(SpeechRequest("xin chào", "vi-VN"))

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, client.lastRequests.size)
        assertContentEquals(first!!.bytes, second!!.bytes)
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
              },
              "followUpQuestions": ["Practise another sentence", "Explain this phrase", "Help me pronounce it"]
            }
            """.trimIndent(),
        )

        assertEquals("Let's practise.", result.text)
        assertEquals("Tôi", result.languageStudy?.tokens?.single()?.text)
    }

    @Test
    fun `Qwen structured reply parser accepts text-only short reply`() {
        val result = QwenStructuredReplyParser.parse("""{"text":"Quick helpful answer."}""")

        assertEquals("Quick helpful answer.", result.text)
        assertNull(result.languageStudy)
        assertNull(result.factId)
        assertEquals(emptyList(), result.followUpQuestions)
    }

    @Test
    fun `Qwen structured reply parser tolerates fenced regular response`() {
        val result = QwenStructuredReplyParser.parse(
            "```json\n{\"text\":\"Hello\",\"languageStudy\":null,\"followUpQuestions\":[\"How can you help?\",\"What can I ask?\",\"What should we discuss?\"]}\n```",
        )

        assertEquals("Hello", result.text)
        assertNull(result.languageStudy)
    }

    @Test
    fun `Qwen structured reply parser accepts catalog fact id`() {
        val result = QwenStructuredReplyParser.parse(
            """{"text":"A useful fact.","languageStudy":null,"factId":"prime-location","followUpQuestions":["How close is District 1?","What ward is it in?","What else should I know?"]}""",
        )

        assertEquals("prime-location", result.factId)
    }

    @Test
    fun `Qwen structured reply parser accepts exactly three normalized follow-up questions`() {
        val result = QwenStructuredReplyParser.parse(
            """
            {
              "text": "One Verandah is a prime riverside location in Thanh My Loi.",
              "languageStudy": null,
              "factId": "prime-location",
              "followUpQuestions": [
                "  How close is District 1?  ",
                "What ward is it in?",
                "Tell me about the towers"
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "How close is District 1?",
                "What ward is it in?",
                "Tell me about the towers",
            ),
            result.followUpQuestions,
        )
    }

    @Test
    fun `Qwen structured reply parser rejects fewer than three distinct follow-up questions`() {
        val error = assertFailsWith<IllegalArgumentException> {
            QwenStructuredReplyParser.parse(
                """{"text":"Hello","followUpQuestions":["What next?","What next?",""]}""",
            )
        }

        assertContains(error.message.orEmpty(), "exactly three distinct follow-up questions")
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

    private fun qwenConfig() = QwenChatConfig(
        chatEndpoint = "https://example.test/chat",
        chatModel = "qwen3.8-flash",
        chatHeavyModel = "qwen3-max-preview",
        ttsEndpoint = "https://example.test/tts",
        ttsModel = "qwen-audio-3.0-tts-flash",
        ttsVoice = "longanhuan_v3.6",
        voiceDesignEndpoint = "https://example.test/customization",
        voiceDesignModel = "voice-enrollment",
        asrEndpoint = "wss://example.test/realtime",
        asrModel = "qwen3-asr-flash-realtime",
        apiKey = "test-key",
    )
}

private class FakeChatAiClient(
    private val reply: GeneratedChatReply,
    private val partialReply: GeneratedChatReply? = null,
) : ChatAiClient {
    var lastRequest: ChatGenerationRequest? = null
    var partialEmissions = 0

    override suspend fun generate(
        request: ChatGenerationRequest,
        onPartial: suspend (GeneratedChatReply) -> Unit,
    ): GeneratedChatReply {
        lastRequest = request
        if (partialReply != null) {
            partialEmissions += 1
            onPartial(partialReply)
        }
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
