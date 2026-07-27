package apap.oneverandah.routes

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.api.ActivateClonedVoiceResponse
import apap.oneverandah.api.ClonedVoice
import apap.oneverandah.chat.ClonedVoicesService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.phrases.AdminSignInRequest
import apap.oneverandah.phrases.AdminSignInResponse
import apap.oneverandah.phrases.GeneratePhraseImageRequest
import apap.oneverandah.phrases.Phrase
import apap.oneverandah.phrases.PhraseApiError
import apap.oneverandah.phrases.PhraseDefaults
import apap.oneverandah.phrases.PhraseReactRequest
import apap.oneverandah.phrases.PhraseReactions
import apap.oneverandah.phrases.PhraseSystem
import apap.oneverandah.phrases.PhraseUpsertRequest
import apap.oneverandah.phrases.PhrasesCoverResponse
import apap.oneverandah.phrases.PhrasesResponse
import apap.oneverandah.phrases.ReferencePhoto
import apap.oneverandah.phrases.ReferencePhotosResponse
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
import java.util.UUID

/**
 * Phrasebook + admin portal API.
 *
 * Public:
 *  - `GET  /api/v1/phrases`
 *  - `GET  /api/v1/phrases/cover`
 *  - `POST /api/v1/phrases/{id}/react`
 *
 * Admin (Bearer JWT from sign-in):
 *  - `POST   /api/v1/admin/signin`
 *  - `GET    /api/v1/admin/phrases`
 *  - `POST   /api/v1/admin/phrases`
 *  - `PUT    /api/v1/admin/phrases/{id}`
 *  - `DELETE /api/v1/admin/phrases/{id}`
 *  - `POST   /api/v1/admin/phrases/generate-image`
 *  - `GET    /api/v1/admin/reference-photos`
 *  - `POST   /api/v1/admin/reference-photos` (multipart: name + file)
 *  - `DELETE /api/v1/admin/reference-photos/{id}`
 *  - `GET    /api/v1/admin/voices`
 *  - `POST   /api/v1/admin/voices` (multipart: name + languageTag + file)
 *  - `POST   /api/v1/admin/voices/{id}/activate`
 *  - `DELETE /api/v1/admin/voices/{id}`
 */
