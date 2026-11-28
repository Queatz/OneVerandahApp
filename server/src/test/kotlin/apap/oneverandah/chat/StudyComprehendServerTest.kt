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
import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
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
                if (aiCalls.incrementAndGet() <= 3) {
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
            comprehendRetryBackoffMillis = listOf(5L, 5L, 5L),
        )

        val request = sampleRequest()

        // First job automatically tries 3 full attempts -> all 3 fail -> NO fallback world, status is FAILED
        val firstJob = service.startComprehendJob(request)
        var polledFirst = service.comprehendJob(firstJob.jobId)!!
        repeat(60) {
            if (polledFirst.status == StudyComprehendBuildStatus.FAILED) return@repeat
            delay(25)
            polledFirst = service.comprehendJob(firstJob.jobId)!!
        }
        assertEquals(StudyComprehendBuildStatus.FAILED, polledFirst.status)
        assertNull(polledFirst.world)
        assertEquals(3, aiCalls.get(), "Scene design must try 3 full attempts before marking job FAILED")

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

    @Test
    fun `music uses its own lane and reports the active scene while image permits are occupied`() = runBlocking {
        val imageGate = CompletableDeferred<Unit>()
        val musicGate = CompletableDeferred<Unit>()
        val musicStarted = CompletableDeferred<Unit>()
        val imageStarted = AtomicInteger(0)
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
            override suspend fun generateComprehendWorld(request: StudyComprehendGenerateRequest) =
                QwenStudyComprehendParser.parse(validThreeSceneComprehendJson())
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
                imageStarted.incrementAndGet()
                imageGate.await()
                return "https://cdn.example/sprite.png"
            }
        }
        val service = StudyService(
            aiClient = fakeAi,
            imageGenerator = fakeImages,
            scope = CoroutineScope(Dispatchers.Default),
            musicGenerator = StudyComprehendMusicGenerator {
                musicStarted.complete(Unit)
                musicGate.await()
                "https://cdn.example/scene-music.mp3"
            },
        )

        val job = service.startComprehendJob(sampleRequest())
        try {
            withTimeout(5_000) {
                while (imageStarted.get() < 3) delay(5)
            }
            withTimeout(5_000) { musicStarted.await() }

            val activeJob = service.comprehendJob(job.jobId)!!
            assertEquals(StudyComprehendBuildStatus.GENERATING_ASSETS, activeJob.status)
            assertTrue("MUSIC:1" in activeJob.inFlightSteps, "Music generation must be visible in job progress: $activeJob")
        } finally {
            imageGate.complete(Unit)
            musicGate.complete(Unit)
        }

        withTimeout(5_000) {
            while (service.comprehendJob(job.jobId)?.status != StudyComprehendBuildStatus.READY) delay(5)
        }
    }

    @Test
    fun `retry after partial asset failure skips scene design and all already succeeded sprites speech and music`() = runBlocking {
        val aiCalls = AtomicInteger(0)
        val portraitCallsByPrompt = ConcurrentHashMap<String, AtomicInteger>()
        val ttsCallsByLine = ConcurrentHashMap<String, AtomicInteger>()
        val musicCallsByPrompt = ConcurrentHashMap<String, AtomicInteger>()
        val latestPersistedCheckpoint = AtomicReference<StudyComprehendWorld?>(null)

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
                aiCalls.incrementAndGet()
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
                val attempt = portraitCallsByPrompt.computeIfAbsent(prompt) { AtomicInteger(0) }.incrementAndGet()
                if ("pine tree" in prompt.lowercase() && attempt <= 3) {
                    throw IllegalStateException("Persistent sprite failure on pine tree attempt $attempt")
                }
                return "https://cdn.example/sprite-${kotlin.math.abs(prompt.hashCode())}-$attempt.png"
            }
        }

        val ttsGenerator = StudyTalkAudioGenerator { text, _ ->
            val attempt = ttsCallsByLine.computeIfAbsent(text) { AtomicInteger(0) }.incrementAndGet()
            "https://cdn.example/tts-${kotlin.math.abs(text.hashCode())}-$attempt.mp3"
        }

        val musicGenerator = StudyComprehendMusicGenerator { prompt ->
            val attempt = musicCallsByPrompt.computeIfAbsent(prompt) { AtomicInteger(0) }.incrementAndGet()
            // Fail the 3rd scene's music across all 3 automatic attempts of job 1, then succeed on manual retry
            if ("Atmospheric scene 3" in prompt && attempt <= 3) {
                throw IllegalStateException("Persistent fun-music-v1 failure on scene 3 attempt $attempt")
            }
            "https://cdn.example/music-${kotlin.math.abs(prompt.hashCode())}-$attempt.mp3"
        }

        // Simulate two separate StudyService instances (or server restart!) sharing only the persisted seedWorld checkpoint
        fun newService() = StudyService(
            aiClient = fakeAi,
            imageGenerator = fakeImages,
            scope = CoroutineScope(Dispatchers.Default),
            talkAudioGenerator = ttsGenerator,
            musicGenerator = musicGenerator,
            comprehendRetryBackoffMillis = listOf(5L, 5L, 5L),
        )

        val firstService = newService()
        val request = sampleRequest()

        val firstJob = firstService.startComprehendJob(
            rawRequest = request,
            seedWorld = null,
        ) { checkpointWorld, _ ->
            latestPersistedCheckpoint.set(checkpointWorld)
        }
        var polledFirst = firstService.comprehendJob(firstJob.jobId)!!
        repeat(80) {
            if (polledFirst.status == StudyComprehendBuildStatus.FAILED ||
                polledFirst.status == StudyComprehendBuildStatus.READY
            ) return@repeat
            delay(25)
            polledFirst = firstService.comprehendJob(firstJob.jobId)!!
        }
        assertEquals(StudyComprehendBuildStatus.FAILED, polledFirst.status)

        val savedPartial = assertNotNull(latestPersistedCheckpoint.get())
        assertFalse(savedPartial.isReadyToPlay())
        assertNotNull(savedPartial.playerSpriteUrl)
        assertEquals(3, savedPartial.sprites.count { !it.imageUrl.isNullOrBlank() })
        assertEquals(1, savedPartial.sprites.count { it.imageUrl.isNullOrBlank() })
        assertEquals(2, savedPartial.scenes.count { !it.musicAudioUrl.isNullOrBlank() })
        assertEquals(1, savedPartial.scenes.count { it.musicAudioUrl.isNullOrBlank() })

        // Retry on a brand-new StudyService seeded from savedPartial (verifies persisted checkpoint alone is enough!)
        val secondService = newService()
        val retryJob = secondService.startComprehendJob(
            rawRequest = request,
            seedWorld = savedPartial,
        ) { checkpointWorld, _ ->
            latestPersistedCheckpoint.set(checkpointWorld)
        }
        // Retry must skip GENERATING_SCENES and start directly in GENERATING_ASSETS with only the 2 failed steps remaining!
        assertEquals(StudyComprehendBuildStatus.GENERATING_ASSETS, retryJob.status)
        assertEquals(retryJob.totalSteps - 2, retryJob.completedSteps)

        var polledRetry = secondService.comprehendJob(retryJob.jobId)!!
        repeat(80) {
            if (polledRetry.status == StudyComprehendBuildStatus.READY ||
                polledRetry.status == StudyComprehendBuildStatus.FAILED
            ) return@repeat
            delay(25)
            polledRetry = secondService.comprehendJob(retryJob.jobId)!!
        }
        assertEquals(StudyComprehendBuildStatus.READY, polledRetry.status)
        val finalWorld = assertNotNull(polledRetry.world)
        assertTrue(finalWorld.isReadyToPlay())
        assertTrue(finalWorld.hasAllAssets(requireAudio = true, requireMusic = true))
        assertTrue(secondService.isComprehendWorldComplete(finalWorld))

        // Verify Qwen heavy scene design ran ONLY ONCE across both attempts
        assertEquals(1, aiCalls.get(), "Scene design must NOT re-run on retry")

        // Verify every sprite except pine-tree ran ONLY ONCE, and pine-tree ran 4 times (3 auto attempts + 1 manual retry)
        portraitCallsByPrompt.forEach { (prompt, calls) ->
            if ("pine tree" in prompt.lowercase()) {
                assertEquals(4, calls.get(), "Failed pine-tree sprite should try 3 automatic attempts + 1 manual retry")
            } else {
                assertEquals(1, calls.get(), "Already-succeeded sprite must NOT regenerate on retry: $prompt")
            }
        }

        // Verify every TTS line ran ONLY ONCE
        assertTrue(ttsCallsByLine.isNotEmpty())
        ttsCallsByLine.forEach { (line, calls) ->
            assertEquals(1, calls.get(), "Already-succeeded TTS line must NOT regenerate on retry: $line")
        }

        // Verify scenes 1 & 2 music ran ONLY ONCE, and scene 3 music ran 4 times (3 auto attempts + 1 manual retry)
        assertEquals(3, musicCallsByPrompt.size)
        musicCallsByPrompt.forEach { (prompt, calls) ->
            assertContains(prompt, "Purely instrumental background music")
            if ("Atmospheric scene 3" in prompt) {
                assertEquals(4, calls.get(), "Failed scene 3 music should try 3 automatic attempts + 1 manual retry")
            } else {
                assertEquals(1, calls.get(), "Already-succeeded scene music must NOT regenerate on retry: $prompt")
            }
        }
    }

    @Test
    fun `every comprehend step automatically retries up to 3 attempts and succeeds without manual retry`() = runBlocking {
        val aiCalls = AtomicInteger(0)
        val playerCalls = AtomicInteger(0)
        val pineTreeCalls = AtomicInteger(0)
        val firstTtsCalls = AtomicInteger(0)
        val scene2MusicCalls = AtomicInteger(0)
        val seenFailedStatus = AtomicReference(false)

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
                val attempt = aiCalls.incrementAndGet()
                if (attempt <= 2) {
                    throw IllegalStateException("Transient scene generation error on attempt $attempt/3")
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
                if ("explorer character sprite" in prompt.lowercase()) {
                    val attempt = playerCalls.incrementAndGet()
                    if (attempt <= 2) {
                        throw java.io.EOFException("Not enough data available on player sprite attempt $attempt/3")
                    }
                    return "https://cdn.example/player-ok-$attempt.png"
                }
                if ("pine tree" in prompt.lowercase()) {
                    val attempt = pineTreeCalls.incrementAndGet()
                    if (attempt <= 2) {
                        throw IllegalStateException("HTTP 429 rate limit on pine tree attempt $attempt/3")
                    }
                    return "https://cdn.example/pine-ok-$attempt.png"
                }
                return "https://cdn.example/sprite-${kotlin.math.abs(prompt.hashCode())}.png"
            }
        }

        val firstSynthesizedText = AtomicReference<String?>(null)
        val ttsGenerator = StudyTalkAudioGenerator { text, _ ->
            firstSynthesizedText.compareAndSet(null, text)
            if (text == firstSynthesizedText.get()) {
                val attempt = firstTtsCalls.incrementAndGet()
                if (attempt <= 2) {
                    throw QwenSpeechException("Transient TTS failure on attempt $attempt/3")
                }
                return@StudyTalkAudioGenerator "https://cdn.example/tts-ok-$attempt.mp3"
            }
            "https://cdn.example/tts-${kotlin.math.abs(text.hashCode())}.mp3"
        }

        val musicGenerator = StudyComprehendMusicGenerator { prompt ->
            if ("Atmospheric scene 2" in prompt) {
                val attempt = scene2MusicCalls.incrementAndGet()
                if (attempt <= 2) {
                    throw QwenMusicException("Transient fun-music-v1 503 on attempt $attempt/3")
                }
                return@StudyComprehendMusicGenerator "https://cdn.example/music-s2-ok-$attempt.mp3"
            }
            "https://cdn.example/music-${kotlin.math.abs(prompt.hashCode())}.mp3"
        }

        val service = StudyService(
            aiClient = fakeAi,
            imageGenerator = fakeImages,
            scope = CoroutineScope(Dispatchers.Default),
            talkAudioGenerator = ttsGenerator,
            musicGenerator = musicGenerator,
            comprehendRetryBackoffMillis = listOf(5L, 5L, 5L),
        )

        val job = service.startComprehendJob(sampleRequest())
        var polled = service.comprehendJob(job.jobId)!!
        repeat(120) {
            if (polled.status == StudyComprehendBuildStatus.FAILED) {
                seenFailedStatus.set(true)
            }
            if (polled.status == StudyComprehendBuildStatus.READY ||
                polled.status == StudyComprehendBuildStatus.FAILED
            ) return@repeat
            delay(20)
            polled = service.comprehendJob(job.jobId)!!
        }

        assertFalse(seenFailedStatus.get(), "Job must never enter FAILED when steps succeed within 3 attempts")
        assertEquals(StudyComprehendBuildStatus.READY, polled.status)
        val readyWorld = assertNotNull(polled.world)
        assertTrue(readyWorld.isReadyToPlay())
        assertTrue(service.isComprehendWorldComplete(readyWorld))

        assertEquals(3, aiCalls.get(), "Scene design recovered on attempt 3/3")
        assertEquals(3, playerCalls.get(), "Player sprite recovered on attempt 3/3")
        assertEquals(3, pineTreeCalls.get(), "Object sprite recovered on attempt 3/3")
        assertEquals(3, firstTtsCalls.get(), "Character TTS recovered on attempt 3/3")
        assertEquals(3, scene2MusicCalls.get(), "Scene music recovered on attempt 3/3")
    }

    @Test
    fun `DashScopeMusicClient sends fun-music-v1 instrumental MP3 request and downloads audio bytes`() = runBlocking {
        val tempEnv = Files.createTempFile("fun-music-env-", ".env").toFile()
        try {
            tempEnv.writeText(
                """
                QWEN_API_KEY=sk-music-test
                QWEN_ASR_WORKSPACE_ID=ws-beijing-123
                """.trimIndent(),
            )
            val loadedConfig = AppConfig.load(tempEnv)
            assertEquals(
                "https://ws-beijing-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/music/generation",
                loadedConfig.qwenChat.musicEndpoint,
            )
            assertEquals("fun-music-v1", loadedConfig.qwenChat.musicModel)

            val capturedRequests = CopyOnWriteArrayList<Pair<String, String>>()
            val expectedMp3Bytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x11, 0x22, 0x33)
            val mockEngine = MockEngine { req ->
                val url = req.url.toString()
                when {
                    url.endsWith("/api/v1/services/audio/music/generation") -> {
                        assertEquals("Bearer sk-music-test", req.headers[HttpHeaders.Authorization])
                        val bodyText = (req.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        capturedRequests += url to bodyText
                        respond(
                            content = """
                                {
                                  "output": {
                                    "audio": {
                                      "data": "",
                                      "id": "audio_123",
                                      "url": "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/fun-music/track-1.mp3"
                                    },
                                    "finish_reason": "stop"
                                  },
                                  "request_id": "req-music-1"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    url == "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/fun-music/track-1.mp3" -> {
                        respond(
                            content = expectedMp3Bytes,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "audio/mpeg"),
                        )
                    }

                    else -> error("Unexpected URL: $url")
                }
            }
            val httpClient = HttpClient(mockEngine)
            val musicClient = DashScopeMusicClient(loadedConfig.qwenChat, httpClient)

            val generated = musicClient.generateInstrumental("Misty Vietnamese garden at dawn, soft bamboo flute and zither")
            assertEquals("audio/mpeg", generated.contentType)
            assertEquals("mp3", generated.extension)
            assertTrue(expectedMp3Bytes.contentEquals(generated.bytes))

            val (postUrl, postBody) = capturedRequests.single()
            assertEquals(
                "https://ws-beijing-123.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/music/generation",
                postUrl,
            )
            val jsonObj = Json.parseToJsonElement(postBody).jsonObject
            assertEquals("fun-music-v1", jsonObj["model"]?.jsonPrimitive?.content)
            val inputObj = assertNotNull(jsonObj["input"]?.jsonObject)
            assertEquals(true, inputObj["is_instrumental"]?.jsonPrimitive?.boolean)
            assertEquals("mp3", inputObj["format"]?.jsonPrimitive?.content)
            assertEquals(false, inputObj["enable_aigc_watermark"]?.jsonPrimitive?.boolean)
            assertContains(inputObj["prompt"]?.jsonPrimitive?.content.orEmpty(), "bamboo flute")
            assertNull(inputObj["lyrics"])
            assertNull(inputObj["gender"])

            httpClient.close()
        } finally {
            tempEnv.delete()
        }
    }

    @Test
    fun `system prompt removes hardcoded axial coordinates and sessions receive distinct layout blueprints`() = runBlocking {
        val capturedMessages = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedMessages += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
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
            chatHeavyModel = "qwen-heavy",
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
        aiClient.generateComprehendWorld(sampleRequest())
        httpClient.close()

        val body = capturedMessages.single()
        assertFalse("\"playerSpawnX\": 16" in body, "System prompt must not anchor playerSpawnX to 16")
        assertFalse("\"playerSpawnZ\": 28" in body, "System prompt must not anchor playerSpawnZ to 28")
        assertFalse("Courtyard Gate" in body, "System prompt must not anchor example titles to Courtyard Gate")
        assertContains(body, "EVERYTHING IS CUSTOM PER SCENE")
        assertContains(body, "groundPaths")
        assertContains(body, "wallLines")

        // Within a single session, all 3 scenes receive distinct entry regions and distinct spatial topologies
        val sessionA = sampleRequest()
        val blueprintsA = selectComprehendLayoutBlueprints(sessionA)
        assertEquals(3, blueprintsA.map { it.entryId }.distinct().size)
        assertEquals(3, blueprintsA.map { it.topologyId }.distinct().size)
        // Deterministic across retries of the exact same session
        assertEquals(blueprintsA, selectComprehendLayoutBlueprints(sessionA))

        // Across different study sessions, layout combinations genuinely vary
        val distinctBlueprints = (1..8).map { n ->
            selectComprehendLayoutBlueprints(
                sessionA.copy(
                    sessionId = "different-session-$n",
                    text = "Bài học số $n tại khu phố cổ nhộn nhịp bên bờ sông.",
                ),
            ).map { "${it.entryId}:${it.topologyId}" }
        }.toSet()
        assertTrue(distinctBlueprints.size >= 6, "Different study sessions must receive distinct 3-scene spatial blueprints")
    }

    @Test
    fun `parser builds custom elliptical zones winding paths and 3d wall partitions with doorways`() {
        val customSceneJson = """
            {
              "setting": "Custom multi-scenario adventure",
              "sprites": [
                {"id": "char-1", "isCharacter": true, "visualPrompt": "Tea master"},
                {"id": "herb-jar", "visualPrompt": "Ceramic herb jar"},
                {"id": "wood-door", "visualPrompt": "Carved sliding door"},
                {"id": "teacup", "visualPrompt": "Porcelain teacup"}
              ],
              "scenes": [
                ${buildCustomRichSceneJson(1)},
                ${buildCustomRichSceneJson(2)},
                ${buildCustomRichSceneJson(3)}
              ]
            }
        """.trimIndent()

        val world = QwenStudyComprehendParser.parse(customSceneJson)
        val scene1 = world.scenes.first()
        assertEquals("Diagonal canal & partitioned apothecary", scene1.layoutSummary)
        assertEquals(apap.oneverandah.api.StudyComprehendGroundType.WOOD_WALL, scene1.wallMaterial)
        assertEquals(apap.oneverandah.api.StudyComprehendGroundType.WATER, scene1.surroundingGround)
        assertEquals("#e9c46a", scene1.ambientTintHex)
        assertEquals(135f, scene1.initialCameraYawDegrees)
        assertEquals(4, scene1.playerSpawnX)
        assertEquals(15, scene1.playerSpawnZ)

        // Elliptical courtyard centered at (20, 20) with radius 3 rendered TERRACOTTA_TILE
        assertEquals(
            apap.oneverandah.api.StudyComprehendGroundType.TERRACOTTA_TILE,
            scene1.tileAt(20, 20)?.ground,
        )
        // Winding groundPath painted COBBLESTONE
        assertEquals(
            apap.oneverandah.api.StudyComprehendGroundType.COBBLESTONE,
            scene1.tileAt(8, 15)?.ground,
        )
        // wallLines painted WOOD_WALL along x=14, z=8..20 except doorway gap at (14, 15)
        assertEquals(
            apap.oneverandah.api.StudyComprehendGroundType.WOOD_WALL,
            scene1.tileAt(14, 10)?.ground,
        )
        assertFalse(
            scene1.tileAt(14, 15)?.ground?.isWallStructure == true,
            "Doorway coordinate in wallLines must clear the 3D wall block",
        )
        // Every character is reachable from the custom West-edge spawn (4, 15)
        val init = StudyComprehendScenePlayState.initial(scene1)
        val charObj = scene1.objects.first { it.isCharacter }
        assertTrue(scene1.findWalkPath(init, scene1.playerSpawnX, scene1.playerSpawnZ, charObj.x, charObj.z).isNotEmpty())
    }

    @Test
    fun `music prompt builders scrub vocal song lyric triggers and enforce bilingual pure instrumental rules`() {
        val parsed = QwenStudyComprehendParser.parse(validThreeSceneComprehendJson())
        val noisyScene = parsed.scenes.first().copy(
            timeOfDay = apap.oneverandah.api.StudyComprehendTimeOfDay.NIGHT,
            musicPrompt = "[verse] Gentle folk song and soft lullaby ballad with female choir vocals and humming lyrics",
            description = "A romantic evening song by the river where singers chant",
        )
        val builtPrompt = comprehendSceneMusicPrompt(parsed, noisyScene, "ru")
        assertContains(builtPrompt, "Purely instrumental background music")
        assertContains(builtPrompt, "纯音乐")
        assertContains(builtPrompt, "无人声")
        assertContains(builtPrompt, "无歌词")
        assertContains(builtPrompt, "no vocals")
        assertContains(builtPrompt, "no lyrics")
        assertContains(builtPrompt, "no singing")
        assertContains(builtPrompt, "lead melody on solo cello")
        assertFalse("[verse]" in builtPrompt.lowercase())
        assertFalse("lullaby" in builtPrompt.lowercase(), "Must not include vocal-inducing word 'lullaby'")
        assertFalse("lyrical" in builtPrompt.lowercase(), "Must not include lyric-root word 'lyrical'")
        assertFalse("female choir" in builtPrompt.lowercase())
        assertFalse("ballad" in builtPrompt.lowercase())

        val enforcedClientPrompt = DashScopeMusicClient.enforceInstrumentalOnlyPrompt(
            "[chorus] Acoustic guitar pop song with warm vocals",
        )
        assertContains(enforcedClientPrompt, "纯音乐")
        assertContains(enforcedClientPrompt, "no vocals")
        assertContains(enforcedClientPrompt, "no lyrics")
        assertFalse("[chorus]" in enforcedClientPrompt.lowercase())
        assertFalse("pop song" in enforcedClientPrompt.lowercase())
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

    private fun buildCustomRichSceneJson(idx: Int): String {
        val decoys = (1..16).joinToString(",") { n ->
            """{
                "id": "custom-$idx-jar-$n",
                "spriteId": "herb-jar",
                "x": ${5 + (n * 3) % 24},
                "z": ${3 + (n * 5) % 26},
                "targetName": "Bình thảo mộc $n",
                "actions": [{"id": "custom-$idx-inspect-$n", "kind": "INSPECT", "label": "Inspect"}]
            }"""
        }
        return """
            {
              "id": "custom-scene-$idx",
              "title": "Harbor Apothecary $idx",
              "description": "Waterfront timber tea house $idx",
              "layoutSummary": "Diagonal canal & partitioned apothecary",
              "musicPrompt": "Gentle bamboo flute and zither instrumental score",
              "environment": "${if (idx == 2) "INDOOR" else "OUTDOOR"}",
              "timeOfDay": "EVENING",
              "weather": "BREEZY",
              "ambientTintHex": "#e9c46a",
              "surroundingGround": "WATER",
              "wallMaterial": "WOOD_WALL",
              "initialCameraYawDegrees": 135,
              "playerSpawnX": ${3 + idx},
              "playerSpawnZ": 15,
              "baseGround": "TATAMI_MAT",
              "groundZones": [
                {"shape": "ELLIPSE", "cx": 20, "cz": 20, "rx": 3, "rz": 3, "ground": "TERRACOTTA_TILE"}
              ],
              "groundPaths": [
                {"ground": "COBBLESTONE", "width": 2, "points": [[4, 15], [10, 15], [18, 12]]}
              ],
              "wallLines": [
                {"ground": "WOOD_WALL", "points": [[14, 8], [14, 20]], "doorways": [[14, 15]]}
              ],
              "objects": [
                {
                  "id": "custom-$idx-master",
                  "spriteId": "char-1",
                  "x": 20,
                  "z": 12,
                  "targetName": "Nghệ nhân",
                  "isCharacter": true,
                  "spokenText": "Bạn mang chén sứ trên kệ lại đây giúp tôi nhé.",
                  "acceptSpokenText": "Cảm ơn bạn nhiều!",
                  "declineSpokenText": "Chưa đúng món rồi.",
                  "actions": [
                    {"id": "custom-$idx-listen", "kind": "LISTEN", "label": "Listen"},
                    {"id": "custom-$idx-give", "kind": "GIVE", "label": "Give", "requiredItemId": "custom-$idx-cup"}
                  ]
                },
                {
                  "id": "custom-$idx-cup",
                  "spriteId": "teacup",
                  "x": 9,
                  "z": 16,
                  "targetName": "Chén sứ",
                  "pickable": true,
                  "actions": [{"id": "custom-$idx-take-cup", "kind": "TAKE", "label": "Take"}]
                },
                $decoys
              ],
              "tasks": [
                {
                  "id": "custom-$idx-task",
                  "description": "Listen to the tea master and bring what they need",
                  "requiredCharacterObjectIds": ["custom-$idx-master"],
                  "requiredActionIds": ["custom-$idx-give"]
                }
              ]
            }
        """.trimIndent()
    }
}
