package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.config.SpeechgenConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import kotlin.math.roundToInt

class SpeechgenSpeechClient(
    private val config: SpeechgenConfig,
    private val client: HttpClient,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val pollTimeoutMs: Long = DEFAULT_POLL_TIMEOUT_MS,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) : SpeechClient {
    override suspend fun designVoice(): String {
        check(config.enabled) { "Speechgen TTS requires SPEECHGEN_API_KEY and SPEECHGEN_EMAIL" }
        val voice = config.voice.trim()
        check(voice.isNotEmpty()) { "Speechgen TTS requires SPEECHGEN_VOICE" }
        return voice
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
        check(config.enabled) { "Speechgen TTS requires SPEECHGEN_API_KEY and SPEECHGEN_EMAIL" }
        if (request.text.length > MAX_TEXT_LENGTH) {
            throw SpeechgenException("Speechgen TTS supports at most $MAX_TEXT_LENGTH characters")
        }

        return try {
            withContext(Dispatchers.IO) {
                try {
                    synthesizeAt(config.endpoint, request)
                } catch (cause: SpeechgenException) {
                    if (!cause.isMissingSubtitles() || !canFallbackToText()) throw cause
                    synthesizeAt(config.textEndpoint, request)
                }
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: SpeechgenException) {
            throw cause
        } catch (cause: Exception) {
            throw SpeechgenException("Speechgen TTS failed: ${cause.message ?: cause::class.simpleName}", cause)
        }
    }

    private suspend fun synthesizeAt(url: String, request: SpeechRequest): GeneratedSpeech {
        val submission = postForm(
            url = url,
            parameters = Parameters.build {
                append("token", config.apiKey)
                append("email", config.email)
                append("voice", config.voice)
                append("text", request.text)
                append("format", "mp3")
            },
        )
        val contentType = submission.headers[HttpHeaders.ContentType].orEmpty()
        if (!contentType.isJsonContentType()) {
            return GeneratedSpeech(
                bytes = submission.body(),
                contentType = contentType.substringBefore(';').ifBlank { "audio/mpeg" },
            )
        }

        val ready = awaitReady(parseJsonObject(submission.bodyAsText()))
        val fileUrl = ready.string("file")
            ?: throw SpeechgenException("Speechgen response did not include an audio file URL")
        requireSecureAudioUrl(fileUrl)

        val audio = client.get(fileUrl)
        if (!audio.status.isSuccess()) {
            throw SpeechgenException("Speechgen audio download returned HTTP ${audio.status.value}")
        }
        val bytes: ByteArray = audio.body()
        if (bytes.isEmpty()) throw SpeechgenException("Speechgen returned an empty audio file")
        return GeneratedSpeech(
            bytes = bytes,
            contentType = audio.headers[HttpHeaders.ContentType]
                ?.substringBefore(';')
                ?.takeIf { it.isNotBlank() }
                ?: "audio/mpeg",
            words = speechgenWordTimings(request.text, ready),
        )
    }

    private fun canFallbackToText(): Boolean =
        config.textEndpoint.isNotBlank() && config.textEndpoint != config.endpoint

    private fun SpeechgenException.isMissingSubtitles(): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return "no subs" in normalized ||
                "no subtitle" in normalized ||
                "subtitle cue" in normalized
    }

    private suspend fun awaitReady(initial: JsonObject): JsonObject {
        var payload = initial
        val deadline = System.currentTimeMillis() + pollTimeoutMs
        while (payload.speechgenStatus() == 0) {
            val id = payload.string("id")?.takeIf { it != "0" }
                ?: throw SpeechgenException("Speechgen queued the task without an id")
            if (System.currentTimeMillis() >= deadline) {
                throw SpeechgenException("Speechgen TTS timed out waiting for audio")
            }
            delayMillis(pollIntervalMs)
            val polled = postForm(
                url = config.resultEndpoint,
                parameters = Parameters.build {
                    append("token", config.apiKey)
                    append("email", config.email)
                    append("id", id)
                },
            )
            payload = parseJsonObject(polled.bodyAsText())
        }
        return when (payload.speechgenStatus()) {
            1 -> payload
            -1 -> throw SpeechgenException(
                payload.string("error")?.takeIf { it.isNotBlank() }
                    ?: "Speechgen returned status -1",
            )
            else -> throw SpeechgenException(
                payload.string("error")?.takeIf { it.isNotBlank() }
                    ?: "Speechgen returned status ${payload.string("status") ?: "unknown"}",
            )
        }
    }

    private suspend fun postForm(url: String, parameters: Parameters): HttpResponse {
        val response = client.submitForm(url = url, formParameters = parameters)
        if (!response.status.isSuccess()) {
            throw SpeechgenException("Speechgen API returned HTTP ${response.status.value}")
        }
        return response
    }

    private fun requireSecureAudioUrl(fileUrl: String) {
        val uri = runCatching { URI(fileUrl) }.getOrElse {
            throw SpeechgenException("Speechgen returned an invalid audio file URL", it)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
            throw SpeechgenException("Speechgen returned an unsafe audio file URL")
        }
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 2000
        const val DEFAULT_POLL_INTERVAL_MS = 2_000L
        const val DEFAULT_POLL_TIMEOUT_MS = 60_000L
    }
}

