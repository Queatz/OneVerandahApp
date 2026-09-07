package apap.oneverandah.images.qwen

import apap.oneverandah.config.QwenImageConfig
import apap.oneverandah.images.storage.GeneratedImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashScopeQwenClientTest {

    @Test
    fun objectKeyFromImagesUrl_parsesRelativeAndAbsolute() {
        assertEquals(
            "reference/pool.jpg",
            DashScopeQwenClient.objectKeyFromImagesUrl("/images/reference/pool.jpg"),
        )
        assertEquals(
            "reference/pool.jpg",
            DashScopeQwenClient.objectKeyFromImagesUrl(
                "https://api.oneverandah.app:8443/images/reference/pool.jpg",
            ),
        )
        assertEquals(
            "reference/pool.jpg",
            DashScopeQwenClient.objectKeyFromImagesUrl(
                "http://localhost:7071/images/reference/pool.jpg?cache=1",
            ),
        )
    }

    @Test
    fun requiresMultimodalTextToImage_detectsWanImageModels() {
        assertTrue(
            DashScopeQwenClient.requiresMultimodalTextToImage(
                model = "qwen-image-3.0-pro",
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
            ),
        )
        assertTrue(
            DashScopeQwenClient.requiresMultimodalTextToImage(
                model = "wan2.7-image",
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
            ),
        )
        assertTrue(
            DashScopeQwenClient.requiresMultimodalTextToImage(
                model = "wan2.7-image-pro",
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
            ),
        )
        assertTrue(
            !DashScopeQwenClient.requiresMultimodalTextToImage(
                model = "wan2.6-t2i",
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
            ),
        )
        assertTrue(
            !DashScopeQwenClient.requiresMultimodalTextToImage(
                model = "wanx-v1",
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
            ),
        )
    }

    @Test
    fun textOnlyWan27Image_usesMultimodalEndpointNotClassicSynthesis() = runBlocking {
        var sawMultimodal = false
        var sawClassicSynthesis = false
        var requestBody = ""
        var requestUrl = ""

        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("multimodal-generation") -> {
                    sawMultimodal = true
                    requestUrl = url
                    requestBody = readBodyText(request.body)
                    respond(
                        content = """
                            {
                              "output": {
                                "choices": [
                                  {
                                    "finish_reason": "stop",
                                    "message": {
                                      "role": "assistant",
                                      "content": [
                                        {
                                          "image": "https://dashscope-result.example.test/comic.png",
                                          "type": "image"
                                        }
                                      ]
                                    }
                                  }
                                ]
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                url.contains("text2image/image-synthesis") -> {
                    sawClassicSynthesis = true
                    respond(
                        content = """{"code":"InvalidParameter","message":"url error, please check url！"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                url.contains("dashscope-result.example.test") -> {
                    respond(
                        content = ByteReadChannel(byteArrayOf(7, 7, 7)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                }

                else -> error("Unexpected request: $url")
            }
        }

        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                model = "wan2.7-image",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
            ),
            client = client,
        )

        val image = qwen.generate("Expressive comic-style single panel of a learner saying xin chao")

        assertTrue(sawMultimodal, "expected multimodal generation call")
        assertTrue(!sawClassicSynthesis, "must not hit classic text2image synthesis for wan2.7-image")
        assertTrue(requestUrl.contains("multimodal-generation"), requestUrl)
        assertTrue(requestBody.contains("\"model\":\"wan2.7-image\"") || requestBody.contains("wan2.7-image"), requestBody)
        assertTrue(requestBody.contains("messages"), requestBody)
        assertTrue(requestBody.contains("xin chao"), requestBody)
        assertTrue(!requestBody.contains("\"image\""), "text-only request must not send reference images: $requestBody")
        assertTrue(image.bytes.contentEquals(byteArrayOf(7, 7, 7)))
        client.close()
    }

    @Test
    fun transientHttpClose_detectsPrematureConnectionClose() {
        val closed = java.io.EOFException(
            "Failed to parse HTTP response: the server prematurely closed the connection",
        )
        assertTrue(DashScopeQwenClient.isTransientHttpClose(closed))
        assertTrue(DashScopeQwenClient.isTransientHttpClose(RuntimeException("wrap", closed)))
        assertTrue(!DashScopeQwenClient.isTransientHttpClose(QwenException("invalid prompt")))
    }

    @Test
    fun multimodalGenerate_retriesWhenDashScopeClosesTheConnection() = runBlocking {
        val attempts = AtomicInteger()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("multimodal-generation") -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw java.io.EOFException(
                            "Failed to parse HTTP response: the server prematurely closed the connection",
                        )
                    }
                    assertNull(
                        request.headers["X-DashScope-Async"],
                        "multimodal-generation does not support DashScope async",
                    )
                    respond(
                        content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/cover.png","type":"image"}]}}]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> {
                    respond(
                        content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                }
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                model = "wan2.7-image",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
            ),
            client = client,
            pollIntervalMs = 1,
        )
        val image = qwen.generate("Lan by the pool at dusk")
        assertEquals(2, attempts.get())
        assertTrue(image.bytes.contentEquals(byteArrayOf(4, 4, 4)))
        client.close()
    }

    @Test
    fun decodeDataUrl_readsBase64Png() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
        val image = DashScopeQwenClient.decodeDataUrl(dataUrl)
        assertNotNull(image)
        assertTrue(image.bytes.contentEquals(bytes))
        assertEquals("png", image.extension)
    }

    @Test
    fun shrinkForDashScope_downscalesLargePngToJpeg() {
        val original = pngBytes(width = 2000, height = 1600)
        val shrunk = DashScopeQwenClient.shrinkForDashScope(
            GeneratedImage(
                bytes = original,
                contentType = "image/png",
                extension = "png",
            ),
        )
        assertEquals("jpg", shrunk.extension)
        assertEquals("image/jpeg", shrunk.contentType)
        assertTrue(shrunk.bytes.size < original.size, "expected ${shrunk.bytes.size} < ${original.size}")
        val decoded = ImageIO.read(ByteArrayInputStream(shrunk.bytes))
        assertNotNull(decoded)
        assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
    }

    @Test
    fun transientHttpClose_detectsRequestTimeout() {
        val timeout = io.ktor.client.plugins.HttpRequestTimeoutException(
            "https://dashscope-file-mgr.oss-cn-beijing.aliyuncs.com",
            300_000,
        )
        assertTrue(DashScopeQwenClient.isTransientHttpClose(timeout))
        assertTrue(
            DashScopeQwenClient.isTransientHttpClose(
                RuntimeException("Request timeout has expired [url=https://dashscope-file-mgr.oss-cn-beijing.aliyuncs.com, request_timeout=300000 ms]"),
            ),
        )
    }

    @Test
    fun generateWithReference_uploadsShrunkJpegWithoutRequestTimeout() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-shrink-").toFile()
        try {
            val key = "reference/huge-scene.png"
            val file = root.resolve(key)
            file.parentFile.mkdirs()
            val original = pngBytes(width = 1800, height = 1400)
            file.writeBytes(original)

            var uploadedBytes = 0
            var uploadTimeout: Long? = -1L

            val engine = MockEngine { request ->
                val url = request.url.toString()
                when {
                    url.contains("/api/v1/uploads") -> respond(
                        content = """
                            {
                              "data": {
                                "upload_host": "https://dashscope-file-mgr.oss-cn-beijing.aliyuncs.com",
                                "upload_dir": "dashscope-instant/test-dir",
                                "oss_access_key_id": "AKIATEST",
                                "signature": "sig",
                                "policy": "pol",
                                "x_oss_object_acl": "private",
                                "x_oss_forbid_overwrite": "true"
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    url.contains("dashscope-file-mgr.oss-cn-beijing.aliyuncs.com") -> {
                        uploadedBytes = readBodyBytes(request.body).size
                        uploadTimeout = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                        respondOk("OK")
                    }
                    url.contains("multimodal-generation") -> respond(
                        content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/out.png","type":"image"}]}}]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    url.contains("dashscope-result.example.test") -> respond(
                        content = ByteReadChannel(byteArrayOf(3, 3, 3)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                    else -> error("Unexpected request: $url")
                }
            }
            val client = HttpClient(engine) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 300_000
                }
            }
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                    apiKey = "test-key",
                    model = "wan2.7-image",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "wan2.7-image",
                ),
                client = client,
                localImageRoot = root,
                publicBaseUrl = "https://api.oneverandah.app",
            )
            val image = qwen.generate(
                prompt = "Lan by the pool at dusk",
                referenceImageUrls = listOf("https://api.oneverandah.app/images/$key"),
            )
            assertTrue(image.bytes.contentEquals(byteArrayOf(3, 3, 3)))
            assertTrue(uploadedBytes in 1 until original.size, "uploaded $uploadedBytes bytes from ${original.size}")
            assertEquals(HttpTimeoutConfig.INFINITE_TIMEOUT_MS, uploadTimeout)
            client.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ensureDashScopeAccessible_rehostsLocalImageFile() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-ref-").toFile()
        try {
            val key = "reference/apartment-pool.png"
            val file = root.resolve(key)
            file.parentFile.mkdirs()
            val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A) // pretend PNG header
            file.writeBytes(payload)

            var sawPolicy = false
            var sawUpload = false
            var sawGenerate = false
            var generateBody = ""

            val engine = MockEngine { request ->
                val url = request.url.toString()
                when {
                    url.contains("/api/v1/uploads") -> {
                        sawPolicy = true
                        respond(
                            content = """
                                {
                                  "data": {
                                    "upload_host": "https://upload.example.test/oss",
                                    "upload_dir": "dashscope-instant/test-dir",
                                    "oss_access_key_id": "AKIATEST",
                                    "signature": "sig",
                                    "policy": "pol",
                                    "x_oss_object_acl": "private",
                                    "x_oss_forbid_overwrite": "true"
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    url.contains("upload.example.test") -> {
                        sawUpload = true
                        respondOk("OK")
                    }

                    url.contains("multimodal-generation") -> {
                        sawGenerate = true
                        generateBody = readBodyText(request.body)
                        // Ensure oss resolve header is present for oss:// refs
                        assertEquals("enable", request.headers["X-DashScope-OssResourceResolve"])
                        respond(
                            content = """
                                {
                                  "output": {
                                    "choices": [
                                      {
                                        "finish_reason": "stop",
                                        "message": {
                                          "role": "assistant",
                                          "content": [
                                            {
                                              "image": "https://dashscope-result.example.test/out.png",
                                              "type": "image"
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    url.contains("dashscope-result.example.test") -> {
                        respond(
                            content = ByteReadChannel(byteArrayOf(9, 9, 9)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "image/png"),
                        )
                    }

                    else -> error("Unexpected request: $url")
                }
            }

            val client = HttpClient(engine)
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                    apiKey = "test-key",
                    model = "wan2.7-image",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "wan2.6-image",
                ),
                client = client,
                localImageRoot = root,
                publicBaseUrl = "https://api.oneverandah.app:8443",
            )

            // Localhost URL that DashScope cannot fetch — must be re-hosted.
            val localUrl = "http://localhost:7071/images/$key"
            val image = qwen.generate(
                prompt = "Golden hour at the infinity pool",
                referenceImageUrls = listOf(localUrl),
            )

            assertTrue(sawPolicy, "expected DashScope upload policy request")
            assertTrue(sawUpload, "expected temp OSS upload")
            assertTrue(sawGenerate, "expected multimodal generate call")
            assertTrue(generateBody.contains("oss://dashscope-instant/test-dir/"), generateBody)
            assertTrue(generateBody.contains("Golden hour at the infinity pool"), generateBody)
            assertTrue(image.bytes.contentEquals(byteArrayOf(9, 9, 9)))
            client.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun multimodalRequest_disablesPromptExtendAndUsesConfiguredSize() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("multimodal-generation") -> {
                    requestBody = readBodyText(request.body)
                    respond(
                        content = """
                            {
                              "output": {
                                "choices": [
                                  {
                                    "finish_reason": "stop",
                                    "message": {
                                      "role": "assistant",
                                      "content": [
                                        {
                                          "image": "https://dashscope-result.example.test/comic.png",
                                          "type": "image"
                                        }
                                      ]
                                    }
                                  }
                                ]
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> {
                    respond(
                        content = ByteReadChannel(byteArrayOf(3, 3, 3)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                }
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                model = "wan2.7-image",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
                promptExtend = false,
                multimodalSize = "768*768",
            ),
            client = client,
        )
        qwen.generate("Detailed study panel prompt already complete")
        assertTrue(requestBody.contains("\"prompt_extend\":false"), requestBody)
        assertTrue(requestBody.contains("\"size\":\"768*768\""), requestBody)
        client.close()
    }

    @Test
    fun flashTextToImage_usesClassicAsyncWithConfiguredSize() = runBlocking {
        var sawSubmit = false
        var requestBody = ""
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("text2image/image-synthesis") -> {
                    sawSubmit = true
                    requestBody = readBodyText(request.body)
                    respond(
                        content = """{"output":{"task_id":"task-flash-1","task_status":"PENDING"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("/tasks/task-flash-1") -> {
                    respond(
                        content = """
                            {
                              "output": {
                                "task_status": "SUCCEEDED",
                                "results": [ { "url": "https://dashscope-result.example.test/flash.png" } ]
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> {
                    respond(
                        content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                }
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                // Classic *-t2i* path still works when explicitly configured.
                model = "wan2.2-t2i-flash",
                textToImageSize = "512*512",
                promptExtend = false,
            ),
            client = client,
        )
        val image = qwen.generate("Quick portrait of Lan")
        assertTrue(sawSubmit)
        assertTrue(requestBody.contains("wan2.2-t2i-flash"), requestBody)
        assertTrue(requestBody.contains("\"size\":\"512*512\""), requestBody)
        assertTrue(requestBody.contains("\"prompt_extend\":false"), requestBody)
        assertTrue(image.bytes.contentEquals(byteArrayOf(4, 4, 4)))
        client.close()
    }

    @Test
    fun referenceRehost_isCachedAcrossGenerations() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-cache-").toFile()
        try {
            val key = "study/story/lan.png"
            val file = root.resolve(key)
            file.parentFile.mkdirs()
            file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

            var policyCount = 0
            var uploadCount = 0
            var generateCount = 0

            val engine = MockEngine { request ->
                val url = request.url.toString()
                when {
                    url.contains("/api/v1/uploads") -> {
                        policyCount += 1
                        respond(
                            content = """
                                {
                                  "data": {
                                    "upload_host": "https://upload.example.test/oss",
                                    "upload_dir": "dashscope-instant/cache-dir",
                                    "oss_access_key_id": "AKIATEST",
                                    "signature": "sig",
                                    "policy": "pol",
                                    "x_oss_object_acl": "private",
                                    "x_oss_forbid_overwrite": "true"
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    url.contains("upload.example.test") -> {
                        uploadCount += 1
                        respondOk("OK")
                    }
                    url.contains("multimodal-generation") -> {
                        generateCount += 1
                        respond(
                            content = """
                                {
                                  "output": {
                                    "choices": [
                                      {
                                        "finish_reason": "stop",
                                        "message": {
                                          "role": "assistant",
                                          "content": [
                                            {
                                              "image": "https://dashscope-result.example.test/out.png",
                                              "type": "image"
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    url.contains("dashscope-result.example.test") -> {
                        respond(
                            content = ByteReadChannel(byteArrayOf(5, 5, 5)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "image/png"),
                        )
                    }
                    else -> error("Unexpected request: $url")
                }
            }

            val client = HttpClient(engine)
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                    apiKey = "test-key",
                    model = "wan2.2-t2i-flash",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "wan2.7-image",
                ),
                client = client,
                localImageRoot = root,
                publicBaseUrl = "https://api.oneverandah.app:8443",
            )
            val localUrl = "https://api.oneverandah.app:8443/images/$key"
            qwen.generate("panel one", referenceImageUrls = listOf(localUrl))
            qwen.generate("panel two", referenceImageUrls = listOf(localUrl))
            assertEquals(1, policyCount, "policy should only run once for cached ref")
            assertEquals(1, uploadCount, "upload should only run once for cached ref")
            assertEquals(2, generateCount)
            client.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun referenceRehost_singleFlightsConcurrentGenerations() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-singleflight-").toFile()
        try {
            val key = "study/story/main.png"
            val file = root.resolve(key)
            file.parentFile.mkdirs()
            file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

            val policyCount = AtomicInteger(0)
            val uploadCount = AtomicInteger(0)
            val generateCount = AtomicInteger(0)

            val engine = MockEngine { request ->
                val url = request.url.toString()
                when {
                    url.contains("/api/v1/uploads") -> {
                        policyCount.incrementAndGet()
                        // Stretch the race window so concurrent callers would
                        // double-upload without single-flight locking.
                        delay(40)
                        respond(
                            content = """
                                {
                                  "data": {
                                    "upload_host": "https://upload.example.test/oss",
                                    "upload_dir": "dashscope-instant/singleflight-dir",
                                    "oss_access_key_id": "AKIATEST",
                                    "signature": "sig",
                                    "policy": "pol",
                                    "x_oss_object_acl": "private",
                                    "x_oss_forbid_overwrite": "true"
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    url.contains("upload.example.test") -> {
                        uploadCount.incrementAndGet()
                        delay(40)
                        respondOk("OK")
                    }
                    url.contains("multimodal-generation") -> {
                        generateCount.incrementAndGet()
                        respond(
                            content = """
                                {
                                  "output": {
                                    "choices": [
                                      {
                                        "finish_reason": "stop",
                                        "message": {
                                          "role": "assistant",
                                          "content": [
                                            {
                                              "image": "https://dashscope-result.example.test/out.png",
                                              "type": "image"
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    url.contains("dashscope-result.example.test") -> {
                        respond(
                            content = ByteReadChannel(byteArrayOf(6, 6, 6)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "image/png"),
                        )
                    }
                    else -> error("Unexpected request: $url")
                }
            }

            val client = HttpClient(engine)
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                    apiKey = "test-key",
                    model = "wan2.2-t2i-flash",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "wan2.7-image",
                ),
                client = client,
                localImageRoot = root,
                publicBaseUrl = "https://api.oneverandah.app",
            )
            val localUrl = "https://api.oneverandah.app/images/$key"
            coroutineScope {
                (1..4).map { i ->
                    async {
                        qwen.generate("panel $i", referenceImageUrls = listOf(localUrl))
                    }
                }.awaitAll()
            }
            assertEquals(1, policyCount.get(), "concurrent panels must single-flight policy")
            assertEquals(1, uploadCount.get(), "concurrent panels must single-flight rehost upload")
            assertEquals(4, generateCount.get())
            client.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun readBodyText(body: OutgoingContent): String =
        readBodyBytes(body).toString(Charsets.UTF_8)

    private suspend fun readBodyBytes(body: OutgoingContent): ByteArray =
        when (body) {
            is TextContent -> body.text.toByteArray()
            is OutgoingContent.ByteArrayContent -> body.bytes()
            is OutgoingContent.ReadChannelContent -> {
                val channel = body.readFrom()
                channel.readRemaining().readByteArray()
            }
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel(autoFlush = true)
                body.writeTo(channel)
                channel.flushAndClose()
                channel.readRemaining().readByteArray()
            }
            else -> body.toString().toByteArray()
        }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        var seed = 0x9E3779B9.toInt()
        for (y in 0 until height) {
            for (x in 0 until width) {
                seed = seed * 1664525 + 1013904223
                image.setRGB(x, y, seed and 0x00FFFFFF)
            }
        }
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, "png", out)) { "failed to encode test PNG" }
        return out.toByteArray()
    }
}
