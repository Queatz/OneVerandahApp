package apap.oneverandah.chat

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

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

class DashScopeStudyAiClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) : StudyAiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
        val content = complete(
            systemPrompt = skeletonSystemPrompt(),
            userContent = request.text,
            label = "study skeleton",
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
        )
        return QwenStudyDissectParser.parse(content)
    }

    private suspend fun complete(systemPrompt: String, userContent: String, label: String): String {
        check(config.enabled) { "Qwen study requires QWEN_API_KEY" }
        val body = buildJsonObject {
            put("model", config.chatModel)
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
            put("temperature", 0.2)
        }
        val response = client.post(config.chatEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenChatException("Qwen $label failed (${response.status}): $responseText")
        }
        val root = json.parseToJsonElement(responseText).jsonObject
        return root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw QwenChatException("Qwen $label returned no structured content: $responseText")
    }

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
}
