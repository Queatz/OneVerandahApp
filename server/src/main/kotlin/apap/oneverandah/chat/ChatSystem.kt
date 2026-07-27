package apap.oneverandah.chat

import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

class ChatSystem(
    val chatService: ChatService,
    val speechService: SpeechService,
    val studyService: StudyService,
    val asrRelay: QwenAsrRelay,
    val deviceService: DeviceService,
    val savedMessagesService: SavedMessagesService,
    val studySessionsService: StudySessionsService,
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

        suspend fun create(config: AppConfig): ChatSystem {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 120_000
                }
                install(WebSockets)
                expectSuccess = false
            }
            val arangoRepository = ArangoChatRepository.connect(config.arango, client)
            val arangoDeviceRepository = ArangoDeviceRepository.connect(config.arango, client)
            val arangoSavedRepository = ArangoSavedMessagesRepository.connect(config.arango, client)
            val arangoStudySessionsRepository = ArangoStudySessionsRepository.connect(config.arango, client)
            if (arangoRepository == null && config.isProduction) {
                client.close()
                scope.cancel()
                error("Production chat requires ArangoDB at ${config.arango.baseUrl}")
            }
            val repository = arangoRepository ?: InMemoryChatRepository()
            val deviceRepository = arangoDeviceRepository ?: InMemoryDeviceRepository()
            val savedRepository = arangoSavedRepository ?: InMemorySavedMessagesRepository()
            val studySessionsRepository = arangoStudySessionsRepository ?: InMemoryStudySessionsRepository()
            if (arangoRepository == null) log.info("Chat persistence is using the in-memory development store")
            if (arangoDeviceRepository == null) log.info("Device identity is using the in-memory development store")
            if (arangoSavedRepository == null) log.info("Saved messages are using the in-memory development store")
            if (arangoStudySessionsRepository == null) log.info("Study sessions are using the in-memory development store")
            val residents = Catalog.services.map { it.provider }.distinctBy { it.id }
            val deviceService = DeviceService(deviceRepository)
            return ChatSystem(
                chatService = ChatService(
                    repository = repository,
                    aiClient = DashScopeChatAiClient(config.qwenChat, client),
                    residentDirectory = ResidentDirectory(residents),
                ),
                speechService = SpeechService(DashScopeSpeechClient(config.qwenChat, client)),
                studyService = StudyService(DashScopeStudyAiClient(config.qwenChat, client)),
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
                httpClient = client,
                voiceDesignEnabled = config.qwenChat.enabled,
                scope = scope,
            )
        }
    }
}
