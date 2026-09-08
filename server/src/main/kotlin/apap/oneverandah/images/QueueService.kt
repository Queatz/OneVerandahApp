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
 *  - Deduplicate: a target that already has a COMPLETED image is never queued
 *    again. FAILED images are reset and retried. PENDING/GENERATING images are
 *    re-sent because the in-memory channel does not survive a process restart.
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
     * same target is already COMPLETED. A previously FAILED image is reset and
     * retried. PENDING/GENERATING records are re-sent so a restart can recover
     * jobs that never made it into this process's in-memory channel.
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

            existing.status == ImageStatus.COMPLETED -> {
                log.debug("Skipping duplicate enqueue for {} ({})", entity.targetId, existing.status)
            }

            else -> {
                // PENDING / GENERATING. The in-memory channel does not survive a
                // process restart, so a persisted unfinished record is not
                // actually "already queued". Re-send it; inFlight + generateImage
                // make duplicate sends in a live process safe.
                send(existing.id)
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

    /**
     * Re-queues PENDING and GENERATING records left behind by a previous
     * process. Unlike [rescheduleFailed], this must not run on the hourly
     * loop — live waiting jobs are already in the channel.
     */
    suspend fun resumeInterrupted() {
        val interrupted = repository.findByStatus(ImageStatus.PENDING) +
            repository.findByStatus(ImageStatus.GENERATING)
        if (interrupted.isEmpty()) return
        log.info("Re-queueing {} interrupted image(s)", interrupted.size)
        interrupted.forEach { send(it.id) }
    }
}
