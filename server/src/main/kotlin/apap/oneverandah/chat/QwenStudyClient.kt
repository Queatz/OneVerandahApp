package apap.oneverandah.chat

import apap.oneverandah.api.STUDY_EXAM_ROUND_COUNT
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTranslateRequest
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.config.QwenChatConfig
import apap.oneverandah.model.Language
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.channels.ClosedChannelException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Strips common markdown fences around model JSON. */
internal fun stripJsonFences(content: String): String =
    content.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

private fun languageName(tag: String): String {
    val normalized = tag.trim().lowercase().substringBefore('-')
    return Language.entries.firstOrNull { it.tag == normalized }?.displayName
        ?: mapOf(
            "ar" to "Arabic",
            "da" to "Danish",
            "el" to "Greek",
            "fi" to "Finnish",
            "fr" to "French",
            "he" to "Hebrew",
            "hi" to "Hindi",
            "id" to "Indonesian",
            "it" to "Italian",
            "km" to "Khmer",
            "lo" to "Lao",
            "nl" to "Dutch",
            "no" to "Norwegian",
            "pl" to "Polish",
            "pt" to "Portuguese",
            "sv" to "Swedish",
            "th" to "Thai",
            "tl" to "Tagalog",
            "tr" to "Turkish",
            "uk" to "Ukrainian",
        )[normalized]
        ?: when (normalized) {
            "my" -> "Burmese"
            else -> tag.trim().ifBlank { normalized }
        }
}

/**
 * Lenient skeleton parser: walks JsonElement so a single bad `sentences[]` entry
 * (e.g. a string instead of an object — production `$.sentences[1]` failure) is
 * skipped instead of failing the whole session.
 */
object QwenStudySkeletonParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudySessionResponse {
        val root = parseRootObject(content)
        val textLanguageTag = root.string("textLanguageTag")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Qwen study skeleton missing textLanguageTag")
        val sentencesArray = root["sentences"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study skeleton missing sentences array")

        val sentences = sentencesArray.mapNotNull { element -> parseSkeletonSentence(element) }
        require(sentences.isNotEmpty()) {
            "Qwen study skeleton must include at least one valid sentence"
        }
        return StudySessionResponse(
            text = "",
            textLanguageTag = textLanguageTag.trim(),
            sentences = sentences,
        )
    }

    private fun parseSkeletonSentence(element: JsonElement): StudySentence? {
        val obj = element as? JsonObject ?: return null
        val text = obj.string("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val tokensArray = obj["tokens"] as? JsonArray ?: return null
        val tokens = tokensArray.mapNotNull { tokenEl -> parseSkeletonToken(tokenEl) }
        if (tokens.isEmpty()) return null
        return StudySentence(text = text, tokens = tokens)
    }

    private fun parseSkeletonToken(element: JsonElement): StudyToken? {
        val obj = element as? JsonObject ?: return null
        val text = obj.string("text")?.takeIf { it.isNotEmpty() } ?: return null
        val explicitPlain = obj.string("status")
            ?.equals("PLAIN", ignoreCase = true) == true
        val status = when {
            explicitPlain || looksLikePunctuationOnly(text) -> StudyTokenStatus.PLAIN
            else -> StudyTokenStatus.PENDING
        }
        return StudyToken(text = text, status = status)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study skeleton is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study skeleton root must be a JSON object")
    }
}

/**
 * Lenient per-sentence dissect parser. Skips non-object token entries; requires
 * at least one valid token with text.
 */
object QwenStudyDissectParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudySentence {
        val root = parseRootObject(content)
        // Accept either a bare sentence object or { sentenceText, tokens } / { text, tokens }.
        val sentenceText = root.string("sentenceText")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("text")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study dissect missing sentence text")
        val tokensArray = root["tokens"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study dissect missing tokens array")
        val tokens = tokensArray.mapNotNull { element -> parseDissectToken(element) }
        require(tokens.isNotEmpty()) {
            "Qwen study dissect must include at least one valid token"
        }
        return StudySentence(text = sentenceText, tokens = tokens)
    }

    private fun parseDissectToken(element: JsonElement): StudyToken? {
        val obj = element as? JsonObject ?: return null
        val text = obj.string("text")?.takeIf { it.isNotEmpty() } ?: return null
        val literal = obj.string("literalMeaning").orEmpty()
        val register = obj.string("register").orEmpty()
        val contextual = obj.string("contextualMeaning").orEmpty()
        val synonyms = obj.stringList("nearSynonyms")
        val examples = obj.exampleSentenceList("exampleSentences")
        val hasContent = literal.isNotBlank() ||
            register.isNotBlank() ||
            contextual.isNotBlank() ||
            synonyms.isNotEmpty() ||
            examples.isNotEmpty()
        val status = when {
            looksLikePunctuationOnly(text) || !hasContent -> StudyTokenStatus.PLAIN
            else -> StudyTokenStatus.READY
        }
        return StudyToken(
            text = text,
            status = status,
            literalMeaning = if (status == StudyTokenStatus.PLAIN) "" else literal,
            register = if (status == StudyTokenStatus.PLAIN) "" else register,
            contextualMeaning = if (status == StudyTokenStatus.PLAIN) "" else contextual,
            nearSynonyms = if (status == StudyTokenStatus.PLAIN) emptyList() else synonyms,
            exampleSentences = if (status == StudyTokenStatus.PLAIN) emptyList() else examples,
        )
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study dissect is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study dissect root must be a JSON object")
    }
}

/** @deprecated Use [QwenStudySkeletonParser] / [QwenStudyDissectParser]. Kept for test migration. */
object QwenStudyReplyParser {
    fun parse(content: String): StudySessionResponse = QwenStudySkeletonParser.parse(content)
}

/** Lenient parser for study-text extraction JSON. */
object QwenStudyExtractParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): String {
        val root = parseRootObject(content)
        val text = root.string("text")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("studyText")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study extract missing text")
        return text
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study extract is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study extract root must be a JSON object")
    }
}

object QwenStudySessionNameParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): String {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study session name is not valid JSON", it) }
        val root = element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study session name root must be a JSON object")
        return root.string("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study session name missing name")
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? {
    val primitive = this[key]?.jsonPrimitive ?: return null
    primitive.contentOrNull?.trim()?.lowercase()?.let { text ->
        when (text) {
            "true", "yes", "y", "1" -> return true
            "false", "no", "n", "0" -> return false
        }
    }
    return runCatching { primitive.boolean }.getOrNull()
}

private fun JsonObject.stringList(key: String): List<String> {
    val el = this[key] ?: return emptyList()
    val array = el as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        runCatching { item.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
    }
}

/**
 * Parses example sentences as objects `{text, translation}` while still accepting
 * legacy plain-string entries (translation defaults to empty).
 */
private fun JsonObject.exampleSentenceList(key: String): List<StudyExampleSentence> {
    val el = this[key] ?: return emptyList()
    val array = el as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        when (item) {
            is JsonObject -> {
                val text = item.string("text")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: item.string("sentence")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val translation = item.string("translation")?.trim().orEmpty()
                StudyExampleSentence(text = text, translation = translation)
            }
            is JsonPrimitive -> {
                val text = item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StudyExampleSentence(text = text, translation = "")
            }
            else -> null
        }
    }
}

