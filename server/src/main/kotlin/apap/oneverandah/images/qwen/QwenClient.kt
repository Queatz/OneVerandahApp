package apap.oneverandah.images.qwen

import apap.oneverandah.images.storage.GeneratedImage

/**
 * Generates an image from a text [prompt].
 *
 * Implementations: [DashScopeQwenClient] (real Qwen/DashScope text-to-image)
 * and [PlaceholderQwenClient] (an offline, deterministic renderer used when no
 * API key is configured so the pipeline still produces a usable cover).
 */
interface QwenClient {
    /** A short label used in logs (e.g. "qwen", "placeholder"). */
    val kind: String

    /** Returns the generated image bytes, or throws on failure. */
    suspend fun generate(prompt: String): GeneratedImage
}
