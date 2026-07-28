package apap.oneverandah.chat

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyDissectSentenceResponse
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyPlaygroundImageResponse
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySentenceImageResponse
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.StudyWordPlaygroundResponse
import apap.oneverandah.model.Language
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

data class StudyGenerationRequest(
    val text: String,
    val languageTag: String,
)

data class StudyDissectGenerationRequest(
    val text: String,
    val sentenceText: String,
    val tokens: List<String>,
    val textLanguageTag: String,
    val languageTag: String,
)

data class StudyWordPlaygroundGenerationRequest(
    val word: String,
    val sentence: String,
    val textLanguageTag: String,
    val languageTag: String,
    val literalMeaning: String = "",
    val contextualMeaning: String = "",
)

data class StudyWordPlaygroundJudgment(
    val natural: Boolean,
    val summary: String,
)

/** Generates and stores a comic-style playground image; returns a public URL. */
fun interface StudyPlaygroundImageGenerator {
    suspend fun generate(prompt: String): String
}

interface StudyAiClient {
    suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse
    suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence
    suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest): StudyWordPlaygroundJudgment
}

class StudyService(
    private val aiClient: StudyAiClient,
    private val imageGenerator: StudyPlaygroundImageGenerator? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(StudyService::class.java)
    /** Shared async job store for playground comics and Study-tab sentence visuals. */
    private val studyImages = ConcurrentHashMap<String, StudyPlaygroundImageResponse>()

    /** Fast skeleton: language + sentences + tokens (no full dissections). */
    suspend fun skeleton(rawRequest: StudySessionRequest): StudySessionResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study text must not be blank" }
        val generated = aiClient.generateSkeleton(
            StudyGenerationRequest(
                text = request.text,
                languageTag = request.languageTag,
            ),
        )
        return generated.normalizedSkeleton(sourceText = request.text)
    }

    /** Full dissection for one skeleton sentence; tokens must match the skeleton order/text. */
    suspend fun dissectSentence(rawRequest: StudyDissectSentenceRequest): StudyDissectSentenceResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study dissect request requires non-blank text, sentence, and tokens"
        }
        val generated = aiClient.dissectSentence(
            StudyDissectGenerationRequest(
                text = request.text,
                sentenceText = request.sentenceText,
                tokens = request.tokens,
                textLanguageTag = request.textLanguageTag,
                languageTag = request.languageTag,
            ),
        )
        val sentence = generated.normalizedDissected(expectedTokens = request.tokens)
        return StudyDissectSentenceResponse(
            sentenceText = sentence.text,
            tokens = sentence.tokens,
        )
    }

    /**
     * Judges whether [rawRequest] uses the target word naturally, then starts an
     * async comic-image job. Poll [playgroundImage] with the returned image id.
     */
    suspend fun playground(rawRequest: StudyWordPlaygroundRequest): StudyWordPlaygroundResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study playground requires a non-blank word and sentence"
        }
        val judgment = aiClient.evaluateWordUsage(
            StudyWordPlaygroundGenerationRequest(
                word = request.word,
                sentence = request.sentence,
                textLanguageTag = request.textLanguageTag,
                languageTag = request.languageTag,
                literalMeaning = request.literalMeaning,
                contextualMeaning = request.contextualMeaning,
            ),
        )
        val summary = judgment.summary.trim().ifBlank {
            if (judgment.natural) {
                "Natural usage."
            } else {
                "This usage does not sound natural."
            }
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        enqueuePlaygroundImage(
            imageId = imageId,
            word = request.word,
            sentence = request.sentence,
            natural = judgment.natural,
        )
        return StudyWordPlaygroundResponse(
            natural = judgment.natural,
            summary = summary,
            imageId = imageId,
        )
    }

    /**
     * Starts an async expressive visual for one study sentence.
     * Poll [playgroundImage] (shared job store) with the returned image id.
     */
    suspend fun sentenceImage(rawRequest: StudySentenceImageRequest): StudySentenceImageResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study sentence image requires non-blank sentence text"
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        enqueueStudyImage(
            imageId = imageId,
            prompt = sentenceVisualPrompt(request.sentenceText),
            logLabel = "sentence visual",
        )
        return StudySentenceImageResponse(imageId = imageId)
    }

    /** Current comic/sentence-image job status (shared store for playground + Study tab). */
    fun playgroundImage(imageId: String): StudyPlaygroundImageResponse? {
        val key = imageId.trim()
        if (key.isEmpty()) return null
        return studyImages[key]
    }

    private fun enqueuePlaygroundImage(
        imageId: String,
        word: String,
        sentence: String,
        natural: Boolean,
    ) {
        enqueueStudyImage(
            imageId = imageId,
            prompt = playgroundComicPrompt(word = word, sentence = sentence, natural = natural),
            logLabel = "playground comic",
        )
    }

    private fun enqueueStudyImage(
        imageId: String,
        prompt: String,
        logLabel: String,
    ) {
        val generator = imageGenerator
        if (generator == null) {
            studyImages[imageId] = StudyPlaygroundImageResponse(
                imageId = imageId,
                status = StudyPlaygroundImageStatus.FAILED,
            )
            return
        }
        studyImages[imageId] = StudyPlaygroundImageResponse(
            imageId = imageId,
            status = StudyPlaygroundImageStatus.PENDING,
        )
        scope.launch {
            studyImages[imageId] = StudyPlaygroundImageResponse(
                imageId = imageId,
                status = StudyPlaygroundImageStatus.GENERATING,
            )
            runCatching { generator.generate(prompt) }
                .onSuccess { url ->
                    studyImages[imageId] = StudyPlaygroundImageResponse(
                        imageId = imageId,
                        status = StudyPlaygroundImageStatus.COMPLETED,
                        imageUrl = url,
                    )
                }
                .onFailure { error ->
                    log.warn("Study {} generation failed for {}: {}", logLabel, imageId, error.message)
                    studyImages[imageId] = StudyPlaygroundImageResponse(
                        imageId = imageId,
                        status = StudyPlaygroundImageStatus.FAILED,
                    )
                }
        }
    }
}