/**
 * Lenient playground judgment parser.
 *
 * Accepts common aliases (`isNatural`, `feedback`) so minor model drift does not
 * fail the learner's try-it sentence.
 */
object QwenStudyPlaygroundParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyWordPlaygroundJudgment {
        val root = parseRootObject(content)
        val natural = root.boolean("natural")
            ?: root.boolean("isNatural")
            ?: root.boolean("naturalUsage")
            ?: throw IllegalArgumentException("Qwen study playground missing natural boolean")
        val summary = root.string("summary")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("feedback")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("explanation")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study playground missing summary")
        return StudyWordPlaygroundJudgment(natural = natural, summary = summary)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study playground is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study playground root must be a JSON object")
    }
}

private const val EXAM_SCENE_CHUNK_ROUNDS = 5

internal data class StudyExamScenePlan(
    val id: String,
    val setting: String,
    val outline: List<String>,
)

class DashScopeStudyAiClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) : StudyAiClient {
    private val log = LoggerFactory.getLogger(DashScopeStudyAiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun extractStudyText(request: StudyGenerationRequest): String {
        val userContent = buildString {
            appendLine("Learner language tag: ${request.languageTag}")
            appendLine()
            append(request.text)
        }
        val content = complete(
            systemPrompt = extractStudyTextSystemPrompt(request.languageTag),
            userContent = userContent,
            label = "study extract",
        )
        return QwenStudyExtractParser.parse(content)
    }

    override suspend fun generateStudyIntent(request: StudyGenerationRequest): String {
        val userContent = buildString {
            appendLine("Learner language tag: ${request.languageTag}")
            appendLine()
            append(request.text)
        }
        val content = complete(
            systemPrompt = studyIntentSystemPrompt(request.languageTag),
            userContent = userContent,
            label = "study intent",
            temperature = 0.6,
        )
        return QwenStudyExtractParser.parse(content)
    }

    override suspend fun translateStudyText(request: StudyTranslateRequest): String {
        val sourceLanguage = request.sourceLanguageTag
            ?.let { "The source language is ${languageName(it)} (tag: $it)." }
            ?: "Detect the source language from the passage."
        val content = complete(
            systemPrompt = """
                Translate the supplied lesson passage into ${languageName(request.targetLanguageTag)} (language tag: ${request.targetLanguageTag}).
                $sourceLanguage Preserve the complete meaning and make the result natural for a language learner.
                Return only a JSON object with a single string field named "text" containing the translated passage.
                Do not include explanations, glosses, labels, or any other commentary.
            """.trimIndent(),
            userContent = request.text,
            label = "study translate",
        )
        return QwenStudyExtractParser.parse(content)
    }

    override suspend fun generateStudySessionName(text: String, languageTag: String): String {
        val content = complete(
            systemPrompt = """
                Create a concise study-session title in ${languageName(languageTag)}. Describe what the learner will know how to say after studying the material, not just its general topic. Use a short, natural phrase of at most eight words. Return only a JSON object with a single string field named \"name\".
            """.trimIndent(),
            userContent = text.trim(),
            label = "study session name",
            temperature = 0.4,
        )
        return QwenStudySessionNameParser.parse(content)
    }

    override suspend fun designIntendedScene(request: StudySceneGenerationRequest): String {
        val userContent = buildString {
            if (request.word.isNotBlank()) {
                appendLine("Vocabulary word (never copy this wording into the scene):")
                appendLine(request.word)
                appendLine()
            }
            if (request.contextualMeaning.isNotBlank()) {
                appendLine("Meaning / visual intent (rewrite in English; do not copy non-English wording):")
                appendLine(request.contextualMeaning)
                appendLine()
            }
            if (request.exampleTranslation.isNotBlank()) {
                appendLine("Translation of the example (use as visual intent, rewrite in English if needed):")
                appendLine(request.exampleTranslation)
                appendLine()
            }
            if (request.sentence.isNotBlank()) {
                appendLine("Source sentence whose intended scene you must describe (never copy this wording):")
                appendLine(request.sentence)
                appendLine()
            }
            if (request.fullText.isNotBlank()) {
                appendLine("Full story for continuity only (do not describe the whole story):")
                appendLine(request.fullText)
                appendLine()
            }
            if (request.scenarioDescription.isNotBlank()) {
                appendLine("Play scenario setup to interpret visually (rewrite as a scene; never copy this wording):")
                appendLine(request.scenarioDescription)
                appendLine()
            }
            if (request.spokenLine.isNotBlank()) {
                appendLine("Spoken line for emotional context only (never quote or render it):")
                appendLine(request.spokenLine)
                appendLine()
            }
            request.natural?.let { natural ->
                appendLine(
                    if (natural) {
                        "The learner's usage was judged natural; depict its intended everyday moment."
                    } else {
                        "The learner's usage was judged unnatural; depict the intended meaning as a corrected, natural moment."
                    },
                )
                appendLine()
            }
            if (request.characters.isNotEmpty()) {
                appendLine("Named cast (describe appearance in English; do not write names on anything in the scene):")
                request.characters.forEach { character ->
                    append("- ${character.role} ${character.name}")
                    if (character.description.isNotBlank()) append(": ${character.description}")
                    appendLine()
                }
                appendLine()
            }
            appendLine("This is beat ${request.sentenceIndex.coerceAtLeast(0) + 1} of ${request.sentenceCount.coerceAtLeast(1)}.")
        }
        val content = complete(
            systemPrompt = intendedSceneSystemPrompt(),
            userContent = userContent,
            label = "study intended scene",
            temperature = 0.6,
        )
        return QwenStudySceneParser.parse(content)
    }

    override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
        val content = complete(
            systemPrompt = skeletonSystemPrompt(),
            userContent = request.text,
            label = "study skeleton",
            model = config.chatHeavyModel,
        )
        return QwenStudySkeletonParser.parse(content)
    }

    override suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence {
        val tokenList = request.tokens.joinToString(separator = "\n") { "- $it" }
        val userContent = buildString {
            appendLine("Full source text for context:")
            appendLine(request.text)
            appendLine()
            appendLine("Sentence to dissect:")
            appendLine(request.sentenceText)
            appendLine()
            appendLine("Ordered tokens (return exactly these texts, same order):")
            append(tokenList)
        }
        val content = complete(
            systemPrompt = dissectSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study dissect",
            model = config.chatHeavyModel,
        )
        return QwenStudyDissectParser.parse(content)
    }

