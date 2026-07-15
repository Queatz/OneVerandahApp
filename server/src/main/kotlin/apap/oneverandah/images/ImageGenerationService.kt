package apap.oneverandah.images

import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.StorageService
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Turns a single queued [ImageEntity] into a stored, publicly reachable cover.
 *
 * Steps: mark GENERATING → ask the [QwenClient] for image bytes → upload via the
 * [StorageService] → mark COMPLETED with the resulting URL. Each external step
 * is retried with exponential backoff; if everything fails the record is marked
 * FAILED so a later scheduled pass can retry it.
 */
class ImageGenerationService(
    private val qwenClient: QwenClient,
    private val storage: StorageService,
    private val repository: ImageRepository,
    private val maxRetries: Int = 3,
) {
    private val log = LoggerFactory.getLogger(ImageGenerationService::class.java)

    suspend fun generateImage(imageId: String) {
        val entity = repository.findByImageId(imageId)
        if (entity == null) {
            log.warn("Image {} not found; skipping generation", imageId)
            return
        }
        if (entity.status == ImageStatus.COMPLETED && entity.imageUrl != null) return

        repository.updateStatus(imageId, ImageStatus.GENERATING)
        try {
            val image = withRetry("qwen:${entity.targetId}") { qwenClient.generate(entity.prompt) }
            val objectKey = "${entity.targetType.name.lowercase()}/${entity.targetId}.${image.extension}"
            val url = withRetry("upload:${entity.targetId}") { storage.upload(objectKey, image) }
            repository.updateImage(imageId, url, ImageStatus.COMPLETED)
            log.info("Generated cover for {} -> {}", entity.targetId, url)
        } catch (e: Exception) {
            log.error("Generation failed for {} ({}): {}", entity.targetId, imageId, e.message)
            repository.updateStatus(imageId, ImageStatus.FAILED)
        }
    }

    private suspend fun <T> withRetry(label: String, block: suspend () -> T): T {
        var lastError: Exception? = null
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    val backoff = (1_000L shl (attempt - 1)).coerceAtMost(30_000L)
                    log.warn("Attempt {}/{} for {} failed: {} (retrying in {} ms)", attempt, maxRetries, label, e.message, backoff)
                    delay(backoff)
                }
            }
        }
        throw lastError ?: IllegalStateException("Retry failed for $label")
    }
}