internal fun playgroundComicPrompt(word: String, sentence: String, natural: Boolean): String {
    val sceneGuidance = if (natural) {
        "Depict the learner's sentence as a vivid, natural everyday moment."
    } else {
        "Interpret the intended meaning of the learner's attempt and depict a corrected, natural everyday moment that uses the word correctly."
    }
    return buildString {
        append("Expressive comic-style single or double panel illustration for language learning. ")
        append("Focus word/phrase: \"$word\". ")
        append("Learner sentence: \"$sentence\". ")
        append(sceneGuidance)
        append(' ')
        append(
            "Warm natural scene in Vietnam or Russian (whichever country is most suitable)," +
                "storytelling vibe, clear character emotion, soft ink wash with bold comic outlines, " +
                "dynamic composition, no captions or letters, no border, no watermark, high quality square image." +
                "Use speech bubbles with readable text using the focus word/phrase if it makes sense to.",
        )
    }
}

/** Prompt for the Study-tab expressive visual attached to each source sentence. */
internal fun sentenceVisualPrompt(sentence: String): String = buildString {
    append("Expressive single-panel illustration that vividly captures the meaning of this sentence for language learners. ")
    append("Sentence: \"$sentence\". ")
    append(
        "Warm natural everyday scene (Vietnam or Russia, whichever fits best), clear character emotion, " +
            "storytelling vibe, soft ink wash with bold outlines, dynamic composition, " +
            "no captions, no letters, no speech bubbles, no border, no watermark, high quality square image.",
    )
}

internal fun StudySessionResponse.normalizedSkeleton(sourceText: String): StudySessionResponse {
    val languageTag = Language.fromTag(textLanguageTag).tag
    val sentences = sentences.mapNotNull { sentence ->
        val sentenceText = sentence.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val tokens = sentence.tokens.mapNotNull { token ->
            val tokenText = token.text.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val isLexical = token.status != StudyTokenStatus.PLAIN && !looksLikePunctuationOnly(tokenText)
            StudyToken(
                text = tokenText,
                status = if (isLexical) StudyTokenStatus.PENDING else StudyTokenStatus.PLAIN,
            )
        }
        if (tokens.isEmpty()) return@mapNotNull null
        StudySentence(
            text = sentenceText,
            tokens = tokens,
            imageId = sentence.imageId?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = sentence.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
    require(sentences.isNotEmpty()) { "Study session must contain at least one sentence" }
    return StudySessionResponse(
        text = sourceText,
        textLanguageTag = languageTag,
        sentences = sentences,
    )
}

internal fun StudySentence.normalizedDissected(expectedTokens: List<String>): StudySentence {
    val sentenceText = text.trim().takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Dissected sentence text must not be blank")
    require(expectedTokens.isNotEmpty()) { "Expected token list must not be empty" }

    val byText = tokens
        .mapNotNull { token ->
            val tokenText = token.text.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            tokenText to token
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, group) -> group.toMutableList() }

    val aligned = expectedTokens.map { expectedText ->
        val match = byText[expectedText]?.removeFirstOrNull()
            ?: throw IllegalArgumentException(
                "Dissected tokens must match skeleton order and text (missing \"$expectedText\")",
            )
        val cleaned = StudyToken(
            text = expectedText,
            status = StudyTokenStatus.READY,
            literalMeaning = match.literalMeaning.trim(),
            register = match.register.trim(),
            contextualMeaning = match.contextualMeaning.trim(),
            nearSynonyms = match.nearSynonyms.map(String::trim).filter(String::isNotEmpty).distinct(),
            exampleSentences = match.exampleSentences.mapNotNull { example ->
                val exampleText = example.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StudyExampleSentence(
                    text = exampleText,
                    translation = example.translation.trim(),
                )
            },
        )
        if (looksLikePunctuationOnly(expectedText) || !cleaned.hasDissectionContent()) {
            cleaned.copy(
                status = StudyTokenStatus.PLAIN,
                literalMeaning = "",
                register = "",
                contextualMeaning = "",
                nearSynonyms = emptyList(),
                exampleSentences = emptyList(),
            )
        } else {
            cleaned.copy(status = StudyTokenStatus.READY)
        }
    }

    // Strict order equality: regenerated token texts must equal expected list exactly.
    require(aligned.map { it.text } == expectedTokens) {
        "Dissected tokens must match skeleton order and text"
    }

    return StudySentence(text = sentenceText, tokens = aligned)
}

private fun StudyToken.hasDissectionContent(): Boolean =
    literalMeaning.isNotBlank() ||
        contextualMeaning.isNotBlank() ||
        register.isNotBlank() ||
        nearSynonyms.isNotEmpty() ||
        exampleSentences.isNotEmpty()

/** Heuristic for pure punctuation / symbols when the model omits an explicit plain flag. */
internal fun looksLikePunctuationOnly(text: String): Boolean {
    if (text.isEmpty()) return false
    return text.all { ch ->
        ch.isWhitespace() || (!ch.isLetterOrDigit() && ch.category != CharCategory.OTHER_LETTER)
    }
}
