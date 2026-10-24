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
    /**
     * True while a faster short reply is shown and the fuller reply is still running.
     * Final responses set this to false (default) so older clients ignore the field.
     */
    val partial: Boolean = false,
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
            languageTag = Language.normalizeContentTag(languageTag),
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

/**
 * Study text produced from a learner prompt (parse mode).
 *
 * `POST /api/chat/study/intent` turns a request such as "sentences about
 * talking about relationships in Vietnamese" into the actual study material,
 * which the client then sends to [StudySessionRequest] as usual.
 */
@Serializable
data class StudyIntentResponse(
    val text: String,
)

/**
 * Named cast member for Study-tab story visuals.
 *
 * Designed once per session, then used as a reference portrait so every sentence
 * panel keeps the same main (and optional secondary) character.
 */
@Serializable
data class StudyStoryCharacter(
    /** Stable id within the session (e.g. "main", "secondary"). */
    val id: String,
    /** Short given name used in prompts ("Lan", "Minh"). */
    val name: String,
    /** "main" or "secondary". */
    val role: String,
    /** Visual appearance description for portraits and scene prompts. */
    val description: String,
    /** Async job id for the character reference portrait. */
    val imageId: String? = null,
    /** Public URL of the completed reference portrait. */
    val imageUrl: String? = null,
)

/** Study-session payload returned by `POST /api/chat/study` (skeleton or merged). */
@Serializable
data class StudySessionResponse(
    val text: String,
    val textLanguageTag: String,
    val sentences: List<StudySentence>,
    /**
     * Story cast for Study-tab visuals. Empty on fresh skeletons; filled after
     * character design and persisted so portraits are not regenerated.
     */
    val characters: List<StudyStoryCharacter> = emptyList(),
    /**
     * Play-tab role-play scenarios with full conversation history and images.
     * Empty on fresh skeletons; filled once (and updated on each turn) so reopen
     * can resume without regenerating scenarios or chats.
     */
    val playScenarios: List<StudyPlayScenario> = emptyList(),
)

/** One sentence from the source text, tokenized for interactive study. */
@Serializable
data class StudySentence(
    val text: String,
    val tokens: List<StudyToken>,
    /**
     * Async job id for the expressive Study-tab visual.
     * Poll [StudyPlaygroundImageResponse] until ready; persisted so reopen can resume.
     */
    val imageId: String? = null,
    /**
     * Public URL of the completed Study-tab visual. When set, the client must not
     * regenerate the image for this sentence.
     */
    val imageUrl: String? = null,
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
 *
 * [imageId]/[imageUrl] hold the pre-generated expressive word visual for
 * dissectable tokens (prompted from the word + contextual meaning).
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
    /** Async word-image job id; poll [StudyPlaygroundImageResponse]. */
    val imageId: String? = null,
    /** Public URL once the word visual is ready. */
    val imageUrl: String? = null,
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

/**
 * Design a main (and optional secondary) story character for Study-tab visuals.
 *
 * Returns cast members with portrait [StudyStoryCharacter.imageId] jobs started;
 * poll [StudyPlaygroundImageResponse] until each [StudyStoryCharacter.imageUrl] is ready.
 */
@Serializable
data class StudyStoryCharactersRequest(
    val text: String,
    val textLanguageTag: String = "",
) {
    fun normalizedOrNull(): StudyStoryCharactersRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            text = normalizedText,
            textLanguageTag = textLanguageTag.trim().takeIf { it.isNotEmpty() }
                ?.let { Language.fromTag(it).tag }
                .orEmpty(),
        )
    }
}

/** Cast designed for a study text, with portrait jobs already enqueued. */
@Serializable
data class StudyStoryCharactersResponse(
    val characters: List<StudyStoryCharacter>,
)

/**
 * Request an expressive Study-tab visual for one skeleton sentence.
 *
 * Returns an [imageId] immediately; poll [StudyPlaygroundImageResponse] until
 * the image is ready, then persist [StudySentence.imageUrl] on the session.
 * When [characters] include portrait URLs, the generator uses them as named
 * identity references so panels feel like one continuous story.
 */
