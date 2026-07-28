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

/**
 * Word-level timing for a synthesized utterance.
 *
 * Times are milliseconds from the start of the audio. Optional [beginIndex]/
 * [endIndex] are character offsets into the synthesized source text when the
 * TTS provider supplies them (end is exclusive when present).
 */
@Serializable
data class SpeechWordTiming(
    val text: String,
    val beginMs: Int,
    val endMs: Int,
    val beginIndex: Int = -1,
    val endIndex: Int = -1,
)

@Serializable
data class SpeechResponse(
    val audioBase64: String,
    val contentType: String,
    val words: List<SpeechWordTiming> = emptyList(),
)

/**
 * Progressive load state for a study token.
 *
 * - [PLAIN]: punctuation / non-lexical; never dissected
 * - [PENDING]: lexical token awaiting sentence dissection
 * - [READY]: full dissection available
 * - [ERROR]: reserved for UI-level failure presentation (optional on tokens)
 */
@Serializable
enum class StudyTokenStatus {
    PLAIN,
    PENDING,
    READY,
    ERROR,
}

/**
 * Request to open an on-demand language study session for arbitrary chat text.
 *
 * [languageTag] is the learner's active UI language and is used for explanations
 * (literal meaning, register, context, synonym glosses, example translations).
 * The language of [text] itself is detected server-side for tokenization and
 * example sentence source text.
 *
 * `POST /api/chat/study` returns a lightweight skeleton (tokens only); full
 * meanings are filled via [StudyDissectSentenceRequest].
 */
@Serializable
data class StudySessionRequest(
    val text: String,
    val languageTag: String,
) {
    fun normalizedOrNull(): StudySessionRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            text = normalizedText,
            languageTag = Language.fromTag(languageTag).tag,
        )
    }
}

/** Study-session payload returned by `POST /api/chat/study` (skeleton or merged). */
@Serializable
data class StudySessionResponse(
    val text: String,
    val textLanguageTag: String,
    val sentences: List<StudySentence>,
)

/** One sentence from the source text, tokenized for interactive study. */
@Serializable
data class StudySentence(
    val text: String,
    val tokens: List<StudyToken>,
)

/**
 * One example sentence for a dissected [StudyToken].
 *
 * [text] is written in the source-text language; [translation] is written in
 * the learner's active UI language.
 */
@Serializable
data class StudyExampleSentence(
    val text: String,
    val translation: String = "",
)

/**
 * A token inside a [StudySentence].
 *
 * Lexical tokens start as [StudyTokenStatus.PENDING] on the skeleton and become
 * [StudyTokenStatus.READY] after per-sentence dissection. Punctuation stays
 * [StudyTokenStatus.PLAIN] with empty dissection fields.
 */
@Serializable
data class StudyToken(
    val text: String,
    val status: StudyTokenStatus = StudyTokenStatus.READY,
    val literalMeaning: String = "",
    val register: String = "",
    val contextualMeaning: String = "",
    val nearSynonyms: List<String> = emptyList(),
    val exampleSentences: List<StudyExampleSentence> = emptyList(),
) {
    /** True when this token is ready for full interactive dissection. */
    val isDissectable: Boolean
        get() = status == StudyTokenStatus.READY && (
            literalMeaning.isNotBlank() ||
                contextualMeaning.isNotBlank() ||
                register.isNotBlank() ||
                nearSynonyms.isNotEmpty() ||
                exampleSentences.isNotEmpty()
            )
}

/**
 * Request to dissect one skeleton sentence into full token details.
 *
 * [tokens] must be the ordered token texts from the skeleton so the server can
 * align AI output back to the UI token stream.
 */
@Serializable
data class StudyDissectSentenceRequest(
    val text: String,
    val sentenceText: String,
    val tokens: List<String>,
    val textLanguageTag: String,
    val languageTag: String,
) {
    fun normalizedOrNull(): StudyDissectSentenceRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedSentence = sentenceText.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedTokens = tokens.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedTokens.isEmpty()) return null
        return copy(
            text = normalizedText,
            sentenceText = normalizedSentence,
            tokens = normalizedTokens,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
        )
    }
}

