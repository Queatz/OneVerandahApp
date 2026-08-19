package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.config.AppConfig
import apap.oneverandah.stories.GenerateStoryImageRequest
import apap.oneverandah.stories.StoriesResponse
import apap.oneverandah.stories.StoryApiError
import apap.oneverandah.stories.StoryCharacterRenameRequest
import apap.oneverandah.stories.StoryCharactersResponse
import apap.oneverandah.stories.StoryImageUploadResponse
import apap.oneverandah.stories.StoryLanguageResponse
import apap.oneverandah.stories.StoryPublishRequest
import apap.oneverandah.stories.StorySystem
import apap.oneverandah.stories.StoryUpsertRequest
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

/**
 * Mini Stories API.
 *
 * Public:
 *  - `GET  /api/v1/stories`
 *  - `GET  /api/v1/stories/{id}` (increments open count)
 *  - `GET  /api/v1/stories/{id}/pages/{pageId}/study?languageTag=`
 *  - `POST /api/v1/stories/cast-photos` (multipart file for featured submissions)
 *
 * Admin (Bearer JWT):
 *  - `GET    /api/v1/admin/stories`
 *  - `POST   /api/v1/admin/stories`
 *  - `GET    /api/v1/admin/stories/{id}`
 *  - `PUT    /api/v1/admin/stories/{id}`
 *  - `DELETE /api/v1/admin/stories/{id}`
 *  - `POST   /api/v1/admin/stories/{id}/detect-language`
 *  - `POST   /api/v1/admin/stories/{id}/publish` (starts a background job)
 *  - `GET    /api/v1/admin/stories/publish/{jobId}`
 *  - `POST   /api/v1/admin/stories/{id}/unpublish`
 *  - `POST   /api/v1/admin/stories/generate-image` (starts a background job)
 *  - `GET    /api/v1/admin/stories/generate-image/{jobId}`
 *  - `POST   /api/v1/admin/stories/upload-image`
 *  - `GET    /api/v1/admin/story-characters`
 *  - `POST   /api/v1/admin/story-characters`
 *  - `DELETE /api/v1/admin/story-characters/{id}`
 */
