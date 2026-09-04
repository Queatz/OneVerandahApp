package apap.oneverandah.chat

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyDissectSentenceResponse
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressCheckResponse
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyExpressGenerateResponse
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayHistoryMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlayScenariosRequest
import apap.oneverandah.api.StudyPlayScenariosResponse
import apap.oneverandah.api.StudyPlayTurnRequest
import apap.oneverandah.api.StudyPlayTurnResponse
import apap.oneverandah.api.StudyWriteCheckRequest
import apap.oneverandah.api.StudyWriteCheckResponse
import apap.oneverandah.api.StudyPlaygroundImageResponse
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySentenceImageResponse
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudyStoryCharactersResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.api.StudyWordImageResponse
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

data class StudyCharactersGenerationRequest(
    val text: String,
    val textLanguageTag: String,
)

data class StudyPlayScenariosGenerationRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val characters: List<StudyStoryCharacter> = emptyList(),
)

/** Scenario design from the model before image jobs are attached. */
data class StudyPlayScenarioDesign(
    val description: String,
    val openingLine: String? = null,
)

data class StudyPlayTurnGenerationRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val scenarioDescription: String,
    val userMessage: String,
    val history: List<StudyPlayHistoryMessage> = emptyList(),
    val characters: List<StudyStoryCharacter> = emptyList(),
)

data class StudyPlayTurnDesign(
    val description: String,
    val reply: String,
)

data class StudyWriteCheckGenerationRequest(
    val expected: String,
    val attempt: String,
    val textLanguageTag: String,
    val languageTag: String,
)

data class StudyWriteFeedback(
    val feedback: String,
)

data class StudyExpressCheckGenerationRequest(
    val meaning: String,
    val attempt: String,
    val textLanguageTag: String,
    val languageTag: String,
    val referenceText: String = "",
)

data class StudyExpressJudgment(
    val correct: Boolean,
    val feedback: String,
)

data class StudyExpressGenerateGenerationRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val vocabulary: List<String> = emptyList(),
    val previousSentences: List<String> = emptyList(),
)

/** Designed cast member before portrait jobs are attached. */
data class StudyCharacterDesign(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
)

/**
 * Generates and stores a study image; returns a public URL.
 * [referenceImageUrls] are optional character (or other) identity references for R2I.
 */
fun interface StudyPlaygroundImageGenerator {
    suspend fun generate(prompt: String, referenceImageUrls: List<String>): String
}

interface StudyAiClient {
    /**
     * Pulls verbatim study-language material out of mixed chat/coaching text.
     * Default keeps the original so tests and lighter fakes stay unchanged.
     */
    suspend fun extractStudyText(request: StudyGenerationRequest): String = request.text.trim()

    suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse
    suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence
    suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest): StudyWordPlaygroundJudgment
    suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest): List<StudyCharacterDesign>
    suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest): List<StudyPlayScenarioDesign>
    suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest): StudyPlayTurnDesign
    suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest): StudyWriteFeedback
    suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest): StudyExpressJudgment
    suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest): List<StudyExampleSentence>
}

