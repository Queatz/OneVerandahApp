package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.Catalog
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.http.longRunningHttpClient
import apap.oneverandah.http.shortHttpClient
import apap.oneverandah.http.websocketHttpClient
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.images.storage.applyBinaryImageMask
import apap.oneverandah.logging.SafeFailureDiagnostics
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
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
    val studyBuildService: StudyBuildService,
    val clonedVoicesService: ClonedVoicesService?,
    private val arangoHttpClient: HttpClient,
    private val longRunningHttpClient: HttpClient,
    private val websocketHttpClient: HttpClient,
    private val voiceDesignEnabled: Boolean,
    private val scope: CoroutineScope,
) {
    suspend fun startup() {
        if (voiceDesignEnabled) speechService.startup()
        asrRelay.startup()
        studyBuildService.resumeAll()
    }

    fun shutdown() {
        asrRelay.shutdown()
        scope.cancel()
        arangoHttpClient.close()
        longRunningHttpClient.close()
        websocketHttpClient.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatSystem::class.java)

        private suspend fun <T> logTalkImageStage(stage: String, action: suspend () -> T): T {
            val context = currentCoroutineContext()[CoroutineName]?.name ?: "study-build context unavailable"
            val startedAt = System.nanoTime()
            log.info("Study Talk image stage started: context={} stage={}", context, stage)
            return try {
                action().also {
                    log.info(
                        "Study Talk image stage completed: context={} stage={} elapsedMs={}",
                        context,
                        stage,
                        (System.nanoTime() - startedAt) / 1_000_000,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.warn(
                    "Study Talk image stage failed: context={} stage={} elapsedMs={} diagnostics={}",
                    context,
                    stage,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    SafeFailureDiagnostics.describe(error),
                )
                throw error
            }
        }

        suspend fun create(
            config: AppConfig,
            storage: StorageService? = null,
        ): ChatSystem {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val arangoClient = shortHttpClient()
            val providerClient = longRunningHttpClient()
            val asrClient = websocketHttpClient()
            val arangoRepository = ArangoChatRepository.connect(config.arango, arangoClient)
            val arangoDeviceRepository = ArangoDeviceRepository.connect(config.arango, arangoClient)
            val arangoSavedRepository = ArangoSavedMessagesRepository.connect(config.arango, arangoClient)
            val arangoStudySessionsRepository = ArangoStudySessionsRepository.connect(config.arango, arangoClient)
            val arangoClonedVoicesRepository = ArangoClonedVoicesRepository.connect(config.arango, arangoClient)
            if (arangoRepository == null && config.isProduction) {
                arangoClient.close()
                providerClient.close()
                asrClient.close()
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
                QwenVoiceEnrollmentClient(config.qwenChat, providerClient)
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
                        client = providerClient,
                        localImageRoot = File(config.localStorageDir),
                        publicBaseUrl = config.publicBaseUrl,
                    )
                } else {
                    PlaceholderQwenClient()
                }
                object : StudyPlaygroundImageGenerator {
                    override suspend fun generate(prompt: String, referenceImageUrls: List<String>): String {
                        val generated = qwen.generate(prompt, referenceImageUrls)
                        val folder = if (referenceImageUrls.isEmpty()) "study/playground" else "study/story"
                        val objectKey = "$folder/${UUID.randomUUID()}.${generated.extension}"
                        return storage.upload(objectKey, generated)
                    }

                    override suspend fun generateMaskedPortrait(
                        prompt: String,
                        referenceImageUrls: List<String>,
                        maskPrompt: String,
                        size: String?,
                    ): String {
                        val generated = logTalkImageStage("portrait_generation") {
                            qwen.generate(prompt, referenceImageUrls, size)
                        }
                        val portrait = if (qwen.kind == "placeholder") {
                            generated
                        } else {
                            val mask = logTalkImageStage("mask_generation") {
                                qwen.generateMask(generated, maskPrompt, size)
                            }
                            logTalkImageStage("mask_compositing") {
                                applyBinaryImageMask(generated, mask)
                            }
                        }
                        return logTalkImageStage("portrait_storage") {
                            storage.upload(
                                "study/story/talk/characters/${UUID.randomUUID()}.${portrait.extension}",
                                portrait,
                            )
                        }
                    }
                }
            } else {
                log.warn(
                    "Study playground images disabled: ChatSystem.create() was called without a StorageService",
                )
                null
            }
            val studyAiClient = DashScopeStudyAiClient(config.qwenChat, providerClient)
            val studyService = StudyService(
                aiClient = studyAiClient,
                imageGenerator = playgroundImageGenerator,
                scope = scope,
                talkCharacterImageSize = config.qwen.talkMultimodalSize,
            )
            val studySessionStore = StudySessionStore(studySessionsRepository, scope = scope)
            val studySessionsService = StudySessionsService(
                repository = studySessionsRepository,
                deviceService = deviceService,
                studyAiClient = studyAiClient,
                sessionStore = studySessionStore,
            )
            val speechService = SpeechService(
                client = RoutingSpeechClient(
                    qwenClient = DashScopeSpeechClient(config.qwenChat, providerClient),
                    speechgenClient = SpeechgenSpeechClient(config.speechgen, providerClient),
                    qwenEnabled = config.qwenChat.enabled,
                    speechgenEnabled = config.speechgen.enabled,
                ),
                voiceResolver = clonedVoicesService,
                cache = createTtsCache(File(config.ttsCacheDir)).also {
                    log.info("TTS speech cache enabled at {}", File(config.ttsCacheDir).absolutePath)
                },
            )
            val talkAudioGenerator = storage?.let { assetStorage ->
                StudyTalkAudioGenerator { text, languageTag ->
                    val speech = requireNotNull(
                        speechService.synthesize(SpeechRequest(text = text, languageTag = languageTag)),
                    ) { "Talk audio synthesis returned no audio" }
                    val extension = when (speech.contentType.substringBefore(';').lowercase()) {
                        "audio/mpeg", "audio/mp3", "audio/x-mpeg" -> "mp3"
                        "audio/wav", "audio/x-wav" -> "wav"
                        "audio/ogg" -> "ogg"
                        "audio/webm" -> "webm"
                        else -> "bin"
                    }
                    assetStorage.upload(
                        "study/talk/audio/${UUID.randomUUID()}.$extension",
                        GeneratedImage(
                            bytes = speech.bytes,
                            contentType = speech.contentType,
                            extension = extension,
                        ),
                    )
                }
            }
            val studyBuildService = StudyBuildService(
                repository = studySessionsRepository,
                deviceService = deviceService,
                sessionsService = studySessionsService,
                studyService = studyService,
                speechService = speechService,
                talkAudioGenerator = talkAudioGenerator,
                scope = scope,
                config = config,
            )
            return ChatSystem(
                chatService = ChatService(
                    repository = repository,
                    aiClient = DashScopeChatAiClient(config.qwenChat, providerClient),
                    residentDirectory = ResidentDirectory(residents),
                ),
                speechService = speechService,
                studyService = studyService,
                asrRelay = QwenAsrRelay(config.qwenChat, asrClient, scope),
                deviceService = deviceService,
                savedMessagesService = SavedMessagesService(
                    repository = savedRepository,
                    deviceService = deviceService,
                ),
                studySessionsService = studySessionsService,
                studyBuildService = studyBuildService,
                clonedVoicesService = clonedVoicesService,
                arangoHttpClient = arangoClient,
                longRunningHttpClient = providerClient,
                websocketHttpClient = asrClient,
                voiceDesignEnabled = config.qwenChat.enabled || config.speechgen.enabled,
                scope = scope,
            )
        }
    }
}
