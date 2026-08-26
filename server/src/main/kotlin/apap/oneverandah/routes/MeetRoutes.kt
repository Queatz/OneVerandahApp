package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.meets.MeetApiError
import apap.oneverandah.meets.MeetLogic
import apap.oneverandah.meets.MeetSystem
import apap.oneverandah.meets.SendMeetPitchRequest
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
import io.ktor.server.routing.route

private const val MAX_UPLOAD_BYTES = 12L * 1024 * 1024

/**
 * Neighborhood Meet API.
 *
 * Public:
 *  - `GET  /api/v1/meets`
 *  - `GET  /api/v1/meets/{id}`
 *  - `POST /api/v1/meets` (multipart photo + name/pitch/zalo/limit)
 *  - `POST /api/v1/meets/{id}/pitches`
 *
 * Admin (Bearer JWT):
 *  - `GET    /api/v1/admin/meets`
 *  - `POST   /api/v1/admin/meets/{id}/approve`
 *  - `POST   /api/v1/admin/meets/{id}/reset`
 *  - `DELETE /api/v1/admin/meets/{id}`
 *  - `POST   /api/v1/admin/meet-pitches/{id}/complete`
 *  - `DELETE /api/v1/admin/meet-pitches/{id}`
 */
fun Route.meetRoutes(system: MeetSystem, config: AppConfig) {
    val service = system.service

    route("/api/v1/meets") {
        get {
            call.respond(service.feed(deviceHash()))
        }

        post {
            val hash = requireDeviceHash() ?: return@post
            val uploaded = receiveMeetParts()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError("A photo file is required"))
                return@post
            }
            try {
                val meet = system.createMeet(
                    name = uploaded.name,
                    pitch = uploaded.pitch,
                    zaloNumber = uploaded.zaloNumber,
                    pitchLimit = MeetLogic.parsePitchLimit(uploaded.pitchLimit),
                    deviceTokenHash = hash,
                    bytes = uploaded.bytes,
                    fileName = uploaded.fileName,
                    contentType = uploaded.contentType,
                )
                call.respond(HttpStatusCode.Created, MeetLogic.toPublicCard(meet, hash))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError(e.message ?: "Invalid meet"))
            }
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val detail = service.getPublic(id, deviceHash())
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, MeetApiError("Meet '$id' not found"))
                return@get
            }
            call.respond(detail)
        }

        post("/{id}/pitches") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<SendMeetPitchRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError("Invalid pitch payload"))
                return@post
            }
            try {
                service.sendPitch(id, body.message, body.zaloNumber, hash)
                val detail = service.getPublic(id, hash)
                    ?: error("Meet '$id' not found")
                call.respond(detail)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError(e.message ?: "Cannot send pitch"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, MeetApiError(e.message ?: "Not found"))
            }
        }
    }

    route("/api/v1/admin") {
        get("/meets") {
            if (!requireAdmin(config)) return@get
            call.respond(service.listAdmin())
        }

        post("/meets/{id}/approve") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.approve(id))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError(e.message ?: "Cannot approve"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, MeetApiError(e.message ?: "Not found"))
            }
        }

        post("/meets/{id}/reset") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.resetRemaining(id))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, MeetApiError(e.message ?: "Not found"))
            }
        }

        delete("/meets/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteMeet(id)) {
                call.respond(HttpStatusCode.NotFound, MeetApiError("Meet '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/meet-pitches/{id}/complete") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.completePitch(id))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, MeetApiError(e.message ?: "Cannot complete"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, MeetApiError(e.message ?: "Not found"))
            }
        }

        delete("/meet-pitches/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deletePitch(id)) {
                call.respond(HttpStatusCode.NotFound, MeetApiError("Pitch '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
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
        call.respond(HttpStatusCode.Unauthorized, MeetApiError("Valid device token required"))
    }
    return hash
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, MeetApiError("Admin authentication required"))
        return false
    }
    return true
}

private data class UploadedMeet(
    val name: String,
    val pitch: String,
    val zaloNumber: String,
    val pitchLimit: String,
    val bytes: ByteArray?,
    val contentType: String,
    val fileName: String?,
)

private suspend fun io.ktor.server.routing.RoutingContext.receiveMeetParts(): UploadedMeet {
    var name = ""
    var pitch = ""
    var zaloNumber = ""
    var pitchLimit = ""
    var bytes: ByteArray? = null
    var contentType = "image/jpeg"
    var fileName: String? = null
    val multipart = call.receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES)
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> when (part.name) {
                "name" -> name = part.value.trim()
                "pitch" -> pitch = part.value
                "zalo", "zaloNumber" -> zaloNumber = part.value.trim()
                "pitchLimit", "remaining" -> pitchLimit = part.value.trim()
            }
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
    return UploadedMeet(name, pitch, zaloNumber, pitchLimit, bytes, contentType, fileName)
}
