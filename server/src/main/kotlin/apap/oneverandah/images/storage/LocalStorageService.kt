package apap.oneverandah.images.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores images on the local filesystem under [rootDir] and serves them through
 * the app's static `/images/` route.
 *
 * This is the zero-configuration fallback used whenever Alibaba OSS is not
 * configured, so the end-to-end flow (generate → store → display) keeps working
 * out of the box in development.
 */
class LocalStorageService(
    private val rootDir: File,
    private val publicBaseUrl: String,
) : StorageService {

    override val kind: String = "local"

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override suspend fun upload(objectKey: String, image: GeneratedImage): String =
        withContext(Dispatchers.IO) {
            val target = File(rootDir, objectKey)
            target.parentFile?.mkdirs()
            target.writeBytes(image.bytes)
            "${publicBaseUrl.trimEnd('/')}/images/$objectKey"
        }
}
