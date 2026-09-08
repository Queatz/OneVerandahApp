package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.locations.CommitLocationFundsRequest
import apap.oneverandah.locations.ContactLocationRequest
import apap.oneverandah.locations.LocationApiError
import apap.oneverandah.locations.LocationPhotoUploadResponse
import apap.oneverandah.locations.LocationSystem
import apap.oneverandah.locations.LocationUpsertRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private const val MAX_UPLOAD_BYTES = 12L * 1024 * 1024

/**
 * Nearby Locations API.
 *
 * Public:
 *  - `GET  /api/v1/locations`
 *  - `GET  /api/v1/locations/{id}`
 *  - `POST /api/v1/locations/{id}/like`
 *  - `POST /api/v1/locations/{id}/commitments`
 *  - `POST /api/v1/locations/{id}/contacts`
 *
 * Admin (Bearer JWT):
 *  - `GET    /api/v1/admin/locations`
 *  - `POST   /api/v1/admin/locations`
 *  - `PUT    /api/v1/admin/locations/{id}`
 *  - `DELETE /api/v1/admin/locations/{id}`
 *  - `POST   /api/v1/admin/locations/photos`
 */
fun Route.locationRoutes(system: LocationSystem, config: AppConfig) {
    val service = system.service

    route("/api/v1/locations") {
        get {
            call.respond(service.feed(deviceHash()))
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val detail = service.getPublic(id, deviceHash())
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, LocationApiError("Location '$id' not found"))
                return@get
            }
            call.respond(detail)
        }

        post("/{id}/like") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.toggleLike(id, hash))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Cannot like"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, LocationApiError(e.message ?: "Not found"))
            }
        }

        post("/{id}/commitments") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<CommitLocationFundsRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError("Invalid commitment payload"))
                return@post
            }
            try {
                service.commitFunds(id, body.amountVnd, body.message, body.zaloNumber, hash)
                val detail = service.getPublic(id, hash) ?: error("Location '$id' not found")
                call.respond(detail)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Cannot commit funds"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, LocationApiError(e.message ?: "Not found"))
            }
        }

        post("/{id}/contacts") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<ContactLocationRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError("Invalid contact payload"))
                return@post
            }
            try {
                service.contactHost(id, body.message, body.zaloNumber, hash)
                val detail = service.getPublic(id, hash) ?: error("Location '$id' not found")
                call.respond(detail)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Cannot contact"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, LocationApiError(e.message ?: "Not found"))
            }
        }
    }

    route("/api/v1/admin") {
        get("/locations") {
            if (!requireAdmin(config)) return@get
            call.respond(service.listAdmin())
        }

        post("/locations") {
            if (!requireAdmin(config)) return@post
            val body = runCatching { call.receive<LocationUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError("Invalid location payload"))
                return@post
            }
            try {
                call.respond(HttpStatusCode.Created, service.upsert(body.copy(id = null)))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Invalid location"))
            }
        }

        put("/locations/{id}") {
            if (!requireAdmin(config)) return@put
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<LocationUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError("Invalid location payload"))
                return@put
            }
            try {
                call.respond(service.upsert(body.copy(id = id)))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Invalid location"))
            }
        }

        delete("/locations/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.delete(id)) {
                call.respond(HttpStatusCode.NotFound, LocationApiError("Location '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/locations/photos") {
            if (!requireAdmin(config)) return@post
            val uploaded = receiveLocationPhoto()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError("A photo file is required"))
                return@post
            }
            try {
                val url = system.uploadPhoto(
                    bytes = uploaded.bytes,
                    fileName = uploaded.fileName,
                    contentType = uploaded.contentType,
                )
                call.respond(LocationPhotoUploadResponse(url))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, LocationApiError(e.message ?: "Invalid photo"))
            }
        }
    }
}

private fun io.ktor.server.routing.RoutingContext.deviceHash(): String? {
    val token = call.request.header(DEVICE_TOKEN_HEADER)?.trim().orEmpty()
    return token.takeIf { it.isNotEmpty() }?.let(DeviceService::hashToken)
}

private suspend fun io.ktor.server.routing.RoutingContext.requireDeviceHash(): String? {
    val hash = deviceHash()
    if (hash == null) {
        call.respond(HttpStatusCode.Unauthorized, LocationApiError("Valid device token required"))
    }
    return hash
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, LocationApiError("Admin authentication required"))
        return false
    }
    return true
}

private data class UploadedLocationPhoto(
    val bytes: ByteArray?,
    val contentType: String,
    val fileName: String?,
)

private suspend fun io.ktor.server.routing.RoutingContext.receiveLocationPhoto(): UploadedLocationPhoto {
    var bytes: ByteArray? = null
    var contentType = "image/jpeg"
    var fileName: String? = null
    val multipart = call.receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES)
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                fileName = part.originalFileName
                contentType = part.contentType?.toString() ?: "image/jpeg"
                @Suppress("DEPRECATION")
                bytes = part.streamProvider().readBytes()
            }
            else -> Unit
        }
        part.dispose()
    }
    return UploadedLocationPhoto(bytes, contentType, fileName)
}
