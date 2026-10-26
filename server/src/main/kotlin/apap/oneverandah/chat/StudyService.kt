package apap.oneverandah.chat

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyDissectSentenceResponse
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamBeatScore
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExamSceneRequest
import apap.oneverandah.api.StudyExamScoreRequest
import apap.oneverandah.api.StudyExamScoreResponse
import apap.oneverandah.api.STUDY_EXAM_BEAT_COUNT
import apap.oneverandah.api.STUDY_EXAM_ROUND_COUNT
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressCheckResponse
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyExpressGenerateResponse
import apap.oneverandah.api.StudyListenCheckRequest
import apap.oneverandah.api.StudyListenCheckResponse
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
import apap.oneverandah.api.StudyIntentResponse
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTranslateRequest
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudyStoryCharactersResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.api.StudyWordImageResponse
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.StudyWordPlaygroundResponse
import apap.oneverandah.images.PromptFactory
import apap.oneverandah.model.Language
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

data class StudyGenerationRequest(
    val text: String,
    val languageTag: String,
)

/** Source material for an English movie-still description used by image prompts. */
data class StudySceneGenerationRequest(
    val sentence: String,
    val fullText: String = "",
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 1,
    val characters: List<StudyStoryCharacter> = emptyList(),
    val word: String = "",
    val contextualMeaning: String = "",
    val exampleTranslation: String = "",
    val scenarioDescription: String = "",
    val spokenLine: String = "",
    val natural: Boolean? = null,
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

data class StudyExamSceneGenerationRequest(
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val sentences: List<StudySentence>,
    val characters: List<StudyStoryCharacter>,
)

data class StudyExamBeatJudgmentRequest(
    val setting: String,
    val beat: StudyExamBeat,
    val transcript: String,
    val textLanguageTag: String,
    val languageTag: String,
)

data class StudyExamJudgment(
    val points: Int,
    val feedback: String,
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

data class StudyListenCheckGenerationRequest(
    val sourceText: String,
    val expectedMeaning: String,
    val attempt: String,
    val textLanguageTag: String,
    val languageTag: String,
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

    /** Translates lesson text into the requested study language; lightweight clients keep it unchanged. */
    suspend fun translateStudyText(request: StudyTranslateRequest): String = request.text.trim()

    /**
     * Turns a learner prompt into the study text they meant (e.g. generate
     * example sentences). Default keeps the original so tests and fakes stay
     * unchanged; exact-mode clients skip this entirely.
     */
    suspend fun generateStudyIntent(request: StudyGenerationRequest): String = request.text.trim()

    suspend fun generateStudySessionName(text: String, languageTag: String): String = text.trim().take(80)

    /**
     * Turns a study sentence (or a word's first example) into a detailed English
     * movie-still description. Default is empty so tests and lighter fakes stay
     * unchanged; [StudyService] then uses a source-free English fallback.
     */
    suspend fun designIntendedScene(request: StudySceneGenerationRequest): String = ""

    suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse
    suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence
    suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest): StudyWordPlaygroundJudgment
    suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest): List<StudyCharacterDesign>
    suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest): List<StudyPlayScenarioDesign>
    suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest): StudyPlayTurnDesign
    suspend fun generateExamScene(request: StudyExamSceneGenerationRequest): StudyExamScene =
        throw UnsupportedOperationException("Study exam scene generation is not supported")

    suspend fun evaluateExamBeat(request: StudyExamBeatJudgmentRequest): StudyExamJudgment =
        StudyExamJudgment(points = 0, feedback = "")
    suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest): StudyWriteFeedback
    suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest): StudyExpressJudgment
    suspend fun evaluateListenAttempt(request: StudyListenCheckGenerationRequest): StudyExpressJudgment =
        StudyExpressJudgment(correct = false, feedback = "")
    suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest): List<StudyExampleSentence>
}

