package apap.oneverandah

import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.AppEnv
import apap.oneverandah.config.EnvSource
import apap.oneverandah.images.ImageEntity
import apap.oneverandah.images.ImageGenerationService
import apap.oneverandah.images.ImageStatus
import apap.oneverandah.images.ImageSystem
import apap.oneverandah.images.ImageTargetType
import apap.oneverandah.images.InMemoryImageRepository
import apap.oneverandah.images.QueueService
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.qwen.QwenErrorLog
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.LocalImageCache
import apap.oneverandah.images.storage.LocalStorageService
import apap.oneverandah.images.storage.StorageService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageSystemTest {

    private fun noEnvConfig(): AppConfig = AppConfig.load(File("this-file-does-not-exist.env"))

    @Test
    fun envSourceParsesKeyValuesQuotesAndComments() {
        val parsed = EnvSource.parse(
            """
            # comment
            APP_PORT=9000
            export QWEN_API_KEY="sk-abc"
            OSS_BUCKET_NAME='my-bucket'

            INVALID LINE WITHOUT EQUALS
            """.trimIndent(),
        )
        assertEquals("9000", parsed["APP_PORT"])
        assertEquals("sk-abc", parsed["QWEN_API_KEY"])
        assertEquals("my-bucket", parsed["OSS_BUCKET_NAME"])
        assertNull(parsed["INVALID LINE WITHOUT EQUALS"])
    }

    @Test
    fun qwenInlineReferencesDefaultOnAndCanBeDisabled() {
        val envFile = Files.createTempFile("ov-inline-references", ".env").toFile()
        try {
            assertTrue(AppConfig.load(envFile).qwen.inlineReferenceImages)
            envFile.writeText("QWEN_INLINE_REFERENCES=false")
            assertTrue(!AppConfig.load(envFile).qwen.inlineReferenceImages)
        } finally {
            envFile.delete()
        }
    }

    @Test
    fun productionPublicBaseUrlUsesSslDomainNotLocalhost() {
        // Reproduces the production bug: with SSL on and the server binding
        // 0.0.0.0, the public base URL used to become https://localhost:443,
        // yielding broken image links like https://localhost/images/... .
        val envFile = Files.createTempFile("ov-prod", ".env").toFile()
        envFile.writeText(
            """
            APP_ENV=production
            APP_HOST=0.0.0.0
            APP_PORT=80
            SSL_ENABLED=true
            SSL_PORT=443
            SSL_DOMAIN=api.oneverandah.app
            SSL_KEYSTORE_PATH=/tmp/keystore.p12
            """.trimIndent(),
        )

        val config = AppConfig.load(envFile)

        // The public base URL now uses the real domain and omits the default
        // https port instead of falling back to localhost.
        assertEquals("https://api.oneverandah.app", config.publicBaseUrl)
        // Production also defaults to the local ArangoDB installed by init.sh,
        // so a copied .env.example with blank ARANGO_HOST no longer disables
        // persistence and makes /health report the in-memory repository.
        assertEquals("127.0.0.1", config.arango.host)

        // A locally-served image link built from that base points at the public
        // domain, which is exactly what the frontend needs.
        val dir = Files.createTempDirectory("ov-prod-img").toFile()
        val storage = LocalStorageService(dir, config.publicBaseUrl)
        val url = runBlocking { storage.upload("category/massages.png", GeneratedImage(byteArrayOf(1, 2, 3))) }
        assertEquals("https://api.oneverandah.app/images/category/massages.png", url)
    }

    @Test
    fun productionArangoFailureFailsStartupInsteadOfUsingInMemory() = runBlocking {
        val config = noEnvConfig().copy(
            env = AppEnv.PRODUCTION,
            arango = noEnvConfig().arango.copy(host = "127.0.0.1", port = 1),
            autoStart = false,
        )

        val error = assertFailsWith<IllegalStateException> {
            ImageSystem.create(config)
        }
        assertTrue(error.message.orEmpty().contains("Production requires ArangoDB"))
    }

    @Test
    fun placeholderGeneratorProducesDecodablePng() = runBlocking {
        val image = PlaceholderQwenClient(size = 64).generate("Massages")
        assertEquals("image/png", image.contentType)
        assertTrue(image.bytes.isNotEmpty())
        val decoded = ImageIO.read(ByteArrayInputStream(image.bytes))
        assertNotNull(decoded, "Generated bytes should be a valid PNG")
        assertEquals(64, decoded.width)
    }

    @Test
    fun generationStoresImageAndMarksCompleted() = runBlocking {
        val repo = InMemoryImageRepository()
        val dir = Files.createTempDirectory("ov-img").toFile()
        val storage = LocalStorageService(dir, "http://localhost:7071")
        val service = ImageGenerationService(
            qwenClient = PlaceholderQwenClient(size = 64),
            storage = storage,
            repository = repo,
            maxRetries = 1,
        )
        repo.save(
            ImageEntity(
                id = "svc_word-massages-jacob",
                targetId = "word-massages-jacob",
                targetType = ImageTargetType.SERVICE,
                name = "Word Massages",
                prompt = "a prompt",
                status = ImageStatus.PENDING,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        service.generateImage("svc_word-massages-jacob")

        val updated = repo.findByImageId("svc_word-massages-jacob")
        assertNotNull(updated)
        assertEquals(ImageStatus.COMPLETED, updated.status)
        assertEquals("http://localhost:7071/images/service/word-massages-jacob.png", updated.imageUrl)
        val file = File(dir, "service/word-massages-jacob.png")
        assertTrue(file.exists() && file.length() > 0)
    }

    @Test
    fun uploadFailureMarksFailedAndIsLogged() = runBlocking {
        val repo = InMemoryImageRepository()
        val logFile = Files.createTempFile("qwen-errors", ".txt").toFile()
        logFile.delete()
        // Qwen generation succeeds, but the storage upload always fails - exactly
        // the scenario that previously produced a FAILED image with no logged
        // error (the failure never touched the Qwen client).
        val failingStorage = object : StorageService {
            override val kind = "failing"
            override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                throw RuntimeException("bucket not provisioned (404)")
        }
        val service = ImageGenerationService(
            qwenClient = PlaceholderQwenClient(size = 32),
            storage = failingStorage,
            repository = repo,
            maxRetries = 1,
            errorLog = QwenErrorLog(logFile),
        )
        repo.save(
            ImageEntity(
                id = "cat_massages",
                targetId = "massages",
                targetType = ImageTargetType.CATEGORY,
                name = "Massages",
                prompt = "a prompt",
                status = ImageStatus.PENDING,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        service.generateImage("cat_massages")

        assertEquals(ImageStatus.FAILED, repo.findByImageId("cat_massages")?.status)
        // The invariant this issue is about: a FAILED image ALWAYS has a logged error.
        val logged = logFile.readText()
        assertTrue(logged.contains("massages"), "upload failure should be logged: <$logged>")
        assertTrue(logged.contains("bucket not provisioned (404)"))
    }

    @Test
    fun uploadFailureCachesImageAndRetryReusesItWithoutRegenerating() = runBlocking {
        val repo = InMemoryImageRepository()
        val cacheDir = Files.createTempDirectory("ov-cache").toFile()
        val cache = LocalImageCache(cacheDir)

        // A Qwen client that counts how many times it actually generates.
        val generateCount = java.util.concurrent.atomic.AtomicInteger(0)
        val countingQwen = object : QwenClient {
            override val kind = "counting"
            override suspend fun generate(
                prompt: String,
                referenceImageUrls: List<String>,
            ): GeneratedImage {
                generateCount.incrementAndGet()
                return PlaceholderQwenClient(size = 32).generate(prompt, referenceImageUrls)
            }
        }

        // Storage that fails the first time (forcing FAILED) then succeeds.
        val uploadAttempts = java.util.concurrent.atomic.AtomicInteger(0)
        val flakyDir = Files.createTempDirectory("ov-flaky").toFile()
        val local = LocalStorageService(flakyDir, "http://localhost:7071")
        val flakyStorage = object : StorageService {
            override val kind = "flaky"
            override suspend fun upload(objectKey: String, image: GeneratedImage): String {
                if (uploadAttempts.getAndIncrement() == 0) throw RuntimeException("OSS down (503)")
                return local.upload(objectKey, image)
            }
        }

        val service = ImageGenerationService(
            qwenClient = countingQwen,
            storage = flakyStorage,
            repository = repo,
            maxRetries = 1,
            imageCache = cache,
        )
        repo.save(
            ImageEntity(
                id = "cat_massages",
                targetId = "massages",
                targetType = ImageTargetType.CATEGORY,
                name = "Massages",
                prompt = "a prompt",
                status = ImageStatus.PENDING,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        // First pass: generation succeeds, upload fails -> FAILED, but bytes cached.
        service.generateImage("cat_massages")
        assertEquals(ImageStatus.FAILED, repo.findByImageId("cat_massages")?.status)
        assertEquals(1, generateCount.get())
        assertNotNull(cache.load(ImageTargetType.CATEGORY, "massages"), "image should be cached after generation")

        // Retry: upload succeeds this time. Qwen must NOT be called again - the
        // cached image is re-uploaded instead of regenerated.
        repo.updateStatus("cat_massages", ImageStatus.PENDING)
        service.generateImage("cat_massages")

        val updated = repo.findByImageId("cat_massages")
        assertEquals(ImageStatus.COMPLETED, updated?.status)
        assertEquals(1, generateCount.get(), "cached image must be reused, not regenerated")
        // Cache is cleared once the upload finally succeeds.
        assertNull(cache.load(ImageTargetType.CATEGORY, "massages"))
    }

    @Test
    fun queueDeduplicatesByTarget() = runBlocking {
        val repo = InMemoryImageRepository()
        val dir = Files.createTempDirectory("ov-queue").toFile()
        val generation = ImageGenerationService(
            PlaceholderQwenClient(size = 32),
            LocalStorageService(dir, "http://localhost"),
            repo,
            1,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = QueueService(generation, repo, scope, maxConcurrent = 1, delayBetweenMs = 0)

        val entity = ImageEntity(
            id = "cat_massages",
            targetId = "massages",
            targetType = ImageTargetType.CATEGORY,
            name = "Massages",
            prompt = "p",
            status = ImageStatus.PENDING,
            createdAt = 1,
            updatedAt = 1,
        )
        queue.enqueue(entity)
        // Same target/type but different id -> must be treated as a duplicate.
        queue.enqueue(entity.copy(id = "cat_massages_dup"))

        assertEquals(1, repo.findAll().size)
        scope.cancel()
    }

    @Test
    fun rescheduleFailedResetsStatusToPendingAndReprocesses() = runBlocking {
        val repo = InMemoryImageRepository()
        val dir = Files.createTempDirectory("ov-restart").toFile()
        val generation = ImageGenerationService(
            PlaceholderQwenClient(size = 32),
            LocalStorageService(dir, "http://localhost"),
            repo,
            1,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = QueueService(generation, repo, scope, maxConcurrent = 1, delayBetweenMs = 0)

        // Simulate an image record left FAILED by a previous (crashed) run.
        repo.save(
            ImageEntity(
                id = "cat_massages",
                targetId = "massages",
                targetType = ImageTargetType.CATEGORY,
                name = "Massages",
                prompt = "p",
                status = ImageStatus.FAILED,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        // This is exactly what ImageSystem.create() now runs on every server start.
        queue.rescheduleFailed()

        assertEquals(ImageStatus.PENDING, repo.findByImageId("cat_massages")?.status)

        // The reset image was also re-queued, so once workers start it gets processed.
        queue.start()
        withTimeout(5_000) {
            while (repo.findByImageId("cat_massages")?.status != ImageStatus.COMPLETED) {
                delay(10)
            }
        }
        scope.cancel()
    }

    @Test
    fun seedEnqueueRequeuesPersistedPendingAfterRestart() = runBlocking {
        val repo = InMemoryImageRepository()
        val dir = Files.createTempDirectory("ov-pending-restart").toFile()
        val generation = ImageGenerationService(
            PlaceholderQwenClient(size = 32),
            LocalStorageService(dir, "http://localhost"),
            repo,
            1,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = QueueService(generation, repo, scope, maxConcurrent = 1, delayBetweenMs = 0)

        // Leftover from a previous process: the record is still PENDING in
        // persistent storage, but the in-memory channel is empty.
        val entity = ImageEntity(
            id = "cat_massages",
            targetId = "massages",
            targetType = ImageTargetType.CATEGORY,
            name = "Massages",
            prompt = "p",
            status = ImageStatus.PENDING,
            createdAt = 1,
            updatedAt = 1,
        )
        repo.save(entity)

        queue.start()
        // ImageSystem.seed() re-enqueues every catalog target on startup.
        queue.enqueue(entity)

        withTimeout(5_000) {
            while (repo.findByImageId("cat_massages")?.status != ImageStatus.COMPLETED) {
                delay(10)
            }
        }
        scope.cancel()
    }

    @Test
    fun resumeInterruptedRequeuesPersistedGeneratingAfterRestart() = runBlocking {
        val repo = InMemoryImageRepository()
        val dir = Files.createTempDirectory("ov-generating-restart").toFile()
        val generation = ImageGenerationService(
            PlaceholderQwenClient(size = 32),
            LocalStorageService(dir, "http://localhost"),
            repo,
            1,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queue = QueueService(generation, repo, scope, maxConcurrent = 1, delayBetweenMs = 0)

        repo.save(
            ImageEntity(
                id = "cat_massages",
                targetId = "massages",
                targetType = ImageTargetType.CATEGORY,
                name = "Massages",
                prompt = "p",
                status = ImageStatus.GENERATING,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        // ImageSystem.create() now resumes interrupted jobs before workers start.
        queue.resumeInterrupted()
        queue.start()
        withTimeout(5_000) {
            while (repo.findByImageId("cat_massages")?.status != ImageStatus.COMPLETED) {
                delay(10)
            }
        }
        scope.cancel()
    }

    @Test
    fun healthAndImagesEndpointsWork() = testApplication {
        val config = noEnvConfig()
        val system = ImageSystem.create(config)
        application { module(config, system) }

        // Seed in-memory records (no worker started -> they stay PENDING).
        system.seed()

        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("\"status\":\"UP\""))

        val images = client.get("/api/v1/images").bodyAsText()
        assertTrue(images.contains("\"targetId\":\"massages\""), "Expected seeded category in response")

        val status = client.get("/api/v1/images/status/massages")
        assertEquals(HttpStatusCode.OK, status.status)
        assertTrue(status.bodyAsText().contains("CATEGORY"))
    }

    @Test
    fun generateEndpointValidatesInput() = testApplication {
        val config = noEnvConfig()
        val system = ImageSystem.create(config)
        application { module(config, system) }
        system.seed()

        val notFound = client.post("/api/v1/images/generate") {
            contentType(ContentType.Application.Json)
            setBody("""{"imageId":"does-not-exist"}""")
        }
        assertEquals(HttpStatusCode.NotFound, notFound.status)

        val accepted = client.post("/api/v1/images/generate") {
            contentType(ContentType.Application.Json)
            setBody("""{"imageId":"cat_massages"}""")
        }
        assertEquals(HttpStatusCode.Accepted, accepted.status)
    }

    @Test
    fun rootStillGreets() = testApplication {
        val config = noEnvConfig()
        val system = ImageSystem.create(config)
        application { module(config, system) }
        assertEquals("Hello, Ktor!", client.get("/").bodyAsText())
    }

    @Test
    fun searchEndpointRecordsQueryAndValidatesInput() = testApplication {
        val config = noEnvConfig()
        val system = ImageSystem.create(config)
        application { module(config, system) }

        // A blank query is rejected.
        val blank = client.post("/search") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)

        // A real query is accepted and echoed back.
        val accepted = client.post("/search") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"deep tissue massage"}""")
        }
        assertEquals(HttpStatusCode.Accepted, accepted.status)
        assertTrue(accepted.bodyAsText().contains("deep tissue massage"))
    }

    @Test
    fun searchHistoryReturnsLatestTwentyNewestFirst() = testApplication {
        val config = noEnvConfig()
        val system = ImageSystem.create(config)
        application { module(config, system) }

        // Record 25 distinct queries; only the latest 20 should come back.
        for (i in 1..25) {
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody("""{"query":"query-$i"}""")
            }
            assertEquals(HttpStatusCode.Accepted, response.status)
            // Ensure a strictly increasing createdAt so ordering is deterministic.
            delay(2)
        }

        val history = client.get("/search-history")
        assertEquals(HttpStatusCode.OK, history.status)
        val body = history.bodyAsText()

        // Newest queries are present, oldest ones have fallen off the list.
        assertTrue(body.contains("query-25"), "Latest query should be present")
        assertTrue(body.contains("query-6"), "20th-newest query should be present")
        assertTrue(!body.contains("\"query-5\""), "Older queries should be dropped")

        // Exactly 20 records are returned.
        val count = Regex("\"query\":\"query-").findAll(body).count()
        assertEquals(20, count)
    }

    @Test
    fun qwenErrorLogAppendsTimestampedMessages() {
        val file = Files.createTempFile("qwen-errors", ".txt").toFile()
        file.delete() // start from "does not exist yet", like a fresh deployment
        val errorLog = QwenErrorLog(file)

        errorLog.record("Qwen submit failed (400 Bad Request): boom")
        errorLog.record("Qwen task abc123 failed: oops")

        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("Qwen submit failed (400 Bad Request): boom"))
        assertTrue(lines[1].endsWith("Qwen task abc123 failed: oops"))
    }

    @Test
    fun qwenErrorsEndpointServesLogFileOrEmpty() = testApplication {
        val logFile = Files.createTempFile("qwen-errors", ".txt").toFile()
        logFile.delete()
        val config = noEnvConfig().copy(qwenErrorLogPath = logFile.absolutePath)
        val system = ImageSystem.create(config)
        application { module(config, system) }

        // No errors recorded yet -> the endpoint is reachable but empty.
        val beforeAny = client.get("/qwen_errors")
        assertEquals(HttpStatusCode.OK, beforeAny.status)
        assertEquals("", beforeAny.bodyAsText())

        QwenErrorLog(logFile).record("Qwen task xyz failed: something went wrong")

        val afterFailure = client.get("/qwen_errors")
        assertEquals(HttpStatusCode.OK, afterFailure.status)
        assertTrue(afterFailure.bodyAsText().contains("Qwen task xyz failed: something went wrong"))
    }
}
