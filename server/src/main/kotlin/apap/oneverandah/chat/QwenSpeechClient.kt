package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.config.QwenChatConfig
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer

class DashScopeSpeechClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
    private val synthesizer: CosyVoiceSynthesizer = DashScopeCosyVoiceSynthesizer(config),
) : SpeechClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun designVoice(): String {
        check(config.enabled) { "Qwen voice design requires QWEN_API_KEY" }
        val body = buildJsonObject {
            put("model", config.voiceDesignModel)
            put("input", buildJsonObject {
                put("action", "create_voice")
                put("target_model", config.ttsModel)
                put("prefix", "onev")
                put(
                    "voice_prompt",
                    "A warm, calm and approachable multilingual community assistant voice with clear pronunciation.",
                )
                put("preview_text", "Hello! I am your friendly One Verandah community assistant.")
            })
            put("parameters", buildJsonObject {
                put("sample_rate", 24_000)
                put("response_format", "wav")
            })
        }
        val response = client.post(config.voiceDesignEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenSpeechException("Qwen voice design failed (${response.status}): $responseText")
        }
        val output = json.parseToJsonElement(responseText).jsonObject["output"]?.jsonObject
            ?: throw QwenSpeechException("Qwen voice design returned no output: $responseText")
        return output["voice"]?.jsonPrimitive?.content
            ?: output["voice_id"]?.jsonPrimitive?.content
            ?: throw QwenSpeechException("Qwen voice design returned no voice id: $responseText")
    }

    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
        check(config.enabled) { "Qwen speech synthesis requires QWEN_API_KEY" }
        return try {
            GeneratedSpeech(
                bytes = synthesizer.synthesize(request.text, voiceId, request.languageTag),
                contentType = "audio/mpeg",
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw QwenSpeechException("Qwen TTS failed: ${cause.message ?: cause::class.simpleName}", cause)
        }
    }
}

fun interface CosyVoiceSynthesizer {
    suspend fun synthesize(text: String, voiceId: String, languageTag: String): ByteArray
}

private class DashScopeCosyVoiceSynthesizer(
    private val config: QwenChatConfig,
) : CosyVoiceSynthesizer {
    override suspend fun synthesize(text: String, voiceId: String, languageTag: String): ByteArray =
        withContext(Dispatchers.IO) {
            val parameters = SpeechSynthesisParam.builder()
                .apiKey(config.apiKey)
                .model(config.ttsModel)
                .voice(voiceId)
                .format(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS)
                .languageHints(listOf(languageTag.substringBefore('-').lowercase()))
                .build()
            SpeechSynthesizer(parameters, null, config.ttsEndpoint).call(text).toByteArray()
        }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return bytes
}

class QwenSpeechException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)