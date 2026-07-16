package apap.oneverandah.images.storage

import apap.oneverandah.images.ImageTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Keeps a local copy of every successfully generated image, so that an image is
 * never regenerated just because a previous upload to remote storage (OSS)
 * failed.
 *
 * The generation pipeline calls [save] the moment Qwen returns valid bytes -
 * before attempting any upload - and [load] before asking Qwen for a new image.
 * When a cached image exists the pipeline simply retries the upload instead of
 * paying for another (rate-limited) generation. Once the upload finally
 * succeeds the entry is dropped via [remove].
 *
 * Files are stored under [rootDir] as `<targetType>/<targetId>.<extension>`,
 * mirroring the object key used by the [StorageService] backends.
 */
class LocalImageCache(private val rootDir: File) {
    private val log = LoggerFactory.getLogger(LocalImageCache::class.java)

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    /** Persists [image] for the given target, overwriting any previous copy. */
    suspend fun save(targetType: ImageTargetType, targetId: String, image: GeneratedImage) =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = fileFor(targetType, targetId, image.extension)
                target.parentFile?.mkdirs()
                // Remove any stale copy with a different extension before writing.
                remove(targetType, targetId)
                target.writeBytes(image.bytes)
                log.info("Cached generated image locally for {} '{}' -> {}", targetType, targetId, target.path)
            }.onFailure {
                log.warn("Failed to cache image for {} '{}': {}", targetType, targetId, it.message)
            }
            Unit
        }

    /** Returns the cached image for the target, or null when none is stored. */
    suspend fun load(targetType: ImageTargetType, targetId: String): GeneratedImage? =
        withContext(Dispatchers.IO) {
            val file = existingFile(targetType, targetId) ?: return@withContext null
            runCatching {
                val extension = file.extension.lowercase()
                GeneratedImage(
                    bytes = file.readBytes(),
                    contentType = contentTypeFor(extension),
                    extension = extension,
                )
            }.getOrElse {
                log.warn("Failed to read cached image for {} '{}': {}", targetType, targetId, it.message)
                null
            }
        }

    /** Deletes any cached copy for the target. Safe to call when none exists. */
    suspend fun remove(targetType: ImageTargetType, targetId: String) =
        withContext(Dispatchers.IO) {
            dir(targetType).listFiles { file -> file.nameWithoutExtension == targetId }
                ?.forEach { it.delete() }
            Unit
        }

    private fun dir(targetType: ImageTargetType) = File(rootDir, targetType.name.lowercase())

    private fun fileFor(targetType: ImageTargetType, targetId: String, extension: String) =
        File(dir(targetType), "$targetId.$extension")

    private fun existingFile(targetType: ImageTargetType, targetId: String): File? =
        dir(targetType).listFiles { file -> file.nameWithoutExtension == targetId }
            ?.firstOrNull { it.isFile && it.length() > 0 }

    private fun contentTypeFor(extension: String): String = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/png"
    }
}
