package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.challenges.ChallengeApiError
import apap.oneverandah.challenges.ChallengeRenameRequest
import apap.oneverandah.challenges.ChallengeSystem
import apap.oneverandah.challenges.ChallengeVoteRequest
import apap.oneverandah.chat.DeviceService
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
import io.ktor.server.routing.route

private const val MAX_UPLOAD_BYTES = 200L * 1024 * 1024

/**
 * Neighborhood Challenges API.
 *
 * Public:
 *  - `GET  /api/v1/challenges`
 *  - `GET  /api/v1/challenges/{id}`
 *  - `POST /api/v1/challenges` (multipart video + name)
 *  - `POST /api/v1/challenges/{id}/submissions`
 *  - `GET  /api/v1/challenges/jobs/{jobId}`
 *  - `POST /api/v1/challenges/{id}/submissions/{sid}/vote`
 *
 * Admin (Bearer JWT):
 *  - `GET    /api/v1/admin/challenges`
 *  - `POST   /api/v1/admin/challenges/{id}/rename`
 *  - `POST   /api/v1/admin/submissions/{id}/publish`
 *  - `DELETE /api/v1/admin/challenges/{id}`
 *  - `DELETE /api/v1/admin/submissions/{id}`
 */
fun Route.challengeRoutes(system: ChallengeSystem, config: AppConfig) {
    val service = system.service

    route("/api/v1/challenges") {
        get {
            call.respond(service.feed(deviceHash()))
        }

        get("/jobs/{jobId}") {
            val jobId = call.parameters["jobId"].orEmpty()
            val job = system.job(jobId)
            if (job == null) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError("Upload job '$jobId' not found"))
                return@get
            }
            call.respond(job)
        }

        post {
            val hash = requireDeviceHash() ?: return@post
            val uploaded = receiveVideoParts()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError("A video file is required"))
                return@post
            }
            try {
                val job = system.enqueueUpload(
                    challengeId = null,
                    challengerName = uploaded.name,
                    deviceTokenHash = hash,
                    bytes = uploaded.bytes,
                    fileName = uploaded.fileName,
                    contentType = uploaded.contentType,
                )
                call.respond(HttpStatusCode.Accepted, job)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError(e.message ?: "Invalid upload"))
            }
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val detail = service.getPublic(id, deviceHash())
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError("Challenge '$id' not found"))
                return@get
            }
            call.respond(detail)
        }

        post("/{id}/submissions") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val uploaded = receiveVideoParts()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError("A video file is required"))
                return@post
            }
            try {
                val job = system.enqueueUpload(
                    challengeId = id,
                    challengerName = uploaded.name,
                    deviceTokenHash = hash,
                    bytes = uploaded.bytes,
                    fileName = uploaded.fileName,
                    contentType = uploaded.contentType,
                )
                call.respond(HttpStatusCode.Accepted, job)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError(e.message ?: "Invalid upload"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError(e.message ?: "Not found"))
            }
        }

        post("/{id}/submissions/{sid}/vote") {
            val hash = requireDeviceHash() ?: return@post
            val id = call.parameters["id"].orEmpty()
            val sid = call.parameters["sid"].orEmpty()
            val body = runCatching { call.receive<ChallengeVoteRequest>() }.getOrNull()
            try {
                call.respond(service.vote(id, sid, hash, body?.emoji))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError(e.message ?: "Cannot vote"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError(e.message ?: "Not found"))
            }
        }
    }

    route("/api/v1/admin") {
        get("/challenges") {
            if (!requireAdmin(config)) return@get
            call.respond(service.listAdmin())
        }

        post("/challenges/{id}/rename") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<ChallengeRenameRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError("Invalid rename payload"))
                return@post
            }
            try {
                call.respond(service.rename(id, body.name))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError(e.message ?: "Invalid name"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError(e.message ?: "Not found"))
            }
        }

        delete("/challenges/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteChallenge(id)) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError("Challenge '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/submissions/{id}/publish") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.publish(id))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ChallengeApiError(e.message ?: "Cannot publish"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError(e.message ?: "Not found"))
            }
        }

        delete("/submissions/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteSubmission(id)) {
                call.respond(HttpStatusCode.NotFound, ChallengeApiError("Submission '$id' not found"))
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
        call.respond(HttpStatusCode.Unauthorized, ChallengeApiError("Valid device token required"))
    }
    return hash
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, ChallengeApiError("Admin authentication required"))
        return false
    }
    return true
}

private data class UploadedVideo(
    val name: String,
    val bytes: ByteArray?,
    val contentType: String,
    val fileName: String?,
)

private suspend fun io.ktor.server.routing.RoutingContext.receiveVideoParts(): UploadedVideo {
    var name = ""
    var bytes: ByteArray? = null
    var contentType = "video/mp4"
    var fileName: String? = null
    val multipart = call.receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES)
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "name") name = part.value.trim()
            is PartData.FileItem -> {
                fileName = part.originalFileName
                contentType = part.contentType?.toString() ?: "video/mp4"
                @Suppress("DEPRECATION")
                bytes = part.streamProvider().readBytes()
            }
            else -> Unit
        }
        part.dispose()
    }
    return UploadedVideo(name, bytes, contentType, fileName)
}
