package apap.oneverandah.challenges

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeTranscriptionTest {
    @Test
    fun challengeNamingDisablesThinkingForChatModel() = runBlocking {
        var requestBody = ""
        val client = HttpClient(MockEngine { request ->
            requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"choices":[{"message":{"content":"Poolside Fun"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val config = QwenChatConfig(
            chatEndpoint = "https://example.test/chat",
            chatModel = "qwen3.8-flash",
            chatHeavyModel = "qwen3.8-max",
            ttsEndpoint = "https://example.test/tts",
            ttsModel = "qwen-audio-3.0-tts-flash",
            ttsVoice = "longanhuan_v3.6",
            voiceDesignEndpoint = "https://example.test/customization",
            voiceDesignModel = "voice-enrollment",
            asrEndpoint = "wss://example.test/realtime",
            asrModel = "qwen3-asr-flash-realtime",
            apiKey = "test-key",
        )

        QwenChallengeNamer(config, client).nameFrom("A fun afternoon by the pool")

        val body = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals("qwen3.8-flash", body["model"]?.jsonPrimitive?.content)
        assertEquals("false", body["enable_thinking"]?.jsonPrimitive?.content)
        client.close()
    }

    @Test
    fun parseFileTranscriptionWordsAndText() {
        val raw = """
            {
              "output": {
                "task_status": "SUCCEEDED",
                "transcripts": [{
                  "text": "hello neighbors",
                  "sentences": [{
                    "text": "hello neighbors",
                    "words": [
                      {"text": "hello", "begin_time": 0, "end_time": 400},
                      {"text": "neighbors", "begin_time": 400, "end_time": 900}
                    ]
                  }]
                }]
              }
            }
        """.trimIndent()
        val parsed = parseChallengeTranscript(raw)
        assertEquals("hello neighbors", parsed.text)
        assertEquals(listOf("hello", "neighbors"), parsed.words.map { it.text })
        assertEquals(400, parsed.words.last().startMs)
    }

    @Test
    fun parsePlainTextFallsBackToEvenTimings() {
        val parsed = parseChallengeTranscript("""{"output":{"text":"pool day fun"}}""", durationMs = 900)
        assertEquals("pool day fun", parsed.text)
        assertEquals(3, parsed.words.size)
        assertEquals(0, parsed.words.first().startMs)
        assertEquals(900, parsed.words.last().endMs)
    }

    @Test
    fun parseNonJsonKeepsRawText() {
        val parsed = parseChallengeTranscript("just talking")
        assertEquals("just talking", parsed.text)
        assertTrue(parsed.words.isNotEmpty())
    }

    @Test
    fun fractionalSecondsBecomeMilliseconds() {
        assertEquals(1_250, parseTimestampMs(1.25))
        assertEquals(400, parseTimestampMs(400.0))
        val parsed = parseChallengeTranscript(
            """{"words":[{"text":"hi","begin_time":0.0,"end_time":1.25}]}""",
            durationMs = 5_000,
        )
        assertEquals(1_250, parsed.words.single().endMs)
    }

    @Test
    fun integerSecondsScaleWhenDurationIsMuchLonger() {
        val scaled = normalizeTranscriptTimings(
            listOf(ChallengeWord("hi", 0, 2)),
            durationMs = 5_000,
        )
        assertEquals(2_000, scaled.single().endMs)
        val alreadyMs = normalizeTranscriptTimings(
            listOf(ChallengeWord("hi", 0, 4_000)),
            durationMs = 5_000,
        )
        assertEquals(4_000, alreadyMs.single().endMs)
    }

    @Test
    fun findsDashScopeTranscriptionFileUrl() {
        val raw = """
            {
              "output": {
                "task_status": "SUCCEEDED",
                "result": [{
                  "file_url": "https://example.com/audio.wav",
                  "transcription_url": "https://dashscope-result.oss-cn-beijing.aliyuncs.com/transcript.json"
                }]
              }
            }
        """.trimIndent()
        assertEquals(
            "https://dashscope-result.oss-cn-beijing.aliyuncs.com/transcript.json",
            findTranscriptionUrl(raw),
        )
        assertEquals(null, findTranscriptionUrl("""{"output":{"text":"hi"}}"""))
    }

    @Test
    fun fileTranscriptionPayloadMatchesAudio30Spec() {
        assertEquals(
            "qwen-audio-3.0-asr-flash-filetrans",
            QwenFileTranscriptionClient.DEFAULT_FILE_MODEL,
        )
        val payload = qwenFileTranscriptionPayload("https://cdn.example/audio.wav")
        assertEquals("qwen-audio-3.0-asr-flash-filetrans", payload["model"]?.jsonPrimitive?.content)
        val input = payload["input"]?.jsonObject
        assertEquals(
            listOf("https://cdn.example/audio.wav"),
            input?.get("file_urls")?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertNull(input?.get("file_url"))
        val params = payload["parameters"]?.jsonObject
        assertEquals(listOf(0), params?.get("channel_id")?.jsonArray?.map { it.jsonPrimitive.int })
        assertEquals(
            listOf("zh", "en"),
            params?.get("language_hints")?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertNull(params?.get("timestamp_alignment_enabled"))
        assertNull(params?.get("enable_itn"))
    }

    @Test
    fun parseAudio30TranscriptionFile() {
        val raw = """
            {
              "file_url": "https://cdn.example/audio.wav",
              "properties": {
                "original_sampling_rate": 16000,
                "original_duration_in_milliseconds": 3834
              },
              "transcripts": [{
                "channel_id": 0,
                "text": "Hello World",
                "sentences": [{
                  "begin_time": 760,
                  "end_time": 3240,
                  "text": "Hello World",
                  "words": [
                    {"begin_time": 760, "end_time": 1400, "text": "Hello"},
                    {"begin_time": 1400, "end_time": 3240, "text": "World"}
                  ]
                }]
              }]
            }
        """.trimIndent()
        val parsed = parseChallengeTranscript(raw, durationMs = 3_834)
        assertEquals("Hello World", parsed.text)
        assertEquals(listOf("Hello", "World"), parsed.words.map { it.text })
        assertEquals(760, parsed.words.first().startMs)
        assertEquals(3240, parsed.words.last().endMs)
    }
}