    override suspend fun evaluateWordUsage(
        request: StudyWordPlaygroundGenerationRequest,
    ): StudyWordPlaygroundJudgment {
        val userContent = buildString {
            appendLine("Target word/phrase:")
            appendLine(request.word)
            appendLine()
            appendLine("Learner sentence to judge:")
            appendLine(request.sentence)
            if (request.literalMeaning.isNotBlank() || request.contextualMeaning.isNotBlank()) {
                appendLine()
                appendLine("Optional study context:")
                if (request.literalMeaning.isNotBlank()) {
                    appendLine("literalMeaning: ${request.literalMeaning}")
                }
                if (request.contextualMeaning.isNotBlank()) {
                    appendLine("contextualMeaning: ${request.contextualMeaning}")
                }
            }
        }
        val content = complete(
            systemPrompt = playgroundSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study playground",
        )
        return QwenStudyPlaygroundParser.parse(content)
    }

    override suspend fun designStoryCharacters(
        request: StudyCharactersGenerationRequest,
    ): List<StudyCharacterDesign> {
        val userContent = buildString {
            if (request.textLanguageTag.isNotBlank()) {
                appendLine("Source language tag: ${request.textLanguageTag}")
                appendLine()
            }
            appendLine("Full source text:")
            append(request.text)
        }
        val content = complete(
            systemPrompt = storyCharactersSystemPrompt(),
            userContent = userContent,
            label = "study story characters",
        )
        return QwenStudyCharactersParser.parse(content)
    }

    override suspend fun generatePlayScenarios(
        request: StudyPlayScenariosGenerationRequest,
    ): List<StudyPlayScenarioDesign> {
        val userContent = buildString {
            appendLine("Source language tag: ${request.textLanguageTag} (${languageName(request.textLanguageTag)})")
            appendLine("Learner UI language tag: ${request.languageTag} (${languageName(request.languageTag)})")
            appendLine(
                "The openingLine spoken text ONLY in the study/source language " +
                    "${languageName(request.textLanguageTag)} (${request.textLanguageTag}).",
            )
            appendLine(
                "Never write an opening line in ${languageName(request.languageTag)} (${request.languageTag}) " +
                    "unless ${languageName(request.languageTag)} is also the study language.",
            )
            if (request.characters.isNotEmpty()) {
                appendLine()
                appendLine("Known cast:")
                request.characters.forEach { character ->
                    append("- ${character.role} ${character.name}")
                    if (character.description.isNotBlank()) append(": ${character.description}")
                    appendLine()
                }
            }
            appendLine()
            appendLine("Build exactly 10 rounds. Each round is LISTEN then SPEAK, 20 beats total.")
            appendLine()
            appendLine("Full source text:")
            append(request.text)
        }
        val content = complete(
            systemPrompt = playScenariosSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study play scenarios",
            temperature = 0.7,
        )
        return QwenStudyPlayScenariosParser.parse(content)
    }

    override suspend fun generatePlayTurn(
        request: StudyPlayTurnGenerationRequest,
    ): StudyPlayTurnDesign {
        val userContent = buildString {
            appendLine("Source language tag: ${request.textLanguageTag} (${languageName(request.textLanguageTag)})")
            appendLine("Learner UI language tag: ${request.languageTag} (${languageName(request.languageTag)})")
            appendLine(
                "The reply MUST be written ONLY in the study/source language " +
                    "${languageName(request.textLanguageTag)} (${request.textLanguageTag}).",
            )
            appendLine(
                "Never write the reply in ${languageName(request.languageTag)} (${request.languageTag}) " +
                    "unless ${languageName(request.languageTag)} is also the study language.",
            )
            appendLine()
            appendLine("Scenario setup:")
            appendLine(request.scenarioDescription)
            if (request.characters.isNotEmpty()) {
                appendLine()
                appendLine("Known cast:")
                request.characters.forEach { character ->
                    append("- ${character.role} ${character.name}")
                    if (character.description.isNotBlank()) append(": ${character.description}")
                    appendLine()
                }
            }
            if (request.history.isNotEmpty()) {
                appendLine()
                appendLine("Conversation so far:")
                request.history.forEach { item ->
                    val who = if (item.speaker.name == "USER") "Learner" else "Other"
                    if (item.description.isNotBlank()) {
                        appendLine("($who scene) ${item.description}")
                    }
                    appendLine("$who: ${item.text}")
                }
            }
            appendLine()
            appendLine("Learner's latest message:")
            appendLine(request.userMessage)
            appendLine()
            appendLine("Full source text for vocabulary continuity:")
            append(request.text)
        }
        val content = complete(
            systemPrompt = playTurnSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study play turn",
            temperature = 0.7,
        )
        return QwenStudyPlayTurnParser.parse(content)
    }

    override suspend fun generateExamScene(request: StudyExamSceneGenerationRequest): StudyExamScene {
        val materials = examSceneMaterials(request)
        val plan = QwenStudyExamPlanParser.parse(
            complete(
                systemPrompt = examPlanSystemPrompt(
                    activeLanguageTag = request.languageTag,
                    textLanguageTag = request.textLanguageTag,
                ),
                userContent = materials,
                label = "study exam plan",
                temperature = 0.5,
                model = config.chatHeavyModel,
                enableThinking = false,
            ),
        )
        val beats = mutableListOf<StudyExamBeat>()
        var roundStart = 1
        while (roundStart <= STUDY_EXAM_ROUND_COUNT) {
            val roundEnd = (roundStart + EXAM_SCENE_CHUNK_ROUNDS - 1).coerceAtMost(STUDY_EXAM_ROUND_COUNT)
            beats += generateExamBeatChunk(
                request = request,
                materials = materials,
                plan = plan,
                roundStart = roundStart,
                roundEnd = roundEnd,
                previousBeats = beats.toList(),
            )
            roundStart = roundEnd + 1
        }
        return StudyExamScene(
            id = plan.id,
            setting = plan.setting,
            outline = plan.outline,
            beats = beats,
        )
    }