@Serializable
data class StudySentenceImageRequest(
    val sentenceText: String,
    /** Full source text for story continuity across panels. */
    val fullText: String = "",
    /** Zero-based index of this sentence in the session. */
    val sentenceIndex: Int = 0,
    /** Total sentence count in the session (for "panel N of M" guidance). */
    val sentenceCount: Int = 1,
    /** Named cast with optional reference portrait URLs. */
    val characters: List<StudyStoryCharacter> = emptyList(),
) {
    fun normalizedOrNull(): StudySentenceImageRequest? {
        val normalized = sentenceText.trim().takeIf { it.isNotEmpty() } ?: return null
        val cast = characters.mapNotNull { character ->
            val name = character.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val role = character.role.trim().ifEmpty { "main" }
            val description = character.description.trim()
            StudyStoryCharacter(
                id = character.id.trim().ifEmpty { role },
                name = name,
                role = role,
                description = description,
                imageId = character.imageId?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return copy(
            sentenceText = normalized,
            fullText = fullText.trim(),
            sentenceIndex = sentenceIndex.coerceAtLeast(0),
            sentenceCount = sentenceCount.coerceAtLeast(1),
            characters = cast,
        )
    }
}

/** Immediate ack for a Study-tab sentence visual job. */
@Serializable
data class StudySentenceImageResponse(
    val imageId: String,
)

/**
 * Request an expressive Study-tab visual for one dissected word/phrase.
 *
 * Returns an [imageId] immediately; poll [StudyPlaygroundImageResponse] until
 * the image is ready, then persist [StudyToken.imageUrl] on the session.
 * The generator designs a movie-like scene from the first [exampleSentences]
 * entry (else [contextualMeaning]) and never paints [word] itself.
 */
@Serializable
data class StudyWordImageRequest(
    val word: String,
    val contextualMeaning: String = "",
    val exampleSentences: List<StudyExampleSentence> = emptyList(),
) {
    fun normalizedOrNull(): StudyWordImageRequest? {
        val normalizedWord = word.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            word = normalizedWord,
            contextualMeaning = contextualMeaning.trim(),
            exampleSentences = exampleSentences.mapNotNull { example ->
                val text = example.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StudyExampleSentence(
                    text = text,
                    translation = example.translation.trim(),
                )
            },
        )
    }
}

/** Immediate ack for a Study-tab word visual job. */
@Serializable
data class StudyWordImageResponse(
    val imageId: String,
)

/**
 * Request four related role-play scenario starters for the Play tab.
 *
 * [languageTag] is the learner's UI language (scenario setup text).
 * Dialogue lines are written in the study/source language ([textLanguageTag]).
 */
@Serializable
data class StudyPlayScenariosRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val characters: List<StudyStoryCharacter> = emptyList(),
) {
    fun normalizedOrNull(): StudyPlayScenariosRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val cast = characters.mapNotNull { character ->
            val name = character.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val role = character.role.trim().ifEmpty { "main" }
            StudyStoryCharacter(
                id = character.id.trim().ifEmpty { role },
                name = name,
                role = role,
                description = character.description.trim(),
                imageId = character.imageId?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return copy(
            text = normalizedText,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
            characters = cast,
        )
    }
}

/**
 * One turn in a persisted Play-tab conversation (opening beat or later reply).
 *
 * [text] may be empty for an opening beat that only has a scene description.
 * [imageId]/[imageUrl] attach the cover/beat visual when present.
 */
@Serializable
data class StudyPlayChatMessage(
    val speaker: StudyPlaySpeaker,
    val text: String = "",
    val description: String = "",
    val imageId: String? = null,
    val imageUrl: String? = null,
)

/** One Play-tab scenario starter with optional opening line and full chat history. */
@Serializable
data class StudyPlayScenario(
    val id: String,
    /** Short scene setup for the learner (UI language). */
    val description: String,
    /** Optional first line spoken by the other person (study language). */
    val openingLine: String? = null,
    /** Async cover-image job id; poll [StudyPlaygroundImageResponse]. */
    val imageId: String? = null,
    /** Public URL once the cover image is ready. */
    val imageUrl: String? = null,
    /**
     * Full conversation for this scenario, including the opening beat.
     * Persisted so follow-up chats and images survive reopen.
     */
    val messages: List<StudyPlayChatMessage> = emptyList(),
)

@Serializable
data class StudyPlayScenariosResponse(
    val scenarios: List<StudyPlayScenario>,
)

/** Speaker in a Play-tab conversation history. */
@Serializable
enum class StudyPlaySpeaker {
    USER,
    OTHER,
}

/** One prior turn used when generating the next Play-tab reply. */
@Serializable
data class StudyPlayHistoryMessage(
    val speaker: StudyPlaySpeaker,
    val text: String,
    val description: String = "",
)

/**
 * Continue a Play-tab conversation after the learner sends a message.
 *
 * Returns the other character's reply plus a fresh async cover image job.
 */
@Serializable
data class StudyPlayTurnRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val scenarioDescription: String,
    val userMessage: String,
    val history: List<StudyPlayHistoryMessage> = emptyList(),
    val characters: List<StudyStoryCharacter> = emptyList(),
) {
    fun normalizedOrNull(): StudyPlayTurnRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedScenario = scenarioDescription.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedUser = userMessage.trim().takeIf { it.isNotEmpty() } ?: return null
        val cast = characters.mapNotNull { character ->
            val name = character.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val role = character.role.trim().ifEmpty { "main" }
            StudyStoryCharacter(
                id = character.id.trim().ifEmpty { role },
                name = name,
                role = role,
                description = character.description.trim(),
                imageId = character.imageId?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        val cleanedHistory = history.mapNotNull { item ->
            val line = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            StudyPlayHistoryMessage(
                speaker = item.speaker,
                text = line,
                description = item.description.trim(),
            )
        }
        return copy(
            text = normalizedText,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
            scenarioDescription = normalizedScenario,
            userMessage = normalizedUser,
            history = cleanedHistory,
            characters = cast,
        )
    }
}

/** Immediate Play-tab turn payload; poll [imageId] for the cover image. */
@Serializable
data class StudyPlayTurnResponse(
    /** Short scene beat for this reply (UI language). */
    val description: String,
    /** Other character dialogue in the study language. */
    val reply: String,
    val imageId: String,
)

/**
 * Ask the coach why a Write-tab attempt does not match the expected sentence.
 *
 * [languageTag] is the learner UI language for the feedback text.
 * [textLanguageTag] is the study/source language of [expected] and [attempt].
 */
@Serializable
data class StudyWriteCheckRequest(
    val expected: String,
    val attempt: String,
    val textLanguageTag: String,
    val languageTag: String,
) {
    fun normalizedOrNull(): StudyWriteCheckRequest? {
        val normalizedExpected = expected.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedAttempt = attempt.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            expected = normalizedExpected,
            attempt = normalizedAttempt,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
        )
    }
}

