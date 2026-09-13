package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.clubs.ClubActionResponse
import apap.oneverandah.clubs.ClubApiError
import apap.oneverandah.clubs.ClubPhotoUploadResponse
import apap.oneverandah.clubs.ClubSystem
import apap.oneverandah.clubs.ClubUpsertRequest
import apap.oneverandah.clubs.CreateClubRequestPayload
import apap.oneverandah.clubs.JoinClubRequest
import apap.oneverandah.config.AppConfig
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
 * Clubs API.
 *
 * Public:
 *  - `GET  /api/v1/clubs`
 *  - `GET  /api/v1/clubs/{id}`
 *  - `POST /api/v1/clubs/requests`
 *  - `POST /api/v1/clubs/{id}/joins`
 *
 * Admin (Bearer JWT):
 *  - `GET    /api/v1/admin/clubs`
 *  - `POST   /api/v1/admin/clubs`
 *  - `PUT    /api/v1/admin/clubs/{id}`
 *  - `DELETE /api/v1/admin/clubs/{id}`
 *  - `POST   /api/v1/admin/clubs/photos`
 *  - `DELETE /api/v1/admin/clubs/requests/{id}`
 *  - `DELETE /api/v1/admin/clubs/joins/{id}`
 */
fun Route.clubRoutes(system: ClubSystem, config: AppConfig) {
    val service = system.service

    route("/api/v1/clubs") {
        get {
            call.respond(service.feed())
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val detail = service.getPublic(id)
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, ClubApiError("Club '$id' not found"))
                return@get
            }
            call.respond(detail)
        }

        post("/requests") {
            val hash = requireClubDeviceHash() ?: return@post
            val body = runCatching { call.receive<CreateClubRequestPayload>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError("Invalid club request payload"))
                return@post
            }
            try {
                service.requestCreate(
                    name = body.name,
                    tagline = body.tagline,
                    description = body.description,
                    hostName = body.hostName,
                    hostZalo = body.hostZalo,
                    deviceTokenHash = hash,
                )
                call.respond(HttpStatusCode.Created, ClubActionResponse())
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError(e.message ?: "Cannot create club request"))
            }
        }

        post("/{id}/joins") {
            val hash = requireClubDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<JoinClubRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError("Invalid join payload"))
                return@post
            }
            try {
                service.join(id, body.name, body.zaloNumber, body.answers, body.message, hash)
                val detail = service.getPublic(id) ?: error("Club '$id' not found")
                call.respond(detail)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError(e.message ?: "Cannot join"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, ClubApiError(e.message ?: "Not found"))
            }
        }
    }

    route("/api/v1/admin") {
        get("/clubs") {
            if (!requireClubAdmin(config)) return@get
            call.respond(service.listAdmin())
        }

        post("/clubs") {
            if (!requireClubAdmin(config)) return@post
            val body = runCatching { call.receive<ClubUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError("Invalid club payload"))
                return@post
            }
            try {
                call.respond(HttpStatusCode.Created, service.upsert(body.copy(id = null)))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError(e.message ?: "Invalid club"))
            }
        }

        put("/clubs/{id}") {
            if (!requireClubAdmin(config)) return@put
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<ClubUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError("Invalid club payload"))
                return@put
            }
            try {
                call.respond(service.upsert(body.copy(id = id)))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError(e.message ?: "Invalid club"))
            }
        }

        post("/clubs/photos") {
            if (!requireClubAdmin(config)) return@post
            val uploaded = receiveClubPhoto()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError("A photo file is required"))
                return@post
            }
            try {
                val url = system.uploadPhoto(
                    bytes = uploaded.bytes,
                    fileName = uploaded.fileName,
                    contentType = uploaded.contentType,
                )
                call.respond(ClubPhotoUploadResponse(url))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ClubApiError(e.message ?: "Invalid photo"))
            }
        }

        delete("/clubs/requests/{id}") {
            if (!requireClubAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteCreateRequest(id)) {
                call.respond(HttpStatusCode.NotFound, ClubApiError("Club request '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/clubs/joins/{id}") {
            if (!requireClubAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteJoin(id)) {
                call.respond(HttpStatusCode.NotFound, ClubApiError("Club join '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/clubs/{id}") {
            if (!requireClubAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.delete(id)) {
                call.respond(HttpStatusCode.NotFound, ClubApiError("Club '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun io.ktor.server.routing.RoutingContext.clubDeviceHash(): String? {
    val token = call.request.header(DEVICE_TOKEN_HEADER)?.trim().orEmpty()
    return token.takeIf { it.isNotEmpty() }?.let(DeviceService::hashToken)
}

private suspend fun io.ktor.server.routing.RoutingContext.requireClubDeviceHash(): String? {
    val hash = clubDeviceHash()
    if (hash == null) {
        call.respond(HttpStatusCode.Unauthorized, ClubApiError("Valid device token required"))
    }
    return hash
}

private suspend fun io.ktor.server.routing.RoutingContext.requireClubAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, ClubApiError("Admin authentication required"))
        return false
    }
    return true
}

private data class UploadedClubPhoto(
    val bytes: ByteArray?,
    val contentType: String,
    val fileName: String?,
)

private suspend fun io.ktor.server.routing.RoutingContext.receiveClubPhoto(): UploadedClubPhoto {
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
    return UploadedClubPhoto(bytes, contentType, fileName)
}
