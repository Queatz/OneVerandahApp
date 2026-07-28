package apap.oneverandah.images.qwen

import apap.oneverandah.config.QwenImageConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
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
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun decodeDataUrl_readsBase64Png() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
        val image = DashScopeQwenClient.decodeDataUrl(dataUrl)
        assertNotNull(image)
        assertTrue(image.bytes.contentEquals(bytes))
        assertEquals("png", image.extension)
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
        when (body) {
            is TextContent -> body.text
            is OutgoingContent.ByteArrayContent -> body.bytes().toString(Charsets.UTF_8)
            is OutgoingContent.ReadChannelContent -> {
                val channel = body.readFrom()
                channel.readRemaining().readByteArray().toString(Charsets.UTF_8)
            }
            is OutgoingContent.WriteChannelContent -> {
                val channel = ByteChannel(autoFlush = true)
                body.writeTo(channel)
                channel.flushAndClose()
                channel.readRemaining().readByteArray().toString(Charsets.UTF_8)
            }
            else -> body.toString()
        }
}
