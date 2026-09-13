package apap.oneverandah.clubs

import apap.oneverandah.clubs.arango.ArangoClubRepository
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owns club persistence plus photo upload for admin editors.
 */
class ClubSystem(
    val service: ClubService,
    val storage: StorageService,
) {
    suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): String {
        require(bytes.isNotEmpty()) { "A photo is required" }
        val kind = clubImageKind(fileName, contentType)
        require(kind != null) { "A photo file is required" }
        return storage.upload(
            "clubs/${UUID.randomUUID()}.${kind.extension}",
            GeneratedImage(bytes = bytes, contentType = kind.contentType, extension = kind.extension),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClubSystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            httpClient: HttpClient? = null,
        ): ClubSystem {
            val repo = ArangoClubRepository.connect(config.arango, httpClient)
                ?: InMemoryClubRepository().also {
                    log.info(
                        "Club repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            return ClubSystem(service = ClubService(repo), storage = storage)
        }
    }
}

internal data class ClubImageKind(val contentType: String, val extension: String)

internal fun clubImageKind(fileName: String?, contentType: String): ClubImageKind? {
    val type = contentType.lowercase()
    val name = fileName.orEmpty().lowercase()
    return when {
        "jpeg" in type || "jpg" in type || name.endsWith(".jpg") || name.endsWith(".jpeg") ->
            ClubImageKind("image/jpeg", "jpg")
        "png" in type || name.endsWith(".png") -> ClubImageKind("image/png", "png")
        "webp" in type || name.endsWith(".webp") -> ClubImageKind("image/webp", "webp")
        "gif" in type || name.endsWith(".gif") -> ClubImageKind("image/gif", "gif")
        type.startsWith("image/") -> ClubImageKind(type, "jpg")
        else -> null
    }
}
