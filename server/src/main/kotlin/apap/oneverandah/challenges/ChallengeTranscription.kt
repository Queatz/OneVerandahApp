package apap.oneverandah.challenges

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

fun interface ChallengeTranscriber {
    suspend fun transcribe(audioBytes: ByteArray?, audioUrl: String?, durationMs: Long): ChallengeTranscript
}

fun interface ChallengeNamer {
    suspend fun nameFrom(transcript: String): String
}

object NoopChallengeTranscriber : ChallengeTranscriber {
    override suspend fun transcribe(
        audioBytes: ByteArray?,
        audioUrl: String?,
        durationMs: Long,
    ): ChallengeTranscript = ChallengeTranscript()
}

object NoopChallengeNamer : ChallengeNamer {
    override suspend fun nameFrom(transcript: String): String = ""
}

/**
 * Parses DashScope long-form / file-transcription payloads into timed words.
 * Kept as a function so tests can cover the vendor JSON without HTTP.
 */
fun parseChallengeTranscript(raw: String, durationMs: Long = 0): ChallengeTranscript {
    val json = Json { ignoreUnknownKeys = true }
    val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        ?: return ChallengeTranscript(text = raw.trim(), words = evenlyTimedWords(raw.trim(), durationMs))
    val words = mutableListOf<ChallengeWord>()
    val texts = mutableListOf<String>()
    collectTranscript(root, words, texts)
    val text = texts.firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank {
        words.joinToString(" ") { it.text }.trim()
    }
    val timed = if (words.isNotEmpty()) words else evenlyTimedWords(text, durationMs)
    return ChallengeTranscript(text = text, words = timed)
}

