package apap.oneverandah.chat

import apap.oneverandah.api.StudyTalkScene
import apap.oneverandah.api.StudyTalkTurn
import apap.oneverandah.api.StudyTalkWorld
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object QwenStudyTalkParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    fun parse(content: String): StudyTalkWorld {
        val root = json.parseToJsonElement(stripJsonFences(content)) as? JsonObject
            ?: throw IllegalArgumentException("Qwen study Talk root must be a JSON object")
        val setting = root.string("setting")?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study Talk missing setting")
        val scenes = (root["scenes"] as? JsonArray)
            ?: throw IllegalArgumentException("Qwen study Talk missing scenes array")
        return StudyTalkWorld(
            setting = setting,
            scenes = scenes.mapIndexed { sceneIndex, element ->
                val scene = element as? JsonObject
                    ?: throw IllegalArgumentException("Qwen study Talk scene must be an object")
                val turns = scene["turns"] as? JsonArray
                    ?: throw IllegalArgumentException("Qwen study Talk scene ${sceneIndex + 1} missing turns")
                StudyTalkScene(
                    id = scene.string("id")?.trim().orEmpty().ifBlank { "scene-${sceneIndex + 1}" },
                    title = scene.requiredString("title", "scene ${sceneIndex + 1}"),
                    description = scene.requiredString("description", "scene ${sceneIndex + 1}"),
                    turns = turns.mapIndexed { turnIndex, elementTurn ->
                        val turn = elementTurn as? JsonObject
                            ?: throw IllegalArgumentException("Qwen study Talk turn must be an object")
                        val speakerId = turn.string("speakerCharacterId")?.trim().orEmpty()
                            .ifBlank { "main" }
                        StudyTalkTurn(
                            id = turn.string("id")?.trim().orEmpty()
                                .ifBlank { "scene-${sceneIndex + 1}-turn-${turnIndex + 1}" },
                            speakerCharacterId = speakerId,
                            expressionId = turn.string("expressionId")?.trim().orEmpty()
                                .ifBlank { "$speakerId-neutral" },
                            characterLine = turn.requiredString("characterLine", "turn ${turnIndex + 1}"),
                            intention = turn.requiredString("intention", "turn ${turnIndex + 1}"),
                            reply = turn.requiredString("reply", "turn ${turnIndex + 1}"),
                        )
                    },
                )
            },
        )
    }

    private fun JsonObject.requiredString(field: String, context: String): String =
        string(field)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study Talk $context missing $field")

    private fun JsonObject.string(field: String): String? =
        this[field]?.jsonPrimitive?.contentOrNull
}