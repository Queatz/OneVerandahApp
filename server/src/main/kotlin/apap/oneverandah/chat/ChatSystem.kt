package apap.oneverandah.chat

import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.StorageService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

class ChatSystem(
    val chatService: ChatService,
    val speechService: SpeechService,
    val studyService: StudyService,
    val asrRelay: QwenAsrRelay,
    val deviceService: DeviceService,
    val savedMessagesService: SavedMessagesService,
    val studySessionsService: StudySessionsService,
    val clonedVoicesService: ClonedVoicesService?,
    private val httpClient: HttpClient,
    private val voiceDesignEnabled: Boolean,
    private val scope: CoroutineScope,
) {
    suspend fun startup() {
        if (voiceDesignEnabled) speechService.startup()
        asrRelay.startup()
    }

    fun shutdown() {
        asrRelay.shutdown()
        scope.cancel()
        httpClient.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService? = null,
        ): ChatSystem {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HttpClient(CIO) {
                install(HttpTimeout) {
                    // Multimodal image gen (esp. reference-to-image) regularly exceeds 2 minutes.
                    requestTimeoutMillis = 300_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 300_000
                }
                install(WebSockets)
                expectSuccess = false
            }
            val arangoRepository = ArangoChatRepository.connect(config.arango, client)
            val arangoDeviceRepository = ArangoDeviceRepository.connect(config.arango, client)
            val arangoSavedRepository = ArangoSavedMessagesRepository.connect(config.arango, client)
            val arangoStudySessionsRepository = ArangoStudySessionsRepository.connect(config.arango, client)
            val arangoClonedVoicesRepository = ArangoClonedVoicesRepository.connect(config.arango, client)
            if (arangoRepository == null && config.isProduction) {
                client.close()
                scope.cancel()
                error("Production chat requires ArangoDB at ${config.arango.baseUrl}")
            }
            val repository = arangoRepository ?: InMemoryChatRepository()
            val deviceRepository = arangoDeviceRepository ?: InMemoryDeviceRepository()
            val savedRepository = arangoSavedRepository ?: InMemorySavedMessagesRepository()
            val studySessionsRepository = arangoStudySessionsRepository ?: InMemoryStudySessionsRepository()
            val clonedVoicesRepository = arangoClonedVoicesRepository ?: InMemoryClonedVoicesRepository()
            if (arangoRepository == null) log.info("Chat persistence is using the in-memory development store")
            if (arangoDeviceRepository == null) log.info("Device identity is using the in-memory development store")
            if (arangoSavedRepository == null) log.info("Saved messages are using the in-memory development store")
            if (arangoStudySessionsRepository == null) log.info("Study sessions are using the in-memory development store")
            if (arangoClonedVoicesRepository == null) log.info("Cloned voices are using the in-memory development store")
            val residents = Catalog.services.map { it.provider }.distinctBy { it.id }
            val deviceService = DeviceService(deviceRepository)
            val enrollment: VoiceEnrollmentClient = if (config.qwenChat.enabled) {
                QwenVoiceEnrollmentClient(config.qwenChat, client)
            } else {
                PlaceholderVoiceEnrollmentClient()
            }
            val clonedVoicesService = if (storage != null) {
                ClonedVoicesService(
                    repository = clonedVoicesRepository,
                    storage = storage,
                    enrollment = enrollment,
                )
            } else {
                log.warn(
                    "Cloned voices admin API disabled: ChatSystem.create() was called without a StorageService",
                )
                null
            }
            val playgroundImageGenerator = if (storage != null) {
                val qwen: QwenClient = if (config.qwen.enabled) {
                    DashScopeQwenClient(
                        config = config.qwen,
                        client = client,
                        localImageRoot = File(config.localStorageDir),
                        publicBaseUrl = config.publicBaseUrl,
                    )
                } else {
                    PlaceholderQwenClient()
                }
                StudyPlaygroundImageGenerator { prompt, referenceImageUrls ->
                    val generated = qwen.generate(prompt, referenceImageUrls)
                    val folder = if (referenceImageUrls.isEmpty()) "study/playground" else "study/story"
                    val objectKey = "$folder/${UUID.randomUUID()}.${generated.extension}"
                    storage.upload(objectKey, generated)
                }
            } else {
                log.warn(
                    "Study playground comics disabled: ChatSystem.create() was called without a StorageService",
                )
                null
            }
            return ChatSystem(
                chatService = ChatService(
                    repository = repository,
                    aiClient = DashScopeChatAiClient(config.qwenChat, client),
                    residentDirectory = ResidentDirectory(residents),
                ),
                speechService = SpeechService(
                    client = DashScopeSpeechClient(config.qwenChat, client),
                    voiceResolver = clonedVoicesService,
                    cache = createTtsCache(File(config.ttsCacheDir)).also {
                        log.info("TTS speech cache enabled at {}", File(config.ttsCacheDir).absolutePath)
                    },
                ),
                studyService = StudyService(
                    aiClient = DashScopeStudyAiClient(config.qwenChat, client),
                    imageGenerator = playgroundImageGenerator,
                    scope = scope,
                ),
                asrRelay = QwenAsrRelay(config.qwenChat, client, scope),
                deviceService = deviceService,
                savedMessagesService = SavedMessagesService(
                    repository = savedRepository,
                    deviceService = deviceService,
                ),
                studySessionsService = StudySessionsService(
                    repository = studySessionsRepository,
                    deviceService = deviceService,
                ),
                clonedVoicesService = clonedVoicesService,
                httpClient = client,
                voiceDesignEnabled = config.qwenChat.enabled,
                scope = scope,
            )
        }
    }
}