fun Route.phraseRoutes(
    system: PhraseSystem,
    config: AppConfig,
    clonedVoicesService: ClonedVoicesService? = null,
) {
    val phrases = system.phrases
    val photos = system.referencePhotos
    val storage = system.storage

    route("/api/v1/phrases") {
        get {
            val approved = phrases.findAll()
                .filter { it.approved && it.id != PhraseSystem.COVER_PHRASE_ID }
            call.respond(
                PhrasesResponse(
                    phrases = approved,
                    coverImageUrl = system.coverImageUrl(),
                ),
            )
        }

        get("/cover") {
            call.respond(PhrasesCoverResponse(imageUrl = system.coverImageUrl()))
        }

        post("/{id}/react") {
            val id = call.parameters["id"].orEmpty()
            if (id.isBlank() || id == PhraseSystem.COVER_PHRASE_ID) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("id is required"))
                return@post
            }
            val body = runCatching { call.receive<PhraseReactRequest>() }.getOrNull()
            val emoji = body?.emoji?.trim().orEmpty()
            if (emoji !in PhraseReactions.allowed) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    PhraseApiError("emoji must be one of ${PhraseReactions.allowed.joinToString()}"),
                )
                return@post
            }
            val existing = phrases.findById(id)
            if (existing == null || !existing.approved) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError("Phrase '$id' not found"))
                return@post
            }
            val nextCount = (existing.reactions[emoji] ?: 0) + 1
            val updated = existing.copy(
                reactions = existing.reactions + (emoji to nextCount),
                updatedAt = System.currentTimeMillis(),
            )
            phrases.save(updated)
            call.respond(updated)
        }
    }

    route("/api/v1/admin") {
        post("/signin") {
            val body = runCatching { call.receive<AdminSignInRequest>() }.getOrNull()
            val password = body?.password.orEmpty()
            if (password.isBlank() || password != config.adminPassword) {
                call.respond(HttpStatusCode.Unauthorized, PhraseApiError("Invalid password"))
                return@post
            }
            val token = AdminJwt.issue(config.adminJwtSecret)
            call.respond(AdminSignInResponse(token = token))
        }

        get("/phrases") {
            if (!requireAdmin(config)) return@get
            phrases.seedIfEmpty()
            val list = phrases.findAll().filter { it.id != PhraseSystem.COVER_PHRASE_ID }
            call.respond(
                PhrasesResponse(
                    phrases = list,
                    coverImageUrl = system.coverImageUrl(),
                ),
            )
        }

        post("/phrases") {
            if (!requireAdmin(config)) return@post
            val body = runCatching { call.receive<PhraseUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Invalid phrase payload"))
                return@post
            }
            val now = System.currentTimeMillis()
            val id = body.id?.trim()?.takeIf { it.isNotEmpty() && it != PhraseSystem.COVER_PHRASE_ID }
                ?: "emp-${UUID.randomUUID()}"
            val phrase = Phrase(
                id = id,
                locationKey = body.locationKey.trim(),
                location = body.location,
                icon = body.icon.ifBlank { "auto_awesome" },
                spoken = body.spoken,
                verse = body.verse,
                rawAsk = body.rawAsk,
                instructions = body.instructions.takeUnless { it.en.isBlank() } ?: PhraseDefaults.instructions,
                imageUrl = body.imageUrl,
                approved = body.approved,
                createdAt = now,
                updatedAt = now,
            )
            if (phrase.locationKey.isBlank() || phrase.spoken.en.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("locationKey and spoken.en are required"))
                return@post
            }
            phrases.save(phrase)
            call.respond(HttpStatusCode.Created, phrase)
        }

        put("/phrases/{id}") {
            if (!requireAdmin(config)) return@put
            val id = call.parameters["id"].orEmpty()
            if (id == PhraseSystem.COVER_PHRASE_ID) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Cannot edit internal cover record"))
                return@put
            }
            val existing = phrases.findById(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError("Phrase '$id' not found"))
                return@put
            }
            val body = runCatching { call.receive<PhraseUpsertRequest>() }.getOrNull()
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Invalid phrase payload"))
                return@put
            }
            val updated = existing.copy(
                locationKey = body.locationKey.trim().ifBlank { existing.locationKey },
                location = body.location,
                icon = body.icon.ifBlank { existing.icon },
                spoken = body.spoken,
                verse = body.verse,
                rawAsk = body.rawAsk,
                instructions = body.instructions.takeUnless { it.en.isBlank() } ?: existing.instructions,
                imageUrl = body.imageUrl,
                approved = body.approved,
                updatedAt = System.currentTimeMillis(),
            )
            phrases.save(updated)
            call.respond(updated)
        }

        delete("/phrases/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (id == PhraseSystem.COVER_PHRASE_ID) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Cannot delete internal cover record"))
                return@delete
            }
            if (!phrases.delete(id)) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError("Phrase '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/phrases/generate-image") {
            if (!requireAdmin(config)) return@post
            val body = runCatching { call.receive<GeneratePhraseImageRequest>() }.getOrNull()
            if (body == null || body.prompt.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("prompt is required"))
                return@post
            }
            try {
                val result = system.generateImage(body)
                call.respond(result)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError(e.message ?: "Invalid request"))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError(e.message ?: "Not found"))
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.BadGateway,
                    PhraseApiError(e.message ?: "Image generation failed"),
                )
            }
        }

        get("/reference-photos") {
            if (!requireAdmin(config)) return@get
            call.respond(ReferencePhotosResponse(photos.findAll()))
        }

        post("/reference-photos") {
            if (!requireAdmin(config)) return@post
            var name = ""
            var bytes: ByteArray? = null
            var contentType = "image/jpeg"
            var originalFileName: String? = null

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "name") name = part.value.trim()
                    }
                    is PartData.FileItem -> {
                        originalFileName = part.originalFileName
                        contentType = part.contentType?.toString() ?: "image/jpeg"
                        @Suppress("DEPRECATION")
                        bytes = part.streamProvider().readBytes()
                    }
                    else -> Unit
                }
                part.dispose()
            }

            if (name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("A non-blank 'name' is required"))
                return@post
            }
            val imageBytes = bytes
            if (imageBytes == null || imageBytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("An image file is required"))
                return@post
            }

            val extension = extensionFor(contentType, originalFileName)
            val safeName = name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "reference" }
            val id = "ref-$safeName-${UUID.randomUUID().toString().take(8)}"
            val objectKey = "reference/$safeName-$id.$extension"
            val url = storage.upload(
                objectKey,
                GeneratedImage(bytes = imageBytes, contentType = contentType, extension = extension),
            )
            val photo = ReferencePhoto(
                id = id,
                name = name,
                imageUrl = url,
                createdAt = System.currentTimeMillis(),
            )
            photos.save(photo)
            call.respond(HttpStatusCode.Created, photo)
        }

        delete("/reference-photos/{id}") {
            if (!requireAdmin(config)) return@delete
            val id = call.parameters["id"].orEmpty()
            if (!photos.delete(id)) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError("Reference photo '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/voices") {
            if (!requireAdmin(config)) return@get
            val voices = clonedVoicesService
            if (voices == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, PhraseApiError("Cloned voices are unavailable"))
                return@get
            }
            call.respond(voices.list())
        }

        post("/voices") {
            if (!requireAdmin(config)) return@post
            val voices = clonedVoicesService
            if (voices == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, PhraseApiError("Cloned voices are unavailable"))
                return@post
            }
            var name = ""
            var languageTag = ClonedVoice.FALLBACK_LANGUAGE_TAG
            var bytes: ByteArray? = null
            var contentType = "audio/wav"
            var originalFileName: String? = null

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "name" -> name = part.value.trim()
                        "languageTag" -> languageTag = part.value.trim()
                    }
                    is PartData.FileItem -> {
                        originalFileName = part.originalFileName
                        contentType = part.contentType?.toString() ?: "audio/wav"
                        @Suppress("DEPRECATION")
                        bytes = part.streamProvider().readBytes()
                    }
                    else -> Unit
                }
                part.dispose()
            }

            if (name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("A non-blank 'name' is required"))
                return@post
            }
            val audioBytes = bytes
            if (audioBytes == null || audioBytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("An audio file is required"))
                return@post
            }
            if (ClonedVoice.normalizeLanguageTag(languageTag) == null) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("languageTag is required"))
                return@post
            }

            try {
                val voice = voices.upload(
                    rawName = name,
                    rawLanguageTag = languageTag,
                    bytes = audioBytes,
                    contentType = contentType,
                    originalFileName = originalFileName,
                )
                call.respond(HttpStatusCode.Created, voice)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError(e.message ?: "Invalid voice payload"))
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.BadGateway,
                    PhraseApiError(e.message ?: "Voice enrollment failed"),
                )
            }
        }

        post("/voices/{id}/activate") {
            if (!requireAdmin(config)) return@post
            val voices = clonedVoicesService
            if (voices == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, PhraseApiError("Cloned voices are unavailable"))
                return@post
            }
            val id = call.parameters["id"].orEmpty()
            if (id.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Voice id is required"))
                return@post
            }
            try {
                val voice = voices.activate(id)
                call.respond(ActivateClonedVoiceResponse(voice = voice))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError(e.message ?: "Voice not found"))
            }
        }

        delete("/voices/{id}") {
            if (!requireAdmin(config)) return@delete
            val voices = clonedVoicesService
            if (voices == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, PhraseApiError("Cloned voices are unavailable"))
                return@delete
            }
            val id = call.parameters["id"].orEmpty()
            if (id.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, PhraseApiError("Voice id is required"))
                return@delete
            }
            if (!voices.delete(id)) {
                call.respond(HttpStatusCode.NotFound, PhraseApiError("Voice '$id' not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(config: AppConfig): Boolean {
    val header = call.request.header(HttpHeaders.Authorization)
    if (!AdminJwt.verify(header, config.adminJwtSecret)) {
        call.respond(HttpStatusCode.Unauthorized, PhraseApiError("Admin authentication required"))
        return false
    }
    return true
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
