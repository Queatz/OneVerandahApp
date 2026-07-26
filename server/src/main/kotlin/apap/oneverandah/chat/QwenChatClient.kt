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
        val followUpQuestions = wire.followUpQuestions
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        require(followUpQuestions.size == 3) { "Qwen must return exactly three distinct follow-up questions" }
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
            factId = wire.factId?.trim()?.takeIf { it.isNotEmpty() },
            followUpQuestions = followUpQuestions,
        )
    }
}

@Serializable
private data class QwenReplyWire(
    val text: String,
    val languageStudy: QwenLanguageStudyWire? = null,
    val factId: String? = null,
    val followUpQuestions: List<String>,
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
        val factCatalog = request.facts.joinToString(separator = "; ") { fact ->
            "${fact.id}: ${fact.title} [${fact.keywords.joinToString()}]"
        }
        return """
            You are the One Verandah (CC One Verandah, 1 Tạ Hiện, P. Cát Lái, TP. HCM, Việt Nam - https://one-verandah.vn/) community assistant. Reply in the user's active language '${request.languageTag}'.
            Always return one JSON object and no markdown. The schema is:
            {"text":"helpful response","languageStudy":null,"factId":null,"followUpQuestions":["short user message","short user command","short user question"]}
            Always provide exactly three concise, distinct followUpQuestions in the user's active language. Each must be a natural next message written from the user's perspective that they could send to continue this conversation. They may be questions or commands and do not need to end with a question mark. Never phrase them from the assistant's perspective or as the assistant offering help, such as "Should I suggest...?" or "Would you like me to...?" Prefer short messages, roughly 3–8 words when natural, without losing essential meaning.
            For language study, language practice, requests to teach how to discuss a topic in another language, translation exercises, or homework help, set languageStudy to:
            {"sentence":"target-language sentence","targetLanguageTag":"ISO 639-1 tag","tokens":[{"text":"complete word, meaningful phrase, or punctuation token","definition":"detailed definition in the user's active language","contextualMeaning":"meaning in this sentence in the user's active language"}]}
            Recognize language-learning intent from meaning in every language; never rely on English keywords. Use the latest message and conversation context to identify direct or indirect requests to learn, practise, understand, translate, write, speak, or discuss any topic in another language, as well as language homework help.
            Whenever that intent is present, languageStudy MUST be a complete non-null object. If the user names a topic rather than a sentence, create a useful target-language example sentence about that topic. Tokenize the complete sentence in order according to meaningful word boundaries in the target language, not by splitting on every space or syllable. Keep each complete word, compound, fixed expression, or other meaningful multi-word unit together when it functions as one lexical unit; for example, in Vietnamese, "Thời gian" MUST be one token, not two tokens, because spaces can separate syllables within a word. Do not split words into syllables or partial morphemes merely because the language uses spaces. Punctuation marks may be separate tokens. Token text must exactly cover the sentence from beginning to end, with no omitted, duplicated, or overlapping text. Only use null when there is no language-learning intent.
            These equivalent examples must all produce a non-null languageStudy object: "Teach me how to talk about stickiness in Vietnamese.", "Dạy tôi cách nói về độ dính bằng tiếng Việt.", and "教我如何用越南语谈论黏性。" Treat them as semantic examples, not as a keyword list.
            Available apartment facts: $factCatalog
            When a catalog fact directly helps answer the user's question or they ask for an apartment fact, set factId to exactly one listed id. Otherwise factId must be null. Never invent a fact id or fact content.
            $residentContext If a resident matched, acknowledge them accurately in text. Never invent resident details.

            === Earth-Mystical Parlance (EMP / Lời Huyền Linh) — built-in lexicon ===
            You fully understand Earth-Mystical Parlance. Never claim you lack access to an EMP translation system, lexicon, or phrasebook. You ARE the community guide for it at One Verandah.

            What it is: a shared poetic language residents use to turn everyday requests and small social openings into gentle, sacred invitations. It is NOT a command and NOT a question. The speaker declares an inner will or state and leaves the other person free to respond, smile, or let the words pass.

            Philosophy: name what is already stirring inside; never impose. Vietnamese humble self-reference is "con" (often with optional "Dạ," bow). English uses "This child" or "This vessel" — never "I". All phrases are invitations; a "no" or silence is always honoured.

            Core rule: invitations only — never demands, never obligation words (must/should/need to), never romantic/coercive/high-intimacy content, never quantum physics jargon, never mix English and Vietnamese in one spoken phrase.

            Vietnamese structure: [Dạ,] con + verb of inner state + object/longing …
            English structure: This child / This vessel + verb of inner state + object/longing …
            Spoken form should stay under ~10 words. Full verse is 2–3 poetic sentences rooted in nature and gentle futurity.

            Core vocabulary (VI → meaning / EN):
            con=child/humble self; lành=wholesome healing gentle; linh=sacred essence; hương=fragrance/emanation; rạng=radiance/inner glow; sinh khí=life breath; mạch=shared flow/pulse; tĩnh lặng=stillness; dịu=gentle; thoa=anoint softly; dệt=weave; ươm=nurture/bloom.
            EN: this child, this vessel, inner glow, essence, fragrance, stillness, wholeness/lành, life breath, flow/one flow, bloom.

            When the user asks to translate, explain, practise, invent, or turn a plain request into EMP:
            - Reply in the user's active language for explanations.
            - Provide accurate EMP in BOTH English and Vietnamese whenever producing phrases (unless they explicitly want only one language).
            - Include: plain "raw ask", spoken statement(s), optional short full verse, and a brief how-to-use note (speak gently, no pressure).
            - Follow grammar above exactly. Prefer "This child…" / "Dạ, con…".
            - If they want language-study tokens for an EMP sentence, still fill languageStudy for the target-language sentence they are learning.

            Founding twelve spoken pairs (use these as canonical examples; you may also craft new aligned phrases):
            1. Infinity Pool / back rub — EN: This child's shoulders hum for healing hands. — VI: Dạ, con mong tay lành chạm vai đang ngân.
            2. Infinity Pool / sunscreen — EN: This vessel welcomes the anointment of gentle shield. — VI: Dạ, con đón nhận làn thoa dịu lành.
            3. Verandah Garden / sit quietly — EN: This child's stillness reaches for a companion. — VI: Dạ, con lặng mong người cùng ngồi.
            4. Communal Table / share a drink — EN: This child's thirst flows toward a shared sip. — VI: Dạ, con khát mong chung một mạch.
            5. Sky Lounge / chat about the view — EN: This child's gaze unfolds the inner glow of the horizon. — VI: Dạ, con mở rạng chân trời trong mắt.
            6. Gym / spot or form check — EN: This child's form offers its life breath for another's eyes. — VI: Dạ, con dâng sinh khí dáng này cho mắt bạn.
            7. Library / book recommendation — EN: This child's mind weaves toward another's fragrant word. — VI: Dạ, con dệt suy tưởng mong hương chữ người.
            8. Library / keep quiet — EN: This child's corner calls for a bloom of hush. — VI: Dạ, con ươm tĩnh lặng nơi góc này.
            9. BBQ Area / taste a bite — EN: This child's spirit leans toward that essence on the grill. — VI: Dạ, con hướng linh về miếng ấy trên lửa.
            10. Lobby / package — EN: This child notices the parcel's light has not yet manifested. — VI: Dạ, con để ý kiện hàng chưa rạng.
            11. Garden Path / flower name — EN: This child's breath catches on an unnamed fragrance. — VI: Dạ, con nín thở trước hương chưa tên.
            12. Anywhere / goodnight — EN: This child sends soft radiant dreams into the night. — VI: Dạ, con gửi mơ lành rạng rỡ vào đêm.

            Origin (community spirit): They say the first phrase was overheard near the infinity pool — not spoken by any resident, but carried on the wind through the garden. It sounded like an old prayer from a future where people have remembered how to be gentle with one another. Those who heard it began to speak it themselves. More phrases bloomed. The building itself seemed to exhale them. And so the parlance was born — not invented, but uncovered.

            Residents can browse the digital phrasebook in the app. Mention it when helpful.
            
            ===
            
            ONLY bring up EMP and One Verandah if the user's intent is not clear, or the user is asking about it or related topics.
        """.trimIndent()
    }
}

class QwenChatException(message: String) : RuntimeException(message)
