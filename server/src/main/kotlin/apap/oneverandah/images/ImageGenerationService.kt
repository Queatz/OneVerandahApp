package apap.oneverandah.images

import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.qwen.QwenErrorLog
import apap.oneverandah.images.storage.LocalImageCache
import apap.oneverandah.images.storage.StorageService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Turns a single queued [ImageEntity] into a stored, publicly reachable cover.
 *
 * Steps: mark GENERATING → ask the [QwenClient] for image bytes → upload via the
 * [StorageService] → mark COMPLETED with the resulting URL. Each external step
 * is retried with exponential backoff; if everything fails the record is marked
 * FAILED so a later scheduled pass can retry it.
 *
 * Whenever a record is marked FAILED, the underlying error is also appended to
 * [errorLog]. This is the single place a status becomes FAILED, so recording it
 * here guarantees the invariant "a FAILED image always has a logged error" — no
 * matter which step failed (Qwen generation *or* storage upload).
 */
class ImageGenerationService(
    private val qwenClient: QwenClient,
    private val storage: StorageService,
    private val repository: ImageRepository,
    private val maxRetries: Int = 3,
    private val errorLog: QwenErrorLog? = null,
    private val imageCache: LocalImageCache? = null,
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
            // Reuse a locally cached image if a previous run generated one but
            // failed to upload it - this avoids paying for another (rate-limited)
            // Qwen generation just to recover from a transient storage failure.
            val cached = imageCache?.load(entity.targetType, entity.targetId)
            val image = if (cached != null) {
                log.info("Reusing locally cached image for {}; skipping generation", entity.targetId)
                cached
            } else {
                val generated = withRetry("qwen:${entity.targetId}") { qwenClient.generate(entity.prompt) }
                // Persist locally the moment generation succeeds, so the bytes
                // survive even if the upload below fails and the image is retried.
                imageCache?.save(entity.targetType, entity.targetId, generated)
                generated
            }
            val objectKey = "${entity.targetType.name.lowercase()}/${entity.targetId}.${image.extension}"
            val url = withRetry("upload:${entity.targetId}") { storage.upload(objectKey, image) }
            repository.updateImage(imageId, url, ImageStatus.COMPLETED)
            // The upload succeeded, so the local backup is no longer needed.
            imageCache?.remove(entity.targetType, entity.targetId)
            log.info("Generated cover for {} -> {}", entity.targetId, url)
        } catch (e: CancellationException) {
            // App shutdown / coroutine cancellation is not a generation failure;
            // don't mark the record FAILED or log it as an error.
            throw e
        } catch (e: Exception) {
            log.error("Generation failed for {} ({}): {}", entity.targetId, imageId, e.message)
            // Record every failure that leads to a FAILED status, regardless of
            // whether it originated in Qwen generation or the storage upload.
            errorLog?.record(
                "generation failed for ${entity.targetType} '${entity.targetId}' " +
                    "(generator=${qwenClient.kind}): ${e.message}",
            )
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
