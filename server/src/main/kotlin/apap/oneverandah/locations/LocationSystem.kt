package apap.oneverandah.locations

import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.locations.arango.ArangoLocationRepository
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owns location persistence plus photo upload for admin editors.
 */
class LocationSystem(
    val service: LocationService,
    val storage: StorageService,
) {
    suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): String {
        require(bytes.isNotEmpty()) { "A photo is required" }
        val kind = locationImageKind(fileName, contentType)
        require(kind != null) { "A photo file is required" }
        return storage.upload(
            "locations/${UUID.randomUUID()}.${kind.extension}",
            GeneratedImage(bytes = bytes, contentType = kind.contentType, extension = kind.extension),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocationSystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            httpClient: HttpClient? = null,
        ): LocationSystem {
            val repo = ArangoLocationRepository.connect(config.arango, httpClient)
                ?: InMemoryLocationRepository().also {
                    log.info(
                        "Location repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            return LocationSystem(service = LocationService(repo), storage = storage)
        }
    }
}

internal data class LocationImageKind(val contentType: String, val extension: String)

internal fun locationImageKind(fileName: String?, contentType: String): LocationImageKind? {
    val type = contentType.lowercase()
    val name = fileName.orEmpty().lowercase()
    return when {
        "jpeg" in type || "jpg" in type || name.endsWith(".jpg") || name.endsWith(".jpeg") ->
            LocationImageKind("image/jpeg", "jpg")
        "png" in type || name.endsWith(".png") -> LocationImageKind("image/png", "png")
        "webp" in type || name.endsWith(".webp") -> LocationImageKind("image/webp", "webp")
        "gif" in type || name.endsWith(".gif") -> LocationImageKind("image/gif", "gif")
        type.startsWith("image/") -> LocationImageKind(type, "jpg")
        else -> null
    }
}
