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

    @Test
    fun `fact card payload round trips as structured JSON`() {
        val miniApp: ChatMiniApp = FactMiniApp(
            factId = "balcony-drains",
            title = "Keep balcony drains clear",
            body = "Clear leaves and debris before heavy rain.",
            icon = "water_drop",
        )

        val decoded = json.decodeFromString<ChatMiniApp>(json.encodeToString(miniApp))

        assertEquals(miniApp, decoded)
        assertIs<FactMiniApp>(decoded)
    }

    @Test
    fun `study session request trims content and resolves language`() {
        val request = StudySessionRequest(text = "  Xin chào  ", languageTag = "vi-VN")

        assertEquals("Xin chào", request.normalizedOrNull()?.text)
        assertEquals("vi", request.normalizedOrNull()?.languageTag)
    }

    @Test
    fun `blank study session request is rejected`() {
        assertNull(StudySessionRequest(text = "   ", languageTag = "en").normalizedOrNull())
    }

    @Test
    fun `study dissect request normalizes and rejects empty tokens`() {
        val request = StudyDissectSentenceRequest(
            text = "  Hello world.  ",
            sentenceText = "  Hello world. ",
            tokens = listOf(" Hello ", "", "world", "."),
            textLanguageTag = "en-US",
            languageTag = "vi-VN",
        )
        val normalized = request.normalizedOrNull()
        assertEquals("Hello world.", normalized?.text)
        assertEquals("Hello world.", normalized?.sentenceText)
        assertEquals(listOf("Hello", "world", "."), normalized?.tokens)
        assertEquals("en", normalized?.textLanguageTag)
        assertEquals("vi", normalized?.languageTag)

        assertNull(
            StudyDissectSentenceRequest(
                text = "Hello",
                sentenceText = "Hello",
                tokens = emptyList(),
                textLanguageTag = "en",
                languageTag = "en",
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `study session payload round trips as structured JSON`() {
        val response = StudySessionResponse(
            text = "Tôi đang học.",
            textLanguageTag = "vi",
            sentences = listOf(
                StudySentence(
                    text = "Tôi đang học.",
                    tokens = listOf(
                        StudyToken(
                            text = "Tôi",
                            status = StudyTokenStatus.READY,
                            literalMeaning = "I",
                            register = "neutral",
                            contextualMeaning = "The speaker",
                            nearSynonyms = listOf("mình"),
                            exampleSentences = listOf(
                                StudyExampleSentence(
                                    text = "Tôi đi học.",
                                    translation = "I go to school.",
                                ),
                                StudyExampleSentence(
                                    text = "Tôi thích trà.",
                                    translation = "I like tea.",
                                ),
                                StudyExampleSentence(
                                    text = "Tôi ở đây.",
                                    translation = "I am here.",
                                ),
                            ),
                        ),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                        StudyToken(text = "đang", status = StudyTokenStatus.PENDING),
                    ),
                    imageId = "img-1",
                    imageUrl = "https://cdn.example/study.png",
                ),
            ),
            characters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "Young woman with long dark hair",
                    imageId = "char-img-1",
                    imageUrl = "https://cdn.example/lan.png",
                ),
            ),
        )

        val decoded = json.decodeFromString<StudySessionResponse>(json.encodeToString(response))

        assertEquals(response, decoded)
        assertEquals(true, decoded.sentences.single().tokens[0].isDissectable)
        assertEquals(false, decoded.sentences.single().tokens[1].isDissectable)
        assertEquals(false, decoded.sentences.single().tokens[2].isDissectable)
        assertEquals(StudyTokenStatus.PENDING, decoded.sentences.single().tokens[2].status)
        assertEquals("img-1", decoded.sentences.single().imageId)
        assertEquals("https://cdn.example/study.png", decoded.sentences.single().imageUrl)
        assertEquals("Lan", decoded.characters.single().name)
        assertEquals("https://cdn.example/lan.png", decoded.characters.single().imageUrl)
    }

    @Test
    fun `study sentence image request normalizes and rejects blanks`() {
        val normalized = StudySentenceImageRequest(
            sentenceText = "  Xin chào.  ",
            fullText = "  Xin chào.  ",
            sentenceIndex = 0,
            sentenceCount = 1,
            characters = listOf(
                StudyStoryCharacter(
                    id = " main ",
                    name = " Lan ",
                    role = " main ",
                    description = " young ",
                    imageUrl = " https://cdn.example/lan.png ",
                ),
            ),
        ).normalizedOrNull()
        assertEquals("Xin chào.", normalized?.sentenceText)
        assertEquals("Xin chào.", normalized?.fullText)
        assertEquals("Lan", normalized?.characters?.single()?.name)
        assertEquals("https://cdn.example/lan.png", normalized?.characters?.single()?.imageUrl)
        assertNull(StudySentenceImageRequest(sentenceText = "   ").normalizedOrNull())
    }

    @Test
    fun `study story characters request normalizes and rejects blanks`() {
        val normalized = StudyStoryCharactersRequest(text = "  Hello.  ", textLanguageTag = "en-US").normalizedOrNull()
        assertEquals("Hello.", normalized?.text)
        assertEquals("en", normalized?.textLanguageTag)
        assertNull(StudyStoryCharactersRequest(text = "   ").normalizedOrNull())
    }

    @Test
    fun `study dissect response round trips as structured JSON`() {
        val response = StudyDissectSentenceResponse(
            sentenceText = "Hello.",
            tokens = listOf(
                StudyToken(
                    text = "Hello",
                    status = StudyTokenStatus.READY,
                    literalMeaning = "greeting",
                    register = "neutral",
                    contextualMeaning = "hello",
                    nearSynonyms = listOf("hi"),
                    exampleSentences = listOf(
                        StudyExampleSentence(text = "Hello there.", translation = "Chào bạn."),
                        StudyExampleSentence(text = "Hello friend.", translation = "Chào người bạn."),
                        StudyExampleSentence(text = "Hello world.", translation = "Xin chào thế giới."),
                    ),
                ),
                StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
            ),
        )

        val decoded = json.decodeFromString<StudyDissectSentenceResponse>(json.encodeToString(response))
        assertEquals(response, decoded)
        assertEquals(true, decoded.tokens.first().isDissectable)
    }

    @Test
    fun `study playground request normalizes and rejects blanks`() {
        val request = StudyWordPlaygroundRequest(
            word = "  chào  ",
            sentence = "  Xin chào bạn.  ",
            textLanguageTag = "vi-VN",
            languageTag = "en-US",
            literalMeaning = " hello ",
            contextualMeaning = " greeting ",
        )
        val normalized = request.normalizedOrNull()
        assertEquals("chào", normalized?.word)
        assertEquals("Xin chào bạn.", normalized?.sentence)
        assertEquals("vi", normalized?.textLanguageTag)
        assertEquals("en", normalized?.languageTag)
        assertEquals("hello", normalized?.literalMeaning)
        assertEquals("greeting", normalized?.contextualMeaning)

        assertNull(
            StudyWordPlaygroundRequest(
                word = " ",
                sentence = "Hello",
                textLanguageTag = "en",
                languageTag = "en",
            ).normalizedOrNull(),
        )
        assertNull(
            StudyWordPlaygroundRequest(
                word = "hello",
                sentence = "  ",
                textLanguageTag = "en",
                languageTag = "en",
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `study playground response round trips as structured JSON`() {
        val response = StudyWordPlaygroundResponse(
            natural = false,
            summary = "The collocation sounds off.",
            imageId = "img-1",
        )
        val decoded = json.decodeFromString<StudyWordPlaygroundResponse>(json.encodeToString(response))
        assertEquals(response, decoded)

        val image = StudyPlaygroundImageResponse(
            imageId = "img-1",
            status = StudyPlaygroundImageStatus.COMPLETED,
            imageUrl = "https://cdn.example/comic.png",
        )
        assertEquals(image, json.decodeFromString<StudyPlaygroundImageResponse>(json.encodeToString(image)))
    }

    @Test
    fun `save study session request normalizes language and rejects blank text`() {
        val session = StudySessionResponse(
            text = "  Xin chào.  ",
            textLanguageTag = "vi-VN",
            sentences = listOf(
                StudySentence(
                    text = "Xin chào.",
                    tokens = listOf(StudyToken(text = "Xin", status = StudyTokenStatus.PENDING)),
                ),
            ),
        )
        val request = SaveStudySessionRequest(
            sourceMessageId = "  msg-1  ",
            languageTag = "en-US",
            session = session,
        )
        val normalized = request.normalizedOrNull()
        assertEquals("msg-1", normalized?.sourceMessageId)
        assertEquals("en", normalized?.languageTag)
        assertEquals("Xin chào.", normalized?.session?.text)
        assertEquals("vi", normalized?.session?.textLanguageTag)

        assertNull(
            SaveStudySessionRequest(
                languageTag = "en",
                session = StudySessionResponse(text = "  ", textLanguageTag = "en", sentences = emptyList()),
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `saved study session payload round trips as structured JSON`() {
        val saved = SavedStudySession(
            id = "study-1",
            sourceMessageId = "msg-1",
            languageTag = "en",
            text = "Xin chào.",
            textLanguageTag = "vi",
            session = StudySessionResponse(
                text = "Xin chào.",
                textLanguageTag = "vi",
                sentences = listOf(
                    StudySentence(
                        text = "Xin chào.",
                        tokens = listOf(
                            StudyToken(text = "Xin", status = StudyTokenStatus.PENDING),
                            StudyToken(text = "chào", status = StudyTokenStatus.READY, literalMeaning = "hello"),
                        ),
                    ),
                ),
            ),
            createdAt = 10L,
            updatedAt = 20L,
        )

        val decoded = json.decodeFromString<SavedStudySession>(json.encodeToString(saved))
        assertEquals(saved, decoded)
        assertEquals(StudyTokenStatus.PENDING, decoded.session.sentences.single().tokens[0].status)
    }

    @Test
    fun `speech response round trips with optional word timings`() {
        val response = SpeechResponse(
            audioBase64 = "AQID",
            contentType = "audio/mpeg",
            words = listOf(
                SpeechWordTiming(text = "Xin", beginMs = 0, endMs = 180, beginIndex = 0, endIndex = 3),
                SpeechWordTiming(text = "chào", beginMs = 200, endMs = 520, beginIndex = 4, endIndex = 8),
            ),
        )

        val decoded = json.decodeFromString<SpeechResponse>(json.encodeToString(response))
        assertEquals(response, decoded)

        val legacy = json.decodeFromString<SpeechResponse>(
            """{"audioBase64":"AQID","contentType":"audio/mpeg"}""",
        )
        assertEquals(emptyList(), legacy.words)
    }
}