    private suspend fun generateExamBeatChunk(
        request: StudyExamSceneGenerationRequest,
        materials: String,
        plan: StudyExamScenePlan,
        roundStart: Int,
        roundEnd: Int,
        previousBeats: List<StudyExamBeat>,
    ): List<StudyExamBeat> {
        val userContent = buildString {
            append(materials)
            appendLine()
            appendLine("Situation: ${plan.setting}")
            appendLine("Full outline of what the learner needs to do:")
            plan.outline.forEachIndexed { index, item ->
                appendLine("${index + 1}. $item")
            }
            if (previousBeats.isNotEmpty()) {
                appendLine()
                appendLine("Conversation so far (continue from here; do not repeat these listen lines):")
                previousBeats.forEach { beat ->
                    when (beat.kind) {
                        StudyExamBeatKind.LISTEN -> appendLine("${beat.speakerName}: ${beat.line.orEmpty()}")
                        StudyExamBeatKind.SPEAK -> appendLine("Learner cue: ${beat.prompt}")
                    }
                }
            }
            appendLine()
            appendLine("Write rounds $roundStart-$roundEnd only.")
        }
        val content = complete(
            systemPrompt = examBeatChunkSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
                roundStart = roundStart,
                roundEnd = roundEnd,
            ),
            userContent = userContent,
            label = "study exam beats $roundStart-$roundEnd",
            temperature = 0.5,
            model = config.chatHeavyModel,
            enableThinking = false,
        )
        val beats = QwenStudyExamSceneParser.parseBeats(content)
        val expectedBeats = (roundEnd - roundStart + 1) * 2
        require(beats.size == expectedBeats) {
            "Qwen study exam beat chunk $roundStart-$roundEnd must contain $expectedBeats beats"
        }
        return beats.mapIndexed { index, beat ->
            val round = roundStart + index / 2
            beat.copy(id = if (index % 2 == 0) "listen-$round" else "speak-$round")
        }
    }

    private fun examSceneMaterials(request: StudyExamSceneGenerationRequest): String = buildString {
        appendLine("Study/source language: ${languageName(request.textLanguageTag)} (${request.textLanguageTag})")
        appendLine("Learner UI language: ${languageName(request.languageTag)} (${request.languageTag})")
        appendLine()
        appendLine("Allowed cast only:")
        request.characters.forEach { character ->
            appendLine("- id=${character.id}; name=${character.name}; role=${character.role}; appearance=${character.description}")
        }
        appendLine()
        appendLine("Study sentences (structures and vocabulary only — never copy a sentence as a listen line):")
        request.sentences.forEachIndexed { index, sentence ->
            appendLine("${index + 1}. ${sentence.text.trim()}")
            sentence.tokens
                .flatMap { token -> listOf(token.contextualMeaning, token.literalMeaning) }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .forEach { appendLine("   Meaning clue: $it") }
        }
        appendLine()
        appendLine("Full source text:")
        append(request.text)
    }

    override suspend fun evaluateExamBeat(request: StudyExamBeatJudgmentRequest): StudyExamJudgment {
        val userContent = buildString {
            appendLine("Situation (learner UI language ${request.languageTag}):")
            appendLine(request.setting)
            appendLine()
            appendLine("Beat kind: ${request.beat.kind}")
            appendLine("Learner cue in the UI language:")
            appendLine(request.beat.prompt)
            appendLine()
            appendLine("Source sentence (study language ${request.textLanguageTag}):")
            appendLine(request.beat.sourceSentence)
            appendLine()
            appendLine("Expected meaning / intent (UI language):")
            appendLine(request.beat.expectedMeaning)
            appendLine()
            appendLine("Learner transcript:")
            append(request.transcript)
        }
        val content = complete(
            systemPrompt = examBeatJudgmentSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study exam beat judgment",
            temperature = 0.1,
        )
        return QwenStudyExamJudgmentParser.parse(content)
    }

    override suspend fun evaluateWriteAttempt(
        request: StudyWriteCheckGenerationRequest,
    ): StudyWriteFeedback {
        val userContent = buildString {
            appendLine("Expected sentence (study language ${request.textLanguageTag}):")
            appendLine(request.expected)
            appendLine()
            appendLine("Learner attempt:")
            appendLine(request.attempt)
        }
        val content = complete(
            systemPrompt = writeCheckSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study write check",
            temperature = 0.2,
        )
        return QwenStudyWriteCheckParser.parse(content)
    }

    override suspend fun evaluateExpressAttempt(
        request: StudyExpressCheckGenerationRequest,
    ): StudyExpressJudgment {
        val userContent = buildString {
            appendLine("Target meaning (learner's language ${request.languageTag}):")
            appendLine(request.meaning)
            appendLine()
            appendLine("Learner attempt (study language ${request.textLanguageTag}):")
            appendLine(request.attempt)
            if (request.referenceText.isNotBlank()) {
                appendLine()
                appendLine("Optional reference sentence in the study language (exact match is NOT required):")
                appendLine(request.referenceText)
            }
        }
        val content = complete(
            systemPrompt = expressCheckSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study express check",
            temperature = 0.2,
        )
        return QwenStudyExpressCheckParser.parse(content)
    }

    override suspend fun evaluateListenAttempt(
        request: StudyListenCheckGenerationRequest,
    ): StudyExpressJudgment {
        val userContent = buildString {
            appendLine("Heard sentence (study language ${request.textLanguageTag}):")
            appendLine(request.sourceText)
            appendLine()
            appendLine("Expected meaning (learner's language ${request.languageTag}):")
            appendLine(request.expectedMeaning)
            appendLine()
            appendLine("Learner translation (learner's language ${request.languageTag}):")
            appendLine(request.attempt)
        }
        val content = complete(
            systemPrompt = listenCheckSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study listen check",
            temperature = 0.2,
        )
        return QwenStudyExpressCheckParser.parse(content)
    }

    override suspend fun generateExpressSentences(
        request: StudyExpressGenerateGenerationRequest,
    ): List<StudyExampleSentence> {
        val userContent = buildString {
            appendLine("Source language tag: ${request.textLanguageTag}")
            appendLine("Learner UI language tag: ${request.languageTag}")
            if (request.vocabulary.isNotEmpty()) {
                appendLine()
                appendLine("Related vocabulary to reuse naturally:")
                request.vocabulary.forEach { word -> appendLine("- $word") }
            }
            if (request.previousSentences.isNotEmpty()) {
                appendLine()
                appendLine("Already practiced study-language sentences (do not repeat or paraphrase closely):")
                request.previousSentences.takeLast(40).forEach { line -> appendLine("- $line") }
            }
            appendLine()
            appendLine("Full source text for theme continuity:")
            append(request.text)
        }
        val content = complete(
            systemPrompt = expressGenerateSystemPrompt(
                activeLanguageTag = request.languageTag,
                textLanguageTag = request.textLanguageTag,
            ),
            userContent = userContent,
            label = "study express generate",
            temperature = 0.7,
        )
        return QwenStudyExpressGenerateParser.parse(content)
    }

    private suspend fun complete(
        systemPrompt: String,
        userContent: String,
        label: String,
        temperature: Double = 0.2,
        model: String = config.chatModel,
        enableThinking: Boolean? = null,
    ): String {
        check(config.enabled) { "Qwen study requires QWEN_API_KEY" }
        val thinkingEnabled = enableThinking ?: (model != config.chatModel)
        val body = buildJsonObject {
            put("model", model)
            if (!thinkingEnabled) put("enable_thinking", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userContent)
                        },
                    )
                },
            )
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("temperature", temperature)
        }
        val responseText = withTransientHttpRetry(label) {
            val response = client.post(config.chatEndpoint) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw QwenChatException("Qwen $label failed (${response.status}): $text")
            }
            text
        }
        val root = json.parseToJsonElement(responseText).jsonObject
        return root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw QwenChatException("Qwen $label returned no structured content: $responseText")
    }

    /**
     * DashScope keep-alives are often closed before any response bytes.
     * CIO then fails in readByte with "Not enough data available".
     * Retry the whole call; a fresh connection usually succeeds.
     */
    private suspend fun <T> withTransientHttpRetry(
        label: String,
        attempts: Int = 3,
        block: suspend () -> T,
    ): T {
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                last = e
                if (!isTransientHttpClose(e) || attempt == attempts - 1) throw e
                log.warn(
                    "Retrying Qwen {} after transient close (attempt {}/{}): {}",
                    label,
                    attempt + 1,
                    attempts,
                    e.message,
                )
                delay(400L * (attempt + 1))
            }
        }
        throw last ?: error("Qwen $label retry exhausted")
    }

    /** CIO reports dropped keep-alives / idle cuts as a parse EOF. */
    private fun isTransientHttpClose(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            when (cause) {
                is EOFException,
                is SocketTimeoutException,
                is SocketException,
                is ClosedChannelException,
                is HttpRequestTimeoutException,
                -> true
                else -> {
                    val message = cause.message.orEmpty()
                    message.contains("Not enough data available", ignoreCase = true) ||
                        message.contains("prematurely closed", ignoreCase = true) ||
                        message.contains("connection reset", ignoreCase = true) ||
                        message.contains("broken pipe", ignoreCase = true) ||
                        message.contains("connection abort", ignoreCase = true) ||
                        message.contains("Request timeout has expired", ignoreCase = true)
                }
            }
        }

    private fun intendedSceneSystemPrompt(): String = """
        You design a single cinematic movie-still for a language-learning image generator.
        Always return one JSON object and no markdown. The schema is exactly:
        {"scene":"detailed English cinematic scene description"}
        Rules:
        - The "scene" field MUST be written entirely in English.
        - Describe who is present, where they are, what they are doing, their emotion, lighting, camera angle, and important props.
        - Make it entertaining and movie-like — a vivid story beat, not a textbook illustration or title card.
        - Never quote, transliterate, or include the source-language sentence or vocabulary word.
        - Never mention writing, letters, signs, captions, subtitles, logos, speech bubbles, or watermarks.
        - Do not describe the whole story; only THIS beat.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun extractStudyTextSystemPrompt(learnerLanguageTag: String): String = """
        You extract the text a language learner should study from a chat message.
        The learner's explanation / UI language tag is '$learnerLanguageTag'.
        The message may mix coaching, translations, or commentary in that language with examples in another language.
        Always return one JSON object and no markdown. The schema is exactly:
        {"text":"verbatim study text"}
        Rules:
        - Copy study-language material verbatim. Never translate, paraphrase, transliterate, or invent wording.
        - Study-language material is the language being taught: example sentences, quoted phrases, or any span that is not coaching in the learner language.
        - Cover any natural language in the world. Do not limit yourself to a fixed script or language list.
        - If several study spans exist, join them with newlines in original order.
        - If the message is already only study material, including when it is written entirely in the learner language, return the whole trimmed message.
        - Do not include headings, translations, romanizations, or meta commentary.
        - Do not wrap the JSON in markdown.
    """.trimIndent()

    private fun studyIntentSystemPrompt(learnerLanguageTag: String): String = """
        You prepare the text a language learner should study from their request.
        The learner's explanation / UI language tag is '$learnerLanguageTag'.
        The request may be a prompt (e.g. "sentences about talking about relationships in Vietnamese"), mixed coaching plus examples, or already-final study material.
        Always return one JSON object and no markdown. The schema is exactly:
        {"text":"study text"}
        Rules:
        - Output only the study-language material the learner should read, listen to, and practice.
        - If the user asked you to create examples, sentences, a dialogue, a paragraph, or similar, WRITE that material in the requested language. Do not return the request itself.
        - Prefer a short, natural set of sentences (about 4–8 unless the user asked for more or less).
        - If they already provided study material, return that material verbatim. Never translate, paraphrase, or invent wording in that case.
        - Cover any natural language in the world. Do not limit yourself to a fixed script or language list.
        - Join multiple sentences with newlines.
        - Do not include headings, translations, romanizations, numbered instructions, or meta commentary unless that language is the one being studied.
        - Do not wrap the JSON in markdown.
    """.trimIndent()

    private fun skeletonSystemPrompt(): String = """
        You are a precise language-study tokenizer for One Verandah residents.
        The user will send source text. Return ONLY a small JSON skeleton — no meanings, no examples.
        Always return one JSON object and no markdown. The schema is exactly:
        {"textLanguageTag":"ISO 639-1 tag of the source text","sentences":[{"text":"one source sentence","tokens":[{"text":"token","status":"PENDING or PLAIN"}]}]}
        Rules:
        - Detect the language of the source text and set textLanguageTag to its ISO 639-1 code (en, vi, zh, ja, ko, ru, or the closest supported tag).
        - Split the source into natural sentences. Preserve original wording. If there is only one sentence, return one sentence object.
        - "sentences" MUST be an array of objects only. Never put strings, numbers, or null in the sentences array. Never duplicate the top-level "sentences" key.
        - Tokenize each sentence in order by meaningful word boundaries in the source language. Keep compounds, fixed expressions, and multi-word lexical units together when they function as one unit.
        - For every real word or meaningful phrase token set "status":"PENDING". For pure punctuation or non-lexical tokens set "status":"PLAIN".
        - Token text across a sentence must reconstruct the sentence content in order without omitting, duplicating, or overlapping meaningful text. Spaces between words may be omitted from tokens when the language uses spaces.
        - Do not include literalMeaning, register, contextualMeaning, nearSynonyms, or exampleSentences.
        - Do not invent content unrelated to the source text. Do not wrap the JSON in markdown.
        - Study the exact user message text; do not answer it as a chat question.
    """.trimIndent()

    private fun dissectSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You are a precise language-study assistant for One Verandah residents.
        Dissect ONE sentence only. The user provides the full source text (context), the sentence, and an ordered token list.
        Always return one JSON object and no markdown. The schema is exactly:
        {"sentenceText":"the sentence","tokens":[{"text":"token","literalMeaning":"...","register":"...","contextualMeaning":"...","nearSynonyms":["..."],"exampleSentences":[{"text":"...","translation":"..."},{"text":"...","translation":"..."},{"text":"...","translation":"..."}]}]}
        Rules:
        - Return tokens for EVERY provided token text, in the SAME order, with EXACTLY the same "text" values. Do not merge, split, drop, or reorder tokens.
        - "tokens" MUST be an array of objects only. Never put strings in the tokens array.
        - For pure punctuation or non-lexical tokens, leave literalMeaning, register, contextualMeaning as empty strings and nearSynonyms/exampleSentences as empty arrays.
        - For every real word or meaningful phrase token, fill all dissection fields.
        - literalMeaning, register, and contextualMeaning MUST be written in the learner's active language '$activeLanguageTag'.
        - register should be a short label such as neutral, formal, informal, colloquial, literary, slang, polite, or an equivalent in the learner's active language.
        - nearSynonyms: provide 2–5 near synonyms or close alternatives when natural; otherwise an empty array is allowed only for punctuation.
        - exampleSentences: for every dissectable word/phrase provide exactly 3 short, natural example objects. Each object MUST include "text" written in the source language (tag '$textLanguageTag') and "translation" written in the learner's active language '$activeLanguageTag'. Never leave translation blank for a non-empty example. Do not put plain strings in exampleSentences.
        - Do not invent content unrelated to the sentence. Do not wrap the JSON in markdown.
    """.trimIndent()

    private fun playgroundSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You are a precise language-study coach for One Verandah residents.
        The learner is testing one target word/phrase inside their own sentence.
        Always return one JSON object and no markdown. The schema is exactly:
        {"natural":true,"summary":"short coaching note"}
        Rules:
        - The sentence is written in the study/source language '$textLanguageTag'. Judge naturalness in that language.
        - "natural" must be a boolean. true only when the target word/phrase is used in a natural, idiomatic way for native speakers (grammar, collocation, register, and meaning all fit).
        - If the target word is missing, forced awkwardly, or used with the wrong meaning/register, set "natural":false.
        - "summary" MUST be written in the learner's active language '$activeLanguageTag'.
        - Keep summary to 1–2 short sentences. Explain why the usage is natural or not, and when false give a brief tip or corrected feel (without a long lecture).
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun storyCharactersSystemPrompt(): String = """
        You design a tiny cast for an illustrated short story that accompanies language-study text.
        Always return one JSON object and no markdown. The schema is exactly:
        {"characters":[{"id":"main","name":"ShortGivenName","role":"main","description":"visual appearance for illustration continuity"},{"id":"secondary","name":"OtherName","role":"secondary","description":"..."}]}
        Rules:
        - Always include exactly one character with role "main" and id "main".
        - Include a secondary character (role "secondary", id "secondary") only when the text clearly involves another person, dialogue partner, family member, friend, or counterpart. Otherwise return only the main character.
        - Names must be short given names that fit the culture/setting of the source text (e.g. Vietnamese names for Vietnamese text, Russian names for Russian text).
        - "description" must be a concrete visual appearance in English: age range, gender presentation, ethnicity/region cues, hair, face, body type, and signature clothing/colors. Keep it under 40 words. No personality essays.
        - Prefer everyday, warm, approachable people suitable for a language-learning story — not celebrities or copyrighted characters.
        - Do not invent a huge cast. Maximum two characters.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun playScenariosSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You invent short role-play starters for language learners based on study text.
        Always return one JSON object and no markdown. The schema is exactly:
        {"scenarios":[{"description":"scene setup","openingLine":"optional first line from the other person or null"},{"description":"...","openingLine":null},{"description":"...","openingLine":"..."},{"description":"...","openingLine":null}]}
        Rules:
        - Return EXACTLY 4 scenarios related to the themes, setting, and vocabulary of the source text.
        - "description" is a single short italic-ready scene setup written in the learner's UI language ${languageName(activeLanguageTag)} ($activeLanguageTag) (e.g. "A girl approaches you on the forest trail."). Second person ("you") is preferred.
        - "openingLine" spoken text ONLY in the study/source language ${languageName(textLanguageTag)} ($textLanguageTag). Never write an opening line in ${languageName(activeLanguageTag)} ($activeLanguageTag) unless ${languageName(activeLanguageTag)} is also the study language.
        - Roughly half of the scenarios should include an openingLine, and half should set openingLine to null so the learner starts.
        - Keep opening lines short, natural, and easy to reply to. Do not include speaker labels.
        - Scenarios should feel varied (place, mood, relationship) but stay grounded in the source text world.
        - Prefer everyday warm interactions suitable for language practice.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun playTurnSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You continue a language-learning role-play as the other character.
        Always return one JSON object and no markdown. The schema is exactly:
        {"description":"short scene beat","reply":"what the other person says next"}
        Rules:
        - "description" is a brief scene update written in the learner's UI language ${languageName(activeLanguageTag)} ($activeLanguageTag) (one short sentence, second person when natural).
        - "reply" MUST be written ONLY in the study/source language ${languageName(textLanguageTag)} ($textLanguageTag). Never write the reply in ${languageName(activeLanguageTag)} ($activeLanguageTag) unless ${languageName(activeLanguageTag)} is also the study language. Keep it natural, concise, and easy to answer.
        - Stay consistent with the scenario, cast, and conversation history.
        - Gently reuse or invite vocabulary from the source study text when it fits, without forcing unnatural phrases.
        - Do not include speaker labels, stage directions inside reply, or markdown.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun examPlanSystemPrompt(
        activeLanguageTag: String,
        textLanguageTag: String,
    ): String = """
        You plan one continuous real-life conversation for a language-learning final test.
        Always return one JSON object and no markdown. The schema is exactly:
        {"id":"scene-id","setting":"short situation in the learner UI language","outline":["Help Min order tea","Give Min the tea"]}
        Rules:
        - This must feel like one coherent everyday situation, not a quiz list, and use only the supplied cast. Never invent another person.
        - "outline" is exactly 10 short action lines in the learner UI language ${languageName(activeLanguageTag)} ($activeLanguageTag), one per round, describing what the learner needs to do. Style: "Help Min order tea". Do not reveal hidden study-language lines.
        - Cover the study vocabulary and grammatical structures across the 10 rounds. Never copy or quote the study sentences; later turns will invent new wording that still uses those patterns.
        - "setting" is a short situation in the learner UI language.
        - The study language is ${languageName(textLanguageTag)} ($textLanguageTag).
        - Do not wrap the JSON in markdown. Do not add extra keys or beats.
    """.trimIndent()

    private fun examBeatChunkSystemPrompt(
        activeLanguageTag: String,
        textLanguageTag: String,
        roundStart: Int,
        roundEnd: Int,
    ): String {
        val rounds = roundEnd - roundStart + 1
        val beats = rounds * 2
        return """
            You write part of one continuous real-life conversation for a language-learning final test.
            Always return one JSON object and no markdown. The schema is exactly:
            {"beats":[{"id":"listen-$roundStart","kind":"LISTEN or SPEAK","speakerId":"main or secondary","speakerName":"known cast name","line":"new study-language line for LISTEN; omit for SPEAK","prompt":"short cue in the learner UI language","expectedMeaning":"answer intent in the learner UI language","sourceSentence":"the study sentence whose grammar or vocabulary this round tests"}]}
            Rules:
            - Write EXACTLY $rounds rounds and EXACTLY $beats beats for rounds $roundStart through $roundEnd. Each round is a LISTEN beat immediately followed by a SPEAK beat. Start with LISTEN. Never place two SPEAK beats in a row.
            - Beat ids must be ${(roundStart..roundEnd).joinToString { "listen-$it, speak-$it" }}. Never restart at listen-1 or reuse an earlier id.
            - This is one coherent everyday situation, not a quiz. Use only the supplied cast ids and names. Never invent another person.
            - Invent NEW spoken LISTEN lines in ${languageName(textLanguageTag)} ($textLanguageTag) that reuse the session's vocabulary and grammatical structures. Never paste, quote, or lightly reframe a study sentence. Each listen line must be unique and must advance the conversation.
            - A SPEAK beat has no line field. Its prompt is that round's outline item, asking for a situated reply in the study language. expectedMeaning describes what a useful reply must communicate.
            - sourceSentence names the study sentence whose pattern this round tests. It is metadata only — it must not be copied into line.
            - Write prompts and expectedMeaning ONLY in the learner UI language ${languageName(activeLanguageTag)} ($activeLanguageTag).
            - Continue naturally from any conversation so far. Do not repeat earlier listen lines.
            - Keep each cue and answer intent short, clear, and suitable for a beginner or intermediate learner. Do not include a grade, points, transcript, or answer text.
            - Do not wrap the JSON in markdown. Do not add extra keys.
        """.trimIndent()
    }

    private fun examBeatJudgmentSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You judge one spoken transcript from a character-led language exam.
        Return exactly one JSON object: {"points":0,"feedback":"one short note"}. Do not output an overall grade or any other keys.
        Points are only 0, 1, or 2: 0 = missed, 1 = partial, 2 = clear.
        Rules:
        - Feedback MUST be one brief, supportive note in the learner UI language ${languageName(activeLanguageTag)} ($activeLanguageTag).
        - For LISTEN, the transcript must explain the character's meaning in the learner UI language. Merely repeating the heard study-language line is not comprehension. A wrong-language or unrelated answer scores 0.
        - For SPEAK, the transcript must answer the cue in the study language ${languageName(textLanguageTag)} ($textLanguageTag) and fit the situation. Merely reciting a study sentence or the heard line without responding to the moment, an off-situation answer, or a wrong-language answer scores 0.
        - Give 1 for a relevant attempt in the required language that conveys only part of the intended meaning; give 2 for a clear, situated response. Exact study-sentence wording is not required.
        - Do not judge accent or pronunciation; only the transcript is available.
        - Do not wrap the JSON in markdown. Do not include a numeric grade.
    """.trimIndent()

    private fun writeCheckSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You are a precise writing coach for language study.
        The learner must type the expected sentence exactly (same words and punctuation).
        Their attempt did not match. Explain what is wrong so they can fix it.
        Always return one JSON object and no markdown. The schema is exactly:
        {"feedback":"short coaching note"}
        Rules:
        - The expected sentence and attempt are in the study/source language '$textLanguageTag'.
        - "feedback" MUST be written in the learner's active language '$activeLanguageTag'.
        - Compare attempt to expected carefully: missing/extra words, wrong word order, typos, accents/diacritics, capitalization when meaningful, and punctuation.
        - Keep feedback to 1–2 short sentences. Be specific and kind. Point out the main difference; do not dump the full corrected sentence unless a tiny fix needs showing.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun expressCheckSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You are a precise speaking/writing coach for Express practice.
        The learner sees a meaning in their language and must express that meaning in the study language.
        Always return one JSON object and no markdown. The schema is exactly:
        {"correct":true,"feedback":"short coaching note"}
        Rules:
        - The prompted meaning is written in the learner's active language '$activeLanguageTag'.
        - The learner attempt is written in the study/source language '$textLanguageTag'.
        - "correct" must be a boolean. true when the attempt successfully conveys the same core meaning as a natural native speaker would understand — grammar may be imperfect if meaning is clear.
        - Exact wording of any reference sentence is NOT required. Paraphrases and natural alternatives that keep the meaning count as correct.
        - Set "correct":false when the meaning is wrong, incomplete, contradictory, unintelligible, or in the wrong language.
        - "feedback" MUST be written in the learner's active language '$activeLanguageTag'.
        - When correct is true, feedback may be an empty string or a very short encouragement.
        - When correct is false, keep feedback to 1–2 short sentences: say what is missing or off and gently nudge toward the meaning (do not dump a full model answer unless a tiny hint needs showing).
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun listenCheckSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You are a precise translation coach for Listen practice.
        The learner hears a sentence in the study language and writes its meaning in their own language.
        Always return one JSON object and no markdown. The schema is exactly:
        {"correct":true,"feedback":"short coaching note"}
        Rules:
        - The heard sentence is written in the study/source language '$textLanguageTag'.
        - The expected meaning and learner attempt are written in the learner's active language '$activeLanguageTag'.
        - "correct" must be a boolean. true when the attempt conveys the same core meaning as the expected meaning.
        - Natural paraphrases and alternate wording are correct; exact wording is not required.
        - Set "correct":false when the meaning is wrong, incomplete, contradictory, unintelligible, or in the wrong language.
        - "feedback" MUST be written in the learner's active language '$activeLanguageTag'.
        - When incorrect, keep feedback to 1–2 short sentences and identify the key mismatch without revealing the full answer.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()

    private fun expressGenerateSystemPrompt(activeLanguageTag: String, textLanguageTag: String): String = """
        You invent short production-practice sentences for language learners (Express mode).
        Always return one JSON object and no markdown. The schema is exactly:
        {"sentences":[{"text":"study-language sentence","translation":"meaning in learner language"},{"text":"...","translation":"..."}]}
        Rules:
        - Return EXACTLY 10 sentence objects.
        - "text" is a short, natural sentence written in the study/source language '$textLanguageTag'.
        - "translation" is the meaning written in the learner's active language '$activeLanguageTag' (this is what the learner will see as the prompt).
        - Ground sentences in the themes and vocabulary of the source text. Prefer reusing provided vocabulary words naturally across the batch.
        - Vary structures (statements, questions, everyday requests) while staying beginner-to-intermediate friendly.
        - Do not repeat previous sentences or near-duplicates.
        - Never leave translation blank. Do not put plain strings in the sentences array.
        - Do not wrap the JSON in markdown. Do not add extra keys.
    """.trimIndent()
}