class StudyService(
    private val aiClient: StudyAiClient,
    private val imageGenerator: StudyPlaygroundImageGenerator? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(StudyService::class.java)
    /** Shared async job store for playground comics, character portraits, and sentence visuals. */
    private val studyImages = ConcurrentHashMap<String, StudyPlaygroundImageResponse>()

    /** Fast skeleton: language + sentences + tokens (no full dissections). */
    suspend fun skeleton(rawRequest: StudySessionRequest): StudySessionResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study text must not be blank" }
        val generationRequest = StudyGenerationRequest(
            text = request.text,
            languageTag = request.languageTag,
        )
        val extracted = aiClient.extractStudyText(generationRequest).trim().ifEmpty { request.text }
        val generated = aiClient.generateSkeleton(
            generationRequest.copy(text = extracted),
        )
        return generated.normalizedSkeleton(sourceText = extracted)
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
        // Pre-generate an expressive word visual for every dissectable token.
        val tokensWithImages = sentence.tokens.map { token -> attachWordImageJob(token) }
        return StudyDissectSentenceResponse(
            sentenceText = sentence.text,
            tokens = tokensWithImages,
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
                "This usage does not sound natural yet."
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
     * Designs a main (and optional secondary) character for the full study text,
     * starts async reference-portrait jobs, and returns the cast for session persistence.
     */
    suspend fun storyCharacters(rawRequest: StudyStoryCharactersRequest): StudyStoryCharactersResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study story characters require non-blank text"
        }
        val designed = runCatching {
            aiClient.designStoryCharacters(
                StudyCharactersGenerationRequest(
                    text = request.text,
                    textLanguageTag = request.textLanguageTag,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study character design failed: {}", error.message)
            emptyList()
        }
        val cast = normalizeCharacterDesigns(designed).ifEmpty { defaultStoryCharacters(request.text) }
        val withJobs = cast.map { character ->
            val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
            enqueueStudyImage(
                imageId = imageId,
                prompt = characterPortraitPrompt(character),
                referenceImageUrls = emptyList(),
                logLabel = "character portrait (${character.name})",
            )
            StudyStoryCharacter(
                id = character.id,
                name = character.name,
                role = character.role,
                description = character.description,
                imageId = imageId,
                imageUrl = null,
            )
        }
        return StudyStoryCharactersResponse(characters = withJobs)
    }

    /**
     * Starts an async expressive visual for one study sentence.
     * Poll [playgroundImage] (shared job store) with the returned image id.
     * Pass character portrait URLs on [StudySentenceImageRequest.characters] for story continuity.
     */
    suspend fun sentenceImage(rawRequest: StudySentenceImageRequest): StudySentenceImageResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study sentence image requires non-blank sentence text"
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        val referenceUrls = request.characters.mapNotNull { it.imageUrl?.trim()?.takeIf(String::isNotEmpty) }
        enqueueStudyImage(
            imageId = imageId,
            prompt = sentenceVisualPrompt(
                sentence = request.sentenceText,
                fullText = request.fullText,
                sentenceIndex = request.sentenceIndex,
                sentenceCount = request.sentenceCount,
                characters = request.characters,
            ),
            referenceImageUrls = referenceUrls,
            logLabel = "sentence visual",
        )
        return StudySentenceImageResponse(imageId = imageId)
    }

    /**
     * Starts an async expressive visual for one dissected word/phrase.
     * Poll [playgroundImage] with the returned image id, then persist on the token.
     */
    suspend fun wordImage(rawRequest: StudyWordImageRequest): StudyWordImageResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study word image requires non-blank word text"
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        enqueueStudyImage(
            imageId = imageId,
            prompt = wordVisualPrompt(
                word = request.word,
                contextualMeaning = request.contextualMeaning,
            ),
            referenceImageUrls = emptyList(),
            logLabel = "word visual",
        )
        return StudyWordImageResponse(imageId = imageId)
    }

    /**
     * Generates four related role-play scenario starters and starts async cover images.
     * Poll [playgroundImage] for each scenario's [StudyPlayScenario.imageId].
     */
    suspend fun playScenarios(rawRequest: StudyPlayScenariosRequest): StudyPlayScenariosResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study play scenarios require non-blank text"
        }
        val designed = runCatching {
            aiClient.generatePlayScenarios(
                StudyPlayScenariosGenerationRequest(
                    text = request.text,
                    textLanguageTag = request.textLanguageTag,
                    languageTag = request.languageTag,
                    characters = request.characters,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study play scenario generation failed: {}", error.message)
            emptyList()
        }
        val cleaned = normalizePlayScenarioDesigns(designed)
        val defaults = defaultPlayScenarios(request.text, request.languageTag)
        val designs = (cleaned + defaults)
            .distinctBy { it.description.trim().lowercase() }
            .take(4)
            .let { merged -> if (merged.size < 4) defaults.take(4) else merged }
        val scenarios = designs.map { design ->
            val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
            val referenceUrls = request.characters.mapNotNull {
                it.imageUrl?.trim()?.takeIf(String::isNotEmpty)
            }
            enqueueStudyImage(
                imageId = imageId,
                prompt = playScenarioVisualPrompt(
                    description = design.description,
                    openingLine = design.openingLine,
                    fullText = request.text,
                    characters = request.characters,
                ),
                referenceImageUrls = referenceUrls,
                logLabel = "play scenario cover",
            )
            StudyPlayScenario(
                id = imageId,
                description = design.description,
                openingLine = design.openingLine,
                imageId = imageId,
                imageUrl = null,
            )
        }
        require(scenarios.size == 4) { "Play tab requires exactly four scenarios" }
        return StudyPlayScenariosResponse(scenarios = scenarios)
    }

    /**
     * Continues a Play-tab conversation: AI reply + async cover image for the new beat.
     */
    suspend fun playTurn(rawRequest: StudyPlayTurnRequest): StudyPlayTurnResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study play turn requires non-blank text, scenario, and user message"
        }
        val designed = aiClient.generatePlayTurn(
            StudyPlayTurnGenerationRequest(
                text = request.text,
                textLanguageTag = request.textLanguageTag,
                languageTag = request.languageTag,
                scenarioDescription = request.scenarioDescription,
                userMessage = request.userMessage,
                history = request.history,
                characters = request.characters,
            ),
        )
        val description = designed.description.trim().ifBlank { request.scenarioDescription }
        val reply = designed.reply.trim().ifBlank {
            throw IllegalArgumentException("Play turn reply must not be blank")
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        val referenceUrls = request.characters.mapNotNull {
            it.imageUrl?.trim()?.takeIf(String::isNotEmpty)
        }
        enqueueStudyImage(
            imageId = imageId,
            prompt = playScenarioVisualPrompt(
                description = description,
                openingLine = reply,
                fullText = request.text,
                characters = request.characters,
            ),
            referenceImageUrls = referenceUrls,
            logLabel = "play turn cover",
        )
        return StudyPlayTurnResponse(
            description = description,
            reply = reply,
            imageId = imageId,
        )
    }

    /**
     * Explains why a Write-tab attempt is not an exact match of the expected sentence.
     * Exact-match correctness is decided here with light whitespace normalization.
     */
    suspend fun writeCheck(rawRequest: StudyWriteCheckRequest): StudyWriteCheckResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study write check requires non-blank expected and attempt text"
        }
        if (writeAttemptMatches(request.expected, request.attempt)) {
            return StudyWriteCheckResponse(correct = true, feedback = "")
        }
        val designed = runCatching {
            aiClient.evaluateWriteAttempt(
                StudyWriteCheckGenerationRequest(
                    expected = request.expected,
                    attempt = request.attempt,
                    textLanguageTag = request.textLanguageTag,
                    languageTag = request.languageTag,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study write check failed: {}", error.message)
            StudyWriteFeedback(feedback = "")
        }
        val feedback = designed.feedback.trim().ifBlank {
            defaultWriteFeedback(request.languageTag)
        }
        return StudyWriteCheckResponse(correct = false, feedback = feedback)
    }

    /**
     * Judges whether an Express-tab attempt correctly expresses the prompted meaning
     * in the study language. Exact wording is not required — semantic success is enough.
     */
    suspend fun expressCheck(rawRequest: StudyExpressCheckRequest): StudyExpressCheckResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study express check requires non-blank meaning and attempt text"
        }
        // Fast path: learner typed the known reference sentence (when present).
        if (request.referenceText.isNotBlank() && writeAttemptMatches(request.referenceText, request.attempt)) {
            return StudyExpressCheckResponse(correct = true, feedback = "")
        }
        val judgment = runCatching {
            aiClient.evaluateExpressAttempt(
                StudyExpressCheckGenerationRequest(
                    meaning = request.meaning,
                    attempt = request.attempt,
                    textLanguageTag = request.textLanguageTag,
                    languageTag = request.languageTag,
                    referenceText = request.referenceText,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study express check failed: {}", error.message)
            StudyExpressJudgment(correct = false, feedback = "")
        }
        if (judgment.correct) {
            return StudyExpressCheckResponse(correct = true, feedback = "")
        }
        val feedback = judgment.feedback.trim().ifBlank {
            defaultExpressFeedback(request.languageTag)
        }
        return StudyExpressCheckResponse(correct = false, feedback = feedback)
    }

    /**
     * Generates a fresh batch of Express practice sentences (target + UI translation)
     * grounded in the study text vocabulary. Endless mode calls this when the queue is empty.
     */
    suspend fun expressGenerate(rawRequest: StudyExpressGenerateRequest): StudyExpressGenerateResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study express generate requires non-blank source text"
        }
        val generated = runCatching {
            aiClient.generateExpressSentences(
                StudyExpressGenerateGenerationRequest(
                    text = request.text,
                    textLanguageTag = request.textLanguageTag,
                    languageTag = request.languageTag,
                    vocabulary = request.vocabulary,
                    previousSentences = request.previousSentences,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study express generate failed: {}", error.message)
            emptyList()
        }
        val cleaned = normalizeExpressSentences(generated)
        val sentences = cleaned.ifEmpty {
            defaultExpressSentences(
                sourceText = request.text,
                textLanguageTag = request.textLanguageTag,
                languageTag = request.languageTag,
                vocabulary = request.vocabulary,
            )
        }
        require(sentences.isNotEmpty()) { "Express generate must return at least one sentence" }
        return StudyExpressGenerateResponse(sentences = sentences)
    }

    /** Current comic/sentence/character-image job status (shared store). */
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
            referenceImageUrls = emptyList(),
            logLabel = "playground comic",
        )
    }

    /** Attach a fresh async word-image job when the token is dissectable and has no URL yet. */
    private fun attachWordImageJob(token: StudyToken): StudyToken {
        if (!token.isDissectable) {
            return token.copy(imageId = null, imageUrl = null)
        }
        val existingUrl = token.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (existingUrl != null) {
            return token.copy(
                imageId = token.imageId?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = existingUrl,
            )
        }
        val imageId = token.imageId?.trim()?.takeIf { it.isNotEmpty() }
            ?: idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        // Re-enqueue when the URL is still missing (new dissect or lost in-memory job).
        enqueueStudyImage(
            imageId = imageId,
            prompt = wordVisualPrompt(
                word = token.text,
                contextualMeaning = token.contextualMeaning,
            ),
            referenceImageUrls = emptyList(),
            logLabel = "word visual",
        )
        return token.copy(imageId = imageId, imageUrl = null)
    }

    private fun enqueueStudyImage(
        imageId: String,
        prompt: String,
        referenceImageUrls: List<String>,
        logLabel: String,
    ) {
        val generator = imageGenerator
        if (generator == null) {
            log.warn("Study {} skipped for {}: image generator is not configured", logLabel, imageId)
            studyImages[imageId] = StudyPlaygroundImageResponse(
                imageId = imageId,
                status = StudyPlaygroundImageStatus.FAILED,
            )
            return
        }
        val refs = referenceImageUrls.map { it.trim() }.filter { it.isNotEmpty() }
        studyImages[imageId] = StudyPlaygroundImageResponse(
            imageId = imageId,
            status = StudyPlaygroundImageStatus.PENDING,
        )
        scope.launch {
            val startedAt = System.currentTimeMillis()
            studyImages[imageId] = StudyPlaygroundImageResponse(
                imageId = imageId,
                status = StudyPlaygroundImageStatus.GENERATING,
            )
            log.info(
                "Study {} started for {} (refs={})",
                logLabel,
                imageId,
                refs.size,
            )
            val result = runCatching { generator.generate(prompt, refs) }
                .recoverCatching { error ->
                    // Reference-to-image is slower/flakier (re-host + multimodal).
                    // Fall back to text-only so sentence panels still complete.
                    if (refs.isEmpty()) throw error
                    log.warn(
                        "Study {} with {} ref(s) failed for {}: {}. Retrying without references.",
                        logLabel,
                        refs.size,
                        imageId,
                        error.message,
                    )
                    generator.generate(prompt, emptyList())
                }
            val elapsedMs = System.currentTimeMillis() - startedAt
            result
                .onSuccess { url ->
                    log.info(
                        "Study {} completed for {} in {}ms (urlHost={})",
                        logLabel,
                        imageId,
                        elapsedMs,
                        runCatching { java.net.URI(url).host }.getOrNull() ?: "?",
                    )
                    studyImages[imageId] = StudyPlaygroundImageResponse(
                        imageId = imageId,
                        status = StudyPlaygroundImageStatus.COMPLETED,
                        imageUrl = url,
                    )
                }
                .onFailure { error ->
                    log.error(
                        "Study {} generation failed for {} after {}ms: {}",
                        logLabel,
                        imageId,
                        elapsedMs,
                        error.message,
                        error,
                    )
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
        append("Expressive storybook art style single or double panel illustration for language learning. ")
        append("Focus word/phrase: \"$word\". ")
        append("Learner sentence: \"$sentence\". ")
        append(sceneGuidance)
        append(' ')
        append("Art style: $STUDY_STORY_ART_STYLE. ")
        append(
            "Warm natural scene in storytelling vibe, clear character emotion, " +
                "dynamic composition, no captions or letters, no border, no watermark, high quality square image.",
        )
    }
}

/** Shared art direction so all study/catalog images stay in one expressive storybook world. */
internal const val STUDY_STORY_ART_STYLE: String =
    "expressive storybook art"

/** Prompt for a single dissected word/phrase visual (word + contextual meaning). */
internal fun wordVisualPrompt(word: String, contextualMeaning: String = ""): String = buildString {
    append("Create an image to clearly illustrate the following in a helpful way. ")
    appendLine()
    append("**$word** ")
    appendLine()
    val meaning = contextualMeaning.trim()
    if (meaning.isNotEmpty()) {
        append("$meaning. ")
    }
    appendLine()
    append("Style: $STUDY_STORY_ART_STYLE. ")
    appendLine()
    append(
        "Absolutely no text, no letters, no numbers, no captions, no signs with writing, no logos, " +
            "no speech bubbles, no border, no framing, no watermark, high quality square image.",
    )
}

/** Character reference portrait used as a named identity anchor for later panels. */
internal fun characterPortraitPrompt(character: StudyCharacterDesign): String = buildString {
    val roleLabel = when (character.role.lowercase()) {
        "secondary" -> "secondary supporting character"
        else -> "main protagonist"
    }
    append("Character reference portrait for a continuous illustrated short story. ")
    append("This image is ONLY a clean identity reference of one person. ")
    append("Name: ${character.name}. Role: $roleLabel. ")
    append("Appearance: ${character.description}. ")
    append("Show a clear front three-quarter view, head-to-waist or full body, plain soft neutral background, ")
    append("face and clothing highly readable, natural standing or gentle pose, no props that hide the face. ")
    append("Art style: $STUDY_STORY_ART_STYLE. ")
    append(
        "Absolutely no text, no letters, no numbers, no captions, no name labels, no speech bubbles, " +
            "no watermark, no border, no collage, high quality square image.",
    )
}

/** Prompt for the Study-tab expressive visual attached to each source sentence. */
internal fun sentenceVisualPrompt(
    sentence: String,
    fullText: String = "",
    sentenceIndex: Int = 0,
    sentenceCount: Int = 1,
    characters: List<StudyStoryCharacter> = emptyList(),
): String = buildString {
    val panelNumber = sentenceIndex.coerceAtLeast(0) + 1
    val totalPanels = sentenceCount.coerceAtLeast(1)
    append("Expressive single-panel story illustration for language learners. ")
    append("This is panel $panelNumber of $totalPanels in one continuous short story — keep the same people, wardrobe, and art style as the rest of the story. ")
    if (fullText.isNotBlank()) {
        append("Full story text for continuity: \"$fullText\". ")
    }
    append("This panel's beat / sentence to depict: \"$sentence\". ")

    // Only characters with portrait URLs are attached as reference images (same order).
    val refCharacters = characters.filter { !it.imageUrl.isNullOrBlank() }
    if (refCharacters.isNotEmpty()) {
        append("Named character reference images are attached in order. Match their identity exactly: ")
        refCharacters.forEachIndexed { index, character ->
            val roleLabel = if (character.role.equals("secondary", ignoreCase = true)) "secondary" else "main"
            append("Image ${index + 1} is $roleLabel character \"${character.name}\"")
            if (character.description.isNotBlank()) {
                append(" (${character.description})")
            }
            append(". ")
        }
        append("Keep face, age, hair, skin, body type, and clothing consistent with those references. ")
    } else if (characters.isNotEmpty()) {
        append("Feature these named characters consistently: ")
        characters.forEach { character ->
            val roleLabel = if (character.role.equals("secondary", ignoreCase = true)) "secondary" else "main"
            append("$roleLabel character \"${character.name}\"")
            if (character.description.isNotBlank()) {
                append(" — ${character.description}")
            }
            append(". ")
        }
    } else {
        append("Invent one clear main character and keep them emotionally readable. ")
    }

    append("Show a vivid story moment that captures the meaning of this sentence with clear emotion and natural body language. ")
    append("Setting: warm everyday life in Vietnam or Russia, whichever fits the text best. ")
    append("Art style: $STUDY_STORY_ART_STYLE. ")
    append(
        "Absolutely no text, no letters, no numbers, no captions, no signs with writing, no logos, " +
            "no speech bubbles, no border, no watermark, high quality square image.",
    )
}

/** Backward-compatible overload used by older call sites/tests. */
internal fun sentenceVisualPrompt(sentence: String): String =
    sentenceVisualPrompt(
        sentence = sentence,
        fullText = "",
        sentenceIndex = 0,
        sentenceCount = 1,
        characters = emptyList(),
    )

/** Cover image for a Play-tab scenario starter or conversation beat. */
internal fun playScenarioVisualPrompt(
    description: String,
    openingLine: String? = null,
    fullText: String = "",
    characters: List<StudyStoryCharacter> = emptyList(),
): String = buildString {
    append("Expressive single-panel story illustration for an interactive language role-play. ")
    append("This is a continuous short story world — keep the same people, wardrobe, and art style. ")
    if (fullText.isNotBlank()) {
        append("Source study text for continuity: \"$fullText\". ")
    }
    append("Scene setup to depict: \"$description\". ")
    if (!openingLine.isNullOrBlank()) {
        append("Spoken moment / dialogue vibe (do not render text): \"$openingLine\". ")
    }
    val refCharacters = characters.filter { !it.imageUrl.isNullOrBlank() }
    if (refCharacters.isNotEmpty()) {
        append("Named character reference images are attached in order. Match their identity exactly: ")
        refCharacters.forEachIndexed { index, character ->
            val roleLabel = if (character.role.equals("secondary", ignoreCase = true)) "secondary" else "main"
            append("Image ${index + 1} is $roleLabel character \"${character.name}\"")
            if (character.description.isNotBlank()) {
                append(" (${character.description})")
            }
            append(". ")
        }
        append("Keep face, age, hair, skin, body type, and clothing consistent with those references. ")
    } else if (characters.isNotEmpty()) {
        append("Feature these named characters consistently: ")
        characters.forEach { character ->
            val roleLabel = if (character.role.equals("secondary", ignoreCase = true)) "secondary" else "main"
            append("$roleLabel character \"${character.name}\"")
            if (character.description.isNotBlank()) {
                append(" — ${character.description}")
            }
            append(". ")
        }
    } else {
        append("Invent clear characters that fit the scene and keep them emotionally readable. ")
    }
    append("Show a vivid conversational moment with clear emotion and natural body language. ")
    append("Setting: warm everyday life in Vietnam or Russia, whichever fits the text best. ")
    append("Art style: $STUDY_STORY_ART_STYLE. ")
    append(
        "Absolutely no text, no letters, no numbers, no captions, no signs with writing, no logos, " +
            "no speech bubbles, no border, no watermark, high quality square image.",
    )
}

internal fun normalizePlayScenarioDesigns(designs: List<StudyPlayScenarioDesign>): List<StudyPlayScenarioDesign> {
    val cleaned = designs.mapNotNull { design ->
        val description = design.description.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val opening = design.openingLine?.trim()?.takeIf { it.isNotEmpty() }
        StudyPlayScenarioDesign(description = description, openingLine = opening)
    }
    return cleaned.take(4)
}

internal fun defaultPlayScenarios(sourceText: String, languageTag: String): List<StudyPlayScenarioDesign> {
    val looksVietnamese = sourceText.any { it in '\u00C0'..'\u1EF9' } ||
        sourceText.contains("Tôi", ignoreCase = true) ||
        sourceText.contains("bạn", ignoreCase = true)
    val looksRussian = sourceText.any { it in '\u0400'..'\u04FF' }
    val ui = Language.fromTag(languageTag).tag
    return when {
        looksVietnamese || ui == "vi" -> listOf(
            StudyPlayScenarioDesign(
                description = "A friendly neighbor greets you near the elevator.",
                openingLine = "Chào bạn, hôm nay bạn đi đâu vậy?",
            ),
            StudyPlayScenarioDesign(
                description = "A market vendor offers you something fresh.",
                openingLine = null,
            ),
            StudyPlayScenarioDesign(
                description = "A classmate sits down and starts a short chat.",
                openingLine = "Mình hỏi chút được không?",
            ),
            StudyPlayScenarioDesign(
                description = "You meet someone on a quiet evening walk.",
                openingLine = null,
            ),
        )
        looksRussian || ui == "ru" -> listOf(
            StudyPlayScenarioDesign(
                description = "A neighbor greets you by the building entrance.",
                openingLine = "Здравствуйте! Как ваши дела?",
            ),
            StudyPlayScenarioDesign(
                description = "A shop assistant offers quick help.",
                openingLine = null,
            ),
            StudyPlayScenarioDesign(
                description = "A classmate starts a short conversation after class.",
                openingLine = "Можно вас спросить?",
            ),
            StudyPlayScenarioDesign(
                description = "Someone approaches you on a park path.",
                openingLine = null,
            ),
        )
        else -> listOf(
            StudyPlayScenarioDesign(
                description = "A neighbor greets you near the lobby.",
                openingLine = "Hi there — heading out?",
            ),
            StudyPlayScenarioDesign(
                description = "A shopkeeper offers a friendly suggestion.",
                openingLine = null,
            ),
            StudyPlayScenarioDesign(
                description = "A classmate sits nearby and starts chatting.",
                openingLine = "Mind if I ask something quick?",
            ),
            StudyPlayScenarioDesign(
                description = "Someone approaches you on a quiet trail.",
                openingLine = null,
            ),
        )
    }
}

internal fun writeAttemptMatches(expected: String, actual: String): Boolean {
    fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
    return normalize(expected) == normalize(actual)
}

internal fun defaultWriteFeedback(languageTag: String): String =
    when (Language.fromTag(languageTag).tag) {
        "vi" -> "Chưa khớp câu mẫu. Hãy so lại từng từ và dấu câu."
        "zh" -> "还没有完全一致。请再核对每个词和标点。"
        "ja" -> "まだ一致していません。語句と句読点をもう一度確認してください。"
        "ko" -> "아직 일치하지 않습니다. 단어와 문장 부호를 다시 확인해 보세요."
        "ru" -> "Пока не совпадает. Сверьте каждое слово и знаки препинания."
        else -> "Not an exact match yet. Compare each word and the punctuation."
    }

internal fun defaultExpressFeedback(languageTag: String): String =
    when (Language.fromTag(languageTag).tag) {
        "vi" -> "Chưa diễn đạt đúng ý. Hãy thử lại với từ vựng liên quan."
        "zh" -> "还没有准确表达这个意思。请用相关词汇再试一次。"
        "ja" -> "まだ意味を正しく表せていません。関連語彙でもう一度試してください。"
        "ko" -> "아직 의미를 정확히 표현하지 못했습니다. 관련 어휘로 다시 시도해 보세요."
        "ru" -> "Пока смысл передан неточно. Попробуйте снова с похожей лексикой."
        else -> "That doesn't quite express the meaning yet. Try again with related words."
    }

internal fun normalizeExpressSentences(sentences: List<StudyExampleSentence>): List<StudyExampleSentence> {
    val cleaned = sentences.mapNotNull { item ->
        val text = item.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val translation = item.translation.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        StudyExampleSentence(text = text, translation = translation)
    }
    return cleaned
        .distinctBy { it.text.lowercase() to it.translation.lowercase() }
        .take(10)
}

internal fun defaultExpressSentences(
    sourceText: String,
    textLanguageTag: String,
    languageTag: String,
    vocabulary: List<String>,
): List<StudyExampleSentence> {
    val study = Language.fromTag(textLanguageTag).tag
    val ui = Language.fromTag(languageTag).tag
    val words = vocabulary.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
    if (words.isNotEmpty() && study == "vi") {
        return words.take(3).flatMapIndexed { index, word ->
            listOf(
                StudyExampleSentence(
                    text = "Tôi muốn nói về $word.",
                    translation = when (ui) {
                        "vi" -> "Tôi muốn nói về $word."
                        "zh" -> "我想谈谈${word}。"
                        "ja" -> "${word}について話したいです。"
                        "ko" -> "${word}에 대해 이야기하고 싶어요."
                        "ru" -> "Я хочу поговорить о «$word»."
                        else -> "I want to talk about $word."
                    },
                ),
                StudyExampleSentence(
                    text = "Bạn hiểu $word không?",
                    translation = when (ui) {
                        "vi" -> "Bạn hiểu $word không?"
                        "zh" -> "你明白${word}吗？"
                        "ja" -> "${word}は分かりますか？"
                        "ko" -> "${word} 이해하시나요?"
                        "ru" -> "Вы понимаете «$word»?"
                        else -> "Do you understand $word?"
                    },
                ),
            ).take(if (index == 0) 2 else 1)
        }.take(10)
    }
    // Fallback: split source text into short practice lines.
    val chunks = sourceText
        .split(Regex("(?<=[.!?。！？…])\\s+|\\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(10)
    if (chunks.isNotEmpty()) {
        return chunks.map { line ->
            StudyExampleSentence(
                text = line,
                translation = when (ui) {
                    "vi" -> "Hãy diễn đạt ý của câu này."
                    "zh" -> "请用学习语言表达这句话的意思。"
                    "ja" -> "この文の意味を学習言語で表してください。"
                    "ko" -> "이 문장의 의미를 학습 언어로 표현해 보세요."
                    "ru" -> "Выразите смысл этой фразы на изучаемом языке."
                    else -> "Express the meaning of this sentence."
                },
            )
        }
    }
    return listOf(
        StudyExampleSentence(
            text = sourceText.trim().ifBlank { "Hello." },
            translation = when (ui) {
                "vi" -> "Hãy diễn đạt ý này."
                "zh" -> "请表达这个意思。"
                "ja" -> "この意味を表してください。"
                "ko" -> "이 의미를 표현해 보세요."
                "ru" -> "Выразите этот смысл."
                else -> "Express this meaning."
            },
        ),
    )
}



internal fun normalizeCharacterDesigns(designs: List<StudyCharacterDesign>): List<StudyCharacterDesign> {
    val cleaned = designs.mapNotNull { design ->
        val name = design.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val role = when {
            design.role.equals("secondary", ignoreCase = true) -> "secondary"
            else -> "main"
        }
        val description = design.description.trim().ifEmpty {
            "Expressive everyday person with distinctive hair, clothing, and face"
        }
        StudyCharacterDesign(id = role, name = name, role = role, description = description)
    }
    if (cleaned.isEmpty()) return emptyList()

    val main = (cleaned.firstOrNull { it.role == "main" } ?: cleaned.first())
        .copy(id = "main", role = "main")
    val secondary = cleaned
        .firstOrNull { it.role == "secondary" && !it.name.equals(main.name, ignoreCase = true) }
        ?.copy(id = "secondary", role = "secondary")
        ?: cleaned.firstOrNull {
            it.role != "main" && !it.name.equals(main.name, ignoreCase = true) && it !== cleaned.firstOrNull { c -> c.role == "main" }
        }?.copy(id = "secondary", role = "secondary")
        ?: cleaned.drop(1).firstOrNull { !it.name.equals(main.name, ignoreCase = true) }
            ?.copy(id = "secondary", role = "secondary")

    return listOfNotNull(main, secondary)
}

internal fun defaultStoryCharacters(sourceText: String): List<StudyCharacterDesign> {
    val looksVietnamese = sourceText.any { it in '\u00C0'..'\u1EF9' } ||
        sourceText.contains("Tôi", ignoreCase = true) ||
        sourceText.contains("bạn", ignoreCase = true)
    val looksRussian = sourceText.any { it in '\u0400'..'\u04FF' }
    return when {
        looksVietnamese -> listOf(
            StudyCharacterDesign(
                id = "main",
                name = "Lan",
                role = "main",
                description = "Young Vietnamese woman in her mid-20s, warm brown eyes, long dark hair loosely tied, simple linen blouse and soft earth-tone trousers, kind expressive face",
            ),
            StudyCharacterDesign(
                id = "secondary",
                name = "Minh",
                role = "secondary",
                description = "Young Vietnamese man around 30, short neat black hair, friendly open face, light shirt and dark trousers, slightly taller companion",
            ),
        )
        looksRussian -> listOf(
            StudyCharacterDesign(
                id = "main",
                name = "Anya",
                role = "main",
                description = "Young Russian woman in her mid-20s, light brown hair in a soft braid, fair freckled skin, knit sweater and simple coat, bright curious eyes",
            ),
            StudyCharacterDesign(
                id = "secondary",
                name = "Ivan",
                role = "secondary",
                description = "Young Russian man around 30, short sandy hair, gentle smile, wool scarf and practical jacket, calm supportive presence",
            ),
        )
        else -> listOf(
            StudyCharacterDesign(
                id = "main",
                name = "Alex",
                role = "main",
                description = "Approachable young adult with medium skin tone, short wavy dark hair, cozy sweater and jeans, open expressive face that reads clearly in every panel",
            ),
        )
    }
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
                // Keep any already-persisted word visuals when reopening a session.
                imageId = if (isLexical) token.imageId?.trim()?.takeIf { it.isNotEmpty() } else null,
                imageUrl = if (isLexical) token.imageUrl?.trim()?.takeIf { it.isNotEmpty() } else null,
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
    val cast = characters.mapNotNull { character ->
        val name = character.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        StudyStoryCharacter(
            id = character.id.trim().ifEmpty { character.role.trim().ifEmpty { "main" } },
            name = name,
            role = character.role.trim().ifEmpty { "main" },
            description = character.description.trim(),
            imageId = character.imageId?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
    val play = playScenarios.mapNotNull { scenario ->
        val description = scenario.description.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val opening = scenario.openingLine?.trim()?.takeIf { it.isNotEmpty() }
        val imageId = scenario.imageId?.trim()?.takeIf { it.isNotEmpty() }
        val imageUrl = scenario.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val messages = scenario.messages.mapNotNull { message ->
            val text = message.text.trim()
            val msgDescription = message.description.trim()
            val msgImageId = message.imageId?.trim()?.takeIf { it.isNotEmpty() }
            val msgImageUrl = message.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (text.isEmpty() && msgDescription.isEmpty() && msgImageId == null && msgImageUrl == null) {
                return@mapNotNull null
            }
            StudyPlayChatMessage(
                speaker = message.speaker,
                text = text,
                description = msgDescription,
                imageId = msgImageId,
                imageUrl = msgImageUrl,
            )
        }
        StudyPlayScenario(
            id = scenario.id.trim().ifEmpty { imageId.orEmpty() }.ifEmpty { return@mapNotNull null },
            description = description,
            openingLine = opening,
            imageId = imageId,
            imageUrl = imageUrl,
            messages = messages,
        )
    }
    return StudySessionResponse(
        text = sourceText,
        textLanguageTag = languageTag,
        sentences = sentences,
        characters = cast,
        playScenarios = play,
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
            imageId = match.imageId?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = match.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (looksLikePunctuationOnly(expectedText) || !cleaned.hasDissectionContent()) {
            cleaned.copy(
                status = StudyTokenStatus.PLAIN,
                literalMeaning = "",
                register = "",
                contextualMeaning = "",
                nearSynonyms = emptyList(),
                exampleSentences = emptyList(),
                imageId = null,
                imageUrl = null,
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
