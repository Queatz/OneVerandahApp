package apap.oneverandah.chat

import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import org.slf4j.LoggerFactory

class ChatSystem(
    val chatService: ChatService,
    val speechService: SpeechService,
    val asrRelay: QwenAsrRelay,
    private val httpClient: HttpClient,
    private val voiceDesignEnabled: Boolean,
) {
    suspend fun startup() {
        if (voiceDesignEnabled) speechService.startup()
    }

    fun shutdown() {
        httpClient.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSystem::class.java)

        suspend fun create(config: AppConfig): ChatSystem {
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
            if (arangoRepository == null && config.isProduction) {
                client.close()
                error("Production chat requires ArangoDB at ${config.arango.baseUrl}")
            }
            val repository = arangoRepository ?: InMemoryChatRepository()
            if (arangoRepository == null) log.info("Chat persistence is using the in-memory development store")
            val residents = Catalog.services.map { it.provider }.distinctBy { it.id }
            return ChatSystem(
                chatService = ChatService(
                    repository = repository,
                    aiClient = DashScopeChatAiClient(config.qwenChat, client),
                    residentDirectory = ResidentDirectory(residents),
                ),
                speechService = SpeechService(DashScopeSpeechClient(config.qwenChat, client)),
                asrRelay = QwenAsrRelay(config.qwenChat, client),
                httpClient = client,
                voiceDesignEnabled = config.qwenChat.enabled,
            )
        }
    }
}
