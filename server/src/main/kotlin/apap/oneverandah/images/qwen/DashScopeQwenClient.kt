package apap.oneverandah.images.qwen

import apap.oneverandah.config.QwenImageConfig
import apap.oneverandah.images.storage.GeneratedImage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * Real Qwen image generation via Alibaba Cloud DashScope.
 *
 * - Text-to-image for classic `wanx` / `*-t2i*` models uses the async synthesis
 *   API ([QwenImageConfig.endpoint]) with `{ input: { prompt } }`.
 * - Text-to-image for multimodal still-image models (`wan2.x-image`, including
 *   `wan2.7-image`) uses the multimodal generation protocol. Sending those
 *   models to classic `text2image/image-synthesis` yields DashScope
 *   `InvalidParameter: url error` (model/endpoint mismatch).
 * - Reference-to-image (apartment photos) uses the multimodal image model
 *   ([QwenImageConfig.r2iEndpoint] / [QwenImageConfig.r2iModel]), after first
 *   re-hosting any non-DashScope-reachable reference URLs into DashScope's
 *   temporary OSS (`oss://…`). Local storage URLs (localhost / private hosts /
 *   our own `/images/…` files) are unreadable by DashScope and were another
 *   cause of `InvalidParameter: url error`.
 */
class DashScopeQwenClient(
    private val config: QwenImageConfig,
    private val client: HttpClient,
    /** Root directory used by local image storage (object keys live under it). */
    private val localImageRoot: File? = null,
    /** Public base URL of this server; used to map `/images/{key}` back to disk. */
    private val publicBaseUrl: String? = null,
    /** Faster poll cadence so completed jobs are noticed sooner under concurrency. */
    private val pollIntervalMs: Long = 1_000,
    private val maxPollAttempts: Int = 300,
) : QwenClient {
    override val kind: String = "qwen"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cache of public/local reference URLs → DashScope temp `oss://` URLs.
     * Study scenes reuse the same character portraits many times; re-uploading
     * each portrait per panel was a major latency tax.
     *
     * Paired with [dashScopeRefLocks] so concurrent scene jobs single-flight the
     * same portrait upload instead of racing past a get-then-put cache.
     */
    private val dashScopeRefCache = ConcurrentHashMap<String, String>()
    private val dashScopeRefLocks = ConcurrentHashMap<String, Mutex>()

    private val uploadsBaseUrl: String =
        apiRootFromEndpoint(config.endpoint).ifBlank {
            apiRootFromEndpoint(config.r2iEndpoint)
        } + "/uploads"

    override suspend fun generate(
        prompt: String,
        referenceImageUrls: List<String>,
    ): GeneratedImage {
        val refs = referenceImageUrls.map { it.trim() }.filter { it.isNotEmpty() }
        return if (refs.isEmpty()) {
            // wan2.x-image (and similar multimodal still models) cannot use the
            // classic text2image contract; route them through multimodal.
            if (requiresMultimodalTextToImage(config.model, config.endpoint)) {
                generateMultimodal(
                    prompt = prompt,
                    referenceImageUrls = emptyList(),
                    model = config.model,
                    endpoint = multimodalEndpointFor(config),
                )
            } else {
                val taskId = submitTextToImage(prompt)
                val imageUrl = pollForResult(taskId, config.endpoint)
                download(imageUrl)
            }
        } else {
            // Re-host refs in parallel and reuse cached oss:// URLs across panels.
            val accessible = coroutineScope {
                refs.map { url -> async { ensureDashScopeAccessible(url) } }.map { it.await() }
            }
            generateWithReferences(prompt, accessible)
        }
    }

    /** Classic async text-to-image submit (`wanx` / `*-t2i*` style models). */
    private suspend fun submitTextToImage(prompt: String): String {
        val requestBody = buildJsonObject {
            put("model", config.model)
            put("input", buildJsonObject { put("prompt", prompt) })
            put(
                "parameters",
                buildJsonObject {
                    put("size", config.textToImageSize)
                    put("n", 1)
                    put("prompt_extend", config.promptExtend)
                },
            )
        }
        return submitAsyncTask(config.endpoint, requestBody, usesOssUrls = false)
    }

    /**
     * Multi-reference still-image generation (default: wan2.6-image multimodal).
     *
     * Falls back to the legacy VACE `image_reference` async contract when the
     * configured R2I endpoint still points at image2image/image-synthesis.
     */
    private suspend fun generateWithReferences(
        prompt: String,
        referenceImageUrls: List<String>,
    ): GeneratedImage {
        val multimodal = isMultimodalGenerationEndpoint(config.r2iEndpoint)
        if (multimodal) {
            return generateMultimodal(
                prompt = prompt,
                referenceImageUrls = referenceImageUrls,
                model = config.r2iModel,
                endpoint = config.r2iEndpoint,
            )
        }

        // Legacy VACE / image2image reference-to-image (async).
        val usesOss = referenceImageUrls.any { it.startsWith("oss://") }
        val requestBody = buildJsonObject {
            put("model", config.r2iModel)
            put(
                "input",
                buildJsonObject {
                    put("prompt", prompt)
                    put("function", "image_reference")
                    put(
                        "ref_images_url",
                        buildJsonArray { referenceImageUrls.forEach { add(it) } },
                    )
                },
            )
            put(
                "parameters",
                buildJsonObject {
                    // Apartment photos ground the scene as background references.
                    put(
                        "obj_or_bg",
                        buildJsonArray { repeat(referenceImageUrls.size) { add("bg") } },
                    )
                    put("prompt_extend", config.promptExtend)
                    put("size", config.r2iSize)
                    put("n", 1)
                },
            )
        }
        val taskId = submitAsyncTask(config.r2iEndpoint, requestBody, usesOssUrls = usesOss)
        val imageUrl = pollForResult(taskId, config.r2iEndpoint)
        return download(imageUrl)
    }

    /**
     * Multimodal still-image generation (text-only or reference-grounded).
     *
     * Shared by playground/catalog stills (`qwen-image-3.0-pro` text-to-image) and
     * phrase/cover R2I. This endpoint rejects `X-DashScope-Async` (`AccessDenied:
     * current user api does not support asynchronous calls`), so the HTTP call
     * itself is synchronous. User-facing asynchrony lives in StorySystem jobs.
     */
    private suspend fun generateMultimodal(
        prompt: String,
        referenceImageUrls: List<String>,
        model: String,
        endpoint: String,
    ): GeneratedImage {
        val usesOss = referenceImageUrls.any { it.startsWith("oss://") }
        val requestBody = buildJsonObject {
            put("model", model)
            put(
                "input",
                buildJsonObject {
                    put(
                        "messages",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "user")
                                    put(
                                        "content",
                                        buildJsonArray {
                                            add(buildJsonObject { put("text", prompt) })
                                            referenceImageUrls.forEach { url ->
                                                add(buildJsonObject { put("image", url) })
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            put(
                "parameters",
                buildJsonObject {
                    // Detailed study/cover prompts do not need LLM rewrite latency.
                    put("prompt_extend", config.promptExtend)
                    put("watermark", false)
                    put("n", 1)
                    put("enable_interleave", false)
                    put("size", config.multimodalSize)
                },
            )
        }

        val response: HttpResponse = withTransientHttpRetry {
            client.post(endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                if (usesOss) header(HEADER_OSS_RESOLVE, "enable")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), requestBody))
            }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenException("Qwen multimodal submit failed (${response.status}): $text")
        }

        // Sync multimodal response: output.choices[].message.content[].image
        extractMultimodalImageUrl(text)?.let { return download(it) }

        // Some regions / models return an async task instead.
        val taskId = json.parseToJsonElement(text).jsonObject["output"]
            ?.jsonObject?.get("task_id")?.jsonPrimitive?.contentOrNull
        if (taskId != null) {
            val imageUrl = pollForResult(taskId, endpoint)
            return download(imageUrl)
        }
        throw QwenException("Qwen multimodal returned no image url: $text")
    }

    private suspend fun submitAsyncTask(
        endpoint: String,
        requestBody: JsonObject,
        usesOssUrls: Boolean,
    ): String {
        val response: HttpResponse = withTransientHttpRetry {
            client.post(endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                header(HEADER_ASYNC, "enable")
                if (usesOssUrls) header(HEADER_OSS_RESOLVE, "enable")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), requestBody))
            }
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

    private suspend fun pollForResult(taskId: String, submitEndpoint: String): String {
        val tasksBaseUrl = apiRootFromEndpoint(submitEndpoint) + "/tasks/"
        repeat(maxPollAttempts) {
            val response: HttpResponse = withTransientHttpRetry {
                client.get(tasksBaseUrl + taskId) {
                    header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                }
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw QwenException("Qwen poll failed (${response.status}): $text")
            }
            val output = json.parseToJsonElement(text).jsonObject["output"]?.jsonObject
                ?: throw QwenException("Qwen poll returned no output: $text")
            when (output["task_status"]?.jsonPrimitive?.content) {
                "SUCCEEDED" -> {
                    extractAsyncResultUrl(output)?.let { return it }
                    throw QwenException("Qwen task succeeded but returned no url: $text")
                }

                "FAILED", "CANCELED", "UNKNOWN" ->
                    throw QwenException("Qwen task $taskId failed: $text")

                else -> delay(pollIntervalMs) // PENDING / RUNNING
            }
        }
        throw QwenException("Qwen task $taskId did not complete within timeout")
    }

    private fun extractAsyncResultUrl(output: JsonObject): String? {
        output["results"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { return it }

        // Multimodal async shape.
        return extractImageFromContent(
            output["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content"),
        )
    }

    private fun extractMultimodalImageUrl(responseText: String): String? {
        val root = json.parseToJsonElement(responseText).jsonObject
        val output = root["output"]?.jsonObject ?: return null
        val content = output["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
        return extractImageFromContent(content)
    }

    private fun extractImageFromContent(content: kotlinx.serialization.json.JsonElement?): String? {
        val array = content as? JsonArray ?: return content?.jsonPrimitive?.contentOrNull
        for (el in array) {
            val obj = el as? JsonObject ?: continue
            obj["image"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "image") {
                obj["image"]?.jsonPrimitive?.contentOrNull?.let { return it }
            }
        }
        return null
    }

    private suspend fun download(url: String): GeneratedImage {
        val response: HttpResponse = withTransientHttpRetry {
            client.get(url) { fileTransferTimeout() }
        }
        if (!response.status.isSuccess()) {
            throw QwenException("Failed to download generated image ($url): ${response.status}")
        }
        val bytes: ByteArray = response.body()
        val extension = url.substringAfterLast('.', "png").substringBefore('?').ifBlank { "png" }
        val contentType = contentTypeForExtension(extension)
        return GeneratedImage(bytes = bytes, contentType = contentType, extension = extension)
    }

    /**
     * Ensures [url] is something DashScope's servers can fetch.
     *
     * Already-hosted `oss://` temporary URLs are returned as-is. Everything else
     * is loaded (HTTP, data-URL, or local `/images/` file) and re-uploaded into
     * DashScope temporary storage so R2I never sees localhost / private URLs.
     *
     * Concurrent callers for the same source URL share one upload (study panels
     * often start in parallel with the same character portraits).
     */
    internal suspend fun ensureDashScopeAccessible(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("oss://")) return trimmed

        dashScopeRefCache[trimmed]?.let { cached ->
            log.debug("Using cached DashScope reference for {}", trimmed)
            return cached
        }

        val lock = dashScopeRefLocks.getOrPut(trimmed) { Mutex() }
        return lock.withLock {
            dashScopeRefCache[trimmed]?.let { cached ->
                log.debug("Using cached DashScope reference for {}", trimmed)
                return@withLock cached
            }

            val image = loadReferenceImage(trimmed)?.let { shrinkForDashScope(it) }
                ?: throw QwenException(
                    "Cannot load reference image for Qwen R2I: $trimmed. " +
                        "Upload failed or the file is not reachable from the server.",
                )
            val guessed = guessFileName(trimmed, image.extension)
            val fileName = guessed.substringBeforeLast('.', guessed) + "." + image.extension
            val ossUrl = uploadToDashScopeTemp(image, fileName)
            dashScopeRefCache[trimmed] = ossUrl
            log.info("Re-hosted reference image for DashScope: {} → {}", trimmed, ossUrl)
            ossUrl
        }
    }

    private suspend fun loadReferenceImage(url: String): GeneratedImage? {
        // data:image/png;base64,...
        if (url.startsWith("data:", ignoreCase = true)) {
            return decodeDataUrl(url)
        }

        // Prefer local disk for our own static image URLs (works even when the
        // public URL is not loopback-reachable from this process).
        resolveLocalFile(url)?.let { file ->
            val bytes = file.readBytes()
            val ext = file.extension.ifBlank { "png" }
            return GeneratedImage(bytes = bytes, contentType = contentTypeForExtension(ext), extension = ext)
        }

        // HTTP(S) fetch (public OSS, CDN, or this server if reachable).
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return runCatching {
                val response: HttpResponse = withTransientHttpRetry {
                    client.get(url) { fileTransferTimeout() }
                }
                if (!response.status.isSuccess()) {
                    null
                } else {
                    val bytes: ByteArray = response.body()
                    val ext = url.substringAfterLast('.', "png").substringBefore('?').ifBlank { "png" }
                    GeneratedImage(bytes = bytes, contentType = contentTypeForExtension(ext), extension = ext)
                }
            }.getOrNull()
        }

        return null
    }

    private suspend fun <T> withTransientHttpRetry(
        attempts: Int = 3,
        block: suspend () -> T,
    ): T {
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                last = e
                if (!isTransientHttpClose(e) || attempt == attempts - 1) throw e
                log.warn(
                    "Retrying Qwen HTTP after transient close (attempt {}/{}): {}",
                    attempt + 1,
                    attempts,
                    e.message,
                )
                delay(400L * (attempt + 1))
            }
        }
        throw last ?: error("Qwen HTTP retry exhausted")
    }

    /** OSS uploads/downloads can outlive the 5-minute API request budget. */
    private fun HttpRequestBuilder.fileTransferTimeout() {
        timeout {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    private fun resolveLocalFile(url: String): File? {
        val root = localImageRoot ?: return null
        val key = objectKeyFromImagesUrl(url, publicBaseUrl) ?: return null
        val file = File(root, key)
        return file.takeIf { it.isFile }
    }

    /**
     * Uploads bytes into DashScope's free 48h temporary OSS and returns `oss://…`.
     * Callers must send `X-DashScope-OssResourceResolve: enable` on model calls.
     */
    private suspend fun uploadToDashScopeTemp(image: GeneratedImage, fileName: String): String {
        val policyResponse: HttpResponse = client.get(uploadsBaseUrl) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            parameter("action", "getPolicy")
            parameter("model", config.r2iModel)
        }
        val policyText = policyResponse.bodyAsText()
        if (!policyResponse.status.isSuccess()) {
            throw QwenException("DashScope upload policy failed (${policyResponse.status}): $policyText")
        }
        val data = json.parseToJsonElement(policyText).jsonObject["data"]?.jsonObject
            ?: throw QwenException("DashScope upload policy missing data: $policyText")

        val uploadHost = data.string("upload_host")
            ?: throw QwenException("DashScope upload policy missing upload_host")
        val uploadDir = data.string("upload_dir")
            ?: throw QwenException("DashScope upload policy missing upload_dir")
        val ossAccessKeyId = data.string("oss_access_key_id")
            ?: throw QwenException("DashScope upload policy missing oss_access_key_id")
        val signature = data.string("signature")
            ?: throw QwenException("DashScope upload policy missing signature")
        val policy = data.string("policy")
            ?: throw QwenException("DashScope upload policy missing policy")
        val acl = data.string("x_oss_object_acl") ?: "private"
        val forbidOverwrite = data.string("x_oss_forbid_overwrite") ?: "true"

        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "ref.png" }
        val key = "$uploadDir/$safeName"

        val uploadResponse = withTransientHttpRetry {
            client.submitFormWithBinaryData(
                url = uploadHost,
                formData = formData {
                    append("OSSAccessKeyId", ossAccessKeyId)
                    append("Signature", signature)
                    append("policy", policy)
                    append("x-oss-object-acl", acl)
                    append("x-oss-forbid-overwrite", forbidOverwrite)
                    append("key", key)
                    append("success_action_status", "200")
                    append(
                        "file",
                        image.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, image.contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                        },
                    )
                },
            ) {
                fileTransferTimeout()
            }
        }
        if (!uploadResponse.status.isSuccess()) {
            throw QwenException(
                "DashScope temp upload failed (${uploadResponse.status}): ${uploadResponse.bodyAsText()}",
            )
        }
        return "oss://$key"
    }

    companion object {
        private val log = LoggerFactory.getLogger(DashScopeQwenClient::class.java)
        private const val HEADER_ASYNC = "X-DashScope-Async"
        private const val HEADER_OSS_RESOLVE = "X-DashScope-OssResourceResolve"
        private const val DEFAULT_MULTIMODAL_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"

        /**
         * True when [model]/[endpoint] need the multimodal still-image protocol
         * instead of classic `text2image/image-synthesis` `{prompt}` submits.
         *
         * DashScope returns `InvalidParameter: url error` when a multimodal
         * model such as `wan2.7-image` is sent to the classic endpoint.
         */
        internal fun requiresMultimodalTextToImage(model: String, endpoint: String): Boolean {
            if (isMultimodalGenerationEndpoint(endpoint)) return true
            val normalized = model.trim().lowercase()
            if (normalized.isEmpty()) return false
            // Classic text-to-image family keeps the legacy contract.
            if (normalized.contains("-t2i") || normalized.startsWith("wanx")) return false
            // wan2.x-image / wan2.x-image-pro and similar still models.
            return normalized.contains("-image")
        }

        internal fun isMultimodalGenerationEndpoint(endpoint: String): Boolean {
            val value = endpoint.lowercase()
            return value.contains("multimodal-generation") ||
                value.contains("image-generation/generation")
        }

        /**
         * Endpoint used for multimodal text-to-image when [QwenImageConfig.endpoint]
         * still points at the classic synthesis URL (common misconfiguration).
         */
        internal fun multimodalEndpointFor(config: QwenImageConfig): String {
            if (isMultimodalGenerationEndpoint(config.endpoint)) return config.endpoint
            if (isMultimodalGenerationEndpoint(config.r2iEndpoint)) return config.r2iEndpoint
            return DEFAULT_MULTIMODAL_ENDPOINT
        }

        /** `https://host/api/v1` root derived from a full service endpoint URL. */
        internal fun apiRootFromEndpoint(endpoint: String): String {
            val trimmed = endpoint.trim()
            if (trimmed.isEmpty()) return ""
            val beforeApi = trimmed.substringBefore("/api/")
            if (beforeApi != trimmed) return beforeApi.trimEnd('/') + "/api/v1"
            return trimmed.trimEnd('/').substringBeforeLast("/services", missingDelimiterValue = trimmed.trimEnd('/'))
        }

        /**
         * Maps a public or relative `/images/{objectKey}` URL back to the object key.
         * Returns null when the URL is not one of ours.
         */
        fun objectKeyFromImagesUrl(url: String, publicBaseUrl: String? = null): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null

            // Relative path.
            if (trimmed.startsWith("/images/")) {
                return trimmed.removePrefix("/images/").substringBefore('?').takeIf { it.isNotBlank() }
            }

            // Absolute URL whose path contains /images/.
            val path = runCatching { URI(trimmed).path }.getOrNull() ?: return null
            val marker = "/images/"
            val idx = path.indexOf(marker)
            if (idx < 0) return null
            val key = path.substring(idx + marker.length).trimStart('/')
            if (key.isBlank()) return null

            // If a public base is configured, only accept URLs under that host
            // when the host is present and differs — still OK to accept any
            // `/images/` path since keys are unique and local root is trusted.
            if (!publicBaseUrl.isNullOrBlank()) {
                val baseHost = runCatching { URI(publicBaseUrl).host?.lowercase() }.getOrNull()
                val urlHost = runCatching { URI(trimmed).host?.lowercase() }.getOrNull()
                if (baseHost != null && urlHost != null && baseHost != urlHost &&
                    urlHost != "localhost" && urlHost != "127.0.0.1"
                ) {
                    // Different public host — still allow path-based key extraction
                    // for mirrored deploys; local file may simply not exist.
                }
            }
            return key
        }

        internal fun contentTypeForExtension(extension: String): String =
            when (extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/png"
            }

        internal fun guessFileName(url: String, extension: String): String {
            val fromPath = runCatching {
                URI(url).path.substringAfterLast('/').takeIf { it.contains('.') }
            }.getOrNull()
            if (!fromPath.isNullOrBlank()) return fromPath
            return "ref-${UUID.randomUUID().toString().take(8)}.$extension"
        }

        /**
         * Shrinks scene/character photos before the DashScope OSS upload.
         * Full-resolution phone pictures routinely exceed the 5-minute request
         * budget on `dashscope-file-mgr.oss-cn-beijing.aliyuncs.com`.
         */
        internal fun shrinkForDashScope(
            image: GeneratedImage,
            maxEdge: Int = 1024,
            maxBytes: Int = 400_000,
        ): GeneratedImage {
            val decoded = runCatching { ImageIO.read(ByteArrayInputStream(image.bytes)) }.getOrNull()
                ?: return image
            val width = decoded.width
            val height = decoded.height
            if (width <= 0 || height <= 0) return image

            val longest = maxOf(width, height)
            if (longest <= maxEdge && image.bytes.size <= maxBytes) return image

            val scale = if (longest > maxEdge) maxEdge.toDouble() / longest else 1.0
            val targetW = (width * scale).toInt().coerceAtLeast(1)
            val targetH = (height * scale).toInt().coerceAtLeast(1)
            val rgb = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
            val graphics = rgb.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, targetW, targetH)
            graphics.drawImage(decoded, 0, 0, targetW, targetH, null)
            graphics.dispose()

            val jpeg = encodeJpeg(rgb, quality = 0.82f) ?: return image
            return GeneratedImage(bytes = jpeg, contentType = "image/jpeg", extension = "jpg")
        }

        private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray? {
            val writers = ImageIO.getImageWritersByFormatName("jpeg")
            if (!writers.hasNext()) return null
            val writer = writers.next()
            return try {
                val out = ByteArrayOutputStream()
                MemoryCacheImageOutputStream(out).use { stream ->
                    writer.output = stream
                    val param = writer.defaultWriteParam
                    if (param.canWriteCompressed()) {
                        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        param.compressionQuality = quality.coerceIn(0.1f, 1.0f)
                    }
                    writer.write(null, IIOImage(image, null, null), param)
                    stream.flush()
                }
                out.toByteArray().takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            } finally {
                writer.dispose()
            }
        }

        /** CIO reports dropped keep-alives / idle cuts as a parse EOF. */
        internal fun isTransientHttpClose(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.any { cause ->
                when (cause) {
                    is java.io.EOFException,
                    is SocketTimeoutException,
                    is java.net.SocketException,
                    is java.nio.channels.ClosedChannelException,
                    is HttpRequestTimeoutException,
                    -> true
                    else -> {
                        val message = cause.message.orEmpty()
                        message.contains("prematurely closed", ignoreCase = true) ||
                            message.contains("connection reset", ignoreCase = true) ||
                            message.contains("broken pipe", ignoreCase = true) ||
                            message.contains("connection abort", ignoreCase = true) ||
                            message.contains("Request timeout has expired", ignoreCase = true)
                    }
                }
            }

        internal fun decodeDataUrl(dataUrl: String): GeneratedImage? {
            val comma = dataUrl.indexOf(',')
            if (comma <= 5) return null
            val meta = dataUrl.substring(5, comma) // after "data:"
            val payload = dataUrl.substring(comma + 1)
            if (!meta.contains(";base64", ignoreCase = true)) return null
            val mime = meta.substringBefore(';').ifBlank { "image/png" }
            val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
            val ext = when {
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "png"
            }
            return GeneratedImage(bytes = bytes, contentType = mime, extension = ext)
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

/** Raised when the Qwen/DashScope API cannot produce an image. */
class QwenException(message: String) : RuntimeException(message)
