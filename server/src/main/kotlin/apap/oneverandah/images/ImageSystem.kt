package apap.oneverandah.images

import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import apap.oneverandah.images.arango.ArangoImageRepository
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.qwen.QwenErrorLog
import apap.oneverandah.images.storage.AliyunOssStorageService
import apap.oneverandah.images.storage.LocalImageCache
import apap.oneverandah.images.storage.LocalStorageService
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.search.InMemorySearchRepository
import apap.oneverandah.search.SearchRepository
import apap.oneverandah.search.arango.ArangoSearchRepository
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
    val searchRepository: SearchRepository,
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
        private val log = LoggerFactory.getLogger(ImageSystem::class.java)

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

            val arangoRepository = ArangoImageRepository.connect(config.arango, httpClient)
            if (arangoRepository == null && config.isProduction) {
                httpClient.close()
                error("Production requires ArangoDB, but it could not be initialized at ${config.arango.baseUrl}")
            }
            val repository: ImageRepository = arangoRepository ?: InMemoryImageRepository()
            val repositoryKind = if (repository is InMemoryImageRepository) "in-memory" else "arangodb"

            // Best-effort search analytics: persisted in ArangoDB when reachable,
            // otherwise kept in memory. Unlike images it is never required in
            // production, so a failure here only downgrades to in-memory storage.
            val searchRepository: SearchRepository =
                ArangoSearchRepository.connect(config.arango, httpClient) ?: InMemorySearchRepository()

            val storage: StorageService = if (config.oss.enabled) {
                AliyunOssStorageService(config.oss, httpClient)
            } else {
                LocalStorageService(File(config.localStorageDir), config.publicBaseUrl)
            }

            // A single failure log shared by the whole generation pipeline. It is
            // wired into ImageGenerationService (not the Qwen client) so that EVERY
            // failure that ends up marking an image FAILED is recorded here -
            // including storage/upload failures, which never touch the Qwen client.
            val errorLog = QwenErrorLog(File(config.qwenErrorLogPath))

            val qwen: QwenClient = if (config.qwen.enabled) {
                DashScopeQwenClient(config.qwen, httpClient)
            } else {
                PlaceholderQwenClient()
            }

            // A local backup of every generated image. It lets the pipeline retry
            // a failed upload (e.g. OSS outage) by re-uploading the cached bytes
            // instead of regenerating - saving a rate-limited Qwen call.
            val imageCache = LocalImageCache(File(config.imageCacheDir))

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val generationService =
                ImageGenerationService(qwen, storage, repository, config.maxRetries, errorLog, imageCache)
            val queue = QueueService(
                generationService = generationService,
                repository = repository,
                scope = scope,
                maxConcurrent = config.maxConcurrentGenerations,
                delayBetweenMs = config.generationDelayMs,
            )

            // A FAILED image is only "permanently" failed until the next server
            // restart: any image record left FAILED (e.g. from a previous run
            // that ran out of retries) is reset to PENDING and re-queued here,
            // so a fresh process always gives every image another chance
            // instead of leaving it stuck forever.
            runCatching { queue.rescheduleFailed() }
                .onFailure {
                    log.warn("Failed to reset FAILED images to PENDING on startup: {}", it.message)
                }

            return ImageSystem(
                config = config,
                repository = repository,
                searchRepository = searchRepository,
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
