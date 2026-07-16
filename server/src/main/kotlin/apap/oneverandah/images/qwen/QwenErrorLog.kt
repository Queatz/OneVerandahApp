package apap.oneverandah.images.qwen

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * Appends every Qwen/DashScope failure to a local text file, so failures can
 * be inspected without digging through the full application log.
 *
 * The resulting file is also served as a static resource at `GET /qwen_errors`
 * (see [apap.oneverandah.routes.imageRoutes]).
 */
class QwenErrorLog(private val file: File) {
    private val log = LoggerFactory.getLogger(QwenErrorLog::class.java)
    private val lock = ReentrantLock()

    /** Appends a timestamped [message] to [file]. Never throws. */
    fun record(message: String) {
        val line = "${Instant.now()} $message"
        lock.lock()
        try {
            file.absoluteFile.parentFile?.mkdirs()
            file.appendText(line + System.lineSeparator())
        } catch (e: Exception) {
            log.warn("Failed to persist Qwen error to {}: {}", file.absolutePath, e.message)
        } finally {
            lock.unlock()
        }
    }
}