/** One fully dissected sentence returned by `POST /api/chat/study/dissect`. */
@Serializable
data class StudyDissectSentenceResponse(
    val sentenceText: String,
    val tokens: List<StudyToken>,
)

/**
 * Request to test a dissected word inside a learner-authored sentence.
 *
 * [sentence] should be written in the source/study language ([textLanguageTag]).
 * [summary] feedback is written in the learner's active UI language ([languageTag]).
 */
@Serializable
data class StudyWordPlaygroundRequest(
    val word: String,
    val sentence: String,
    val textLanguageTag: String,
    val languageTag: String,
    val literalMeaning: String = "",
    val contextualMeaning: String = "",
) {
    fun normalizedOrNull(): StudyWordPlaygroundRequest? {
        val normalizedWord = word.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedSentence = sentence.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            word = normalizedWord,
            sentence = normalizedSentence,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
            literalMeaning = literalMeaning.trim(),
            contextualMeaning = contextualMeaning.trim(),
        )
    }
}

/**
 * Immediate playground judgment plus an async comic-image job id.
 *
 * Poll [StudyPlaygroundImageResponse] with [imageId] until the comic is ready.
 */
@Serializable
data class StudyWordPlaygroundResponse(
    val natural: Boolean,
    val summary: String,
    val imageId: String,
)

/** Lifecycle of a study-playground comic image job. */
@Serializable
enum class StudyPlaygroundImageStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED,
}

/** Status payload for `GET /api/chat/study/playground/image/{imageId}`. */
@Serializable
data class StudyPlaygroundImageResponse(
    val imageId: String,
    val status: StudyPlaygroundImageStatus,
    val imageUrl: String? = null,
)

@Serializable
data class ChatErrorResponse(val error: String)

@Serializable
data class DeviceTokenResponse(val deviceToken: String)

@Serializable
data class SaveChatMessageRequest(
    val sourceMessageId: String? = null,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val miniApp: ChatMiniApp? = null,
    val followUpQuestions: List<String> = emptyList(),
) {
    fun normalizedOrNull(): SaveChatMessageRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            sourceMessageId = sourceMessageId?.trim()?.takeIf { it.isNotEmpty() },
            text = normalizedText,
            followUpQuestions = followUpQuestions.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

@Serializable
data class SavedChatMessage(
    val id: String,
    val sourceMessageId: String? = null,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val savedAt: Long,
    val miniApp: ChatMiniApp? = null,
    val followUpQuestions: List<String> = emptyList(),
)

@Serializable
data class SavedChatMessagesResponse(val items: List<SavedChatMessage>)

/**
 * Upsert a progressive study session for the current device.
 *
 * Called automatically as the client receives the skeleton and each sentence
 * dissection so the learner can resume later from the chat empty state.
 */
@Serializable
data class SaveStudySessionRequest(
    val sourceMessageId: String? = null,
    val languageTag: String,
    val session: StudySessionResponse,
) {
    fun normalizedOrNull(): SaveStudySessionRequest? {
        val normalizedText = session.text.trim().takeIf { it.isNotEmpty() } ?: return null
        if (session.sentences.isEmpty()) return null
        return copy(
            sourceMessageId = sourceMessageId?.trim()?.takeIf { it.isNotEmpty() },
            languageTag = Language.fromTag(languageTag).tag,
            session = session.copy(
                text = normalizedText,
                textLanguageTag = Language.fromTag(session.textLanguageTag).tag,
            ),
        )
    }
}

/** A study session snapshot scoped to a device token. */
@Serializable
data class SavedStudySession(
    val id: String,
    val sourceMessageId: String? = null,
    val languageTag: String,
    val text: String,
    val textLanguageTag: String,
    val session: StudySessionResponse,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SavedStudySessionsResponse(val items: List<SavedStudySession>)