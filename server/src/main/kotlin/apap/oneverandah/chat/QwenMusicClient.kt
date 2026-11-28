package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.QwenErrorLog
import apap.oneverandah.logging.SafeFailureDiagnostics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

data class GeneratedMusic(
    val bytes: ByteArray,
    val contentType: String = "audio/mpeg",
    val extension: String = "mp3",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedMusic) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            extension == other.extension
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }
}

fun interface MusicClient {
    suspend fun generateInstrumental(prompt: String): GeneratedMusic
}

/**
 * Generates purely instrumental background music via Alibaba Cloud Model Studio's
 * `fun-music-v1` endpoint (`/api/v1/services/audio/music/generation`) and downloads
 * the resulting MP3 bytes so they can be persisted durably via `StorageService`.
 */
class DashScopeMusicClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
    private val errorLog: QwenErrorLog? = null,
) : MusicClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun generateInstrumental(prompt: String): GeneratedMusic {
        check(config.enabled) { "Qwen music generation requires QWEN_API_KEY" }
        val rawTrimmed = prompt.trim()
        require(rawTrimmed.isNotEmpty()) { "Music prompt must not be blank" }
        val cleanPrompt = enforceInstrumentalOnlyPrompt(rawTrimmed).take(MAX_PROMPT_CHARS)
        val endpoint = config.musicEndpoint.trim().ifEmpty { QwenChatConfig.DEFAULT_MUSIC_ENDPOINT }
        val model = config.musicModel.trim().ifEmpty { QwenChatConfig.DEFAULT_MUSIC_MODEL }

        val requestBody = buildJsonObject {
            put("model", model)
            put(
                "input",
                buildJsonObject {
                    put("prompt", cleanPrompt)
                    put("is_instrumental", true)
                    put("format", "mp3")
                    put("enable_aigc_watermark", false)
                },
            )
        }

        return try {
            val (status, rawBody) = withTransientHttpRetry("music generation request") {
                val response = client.post(endpoint) {
                    header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    contentType(ContentType.Application.Json)
                    timeout {
                        requestTimeoutMillis = MUSIC_HTTP_REQUEST_TIMEOUT_MILLIS
                        socketTimeoutMillis = MUSIC_HTTP_REQUEST_TIMEOUT_MILLIS
                    }
                    setBody(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), requestBody))
                }
                response.status to response.bodyAsText()
            }
            if (!status.isSuccess()) {
                val detail = extractErrorMessage(rawBody) ?: rawBody.take(300)
                throw QwenMusicException("Qwen music generation failed (${status.value}): $detail")
            }

            val root = json.parseToJsonElement(rawBody).jsonObject
            val errorCode = root["code"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            if (errorCode != null) {
                val errorMessage = root["message"]?.jsonPrimitive?.contentOrNull ?: errorCode
                throw QwenMusicException("Qwen music generation failed ($errorCode): $errorMessage")
            }

            val audioObj = root["output"]?.jsonObject?.get("audio")?.jsonObject
                ?: throw QwenMusicException("Qwen music generation returned no output.audio object")
            val audioUrl = audioObj["url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            val inlineBase64 = audioObj["data"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

            val bytes = when {
                audioUrl != null -> downloadAudioBytes(audioUrl)
                inlineBase64 != null -> Base64.getDecoder().decode(inlineBase64)
                else -> throw QwenMusicException("Qwen music generation returned neither audio.url nor audio.data")
            }
            if (bytes.isEmpty()) {
                throw QwenMusicException("Qwen music generation returned empty audio payload")
            }
            GeneratedMusic(
                bytes = bytes,
                contentType = "audio/mpeg",
                extension = "mp3",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: QwenMusicException) {
            errorLog?.record("[fun-music] ${error.message}")
            throw error
        } catch (error: Exception) {
            val msg = "Qwen music generation failed: ${SafeFailureDiagnostics.describe(error)}"
            errorLog?.record("[fun-music] $msg")
            throw QwenMusicException(msg, error)
        }
    }

    private suspend fun downloadAudioBytes(url: String): ByteArray =
        withTransientHttpRetry("generated music download request") {
            val downloadResponse = client.get(url) {
                timeout {
                    requestTimeoutMillis = AUDIO_DOWNLOAD_TIMEOUT_MILLIS
                    socketTimeoutMillis = AUDIO_DOWNLOAD_TIMEOUT_MILLIS
                }
            }
            if (!downloadResponse.status.isSuccess()) {
                throw QwenMusicException("Downloading generated music failed (${downloadResponse.status.value})")
            }
            downloadResponse.body()
        }

    private suspend fun <T> withTransientHttpRetry(
        operation: String,
        attempts: Int = 3,
        block: suspend () -> T,
    ): T {
        val startedAt = System.nanoTime()
        val context = currentCoroutineContext()[CoroutineName]?.name ?: "unscoped"
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                last = e
                val transient = DashScopeQwenClient.isTransientHttpClose(e)
                if (!transient || attempt == attempts - 1) {
                    if (transient) {
                        log.warn(
                            "Qwen music HTTP operation exhausted transient retries: context={} operation={} attempt={}/{} elapsedMs={} diagnostics={}",
                            context,
                            operation,
                            attempt + 1,
                            attempts,
                            (System.nanoTime() - startedAt) / 1_000_000,
                            SafeFailureDiagnostics.describe(e),
                        )
                    }
                    throw e
                }
                log.warn(
                    "Retrying Qwen music HTTP operation after transient close: context={} operation={} attempt={}/{} elapsedMs={} diagnostics={}",
                    context,
                    operation,
                    attempt + 1,
                    attempts,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    SafeFailureDiagnostics.describe(e),
                )
                delay(400L * (attempt + 1))
            }
        }
        throw last ?: error("Qwen music HTTP retry exhausted")
    }

    private fun extractErrorMessage(rawBody: String): String? = runCatching {
        val obj = json.parseToJsonElement(rawBody).jsonObject
        val code = obj["code"]?.jsonPrimitive?.contentOrNull
        val message = obj["message"]?.jsonPrimitive?.contentOrNull
        listOfNotNull(code, message).joinToString(": ").takeIf(String::isNotBlank)
    }.getOrNull()

    companion object {
        private val log = LoggerFactory.getLogger(DashScopeMusicClient::class.java)
        private const val MAX_PROMPT_CHARS = 1_800
        private const val MUSIC_HTTP_REQUEST_TIMEOUT_MILLIS = 300_000L
        private const val AUDIO_DOWNLOAD_TIMEOUT_MILLIS = 120_000L
        private const val INSTRUMENTAL_PREFIX =
            "Purely instrumental background music (纯音乐, 纯器乐演奏, 无人声, 无歌词, 无伴唱), strictly 100% instrumental only — no vocals, no lyrics, no singing, no humming, no choir, no spoken word, no human voice."
        private const val INSTRUMENTAL_SUFFIX = "Instruments only, zero vocals, 无歌词无人声纯音乐."

        internal fun enforceInstrumentalOnlyPrompt(rawPrompt: String): String {
            val sanitized = sanitizeInstrumentalMusicHint(rawPrompt)
            val hasExplicitNoVocals = sanitized.contains("no vocals", ignoreCase = true) &&
                sanitized.contains("no lyrics", ignoreCase = true) &&
                sanitized.contains("纯音乐")
            return if (hasExplicitNoVocals) {
                sanitized
            } else {
                "$INSTRUMENTAL_PREFIX ${sanitized.trimEnd('.')} . $INSTRUMENTAL_SUFFIX"
                    .replace(" .", ".")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }
    }
}

class QwenMusicException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