private fun collectTranscript(element: JsonElement, words: MutableList<ChallengeWord>, texts: MutableList<String>) {
    when (element) {
        is JsonObject -> {
            element["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
            val wordArray = element["words"] ?: element["tokens"]
            if (wordArray is JsonArray) {
                wordArray.forEach { item ->
                    val obj = item as? JsonObject ?: return@forEach
                    val token = (obj["text"] ?: obj["word"] ?: obj["value"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (token.isEmpty()) return@forEach
                    val start = timeMs(obj, "begin_time", "start_time", "start", "begin")
                    val end = timeMs(obj, "end_time", "end", "finish_time")
                    words += ChallengeWord(text = token, startMs = start, endMs = end.coerceAtLeast(start))
                }
            }
            element.values.forEach { collectTranscript(it, words, texts) }
        }
        is JsonArray -> element.forEach { collectTranscript(it, words, texts) }
        else -> Unit
    }
}

private fun timeMs(obj: JsonObject, vararg keys: String): Long {
    val raw = keys.firstNotNullOfOrNull { key ->
        obj[key]?.jsonPrimitive?.let { it.doubleOrNull ?: it.longOrNull?.toDouble() }
    } ?: 0.0
    return raw.toLong().coerceAtLeast(0)
}

internal fun evenlyTimedWords(text: String, durationMs: Long): List<ChallengeWord> {
    val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val span = durationMs.coerceAtLeast(tokens.size * 250L)
    val each = span / tokens.size
    return tokens.mapIndexed { index, token ->
        ChallengeWord(text = token, startMs = index * each, endMs = (index + 1) * each)
    }
}

/**
 * Qwen long-format file transcription (async DashScope audio ASR).
 *
 * Prefers a public [audioUrl] when the video is on OSS; otherwise posts the
 * extracted wav as base64 to the multimodal flash model as a fallback.
 */
class QwenFileTranscriptionClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
    private val fileEndpoint: String = DEFAULT_FILE_ENDPOINT,
    private val fileModel: String = DEFAULT_FILE_MODEL,
    private val multimodalEndpoint: String = DEFAULT_MULTIMODAL_ENDPOINT,
    private val multimodalModel: String = DEFAULT_MULTIMODAL_MODEL,
    private val pollMs: Long = 1_500,
    private val maxPolls: Int = 40,
) : ChallengeTranscriber {
    private val log = LoggerFactory.getLogger(QwenFileTranscriptionClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(
        audioBytes: ByteArray?,
        audioUrl: String?,
        durationMs: Long,
    ): ChallengeTranscript {
        if (!config.enabled) return ChallengeTranscript()
        val publicUrl = audioUrl?.takeIf { it.startsWith("http") && !it.contains("localhost") && !it.contains("127.0.0.1") }
        return try {
            if (!publicUrl.isNullOrBlank()) {
                transcribeFile(publicUrl, durationMs)
            } else if (audioBytes != null && audioBytes.isNotEmpty()) {
                transcribeInline(audioBytes, durationMs)
            } else {
                ChallengeTranscript()
            }
        } catch (e: Exception) {
            log.warn("Challenge transcription failed: {}", e.message)
            ChallengeTranscript()
        }
    }

    private suspend fun transcribeFile(fileUrl: String, durationMs: Long): ChallengeTranscript {
        val payload = buildJsonObject {
            put("model", fileModel)
            put(
                "input",
                buildJsonObject { put("file_url", fileUrl) },
            )
            put(
                "parameters",
                buildJsonObject {
                    put("channel_id", buildJsonArray { add(JsonPrimitive(0)) })
                    put("enable_itn", JsonPrimitive(true))
                },
            )
        }
        val submitted = client.post(fileEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            header("X-DashScope-Async", "enable")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), payload))
        }
        val submitBody = json.parseToJsonElement(submitted.bodyAsText()).jsonObject
        val taskId = submitBody["output"]?.jsonObject?.get("task_id")?.jsonPrimitive?.contentOrNull
            ?: submitBody["task_id"]?.jsonPrimitive?.contentOrNull
            ?: return parseChallengeTranscript(submitBody.toString(), durationMs)
        val taskUrl = fileEndpoint.substringBefore("/services/") + "/tasks/$taskId"
        repeat(maxPolls) {
            delay(pollMs)
            val polled = client.get(taskUrl) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            val bodyText = polled.bodyAsText()
            val body = json.parseToJsonElement(bodyText).jsonObject
            val status = (body["output"]?.jsonObject?.get("task_status") ?: body["task_status"])
                ?.jsonPrimitive?.contentOrNull
                ?.uppercase()
            if (status == "SUCCEEDED" || status == "SUCCESS") {
                return parseChallengeTranscript(bodyText, durationMs)
            }
            if (status == "FAILED" || status == "CANCELED") {
                log.warn("Qwen file transcription {}: {}", status, bodyText.take(300))
                return ChallengeTranscript()
            }
        }
        return ChallengeTranscript()
    }

    private suspend fun transcribeInline(audioBytes: ByteArray, durationMs: Long): ChallengeTranscript {
        val encoded = Base64.getEncoder().encodeToString(audioBytes)
        val payload = buildJsonObject {
            put("model", multimodalModel)
            put(
                "input",
                buildJsonObject {
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put(
                                        "content",
                                        buildJsonArray {
                                            add(buildJsonObject { put("audio", "data:audio/wav;base64,$encoded") })
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            put("parameters", buildJsonObject { put("asr_options", buildJsonObject { put("enable_itn", JsonPrimitive(true)) }) })
        }
        val response = client.post(multimodalEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), payload))
        }
        return parseChallengeTranscript(response.bodyAsText(), durationMs)
    }

    companion object {
        const val DEFAULT_FILE_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription"
        const val DEFAULT_FILE_MODEL = "qwen3-asr-flash-filetrans"
        const val DEFAULT_MULTIMODAL_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
        const val DEFAULT_MULTIMODAL_MODEL = "qwen3-asr-flash"
    }
}

class QwenChallengeNamer(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) : ChallengeNamer {
    private val log = LoggerFactory.getLogger(QwenChallengeNamer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun nameFrom(transcript: String): String {
        val spoken = transcript.trim()
        if (spoken.isEmpty() || !config.enabled) return ""
        return try {
            val payload = buildJsonObject {
                put("model", config.chatModel)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put(
                                    "content",
                                    "Name this neighborhood challenge from the spoken transcript. " +
                                        "Return ONLY 2-4 words. No quotes, no punctuation, no explanation. " +
                                        "If there is no real speech, return EMPTY.",
                                )
                            },
                        )
                        add(buildJsonObject { put("role", "user"); put("content", spoken.take(4_000)) })
                    },
                )
            }
            val response = client.post(config.chatEndpoint) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), payload))
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
                ?: root["output"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: ""
            val cleaned = content.trim().removePrefix("EMPTY").trim()
            if (cleaned.equals("EMPTY", ignoreCase = true)) "" else ChallengeLogic.sanitizeChallengeName(cleaned)
        } catch (e: Exception) {
            log.warn("Challenge naming failed: {}", e.message)
            ""
        }
    }
}
