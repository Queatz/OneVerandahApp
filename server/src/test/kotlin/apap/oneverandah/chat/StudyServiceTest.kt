package apap.oneverandah.chat

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun `skeleton rejects blank text`() = runBlocking {
        val service = StudyService(FakeStudyAiClient())
        assertFailsWith<IllegalArgumentException> {
            service.skeleton(StudySessionRequest(text = "   ", languageTag = "en"))
        }
        Unit
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
        assertEquals(StudyTokenStatus.PLAIN, response.tokens.last().status)
        assertFalse(response.tokens.last().isDissectable)
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
}

private class FakeStudyAiClient(
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
) : StudyAiClient {
    var lastSkeletonRequest: StudyGenerationRequest? = null
    var lastDissectRequest: StudyDissectGenerationRequest? = null

    override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
        lastSkeletonRequest = request
        return skeleton
    }

    override suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence {
        lastDissectRequest = request
        return dissect
    }
}