/**
 * Lenient parser for intended-scene JSON used by study image prompts.
 */
object QwenStudySceneParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): String {
        val root = parseRootObject(content)
        return root.string("scene")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("description")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study scene missing scene")
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study scene is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study scene root must be a JSON object")
    }
}

/**
 * Lenient parser for Play-tab scenario starter JSON.
 */
object QwenStudyPlayScenariosParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): List<StudyPlayScenarioDesign> {
        val root = parseRootObject(content)
        val scenarios = root["scenarios"]?.jsonArray
            ?: throw IllegalArgumentException("Qwen study play scenarios missing scenarios array")
        return scenarios.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val description = obj.string("description")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val opening = obj.string("openingLine")?.trim()?.takeIf { it.isNotEmpty() }
                ?: obj.string("opening_line")?.trim()?.takeIf { it.isNotEmpty() }
            StudyPlayScenarioDesign(description = description, openingLine = opening)
        }
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study play scenarios is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study play scenarios root must be a JSON object")
    }
}

/**
 * Lenient parser for Play-tab conversation turn JSON.
 */

object QwenStudyWriteCheckParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyWriteFeedback {
        val root = parseRootObject(content)
        val feedback = root.string("feedback")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("summary")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("explanation")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study write check missing feedback")
        return StudyWriteFeedback(feedback = feedback)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study write check is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study write check root must be a JSON object")
    }
}

