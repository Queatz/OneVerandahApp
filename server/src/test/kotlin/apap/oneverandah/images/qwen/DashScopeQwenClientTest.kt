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
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.nio.file.Files
import java.util.Base64
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
                    model = "wanx2.1-t2i-turbo",
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
