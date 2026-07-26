package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.config.QwenChatConfig
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult
import com.alibaba.dashscope.audio.tts.timestamp.Word
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer
import com.alibaba.dashscope.common.ResultCallback
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class DashScopeSpeechClient(
    private val config: QwenChatConfig,
    @Suppress("unused") private val client: HttpClient,
    private val synthesizer: QwenAudioTtsSynthesizer = DashScopeQwenAudioTtsSynthesizer(config),
) : SpeechClient {
    /**
     * Qwen-Audio-TTS ships curated system voices instead of CosyVoice-style prompt design.
     * Resolve the configured default designed voice once and reuse it for synthesis.
     */
    override suspend fun designVoice(): String {
        check(config.enabled) { "Qwen speech synthesis requires QWEN_API_KEY" }
        val voice = config.ttsVoice.trim()
        check(voice.isNotEmpty()) { "Qwen TTS requires QWEN_TTS_VOICE" }
        return voice
    }

    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
        check(config.enabled) { "Qwen speech synthesis requires QWEN_API_KEY" }
        return try {
            val audio = synthesizer.synthesize(request.text, voiceId, request.languageTag)
            GeneratedSpeech(
                bytes = audio.bytes,
                contentType = "audio/mpeg",
                words = audio.words,
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw QwenSpeechException("Qwen TTS failed: ${cause.message ?: cause::class.simpleName}", cause)
        }
    }
}

data class SynthesizedAudio(
    val bytes: ByteArray,
    val words: List<SpeechWordTiming> = emptyList(),
)

fun interface QwenAudioTtsSynthesizer {
    suspend fun synthesize(text: String, voiceId: String, languageTag: String): SynthesizedAudio
}

private class DashScopeQwenAudioTtsSynthesizer(
    private val config: QwenChatConfig,
) : QwenAudioTtsSynthesizer {
    override suspend fun synthesize(text: String, voiceId: String, languageTag: String): SynthesizedAudio =
        withContext(Dispatchers.IO) {
            val parameters = SpeechSynthesisParam.builder()
                .apiKey(config.apiKey)
                .model(config.ttsModel)
                .voice(voiceId)
                .format(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS)
                .languageHints(listOf(languageTag.substringBefore('-').lowercase()))
                .enableWordTimestamp(true)
                .build()

            val audioOut = ByteArrayOutputStream()
            val latestWords = AtomicReference<List<SpeechWordTiming>>(emptyList())
            val done = CountDownLatch(1)
            val failure = AtomicReference<Exception?>(null)

            val callback = object : ResultCallback<SpeechSynthesisResult>() {
                override fun onEvent(message: SpeechSynthesisResult) {
                    message.audioFrame?.let { frame ->
                        audioOut.write(frame.toByteArray())
                    }
                    val sentence = message.timestamp ?: return
                    val mapped = sentence.words
                        ?.mapNotNull { it.toSpeechWordTiming() }
                        .orEmpty()
                    if (mapped.isNotEmpty()) {
                        // Providers stream progressive sentence snapshots; keep the richest one.
                        val current = latestWords.get()
                        if (mapped.size >= current.size || sentence.endTime > 0) {
                            latestWords.set(mapped)
                        }
                    }
                }

                override fun onComplete() {
                    done.countDown()
                }

                override fun onError(e: Exception) {
                    failure.set(e)
                    done.countDown()
                }
            }

            // Callback mode delivers both binary frames and timestamp JSON events.
            SpeechSynthesizer(parameters, callback, config.ttsEndpoint).call(text)
            done.await()
            failure.get()?.let { throw it }
            SynthesizedAudio(
                bytes = audioOut.toByteArray(),
                words = latestWords.get(),
            )
        }
}

internal fun Word.toSpeechWordTiming(): SpeechWordTiming? {
    val wordText = text?.trim().orEmpty()
    if (wordText.isEmpty()) return null
    val begin = beginTime.coerceAtLeast(0)
    val end = endTime.coerceAtLeast(begin)
    if (end <= begin) return null
    return SpeechWordTiming(
        text = wordText,
        beginMs = begin,
        endMs = end,
        beginIndex = beginIndex,
        endIndex = endIndex,
    )
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return bytes
}

class QwenSpeechException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