class StudyService(
    private val aiClient: StudyAiClient,
    private val imageGenerator: StudyPlaygroundImageGenerator? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(StudyService::class.java)
    /** Shared async job store for playground stills, character portraits, and sentence visuals. */
    private val studyImages = ConcurrentHashMap<String, StudyPlaygroundImageResponse>()

    /**
     * Parse-mode prep: generate the study text the learner asked for, which the
     * client then sends through [skeleton] as usual.
     */
    suspend fun intendedStudyText(rawRequest: StudySessionRequest): StudyIntentResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study text must not be blank" }
        val composed = aiClient.generateStudyIntent(
            StudyGenerationRequest(
                text = request.text,
                languageTag = request.languageTag,
            ),
        ).trim().ifEmpty { request.text }
        return StudyIntentResponse(text = composed)
    }

    /** Translate existing lesson text before creating a study session for it. */
    suspend fun translateStudyText(rawRequest: StudyTranslateRequest): StudyIntentResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study translation requires text and a target language" }
        val translated = aiClient.translateStudyText(request).trim().ifEmpty { request.text }
        return StudyIntentResponse(text = translated)
    }

    /** Fast skeleton: language + sentences + tokens (no full dissections). */
    suspend fun skeleton(rawRequest: StudySessionRequest): StudySessionResponse =
        withContext(NonCancellable) {
            val request = requireNotNull(rawRequest.normalizedOrNull()) { "Study text must not be blank" }
            val generationRequest = StudyGenerationRequest(
                text = request.text,
                languageTag = request.languageTag,
            )
            val extracted = aiClient.extractStudyText(generationRequest).trim().ifEmpty { request.text }
            val generated = aiClient.generateSkeleton(
                generationRequest.copy(text = extracted),
            )
            generated.normalizedSkeleton(sourceText = extracted)
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
     * async image job. Poll [playgroundImage] with the returned image id.
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
            contextualMeaning = request.contextualMeaning.ifBlank { request.literalMeaning },
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
            prompt = {
                val scene = resolveIntendedScene(
                    StudySceneGenerationRequest(
                        sentence = request.sentenceText,
                        fullText = request.fullText,
                        sentenceIndex = request.sentenceIndex,
                        sentenceCount = request.sentenceCount,
                        characters = request.characters,
                    ),
                )
                sentenceVisualPrompt(
                    intendedScene = scene,
                    sentenceIndex = request.sentenceIndex,
                    sentenceCount = request.sentenceCount,
                    characters = request.characters,
                )
            },
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
            prompt = wordImagePrompt(
                word = request.word,
                contextualMeaning = request.contextualMeaning,
                exampleSentences = request.exampleSentences,
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
        val cleaned = normalizePlayScenarioDesigns(designed).map { design ->
            design.copy(
                openingLine = design.openingLine?.takeUnless {
                    request.textLanguageTag != "en" && looksPrimarilyEnglish(it)
                },
            )
        }
        val defaults = defaultPlayScenarios(request.textLanguageTag)
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
                prompt = {
                    val scene = resolveIntendedScene(
                        StudySceneGenerationRequest(
                            sentence = "",
                            fullText = request.text,
                            characters = request.characters,
                            scenarioDescription = design.description,
                            spokenLine = design.openingLine.orEmpty(),
                        ),
                    )
                    playScenarioVisualPrompt(
                        intendedScene = scene,
                        characters = request.characters,
                    )
                },
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
        require(request.textLanguageTag == "en" || !looksPrimarilyEnglish(reply)) {
            "Play turn reply must use the study language"
        }
        val imageId = idGenerator().trim().ifBlank { UUID.randomUUID().toString() }
        val referenceUrls = request.characters.mapNotNull {
            it.imageUrl?.trim()?.takeIf(String::isNotEmpty)
        }
        enqueueStudyImage(
            imageId = imageId,
            prompt = {
                val scene = resolveIntendedScene(
                    StudySceneGenerationRequest(
                        sentence = "",
                        fullText = request.text,
                        characters = request.characters,
                        scenarioDescription = description,
                        spokenLine = reply,
                    ),
                )
                playScenarioVisualPrompt(
                    intendedScene = scene,
                    characters = request.characters,
                )
            },
            referenceImageUrls = referenceUrls,
            logLabel = "play turn cover",
        )
        return StudyPlayTurnResponse(
            description = description,
            reply = reply,
            imageId = imageId,
        )
    }

    suspend fun examScene(rawRequest: StudyExamSceneRequest): StudyExamScene {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study exam scene requires non-blank text and at least one sentence"
        }
        val characters = normalizeExamCharacters(request.session.characters)
        val generated = runCatching {
            aiClient.generateExamScene(
                StudyExamSceneGenerationRequest(
                    text = request.session.text,
                    textLanguageTag = request.session.textLanguageTag,
                    languageTag = request.languageTag,
                    sentences = request.session.sentences,
                    characters = characters,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study exam scene generation failed: {}", error.message, error)
            null
        }
        val normalized = generated?.let { scene ->
            runCatching { normalizeExamScene(scene, request.session.sentences, characters) }
                .onFailure { error ->
                    log.warn("Study exam scene normalization failed: {}", error.message, error)
                }
                .getOrNull()
        }
        if (normalized == null) {
            log.warn("Study exam scene falling back to a local script: generated={} beats={}", generated != null, generated?.beats?.size)
        }
        return normalized ?: fallbackExamScene(
            sentences = request.session.sentences,
            characters = characters,
            languageTag = request.languageTag,
        )
    }

    suspend fun scoreExam(
        scene: StudyExamScene,
        request: StudyExamScoreRequest,
        textLanguageTag: String,
        languageTag: String,
    ): StudyExamScoreResponse {
        val answersByBeat = request.answers
            .mapNotNull { answer -> answer.beatId.trim().takeIf(String::isNotEmpty)?.let { it to answer.transcript.trim() } }
            .toMap()
        val beatScores = scene.beats.map { beat ->
            val transcript = answersByBeat[beat.id].orEmpty()
            if (transcript.isBlank()) {
                StudyExamBeatScore(beatId = beat.id, points = 0, feedback = "")
            } else {
                val judgment = runCatching {
                    aiClient.evaluateExamBeat(
                        StudyExamBeatJudgmentRequest(
                            setting = scene.setting,
                            beat = beat,
                            transcript = transcript,
                            textLanguageTag = Language.normalizeContentTag(textLanguageTag),
                            languageTag = Language.normalizeLearnerLanguageTag(languageTag),
                        ),
                    )
                }.getOrElse { error ->
                    log.warn("Study exam beat judgment failed: {}", error.message)
                    StudyExamJudgment(points = 0, feedback = "")
                }
                StudyExamBeatScore(
                    beatId = beat.id,
                    points = judgment.points.coerceIn(0, 2),
                    feedback = judgment.feedback.trim(),
                )
            }
        }
        val grade = StudyExamScoring.grade(
            beats = scene.beats,
            beatScores = beatScores,
            scoredAt = clock(),
            feedback = StudyExamScoring.feedback(languageTag),
        )
        return StudyExamScoreResponse(grade = grade, beats = beatScores)
    }

    private fun normalizeExamScene(
        generated: StudyExamScene,
        sentences: List<StudySentence>,
        characters: List<StudyStoryCharacter>,
    ): StudyExamScene {
        val sourceSentences = sentences.map { it.text.trim() }.filter(String::isNotEmpty)
        require(sourceSentences.isNotEmpty()) { "Study exam requires at least one source sentence" }
        require(generated.setting.isNotBlank()) { "Study exam scene setting must not be blank" }
        require(generated.beats.size == STUDY_EXAM_BEAT_COUNT) {
            "Study exam scene must contain exactly $STUDY_EXAM_BEAT_COUNT beats"
        }
        require(generated.beats.withIndex().all { (index, beat) ->
            val expected = if (index % 2 == 0) StudyExamBeatKind.LISTEN else StudyExamBeatKind.SPEAK
            beat.kind == expected
        }) { "Study exam rounds must alternate listen then speak" }
        val knownCharacters = characters.associateBy { it.id }
        val fallbackSpeaker = characters.first()
        val usedSentences = mutableSetOf<Int>()
        val beats = generated.beats.mapIndexed { index, beat ->
            val round = index / 2 + 1
            val id = if (beat.kind == StudyExamBeatKind.LISTEN) "listen-$round" else "speak-$round"
            val sourceSentence = resolveExamSourceSentence(
                rawSource = beat.sourceSentence,
                line = beat.line,
                sourceSentences = sourceSentences,
                used = usedSentences,
            )
            val speaker = knownCharacters[beat.speakerId.trim()] ?: fallbackSpeaker
            val line = if (beat.kind == StudyExamBeatKind.LISTEN) {
                beat.line?.trim()?.takeIf(String::isNotEmpty)
                    ?: throw IllegalArgumentException("Study exam listen beat missing line")
            } else {
                null
            }
            require(beat.prompt.isNotBlank()) { "Study exam beat prompt must not be blank" }
            require(beat.expectedMeaning.isNotBlank()) { "Study exam beat meaning must not be blank" }
            beat.copy(
                id = id,
                speakerId = speaker.id,
                speakerName = speaker.name,
                line = line,
                prompt = beat.prompt.trim(),
                expectedMeaning = beat.expectedMeaning.trim(),
                sourceSentence = sourceSentence,
            )
        }
        require(beats.map { it.id }.distinct().size == beats.size) { "Study exam beat ids must be unique" }
        val speakPrompts = beats.filter { it.kind == StudyExamBeatKind.SPEAK }.map { it.prompt }
        val outline = generated.outline.map { it.trim() }.filter(String::isNotEmpty)
            .takeIf { it.size == STUDY_EXAM_ROUND_COUNT }
            ?: speakPrompts
        require(outline.size == STUDY_EXAM_ROUND_COUNT) { "Study exam outline must contain 10 rounds" }
        val scene = generated.copy(
            id = generated.id.trim().ifEmpty { idGenerator().trim().ifEmpty { UUID.randomUUID().toString() } },
            setting = generated.setting.trim(),
            outline = outline,
            beats = beats,
        )
        require(scene.isTenRoundExam()) { "Study exam scene must be 10 alternating rounds" }
        return scene
    }

    private fun normalizeExamCharacters(characters: List<StudyStoryCharacter>): List<StudyStoryCharacter> {
        val known = characters
            .mapNotNull { character ->
                val id = character.id.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val name = character.name.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                if (character.role != "main" && character.role != "secondary") return@mapNotNull null
                character.copy(id = id, name = name)
            }
            .sortedBy { if (it.role == "main") 0 else 1 }
            .distinctBy { it.id }
            .take(2)
        return known.ifEmpty {
            listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Ari",
                    role = "main",
                    description = "A friendly local who helps the learner in the situation.",
                ),
            )
        }
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

    /** Checks a Listen-tab translation; exact normalized meanings avoid an AI call. */
    suspend fun listenCheck(rawRequest: StudyListenCheckRequest): StudyListenCheckResponse {
        val request = requireNotNull(rawRequest.normalizedOrNull()) {
            "Study listen check requires non-blank source, expected meaning, and attempt text"
        }
        if (writeAttemptMatches(request.expectedMeaning, request.attempt)) {
            return StudyListenCheckResponse(correct = true, feedback = "")
        }
        val judgment = runCatching {
            aiClient.evaluateListenAttempt(
                StudyListenCheckGenerationRequest(
                    sourceText = request.sourceText,
                    expectedMeaning = request.expectedMeaning,
                    attempt = request.attempt,
                    textLanguageTag = request.textLanguageTag,
                    languageTag = request.languageTag,
                ),
            )
        }.getOrElse { error ->
            log.warn("Study listen check failed: {}", error.message)
            StudyExpressJudgment(correct = false, feedback = "")
        }
        if (judgment.correct) return StudyListenCheckResponse(correct = true, feedback = "")
        val feedback = judgment.feedback.trim().ifBlank {
            defaultExpressFeedback(request.languageTag)
        }
        return StudyListenCheckResponse(correct = false, feedback = feedback)
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

    /** Current playground/sentence/character-image job status (shared store). */
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
        contextualMeaning: String,
    ) {
        enqueueStudyImage(
            imageId = imageId,
            prompt = {
                val scene = resolveIntendedScene(
                    StudySceneGenerationRequest(
                        sentence = sentence,
                        word = word,
                        contextualMeaning = contextualMeaning,
                        natural = natural,
                    ),
                )
                playgroundComicPrompt(intendedScene = scene, natural = natural)
            },
            referenceImageUrls = emptyList(),
            logLabel = "playground visual",
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
            prompt = wordImagePrompt(
                word = token.text,
                contextualMeaning = token.contextualMeaning,
                exampleSentences = token.exampleSentences,
            ),
            referenceImageUrls = emptyList(),
            logLabel = "word visual",
        )
        return token.copy(imageId = imageId, imageUrl = null)
    }

    private fun wordImagePrompt(
        word: String,
        contextualMeaning: String,
        exampleSentences: List<StudyExampleSentence>,
    ): suspend () -> String = {
        val example = exampleSentences.firstOrNull { it.text.trim().isNotEmpty() }
        val scene = resolveIntendedScene(
            StudySceneGenerationRequest(
                sentence = example?.text.orEmpty(),
                word = word,
                contextualMeaning = contextualMeaning,
                exampleTranslation = example?.translation.orEmpty(),
            ),
        )
        wordVisualPrompt(intendedScene = scene)
    }

    private suspend fun resolveIntendedScene(request: StudySceneGenerationRequest): String {
        val designed = runCatching { aiClient.designIntendedScene(request) }
            .onFailure { error ->
                log.warn("Study intended scene failed: {}", error.message)
            }
            .getOrDefault("")
            .trim()
        val sanitized = sanitizeIntendedScene(
            scene = designed,
            forbidden = listOf(
                request.sentence,
                request.word,
                request.fullText,
                request.scenarioDescription,
                request.spokenLine,
            ),
        )
        return sanitized.takeIf { it.isNotBlank() && looksPrimarilyEnglish(it) }
            ?: fallbackIntendedScene(
                contextualMeaning = request.contextualMeaning,
                exampleTranslation = request.exampleTranslation,
            )
    }

    private fun enqueueStudyImage(
        imageId: String,
        prompt: String,
        referenceImageUrls: List<String>,
        logLabel: String,
    ) {
        enqueueStudyImage(
            imageId = imageId,
            prompt = { prompt },
            referenceImageUrls = referenceImageUrls,
            logLabel = logLabel,
        )
    }

    private fun enqueueStudyImage(
        imageId: String,
        prompt: suspend () -> String,
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
            val builtPrompt = runCatching { prompt() }.getOrElse { error ->
                log.error(
                    "Study {} prompt failed for {}: {}",
                    logLabel,
                    imageId,
                    error.message,
                    error,
                )
                studyImages[imageId] = StudyPlaygroundImageResponse(
                    imageId = imageId,
                    status = StudyPlaygroundImageStatus.FAILED,
                )
                return@launch
            }
            val result = runCatching { generator.generate(builtPrompt, refs) }
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
                    generator.generate(builtPrompt, emptyList())
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

internal fun playgroundComicPrompt(intendedScene: String, natural: Boolean): String =
    PromptFactory.forPlayground(intendedScene = intendedScene, natural = natural)

/** Shared art direction so all study/catalog images stay in one realistic expressive world. */
internal const val STUDY_STORY_ART_STYLE: String = PromptFactory.ART_STYLE

/** Prompt for a single dissected word/phrase visual from an English intended scene. */
internal fun wordVisualPrompt(intendedScene: String): String =
    PromptFactory.forWord(intendedScene = intendedScene)

/** Character reference portrait used as a named identity anchor for later scenes. */
internal fun characterPortraitPrompt(character: StudyCharacterDesign): String =
    PromptFactory.forCharacterPortrait(
        name = character.name,
        role = character.role,
        description = character.description,
    )

/** Prompt for the Study-tab expressive visual attached to each source sentence. */
internal fun sentenceVisualPrompt(
    intendedScene: String,
    sentenceIndex: Int = 0,
    sentenceCount: Int = 1,
    characters: List<StudyStoryCharacter> = emptyList(),
): String = PromptFactory.forSentence(
    intendedScene = intendedScene,
    sentenceIndex = sentenceIndex,
    sentenceCount = sentenceCount,
    characters = characters.toImageCharacters(),
)

/** Cover image for a Play-tab scenario starter or conversation beat. */
internal fun playScenarioVisualPrompt(
    intendedScene: String,
    characters: List<StudyStoryCharacter> = emptyList(),
): String = PromptFactory.forPlayScenario(
    intendedScene = intendedScene,
    characters = characters.toImageCharacters(),
)

private fun List<StudyStoryCharacter>.toImageCharacters(): List<PromptFactory.CharacterRef> =
    map { character ->
        PromptFactory.CharacterRef(
            name = character.name,
            role = character.role,
            description = character.description,
            hasReferenceImage = !character.imageUrl.isNullOrBlank(),
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

internal fun defaultPlayScenarios(languageTag: String): List<StudyPlayScenarioDesign> {
    val studyLanguageTag = languageTag.trim().lowercase().substringBefore('-')
    val openingLines = when (studyLanguageTag) {
        "vi" -> listOf("Chào bạn, hôm nay bạn đi đâu vậy?", null, "Mình hỏi chút được không?", null)
        "zh" -> listOf("你好！你今天要去哪里？", null, "我可以问你一件事吗？", null)
        "ja" -> listOf("こんにちは！今日はどこへ行くの？", null, "ちょっと聞いてもいい？", null)
        "ko" -> listOf("안녕하세요! 오늘 어디 가세요?", null, "잠깐 여쭤봐도 될까요?", null)
        "ru" -> listOf("Здравствуйте! Как ваши дела?", null, "Можно вас спросить?", null)
        "es" -> listOf("¡Hola! ¿Adónde vas hoy?", null, "¿Puedo preguntarte algo?", null)
        "de" -> listOf("Hallo! Wohin gehst du heute?", null, "Darf ich dich kurz etwas fragen?", null)
        "en" -> listOf("Hi there — heading out?", null, "Mind if I ask something quick?", null)
        else -> List(4) { null }
    }
    val descriptions = listOf(
        "A neighbor greets you near the lobby.",
        "A shopkeeper offers a friendly suggestion.",
        "A classmate sits nearby and starts chatting.",
        "Someone approaches you on a quiet trail.",
    )
    return descriptions.mapIndexed { index, description ->
        StudyPlayScenarioDesign(description = description, openingLine = openingLines[index])
    }
}

internal fun resolveExamSourceSentence(
    rawSource: String,
    line: String?,
    sourceSentences: List<String>,
    used: MutableSet<Int>,
): String {
    val candidates = listOf(rawSource.trim(), line.orEmpty().trim()).filter(String::isNotEmpty)
    val exactIndex = sourceSentences.indexOfFirst { sentence ->
        candidates.any { it.equals(sentence, ignoreCase = true) }
    }
    if (exactIndex >= 0) {
        used += exactIndex
        return sourceSentences[exactIndex]
    }
    val containedIndex = sourceSentences.indices
        .filter { index ->
            val sentence = sourceSentences[index]
            sentence.length >= 2 && candidates.any { it.contains(sentence, ignoreCase = true) }
        }
        .maxByOrNull { sourceSentences[it].length }
    if (containedIndex != null) {
        used += containedIndex
        return sourceSentences[containedIndex]
    }
    if (sourceSentences.size == 1) return sourceSentences.first()
    val unused = sourceSentences.indices.firstOrNull { it !in used } ?: 0
    used += unused
    return sourceSentences[unused]
}

internal fun fallbackExamScene(
    sentences: List<StudySentence>,
    characters: List<StudyStoryCharacter>,
    languageTag: String,
): StudyExamScene {
    val sourceSentences = sentences.map { it.text.trim() }.filter(String::isNotEmpty)
    require(sourceSentences.isNotEmpty()) { "Study exam fallback requires a source sentence" }
    val speaker = characters.firstOrNull() ?: StudyStoryCharacter(
        id = "main",
        name = "Ari",
        role = "main",
        description = "A friendly local who helps the learner in the situation.",
    )
    val tasks = fallbackExamTasks(languageTag, speaker.name)
    val beats = List(STUDY_EXAM_BEAT_COUNT) { index ->
        val round = index / 2
        val sentence = sourceSentences[round % sourceSentences.size]
        val kind = if (index % 2 == 0) StudyExamBeatKind.LISTEN else StudyExamBeatKind.SPEAK
        StudyExamBeat(
            id = if (kind == StudyExamBeatKind.LISTEN) "listen-${round + 1}" else "speak-${round + 1}",
            kind = kind,
            speakerId = speaker.id,
            speakerName = speaker.name,
            line = if (kind == StudyExamBeatKind.LISTEN) sentence else null,
            prompt = if (kind == StudyExamBeatKind.LISTEN) {
                fallbackExamListenPrompt(languageTag, speaker.name)
            } else {
                tasks[round]
            },
            expectedMeaning = fallbackExamMeaning(languageTag, sentence),
            sourceSentence = sentence,
        )
    }
    return StudyExamScene(
        id = "exam-fallback",
        setting = fallbackExamSetting(languageTag),
        outline = tasks,
        beats = beats,
    )
}

private fun fallbackExamSetting(languageTag: String): String = when (Language.fromTag(languageTag).tag) {
    "vi" -> "Một cuộc trò chuyện ngắn trong đời thường."
    "zh" -> "一段日常短对话。"
    "ja" -> "日常の短い会話です。"
    "ko" -> "짧은 일상 대화입니다."
    "ru" -> "Короткий повседневный разговор."
    "es" -> "Una conversación breve de la vida diaria."
    "de" -> "Ein kurzes Alltagsgespräch."
    "my" -> "နေ့စဉ်ဘဝ စကားဝိုင်းတိုတစ်ခု။"
    else -> "A short everyday conversation."
}

private fun fallbackExamListenPrompt(languageTag: String, speakerName: String): String =
    when (Language.fromTag(languageTag).tag) {
        "vi" -> "$speakerName vừa nói gì?"
        "zh" -> "$speakerName 刚才是什么意思？"
        "ja" -> "${speakerName}は何と言いましたか？"
        "ko" -> "${speakerName}는 무슨 뜻으로 말했나요?"
        "ru" -> "Что имел в виду $speakerName?"
        "es" -> "¿Qué quiso decir $speakerName?"
        "de" -> "Was meinte $speakerName?"
        "my" -> "$speakerName က ဘာကို ဆိုလိုတာလဲ။"
        else -> "What did $speakerName mean?"
    }

private fun fallbackExamTasks(languageTag: String, name: String): List<String> {
    val lines = when (Language.fromTag(languageTag).tag) {
        "vi" -> listOf(
            "Giúp $name gọi món",
            "Đưa cho $name thứ vừa gọi",
            "Hỏi $name thấy thế nào",
            "Mời $name đi tiếp",
            "Nhận xét khung cảnh quanh $name",
            "Xác nhận kế hoạch với $name",
            "Cảm ơn $name",
            "Hỏi $name thêm một câu",
            "Đề nghị giúp $name",
            "Chào tạm biệt $name",
        )
        "zh" -> listOf(
            "帮${name}点单",
            "把东西递给${name}",
            "问${name}感觉怎么样",
            "邀请${name}继续走",
            "评论${name}身边的情景",
            "和${name}确认接下来的安排",
            "谢谢${name}",
            "再问${name}一个问题",
            "提出帮${name}的忙",
            "和${name}道别",
        )
        "ja" -> listOf(
            "${name}の注文を手伝う",
            "${name}に頼んだものを渡す",
            "${name}に感想を聞く",
            "${name}を次の場所へ誘う",
            "${name}の周りの様子について話す",
            "${name}と予定を確認する",
            "${name}に礼を言う",
            "${name}にもう一つ質問する",
            "${name}を手伝うと申し出る",
            "${name}に別れを告げる",
        )
        "ko" -> listOf(
            "${name}의 주문을 돕기",
            "${name}에게 주문한 것을 건네기",
            "${name}에게 어땠는지 묻기",
            "${name}를 계속 걷자고 초대하기",
            "${name} 주변 상황에 대해 말하기",
            "${name}와 계획을 확인하기",
            "${name}에게 고마워하기",
            "${name}에게 질문을 하나 더 하기",
            "${name}를 돕겠다고 제안하기",
            "${name}에게 작별 인사하기",
        )
        "ru" -> listOf(
            "Помогите $name сделать заказ",
            "Передайте $name заказанное",
            "Спросите $name, как это было",
            "Пригласите $name продолжить",
            "Прокомментируйте, что происходит вокруг $name",
            "Подтвердите план с $name",
            "Поблагодарите $name",
            "Задайте $name ещё один вопрос",
            "Предложите помочь $name",
            "Попрощайтесь с $name",
        )
        "es" -> listOf(
            "Ayuda a $name a pedir",
            "Entrégale a $name lo pedido",
            "Pregúntale a $name qué le pareció",
            "Invita a $name a seguir",
            "Comenta lo que pasa alrededor de $name",
            "Confirma el plan con $name",
            "Agradece a $name",
            "Hazle a $name una pregunta más",
            "Ofrécete a ayudar a $name",
            "Despídete de $name",
        )
        "de" -> listOf(
            "Hilf $name beim Bestellen",
            "Gib $name das Bestellte",
            "Frag $name, wie es war",
            "Lade $name ein weiterzugehen",
            "Kommentiere, was um $name herum passiert",
            "Bestätige den Plan mit $name",
            "Bedanke dich bei $name",
            "Stell $name noch eine Frage",
            "Biete $name Hilfe an",
            "Verabschiede dich von $name",
        )
        "my" -> listOf(
            "$name မှာယူတာကို ကူညီပါ",
            "$name ကို မှာထားတာ ပေးပါ",
            "$name ကို ဘယ်လိုလဲ မေးပါ",
            "$name ကို ဆက်သွားဖို့ ဖိတ်ပါ",
            "$name ပတ်ဝန်းကျင်အကြောင်း ပြောပါ",
            "$name နဲ့ အစီအစဉ်ကို အတည်ပြုပါ",
            "$name ကို ကျေးဇူးတင်ပါ",
            "$name ကို နောက်ထပ်တစ်ခု မေးပါ",
            "$name ကို ကူညီမယ်လို့ ကမ်းလှမ်းပါ",
            "$name ကို နှုတ်ဆက်ပါ",
        )
        else -> listOf(
            "Help $name place an order",
            "Give $name what was ordered",
            "Ask $name how it was",
            "Invite $name to keep going",
            "Comment on what is happening around $name",
            "Confirm the plan with $name",
            "Thank $name",
            "Ask $name one more question",
            "Offer to help $name",
            "Say goodbye to $name",
        )
    }
    require(lines.size == STUDY_EXAM_ROUND_COUNT) { "Fallback exam outline must contain 10 rounds" }
    return lines
}

private fun fallbackExamMeaning(languageTag: String, sentence: String): String = when (Language.fromTag(languageTag).tag) {
    "vi" -> "Dùng đúng ý của câu: $sentence"
    "zh" -> "表达这句的意思：$sentence"
    "ja" -> "この文の意味を使う：$sentence"
    "ko" -> "이 문장의 의미를 사용하세요: $sentence"
    "ru" -> "Используйте смысл фразы: $sentence"
    "es" -> "Usa el significado de esta frase: $sentence"
    "de" -> "Verwende die Bedeutung dieses Satzes: $sentence"
    "my" -> "ဒီဝါကျရဲ့ အဓိပ္ပာယ်ကို သုံးပါ။ $sentence"
    else -> "Use the meaning of this study sentence: $sentence"
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
        textLanguageTag = Language.normalizeContentTag(textLanguageTag),
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

/** Drops source-language sentence/word copies so they cannot leak into the image prompt. */
internal fun sanitizeIntendedScene(scene: String, forbidden: List<String>): String {
    var cleaned = scene.trim()
    forbidden
        .map { it.trim() }
        .filter { it.length >= 2 }
        .sortedByDescending { it.length }
        .forEach { needle ->
            cleaned = cleaned.replace(needle, " ", ignoreCase = true)
        }
    return cleaned.replace(Regex("\\s+"), " ").trim()
}

internal fun looksPrimarilyEnglish(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty()) return false
    return letters.all { it in 'A'..'Z' || it in 'a'..'z' }
}

/** English movie-still fallback that never quotes the source sentence or word. */
internal fun fallbackIntendedScene(
    contextualMeaning: String = "",
    exampleTranslation: String = "",
): String {
    val visualIntent = listOf(exampleTranslation, contextualMeaning)
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && looksPrimarilyEnglish(it) }
    return if (visualIntent != null) {
        "A cinematic movie still of $visualIntent, with clear emotion, natural body language, rich everyday environment, and dramatic natural lighting."
    } else {
        "A vivid cinematic everyday moment with clear character emotion, natural body language, rich environmental detail, and warm movie-still lighting."
    }
}
