package apap.oneverandah.images

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory, coroutine-based generation queue (single node, no external broker).
 *
 * Responsibilities:
 *  - Deduplicate: a target that already has a non-FAILED image is never queued
 *    again.
 *  - Bound concurrency to [maxConcurrent] workers and pause [delayBetweenMs]
 *    between generations to respect Qwen rate limits.
 *  - Track in-flight ids so the same image is never processed twice in parallel.
 */
class QueueService(
    private val generationService: ImageGenerationService,
    private val repository: ImageRepository,
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2,
    private val delayBetweenMs: Long = 1_000,
) {
    private val log = LoggerFactory.getLogger(QueueService::class.java)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val started = AtomicBoolean(false)

    /**
     * Persists [entity] and queues it for generation, unless an image for the
     * same target already exists in a non-FAILED state (duplicate prevention).
     * A previously FAILED image for the target is reset and retried.
     */
    suspend fun enqueue(entity: ImageEntity) {
        val existing = repository.findByTarget(entity.targetId, entity.targetType)
        when {
            existing == null -> {
                repository.save(entity)
                send(entity.id)
            }

            existing.status == ImageStatus.FAILED -> {
                repository.updateStatus(existing.id, ImageStatus.PENDING)
                send(existing.id)
            }

            else -> {
                // COMPLETED / GENERATING / PENDING -> already handled, skip.
                log.debug("Skipping duplicate enqueue for {} ({})", entity.targetId, existing.status)
            }
        }
    }

    /** Forces (re)generation of an existing image by id (used by the API/scheduler). */
    suspend fun requeue(imageId: String) {
        val existing = repository.findByImageId(imageId) ?: return
        repository.updateStatus(existing.id, ImageStatus.PENDING)
        send(existing.id)
    }

    private suspend fun send(imageId: String) {
        queue.send(imageId)
    }

    /** Starts the background workers exactly once. Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        repeat(maxConcurrent.coerceAtLeast(1)) { worker ->
            scope.launch {
                log.info("Generation worker #{} started", worker)
                for (imageId in queue) {
                    if (!scope.isActive) break
                    if (!inFlight.add(imageId)) continue
                    try {
                        generationService.generateImage(imageId)
                    } catch (e: Exception) {
                        log.error("Unexpected error generating {}: {}", imageId, e.message)
                    } finally {
                        inFlight.remove(imageId)
                    }
                    if (delayBetweenMs > 0) delay(delayBetweenMs)
                }
            }
        }
    }

    /** Re-queues every FAILED image; intended for the periodic scheduler. */
    suspend fun rescheduleFailed() {
        val failed = repository.findByStatus(ImageStatus.FAILED)
        if (failed.isEmpty()) return
        log.info("Re-queueing {} failed image(s)", failed.size)
        failed.forEach { requeue(it.id) }
    }
}
