package apap.oneverandah.routes

import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.ApiError
import apap.oneverandah.images.GenerateRequest
import apap.oneverandah.images.GenerateResponse
import apap.oneverandah.images.ImageStatus
import apap.oneverandah.images.ImageStatusResponse
import apap.oneverandah.images.ImageSystem
import apap.oneverandah.images.ImagesResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.http.content.staticFiles
import kotlinx.serialization.Serializable
import java.io.File

/** Health-check payload returned by `GET /health`. */
@Serializable
data class HealthResponse(
    val status: String,
    val environment: String,
    val repository: String,
    val storage: String,
    val generator: String,
)

/**
 * Registers the image system's HTTP API and the local static image route.
 *
 * Endpoints (per the PRD):
 *  - `GET  /health`
 *  - `GET  /api/v1/images`
 *  - `POST /api/v1/images/generate`
 *  - `GET  /api/v1/images/status/{targetId}`
 *  - `GET  /images/{...}` — serves locally stored images (local storage mode)
 */
fun Route.imageRoutes(system: ImageSystem, config: AppConfig) {
    val repository = system.repository
    val queue = system.queue

    get("/health") {
        call.respond(
            HealthResponse(
                status = "UP",
                environment = config.env.name,
                repository = system.repositoryKind,
                storage = system.storageKind,
                generator = system.generatorKind,
            ),
        )
    }

    route("/api/v1/images") {
        // 4.1 Get all images
        get {
            val images = repository.findAll().map { it.toDto() }
            call.respond(ImagesResponse(images))
        }

        // 4.2 Trigger generation (internal)
        post("/generate") {
            val request = runCatching { call.receive<GenerateRequest>() }.getOrNull()
            if (request == null || request.imageId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("A non-blank 'imageId' is required"))
                return@post
            }
            val entity = repository.findByImageId(request.imageId)
            if (entity == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Image '${request.imageId}' not found"))
                return@post
            }
            queue.requeue(request.imageId)
            call.respond(HttpStatusCode.Accepted, GenerateResponse(request.imageId, ImageStatus.PENDING))
        }

        // 4.3 Get image status by target id
        get("/status/{targetId}") {
            val targetId = call.parameters["targetId"]
            if (targetId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("targetId is required"))
                return@get
            }
            val entity = repository.findAll().firstOrNull { it.targetId == targetId }
            if (entity == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("No image for target '$targetId'"))
                return@get
            }
            call.respond(
                ImageStatusResponse(
                    targetId = entity.targetId,
                    targetType = entity.targetType,
                    status = entity.status,
                    imageUrl = entity.imageUrl,
                ),
            )
        }
    }

    // Serve locally-stored images so the local storage fallback is reachable
    // by the frontend without any external CDN.
    val localDir = File(config.localStorageDir).apply { mkdirs() }
    staticFiles("/images", localDir)
}
