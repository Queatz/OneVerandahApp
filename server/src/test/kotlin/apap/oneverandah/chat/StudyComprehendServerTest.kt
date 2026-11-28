package apap.oneverandah.chat

import apap.oneverandah.api.STUDY_COMPREHEND_MIN_ACTIONS
import apap.oneverandah.api.STUDY_COMPREHEND_SCENE_COUNT
import apap.oneverandah.api.STUDY_COMPREHEND_TILE_COUNT
import apap.oneverandah.api.StudyComprehendBuildStatus
import apap.oneverandah.api.StudyComprehendGenerateRequest
import apap.oneverandah.api.StudyComprehendWorld
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.findWalkPath
import apap.oneverandah.api.StudyComprehendScenePlayState
import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StudyComprehendServerTest {
    @Test
    fun `DashScopeStudyAiClient uses chatHeavyModel and parses 3 32x32 scenes with reachability`() = runBlocking {
        val capturedBodies = mutableListOf<String>()
        val heavyModelName = "qwen-heavy-comprehend-model"
        val mockEngine = MockEngine { request ->
            capturedBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val payload = validThreeSceneComprehendJson()
            val escaped = Json.encodeToString(kotlinx.serialization.serializer<String>(), payload)
            respond(
                content = """{"choices":[{"message":{"content":$escaped}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine)
        val config = QwenChatConfig(
            chatEndpoint = "https://dashscope.example/v1/chat",
            chatModel = "qwen-light",
            chatHeavyModel = heavyModelName,
            ttsEndpoint = "https://dashscope.example/v1/tts",
            ttsModel = "qwen-tts",
            ttsVoice = "Cherry",
            voiceDesignEndpoint = "https://dashscope.example/v1/voice",
            voiceDesignModel = "voice-enrollment",
            asrEndpoint = "wss://dashscope.example/v1/asr",
            asrModel = "qwen-asr",
            apiKey = "test-key",
        )
        val aiClient = DashScopeStudyAiClient(config, httpClient)

        val world = aiClient.generateComprehendWorld(sampleRequest())

        val sentJson = Json.parseToJsonElement(capturedBodies.single()).jsonObject
        assertEquals(heavyModelName, sentJson["model"]?.jsonPrimitive?.content)
        assertEquals("true", sentJson["enable_thinking"]?.jsonPrimitive?.content)

        assertEquals(STUDY_COMPREHEND_SCENE_COUNT, world.scenes.size)
        world.scenes.forEach { scene ->
            assertEquals(STUDY_COMPREHEND_TILE_COUNT, scene.tiles.size)
            assertTrue(scene.possibleActionCount() >= STUDY_COMPREHEND_MIN_ACTIONS)
            assertTrue(scene.objects.all { it.targetName.isNotBlank() })
            // Reachability check: every character has a walkable path from player spawn
            val init = StudyComprehendScenePlayState.initial(scene)
            scene.objects.filter { it.isCharacter }.forEach { charObj ->
                val path = scene.findWalkPath(init, scene.playerSpawnX, scene.playerSpawnZ, charObj.x, charObj.z)
                assertTrue(path.isNotEmpty(), "Character ${charObj.id} in ${scene.id} must be reachable from spawn")
            }
        }
        httpClient.close()
    }

    @Test
    fun `parser rejects scenes with fewer than 16 possible actions`() {
        val tooFewActionsJson = """
            {
              "setting": "Small courtyard",
              "sprites": [{"id":"char-1","isCharacter":true,"visualPrompt":"Guard"}],
              "scenes": [
                {
                  "id":"s1","title":"Gate","environment":"OUTDOOR","timeOfDay":"MORNING","weather":"SUNNY",
                  "objects":[
                    {"id":"guard","spriteId":"char-1","x":10,"z":10,"targetName":"Lính gác","isCharacter":true,"spokenText":"Xin chào bạn mới đến."}
                  ],
                  "tasks":[{"id":"t1","description":"Listen","requiredCharacterObjectIds":["guard"],"requiredActionIds":["guard-listen"]}]
                },
                {"id":"s2","title":"Hall","objects":[],"tasks":[]},
                {"id":"s3","title":"Room","objects":[],"tasks":[]}
              ]
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            QwenStudyComprehendParser.parse(tooFewActionsJson)
        }
    }

    @Test
    fun `comprehend job has no AI fallback on failure and retries cleanly when reopened`() = runBlocking {
        val aiCalls = AtomicInteger(0)
        val maskPrompts = CopyOnWriteArrayList<String>()
        val synthesizedLines = CopyOnWriteArrayList<String>()

        val fakeAi = object : StudyAiClient {
            override suspend fun generateSkeleton(request: StudyGenerationRequest) = error("unused")
            override suspend fun dissectSentence(request: StudyDissectGenerationRequest) = error("unused")
            override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) = error("unused")
            override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) = emptyList<StudyCharacterDesign>()
            override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) = emptyList<StudyPlayScenarioDesign>()
            override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) = error("unused")
            override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) = error("unused")
            override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) = error("unused")
            override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) = emptyList<apap.oneverandah.api.StudyExampleSentence>()

            override suspend fun generateComprehendWorld(request: StudyComprehendGenerateRequest): StudyComprehendWorld {
                if (aiCalls.incrementAndGet() == 1) {
                    throw IllegalStateException("Simulated Qwen heavy timeout")
                }
                return QwenStudyComprehendParser.parse(validThreeSceneComprehendJson())
            }
        }

        val fakeImages = object : StudyPlaygroundImageGenerator {
            override suspend fun generate(prompt: String, referenceImageUrls: List<String>): String =
                "https://cdn.example/raw.png"

            override suspend fun generateMaskedPortrait(
                prompt: String,
                referenceImageUrls: List<String>,
                maskPrompt: String,
                size: String?,
            ): String {
                maskPrompts += maskPrompt
                return "https://cdn.example/masked-${maskPrompts.size}.png"
            }
        }

        val service = StudyService(
            aiClient = fakeAi,
            imageGenerator = fakeImages,
            scope = CoroutineScope(Dispatchers.Default),
            talkAudioGenerator = StudyTalkAudioGenerator { text, _ ->
                synthesizedLines += text
                "https://cdn.example/tts-${synthesizedLines.size}.mp3"
            },
        )

        val request = sampleRequest()

        // First attempt fails -> NO fallback world, status is FAILED
        val firstJob = service.startComprehendJob(request)
        var polledFirst = service.comprehendJob(firstJob.jobId)!!
        repeat(40) {
            if (polledFirst.status == StudyComprehendBuildStatus.FAILED) return@repeat
            delay(25)
            polledFirst = service.comprehendJob(firstJob.jobId)!!
        }
        assertEquals(StudyComprehendBuildStatus.FAILED, polledFirst.status)
        assertNull(polledFirst.world)

        // User reopens the Comprehend tab -> starts a fresh job and succeeds with transparent sprites & TTS!
        val secondJob = service.startComprehendJob(request)
        assertNotEquals(firstJob.jobId, secondJob.jobId)
        var polledSecond = service.comprehendJob(secondJob.jobId)!!
        repeat(60) {
            if (polledSecond.status == StudyComprehendBuildStatus.READY ||
                polledSecond.status == StudyComprehendBuildStatus.FAILED
            ) return@repeat
            delay(25)
            polledSecond = service.comprehendJob(secondJob.jobId)!!
        }
        assertEquals(StudyComprehendBuildStatus.READY, polledSecond.status)
        val readyWorld = assertNotNull(polledSecond.world)
        assertTrue(readyWorld.hasAllAssets())
        assertTrue(maskPrompts.contains(STUDY_PERSON_MASK_PROMPT))
        assertTrue(maskPrompts.contains(STUDY_OBJECT_MASK_PROMPT))
        assertTrue(synthesizedLines.isNotEmpty())
        assertContains(
            readyWorld.scenes.first().objects.first { it.isCharacter }.spokenAudioUrl.orEmpty(),
            "https://cdn.example/tts-",
        )
    }

    private fun sampleRequest() = StudyComprehendGenerateRequest(
        sessionId = "sess-comprehend-1",
        text = "Chiều nay Lan đi qua cổng cung điện để pha trà sen cho khách.",
        textLanguageTag = "vi",
        languageTag = "en",
        sentences = listOf(
            StudySentence(
                text = "Chiều nay Lan đi qua cổng cung điện để pha trà sen cho khách.",
                translation = "This afternoon Lan walked through the palace gate to brew lotus tea for the guests.",
                tokens = listOf(
                    StudyToken("cung điện", StudyTokenStatus.READY, "palace", "royal palace"),
                    StudyToken("trà sen", StudyTokenStatus.READY, "lotus tea", "lotus tea"),
                ),
            ),
        ),
        characters = listOf(
            StudyStoryCharacter(
                id = "main",
                name = "Lan",
                role = "main",
                description = "Young woman in an emerald áo dài",
                imageUrl = "https://cdn.example/lan-ref.png",
            ),
        ),
    )

    private fun validThreeSceneComprehendJson(): String {
        fun buildSceneJson(
            idx: Int,
            title: String,
            env: String,
            tod: String,
            weather: String,
            spoken: String,
        ): String {
            val decoys = (1..15).joinToString(",") { n ->
                """{
                    "id": "s$idx-prop-$n",
                    "spriteId": "lantern-prop",
                    "x": ${(n * 2) % 30 + 1},
                    "z": ${4 + (n % 5) * 3},
                    "targetName": "Đèn lồng $n",
                    "emitsWarmLight": true,
                    "actions": [{"id": "s$idx-act-$n", "kind": "TOGGLE", "label": "Toggle"}]
                }"""
            }
            return """
                {
                  "id": "scene-$idx",
                  "title": "$title",
                  "description": "Atmospheric scene $idx",
                  "environment": "$env",
                  "timeOfDay": "$tod",
                  "weather": "$weather",
                  "playerSpawnX": 16,
                  "playerSpawnZ": 28,
                  "baseGround": "${if (env == "INDOOR") "MARBLE_TILE" else "GRASS"}",
                  "groundZones": [{"x": 14, "z": 6, "w": 5, "h": 24, "ground": "STONE_PAVING"}],
                  "objects": [
                    {
                      "id": "s$idx-lan",
                      "spriteId": "char-lan",
                      "x": 16,
                      "z": 10,
                      "targetName": "Lan",
                      "isCharacter": true,
                      "characterId": "main",
                      "spokenText": "$spoken",
                      "acceptSpokenText": "Tuyệt quá, cảm ơn bạn nhiều nhé!",
                      "declineSpokenText": "Không phải món này đâu, bạn nghe kỹ lại nhé.",
                      "actions": [
                        {"id": "s$idx-listen-lan", "kind": "LISTEN", "label": "Listen"},
                        {"id": "s$idx-give-lan", "kind": "GIVE", "label": "Give", "requiredItemId": "s$idx-tea-box"}
                      ]
                    },
                    {
                      "id": "s$idx-tea-box",
                      "spriteId": "tea-box",
                      "x": 12,
                      "z": 20,
                      "targetName": "Hộp trà sen",
                      "pickable": true,
                      "actions": [{"id": "s$idx-take-tea", "kind": "TAKE", "label": "Take"}]
                    },
                    $decoys
                  ],
                  "sceneryClusters": [
                    {
                      "spriteId": "pine-tree",
                      "targetName": "Cây thông",
                      "blocksMovement": true,
                      "swaysInWind": true,
                      "positions": [[2, 2], [29, 2], [16, 11]]
                    }
                  ],
                  "tasks": [
                    {
                      "id": "s$idx-task-1",
                      "description": "Listen to Lan",
                      "requiredCharacterObjectIds": ["s$idx-lan"],
                      "requiredActionIds": ["s$idx-listen-lan"]
                    },
                    {
                      "id": "s$idx-task-2",
                      "description": "Find what Lan needs and bring it to her",
                      "requiredCharacterObjectIds": ["s$idx-lan"],
                      "requiredActionIds": ["s$idx-give-lan"]
                    }
                  ]
                }
            """.trimIndent()
        }

        return """
            {
              "setting": "A royal Vietnamese garden leading into a lantern-lit palace tea hall",
              "playerTargetName": "Khách",
              "playerAppearance": "Traveler in smart linen tunic",
              "sprites": [
                {"id": "char-lan", "isCharacter": true, "characterId": "main", "visualPrompt": "Lan in emerald ao dai"},
                {"id": "tea-box", "isCharacter": false, "visualPrompt": "Carved lacquer lotus tea caddy"},
                {"id": "lantern-prop", "isCharacter": false, "visualPrompt": "Silk courtyard lantern"},
                {"id": "pine-tree", "isCharacter": false, "visualPrompt": "Tall courtyard pine tree"}
              ],
              "scenes": [
                ${buildSceneJson(1, "Outside the Garden Gate", "OUTDOOR", "MORNING", "MISTY", "Bạn lấy giúp mình hộp trà sen cạnh lối đá nhé.")},
                ${buildSceneJson(2, "Going inside the palace", "OUTDOOR", "EVENING", "RAINY", "Trời mưa rồi, bạn mang hộp trà sen vào hiên cho mình nhé.")},
                ${buildSceneJson(3, "Inside the Tea Hall", "INDOOR", "NIGHT", "COLD", "Khách sắp đến phòng trà, đưa hộp trà sen cho mình pha nhé.")}
              ]
            }
        """.trimIndent()
    }
}
