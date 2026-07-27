package apap.oneverandah.api

import apap.oneverandah.model.Language
import kotlinx.serialization.Serializable

/**
 * A cloned TTS voice enrolled with Qwen and scoped to one language (or all).
 *
 * [languageTag] is either a supported UI/source language (`en`, `vi`, …)
 * or [FALLBACK_LANGUAGE_TAG] when the voice should cover every language that
 * does not have a more specific active voice.
 */
@Serializable
data class ClonedVoice(
    val id: String,
    val name: String,
    val languageTag: String,
    /** Provider voice id returned by Qwen voice enrollment (used as TTS `voice`). */
    val providerVoiceId: String,
    /** Public URL of the uploaded sample audio, when available. */
    val sampleUrl: String? = null,
    /** Exactly one voice may be active per [languageTag]. */
    val active: Boolean = false,
    val createdAt: Long,
) {
    companion object {
        /** Language tag meaning "fallback for every language". */
        const val FALLBACK_LANGUAGE_TAG = "all"

        fun normalizeLanguageTag(raw: String?): String? {
            val trimmed = raw?.trim()?.lowercase().orEmpty()
            if (trimmed.isEmpty()) return null
            if (trimmed == FALLBACK_LANGUAGE_TAG || trimmed == "*" || trimmed == "any") {
                return FALLBACK_LANGUAGE_TAG
            }
            return Language.fromTag(trimmed).tag
        }
    }
}

@Serializable
data class ClonedVoicesResponse(
    val items: List<ClonedVoice> = emptyList(),
)

@Serializable
data class ActivateClonedVoiceResponse(
    val voice: ClonedVoice,
)