internal fun speechgenWordTimings(sourceText: String, response: JsonObject): List<SpeechWordTiming> {
    val fragments = response.cutObjects() ?: response.subtitleCues() ?: return emptyList()
    var cursor = 0
    return fragments.mapNotNull { fragment ->
        val text = fragment.text()?.trim().orEmpty()
        if (text.isEmpty()) return@mapNotNull null
        val beginMs = fragment.beginMs() ?: return@mapNotNull null
        val endMs = fragment.endMs(beginMs) ?: return@mapNotNull null
        if (endMs - beginMs < MIN_WORD_DURATION_MS) return@mapNotNull null
        val index = sourceText.indexOf(text, startIndex = cursor)
        val beginIndex: Int
        val endIndex: Int
        if (index >= 0) {
            beginIndex = index
            endIndex = index + text.length
            cursor = endIndex
        } else {
            beginIndex = -1
            endIndex = -1
        }
        SpeechWordTiming(
            text = text,
            beginMs = beginMs,
            endMs = endMs,
            beginIndex = beginIndex,
            endIndex = endIndex,
        )
    }
}

private fun JsonObject.cutObjects(): List<JsonObject>? {
    val element = this["cuts"] ?: this["obrezka"] ?: this["words"] ?: return null
    val array = element.asJsonArray() ?: return null
    val objects = array.mapNotNull { it as? JsonObject }
    return objects.takeIf { it.isNotEmpty() }
}

private fun JsonObject.subtitleCues(): List<JsonObject>? {
    val srt = string("srt")
        ?: string("subtitles")
        ?: string("subs")?.takeIf { it.contains("-->") }
        ?: return null
    return parseSrtCues(srt).takeIf { it.isNotEmpty() }
}

private fun parseSrtCues(srt: String): List<JsonObject> {
    val cues = mutableListOf<JsonObject>()
    val blocks = srt.replace("\r\n", "\n").trim().split(Regex("\n\\s*\n"))
    for (block in blocks) {
        val lines = block.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) continue
        val timingLine = lines.firstOrNull { it.contains("-->") } ?: continue
        val times = timingLine.split("-->", limit = 2)
        if (times.size != 2) continue
        val text = lines.dropWhile { !it.contains("-->") }.drop(1).joinToString(" ").trim()
        if (text.isEmpty()) continue
        cues += JsonObject(
            mapOf(
                "text" to JsonPrimitive(text),
                "start" to JsonPrimitive(times[0].trim()),
                "end" to JsonPrimitive(times[1].trim()),
            ),
        )
    }
    return cues
}

private fun JsonObject.text(): String? =
    string("text") ?: string("word") ?: string("content") ?: string("title") ?: string("subtitle")

private fun JsonObject.beginMs(): Int? =
    timeMs("start_ms", "begin_ms", "beginMs", "startMs")
        ?: timeMs("start", "begin", "from", "time_start", "start_time")
        ?: rangeMs()?.first

private fun JsonObject.endMs(beginMs: Int): Int? =
    timeMs("end_ms", "finish_ms", "endMs", "finishMs")
        ?: timeMs("end", "finish", "to", "time_end", "end_time")
        ?: rangeMs()?.second
        ?: timeMs("duration_ms")?.let { beginMs + it }
        ?: timeMs("duration")?.let { beginMs + it }

private fun JsonObject.rangeMs(): Pair<Int, Int>? {
    val raw = string("time") ?: string("times") ?: string("timestamp") ?: return null
    val match = TIME_RANGE.matchEntire(raw.trim()) ?: return null
    val begin = parseSpeechgenTimeToMs(match.groupValues[1], "time") ?: return null
    val end = parseSpeechgenTimeToMs(match.groupValues[2], "time") ?: return null
    return begin to end
}

private fun JsonObject.timeMs(vararg keys: String): Int? {
    for (key in keys) {
        val element = this[key] ?: continue
        parseSpeechgenTimeToMs(element, key)?.let { return it }
    }
    return null
}

internal fun parseSpeechgenTimeToMs(element: JsonElement, fieldName: String): Int? {
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    return parseSpeechgenTimeToMs(primitive.content, fieldName)
}

internal fun parseSpeechgenTimeToMs(raw: String, fieldName: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (CLOCK_TIME.containsMatchIn(trimmed)) {
        return parseClockToMs(trimmed)
    }
    val value = trimmed.toDoubleOrNull() ?: return null
    val asMs = if (
        fieldName.contains("ms", ignoreCase = true) ||
        !trimmed.contains('.')
    ) {
        value.roundToInt()
    } else {
        (value * 1_000.0).roundToInt()
    }
    return asMs.coerceAtLeast(0)
}

private fun parseClockToMs(raw: String): Int? {
    val match = CLOCK_TIME.find(raw.trim()) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    val seconds = match.groupValues[3].toIntOrNull() ?: return null
    val fraction = match.groupValues[4]
    val millis = when {
        fraction.isEmpty() -> 0
        fraction.length >= 3 -> fraction.take(3).toIntOrNull() ?: 0
        else -> fraction.padEnd(3, '0').toIntOrNull() ?: 0
    }
    return (((hours * 60) + minutes) * 60 + seconds) * 1_000 + millis
}

private fun parseJsonObject(body: String): JsonObject =
    Json.parseToJsonElement(body).jsonObject

private fun JsonObject.speechgenStatus(): Int =
    string("status")?.toIntOrNull() ?: Int.MIN_VALUE

private fun JsonObject.string(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: primitive.content.trim().takeIf { it.isNotEmpty() && it != "null" }
}

private fun JsonElement.asJsonArray(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> (this["cuts"] ?: this["items"] ?: this["data"]) as? JsonArray
    else -> null
}

private fun String.isJsonContentType(): Boolean {
    val mediaType = substringBefore(';').trim()
    return mediaType.equals("application/json", ignoreCase = true) ||
        mediaType.endsWith("+json", ignoreCase = true)
}

private const val MIN_WORD_DURATION_MS = 20
private val TIME_RANGE = Regex("""^([\d:.]+)\s*(?:-->|-|–|—)\s*([\d:.]+)$""")
private val CLOCK_TIME = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})(?:[,.](\d{1,3}))?""")

class SpeechgenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
