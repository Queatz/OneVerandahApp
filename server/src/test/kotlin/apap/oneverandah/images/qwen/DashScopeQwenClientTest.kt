package apap.oneverandah.images.qwen

import apap.oneverandah.chat.StudyBuildFailureReason
import apap.oneverandah.config.QwenImageConfig
import apap.oneverandah.images.storage.GeneratedImage
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
                model = "qwen-image-3.0",
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
    fun `generateMask uploads portrait bytes directly to temporary storage`() = runBlocking {
        var uploadBody = ""
        var generationBody = ""
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/uploads") -> respond(
                    content = """
                        {"data":{"upload_host":"https://upload.example.test/oss","upload_dir":"dashscope-instant/mask-test","oss_access_key_id":"AKIATEST","signature":"sig","policy":"pol"}}
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                url.contains("upload.example.test") -> {
                    uploadBody = readBodyText(request.body)
                    respondOk("OK")
                }
                url.contains("multimodal-generation") -> {
                    generationBody = readBodyText(request.body)
                    respond(
                        content = """
                            {"output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":[{"image":"https://dashscope-result.example.test/mask.png","type":"image"}]}}]}}
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(8, 8, 8)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
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
                inlineReferenceImages = false,
            ),
            client = client,
        )

        val result = qwen.generateMask(
            GeneratedImage(byteArrayOf(1, 2, 3), "image/png", "png"),
            "Create a binary mask.",
        )

        assertTrue(uploadBody.contains("talk-mask-"), "mask image should use the dedicated temporary upload path")
        assertTrue(generationBody.contains("oss://dashscope-instant/mask-test/talk-mask-"))
        assertTrue(!generationBody.contains("data:image/"), "mask reference should not be serialized as a data URL")
        assertTrue(result.bytes.contentEquals(byteArrayOf(8, 8, 8)))
        client.close()
    }

    @Test
    fun `generate then mask reuses DashScope result url without temporary upload`() = runBlocking {
        val portraitUrl = "https://dashscope-result.example.test/portrait.png?signature=secret"
        val generationBodies = mutableListOf<String>()
        var generationCount = 0
        var sawUpload = false
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/uploads") || url.contains("upload.example.test") -> {
                    sawUpload = true
                    error("Generated DashScope images should not need temporary uploads")
                }
                url.contains("multimodal-generation") -> {
                    generationBodies += readBodyText(request.body)
                    generationCount += 1
                    val imageUrl = if (generationCount == 1) portraitUrl else
                        "https://dashscope-result.example.test/mask.png?signature=mask-secret"
                    respond(
                        content = """{"output":{"choices":[{"message":{"content":[{"image":"$imageUrl","type":"image"}]}}]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(8, 8, 8)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
            ),
            client = client,
        )

        val portrait = qwen.generate("Generate a portrait.", emptyList())
        qwen.generateMask(portrait, "Create a binary mask.")

        assertTrue(!sawUpload)
        assertEquals(2, generationBodies.size)
        assertTrue(generationBodies.last().contains(portraitUrl))
        val portraitDescription = portrait.toString()
        assertTrue(!portraitDescription.contains("signature=secret"))
        assertTrue(!portraitDescription.contains("8, 8, 8"))
        assertEquals(
            GeneratedImage(portrait.bytes, portrait.contentType, portrait.extension),
            portrait,
        )
        client.close()
    }

    @Test
    fun `bytes-only mask uses an inline reference without temporary upload`() = runBlocking {
        var generationBody = ""
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/uploads") || url.contains("upload.example.test") ->
                    error("Inline mask references should not use temporary uploads")
                url.contains("multimodal-generation") -> {
                    generationBody = readBodyText(request.body)
                    respond(
                        content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/mask.png","type":"image"}]}}]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(7, 7, 7)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "qwen-image-3.0",
            ),
            client = client,
        )

        qwen.generateMask(GeneratedImage(byteArrayOf(1, 2, 3), "image/png", "png"), "Create a mask.")

        assertTrue(generationBody.contains("data:image/png;base64,"), generationBody)
        assertTrue(!generationBody.contains("oss://"), generationBody)
        client.close()
    }

    @Test
    fun `local reference is sent inline and its payload is not logged`() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-inline-local-").toFile()
        try {
            val imageFile = root.resolve("study/main.png")
            imageFile.parentFile.mkdirs()
            imageFile.writeBytes(pngBytes(32, 32))
            var requestBody = ""
            val engine = MockEngine { request ->
                val url = request.url.toString()
                when {
                    url.contains("/uploads") || url.contains("upload.example.test") ->
                        error("Local references should not use temporary uploads")
                    url.contains("multimodal-generation") -> {
                        assertNull(request.headers["X-DashScope-OssResourceResolve"])
                        requestBody = readBodyText(request.body)
                        respond(
                            content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/out.png","type":"image"}]}}]}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    url.contains("dashscope-result.example.test") -> respond(
                        content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                    else -> error("Unexpected request: $url")
                }
            }
            val client = HttpClient(engine)
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    apiKey = "test-key",
                    model = "qwen-image-3.0",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "qwen-image-3.0",
                ),
                client = client,
                localImageRoot = root,
                publicBaseUrl = "https://api.oneverandah.app",
            )
            val logger = org.slf4j.LoggerFactory.getLogger(DashScopeQwenClient::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply {
                context = logger.loggerContext
                start()
            }
            logger.addAppender(appender)

            try {
                withContext(CoroutineName("study-build session=study-1 element=TALK_EXPRESSION:main-neutral attempt=1")) {
                    qwen.generate("Render Lan's neutral expression.", listOf("https://api.oneverandah.app/images/study/main.png"))
                }

                assertTrue(requestBody.contains("data:image/png;base64,"), requestBody)
                assertTrue(appender.list.none { it.formattedMessage.contains("data:image/") })
                assertTrue(appender.list.none { it.formattedMessage.contains("base64,") })
            } finally {
                logger.detachAppender(appender)
                appender.stop()
                client.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `inline reference cache loads a remote image once for repeated generations`() = runBlocking {
        val referenceDownloads = AtomicInteger()
        val generationCount = AtomicInteger()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("reference.example.test") -> {
                    referenceDownloads.incrementAndGet()
                    respond(
                        content = ByteReadChannel(pngBytes(24, 24)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "image/png"),
                    )
                }
                url.contains("/uploads") || url.contains("upload.example.test") ->
                    error("Inline references should not use temporary uploads")
                url.contains("multimodal-generation") -> {
                    generationCount.incrementAndGet()
                    respond(
                        content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/out.png","type":"image"}]}}]}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("dashscope-result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "qwen-image-3.0",
            ),
            client = client,
        )

        coroutineScope {
            (1..4).map { panel ->
                async { qwen.generate("Panel $panel", listOf("https://reference.example.test/lan.png")) }
            }.awaitAll()
        }

        assertEquals(1, referenceDownloads.get())
        assertEquals(4, generationCount.get())
        client.close()
    }

    @Test
    fun `temporary upload helper does not cache data URL references`() = runBlocking {
        var policyCount = 0
        var uploadCount = 0
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/api/v1/uploads") -> {
                    policyCount += 1
                    respond(
                        content = """{"data":{"upload_host":"https://upload.example.test/oss","upload_dir":"dashscope-instant/data-url","oss_access_key_id":"AKIATEST","signature":"sig","policy":"pol"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("upload.example.test") -> {
                    uploadCount += 1
                    respondOk("OK")
                }
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "qwen-image-3.0",
            ),
            client = client,
        )

        val dataUrl = "data:image/png;base64,AQID"
        val first = qwen.ensureDashScopeAccessible(dataUrl)
        val second = qwen.ensureDashScopeAccessible(dataUrl)

        assertTrue(first != second, "each data URL fallback upload should receive a fresh object key")
        assertEquals(2, policyCount)
        assertEquals(2, uploadCount)
        client.close()
    }

    @Test
    fun `invalid parameter from inline references falls back to temporary upload`() = runBlocking {
        var generationCount = 0
        var policyCount = 0
        var uploadCount = 0
        var firstGenerationBody = ""
        var fallbackGenerationBody = ""
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/api/v1/uploads") -> {
                    policyCount += 1
                    respond(
                        content = """{"data":{"upload_host":"https://upload.example.test/oss","upload_dir":"dashscope-instant/fallback","oss_access_key_id":"AKIATEST","signature":"sig","policy":"pol"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("upload.example.test") -> {
                    uploadCount += 1
                    respondOk("OK")
                }
                url.contains("multimodal-generation") -> {
                    generationCount += 1
                    val body = readBodyText(request.body)
                    if (generationCount == 1) {
                        firstGenerationBody = body
                        respond(
                            content = """{"code":"InvalidParameterImage","message":"Inline image not supported"}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        fallbackGenerationBody = body
                        assertEquals("enable", request.headers["X-DashScope-OssResourceResolve"])
                        respond(
                            content = """{"output":{"choices":[{"message":{"content":[{"image":"https://dashscope-result.example.test/out.png","type":"image"}]}}]}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                url.contains("dashscope-result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "qwen-image-3.0",
            ),
            client = client,
        )
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(pngBytes(24, 24))}"

        val image = qwen.generate("Render a portrait.", listOf(dataUrl))

        assertTrue(image.bytes.contentEquals(byteArrayOf(4, 4, 4)))
        assertEquals(2, generationCount)
        assertEquals(1, policyCount)
        assertEquals(1, uploadCount)
        assertTrue(firstGenerationBody.contains("data:image/png;base64,"))
        assertTrue(fallbackGenerationBody.contains("oss://dashscope-instant/fallback/"))
        client.close()
    }

    @Test
    fun `content policy rejection does not trigger upload fallback`() = runBlocking {
        var generationCount = 0
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/uploads") || url.contains("upload.example.test") ->
                    error("Content-policy failures must not retry through OSS")
                url.contains("multimodal-generation") -> {
                    generationCount += 1
                    respond(
                        content = """{"code":"DataInspectionFailed","message":"Image rejected by content policy"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected request: $url")
            }
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = "test-key",
                model = "qwen-image-3.0",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "qwen-image-3.0",
            ),
            client = client,
        )

        val failure = runCatching {
            qwen.generate("Render a portrait.", listOf("data:image/png;base64,AQID"))
        }.exceptionOrNull()

        assertEquals("DataInspectionFailed", (failure as? QwenException)?.providerCode)
        assertEquals(1, generationCount)
        client.close()
    }

    @Test
    fun `temporary upload retry refreshes policy and serializes concurrent uploads`() = runBlocking {
        val policyCount = AtomicInteger()
        val uploadCount = AtomicInteger()
        val activeUploads = AtomicInteger()
        val maxActiveUploads = AtomicInteger()
        val uploadBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/uploads") -> {
                    val policy = policyCount.incrementAndGet()
                    respond(
                        content = """{"data":{"upload_host":"https://upload.example.test/oss","upload_dir":"temp","oss_access_key_id":"key","signature":"sig","policy":"policy-$policy"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                url.contains("upload.example.test") -> {
                    val upload = uploadCount.incrementAndGet()
                    val active = activeUploads.incrementAndGet()
                    maxActiveUploads.updateAndGet { maxOf(it, active) }
                    uploadBodies += readBodyText(request.body)
                    try {
                        delay(10)
                        if (upload == 1) throw java.io.EOFException("simulated dropped OSS upload response")
                        respondOk("OK")
                    } finally {
                        activeUploads.decrementAndGet()
                    }
                }
                url.contains("multimodal-generation") -> respond(
                    content = """{"output":{"choices":[{"message":{"content":[{"image":"https://result.example.test/mask.png","type":"image"}]}}]}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                url.contains("result.example.test") -> respond(
                    content = ByteReadChannel(byteArrayOf(4, 4, 4)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
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
                inlineReferenceImages = false,
            ),
            client = client,
        )

        try {
            coroutineScope {
                (1..2).map {
                    async {
                        qwen.generateMask(
                            GeneratedImage(byteArrayOf(1, 2, 3), "image/png", "png"),
                            "Create a binary mask.",
                        )
                    }
                }.awaitAll()
            }

            assertEquals(3, policyCount.get(), "the failed upload must fetch a fresh policy before retrying")
            assertEquals(3, uploadCount.get())
            assertEquals(1, maxActiveUploads.get(), "temporary OSS uploads should not overlap")
            assertTrue(uploadBodies[0].contains("policy-1"))
            assertTrue(uploadBodies[1].contains("policy-2"))
            assertTrue(uploadBodies[2].contains("policy-3"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `Qwen structured rejection is retained without raw response body`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"code":"InvalidParameter","message":"This model does not accept the requested image mode: https://provider.example.test/error?signature=do-not-log","request_id":"req-123","debug":"raw-response-must-not-leak"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
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
        val logger = org.slf4j.LoggerFactory.getLogger(DashScopeQwenClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)

        try {
            val failure = runCatching {
                withContext(CoroutineName("study-build session=study-1 element=TALK_EXPRESSION:main-neutral attempt=1")) {
                    qwen.generate("Generate a portrait.", emptyList())
                }
            }.exceptionOrNull()
            val qwenError = assertNotNull(failure as? QwenException, "expected a structured Qwen rejection")

            assertEquals(400, qwenError.httpStatus)
            assertEquals("InvalidParameter", qwenError.providerCode)
            assertTrue(qwenError.providerMessage.orEmpty().contains("[URL]"))
            assertEquals("req-123", qwenError.requestId)
            assertTrue(!qwenError.message.orEmpty().contains("raw-response-must-not-leak"))
            val display = StudyBuildFailureReason.display(qwenError)
            assertTrue(display.contains("HTTP 400") && display.contains("InvalidParameter"), display)
            assertTrue(display.contains("req-123"), display)

            val operationFailure = appender.list.first { it.formattedMessage.contains("provider returned an error response") }
                .formattedMessage
            assertTrue(operationFailure.contains("InvalidParameter"), operationFailure)
            assertTrue(operationFailure.contains("req-123"), operationFailure)
            assertTrue(!operationFailure.contains("raw-response-must-not-leak"), operationFailure)
            assertTrue(!operationFailure.contains("do-not-log"), operationFailure)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            client.close()
        }
    }

    @Test
    fun `generateMask failure logs the provider stage with build context and redacts signed urls`() = runBlocking {
        val engine = MockEngine {
            throw java.io.EOFException(
                "Provider connection interrupted at https://signed.example.test/policy?Signature=do-not-log",
            )
        }
        val client = HttpClient(engine)
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                model = "wan2.2-t2i-flash",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
                inlineReferenceImages = false,
            ),
            client = client,
        )
        val logger = org.slf4j.LoggerFactory.getLogger(DashScopeQwenClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)

        try {
            val failure = runCatching {
                withContext(CoroutineName("study-build session=study-1 element=TALK_EXPRESSION:main-neutral attempt=1")) {
                    qwen.generateMask(
                        GeneratedImage(byteArrayOf(1, 2, 3), "image/png", "png"),
                        "Create a binary mask.",
                    )
                }
            }.exceptionOrNull()
            assertNotNull(failure, "expected the mocked upload policy connection to fail")

            val messages = appender.list.map { it.formattedMessage }
            assertTrue(messages.any { it.contains("operation started") && it.contains("temporary upload policy request") })
            val failureMessage = messages.first { it.contains("operation failed") && it.contains("temporary upload policy request") }
            assertTrue(failureMessage.contains("study-build session=study-1 element=TALK_EXPRESSION:main-neutral attempt=1"))
            assertTrue(failureMessage.contains("EOFException"), failureMessage)
            assertTrue(failureMessage.contains("[URL]"), failureMessage)
            assertTrue(!failureMessage.contains("do-not-log"), failureMessage)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            client.close()
        }
    }

    @Test
    fun `reference download cancellation propagates without retrying`() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests += 1
            throw CancellationException("study build cancelled")
        })
        val qwen = DashScopeQwenClient(
            config = QwenImageConfig(
                endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = "test-key",
                model = "wan2.2-t2i-flash",
                r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = "wan2.7-image",
            ),
            client = client,
        )

        try {
            val failure = runCatching {
                qwen.generate("Create a portrait.", listOf("https://reference.example.test/portrait.png"))
            }.exceptionOrNull()
            assertTrue(failure is CancellationException, "expected cancellation, got $failure")
            assertEquals(1, requests, "cancellation should not be retried or converted to a missing reference")
        } finally {
            client.close()
        }
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
            600_000,
        )
        assertTrue(DashScopeQwenClient.isTransientHttpClose(timeout))
        assertTrue(
            DashScopeQwenClient.isTransientHttpClose(
                RuntimeException("Request timeout has expired [url=https://dashscope-file-mgr.oss-cn-beijing.aliyuncs.com, request_timeout=600000 ms]"),
            ),
        )
    }

    @Test
    fun generateWithReference_uploadsShrunkJpegWithBoundedTimeout() = runBlocking {
        val root = Files.createTempDirectory("ov-qwen-shrink-").toFile()
        try {
            val key = "reference/huge-scene.png"
            val file = root.resolve(key)
            file.parentFile.mkdirs()
            val original = pngBytes(width = 1800, height = 1400)
            file.writeBytes(original)

            var uploadedBytes = 0
            var uploadTimeout: Long? = -1L
            var uploadSocketTimeout: Long? = -1L

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
                        val timeout = request.getCapabilityOrNull(HttpTimeoutCapability)
                        uploadTimeout = timeout?.requestTimeoutMillis
                        uploadSocketTimeout = timeout?.socketTimeoutMillis
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
                    requestTimeoutMillis = 600_000
                }
            }
            val qwen = DashScopeQwenClient(
                config = QwenImageConfig(
                    endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                    apiKey = "test-key",
                    model = "wan2.7-image",
                    r2iEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                    r2iModel = "wan2.7-image",
                    inlineReferenceImages = false,
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
            assertEquals(45_000L, uploadTimeout)
            assertEquals(45_000L, uploadSocketTimeout)
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
                    inlineReferenceImages = false,
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
                    inlineReferenceImages = false,
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
                    inlineReferenceImages = false,
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
