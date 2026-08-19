package apap.oneverandah.api

import apap.oneverandah.stories.GenerateStoryImageRequest
import apap.oneverandah.stories.GenerateStoryImageResponse
import apap.oneverandah.stories.StoriesResponse
import apap.oneverandah.stories.Story
import apap.oneverandah.stories.StoryApiError
import apap.oneverandah.stories.StoryCharacter
import apap.oneverandah.stories.StoryCharactersResponse
import apap.oneverandah.stories.StoryImageUploadResponse
import apap.oneverandah.stories.StoryLanguageResponse
import apap.oneverandah.stories.StoryLogic
import apap.oneverandah.stories.StoryPublishRequest
import apap.oneverandah.stories.StoryUpsertRequest
import apap.oneverandah.state.AdminAuth
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.files.File
import org.w3c.xhr.FormData

object StoryApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchStories(): List<Story> = try {
        val text = client.get("${base()}/api/v1/stories").bodyAsText()
        json.decodeFromString(StoriesResponse.serializer(), text).stories
    } catch (e: Throwable) {
        console.error("Failed to load stories: ${e.message}")
        emptyList()
    }

    suspend fun fetchStory(id: String): Story? = try {
        val response = client.get("${base()}/api/v1/stories/$id")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) return null
        json.decodeFromString(Story.serializer(), text)
    } catch (e: Throwable) {
        console.error("Failed to load story: ${e.message}")
        null
    }

    suspend fun uploadCastPhoto(file: File): String {
        val form = FormData()
        form.append("file", file)
        val init = js("({})")
        init.method = "POST"
        init.body = form
        val response = window.fetch("${base()}/api/v1/stories/cast-photos", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw StoryApiException(errorMessage(text, response.status.toInt()))
        return json.decodeFromString(StoryImageUploadResponse.serializer(), text).imageUrl
    }

    suspend fun fetchAdminStories(): List<Story> {
        val response = client.get("${base()}/api/v1/admin/stories") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StoriesResponse.serializer(), text).stories
    }

    suspend fun createStory(request: StoryUpsertRequest): Story {
        val response = client.post("${base()}/api/v1/admin/stories") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StoryUpsertRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Story.serializer(), text)
    }

    suspend fun updateStory(id: String, request: StoryUpsertRequest): Story {
        val response = client.put("${base()}/api/v1/admin/stories/$id") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StoryUpsertRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Story.serializer(), text)
    }

    suspend fun deleteStory(id: String) {
        val response = client.delete("${base()}/api/v1/admin/stories/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw StoryApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun detectLanguage(id: String): String {
        val response = client.post("${base()}/api/v1/admin/stories/$id/detect-language") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StoryLanguageResponse.serializer(), text).languageTag
    }

    suspend fun publish(id: String, languageTag: String): Story {
        val response = client.post("${base()}/api/v1/admin/stories/$id/publish") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StoryPublishRequest.serializer(), StoryPublishRequest(languageTag)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Story.serializer(), text)
    }

    suspend fun unpublish(id: String): Story {
        val response = client.post("${base()}/api/v1/admin/stories/$id/unpublish") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Story.serializer(), text)
    }

    suspend fun generateImage(request: GenerateStoryImageRequest): GenerateStoryImageResponse {
        val started = startGenerateImage(request)
        val jobId = started.jobId?.trim().orEmpty()
        if (jobId.isEmpty() || started.status == StoryLogic.IMAGE_JOB_READY) {
            return started
        }
        if (started.status == StoryLogic.IMAGE_JOB_FAILED) {
            throw StoryApiException(started.error ?: "Image generation failed")
        }
        repeat(180) {
            delay(2_000)
            val job = fetchGenerateJob(jobId)
            when (job.status) {
                StoryLogic.IMAGE_JOB_READY -> return job
                StoryLogic.IMAGE_JOB_FAILED ->
                    throw StoryApiException(job.error ?: "Image generation failed")
            }
        }
        throw StoryApiException("Image generation timed out")
    }

    private suspend fun startGenerateImage(request: GenerateStoryImageRequest): GenerateStoryImageResponse {
        val response = client.post("${base()}/api/v1/admin/stories/generate-image") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GenerateStoryImageRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess() && response.status.value != 202) {
            throw StoryApiException(errorMessage(text, response.status.value))
        }
        return json.decodeFromString(GenerateStoryImageResponse.serializer(), text)
    }

    private suspend fun fetchGenerateJob(jobId: String): GenerateStoryImageResponse {
        val response = client.get("${base()}/api/v1/admin/stories/generate-image/$jobId") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(GenerateStoryImageResponse.serializer(), text)
    }

    suspend fun uploadImage(
        file: File,
        storyId: String? = null,
        pageId: String? = null,
        asCover: Boolean = false,
    ): String {
        val token = AdminAuth.token ?: throw StoryApiException("Not signed in")
        val form = FormData()
        form.append("file", file)
        if (!storyId.isNullOrBlank()) form.append("storyId", storyId)
        if (!pageId.isNullOrBlank()) form.append("pageId", pageId)
        if (asCover) form.append("asCover", "true")
        val init = js("({})")
        init.method = "POST"
        val headers = js("({})")
        headers[HttpHeaders.Authorization] = "Bearer $token"
        init.headers = headers
        init.body = form
        val response = window.fetch("${base()}/api/v1/admin/stories/upload-image", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw StoryApiException(errorMessage(text, response.status.toInt()))
        return runCatching {
            json.decodeFromString(StoryImageUploadResponse.serializer(), text).imageUrl
        }.getOrElse {
            json.decodeFromString(Story.serializer(), text).let { story ->
                when {
                    asCover -> story.coverImageUrl.orEmpty()
                    !pageId.isNullOrBlank() -> story.pages.firstOrNull { it.id == pageId }?.photoUrl.orEmpty()
                    else -> story.coverImageUrl.orEmpty()
                }
            }
        }
    }

    suspend fun fetchCharacters(): List<StoryCharacter> {
        val response = client.get("${base()}/api/v1/admin/story-characters") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoryApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StoryCharactersResponse.serializer(), text).characters
    }

    suspend fun uploadCharacter(name: String, file: File): StoryCharacter {
        val token = AdminAuth.token ?: throw StoryApiException("Not signed in")
        val form = FormData()
        form.append("name", name)
        form.append("file", file)
        val init = js("({})")
        init.method = "POST"
        val headers = js("({})")
        headers[HttpHeaders.Authorization] = "Bearer $token"
        init.headers = headers
        init.body = form
        val response = window.fetch("${base()}/api/v1/admin/story-characters", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw StoryApiException(errorMessage(text, response.status.toInt()))
        return json.decodeFromString(StoryCharacter.serializer(), text)
    }

    suspend fun deleteCharacter(id: String) {
        val response = client.delete("${base()}/api/v1/admin/story-characters/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw StoryApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(StoryApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class StoryApiException(message: String) : RuntimeException(message)
