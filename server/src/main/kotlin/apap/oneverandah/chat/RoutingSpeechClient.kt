package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.model.Language

/** Routes speech requests to provider/voice pairs explicitly supported for their content language. */
class RoutingSpeechClient(
    private val qwenClient: SpeechClient,
    private val speechgenClient: SpeechClient,
    private val qwenEnabled: Boolean,
    private val speechgenEnabled: Boolean,
) : SpeechClient {
    override suspend fun designVoice(): String = when {
        qwenEnabled -> qwenClient.designVoice()
        speechgenEnabled -> speechgenClient.designVoice()
        else -> qwenClient.designVoice()
    }

    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech =
        if (speechgenEnabled && request.requiresSpeechgen()) {
            speechgenClient.synthesize(request, voiceId)
        } else {
            qwenClient.synthesize(request, voiceId)
        }
}

internal fun SpeechRequest.requiresSpeechgen(): Boolean {
    val tag = Language.normalizeContentTag(languageTag)
    return tag in MYANMAR_LANGUAGE_TAGS || text.any { it.isMyanmarCharacter() }
}

private fun Char.isMyanmarCharacter(): Boolean =
    this in '\u1000'..'\u109F' || this in '\uA9E0'..'\uA9FF' || this in '\uAA60'..'\uAA7F'

private val MYANMAR_LANGUAGE_TAGS = setOf("my", "bur", "mya")
