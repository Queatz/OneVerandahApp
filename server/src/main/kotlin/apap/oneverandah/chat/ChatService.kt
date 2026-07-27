package apap.oneverandah.chat

import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatResponse
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
import apap.oneverandah.api.ResidentCardMiniApp
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.data.FactsCatalog
import apap.oneverandah.model.ApartmentFact
import apap.oneverandah.model.Language
import apap.oneverandah.model.ServiceProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

interface ChatRepository {
    suspend fun append(conversationId: String, message: ChatMessage)
    suspend fun history(conversationId: String): List<ChatMessage>
}

class InMemoryChatRepository : ChatRepository {
    private val messages = LinkedHashMap<String, MutableList<ChatMessage>>()
    private val mutex = Mutex()

    override suspend fun append(conversationId: String, message: ChatMessage) {
        mutex.withLock { messages.getOrPut(conversationId) { mutableListOf() } += message }
    }

    override suspend fun history(conversationId: String): List<ChatMessage> =
        mutex.withLock { messages[conversationId]?.toList().orEmpty() }
}

data class ChatGenerationRequest(
    val message: String,
    val languageTag: String,
    val history: List<ChatMessage>,
    val matchedResident: ServiceProvider?,
    val facts: List<FactSummary>,
)

data class FactSummary(
    val id: String,
    val title: String,
    val keywords: List<String>,
)

data class GeneratedChatReply(
    val text: String,
    val languageStudy: GeneratedLanguageStudy? = null,
    val factId: String? = null,
    val followUpQuestions: List<String> = emptyList(),
)

data class GeneratedLanguageStudy(
    val sentence: String,
    val targetLanguageTag: String,
    val tokens: List<LanguageStudyToken>,
)

interface ChatAiClient {
    suspend fun generate(request: ChatGenerationRequest): GeneratedChatReply
}

class ResidentDirectory(residents: List<ServiceProvider>) {
    private val residents = residents.distinctBy { it.id }

    fun findMention(message: String): ServiceProvider? {
        val normalized = message.lowercase()
        return residents.firstOrNull { resident ->
            normalized.contains(resident.name.lowercase()) ||
                normalized.contains(resident.blockAddress.lowercase())
        }
    }
}

class ChatService(
    private val repository: ChatRepository,
    private val aiClient: ChatAiClient,
    private val residentDirectory: ResidentDirectory,
    private val facts: List<ApartmentFact> = FactsCatalog.all,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun send(rawRequest: ChatRequest): ChatResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Message must not be blank" }
        val conversationId = request.conversationId ?: idGenerator()
        val userMessage = ChatMessage(
            id = idGenerator(),
            sender = ChatSender.YOU,
            text = request.message,
            createdAt = clock(),
        )
        repository.append(conversationId, userMessage)

        val language = Language.fromTag(request.languageTag)
        val matchedResident = residentDirectory.findMention(request.message)
        val generated = aiClient.generate(
            ChatGenerationRequest(
                message = request.message,
                languageTag = request.languageTag,
                history = repository.history(conversationId),
                matchedResident = matchedResident,
                facts = facts.map { fact ->
                    FactSummary(fact.id, fact.title.resolve(language), fact.keywords)
                },
            ),
        )
        val matchedFact = facts.firstOrNull { it.id == generated.factId }
        val miniApp = when {
            matchedResident != null -> ResidentCardMiniApp(
                residentId = matchedResident.id,
                name = matchedResident.name,
                address = matchedResident.blockAddress,
                phone = matchedResident.phone,
                photo = matchedResident.photo,
                sex = matchedResident.sex.name,
                intro = matchedResident.intro.resolve(language),
            )
            generated.languageStudy != null -> generated.languageStudy.toMiniApp()
            matchedFact != null -> FactMiniApp(
                factId = matchedFact.id,
                title = matchedFact.title.resolve(language),
                body = matchedFact.body.resolve(language),
                icon = matchedFact.icon,
            )
            else -> null
        }
        val aiMessage = ChatMessage(
            id = idGenerator(),
            sender = ChatSender.AI,
            text = generated.text,
            createdAt = clock(),
            miniApp = miniApp,
            followUpQuestions = generated.followUpQuestions,
        )
        repository.append(conversationId, aiMessage)
        return ChatResponse(conversationId, aiMessage)
    }

    suspend fun history(conversationId: String): List<ChatMessage> = repository.history(conversationId)
}

private fun GeneratedLanguageStudy.toMiniApp() = LanguageStudyMiniApp(
    sentence = sentence,
    targetLanguageTag = Language.fromTag(targetLanguageTag).tag,
    tokens = tokens,
)

data class GeneratedSpeech(
    val bytes: ByteArray,
    val contentType: String,
    val words: List<SpeechWordTiming> = emptyList(),
)

interface SpeechClient {
    suspend fun designVoice(): String
    suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech
}

class SpeechService(
    private val client: SpeechClient,
    private val voiceResolver: VoiceResolver? = null,
) {
    private val mutex = Mutex()
    private var voiceId: String? = null

    suspend fun startup() {
        if (voiceId != null) return
        mutex.withLock {
            if (voiceId == null) voiceId = client.designVoice()
        }
    }

    suspend fun synthesize(rawRequest: SpeechRequest): GeneratedSpeech? {
        val request = rawRequest.normalizedOrNull() ?: return null
        val resolved = voiceResolver?.resolveVoiceId(request.languageTag)
        val voice = if (!resolved.isNullOrBlank()) {
            resolved
        } else {
            startup()
            requireNotNull(voiceId)
        }
        return client.synthesize(request, voice)
    }
}