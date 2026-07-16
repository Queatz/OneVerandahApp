package apap.oneverandah.images.qwen

import apap.oneverandah.config.QwenConfig
import apap.oneverandah.images.storage.GeneratedImage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Real Qwen image generation via Alibaba Cloud DashScope's asynchronous
 * text-to-image API.
 *
 * The flow is: submit the task (async) → poll the task until it succeeds →
 * download the produced image bytes. Errors are surfaced as exceptions so the
 * caller ([apap.oneverandah.images.ImageGenerationService]) can apply its
 * retry/backoff policy.
 */
class DashScopeQwenClient(
    private val config: QwenConfig,
    private val client: HttpClient,
    private val pollIntervalMs: Long = 2_000,
    private val maxPollAttempts: Int = 90,
    private val errorLog: QwenErrorLog? = null,
) : QwenClient {

    override val kind: String = "qwen"

    private val json = Json { ignoreUnknownKeys = true }

    private val tasksBaseUrl: String =
        config.endpoint.substringBefore("/api/").trimEnd('/') + "/api/v1/tasks/"

    override suspend fun generate(prompt: String): GeneratedImage {
        try {
            val taskId = submitTask(prompt)
            val imageUrl = pollForResult(taskId)
            return download(imageUrl)
        } catch (e: CancellationException) {
            // Not a Qwen failure (e.g. app shutdown); don't pollute the error log.
            throw e
        } catch (e: Exception) {
            errorLog?.record("prompt=\"$prompt\" model=${config.model} endpoint=${config.endpoint}: ${e.message}")
            throw e
        }
    }

    private suspend fun submitTask(prompt: String): String {
        val requestBody = buildJsonObject {
            put("model", config.model)
            put("input", buildJsonObject { put("prompt", prompt) })
            put("parameters", buildJsonObject {
                put("size", "1024*1024")
                put("n", 1)
            })
        }

        val response: HttpResponse = client.post(config.endpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            header("X-DashScope-Async", "enable")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), requestBody))
        }

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenException("Qwen submit failed (${response.status}): $text")
        }
        val output = json.parseToJsonElement(text).jsonObject["output"]?.jsonObject
            ?: throw QwenException("Qwen submit returned no output: $text")
        return output["task_id"]?.jsonPrimitive?.content
            ?: throw QwenException("Qwen submit returned no task_id: $text")
    }

    private suspend fun pollForResult(taskId: String): String {
        repeat(maxPollAttempts) {
            val response: HttpResponse = client.get(tasksBaseUrl + taskId) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw QwenException("Qwen poll failed (${response.status}): $text")
            }
            val output = json.parseToJsonElement(text).jsonObject["output"]?.jsonObject
                ?: throw QwenException("Qwen poll returned no output: $text")
            when (output["task_status"]?.jsonPrimitive?.content) {
                "SUCCEEDED" -> {
                    val url = output["results"]?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    return url ?: throw QwenException("Qwen task succeeded but returned no url: $text")
                }

                "FAILED", "CANCELED", "UNKNOWN" ->
                    throw QwenException("Qwen task $taskId failed: $text")

                else -> delay(pollIntervalMs) // PENDING / RUNNING
            }
        }
        throw QwenException("Qwen task $taskId did not complete within timeout")
    }

    private suspend fun download(url: String): GeneratedImage {
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) {
            throw QwenException("Failed to download generated image ($url): ${response.status}")
        }
        val bytes: ByteArray = response.body()
        val extension = url.substringAfterLast('.', "png").substringBefore('?').ifBlank { "png" }
        val contentType = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        return GeneratedImage(bytes = bytes, contentType = contentType, extension = extension)
    }
}

/** Raised when the Qwen/DashScope API cannot produce an image. */
class QwenException(message: String) : RuntimeException(message)
