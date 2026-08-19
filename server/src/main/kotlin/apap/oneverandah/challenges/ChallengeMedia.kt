package apap.oneverandah.challenges

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

data class PreparedChallengeMedia(
    val videoBytes: ByteArray,
    val videoContentType: String,
    val videoExtension: String,
    val thumbnailBytes: ByteArray?,
    val audioBytes: ByteArray?,
    val durationMs: Long,
)

fun interface ChallengeMediaProcessor {
    suspend fun prepare(bytes: ByteArray, fileName: String?, contentType: String): PreparedChallengeMedia
}

/** Pass-through used in tests and when ffmpeg is unavailable. */
class PassthroughChallengeMedia : ChallengeMediaProcessor {
    override suspend fun prepare(
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): PreparedChallengeMedia = PreparedChallengeMedia(
        videoBytes = bytes,
        videoContentType = contentType.ifBlank { "video/mp4" },
        videoExtension = extensionFor(contentType, fileName),
        thumbnailBytes = null,
        audioBytes = bytes.takeIf { looksLikeAudio(contentType, fileName) },
        durationMs = 0,
    )
}

/**
 * Downscales to 480p, grabs the first frame, and extracts mono 16 kHz audio
 * when `ffmpeg` / `ffprobe` are on the PATH.
 */
class FfmpegChallengeMedia : ChallengeMediaProcessor {
    private val log = LoggerFactory.getLogger(FfmpegChallengeMedia::class.java)

    override suspend fun prepare(
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): PreparedChallengeMedia = withContext(Dispatchers.IO) {
        if (!hasBinary("ffmpeg")) {
            log.info("ffmpeg not found; storing the original challenge video")
            return@withContext PassthroughChallengeMedia().prepare(bytes, fileName, contentType)
        }
        val work = File.createTempFile("challenge-", ".work").apply { delete() }
        work.mkdirs()
        try {
            val inputExt = extensionFor(contentType, fileName)
            val input = File(work, "input.$inputExt")
            input.writeBytes(bytes)
            val durationMs = probeDurationMs(input)
            if (durationMs > ChallengeLogic.MAX_DURATION_MS + 2_000) {
                error("Videos can be at most 5 minutes long")
            }
            val videoOut = File(work, "video.mp4")
            val scaled = runLogged(
                "ffmpeg",
                "-y",
                "-i",
                input.absolutePath,
                "-vf",
                "scale=-2:${ChallengeLogic.TARGET_HEIGHT}",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "28",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                "-movflags",
                "+faststart",
                "-t",
                "300",
                videoOut.absolutePath,
            )
            val videoBytes = if (scaled && videoOut.exists() && videoOut.length() > 0) {
                videoOut.readBytes()
            } else {
                bytes
            }
            val thumbOut = File(work, "thumb.jpg")
            val thumbOk = runLogged(
                "ffmpeg",
                "-y",
                "-ss",
                "1",
                "-i",
                input.absolutePath,
                "-frames:v",
                "1",
                "-q:v",
                "3",
                thumbOut.absolutePath,
            ) || runLogged(
                "ffmpeg",
                "-y",
                "-i",
                input.absolutePath,
                "-frames:v",
                "1",
                "-q:v",
                "3",
                thumbOut.absolutePath,
            )
            val audioOut = File(work, "audio.wav")
            val audioOk = runLogged(
                "ffmpeg",
                "-y",
                "-i",
                input.absolutePath,
                "-vn",
                "-ac",
                "1",
                "-ar",
                "16000",
                "-t",
                "300",
                audioOut.absolutePath,
            )
            PreparedChallengeMedia(
                videoBytes = videoBytes,
                videoContentType = "video/mp4",
                videoExtension = "mp4",
                thumbnailBytes = thumbOut.takeIf { thumbOk && it.exists() && it.length() > 0 }?.readBytes(),
                audioBytes = audioOut.takeIf { audioOk && it.exists() && it.length() > 0 }?.readBytes(),
                durationMs = durationMs,
            )
        } finally {
            work.deleteRecursively()
        }
    }

    private fun probeDurationMs(input: File): Long {
        if (!hasBinary("ffprobe")) return 0
        return try {
            val process = ProcessBuilder(
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                input.absolutePath,
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            val text = process.inputStream.bufferedReader().readText().trim()
            if (!finished) {
                process.destroyForcibly()
                return 0
            }
            val seconds = text.toDoubleOrNull() ?: return 0
            (seconds * 1000).toLong().coerceAtLeast(0)
        } catch (e: Exception) {
            log.warn("ffprobe failed: {}", e.message)
            0
        }
    }

    private fun runLogged(vararg command: String): Boolean {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            if (!finished) {
                process.destroyForcibly()
                log.warn("{} timed out", command.first())
                return false
            }
            if (process.exitValue() != 0) {
                log.warn("{} failed: {}", command.first(), output.take(400))
                return false
            }
            true
        } catch (e: Exception) {
            log.warn("{} unavailable: {}", command.first(), e.message)
            false
        }
    }

    private fun hasBinary(name: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparator).any { dir ->
            val file = File(dir, name)
            file.exists() && file.canExecute()
        }
    }
}

internal fun extensionFor(contentType: String, fileName: String?): String {
    val fromName = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf {
        it in setOf("mp4", "webm", "mov", "m4v", "ogg", "wav", "mp3", "m4a")
    }
    if (fromName != null) return fromName
    return when {
        contentType.contains("webm") -> "webm"
        contentType.contains("quicktime") || contentType.contains("mov") -> "mov"
        contentType.contains("ogg") -> "ogg"
        contentType.contains("wav") -> "wav"
        else -> "mp4"
    }
}

private fun looksLikeAudio(contentType: String, fileName: String?): Boolean {
    val name = fileName?.lowercase().orEmpty()
    return contentType.startsWith("audio/") || name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".m4a")
}