/** Coaching note for an incorrect (or near-miss) Write-tab attempt. */
@Serializable
data class StudyWriteCheckResponse(
    /** True only when attempt matches expected after light normalization. */
    val correct: Boolean,
    /** Short explanation in the learner UI language; empty when correct. */
    val feedback: String = "",
)

/**
 * Express-tab check: does the learner's attempt convey [meaning] in the study language?
 *
 * [meaning] is shown in the learner UI language ([languageTag]).
 * [attempt] should be written in the study/source language ([textLanguageTag]).
 * [referenceText] is an optional known good sentence in the study language (not required
 * for a match — semantic meaning is enough).
 */
@Serializable
data class StudyExpressCheckRequest(
    val meaning: String,
    val attempt: String,
    val textLanguageTag: String,
    val languageTag: String,
    val referenceText: String = "",
) {
    fun normalizedOrNull(): StudyExpressCheckRequest? {
        val normalizedMeaning = meaning.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedAttempt = attempt.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            meaning = normalizedMeaning,
            attempt = normalizedAttempt,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
            referenceText = referenceText.trim(),
        )
    }
}

/** Judgment for an Express-tab production attempt. */
@Serializable
data class StudyExpressCheckResponse(
    /** True when the attempt correctly expresses the prompted meaning. */
    val correct: Boolean,
    /** Short coaching note in the learner UI language; empty when correct. */
    val feedback: String = "",
)

/**
 * Ask the model for a fresh batch of Express practice sentences using related vocabulary.
 *
 * [vocabulary] are study-language words/phrases from the session.
 * [previousSentences] are already-seen study-language lines to avoid repeating.
 * Returned [StudyExampleSentence.text] is study language; [StudyExampleSentence.translation]
 * is the learner UI language prompt.
 */
@Serializable
data class StudyExpressGenerateRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val vocabulary: List<String> = emptyList(),
    val previousSentences: List<String> = emptyList(),
) {
    fun normalizedOrNull(): StudyExpressGenerateRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            text = normalizedText,
            textLanguageTag = Language.fromTag(textLanguageTag).tag,
            languageTag = Language.fromTag(languageTag).tag,
            vocabulary = vocabulary.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            previousSentences = previousSentences.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

/** Batch of Express practice prompts (typically 10). */
@Serializable
data class StudyExpressGenerateResponse(
    val sentences: List<StudyExampleSentence>,
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
                textLanguageTag = Language.normalizeContentTag(session.textLanguageTag),
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

/** Paged study sessions from other devices, newest first. */
@Serializable
data class CommunityStudySessionsResponse(
    val items: List<SavedStudySession>,
    val nextCursor: String? = null,
)
