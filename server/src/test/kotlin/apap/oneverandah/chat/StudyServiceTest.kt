package apap.oneverandah.chat

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExamSceneRequest
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.api.StudyExamScoreAnswer
import apap.oneverandah.api.StudyExamScoreRequest
import apap.oneverandah.api.StudyPlayHistoryMessage
import apap.oneverandah.api.StudyPlayScenariosRequest
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudyPlayTurnRequest
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTranslateRequest
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWriteCheckRequest
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.images.PromptFactory
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyListenCheckRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class StudyServiceTest {
    @Test
    fun `skeleton normalizes request language and forces pending tokens`() = runBlocking {
        val ai = FakeStudyAiClient(
            skeleton = StudySessionResponse(
                text = "ignored",
                textLanguageTag = "vi-VN",
                sentences = listOf(
                    StudySentence(
                        text = "  Xin chào.  ",
                        tokens = listOf(
                            StudyToken(text = "Xin chào", status = StudyTokenStatus.READY, literalMeaning = "Hello"),
                            StudyToken(text = ".", status = StudyTokenStatus.READY),
                        ),
                    ),
                ),
            ),
        )
        val service = StudyService(ai)

        val response = service.skeleton(StudySessionRequest(text = "  Xin chào.  ", languageTag = "en-US"))

        assertEquals("en", ai.lastExtractRequest?.languageTag)
        assertEquals("Xin chào.", ai.lastExtractRequest?.text)
        assertEquals("en", ai.lastSkeletonRequest?.languageTag)
        assertEquals("Xin chào.", ai.lastSkeletonRequest?.text)
        assertEquals("Xin chào.", response.text)
        assertEquals("vi", response.textLanguageTag)
        assertEquals("Xin chào.", response.sentences.single().text)
        val word = response.sentences.single().tokens.first()
        assertEquals(StudyTokenStatus.PENDING, word.status)
        assertEquals("", word.literalMeaning)
        assertFalse(word.isDissectable)
        assertEquals(StudyTokenStatus.PLAIN, response.sentences.single().tokens.last().status)
    }

    @Test
    fun `skeleton preserves unsupported content language tags`() = runBlocking {
        val ai = FakeStudyAiClient(
            skeleton = StudySessionResponse(
                text = "ignored",
                textLanguageTag = "my-MM",
                sentences = listOf(
                    StudySentence(
                        text = "မင်္ဂလာပါ",
                        tokens = listOf(StudyToken(text = "မင်္ဂလာပါ", status = StudyTokenStatus.PENDING)),
                    ),
                ),
            ),
        )

        val response = StudyService(ai).skeleton(StudySessionRequest(text = "မင်္ဂလာပါ", languageTag = "en"))

        assertEquals("my", response.textLanguageTag)
    }

    @Test
    fun `skeleton rejects blank text`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.skeleton(StudySessionRequest(text = "   ", languageTag = "en"))
        }
        Unit
    }

    @Test
    fun `intended study text composes learner prompt before skeleton`() = runBlocking {
        val prompt = "Give me a few Vietnamese sentences about talking about relationships."
        val composed = "Anh thích em.\nChúng ta nói chuyện được không?"
        val ai = FakeStudyAiClient(composedText = composed)
        val service = StudyService(ai)

        val response = service.intendedStudyText(StudySessionRequest(text = "  $prompt  ", languageTag = "en-US"))

        assertEquals("en", ai.lastIntentRequest?.languageTag)
        assertEquals(prompt, ai.lastIntentRequest?.text)
        assertEquals(composed, response.text)
        assertEquals(null, ai.lastSkeletonRequest)
    }

    @Test
    fun `intended study text falls back to original when compose is blank`() = runBlocking {
        val ai = FakeStudyAiClient(composedText = "   ")
        val service = StudyService(ai)

        val response = service.intendedStudyText(StudySessionRequest(text = "Xin chào.", languageTag = "en"))

        assertEquals("Xin chào.", response.text)
    }

    @Test
    fun `intended study text rejects blank prompt`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.intendedStudyText(StudySessionRequest(text = "   ", languageTag = "en"))
        }
        Unit
    }

    @Test
    fun `skeleton extracts study text before tokenization for any language`() = runBlocking {
        val mixed = """
            A natural greeting in Spanish is:

            ¿Cómo estás?

            You can also say:

            Encantado de conocerte.
        """.trimIndent()
        val extracted = "¿Cómo estás?\nEncantado de conocerte."
        val ai = FakeStudyAiClient(
            extractedText = extracted,
            skeleton = StudySessionResponse(
                text = "ignored",
                textLanguageTag = "es",
                sentences = listOf(
                    StudySentence(
                        text = "¿Cómo estás?",
                        tokens = listOf(StudyToken(text = "¿Cómo estás?", status = StudyTokenStatus.PENDING)),
                    ),
                    StudySentence(
                        text = "Encantado de conocerte.",
                        tokens = listOf(StudyToken(text = "Encantado de conocerte.", status = StudyTokenStatus.PENDING)),
                    ),
                ),
            ),
        )
        val service = StudyService(ai)

        val response = service.skeleton(StudySessionRequest(text = mixed, languageTag = "en"))

        assertEquals(mixed, ai.lastExtractRequest?.text)
        assertEquals("en", ai.lastExtractRequest?.languageTag)
        assertEquals(extracted, ai.lastSkeletonRequest?.text)
        assertEquals(extracted, response.text)
        assertEquals("es", response.textLanguageTag)
    }

    @Test
    fun `skeleton falls back to original text when extract is blank`() = runBlocking {
        val ai = FakeStudyAiClient(
            extractedText = "   ",
            skeleton = StudySessionResponse(
                text = "ignored",
                textLanguageTag = "en",
                sentences = listOf(
                    StudySentence(
                        text = "Practice this English paragraph about the park.",
                        tokens = listOf(
                            StudyToken(text = "Practice", status = StudyTokenStatus.PENDING),
                        ),
                    ),
                ),
            ),
        )
        val service = StudyService(ai)

        val response = service.skeleton(
            StudySessionRequest(text = "Practice this English paragraph about the park.", languageTag = "en"),
        )

        assertEquals("Practice this English paragraph about the park.", ai.lastSkeletonRequest?.text)
        assertEquals("Practice this English paragraph about the park.", response.text)
    }

    @Test
    fun `extract parser accepts fenced payload and studyText alias`() {
        assertEquals(
            "สวัสดีครับ",
            QwenStudyExtractParser.parse(
                """
                ```json
                {"studyText":"สวัสดีครับ"}
                ```
                """.trimIndent(),
            ),
        )
        assertEquals(
            "مرحبا",
            QwenStudyExtractParser.parse("""{"text":"مرحبا"}"""),
        )
    }

    @Test
    fun `skeleton rejects empty sentence payload`() = runBlocking {
        val service = StudyService(
            FakeStudyAiClient(
                skeleton = StudySessionResponse(
                    text = "Hello",
                    textLanguageTag = "en",
                    sentences = emptyList(),
                ),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            service.skeleton(StudySessionRequest(text = "Hello", languageTag = "en"))
        }
        assertContains(error.message.orEmpty(), "at least one sentence")
        Unit
    }

    @Test
    fun `dissect aligns tokens and marks ready lexical words`() = runBlocking {
        val ai = FakeStudyAiClient(
            dissect = StudySentence(
                text = "  Xin chào. ",
                tokens = listOf(
                    StudyToken(
                        text = "Xin chào",
                        literalMeaning = "  Hello  ",
                        register = " neutral ",
                        contextualMeaning = " Greeting ",
                        nearSynonyms = listOf(" chào ", "chào", "  "),
                        exampleSentences = listOf(
                            StudyExampleSentence(
                                text = " Xin chào bạn. ",
                                translation = " Hello friend. ",
                            ),
                            StudyExampleSentence(
                                text = "Chào buổi sáng.",
                                translation = "Good morning.",
                            ),
                            StudyExampleSentence(
                                text = "Chào nhé.",
                                translation = "  Bye for now. ",
                            ),
                        ),
                    ),
                    StudyToken(text = ".", literalMeaning = "", register = "", contextualMeaning = ""),
                ),
            ),
        )
        val service = StudyService(ai)

        val response = service.dissectSentence(
            StudyDissectSentenceRequest(
                text = "Xin chào.",
                sentenceText = "Xin chào.",
                tokens = listOf("Xin chào", "."),
                textLanguageTag = "vi-VN",
                languageTag = "en-US",
            ),
        )

        assertEquals("en", ai.lastDissectRequest?.languageTag)
        assertEquals("vi", ai.lastDissectRequest?.textLanguageTag)
        assertEquals("Xin chào.", response.sentenceText)
        val word = response.tokens.first()
        assertEquals(StudyTokenStatus.READY, word.status)
        assertEquals("Hello", word.literalMeaning)
        assertEquals("neutral", word.register)
        assertEquals(listOf("chào"), word.nearSynonyms)
        assertEquals(
            listOf(
                StudyExampleSentence(text = "Xin chào bạn.", translation = "Hello friend."),
                StudyExampleSentence(text = "Chào buổi sáng.", translation = "Good morning."),
                StudyExampleSentence(text = "Chào nhé.", translation = "Bye for now."),
            ),
            word.exampleSentences,
        )
        assertTrue(word.isDissectable)
        assertEquals("Greeting", word.contextualMeaning)
        // Pre-generate word visual job id for dissectable tokens.
        assertNotNull(word.imageId)
        assertTrue(word.imageId!!.isNotBlank())
        assertEquals(null, word.imageUrl)
        assertEquals(StudyTokenStatus.PLAIN, response.tokens.last().status)
        assertFalse(response.tokens.last().isDissectable)
        assertEquals(null, response.tokens.last().imageId)
    }

    @Test
    fun `dissect rejects token mismatch`() = runBlocking {
        val service = StudyService(
            FakeStudyAiClient(
                dissect = StudySentence(
                    text = "Hello",
                    tokens = listOf(StudyToken(text = "Hi", literalMeaning = "greeting")),
                ),
            ),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            service.dissectSentence(
                StudyDissectSentenceRequest(
                    text = "Hello",
                    sentenceText = "Hello",
                    tokens = listOf("Hello"),
                    textLanguageTag = "en",
                    languageTag = "en",
                ),
            )
        }
        assertContains(error.message.orEmpty(), "match skeleton")
        Unit
    }

    @Test
    fun `dissect rejects blank tokens`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.dissectSentence(
                StudyDissectSentenceRequest(
                    text = "Hello",
                    sentenceText = "Hello",
                    tokens = emptyList(),
                    textLanguageTag = "en",
                    languageTag = "en",
                ),
            )
        }
        Unit
    }

    @Test
    fun `skeleton parser accepts fenced multi-sentence payload`() {
        val result = QwenStudySkeletonParser.parse(
            """
            ```json
            {
              "textLanguageTag": "vi",
              "sentences": [
                {
                  "text": "Tôi học.",
                  "tokens": [
                    {"text": "Tôi", "status": "PENDING"},
                    {"text": "học", "status": "PENDING"},
                    {"text": ".", "status": "PLAIN"}
                  ]
                },
                {
                  "text": "Bạn khỏe không?",
                  "tokens": [
                    {"text": "Bạn"},
                    {"text": "khỏe"},
                    {"text": "không"},
                    {"text": "?"}
                  ]
                }
              ]
            }
            ```
            """.trimIndent(),
        )

        assertEquals("vi", result.textLanguageTag)
        assertEquals(2, result.sentences.size)
        assertEquals("Tôi", result.sentences.first().tokens.first().text)
        assertEquals(StudyTokenStatus.PENDING, result.sentences.first().tokens.first().status)
        assertEquals(StudyTokenStatus.PLAIN, result.sentences.first().tokens.last().status)
        assertEquals(StudyTokenStatus.PLAIN, result.sentences.last().tokens.last().status)
    }

    @Test
    fun `skeleton parser skips malformed sentence elements like production failure`() {
        // Production: Expected start of the object '{', but had '"' instead at path: $.sentences[1]
        // with trailing junk resembling }]},"sentences"  ]}
        val result = QwenStudySkeletonParser.parse(
            """
            {
              "textLanguageTag": "en",
              "sentences": [
                {
                  "text": "Hello world.",
                  "tokens": [
                    {"text": "Hello", "status": "PENDING"},
                    {"text": "world", "status": "PENDING"},
                    {"text": ".", "status": "PLAIN"}
                  ]
                },
                "this should be an object not a string",
                null,
                42,
                {
                  "text": "Second sentence.",
                  "tokens": [
                    {"text": "Second"},
                    {"text": "sentence"},
                    {"text": "."}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, result.sentences.size)
        assertEquals("Hello world.", result.sentences[0].text)
        assertEquals("Second sentence.", result.sentences[1].text)
    }

    @Test
    fun `skeleton parser rejects empty salvage`() {
        val error = assertFailsWith<IllegalArgumentException> {
            QwenStudySkeletonParser.parse(
                """{"textLanguageTag":"en","sentences":["bad", null, 1]}""",
            )
        }
        assertContains(error.message.orEmpty(), "at least one valid sentence")
    }

    @Test
    fun `skeleton parser rejects missing sentences`() {
        val error = assertFailsWith<IllegalArgumentException> {
            QwenStudySkeletonParser.parse("""{"textLanguageTag":"en","sentences":[]}""")
        }
        assertContains(error.message.orEmpty(), "at least one valid sentence")
    }

    @Test
    fun `dissect parser accepts fenced payload and skips bad token entries`() {
        val result = QwenStudyDissectParser.parse(
            """
            ```json
            {
              "sentenceText": "Tôi học.",
              "tokens": [
                {
                  "text": "Tôi",
                  "literalMeaning": "I",
                  "register": "neutral",
                  "contextualMeaning": "speaker",
                  "nearSynonyms": ["mình"],
                  "exampleSentences": [
                    {"text": "Tôi đi.", "translation": "I go."},
                    {"text": "Tôi ăn.", "translation": "I eat."},
                    {"text": "Tôi ngủ.", "translation": "I sleep."}
                  ]
                },
                "not-an-object",
                {
                  "text": "học",
                  "literalMeaning": "to study",
                  "register": "neutral",
                  "contextualMeaning": "learning",
                  "nearSynonyms": ["nghiên cứu"],
                  "exampleSentences": [
                    {"text": "Học đi.", "translation": "Go study."},
                    "Học tốt.",
                    {"sentence": "Học sớm.", "translation": "Study early."}
                  ]
                },
                {"text": ".", "literalMeaning": "", "register": "", "contextualMeaning": "", "nearSynonyms": [], "exampleSentences": []}
              ]
            }
            ```
            """.trimIndent(),
        )

        assertEquals("Tôi học.", result.text)
        assertEquals(3, result.tokens.size)
        assertEquals(StudyTokenStatus.READY, result.tokens[0].status)
        assertEquals(3, result.tokens[0].exampleSentences.size)
        assertEquals("I go.", result.tokens[0].exampleSentences[0].translation)
        assertEquals(
            listOf(
                StudyExampleSentence(text = "Học đi.", translation = "Go study."),
                StudyExampleSentence(text = "Học tốt.", translation = ""),
                StudyExampleSentence(text = "Học sớm.", translation = "Study early."),
            ),
            result.tokens[1].exampleSentences,
        )
        assertEquals(StudyTokenStatus.PLAIN, result.tokens.last().status)
    }

    @Test
    fun `dissect parser rejects empty tokens`() {
        val error = assertFailsWith<IllegalArgumentException> {
            QwenStudyDissectParser.parse("""{"sentenceText":"Hi","tokens":[]}""")
        }
        assertContains(error.message.orEmpty(), "at least one valid token")
    }

    @Test
    fun `playground judges usage and starts async comic image`() = runBlocking {
        val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
        val ai = FakeStudyAiClient(
            playground = StudyWordPlaygroundJudgment(
                natural = true,
                summary = "  Sounds natural and friendly.  ",
            ),
        )
        val service = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/comic.png"
            },
            idGenerator = { "image-1" },
        )

        val response = service.playground(
            StudyWordPlaygroundRequest(
                word = "  chào  ",
                sentence = "  Xin chào bạn.  ",
                textLanguageTag = "vi-VN",
                languageTag = "en-US",
                literalMeaning = " hello ",
                contextualMeaning = " greeting ",
            ),
        )

        assertEquals(true, response.natural)
        assertEquals("Sounds natural and friendly.", response.summary)
        assertEquals("image-1", response.imageId)
        assertEquals("chào", ai.lastPlaygroundRequest?.word)
        assertEquals("Xin chào bạn.", ai.lastPlaygroundRequest?.sentence)
        assertEquals("vi", ai.lastPlaygroundRequest?.textLanguageTag)
        assertEquals("en", ai.lastPlaygroundRequest?.languageTag)
        assertEquals("hello", ai.lastPlaygroundRequest?.literalMeaning)

        var image = service.playgroundImage("image-1")
        assertNotNull(image)
        // Async generation may still be in flight for a moment.
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("image-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertEquals("https://cdn.example/comic.png", image.imageUrl)
        assertEquals("Xin chào bạn.", ai.lastSceneRequest?.sentence)
        assertEquals("chào", ai.lastSceneRequest?.word)
        assertEquals("greeting", ai.lastSceneRequest?.contextualMeaning)
        assertEquals(true, ai.lastSceneRequest?.natural)
        assertContains(prompts.single(), FakeStudyAiClient.INTENDED_SCENE)
        assertContains(prompts.single(), PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompts.single().contains("Xin chào bạn."))
        assertTrue(!prompts.single().contains("chào", ignoreCase = true))
    }

    @Test
    fun `playground falls back to an English scene without source wording`() = runBlocking {
        val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
        val ai = FakeStudyAiClient(intendedScene = "")
        val service = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/fallback.png"
            },
            idGenerator = { "fallback-image" },
        )

        val response = service.playground(
            StudyWordPlaygroundRequest(
                word = "chào",
                sentence = "Xin chào bạn.",
                textLanguageTag = "vi",
                languageTag = "en",
                contextualMeaning = "greeting",
            ),
        )
        var image = service.playgroundImage(response.imageId)
        var attempts = 0
        while (
            image?.status != StudyPlaygroundImageStatus.COMPLETED &&
            image?.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage(response.imageId)
            attempts++
        }

        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image?.status)
        assertContains(prompts.single(), "A cinematic movie still of greeting")
        assertContains(prompts.single(), PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompts.single().contains("Xin chào bạn."))
        assertTrue(!prompts.single().contains("chào", ignoreCase = true))
    }

    @Test
    fun `playground rejects blank sentence`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.playground(
                StudyWordPlaygroundRequest(
                    word = "hello",
                    sentence = "   ",
                    textLanguageTag = "en",
                    languageTag = "en",
                ),
            )
        }
        Unit
    }

    @Test
    fun `playground image marks failed when generator is absent`() = runBlocking {
        val service = StudyService(
            aiClient = FakeStudyAiClient(
                playground = StudyWordPlaygroundJudgment(natural = false, summary = "Awkward collocation."),
            ),
            imageGenerator = null,
            idGenerator = { "no-gen" },
        )
        val response = service.playground(
            StudyWordPlaygroundRequest(
                word = "hello",
                sentence = "Hello the morning.",
                textLanguageTag = "en",
                languageTag = "en",
            ),
        )
        assertEquals(false, response.natural)
        assertEquals(StudyPlaygroundImageStatus.FAILED, service.playgroundImage(response.imageId)?.status)
    }

    @Test
    fun `playground parser accepts aliases and fences`() {
        val result = QwenStudyPlaygroundParser.parse(
            """
            ```json
            {"isNatural":"true","feedback":"Natural everyday greeting."}
            ```
            """.trimIndent(),
        )
        assertTrue(result.natural)
        assertEquals("Natural everyday greeting.", result.summary)
    }

    @Test
    fun `playground prompt uses intended scene and realistic style`() {
        val prompt = playgroundComicPrompt(
            intendedScene = "A neighbor waves from a sunlit apartment doorway.",
            natural = true,
        )
        assertContains(prompt, "pretty realistic expressive art")
        assertContains(prompt, "A neighbor waves from a sunlit apartment doorway.")
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertContains(prompt, "zero written language")
        assertTrue(!prompt.contains("chào", ignoreCase = true))
    }

    @Test
    fun `word visual prompt uses intended scene and stays in English`() {
        val prompt = wordVisualPrompt(
            intendedScene = "A neighbor waves from a sunlit apartment doorway, warm cinematic medium shot",
        )
        assertContains(prompt, "pretty realistic expressive art")
        assertContains(prompt, "A neighbor waves from a sunlit apartment doorway")
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertContains(prompt, "Absolutely no text")
        assertContains(prompt, STUDY_STORY_ART_STYLE.take(20))
        assertTrue(!prompt.contains("chào"))
    }

    @Test
    fun `word image starts async expressive visual`() = runBlocking {
        val prompts = mutableListOf<String>()
        val fake = FakeStudyAiClient()
        val service = StudyService(
            aiClient = fake,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/word.png"
            },
            idGenerator = { "word-image-1" },
        )

        val response = service.wordImage(
            StudyWordImageRequest(
                word = "  chào  ",
                contextualMeaning = "  greeting  ",
                exampleSentences = listOf(
                    StudyExampleSentence(text = "Xin chào bạn.", translation = "Hello friend."),
                    StudyExampleSentence(text = "Chào buổi sáng.", translation = "Good morning."),
                ),
            ),
        )
        assertEquals("word-image-1", response.imageId)

        var image = service.playgroundImage("word-image-1")
        assertNotNull(image)
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("word-image-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertEquals("https://cdn.example/word.png", image.imageUrl)
        assertEquals("Xin chào bạn.", fake.lastSceneRequest?.sentence)
        assertEquals("chào", fake.lastSceneRequest?.word)
        assertTrue(prompts.single().contains(FakeStudyAiClient.INTENDED_SCENE))
        assertTrue(prompts.single().contains(PromptFactory.ENGLISH_ONLY))
        assertTrue(!prompts.single().contains("chào"))
        assertTrue(!prompts.single().contains("Xin chào bạn."))
    }

    @Test
    fun `dissect pre-generates word images for lexical tokens`() = runBlocking {
        val prompts = mutableListOf<String>()
        val ids = mutableListOf("w1", "w2")
        val fake = FakeStudyAiClient(
            dissect = StudySentence(
                text = "Xin chào.",
                tokens = listOf(
                    StudyToken(
                        text = "Xin chào",
                        literalMeaning = "Hello",
                        contextualMeaning = "Friendly greeting",
                        register = "neutral",
                        exampleSentences = listOf(
                            StudyExampleSentence(text = "Xin chào bạn.", translation = "Hello friend."),
                        ),
                    ),
                    StudyToken(text = "."),
                ),
            ),
        )
        val service = StudyService(
            aiClient = fake,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/${prompts.size}.png"
            },
            idGenerator = { ids.removeFirst() },
        )

        val response = service.dissectSentence(
            StudyDissectSentenceRequest(
                text = "Xin chào.",
                sentenceText = "Xin chào.",
                tokens = listOf("Xin chào", "."),
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )
        assertEquals("w1", response.tokens.first().imageId)
        assertEquals(null, response.tokens.last().imageId)

        var image = service.playgroundImage("w1")
        var attempts = 0
        while (
            image != null &&
            image.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("w1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image?.status)
        assertEquals("https://cdn.example/1.png", image?.imageUrl)
        assertEquals("Xin chào bạn.", fake.lastSceneRequest?.sentence)
        assertEquals("Xin chào", fake.lastSceneRequest?.word)
        assertTrue(prompts.single().contains(FakeStudyAiClient.INTENDED_SCENE))
        assertTrue(prompts.single().contains(PromptFactory.ENGLISH_ONLY))
        assertTrue(!prompts.single().contains("Xin chào"))
        assertTrue(!prompts.single().contains("Friendly greeting"))
    }

    @Test
    fun `sentence image starts async expressive visual`() = runBlocking {
        val prompts = mutableListOf<String>()
        val fake = FakeStudyAiClient()
        val service = StudyService(
            aiClient = fake,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/sentence.png"
            },
            idGenerator = { "sentence-image-1" },
        )

        val response = service.sentenceImage(
            StudySentenceImageRequest(sentenceText = "  Xin chào bạn.  "),
        )
        assertEquals("sentence-image-1", response.imageId)

        var image = service.playgroundImage("sentence-image-1")
        assertNotNull(image)
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("sentence-image-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertEquals("https://cdn.example/sentence.png", image.imageUrl)
        assertEquals("Xin chào bạn.", fake.lastSceneRequest?.sentence)
        assertTrue(prompts.single().contains(FakeStudyAiClient.INTENDED_SCENE))
        assertTrue(prompts.single().contains(PromptFactory.ENGLISH_ONLY))
        assertTrue(!prompts.single().contains("Xin chào bạn."))
    }

    @Test
    fun `word image falls back to English meaning when scene design is empty`() = runBlocking {
        val prompts = mutableListOf<String>()
        val service = StudyService(
            aiClient = FakeStudyAiClient(intendedScene = ""),
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/word-fallback.png"
            },
            idGenerator = { "word-fallback-1" },
        )

        service.wordImage(
            StudyWordImageRequest(word = "chào", contextualMeaning = "a friendly greeting"),
        )
        var image = service.playgroundImage("word-fallback-1")
        var attempts = 0
        while (
            image != null &&
            image.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("word-fallback-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image?.status)
        assertTrue(prompts.single().contains("a friendly greeting"))
        assertTrue(prompts.single().contains(PromptFactory.ENGLISH_ONLY))
        assertTrue(!prompts.single().contains("chào"))
    }

    @Test
    fun `sentence image rejects blank text`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.sentenceImage(StudySentenceImageRequest(sentenceText = "   "))
        }
        Unit
    }

    @Test
    fun `sentence visual prompt mentions intended scene and no speech bubbles`() {
        val prompt = sentenceVisualPrompt("A student studies at a sunlit desk")
        assertContains(prompt, "A student studies at a sunlit desk")
        assertContains(prompt, "no speech bubbles")
        assertContains(prompt, "language learners")
        assertContains(prompt, "Absolutely no text")
        assertContains(prompt, "pretty realistic expressive art")
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompt.contains("Tôi đang học."))
    }

    @Test
    fun `sentence visual prompt names characters and reference images`() {
        val prompt = sentenceVisualPrompt(
            intendedScene = "Lan smiles from a sunlit doorway",
            sentenceIndex = 0,
            sentenceCount = 2,
            characters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "young woman, long dark hair",
                    imageUrl = "https://cdn.example/lan.png",
                ),
                StudyStoryCharacter(
                    id = "secondary",
                    name = "Minh",
                    role = "secondary",
                    description = "young man, short hair",
                    imageUrl = "https://cdn.example/minh.png",
                ),
            ),
        )
        assertContains(prompt, "scene 1 of 2")
        assertContains(prompt, "Image 1 is main character \"Lan\"")
        assertContains(prompt, "Image 2 is secondary character \"Minh\"")
        assertContains(prompt, "Lan smiles from a sunlit doorway")
        assertContains(prompt, STUDY_STORY_ART_STYLE.take(20))
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompt.contains("mỉm cười"))
    }

    @Test
    fun `story characters design starts portrait jobs`() = runBlocking {
        val ids = mutableListOf("char-1", "char-2")
        val prompts = mutableListOf<String>()
        val refs = mutableListOf<List<String>>()
        val service = StudyService(
            aiClient = FakeStudyAiClient(
                characters = listOf(
                    StudyCharacterDesign(
                        id = "main",
                        name = "Lan",
                        role = "main",
                        description = "Young Vietnamese woman",
                    ),
                    StudyCharacterDesign(
                        id = "secondary",
                        name = "Minh",
                        role = "secondary",
                        description = "Young Vietnamese man",
                    ),
                ),
            ),
            imageGenerator = StudyPlaygroundImageGenerator { prompt, referenceImageUrls ->
                prompts += prompt
                refs += referenceImageUrls
                "https://cdn.example/portrait.png"
            },
            idGenerator = { ids.removeFirst() },
        )

        val response = service.storyCharacters(
            StudyStoryCharactersRequest(text = "Lan chào Minh.", textLanguageTag = "vi"),
        )
        assertEquals(2, response.characters.size)
        assertEquals("Lan", response.characters[0].name)
        assertEquals("main", response.characters[0].role)
        assertEquals("char-1", response.characters[0].imageId)
        assertEquals("char-2", response.characters[1].imageId)

        var image = service.playgroundImage("char-1")
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("char-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertTrue(prompts.any { it.contains("Lan") && it.contains("Character reference portrait") })
        assertTrue(refs.all { it.isEmpty() })
    }

    @Test
    fun `sentence image falls back to text-only when references fail`() = runBlocking {
        var calls = 0
        val service = StudyService(
            aiClient = FakeStudyAiClient(),
            imageGenerator = StudyPlaygroundImageGenerator { _, referenceImageUrls ->
                calls += 1
                if (referenceImageUrls.isNotEmpty()) {
                    error("ref boom")
                }
                "https://cdn.example/fallback-scene.png"
            },
            idGenerator = { "scene-fallback-1" },
        )

        service.sentenceImage(
            StudySentenceImageRequest(
                sentenceText = "Lan mỉm cười.",
                fullText = "Lan mỉm cười.",
                sentenceIndex = 0,
                sentenceCount = 1,
                characters = listOf(
                    StudyStoryCharacter(
                        id = "main",
                        name = "Lan",
                        role = "main",
                        description = "young woman",
                        imageUrl = "https://cdn.example/lan.png",
                    ),
                ),
            ),
        )

        var image = service.playgroundImage("scene-fallback-1")
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("scene-fallback-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertEquals("https://cdn.example/fallback-scene.png", image.imageUrl)
        assertEquals(2, calls)
    }

    @Test
    fun `sentence image uses character reference urls`() = runBlocking {
        val capturedRefs = mutableListOf<List<String>>()
        val service = StudyService(
            aiClient = FakeStudyAiClient(),
            imageGenerator = StudyPlaygroundImageGenerator { _, referenceImageUrls ->
                capturedRefs += referenceImageUrls
                "https://cdn.example/scene.png"
            },
            idGenerator = { "scene-1" },
        )

        service.sentenceImage(
            StudySentenceImageRequest(
                sentenceText = "Lan mỉm cười.",
                fullText = "Lan mỉm cười.",
                sentenceIndex = 0,
                sentenceCount = 1,
                characters = listOf(
                    StudyStoryCharacter(
                        id = "main",
                        name = "Lan",
                        role = "main",
                        description = "young woman",
                        imageUrl = "https://cdn.example/lan.png",
                    ),
                ),
            ),
        )

        var image = service.playgroundImage("scene-1")
        var attempts = 0
        while (
            image!!.status != StudyPlaygroundImageStatus.COMPLETED &&
            image.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("scene-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image.status)
        assertEquals(listOf("https://cdn.example/lan.png"), capturedRefs.single())
    }

    @Test
    fun `normalize character designs keeps one main and optional secondary`() {
        val normalized = normalizeCharacterDesigns(
            listOf(
                StudyCharacterDesign("x", "Lan", "main", "desc1"),
                StudyCharacterDesign("y", "Minh", "secondary", "desc2"),
                StudyCharacterDesign("z", "Extra", "main", "desc3"),
            ),
        )
        assertEquals(listOf("main", "secondary"), normalized.map { it.role })
        assertEquals("Lan", normalized[0].name)
        assertEquals("Minh", normalized[1].name)
    }

    @Test
    fun `play scenarios returns four starters and enqueues cover images`() = runBlocking {
        var nextId = 0
        val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
        val ai = FakeStudyAiClient()
        val service = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/play.png"
            },
            idGenerator = { "play-${++nextId}" },
        )

        val response = service.playScenarios(
            StudyPlayScenariosRequest(
                text = "Xin chào bạn.",
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )

        assertEquals(4, response.scenarios.size)
        assertTrue(response.scenarios.any { !it.openingLine.isNullOrBlank() })
        assertTrue(response.scenarios.any { it.openingLine == null })
        assertEquals(listOf("play-1", "play-2", "play-3", "play-4"), response.scenarios.map { it.imageId })

        var attempts = 0
        val scenarioImageIds = listOf("play-1", "play-2", "play-3", "play-4")
        while (
            scenarioImageIds.any { imageId ->
                service.playgroundImage(imageId)?.status != StudyPlaygroundImageStatus.COMPLETED &&
                    service.playgroundImage(imageId)?.status != StudyPlaygroundImageStatus.FAILED
            } && attempts < 40
        ) {
            delay(25)
            attempts++
        }
        assertEquals(4, prompts.size)
        prompts.forEach { prompt ->
            assertContains(prompt, FakeStudyAiClient.INTENDED_SCENE)
            assertContains(prompt, PromptFactory.ENGLISH_ONLY)
            assertTrue(!prompt.contains("Xin chào bạn."))
            assertTrue(!prompt.contains("Xin chào!"))
        }
        assertEquals("Xin chào bạn.", ai.lastSceneRequest?.fullText)
        assertTrue(response.scenarios.any { it.description == ai.lastSceneRequest?.scenarioDescription })
    }

    @Test
    fun `exam scene uses known cast and scores only nonblank known beats`() = runBlocking {
        var now = 40L
        val ai = FakeStudyAiClient()
        val service = StudyService(aiClient = ai, clock = { now++ })
        val session = StudySessionResponse(
            text = "Xin chào.",
            textLanguageTag = "vi-VN",
            sentences = listOf(
                StudySentence(
                    text = "Xin chào.",
                    tokens = listOf(StudyToken(text = "Xin", status = StudyTokenStatus.READY, literalMeaning = "Hello")),
                ),
            ),
            characters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "A neighbor.",
                ),
            ),
        )

        val scene = service.examScene(StudyExamSceneRequest(session = session, languageTag = "en-US"))

        assertEquals("vi", ai.lastExamSceneRequest?.textLanguageTag)
        assertEquals("en", ai.lastExamSceneRequest?.languageTag)
        assertEquals(listOf("main"), ai.lastExamSceneRequest?.characters?.map { it.id })
        assertEquals(20, scene.beats.size)
        assertEquals(10, scene.outline.size)
        assertTrue(scene.isTenRoundExam())
        assertTrue(scene.beats.all { it.speakerId == "main" && it.speakerName == "Lan" })
        assertTrue(scene.beats.filter { it.kind == StudyExamBeatKind.SPEAK }.all { it.line == null })

        val scored = service.scoreExam(
            scene = scene,
            request = StudyExamScoreRequest(
                sessionId = "saved-1",
                answers = listOf(
                    StudyExamScoreAnswer("listen-1", "Hello."),
                    StudyExamScoreAnswer("speak-1", "Chào Lan."),
                    StudyExamScoreAnswer("speak-2", "   "),
                    StudyExamScoreAnswer("unknown", "not used"),
                ),
            ),
            textLanguageTag = "vi",
            languageTag = "en",
        )

        assertEquals(2, ai.examBeatRequests.size)
        assertEquals("listen-1", scored.beats.first().beatId)
        assertEquals(listOf(2, 2, 0), scored.beats.take(3).map { it.points })
        assertEquals(1, scored.grade.listening)
        assertEquals(1, scored.grade.speaking)
        assertEquals(1, scored.grade.overall)
        assertEquals(StudyExamScoring.feedback("en"), scored.grade.feedback)
        assertEquals(40L, scored.grade.scoredAt)

        val fallbackScene = service.examScene(
            StudyExamSceneRequest(session = session.copy(characters = emptyList()), languageTag = "en"),
        )
        assertTrue(fallbackScene.beats.all { it.speakerId == "main" && it.speakerName == "Ari" })
        assertTrue(fallbackScene.isTenRoundExam())
    }

    @Test
    fun `short generated scene becomes ten alternating rounds`() = runBlocking {
        val sentences = (1..6).map { StudySentence(text = "Sentence $it.", tokens = emptyList()) }
        val beats = sentences.mapIndexed { index, sentence ->
            val kind = if (index % 2 == 0) StudyExamBeatKind.LISTEN else StudyExamBeatKind.SPEAK
            StudyExamBeat(
                id = "beat-${index + 1}",
                kind = kind,
                speakerId = "main",
                speakerName = "Lan",
                line = if (kind == StudyExamBeatKind.LISTEN) sentence.text else null,
                prompt = "Respond to this moment.",
                expectedMeaning = "Use the sentence meaning.",
                sourceSentence = sentence.text,
            )
        }
        val ai = FakeStudyAiClient(
            examScene = StudyExamScene(id = "six-line-scene", setting = "At a market.", beats = beats),
        )
        val session = StudySessionResponse(
            text = sentences.joinToString(" ") { it.text },
            textLanguageTag = "en",
            sentences = sentences,
        )

        val scene = StudyService(ai).examScene(StudyExamSceneRequest(session = session, languageTag = "en"))

        assertTrue(scene.isTenRoundExam())
        assertEquals(sentences.map { it.text }.toSet(), scene.beats.map { it.sourceSentence }.toSet())
    }

    @Test
    fun `omitted source sentence is recovered from the spoken line`() = runBlocking {
        val broken = fakeExamScene().copy(
            beats = fakeExamScene().beats.map { beat ->
                beat.copy(sourceSentence = if (beat.kind == StudyExamBeatKind.SPEAK) "" else beat.sourceSentence)
            },
        )
        val scene = StudyService(FakeStudyAiClient(examScene = broken)).examScene(
            StudyExamSceneRequest(
                session = StudySessionResponse(
                    text = "Xin chào.",
                    textLanguageTag = "vi",
                    sentences = listOf(StudySentence(text = "Xin chào.", tokens = emptyList())),
                    characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
                ),
                languageTag = "en",
            ),
        )

        assertEquals(List(20) { "Xin chào." }, scene.beats.map { it.sourceSentence })
        assertTrue(scene.beats.first().line.orEmpty().contains("Xin chào."))
    }

    @Test
    fun `invented listen lines are not overwritten with study sentences`() = runBlocking {
        val invented = fakeExamScene().copy(
            beats = fakeExamScene().beats.mapIndexed { index, beat ->
                if (beat.kind == StudyExamBeatKind.LISTEN) {
                    beat.copy(line = "Câu mới số ${index / 2 + 1}.")
                } else {
                    beat
                }
            },
        )
        val scene = StudyService(FakeStudyAiClient(examScene = invented)).examScene(
            StudyExamSceneRequest(
                session = StudySessionResponse(
                    text = "Xin chào.",
                    textLanguageTag = "vi",
                    sentences = listOf(StudySentence(text = "Xin chào.", tokens = emptyList())),
                    characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
                ),
                languageTag = "en",
            ),
        )

        val listenLines = scene.beats.filter { it.kind == StudyExamBeatKind.LISTEN }.map { it.line }
        assertEquals((1..10).map { "Câu mới số $it." }, listenLines)
        assertTrue(listenLines.none { it.orEmpty().contains("Xin chào") })
    }

    @Test
    fun `failed exam generation falls back to a playable scene`() = runBlocking {
        val scene = StudyService(FakeStudyAiClient(examSceneFailure = true)).examScene(
            StudyExamSceneRequest(
                session = StudySessionResponse(
                    text = "Xin chào.",
                    textLanguageTag = "vi",
                    sentences = listOf(StudySentence(text = "Xin chào.", tokens = emptyList())),
                ),
                languageTag = "en",
            ),
        )

        assertEquals(20, scene.beats.size)
        assertTrue(scene.isTenRoundExam())
        assertEquals("Ari", scene.beats.first().speakerName)
        assertTrue(scene.beats.all { it.sourceSentence == "Xin chào." })
    }

    @Test
    fun `play turn returns reply and starts cover image job`() = runBlocking {
        val prompts = java.util.concurrent.CopyOnWriteArrayList<String>()
        val ai = FakeStudyAiClient()
        val service = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { prompt, _ ->
                prompts += prompt
                "https://cdn.example/turn.png"
            },
            idGenerator = { "turn-1" },
        )

        val response = service.playTurn(
            StudyPlayTurnRequest(
                text = "Xin chào bạn.",
                textLanguageTag = "vi",
                languageTag = "en",
                scenarioDescription = "A neighbor greets you.",
                userMessage = "Chào bạn!",
                history = listOf(
                    StudyPlayHistoryMessage(
                        speaker = StudyPlaySpeaker.OTHER,
                        text = "Xin chào!",
                        description = "A neighbor greets you.",
                    ),
                ),
            ),
        )

        assertEquals("They smile and wait for your reply.", response.description)
        assertEquals("Thật sao?", response.reply)
        assertEquals("turn-1", response.imageId)
        assertNotNull(service.playgroundImage("turn-1"))
        var image = service.playgroundImage("turn-1")
        var attempts = 0
        while (
            image?.status != StudyPlaygroundImageStatus.COMPLETED &&
            image?.status != StudyPlaygroundImageStatus.FAILED &&
            attempts < 40
        ) {
            delay(25)
            image = service.playgroundImage("turn-1")
            attempts++
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, image?.status)
        assertContains(prompts.single(), FakeStudyAiClient.INTENDED_SCENE)
        assertContains(prompts.single(), PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompts.single().contains("Xin chào bạn."))
        assertTrue(!prompts.single().contains("Thật sao?"))
        assertEquals(response.description, ai.lastSceneRequest?.scenarioDescription)
        assertEquals(response.reply, ai.lastSceneRequest?.spokenLine)
        Unit
    }

    @Test
    fun `play scenarios falls back when AI returns nothing`() = runBlocking {
        val service = StudyService(
            aiClient = FakeStudyAiClient(playScenarios = emptyList()),
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://cdn.example/play.png" },
            idGenerator = { java.util.UUID.randomUUID().toString() },
        )
        val response = service.playScenarios(
            StudyPlayScenariosRequest(
                text = "Hello friend.",
                textLanguageTag = "en",
                languageTag = "en",
            ),
        )
        assertEquals(4, response.scenarios.size)
        assertTrue(response.scenarios.all { it.description.isNotBlank() })
    }

    @Test
    fun `English play opening lines are discarded for Burmese study text`() = runBlocking {
        val ai = FakeStudyAiClient(
            playScenarios = listOf(
                StudyPlayScenarioDesign("A neighbor greets you.", "Hello there!"),
                StudyPlayScenarioDesign("A vendor offers fruit.", "မင်္ဂလာပါ။"),
                StudyPlayScenarioDesign("A classmate asks a question.", "How are you?"),
                StudyPlayScenarioDesign("Someone waves from a courtyard.", null),
            ),
        )
        val service = StudyService(ai)

        val response = service.playScenarios(
            StudyPlayScenariosRequest(
                text = "မင်္ဂလာပါ။",
                textLanguageTag = "my-MM",
                languageTag = "en",
            ),
        )

        assertEquals("my", ai.lastPlayScenariosRequest?.textLanguageTag)
        assertEquals(listOf(null, "မင်္ဂလာပါ။", null, null), response.scenarios.map { it.openingLine })
    }

    @Test
    fun `English play turn replies are rejected for Burmese study text`() = runBlocking {
        val ai = FakeStudyAiClient(
            playTurn = StudyPlayTurnDesign(description = "They smile.", reply = "How are you?"),
        )
        val service = StudyService(ai)

        assertFailsWith<IllegalArgumentException> {
            service.playTurn(
                StudyPlayTurnRequest(
                    text = "မင်္ဂလာပါ။",
                    textLanguageTag = "my-MM",
                    languageTag = "en",
                    scenarioDescription = "A neighbor greets you.",
                    userMessage = "Hello!",
                ),
            )
        }
        assertEquals("my", ai.lastPlayTurnRequest?.textLanguageTag)
    }

    @Test
    fun `play scenario fallback uses study language instead of UI language`() = runBlocking {
        val service = StudyService(
            aiClient = FakeStudyAiClient(playScenarios = emptyList()),
            idGenerator = { java.util.UUID.randomUUID().toString() },
        )

        val response = service.playScenarios(
            StudyPlayScenariosRequest(
                text = "你好。",
                textLanguageTag = "zh",
                languageTag = "en",
            ),
        )

        assertEquals("你好！你今天要去哪里？", response.scenarios.first().openingLine)
        assertTrue(response.scenarios.filterNot { it.openingLine.isNullOrBlank() }
            .all { line -> line.openingLine?.any { it in '\u4e00'..'\u9fff' } == true })
    }

    @Test
    fun `write check returns empty feedback when attempt matches`() = runBlocking {
        val service = StudyService(FakeStudyAiClient(), idGenerator = { "write-1" })
        val response = service.writeCheck(
            StudyWriteCheckRequest(
                expected = "Xin chào bạn!",
                attempt = "  Xin   chào bạn! ",
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )
        assertTrue(response.correct)
        assertEquals("", response.feedback)
    }

    @Test
    fun `write check returns AI feedback when attempt differs`() = runBlocking {
        val fake = FakeStudyAiClient(
            writeFeedback = StudyWriteFeedback(feedback = "Missing the word bạn."),
        )
        val service = StudyService(fake, idGenerator = { "write-2" })
        val response = service.writeCheck(
            StudyWriteCheckRequest(
                expected = "Xin chào bạn!",
                attempt = "Xin chào!",
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )
        assertFalse(response.correct)
        assertEquals("Missing the word bạn.", response.feedback)
        assertEquals("Xin chào bạn!", fake.lastWriteCheckRequest?.expected)
        assertEquals("Xin chào!", fake.lastWriteCheckRequest?.attempt)
    }

    @Test
    fun `express check accepts exact reference sentence without AI`() = runBlocking {
        val fake = FakeStudyAiClient(
            expressJudgment = StudyExpressJudgment(correct = false, feedback = "should not be used"),
        )
        val service = StudyService(fake)
        val response = service.expressCheck(
            StudyExpressCheckRequest(
                meaning = "Hello friend.",
                attempt = "  Xin chào bạn. ",
                textLanguageTag = "vi",
                languageTag = "en",
                referenceText = "Xin chào bạn.",
            ),
        )
        assertTrue(response.correct)
        assertEquals("", response.feedback)
        assertEquals(null, fake.lastExpressCheckRequest)
    }

    @Test
    fun `express check uses AI judgment for paraphrases`() = runBlocking {
        val fake = FakeStudyAiClient(
            expressJudgment = StudyExpressJudgment(correct = true, feedback = "Great"),
        )
        val service = StudyService(fake)
        val response = service.expressCheck(
            StudyExpressCheckRequest(
                meaning = "Hello friend.",
                attempt = "Chào bạn nhé!",
                textLanguageTag = "vi",
                languageTag = "en",
                referenceText = "Xin chào bạn.",
            ),
        )
        assertTrue(response.correct)
        assertEquals("Hello friend.", fake.lastExpressCheckRequest?.meaning)
        assertEquals("Chào bạn nhé!", fake.lastExpressCheckRequest?.attempt)
    }

    @Test
    fun `express check returns AI feedback when incorrect`() = runBlocking {
        val fake = FakeStudyAiClient(
            expressJudgment = StudyExpressJudgment(correct = false, feedback = "Missing the greeting."),
        )
        val service = StudyService(fake)
        val response = service.expressCheck(
            StudyExpressCheckRequest(
                meaning = "Hello friend.",
                attempt = "Tôi đói.",
                textLanguageTag = "vi",
                languageTag = "en",
                referenceText = "Xin chào bạn.",
            ),
        )
        assertFalse(response.correct)
        assertEquals("Missing the greeting.", response.feedback)
    }

    @Test
    fun `listen check accepts exact meaning without AI`() = runBlocking {
        val fake = FakeStudyAiClient(
            listenJudgment = StudyExpressJudgment(correct = false, feedback = "should not be used"),
        )
        val response = StudyService(fake).listenCheck(
            StudyListenCheckRequest(
                sourceText = "Xin chào bạn.",
                expectedMeaning = "Hello friend.",
                attempt = "  Hello   friend. ",
                textLanguageTag = "vi-VN",
                languageTag = "en-US",
            ),
        )
        assertTrue(response.correct)
        assertEquals("", response.feedback)
        assertEquals(null, fake.lastListenCheckRequest)
    }

    @Test
    fun `listen check uses AI for alternate translations`() = runBlocking {
        val fake = FakeStudyAiClient(
            listenJudgment = StudyExpressJudgment(correct = true, feedback = "Great"),
        )
        val response = StudyService(fake).listenCheck(
            StudyListenCheckRequest(
                sourceText = "Xin chào bạn.",
                expectedMeaning = "Hello friend.",
                attempt = "Hi there!",
                textLanguageTag = "vi-VN",
                languageTag = "en-US",
            ),
        )
        assertTrue(response.correct)
        assertEquals("Xin chào bạn.", fake.lastListenCheckRequest?.sourceText)
        assertEquals("Hello friend.", fake.lastListenCheckRequest?.expectedMeaning)
        assertEquals("Hi there!", fake.lastListenCheckRequest?.attempt)
        assertEquals("vi", fake.lastListenCheckRequest?.textLanguageTag)
        assertEquals("en", fake.lastListenCheckRequest?.languageTag)
    }

    @Test
    fun `express generate returns cleaned AI batch`() = runBlocking {
        val fake = FakeStudyAiClient(
            expressSentences = listOf(
                StudyExampleSentence(text = "  Xin chào. ", translation = " Hello. "),
                StudyExampleSentence(text = "Tạm biệt.", translation = "Goodbye."),
                StudyExampleSentence(text = "", translation = "skip"),
            ),
        )
        val service = StudyService(fake)
        val response = service.expressGenerate(
            StudyExpressGenerateRequest(
                text = "Xin chào bạn.",
                textLanguageTag = "vi",
                languageTag = "en",
                vocabulary = listOf("chào", "bạn"),
            ),
        )
        assertEquals(
            listOf(
                StudyExampleSentence(text = "Xin chào.", translation = "Hello."),
                StudyExampleSentence(text = "Tạm biệt.", translation = "Goodbye."),
            ),
            response.sentences,
        )
        assertEquals(listOf("chào", "bạn"), fake.lastExpressGenerateRequest?.vocabulary)
    }

    @Test
    fun `express generate falls back when AI returns nothing`() = runBlocking {
        val service = StudyService(FakeStudyAiClient(expressSentences = emptyList()))
        val response = service.expressGenerate(
            StudyExpressGenerateRequest(
                text = "Xin chào bạn.",
                textLanguageTag = "vi",
                languageTag = "en",
                vocabulary = listOf("chào"),
            ),
        )
        assertTrue(response.sentences.isNotEmpty())
        assertTrue(response.sentences.all { it.text.isNotBlank() && it.translation.isNotBlank() })
    }

    @Test
    fun `study translation normalizes target and source tags before calling AI`() = runBlocking {
        val ai = FakeStudyAiClient(translatedText = "  Bonjour.  ")
        val response = StudyService(ai).translateStudyText(
            StudyTranslateRequest(
                text = "  Hello.  ",
                targetLanguageTag = "fr-FR",
                sourceLanguageTag = " en ",
            ),
        )

        assertEquals("Hello.", ai.lastTranslateRequest?.text)
        assertEquals("fr", ai.lastTranslateRequest?.targetLanguageTag)
        assertEquals("en", ai.lastTranslateRequest?.sourceLanguageTag)
        assertEquals("Bonjour.", response.text)
    }

    @Test
    fun `study translation falls back to original when AI returns blank`() = runBlocking {
        val response = StudyService(FakeStudyAiClient(translatedText = "  ")).translateStudyText(
            StudyTranslateRequest(text = "Hello there.", targetLanguageTag = "fr"),
        )

        assertEquals("Hello there.", response.text)
    }
}

internal class FakeStudyAiClient(
    private val skeleton: StudySessionResponse = StudySessionResponse(
        text = "x",
        textLanguageTag = "en",
        sentences = listOf(
            StudySentence(
                text = "x",
                tokens = listOf(StudyToken(text = "x", status = StudyTokenStatus.PENDING)),
            ),
        ),
    ),
    private val dissect: StudySentence = StudySentence(
        text = "x",
        tokens = listOf(StudyToken(text = "x", status = StudyTokenStatus.READY, literalMeaning = "x")),
    ),
    private val playground: StudyWordPlaygroundJudgment = StudyWordPlaygroundJudgment(
        natural = true,
        summary = "Natural.",
    ),
    private val characters: List<StudyCharacterDesign> = listOf(
        StudyCharacterDesign(
            id = "main",
            name = "Lan",
            role = "main",
            description = "Young Vietnamese woman with long dark hair and a linen blouse",
        ),
    ),
    private val playScenarios: List<StudyPlayScenarioDesign> = listOf(
        StudyPlayScenarioDesign("A neighbor greets you.", "Xin chào!"),
        StudyPlayScenarioDesign("A vendor offers fruit.", null),
        StudyPlayScenarioDesign("A classmate asks a question.", "Bạn rảnh không?"),
        StudyPlayScenarioDesign("Someone waves from across the courtyard.", null),
    ),
    private val playTurn: StudyPlayTurnDesign = StudyPlayTurnDesign(
        description = "They smile and wait for your reply.",
        reply = "Thật sao?",
    ),
    private val writeFeedback: StudyWriteFeedback = StudyWriteFeedback(
        feedback = "You dropped the final question mark.",
    ),
    private val expressJudgment: StudyExpressJudgment = StudyExpressJudgment(
        correct = true,
        feedback = "",
    ),
    private val listenJudgment: StudyExpressJudgment = StudyExpressJudgment(
        correct = true,
        feedback = "",
    ),
    private val expressSentences: List<StudyExampleSentence> = listOf(
        StudyExampleSentence(text = "Xin chào.", translation = "Hello."),
        StudyExampleSentence(text = "Tạm biệt.", translation = "Goodbye."),
    ),
    private val extractedText: String? = null,
    private val composedText: String? = null,
    private val translatedText: String? = null,
    private val intendedScene: String = INTENDED_SCENE,
    private val sessionName: String = "A study session",
    private val sessionNameFailure: Boolean = false,
    private val examScene: StudyExamScene = fakeExamScene(),
    private val examJudgment: StudyExamJudgment = StudyExamJudgment(points = 2, feedback = "Clear answer."),
    private val examSceneFailure: Boolean = false,
) : StudyAiClient {
    var lastExtractRequest: StudyGenerationRequest? = null
    var lastIntentRequest: StudyGenerationRequest? = null
    var lastTranslateRequest: StudyTranslateRequest? = null
    var lastSkeletonRequest: StudyGenerationRequest? = null
    var lastDissectRequest: StudyDissectGenerationRequest? = null
    var lastPlaygroundRequest: StudyWordPlaygroundGenerationRequest? = null
    var lastCharactersRequest: StudyCharactersGenerationRequest? = null
    var lastPlayScenariosRequest: StudyPlayScenariosGenerationRequest? = null
    var lastPlayTurnRequest: StudyPlayTurnGenerationRequest? = null
    var lastWriteCheckRequest: StudyWriteCheckGenerationRequest? = null
    var lastExpressCheckRequest: StudyExpressCheckGenerationRequest? = null
    var lastListenCheckRequest: StudyListenCheckGenerationRequest? = null
    var lastExpressGenerateRequest: StudyExpressGenerateGenerationRequest? = null
    var lastSceneRequest: StudySceneGenerationRequest? = null
    var lastExamSceneRequest: StudyExamSceneGenerationRequest? = null
    val examBeatRequests = mutableListOf<StudyExamBeatJudgmentRequest>()
    var studySessionNameCalls = 0

    companion object {
        const val INTENDED_SCENE =
            "A young woman smiles from a sunlit apartment doorway, warm cinematic medium shot"
    }

    override suspend fun extractStudyText(request: StudyGenerationRequest): String {
        lastExtractRequest = request
        return extractedText ?: request.text
    }

    override suspend fun generateStudyIntent(request: StudyGenerationRequest): String {
        lastIntentRequest = request
        return composedText ?: request.text
    }

    override suspend fun translateStudyText(request: StudyTranslateRequest): String {
        lastTranslateRequest = request
        return translatedText ?: request.text
    }

    override suspend fun designIntendedScene(request: StudySceneGenerationRequest): String {
        lastSceneRequest = request
        return intendedScene
    }

    override suspend fun generateStudySessionName(text: String, languageTag: String): String {
        studySessionNameCalls += 1
        if (sessionNameFailure) error("Study session name generation failed")
        return sessionName
    }

    override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
        lastSkeletonRequest = request
        return skeleton
    }

    override suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence {
        lastDissectRequest = request
        return dissect
    }

    override suspend fun evaluateWordUsage(
        request: StudyWordPlaygroundGenerationRequest,
    ): StudyWordPlaygroundJudgment {
        lastPlaygroundRequest = request
        return playground
    }

    override suspend fun designStoryCharacters(
        request: StudyCharactersGenerationRequest,
    ): List<StudyCharacterDesign> {
        lastCharactersRequest = request
        return characters
    }

    override suspend fun generatePlayScenarios(
        request: StudyPlayScenariosGenerationRequest,
    ): List<StudyPlayScenarioDesign> {
        lastPlayScenariosRequest = request
        return playScenarios
    }

    override suspend fun generatePlayTurn(
        request: StudyPlayTurnGenerationRequest,
    ): StudyPlayTurnDesign {
        lastPlayTurnRequest = request
        return playTurn
    }

    override suspend fun generateExamScene(request: StudyExamSceneGenerationRequest): StudyExamScene {
        lastExamSceneRequest = request
        if (examSceneFailure) throw IllegalArgumentException("Qwen study exam beat missing source sentence")
        return examScene
    }

    override suspend fun evaluateExamBeat(request: StudyExamBeatJudgmentRequest): StudyExamJudgment {
        examBeatRequests += request
        return examJudgment
    }

    override suspend fun evaluateWriteAttempt(
        request: StudyWriteCheckGenerationRequest,
    ): StudyWriteFeedback {
        lastWriteCheckRequest = request
        return writeFeedback
    }

    override suspend fun evaluateExpressAttempt(
        request: StudyExpressCheckGenerationRequest,
    ): StudyExpressJudgment {
        lastExpressCheckRequest = request
        return expressJudgment
    }

    override suspend fun evaluateListenAttempt(
        request: StudyListenCheckGenerationRequest,
    ): StudyExpressJudgment {
        lastListenCheckRequest = request
        return listenJudgment
    }

    override suspend fun generateExpressSentences(
        request: StudyExpressGenerateGenerationRequest,
    ): List<StudyExampleSentence> {
        lastExpressGenerateRequest = request
        return expressSentences
    }
}

private fun fakeExamScene() = StudyExamScene(
    id = "exam-1",
    setting = "At the neighborhood market.",
    outline = (1..10).map { "Task $it" },
    beats = listOf(
        StudyExamBeat(
            id = "listen-1",
            kind = StudyExamBeatKind.LISTEN,
            speakerId = "invented",
            speakerName = "Someone else",
            line = "Xin chào.",
            prompt = "What did the neighbor mean?",
            expectedMeaning = "A greeting.",
            sourceSentence = "Xin chào.",
        ),
        StudyExamBeat(
            id = "speak-1",
            kind = StudyExamBeatKind.SPEAK,
            speakerId = "invented",
            speakerName = "Someone else",
            line = "This line must not be shown.",
            prompt = "Greet the neighbor.",
            expectedMeaning = "Return the greeting.",
            sourceSentence = "Xin chào.",
        ),
    ) + (2..10).flatMap { round ->
        listOf(
            StudyExamBeat(
                id = "listen-$round",
                kind = StudyExamBeatKind.LISTEN,
                speakerId = "invented",
                speakerName = "Someone else",
                line = "Xin chào.",
                prompt = "What did the neighbor mean?",
                expectedMeaning = "A greeting.",
                sourceSentence = "Xin chào.",
            ),
            StudyExamBeat(
                id = "speak-$round",
                kind = StudyExamBeatKind.SPEAK,
                speakerId = "invented",
                speakerName = "Someone else",
                prompt = "Use the greeting in moment $round.",
                expectedMeaning = "A situated reply.",
                sourceSentence = "Xin chào.",
            ),
        )
    },
)
