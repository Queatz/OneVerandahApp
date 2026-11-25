package apap.oneverandah.images.qwen

import apap.oneverandah.images.storage.GeneratedImage
import java.util.Base64

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
        size: String? = null,
    ): GeneratedImage

    /** Generates a mask from the provided image bytes without persisting an intermediate object. */
    suspend fun generateMask(image: GeneratedImage, prompt: String, size: String? = null): GeneratedImage {
        val encoded = Base64.getEncoder().encodeToString(image.bytes)
        return generate(prompt, listOf("data:${image.contentType};base64,$encoded"), size)
    }
}
