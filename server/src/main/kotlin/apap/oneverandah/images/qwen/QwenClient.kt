package apap.oneverandah.images.qwen

import apap.oneverandah.images.storage.GeneratedImage

/**
 * Abstraction over the Qwen / DashScope image generation backend.
 *
 * Implementations must be safe to call concurrently; the queue service may
 * run several generations in parallel (bounded by configuration).
 *
 * [referenceImageUrls] enables reference-to-image (R2I) scene crafting when
 * non-empty — the model should ground the result in those apartment photos.
 */
interface QwenClient {
    /** Human-readable name used in logs (e.g. `"qwen"`, `"placeholder"`). */
    val kind: String

    /** Returns the generated image bytes, or throws on failure. */
    suspend fun generate(
        prompt: String,
        referenceImageUrls: List<String> = emptyList(),
    ): GeneratedImage
}
