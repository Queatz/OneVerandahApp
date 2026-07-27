package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Enrolls / deletes cloned voices against DashScope voice-enrollment for the
 * configured Qwen-Audio TTS model (`target_model` must match synthesis model).
 */
class QwenVoiceEnrollmentClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) : VoiceEnrollmentClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun enroll(
        name: String,
        sampleUrl: String,
        sampleBytes: ByteArray,
        contentType: String,
    ): String {
        check(config.enabled) { "Qwen voice enrollment requires QWEN_API_KEY" }
        val prefix = name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(10)
            .ifBlank { "ovvoice" }

        // Prefer URL enrollment (public OSS / public base URL). Fall back to
        // inline base64 when the host is clearly local / unreachable for DashScope.
        val useInline = isLocalOrPrivateUrl(sampleUrl)
        return if (useInline) {
            enrollInline(prefix, sampleBytes, contentType)
        } else {
            runCatching { enrollWithUrl(prefix, sampleUrl) }
                .getOrElse { enrollInline(prefix, sampleBytes, contentType) }
        }
    }

    override suspend fun deleteProviderVoice(providerVoiceId: String) {
        if (!config.enabled || providerVoiceId.isBlank()) return
        if (providerVoiceId.startsWith("placeholder-")) return
        val body = buildJsonObject {
            put("model", config.voiceDesignModel.ifBlank { "voice-enrollment" })
            put(
                "input",
                buildJsonObject {
                    put("action", "delete_voice")
                    put("voice_id", providerVoiceId)
                },
            )
        }
        runCatching {
            val response = client.post(config.voiceDesignEndpoint) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }
            if (!response.status.isSuccess()) {
                // Best-effort cleanup; local record still deletes.
            }
        }
    }

    private suspend fun enrollWithUrl(prefix: String, sampleUrl: String): String {
        val body = buildJsonObject {
            put("model", config.voiceDesignModel.ifBlank { "voice-enrollment" })
            put(
                "input",
                buildJsonObject {
                    put("action", "create_voice")
                    put("target_model", config.ttsModel)
                    put("prefix", prefix)
                    put("url", sampleUrl)
                },
            )
        }
        return postEnrollment(body)
    }

    private suspend fun enrollInline(
        prefix: String,
        sampleBytes: ByteArray,
        contentType: String,
    ): String {
        val encoded = Base64.getEncoder().encodeToString(sampleBytes)
        // qwen-voice-enrollment create with inline audio (works without a public URL).
        val body = buildJsonObject {
            put("model", "qwen-voice-enrollment")
            put(
                "input",
                buildJsonObject {
                    put("action", "create")
                    put("target_model", config.ttsModel)
                    put("preferred_name", prefix)
                    put(
                        "audio",
                        buildJsonObject {
                            put("data", encoded)
                        },
                    )
                },
            )
        }
        return runCatching { postEnrollment(body) }
            .getOrElse {
                // Fallback: classic voice-enrollment with data URL style is not
                // supported; rethrow a clearer error.
                throw QwenSpeechException(
                    "Qwen voice enrollment failed for local sample " +
                        "(${contentType}, ${sampleBytes.size} bytes): ${it.message}",
                    it,
                )
            }
    }

    private suspend fun postEnrollment(body: JsonObject): String {
        val response = client.post(config.voiceDesignEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenSpeechException("Qwen voice enrollment failed (${response.status}): $text")
        }
        val root = json.parseToJsonElement(text).jsonObject
        val output = root["output"]?.jsonObject
        val voiceId = output?.get("voice_id")?.jsonPrimitive?.contentOrNull
            ?: root["voice_id"]?.jsonPrimitive?.contentOrNull
            ?: output?.get("voice")?.jsonPrimitive?.contentOrNull
        if (voiceId.isNullOrBlank()) {
            throw QwenSpeechException("Qwen voice enrollment response missing voice_id: $text")
        }
        return voiceId
    }

    private fun isLocalOrPrivateUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("localhost") ||
            lower.contains("127.0.0.1") ||
            lower.contains("0.0.0.0") ||
            lower.startsWith("/images/") ||
            lower.contains("://10.") ||
            lower.contains("://192.168.") ||
            lower.contains("://172.")
    }
}