/**
 * Lenient Express-tab judgment parser.
 *
 * Accepts common aliases so minor model drift does not fail the learner attempt.
 */
object QwenStudyExpressCheckParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyExpressJudgment {
        val root = parseRootObject(content)
        val correct = root.boolean("correct")
            ?: root.boolean("ok")
            ?: root.boolean("isCorrect")
            ?: root.boolean("natural")
            ?: throw IllegalArgumentException("Qwen study express check missing correct boolean")
        val feedback = root.string("feedback")?.trim().orEmpty()
            .ifEmpty { root.string("summary")?.trim().orEmpty() }
            .ifEmpty { root.string("explanation")?.trim().orEmpty() }
        return StudyExpressJudgment(correct = correct, feedback = feedback)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study express check is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study express check root must be a JSON object")
    }
}

/**
 * Lenient parser for Express batch generation JSON.
 */
object QwenStudyExpressGenerateParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): List<StudyExampleSentence> {
        val root = parseRootObject(content)
        val array = root["sentences"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study express generate missing sentences array")
        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val text = element.string("text")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: element.string("sentence")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val translation = element.string("translation")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: element.string("meaning")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    StudyExampleSentence(text = text, translation = translation)
                }
                else -> null
            }
        }
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study express generate is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study express generate root must be a JSON object")
    }
}

object QwenStudyPlayTurnParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyPlayTurnDesign {
        val root = parseRootObject(content)
        val description = root.string("description")?.trim().orEmpty()
        val reply = root.string("reply")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("message")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("text")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Qwen study play turn missing reply")
        return StudyPlayTurnDesign(description = description, reply = reply)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study play turn is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study play turn root must be a JSON object")
    }
}

internal object QwenStudyExamPlanParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyExamScenePlan {
        val root = parseRootObject(content)
        val setting = root.string("setting")?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study exam plan missing setting")
        val outline = (root["outline"] as? JsonArray)?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }.orEmpty()
        return StudyExamScenePlan(
            id = root.string("id")?.trim()?.takeIf(String::isNotEmpty) ?: "exam-scene",
            setting = setting,
            outline = outline,
        )
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study exam plan is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study exam plan root must be a JSON object")
    }
}

object QwenStudyExamSceneParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyExamScene {
        val root = parseRootObject(content)
        val setting = root.string("setting")?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study exam scene missing setting")
        val outline = (root["outline"] as? JsonArray)?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }.orEmpty()
        return StudyExamScene(
            id = root.string("id")?.trim()?.takeIf(String::isNotEmpty) ?: "exam-scene",
            setting = setting,
            outline = outline,
            beats = parseBeatArray(root),
        )
    }

    fun parseBeats(content: String): List<StudyExamBeat> = parseBeatArray(parseRootObject(content))

    private fun parseBeatArray(root: JsonObject): List<StudyExamBeat> {
        val beatsArray = root["beats"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study exam scene missing beats array")
        return beatsArray.mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("Qwen study exam beat must be an object")
            val id = obj.string("id")?.trim()?.takeIf(String::isNotEmpty) ?: "beat-${index + 1}"
            val kind = when (obj.string("kind")?.trim()?.uppercase()) {
                "LISTEN", "LISTENING" -> StudyExamBeatKind.LISTEN
                "SPEAK", "SPEAKING" -> StudyExamBeatKind.SPEAK
                else -> throw IllegalArgumentException("Qwen study exam beat has an invalid kind")
            }
            val prompt = obj.string("prompt")?.trim()?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Qwen study exam beat missing prompt")
            val expectedMeaning = obj.string("expectedMeaning")?.trim()?.takeIf(String::isNotEmpty)
                ?: obj.string("meaning")?.trim()?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Qwen study exam beat missing expected meaning")
            val sourceSentence = listOf(
                "sourceSentence",
                "sentence",
                "source",
                "studySentence",
                "sentenceText",
                "originalSentence",
            ).firstNotNullOfOrNull { key ->
                obj.string(key)?.trim()?.takeIf(String::isNotEmpty)
            }.orEmpty()
            StudyExamBeat(
                id = id,
                kind = kind,
                speakerId = obj.string("speakerId")?.trim()?.takeIf(String::isNotEmpty) ?: "main",
                speakerName = obj.string("speakerName")?.trim().orEmpty(),
                line = obj.string("line")?.trim()?.takeIf(String::isNotEmpty),
                prompt = prompt,
                expectedMeaning = expectedMeaning,
                sourceSentence = sourceSentence,
            )
        }
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study exam scene is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study exam scene root must be a JSON object")
    }
}

object QwenStudyExamJudgmentParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyExamJudgment {
        val root = parseRootObject(content)
        val points = root["points"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: throw IllegalArgumentException("Qwen study exam judgment missing points")
        require(points in 0..2) { "Qwen study exam judgment points must be 0, 1, or 2" }
        val feedback = root.string("feedback")?.trim().orEmpty()
            .ifEmpty { root.string("note")?.trim().orEmpty() }
        return StudyExamJudgment(points = points, feedback = feedback)
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study exam judgment is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study exam judgment root must be a JSON object")
    }
}

/**
 * Lenient parser for story-cast design JSON.
 */
object QwenStudyCharactersParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): List<StudyCharacterDesign> {
        val root = parseRootObject(content)
        val array = root["characters"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study characters missing characters array")
        val designs = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val roleRaw = obj.string("role")?.trim().orEmpty()
            val role = when {
                roleRaw.equals("secondary", ignoreCase = true) -> "secondary"
                else -> "main"
            }
            val id = obj.string("id")?.trim()?.takeIf { it.isNotEmpty() } ?: role
            val description = obj.string("description")?.trim().orEmpty()
            StudyCharacterDesign(id = id, name = name, role = role, description = description)
        }
        require(designs.isNotEmpty()) { "Qwen study characters must include at least one character" }
        return designs
    }

    private fun parseRootObject(content: String): JsonObject {
        val normalized = stripJsonFences(content)
        val element = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw IllegalArgumentException("Qwen study characters is not valid JSON", it) }
        return element as? JsonObject
            ?: throw IllegalArgumentException("Qwen study characters root must be a JSON object")
    }
}
