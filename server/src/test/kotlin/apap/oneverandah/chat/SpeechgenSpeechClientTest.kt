package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.SpeechgenConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SpeechgenSpeechClientTest {
    @Test
    fun `speechgen configuration uses Xiaoxiao Myanmar voice and requires credentials`() {
        val envFile = Files.createTempFile("one-verandah", ".env").toFile().apply {
            writeText(
                """
                SPEECHGEN_API_KEY=test-token
                SPEECHGEN_EMAIL=tts@example.test
                """.trimIndent(),
            )
        }

        try {
            val config = AppConfig.load(envFile).speechgen
            assertEquals("Xiaoxiao MM", config.voice)
            assertEquals("https://speechgen.io/index.php?r=api/subs", config.endpoint)
            assertEquals("https://speechgen.io/index.php?r=api/result", config.resultEndpoint)
            assertEquals("https://speechgen.io/index.php?r=api/text", config.textEndpoint)
            assertTrue(config.enabled)
        } finally {
            envFile.delete()
        }
        assertFalse(SpeechgenConfig(apiKey = "token", email = "").enabled)
    }

    @Test
    fun `synthesis posts required form fields and downloads the returned mp3`() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        var submittedForm: Parameters? = null
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                submittedForm = (request.body as FormDataContent).formData
                respond(
                    content = """{"status":1,"file":"https://audio.example.test/burmese.mp3"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                assertEquals("https://audio.example.test/burmese.mp3", request.url.toString())
                respond(audio, headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"))
            }
        })
        val speechClient = SpeechgenSpeechClient(config(), client)

        val voiceId = speechClient.designVoice()
        val result = speechClient.synthesize(
            request = SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "my"),
            voiceId = voiceId,
        )

        assertEquals("Xiaoxiao MM", voiceId)
        assertEquals("test-token", submittedForm?.get("token"))
        assertEquals("tts@example.test", submittedForm?.get("email"))
        assertEquals("Xiaoxiao MM", submittedForm?.get("voice"))
        assertEquals("မင်္ဂလာပါ", submittedForm?.get("text"))
        assertEquals("mp3", submittedForm?.get("format"))
        assertContentEquals(audio, result.bytes)
        assertEquals("audio/mpeg", result.contentType)
        assertEquals(emptyList(), result.words)
        client.close()
    }

    @Test
    fun `synthesis maps subtitle cuts onto word timings`() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                respond(
                    content = """
                        {
                          "status":1,
                          "file":"https://audio.example.test/burmese.mp3",
                          "cuts":[
                            {"text":"မင်္ဂလာပါ","start":0.0,"end":0.8},
                            {"text":"ကမ္ဘာ","start":0.8,"end":1.25}
                          ]
                        }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(audio, headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"))
            }
        })

        val result = SpeechgenSpeechClient(config(), client).synthesize(
            SpeechRequest(text = "မင်္ဂလာပါ ကမ္ဘာ", languageTag = "my"),
            "ignored-qwen-voice",
        )

        assertEquals(
            listOf(
                SpeechWordTiming("မင်္ဂလာပါ", 0, 800, 0, 9),
                SpeechWordTiming("ကမ္ဘာ", 800, 1250, 10, 15),
            ),
            result.words,
        )
        client.close()
    }

    @Test
    fun `synthesis polls result until audio and cuts are ready`() = runBlocking {
        val audio = byteArrayOf(9, 8, 7)
        val posted = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posted += request.url.parameters["r"].orEmpty()
                if (request.url.parameters["r"] == "api/result") {
                    respond(
                        content = """
                            {
                              "status":1,
                              "file":"https://audio.example.test/burmese.mp3",
                              "cuts":[{"text":"Hi","begin_ms":0,"end_ms":240}]
                            }
                        """.trimIndent(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        content = """{"status":0,"id":4153594}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            } else {
                respond(audio, headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"))
            }
        })

        val result = SpeechgenSpeechClient(config(), client, delayMillis = {}).synthesize(
            SpeechRequest(text = "Hi", languageTag = "en"),
            "ignored-qwen-voice",
        )

        assertEquals(listOf("api/subs", "api/result"), posted)
        assertContentEquals(audio, result.bytes)
        assertEquals(listOf(SpeechWordTiming("Hi", 0, 240, 0, 2)), result.words)
        client.close()
    }

    @Test
    fun `synthesis times out while Speechgen is still queued`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                content = """{"status":0,"id":4153594}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val exception = assertFailsWith<SpeechgenException> {
            SpeechgenSpeechClient(config(), client, pollTimeoutMs = 0, delayMillis = {}).synthesize(
                SpeechRequest(text = "Hi", languageTag = "en"),
                "ignored-qwen-voice",
            )
        }

        assertEquals("Speechgen TTS timed out waiting for audio", exception.message)
        client.close()
    }

    @Test
    fun `synthesis falls back to text when Speechgen has no subs`() = runBlocking {
        val audio = byteArrayOf(1, 2, 3)
        val posted = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posted += request.url.parameters["r"].orEmpty()
                when (request.url.parameters["r"]) {
                    "api/subs" -> respond(
                        content = """{"status":-1,"error":"no subs"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "api/text" -> respond(
                        content = """{"status":1,"file":"https://audio.example.test/burmese.mp3"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    else -> error("Unexpected Speechgen route ${request.url}")
                }
            } else {
                respond(audio, headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"))
            }
        })

        val result = SpeechgenSpeechClient(config(), client).synthesize(
            SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "my"),
            "ignored-qwen-voice",
        )

        assertEquals(listOf("api/subs", "api/text"), posted)
        assertContentEquals(audio, result.bytes)
        assertEquals(emptyList(), result.words)
        client.close()
    }

    @Test
    fun `word timings parse millisecond cuts and SRT cues`() {
        val fromCuts = speechgenWordTimings(
            "Hello world",
            Json.parseToJsonElement(
                """
                {
                  "cuts":[
                    {"text":"Hello","begin_ms":0,"end_ms":250},
                    {"text":"world","begin_ms":260,"end_ms":500}
                  ]
                }
                """.trimIndent(),
            ).jsonObject,
        )
        val fromSrt = speechgenWordTimings(
            "Hello world",
            Json.parseToJsonElement(
                """
                {
                  "srt":"1\n00:00:00,000 --> 00:00:00,250\nHello\n\n2\n00:00:00,260 --> 00:00:00,500\nworld"
                }
                """.trimIndent(),
            ).jsonObject,
        )

        val expected = listOf(
            SpeechWordTiming("Hello", 0, 250, 0, 5),
            SpeechWordTiming("world", 260, 500, 6, 11),
        )
        assertEquals(expected, fromCuts)
        assertEquals(expected, fromSrt)
    }

    @Test
    fun `synthesis accepts direct binary audio response`() = runBlocking {
        val audio = byteArrayOf(5, 6, 7)
        val client = HttpClient(MockEngine {
            respond(audio, headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"))
        })

        val result = SpeechgenSpeechClient(config(), client).synthesize(
            SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "my"),
            "ignored-qwen-voice",
        )

        assertContentEquals(audio, result.bytes)
        assertEquals("audio/mpeg", result.contentType)
        client.close()
    }

    @Test
    fun `synthesis reports API errors`() = runBlocking {
        val posted = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posted += request.url.parameters["r"].orEmpty()
            }
            respond(
                content = """{"status":-1,"error":"Invalid voice"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val exception = assertFailsWith<SpeechgenException> {
            SpeechgenSpeechClient(config(), client).synthesize(
                SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "my"),
                "ignored-qwen-voice",
            )
        }

        assertEquals("Invalid voice", exception.message)
        assertEquals(listOf("api/subs"), posted)
        client.close()
    }

    @Test
    fun `synthesis preserves coroutine cancellation`() = runBlocking {
        val cancellation = CancellationException("request cancelled")
        val client = HttpClient(MockEngine { throw cancellation })

        val exception = assertFailsWith<CancellationException> {
            SpeechgenSpeechClient(config(), client).synthesize(
                SpeechRequest(text = "မင်္ဂလာပါ", languageTag = "my"),
                "ignored-qwen-voice",
            )
        }

        assertEquals(cancellation.message, exception.message)
        client.close()
    }

    @Test
    fun `router sends Burmese and Myanmar script to speechgen only`() = runBlocking {
        val qwen = RecordingSpeechClient()
        val speechgen = RecordingSpeechClient()
        val router = RoutingSpeechClient(qwen, speechgen, qwenEnabled = true, speechgenEnabled = true)

        router.synthesize(SpeechRequest("Hello", "my-MM"), "qwen-voice")
        router.synthesize(SpeechRequest("မင်္ဂလာပါ", "en"), "qwen-voice")

        assertEquals(0, qwen.requests.size)
        assertEquals(listOf("my-MM", "en"), speechgen.requests.map { it.languageTag })
    }

    @Test
    fun `router keeps English and Vietnamese on Qwen and uses Qwen if speechgen is disabled`() = runBlocking {
        val qwen = RecordingSpeechClient()
        val speechgen = RecordingSpeechClient()
        val router = RoutingSpeechClient(qwen, speechgen, qwenEnabled = true, speechgenEnabled = false)

        router.synthesize(SpeechRequest("Hello", "en"), "qwen-voice")
        router.synthesize(SpeechRequest("Xin chào", "vi-VN"), "qwen-voice")
        router.synthesize(SpeechRequest("မင်္ဂလာပါ", "my"), "qwen-voice")

        assertEquals(listOf("en", "vi-VN", "my"), qwen.requests.map { it.languageTag })
        assertEquals(emptyList(), speechgen.requests)
    }

    @Test
    fun `router uses speechgen voice at startup when Qwen is disabled`() = runBlocking {
        val qwen = RecordingSpeechClient()
        val speechgen = RecordingSpeechClient(voice = "Xiaoxiao MM")
        val router = RoutingSpeechClient(qwen, speechgen, qwenEnabled = false, speechgenEnabled = true)

        assertEquals("Xiaoxiao MM", router.designVoice())
        assertEquals(0, qwen.designCalls)
        assertEquals(1, speechgen.designCalls)
    }

    private fun config() = SpeechgenConfig(
        apiKey = "test-token",
        email = "tts@example.test",
    )

    private class RecordingSpeechClient(
        private val voice: String = "recorded-voice",
    ) : SpeechClient {
        val requests = mutableListOf<SpeechRequest>()
        var designCalls = 0

        override suspend fun designVoice(): String {
            designCalls += 1
            return voice
        }

        override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
            requests += request
            return GeneratedSpeech(byteArrayOf(1), "audio/mpeg")
        }
    }
}
