package apap.oneverandah.meets

import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.meets.arango.ArangoMeetRepository
import io.ktor.client.HttpClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owns meet persistence plus photo upload for new posts.
 */
class MeetSystem(
    val service: MeetService,
    val storage: StorageService,
) {
    suspend fun createMeet(
        name: String,
        pitch: String,
        zaloNumber: String,
        pitchLimit: Int,
        deviceTokenHash: String,
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): Meet {
        require(bytes.isNotEmpty()) { "A photo is required" }
        val kind = imageKind(fileName, contentType)
        require(kind != null) { "A photo file is required" }
        val photoUrl = storage.upload(
            "meets/${UUID.randomUUID()}.${kind.extension}",
            GeneratedImage(bytes = bytes, contentType = kind.contentType, extension = kind.extension),
        )
        return service.create(
            name = name,
            pitch = pitch,
            zaloNumber = zaloNumber,
            pitchLimit = pitchLimit,
            photoUrl = photoUrl,
            deviceTokenHash = deviceTokenHash,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MeetSystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            httpClient: HttpClient? = null,
        ): MeetSystem {
            val repo = ArangoMeetRepository.connect(config.arango, httpClient)
                ?: InMemoryMeetRepository().also {
                    log.info(
                        "Meet repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            return MeetSystem(service = MeetService(repo), storage = storage)
        }
    }
}

internal data class MeetImageKind(val contentType: String, val extension: String)

internal fun imageKind(fileName: String?, contentType: String): MeetImageKind? {
    val type = contentType.lowercase()
    val name = fileName.orEmpty().lowercase()
    return when {
        "jpeg" in type || "jpg" in type || name.endsWith(".jpg") || name.endsWith(".jpeg") ->
            MeetImageKind("image/jpeg", "jpg")
        "png" in type || name.endsWith(".png") -> MeetImageKind("image/png", "png")
        "webp" in type || name.endsWith(".webp") -> MeetImageKind("image/webp", "webp")
        "gif" in type || name.endsWith(".gif") -> MeetImageKind("image/gif", "gif")
        type.startsWith("image/") -> MeetImageKind(type, "jpg")
        else -> null
    }
}
