package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ChatApiModelsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `study session rename trims a name and rejects blank names`() {
        assertEquals("My title", RenameStudySessionRequest("  My title  ").normalizedOrNull()?.name)
        assertNull(RenameStudySessionRequest("   ").normalizedOrNull())
        assertNull(RenameStudySessionRequest().normalizedOrNull())
    }

    @Test
    fun `chat request trims content and resolves supported language`() {
        val request = ChatRequest(message = "  Xin chào  ", languageTag = "vi-VN")

        assertEquals("Xin chào", request.normalizedOrNull()?.message)
        assertEquals("vi", request.normalizedOrNull()?.languageTag)
    }

    @Test
    fun `study session request keeps typed learner languages outside the UI set`() {
        assertEquals("fr", StudySessionRequest(text = "Hello", languageTag = "French").normalizedOrNull()?.languageTag)
        assertEquals("it", StudySessionRequest(text = "Hello", languageTag = "it-IT").normalizedOrNull()?.languageTag)
        assertEquals("Klingon", StudySessionRequest(text = "Hello", languageTag = "Klingon").normalizedOrNull()?.languageTag)
    }

    @Test
    fun `study translate request trims text and preserves typed target languages`() {
        val normalized = StudyTranslateRequest(
            text = "  Hello there.  ",
            targetLanguageTag = "  Klingon  ",
            sourceLanguageTag = "  en-US  ",
        ).normalizedOrNull()

        assertEquals("Hello there.", normalized?.text)
        assertEquals("Klingon", normalized?.targetLanguageTag)
        assertEquals("en-US", normalized?.sourceLanguageTag)
        assertNull(StudyTranslateRequest(text = "Hello", targetLanguageTag = "  ").normalizedOrNull())
    }

    @Test
    fun `study session practice sentences default empty and round trip`() {
        val original = StudySessionResponse(
            text = "Xin chào.",
            textLanguageTag = "vi",
            sentences = listOf(StudySentence(text = "Xin chào.", tokens = emptyList())),
            practiceSentences = listOf(StudyExampleSentence(text = "Xin chào.", translation = "Hello.")),
        )
        val decoded = json.decodeFromString<StudySessionResponse>(json.encodeToString(original))
        assertEquals(original.practiceSentences, decoded.practiceSentences)
        val withoutField = json.decodeFromString<StudySessionResponse>(
            """{"text":"Xin chào.","textLanguageTag":"vi","sentences":[{"text":"Xin chào.","tokens":[]}]}""",
        )
        assertEquals(emptyList(), withoutField.practiceSentences)
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
            factId = "prime-location",
            title = "Prime riverside location",
            body = "One Verandah sits at 103 Bat Nan, Thanh My Loi Ward, Thu Duc City.",
            icon = "location_on",
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
    fun `study intent response round trips as structured JSON`() {
        val json = Json { encodeDefaults = true }
        val original = StudyIntentResponse(text = "Anh thích em.\nChúng ta nói chuyện được không?")
        val encoded = json.encodeToString(StudyIntentResponse.serializer(), original)
        val decoded = json.decodeFromString(StudyIntentResponse.serializer(), encoded)
        assertEquals(original, decoded)
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
                            imageId = "word-img-1",
                            imageUrl = "https://cdn.example/word.png",
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
            playScenarios = listOf(
                StudyPlayScenario(
                    id = "play-1",
                    description = "A neighbor greets you.",
                    openingLine = "Xin chào!",
                    imageId = "play-img-1",
                    imageUrl = "https://cdn.example/play.png",
                    messages = listOf(
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.OTHER,
                            text = "Xin chào!",
                            description = "A neighbor greets you.",
                            imageId = "play-img-1",
                            imageUrl = "https://cdn.example/play.png",
                        ),
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.USER,
                            text = "Chào bạn!",
                        ),
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.OTHER,
                            text = "Bạn khỏe không?",
                            description = "They smile and wait.",
                            imageId = "play-img-2",
                            imageUrl = "https://cdn.example/play2.png",
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<StudySessionResponse>(json.encodeToString(response))

        assertEquals(response, decoded)
        assertEquals(true, decoded.sentences.single().tokens[0].isDissectable)
        assertEquals("word-img-1", decoded.sentences.single().tokens[0].imageId)
        assertEquals("https://cdn.example/word.png", decoded.sentences.single().tokens[0].imageUrl)
        assertEquals(false, decoded.sentences.single().tokens[1].isDissectable)
        assertEquals(false, decoded.sentences.single().tokens[2].isDissectable)
        assertEquals(StudyTokenStatus.PENDING, decoded.sentences.single().tokens[2].status)
        assertEquals("img-1", decoded.sentences.single().imageId)
        assertEquals("https://cdn.example/study.png", decoded.sentences.single().imageUrl)
        assertEquals("Lan", decoded.characters.single().name)
        assertEquals("https://cdn.example/lan.png", decoded.characters.single().imageUrl)
        assertEquals(1, decoded.playScenarios.size)
        assertEquals(3, decoded.playScenarios.single().messages.size)
        assertEquals(StudyPlaySpeaker.USER, decoded.playScenarios.single().messages[1].speaker)
        assertEquals("https://cdn.example/play2.png", decoded.playScenarios.single().messages[2].imageUrl)
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
    fun `study word image request normalizes and rejects blanks`() {
        val normalized = StudyWordImageRequest(
            word = "  chào  ",
            contextualMeaning = "  greeting  ",
            exampleSentences = listOf(
                StudyExampleSentence(text = "  Xin chào bạn.  ", translation = "  Hello friend.  "),
                StudyExampleSentence(text = "   ", translation = "x"),
            ),
            textLanguageTag = " my ",
            characters = listOf(StudyStoryCharacter("main", " Lan ", " main ", " young Burmese woman ")),
        ).normalizedOrNull()
        assertEquals(
            StudyWordImageRequest(
                word = "chào",
                contextualMeaning = "greeting",
                exampleSentences = listOf(
                    StudyExampleSentence(text = "Xin chào bạn.", translation = "Hello friend."),
                ),
                textLanguageTag = "my",
                characters = listOf(StudyStoryCharacter("main", "Lan", "main", "young Burmese woman")),
            ),
            normalized,
        )
        assertEquals(
            null,
            StudyWordImageRequest(word = "   ", contextualMeaning = "x").normalizedOrNull(),
        )
    }

    @Test
    fun `example sentence word glosses round trip through the study API model`() {
        val example = StudyExampleSentence(
            text = "သူမက လှတယ်။",
            translation = "She is very beautiful.",
            tokens = listOf(
                StudyExampleToken("သူမ", "she", "she"),
                StudyExampleToken("လှ", "very", "very"),
            ),
        )

        assertEquals(example, json.decodeFromString<StudyExampleSentence>(json.encodeToString(example)))
        assertEquals(StudyExampleSentence(example.text, example.translation), json.decodeFromString(
            """{"text":"${example.text}","translation":"${example.translation}"}""",
        ))
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
    fun `study play scenarios request normalizes and rejects blank text`() {
        val request = StudyPlayScenariosRequest(
            text = "  Xin chào.  ",
            textLanguageTag = "vi-VN",
            languageTag = "en-US",
            characters = listOf(
                StudyStoryCharacter(
                    id = " main ",
                    name = " Lan ",
                    role = " main ",
                    description = " young woman ",
                    imageUrl = " https://cdn.example/lan.png ",
                ),
            ),
        )
        val normalized = request.normalizedOrNull()
        assertEquals("Xin chào.", normalized?.text)
        assertEquals("vi", normalized?.textLanguageTag)
        assertEquals("en", normalized?.languageTag)
        assertEquals("Lan", normalized?.characters?.single()?.name)
        assertEquals("https://cdn.example/lan.png", normalized?.characters?.single()?.imageUrl)
        assertNull(
            StudyPlayScenariosRequest(
                text = "  ",
                textLanguageTag = "vi",
                languageTag = "en",
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `play requests preserve unsupported study language tags`() {
        val scenarios = StudyPlayScenariosRequest(
            text = "မင်္ဂလာပါ။",
            textLanguageTag = "my-MM",
            languageTag = "en-US",
        ).normalizedOrNull()
        val turn = StudyPlayTurnRequest(
            text = "မင်္ဂလာပါ။",
            textLanguageTag = "my-MM",
            languageTag = "en-US",
            scenarioDescription = "A neighbor greets you.",
            userMessage = "Hello!",
        ).normalizedOrNull()

        assertEquals("my", scenarios?.textLanguageTag)
        assertEquals("en", scenarios?.languageTag)
        assertEquals("my", turn?.textLanguageTag)
        assertEquals("en", turn?.languageTag)
    }

    @Test
    fun `study play turn request normalizes history and rejects blanks`() {
        val request = StudyPlayTurnRequest(
            text = "  Hello.  ",
            textLanguageTag = "en-US",
            languageTag = "vi-VN",
            scenarioDescription = "  A neighbor greets you.  ",
            userMessage = "  Hi there!  ",
            history = listOf(
                StudyPlayHistoryMessage(
                    speaker = StudyPlaySpeaker.OTHER,
                    text = "  Hello!  ",
                    description = "  Opening  ",
                ),
                StudyPlayHistoryMessage(
                    speaker = StudyPlaySpeaker.USER,
                    text = "   ",
                ),
            ),
        )
        val normalized = request.normalizedOrNull()
        assertEquals("Hello.", normalized?.text)
        assertEquals("en", normalized?.textLanguageTag)
        assertEquals("vi", normalized?.languageTag)
        assertEquals("A neighbor greets you.", normalized?.scenarioDescription)
        assertEquals("Hi there!", normalized?.userMessage)
        assertEquals(1, normalized?.history?.size)
        assertEquals("Hello!", normalized?.history?.single()?.text)
        assertEquals("Opening", normalized?.history?.single()?.description)

        assertNull(
            StudyPlayTurnRequest(
                text = "Hello",
                textLanguageTag = "en",
                languageTag = "en",
                scenarioDescription = " ",
                userMessage = "Hi",
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `study play payloads round trip as structured JSON`() {
        val scenarios = StudyPlayScenariosResponse(
            scenarios = listOf(
                StudyPlayScenario(
                    id = "s1",
                    description = "A girl approaches you on the forest trail.",
                    openingLine = "Chào bạn!",
                    imageId = "img-1",
                ),
                StudyPlayScenario(
                    id = "s2",
                    description = "A vendor waves from a stall.",
                    openingLine = null,
                    imageId = "img-2",
                ),
            ),
        )
        assertEquals(scenarios, json.decodeFromString(json.encodeToString(scenarios)))

        val turn = StudyPlayTurnResponse(
            description = "They wait for your answer.",
            reply = "Thật sao?",
            imageId = "img-3",
        )
        assertEquals(turn, json.decodeFromString(json.encodeToString(turn)))
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
    fun `speech and saved study sessions preserve unsupported content language tags`() {
        val speech = SpeechRequest(text = " မင်္ဂလာပါ ", languageTag = "my-MM").normalizedOrNull()
        val saved = SaveStudySessionRequest(
            languageTag = "en-US",
            session = StudySessionResponse(
                text = " မင်္ဂလာပါ ",
                textLanguageTag = "my-MM",
                sentences = listOf(
                    StudySentence(
                        text = "မင်္ဂလာပါ",
                        tokens = listOf(StudyToken(text = "မင်္ဂလာပါ", status = StudyTokenStatus.PENDING)),
                    ),
                ),
            ),
        ).normalizedOrNull()

        assertEquals("my", speech?.languageTag)
        assertEquals("en", saved?.languageTag)
        assertEquals("my", saved?.session?.textLanguageTag)
    }

    @Test
    fun `speech language tag accepts ISO 639 three letter codes`() {
        assertEquals("bur", SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "bur-MM").normalizedOrNull()?.languageTag)
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

    @Test
    fun `study write check request normalizes and rejects blanks`() {
        val normalized = StudyWriteCheckRequest(
            expected = "  Hello there! ",
            attempt = " Hello  ",
            textLanguageTag = "en-US",
            languageTag = "vi-VN",
        ).normalizedOrNull()
        assertEquals("Hello there!", normalized?.expected)
        assertEquals("Hello", normalized?.attempt)
        assertEquals("en", normalized?.textLanguageTag)
        assertEquals("vi", normalized?.languageTag)

        assertNull(
            StudyWriteCheckRequest(
                expected = "   ",
                attempt = "x",
                textLanguageTag = "en",
                languageTag = "en",
            ).normalizedOrNull(),
        )
        assertNull(
            StudyWriteCheckRequest(
                expected = "x",
                attempt = "  ",
                textLanguageTag = "en",
                languageTag = "en",
            ).normalizedOrNull(),
        )
    }

    @Test
    fun `study write check response round trips as structured JSON`() {
        val json = Json { encodeDefaults = true }
        val original = StudyWriteCheckResponse(
            correct = false,
            feedback = "You swapped two words.",
        )
        val encoded = json.encodeToString(StudyWriteCheckResponse.serializer(), original)
        val decoded = json.decodeFromString(StudyWriteCheckResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `legacy saved study sessions decode without exam fields`() {
        val saved = json.decodeFromString<SavedStudySession>(
            """{"id":"study-old","languageTag":"en","text":"Hello.","textLanguageTag":"en","session":{"text":"Hello.","textLanguageTag":"en","sentences":[]},"createdAt":10,"updatedAt":20}""",
        )
        val summary = json.decodeFromString<SavedStudySessionSummary>(
            """{"id":"study-old","languageTag":"en","text":"Hello.","textLanguageTag":"en","createdAt":10,"updatedAt":20}""",
        )

        assertNull(saved.examGrade)
        assertNull(saved.build)
        assertNull(saved.session.examScene)
        assertNull(summary.examGrade)
    }

    @Test
    fun `study build request trims fields and requires translation target`() {
        val normalized = StudyBuildRequest(
            text = "  Hello there.  ",
            languageTag = "vi-VN",
            sourceMessageId = "  message-1  ",
            mode = StudyBuildMode.TRANSLATE,
            targetLanguageTag = "  Klingon  ",
            sourceLanguageTag = "  en-US  ",
        ).normalizedOrNull()

        assertEquals("Hello there.", normalized?.text)
        assertEquals("vi", normalized?.languageTag)
        assertEquals("message-1", normalized?.sourceMessageId)
        assertEquals("Klingon", normalized?.targetLanguageTag)
        assertEquals("en-US", normalized?.sourceLanguageTag)
        assertNull(StudyBuildRequest(text = "  ", languageTag = "en").normalizedOrNull())
        assertNull(
            StudyBuildRequest(text = "Hello", languageTag = "en", mode = StudyBuildMode.TRANSLATE)
                .normalizedOrNull(),
        )
        assertEquals(
            "WORD_IMAGE:2:3",
            StudyBuildElement(StudyBuildElementKind.WORD_IMAGE, sentenceIndex = 2, tokenIndex = 3).key,
        )
    }

    @Test
    fun `study build events round trip with type discriminator`() {
        val eventJson = Json { classDiscriminator = "type" }
        val snapshot = SavedStudySession(
            id = "study-1",
            languageTag = "en",
            text = "Hello.",
            textLanguageTag = "en",
            session = StudySessionResponse(text = "Hello.", textLanguageTag = "en", sentences = emptyList()),
            createdAt = 10L,
            updatedAt = 20L,
            build = StudyBuildState(status = StudyBuildStatus.BUILDING),
        )
        val events = listOf(
            StudyBuildEvent.Snapshot(snapshot),
            StudyBuildEvent.SentenceImage(index = 0, imageUrl = "https://img/hello"),
            StudyBuildEvent.Build(StudyBuildState(status = StudyBuildStatus.PARTIAL)),
            StudyBuildEvent.Heartbeat,
            StudyBuildEvent.Done,
        )

        events.forEach { event ->
            val encoded = eventJson.encodeToString<StudyBuildEvent>(event)
            assertEquals(event, eventJson.decodeFromString<StudyBuildEvent>(encoded))
            assertTrue(encoded.contains("\"type\""))
        }
    }

    @Test
    fun `study exam contracts round trip as structured JSON`() {
        val scene = StudyExamScene(
            id = "scene-1",
            setting = "At a neighborhood market.",
            beats = listOf(
                StudyExamBeat(
                    id = "listen-1",
                    kind = StudyExamBeatKind.LISTEN,
                    speakerId = "main",
                    speakerName = "Lan",
                    line = "Xin chào!",
                    prompt = "What did Lan mean?",
                    expectedMeaning = "Hello.",
                    sourceSentence = "Xin chào!",
                ),
                StudyExamBeat(
                    id = "speak-1",
                    kind = StudyExamBeatKind.SPEAK,
                    speakerId = "main",
                    speakerName = "Lan",
                    prompt = "Reply by greeting Lan.",
                    expectedMeaning = "A suitable greeting.",
                    sourceSentence = "Xin chào!",
                ),
            ),
        )
        val grade = StudyExamGrade(
            overall = 4,
            listening = 5,
            speaking = 3,
            feedback = "You understood the line and gave a useful reply.",
            scoredAt = 100L,
        )
        val scoreRequest = StudyExamScoreRequest(
            sessionId = "study-1",
            answers = listOf(StudyExamScoreAnswer(beatId = "listen-1", transcript = "Hello.")),
        )
        val scoreResponse = StudyExamScoreResponse(
            grade = grade,
            beats = listOf(StudyExamBeatScore("listen-1", 2, "Clear meaning.")),
        )

        assertEquals(scene, json.decodeFromString<StudyExamScene>(json.encodeToString(scene)))
        assertEquals(grade, json.decodeFromString<StudyExamGrade>(json.encodeToString(grade)))
        assertEquals(scoreRequest, json.decodeFromString<StudyExamScoreRequest>(json.encodeToString(scoreRequest)))
        assertEquals(scoreResponse, json.decodeFromString<StudyExamScoreResponse>(json.encodeToString(scoreResponse)))
    }

}
