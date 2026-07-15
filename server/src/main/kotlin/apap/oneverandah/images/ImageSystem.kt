package apap.oneverandah.images

import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import apap.oneverandah.images.arango.ArangoImageRepository
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.AliyunOssStorageService
import apap.oneverandah.images.storage.LocalStorageService
import apap.oneverandah.images.storage.StorageService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Wires together every moving part of the image system and owns its lifecycle.
 *
 * The concrete backends are chosen at [create] time based on configuration, but
 * always fall back to fully-working local defaults, so the system runs
 * end-to-end even when ArangoDB / OSS / Qwen are not configured:
 *  - repository: ArangoDB when reachable, otherwise in-memory.
 *  - storage:    Alibaba OSS when configured, otherwise local disk.
 *  - generator:  Qwen/DashScope when an API key is set, otherwise a built-in
 *                offline placeholder renderer.
 */
class ImageSystem private constructor(
    val config: AppConfig,
    val repository: ImageRepository,
    val queue: QueueService,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    val storageKind: String,
    val generatorKind: String,
    val repositoryKind: String,
) {
    private val log = LoggerFactory.getLogger(ImageSystem::class.java)

    /** Seeds the catalog and starts background processing. Safe to call once. */
    fun startup() {
        log.info(
            "Starting image system [env={}, repo={}, storage={}, generator={}]",
            config.env, repositoryKind, storageKind, generatorKind,
        )
        queue.start()
        scope.launch { seed() }
        scope.launch { reschedulerLoop() }
    }

    /** Ensures every catalog category and service has an image record + is queued. */
    suspend fun seed() {
        val now = System.currentTimeMillis()
        var queued = 0

        Catalog.categories.forEach { category ->
            queue.enqueue(
                ImageEntity(
                    id = "cat_${category.id}",
                    targetId = category.id,
                    targetType = ImageTargetType.CATEGORY,
                    name = category.title.en,
                    prompt = PromptFactory.forCategory(category),
                    status = ImageStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            queued++
        }

        Catalog.services.forEach { service ->
            val categoryName = Catalog.categoryById(service.categoryId)?.title?.en ?: service.categoryId
            queue.enqueue(
                ImageEntity(
                    id = "svc_${service.id}",
                    targetId = service.id,
                    targetType = ImageTargetType.SERVICE,
                    name = service.title.en,
                    prompt = PromptFactory.forService(service, categoryName),
                    status = ImageStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            queued++
        }
        log.info("Seeded/queued {} catalog target(s) for image generation", queued)
    }

    private suspend fun reschedulerLoop() {
        val interval = config.rescheduleIntervalMs
        if (interval <= 0) return
        while (scope.isActive) {
            delay(interval)
            runCatching { queue.rescheduleFailed() }
                .onFailure { log.warn("Reschedule pass failed: {}", it.message) }
        }
    }

    /** Cancels background work and releases the HTTP client. */
    fun shutdown() {
        log.info("Shutting down image system")
        runCatching { scope.cancel() }
        runCatching { httpClient.close() }
    }

    companion object {
        /**
         * Builds a fully wired [ImageSystem]. Never throws for missing optional
         * infrastructure; it degrades to local/in-memory backends instead.
         */
        suspend fun create(config: AppConfig): ImageSystem {
            val httpClient = HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 120_000
                }
                expectSuccess = false
            }

            val repository: ImageRepository =
                ArangoImageRepository.connect(config.arango, httpClient) ?: InMemoryImageRepository()
            val repositoryKind = if (repository is InMemoryImageRepository) "in-memory" else "arangodb"

            val storage: StorageService = if (config.oss.enabled) {
                AliyunOssStorageService(config.oss, httpClient)
            } else {
                LocalStorageService(File(config.localStorageDir), config.publicBaseUrl)
            }

            val qwen: QwenClient = if (config.qwen.enabled) {
                DashScopeQwenClient(config.qwen, httpClient)
            } else {
                PlaceholderQwenClient()
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val generationService = ImageGenerationService(qwen, storage, repository, config.maxRetries)
            val queue = QueueService(
                generationService = generationService,
                repository = repository,
                scope = scope,
                maxConcurrent = config.maxConcurrentGenerations,
                delayBetweenMs = config.generationDelayMs,
            )

            return ImageSystem(
                config = config,
                repository = repository,
                queue = queue,
                httpClient = httpClient,
                scope = scope,
                storageKind = storage.kind,
                generatorKind = qwen.kind,
                repositoryKind = repositoryKind,
            )
        }
    }
}
