package apap.oneverandah.chat

import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.LanguageStudyToken
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object QwenStructuredReplyParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parse(content: String): GeneratedChatReply {
        val normalized = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val wire = json.decodeFromString<QwenReplyWire>(normalized)
        require(wire.text.isNotBlank()) { "Qwen returned an empty chat response" }
        return GeneratedChatReply(
            text = wire.text.trim(),
            languageStudy = wire.languageStudy?.let { study ->
                GeneratedLanguageStudy(
                    sentence = study.sentence,
                    targetLanguageTag = study.targetLanguageTag,
                    tokens = study.tokens.map {
                        LanguageStudyToken(it.text, it.definition, it.contextualMeaning)
                    },
                )
            },
        )
    }
}

@Serializable
private data class QwenReplyWire(
    val text: String,
    val languageStudy: QwenLanguageStudyWire? = null,
)

@Serializable
private data class QwenLanguageStudyWire(
    val sentence: String,
    val targetLanguageTag: String,
    val tokens: List<QwenTokenWire>,
)

@Serializable
private data class QwenTokenWire(
    val text: String,
    val definition: String,
    val contextualMeaning: String,
)

class DashScopeChatAiClient(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) : ChatAiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun generate(request: ChatGenerationRequest): GeneratedChatReply {
        check(config.enabled) { "Qwen chat requires QWEN_API_KEY" }
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt(request))
            })
            request.history.takeLast(20).forEach { message ->
                add(buildJsonObject {
                    put("role", if (message.sender == ChatSender.YOU) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
        val body = buildJsonObject {
            put("model", config.chatModel)
            put("messages", messages)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("temperature", 0.4)
        }
        val response = client.post(config.chatEndpoint) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body))
        }
        val responseText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw QwenChatException("Qwen chat failed (${response.status}): $responseText")
        }
        val root = json.parseToJsonElement(responseText).jsonObject
        val content = root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw QwenChatException("Qwen chat returned no structured content: $responseText")
        return QwenStructuredReplyParser.parse(content)
    }

    private fun systemPrompt(request: ChatGenerationRequest): String {
        val residentContext = request.matchedResident?.let {
            "A local resident matched the request: id=${it.id}, name=${it.name}, address=${it.blockAddress}."
        } ?: "No local resident matched the request."
        return """
            You are the One Verandah community assistant. Reply in the user's active language '${request.languageTag}'.
            Always return one JSON object and no markdown. The schema is:
            {"text":"helpful response","languageStudy":null}
            For language study, language practice, translation exercises, or homework help, set languageStudy to:
            {"sentence":"target-language sentence","targetLanguageTag":"ISO 639-1 tag","tokens":[{"text":"word or punctuation token","definition":"detailed definition in the user's active language","contextualMeaning":"meaning in this sentence in the user's active language"}]}
            Tokenize the complete sentence in order. Otherwise languageStudy must be null.
            $residentContext If a resident matched, acknowledge them accurately in text. Never invent resident details.
        """.trimIndent()
    }
}

class QwenChatException(message: String) : RuntimeException(message)