fun Route.storyRoutes(system: StorySystem, config: AppConfig) {
    val service = system.service

    route("/api/v1/stories") {
        get {
            call.respond(StoriesResponse(stories = service.listPublic()))
        }

        post("/cast-photos") {
            val uploaded = receiveImageParts()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("An image file is required"))
                return@post
            }
            val url = system.uploadImage(
                bytes = uploaded.bytes,
                contentType = uploaded.contentType,
                extension = extensionFor(uploaded.contentType, uploaded.fileName),
            )
            call.respond(HttpStatusCode.Created, StoryImageUploadResponse(url))
        }

        get("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val story = service.getPublic(id)
            if (story == null) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Story '$id' not found"))
                return@get
            }
            call.respond(story)
        }

        get("/{id}/pages/{pageId}/study") {
            val id = call.parameters["id"].orEmpty()
            val pageId = call.parameters["pageId"].orEmpty()
            val languageTag = call.request.queryParameters["languageTag"]
            try {
                call.respond(service.studyPage(id, pageId, languageTag.orEmpty()))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Cannot study this page"))
            }
        }
    }

    route("/api/v1/admin") {
        get("/stories") {
            if (!requireAdmin(config)) return@get
            call.respond(StoriesResponse(stories = service.listAdmin()))
        }

        post("/stories") {
            if (!requireAdmin(config)) return@post
            val body = runCatching { call.receive<StoryUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("Invalid story payload"))
                return@post
            }
            try {
                call.respond(HttpStatusCode.Created, service.create(body))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Invalid story"))
            }
        }

        get("/stories/{id}") {
            if (!requireAdmin(config)) return@get
            val id = call.parameters["id"].orEmpty()
            val story = service.getAdmin(id)
            if (story == null) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Story '$id' not found"))
                return@get
            }
            call.respond(story)
        }

        put("/stories/{id}") {
            if (!requireAdmin(config)) return@put
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<StoryUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("Invalid story payload"))
                return@put
            }
            try {
                call.respond(service.update(id, body))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Invalid story"))
            }
        }

        delete("/stories/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.delete(id)) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Story '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/stories/{id}/detect-language") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(StoryLanguageResponse(service.detectLanguage(id)))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            }
        }

        post("/stories/{id}/publish") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<StoryPublishRequest>() }.getOrNull()
            if (body == null || body.languageTag.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("languageTag is required"))
                return@post
            }
            try {
                call.respond(HttpStatusCode.Accepted, system.enqueuePublish(id, body.languageTag))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Cannot publish"))
            }
        }

        get("/stories/publish/{jobId}") {
            if (!requireAdmin(config)) return@get
            val jobId = call.parameters["jobId"].orEmpty()
            val job = system.publishJob(jobId)
            if (job == null) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Publish job '$jobId' not found"))
                return@get
            }
            call.respond(job)
        }

        post("/stories/{id}/unpublish") {
            if (!requireAdmin(config)) return@post
            val id = call.parameters["id"].orEmpty()
            try {
                call.respond(service.unpublish(id))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            }
        }

        post("/stories/generate-image") {
            if (!requireAdmin(config)) return@post
            val body = runCatching { call.receive<GenerateStoryImageRequest>() }.getOrNull()
            if (body == null || body.prompt.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("prompt is required"))
                return@post
            }
            try {
                call.respond(HttpStatusCode.Accepted, system.enqueueGenerate(body))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Invalid request"))
            }
        }

        get("/stories/generate-image/{jobId}") {
            if (!requireAdmin(config)) return@get
            val jobId = call.parameters["jobId"].orEmpty()
            val job = system.generateJob(jobId)
            if (job == null) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Image job '$jobId' not found"))
                return@get
            }
            call.respond(job)
        }

        post("/stories/upload-image") {
            if (!requireAdmin(config)) return@post
            val uploaded = receiveImageParts()
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("An image file is required"))
                return@post
            }
            val url = system.uploadImage(
                bytes = uploaded.bytes,
                contentType = uploaded.contentType,
                extension = extensionFor(uploaded.contentType, uploaded.fileName),
            )
            val storyId = uploaded.storyId
            val story = if (!storyId.isNullOrBlank()) {
                service.attachImage(storyId, url, uploaded.pageId, uploaded.asCover)
            } else {
                null
            }
            if (story != null) {
                call.respond(story)
            } else {
                call.respond(StoryImageUploadResponse(url))
            }
        }

        get("/story-characters") {
            if (!requireAdmin(config)) return@get
            call.respond(StoryCharactersResponse(service.listCharacters()))
        }

        post("/story-characters") {
            if (!requireAdmin(config)) return@post
            val uploaded = receiveImageParts()
            if (uploaded.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("A character name is required"))
                return@post
            }
            if (uploaded.bytes == null || uploaded.bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("A character photo is required"))
                return@post
            }
            val url = system.uploadImage(
                bytes = uploaded.bytes,
                contentType = uploaded.contentType,
                extension = extensionFor(uploaded.contentType, uploaded.fileName),
            )
            try {
                call.respond(HttpStatusCode.Created, service.addCharacter(uploaded.name, url))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Invalid character"))
            }
        }

        put("/story-characters/{id}") {
            if (!requireAdmin(config)) return@put
            val id = call.parameters["id"].orEmpty()
            val body = runCatching { call.receive<StoryCharacterRenameRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError("Invalid character payload"))
                return@put
            }
            try {
                call.respond(service.renameCharacter(id, body.name))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, StoryApiError(e.message ?: "Invalid character"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, StoryApiError(e.message ?: "Not found"))
            }
        }

        delete("/story-characters/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!service.deleteCharacter(id)) {
                call.respond(HttpStatusCode.NotFound, StoryApiError("Character '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, StoryApiError("Admin authentication required"))
        return false
    }
    return true
}

private data class UploadedImage(
    val name: String,
    val bytes: ByteArray?,
    val contentType: String,
    val fileName: String?,
    val storyId: String?,
    val pageId: String?,
    val asCover: Boolean,
)

private suspend fun io.ktor.server.routing.RoutingContext.receiveImageParts(): UploadedImage {
    var name = ""
    var bytes: ByteArray? = null
    var contentType = "image/jpeg"
    var fileName: String? = null
    var storyId: String? = null
    var pageId: String? = null
    var asCover = false
    val multipart = call.receiveMultipart()
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> when (part.name) {
                "name" -> name = part.value.trim()
                "storyId" -> storyId = part.value.trim().takeIf { it.isNotEmpty() }
                "pageId" -> pageId = part.value.trim().takeIf { it.isNotEmpty() }
                "asCover" -> asCover = part.value.equals("true", ignoreCase = true)
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
    return UploadedImage(name, bytes, contentType, fileName, storyId, pageId, asCover)
}

private fun extensionFor(contentType: String, fileName: String?): String {
    val fromName = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf {
        it in setOf("jpg", "jpeg", "png", "webp", "gif")
    }
    if (fromName != null) return if (fromName == "jpeg") "jpg" else fromName
    return when {
        contentType.contains("png") -> "png"
        contentType.contains("webp") -> "webp"
        contentType.contains("gif") -> "gif"
        else -> "jpg"
    }
}
