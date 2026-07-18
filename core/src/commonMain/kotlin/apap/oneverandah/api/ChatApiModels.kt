package apap.oneverandah.api

import apap.oneverandah.model.Language
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val conversationId: String? = null,
    val message: String,
    val languageTag: String,
) {
    fun normalizedOrNull(): ChatRequest? {
        val normalizedMessage = message.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            conversationId = conversationId?.trim()?.takeIf { it.isNotEmpty() },
            message = normalizedMessage,
            languageTag = Language.fromTag(languageTag).tag,
        )
    }
}

@Serializable
data class ChatResponse(
    val conversationId: String,
    val message: ChatMessage,
)

@Serializable
data class ChatHistoryResponse(
    val conversationId: String,
    val messages: List<ChatMessage>,
)

@Serializable
data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val miniApp: ChatMiniApp? = null,
    val followUpQuestions: List<String> = emptyList(),
)

@Serializable
enum class ChatSender {
    AI,
    YOU,
}

@Serializable
sealed interface ChatMiniApp

@Serializable
@SerialName("language_study")
data class LanguageStudyMiniApp(
    val sentence: String,
    val targetLanguageTag: String,
    val tokens: List<LanguageStudyToken>,
) : ChatMiniApp

@Serializable
data class LanguageStudyToken(
    val text: String,
    val definition: String,
    val contextualMeaning: String,
)

@Serializable
@SerialName("resident_card")
data class ResidentCardMiniApp(
    val residentId: String,
    val name: String,
    val address: String,
    val phone: String,
    val photo: String,
    val sex: String,
    val intro: String,
) : ChatMiniApp

@Serializable
@SerialName("fact")
data class FactMiniApp(
    val factId: String,
    val title: String,
    val body: String,
    val icon: String,
) : ChatMiniApp

@Serializable
data class SpeechRequest(
    val text: String,
    val languageTag: String,
) {
    fun normalizedOrNull(): SpeechRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            text = normalizedText,
            languageTag = Language.fromTag(languageTag).tag,
        )
    }
}

@Serializable
data class SpeechResponse(
    val audioBase64: String,
    val contentType: String,
)

@Serializable
data class ChatErrorResponse(val error: String)