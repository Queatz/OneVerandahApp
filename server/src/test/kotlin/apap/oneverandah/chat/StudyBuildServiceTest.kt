package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildRetryRequest
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTalkScene
import apap.oneverandah.api.StudyTalkTurn
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordRecallLine
import apap.oneverandah.images.qwen.QwenException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StudyBuildServiceTest {
    @Test
    fun `talk generation rejects replies outside the study vocabulary pool`() = runBlocking {
        val ai = FakeBuildStudyAi(CopyOnWriteArrayList(), invalidTalkReply = true)
        val study = StudyService(aiClient = ai)
        val failure = assertFailsWith<IllegalArgumentException> {
            study.talkWorld(
                StudyTalkGenerationRequest(
                    text = "I go.",
                    textLanguageTag = "vi",
                    languageTag = "en",
                    sentences = listOf(
                        StudySentence("I go.", listOf(StudyToken("I", StudyTokenStatus.READY))),
                    ),
                    characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
                ),
            )
        }

        assertContains(failure.message.orEmpty(), "study pool")
    }

    @Test
    fun `talk generation allows natural character lines and matches punctuated words from multiword pool entries`() = runBlocking {
        val study = StudyService(
            aiClient = FakeBuildStudyAi(
                events = CopyOnWriteArrayList(),
                characterLineWithoutVocabulary = true,
            ),
        )

        val world = study.talkWorld(
            StudyTalkGenerationRequest(
                text = "XIN!",
                textLanguageTag = "vi",
                languageTag = "en",
                sentences = listOf(
                    StudySentence("XIN!", listOf(StudyToken("xin chào", StudyTokenStatus.READY))),
                ),
                characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
            ),
        )

        val turn = world.scenes.first().turns.first()
        assertEquals("A natural greeting.", turn.characterLine)
        assertEquals("XIN!", turn.reply)
    }

    @Test
    fun `talk generation rejects the same conversation repeated across scenes`() = runBlocking {
        val study = StudyService(
            aiClient = FakeBuildStudyAi(
                events = CopyOnWriteArrayList(),
                duplicateTalkConversations = true,
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            study.talkWorld(
                StudyTalkGenerationRequest(
                    text = "Xin chào.",
                    textLanguageTag = "vi",
                    languageTag = "en",
                    sentences = listOf(
                        StudySentence("Xin chào.", listOf(StudyToken("Xin", StudyTokenStatus.READY))),
                    ),
                    characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
                ),
            )
        }

        assertContains(failure.message.orEmpty(), "different conversations")
    }

    @Test
    fun `talk generation rejects people and dialogue references in scene backgrounds`() = runBlocking {
        val study = StudyService(
            aiClient = FakeBuildStudyAi(
                events = CopyOnWriteArrayList(),
                talkDescriptionWithPeople = true,
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            study.talkWorld(
                StudyTalkGenerationRequest(
                    text = "Xin chào.",
                    textLanguageTag = "vi",
                    languageTag = "en",
                    sentences = listOf(
                        StudySentence("Xin chào.", listOf(StudyToken("Xin", StudyTokenStatus.READY))),
                    ),
                    characters = listOf(StudyStoryCharacter("main", "Lan", "main", "A neighbor.")),
                ),
            )
        }

        assertContains(failure.message.orEmpty(), "empty scenery")
    }

    @Test
    fun `build completes without a client and duplicate create reuses the record`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val ai = FakeBuildStudyAi(events)
        val images = FakeBuildImageGenerator(events)
        val audioCalls = AtomicInteger()
        val (sessions, service) = harness(repository, devices, ai, images, scope, audioCalls = audioCalls)
        try {
            val request = StudyBuildRequest(
                text = "Please say hello in Vietnamese.",
                languageTag = "en",
                sourceMessageId = "build-source",
            )
            val created = service.create(token, request)
            assertTrue(created.session.sentences.isEmpty())

            val completed = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.COMPLETE, completed.build?.status)
            assertTrue(completed.name.isNotBlank())
            assertEquals(2, completed.session.sentences.single().tokens.count { it.status == StudyTokenStatus.READY })
            assertEquals(2, completed.session.sentences.single().chunks.size)
            assertEquals(listOf(listOf(0), listOf(1)), completed.session.sentences.single().chunks.map { it.wordIndices })
            assertEquals(listOf("chào.", "Xin chào."), completed.session.sentences.single().breakdownSuffixes)
            assertTrue(completed.session.sentences.single().breakdownAudioReady)
            assertTrue(completed.session.sentences.single().tokens.filter { it.isDissectable }
                .all { !it.imageUrl.isNullOrBlank() })
            assertTrue(completed.session.sentences.single().tokens.filter { it.isDissectable }
                .all { it.needConversation.isNotEmpty() })
            assertTrue(events.indexOf("dissect") < events.indexOf("word-recall-scenarios"))
            assertTrue(completed.session.sentences.single().imageUrl?.isNotBlank() == true)
            assertTrue(completed.session.characters.single().imageUrl?.isNotBlank() == true)
            assertEquals(4, completed.session.playScenarios.size)
            assertTrue(completed.session.playScenarios.all { !it.imageUrl.isNullOrBlank() })
            assertTrue(completed.session.examScene?.let { it.beats.size == 20 } == true)
            val talkWorld = assertNotNull(completed.session.talkWorld)
            assertEquals(6, talkWorld.scenes.size)
            assertTrue(talkWorld.scenes.all { it.turns.size in 3..6 && !it.backgroundImageUrl.isNullOrBlank() })
            val usedExpressionIds = talkWorld.scenes.flatMap { it.turns }.map { it.expressionId }.toSet()
            assertTrue(usedExpressionIds.isNotEmpty())
            assertTrue(talkWorld.expressions.filter { it.id in usedExpressionIds }.all { !it.imageUrl.isNullOrBlank() })
            assertTrue(talkWorld.expressions.filter { it.id !in usedExpressionIds }.all { it.imageUrl.isNullOrBlank() })
            assertTrue(talkWorld.scenes.flatMap { it.turns }.all {
                !it.characterAudioUrl.isNullOrBlank() && !it.replyAudioUrl.isNullOrBlank()
            })
            assertEquals(1, ai.skeletonCalls.get())
            assertTrue(images.referenceImageCalls.get() >= 5)
            assertTrue(events.indexOf("portrait-image") in 0 until events.indexOf("reference-image"))

            val duplicate = service.create(token, request)
            assertEquals(created.id, duplicate.id)
            assertEquals(1, ai.skeletonCalls.get())

            val reset =
                service.create(token, request.copy(text = "Please say hello in Vietnamese.", languageTag = "fr"))
            assertEquals(created.id, reset.id)
            val rebuilt = awaitTerminal(service, reset.id)
            assertEquals("fr", rebuilt.languageTag)
            assertEquals(2, ai.skeletonCalls.get())

            val originalWorld = assertNotNull(rebuilt.session.talkWorld)
            val audioCallsBeforeBackfill = audioCalls.get()
            val talkCallsBeforeBackfill = ai.talkWorldCalls.get()
            val missingBackgroundWorld = originalWorld.copy(
                scenes = originalWorld.scenes.mapIndexed { index, scene ->
                    if (index == 0) scene.copy(backgroundImageUrl = null) else scene
                },
            )
            sessions.sessionStore.update(reset.id) { current ->
                current.copy(session = current.session.copy(talkWorld = missingBackgroundWorld))
            }
            service.create(token, request.copy(languageTag = "fr"))
            val backfilled = awaitTerminal(service, reset.id)
            val restoredWorld = assertNotNull(backfilled.session.talkWorld)
            assertTrue(!restoredWorld.scenes.first().backgroundImageUrl.isNullOrBlank())
            assertEquals(originalWorld.scenes.drop(1), restoredWorld.scenes.drop(1))
            assertEquals(originalWorld.scenes.first().turns, restoredWorld.scenes.first().turns)
            assertEquals(originalWorld.expressions, restoredWorld.expressions)
            assertEquals(audioCallsBeforeBackfill, audioCalls.get())
            assertEquals(talkCallsBeforeBackfill, ai.talkWorldCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `translate preparation metadata resumes after a fresh service starts`() = runBlocking {
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstAi = FakeBuildStudyAi(CopyOnWriteArrayList(), translationGate = CompletableDeferred())
        val firstImages = FakeBuildImageGenerator(CopyOnWriteArrayList())
        val (_, firstService) = harness(repository, devices, firstAi, firstImages, firstScope)
        val request = StudyBuildRequest(
            text = "Hello.",
            languageTag = "en",
            sourceMessageId = "translate-resume",
            mode = StudyBuildMode.TRANSLATE,
            targetLanguageTag = "vi",
            sourceLanguageTag = "en",
        )

        val created = firstService.create(token, request)
        withTimeout(2_000L) { firstAi.translationStarted.await() }
        assertEquals(request.normalizedOrNull(), repository.findById(created.id)?.buildRequest)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val secondAi = FakeBuildStudyAi(CopyOnWriteArrayList())
        val secondImages = FakeBuildImageGenerator(CopyOnWriteArrayList())
        val (_, secondService) = harness(repository, devices, secondAi, secondImages, secondScope)
        try {
            secondService.resumeAll()
            val completed = awaitTerminal(secondService, created.id)
            assertEquals(StudyBuildStatus.COMPLETE, completed.build?.status)
            assertEquals("vi", completed.textLanguageTag)
            assertEquals("vi:Hello.", completed.text)
            assertEquals(1, secondAi.translationCalls.get())
            assertEquals(null, repository.findById(created.id)?.buildRequest)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `permanent image failures can be retried manually`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val ai = FakeBuildStudyAi(events)
        val images = FakeBuildImageGenerator(events, fail = AtomicBoolean(true))
        val (_, service) = harness(repository, devices, ai, images, scope, maxAttempts = 2)
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Hello.", languageTag = "en", sourceMessageId = "retry-images"),
            )
            val partial = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.PARTIAL, partial.build?.status)
            assertTrue(partial.build?.failed?.any { it.kind in IMAGE_ELEMENTS } == true)
            val selectedFailure = partial.build!!.failed.first()
            assertContains(partial.build!!.failureReasons[selectedFailure.key].orEmpty(), "temporary image failure")

            images.fail.set(false)
            service.retry(token, created.id, StudyBuildRetryRequest(elements = listOf(selectedFailure)))
            val retriedOne = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.PARTIAL, retriedOne.build?.status)
            assertFalse(retriedOne.build?.failed.orEmpty().any { it.key == selectedFailure.key })
            assertFalse(retriedOne.build?.failureReasons.orEmpty().containsKey(selectedFailure.key))
            assertTrue(retriedOne.build?.failed.isNullOrEmpty().not())

            assertNotNull(service.retry(token, created.id, StudyBuildRetryRequest()))
            val completed = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.COMPLETE, completed.build?.status)
            assertTrue(completed.build?.failed.isNullOrEmpty())
            assertTrue(completed.session.sentences.single().imageUrl?.isNotBlank() == true)
            assertTrue(completed.session.characters.single().imageUrl?.isNotBlank() == true)
            assertTrue(completed.session.playScenarios.all { !it.imageUrl.isNullOrBlank() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `talk expression returning a blank image url is tracked as a failure`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val images = FakeBuildImageGenerator(events).apply { blankTalkExpressions.set(true) }
        val (_, service) = harness(
            repository,
            devices,
            FakeBuildStudyAi(events),
            images,
            scope,
            maxAttempts = 3,
        )
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Hello.", languageTag = "en", sourceMessageId = "blank-talk-expression"),
            )

            val partial = awaitTerminal(service, created.id)
            val failedExpression = partial.build?.failed.orEmpty().firstOrNull {
                it.kind == StudyBuildElementKind.TALK_EXPRESSION
            }

            assertNotNull(failedExpression)
            assertContains(partial.build?.failureReasons?.get(failedExpression.key).orEmpty(), "did not produce")
            assertEquals(3, partial.build?.attempts?.get(failedExpression.key))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `retry rebuilds an incomplete talk expression even without a recorded failure`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val images = FakeBuildImageGenerator(events)
        val (sessions, service) = harness(repository, devices, FakeBuildStudyAi(events), images, scope)
        val missingExpression = StudyBuildElement(
            kind = StudyBuildElementKind.TALK_EXPRESSION,
            expressionId = "main-happy",
        )
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Hello.", languageTag = "en", sourceMessageId = "retry-incomplete-talk"),
            )
            val complete = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.COMPLETE, complete.build?.status)
            sessions.sessionStore.update(created.id, immediate = true) { current ->
                val world = requireNotNull(current.session.talkWorld)
                current.copy(
                    session = current.session.copy(
                        talkWorld = world.copy(
                            expressions = world.expressions.map {
                                if (it.id == missingExpression.expressionId) it.copy(imageUrl = null) else it
                            },
                        ),
                    ),
                    build = current.build?.copy(
                        status = StudyBuildStatus.PARTIAL,
                        attempts = current.build.attempts + (missingExpression.key to 3),
                    ),
                )
            }

            service.retry(token, created.id, StudyBuildRetryRequest(elements = listOf(missingExpression)))
            val retried = awaitTerminal(service, created.id)

            assertTrue(retried.session.talkWorld?.expressions?.first { it.id == missingExpression.expressionId }
                ?.imageUrl.isNullOrBlank().not())
            assertEquals(1, retried.build?.attempts?.get(missingExpression.key))
            assertFalse(retried.build?.failed.orEmpty().any { it.key == missingExpression.key })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `ensure building revives exhausted missing talk backgrounds`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val images = FakeBuildImageGenerator(events)
        val (sessions, service) = harness(repository, devices, FakeBuildStudyAi(events), images, scope)
        val missingBackground = StudyBuildElement(
            kind = StudyBuildElementKind.TALK_SCENE_IMAGE,
            sceneIndex = 0,
        )
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Hello.", languageTag = "en", sourceMessageId = "ensure-incomplete-talk"),
            )
            val complete = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.COMPLETE, complete.build?.status)
            sessions.sessionStore.update(created.id, immediate = true) { current ->
                val world = requireNotNull(current.session.talkWorld)
                current.copy(
                    session = current.session.copy(
                        talkWorld = world.copy(
                            scenes = world.scenes.mapIndexed { index, scene ->
                                if (index == 0) scene.copy(backgroundImageUrl = null) else scene
                            },
                        ),
                    ),
                    build = current.build?.copy(
                        status = StudyBuildStatus.PARTIAL,
                        attempts = current.build.attempts + (missingBackground.key to 3),
                    ),
                )
            }

            service.ensureBuilding(created.id)
            val rebuilt = awaitTerminal(service, created.id)

            assertTrue(rebuilt.session.talkWorld?.scenes?.first()?.backgroundImageUrl.isNullOrBlank().not())
            assertEquals(1, rebuilt.build?.attempts?.get(missingBackground.key))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failure explanations redact provider URLs and inline image data`() {
        val reason = StudyBuildFailureReason.display(
            IllegalStateException(
                "request failed https://provider.example/job?signature=secret data:image/png;base64,verylongpayload",
            ),
        )

        assertTrue(reason.length <= StudyBuildFailureReason.MAX_DISPLAY_LENGTH)
        assertFalse(reason.contains("https://"))
        assertFalse(reason.contains("verylongpayload"))
        assertFalse(reason.contains("signature=secret"))
    }

    @Test
    fun `failure explanations identify interrupted connections and provider statuses`() {
        assertEquals(
            "The provider connection was interrupted before the request completed.",
            StudyBuildFailureReason.display(IllegalStateException("The server prematurely closed the connection")),
        )
        assertEquals(
            "The image provider rejected the request (HTTP 503).",
            StudyBuildFailureReason.display(
                QwenException("Qwen submit failed (503 Service Unavailable): {\"providerDetail\":\"private response\"}"),
            ),
        )
    }

    @Test
    fun `Talk assets build when a character portrait is unavailable`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val images = FakeBuildImageGenerator(events).apply { failPortraits.set(true) }
        val (_, service) = harness(
            repository,
            devices,
            FakeBuildStudyAi(events),
            images,
            scope,
        )
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Please say hello in Vietnamese.", languageTag = "en", sourceMessageId = "talk-without-portrait"),
            )
            val result = awaitTerminal(service, created.id)

            assertEquals(StudyBuildStatus.PARTIAL, result.build?.status)
            assertTrue(result.build?.failed.orEmpty().any { it.kind == StudyBuildElementKind.PORTRAIT })
            val world = assertNotNull(result.session.talkWorld)
            assertTrue(world.scenes.all { !it.backgroundImageUrl.isNullOrBlank() })
            val usedExpressionIds = world.scenes.flatMap { it.turns }.map { it.expressionId }.toSet()
            assertTrue(world.expressions.filter { it.id in usedExpressionIds }.all { !it.imageUrl.isNullOrBlank() })
            assertTrue(world.scenes.flatMap { it.turns }.all {
                !it.characterAudioUrl.isNullOrBlank() && !it.replyAudioUrl.isNullOrBlank()
            })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `session deleted during generation is not recreated by a worker`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val gate = CompletableDeferred<Unit>()
        val ai = FakeBuildStudyAi(CopyOnWriteArrayList(), skeletonGate = gate)
        val images = FakeBuildImageGenerator(CopyOnWriteArrayList())
        val (sessions, service) = harness(repository, devices, ai, images, scope)
        try {
            val created = service.create(
                token,
                StudyBuildRequest(text = "Hello.", languageTag = "en", sourceMessageId = "delete-during-build"),
            )
            withTimeout(2_000L) { ai.skeletonStarted.await() }
            assertTrue(sessions.delete(token, created.id))
            gate.complete(Unit)
            withTimeout(2_000L) { ai.skeletonFinished.await() }
            delay(50L)

            assertEquals(null, repository.findById(created.id))
            assertEquals(null, service.getSession(created.id))
        } finally {
            scope.cancel()
        }
    }

    private fun harness(
        repository: InMemoryStudySessionsRepository,
        devices: DeviceService,
        ai: FakeBuildStudyAi,
        images: FakeBuildImageGenerator,
        scope: CoroutineScope,
        maxAttempts: Int = 3,
        audioCalls: AtomicInteger? = null,
    ): Pair<StudySessionsService, StudyBuildService> {
        val store = StudySessionStore(repository, scope = scope)
        val sessions = StudySessionsService(
            repository = repository,
            deviceService = devices,
            studyAiClient = ai,
            sessionStore = store,
        )
        val study = StudyService(aiClient = ai, imageGenerator = images, scope = scope)
        val builder = StudyBuildService(
            repository = repository,
            deviceService = devices,
            sessionsService = sessions,
            studyService = study,
            scope = scope,
            textWorkers = 2,
            imageWorkers = 3,
            maxAttempts = maxAttempts,
            retryBackoffMillis = listOf(5L, 5L, 5L),
            talkAudioGenerator = StudyTalkAudioGenerator { _, _ ->
                val index = audioCalls?.incrementAndGet() ?: 1
                "https://audio/$index"
            },
        )
        return sessions to builder
    }

    private suspend fun awaitTerminal(service: StudyBuildService, id: String): apap.oneverandah.api.SavedStudySession =
        withTimeout(10_000L) {
            var session = service.getSession(id) ?: error("Study session disappeared")
            while (session.build?.status != StudyBuildStatus.COMPLETE && session.build?.status != StudyBuildStatus.PARTIAL) {
                delay(5L)
                session = service.getSession(id) ?: error("Study session disappeared")
            }
            session
        }

    private class FakeBuildStudyAi(
        private val events: MutableList<String>,
        private val translationGate: CompletableDeferred<Unit>? = null,
        private val skeletonGate: CompletableDeferred<Unit>? = null,
        private val invalidTalkReply: Boolean = false,
        private val characterLineWithoutVocabulary: Boolean = false,
        private val duplicateTalkConversations: Boolean = false,
        private val talkDescriptionWithPeople: Boolean = false,
    ) : StudyAiClient {
        val skeletonCalls = AtomicInteger()
        val talkWorldCalls = AtomicInteger()
        val translationCalls = AtomicInteger()
        val translationStarted = CompletableDeferred<Unit>()
        val skeletonStarted = CompletableDeferred<Unit>()
        val skeletonFinished = CompletableDeferred<Unit>()

        override suspend fun generateStudyIntent(request: StudyGenerationRequest): String =
            "Prepared: ${request.text}"

        override suspend fun translateStudyText(request: apap.oneverandah.api.StudyTranslateRequest): String {
            translationCalls.incrementAndGet()
            events += "translate"
            translationStarted.complete(Unit)
            translationGate?.await()
            return "${request.targetLanguageTag}:${request.text}"
        }

        override suspend fun designIntendedScene(request: StudySceneGenerationRequest): String =
            "A neighbor offers tea in a bright kitchen."

        override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
            skeletonCalls.incrementAndGet()
            events += "skeleton"
            skeletonStarted.complete(Unit)
            skeletonGate?.await()
            val tokens = listOf("Xin", "chào", ".").map { token ->
                StudyToken(
                    text = token,
                    status = if (token == ".") StudyTokenStatus.PLAIN else StudyTokenStatus.PENDING,
                )
            }
            val session = StudySessionResponse(
                text = request.text,
                textLanguageTag = "vi",
                sentences = listOf(StudySentence("Xin chào.", tokens)),
            )
            skeletonFinished.complete(Unit)
            return session
        }

        override suspend fun dissectSentence(request: StudyDissectGenerationRequest): StudySentence {
            events += "dissect"
            return StudySentence(
                text = request.sentenceText,
                tokens = request.tokens.map { token ->
                    if (token == ".") {
                        StudyToken(token, StudyTokenStatus.PLAIN)
                    } else {
                        StudyToken(
                            text = token,
                            status = StudyTokenStatus.READY,
                            literalMeaning = "hello",
                            contextualMeaning = "A friendly greeting.",
                            exampleSentences = listOf(StudyExampleSentence(request.sentenceText, "Hello.")),
                        )
                    }
                },
                translation = "A friendly greeting.",
            )
        }

        override suspend fun generateWordRecallScenarios(
            request: StudyNeedScenarioGenerationRequest,
        ): Map<Int, List<StudyWordRecallLine>> {
            events += "word-recall-scenarios"
            return request.tokens.associate { token ->
                token.tokenIndex to listOf(
                    StudyWordRecallLine("Mina", "___!", "___!"),
                    StudyWordRecallLine("Kai", "___!", "___!"),
                )
            }
        }

        override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
            StudyWordPlaygroundJudgment(natural = true, summary = "Natural.")

        override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest): List<StudyCharacterDesign> {
            events += "characters"
            return listOf(StudyCharacterDesign("main", "Lan", "main", "A friendly young neighbor"))
        }

        override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest): List<StudyPlayScenarioDesign> {
            events += "play-scenarios"
            return (1..4).map { StudyPlayScenarioDesign("A neighbor starts conversation $it.") }
        }

        override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
            StudyPlayTurnDesign(description = "A friendly reply.", reply = "Xin chào.")

        override suspend fun generateExamScene(request: StudyExamSceneGenerationRequest): StudyExamScene {
            events += "exam"
            val sourceSentence = request.sentences.first().text
            val character = request.characters.first()
            return StudyExamScene(
                id = "exam-1",
                setting = "At a neighborhood market.",
                outline = (1..10).map { "Round $it" },
                beats = (1..10).flatMap { round ->
                    listOf(
                        StudyExamBeat(
                            id = "listen-$round",
                            kind = StudyExamBeatKind.LISTEN,
                            speakerId = character.id,
                            speakerName = character.name,
                            line = sourceSentence,
                            prompt = "What did Lan say?",
                            expectedMeaning = "A friendly greeting.",
                            sourceSentence = sourceSentence,
                        ),
                        StudyExamBeat(
                            id = "speak-$round",
                            kind = StudyExamBeatKind.SPEAK,
                            speakerId = character.id,
                            speakerName = character.name,
                            prompt = "Reply to Lan.",
                            expectedMeaning = "A suitable greeting.",
                            sourceSentence = sourceSentence,
                        ),
                    )
                },
            )
        }

        override suspend fun generateTalkWorld(request: StudyTalkGenerationRequest): StudyTalkWorld {
            events += "talk-world"
            talkWorldCalls.incrementAndGet()
            val speakerId = request.characters.first().id
            val sourceSentence = request.sentences.first().text
            val locations = listOf("Apartment lobby", "Corner café", "Flower market", "Riverside walk", "Community garden", "Train platform")
            return StudyTalkWorld(
                setting = "A connected neighborhood story.",
                scenes = List(6) { sceneIndex ->
                    StudyTalkScene(
                        id = "scene-${sceneIndex + 1}",
                        title = locations[sceneIndex],
                        description = if (talkDescriptionWithPeople && sceneIndex == 2) {
                            "People speak inside bright speech bubbles."
                        } else {
                            "An empty view of the ${locations[sceneIndex].lowercase()}."
                        },
                        turns = List(3) { turnIndex ->
                            StudyTalkTurn(
                                id = "turn-${turnIndex + 1}",
                                speakerCharacterId = speakerId,
                                expressionId = "$speakerId-happy",
                                characterLine = when {
                                    duplicateTalkConversations -> sourceSentence
                                    characterLineWithoutVocabulary && sceneIndex == 0 -> "A natural greeting."
                                    characterLineWithoutVocabulary -> "A natural greeting at the ${locations[sceneIndex].lowercase()}."
                                    else -> "A greeting at the ${locations[sceneIndex].lowercase()}."
                                },
                                intention = "I want to say hello.",
                                reply = if (invalidTalkReply) "A reply outside the study pool." else sourceSentence,
                            )
                        },
                    )
                },
            )
        }

        override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
            StudyWriteFeedback("Clear.")

        override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
            StudyExpressJudgment(correct = true, feedback = "Clear.")

        override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
            emptyList<StudyExampleSentence>()
    }

    private class FakeBuildImageGenerator(
        private val events: MutableList<String>,
        val fail: AtomicBoolean = AtomicBoolean(false),
    ) : StudyPlaygroundImageGenerator {
        private val imageCounter = AtomicInteger()
        private val portraitUrls = ConcurrentHashMap.newKeySet<String>()
        val referenceImageCalls = AtomicInteger()
        val failPortraits = AtomicBoolean(false)
        val blankTalkExpressions = AtomicBoolean(false)

        override suspend fun generate(prompt: String, referenceImageUrls: List<String>): String {
            if (referenceImageUrls.isNotEmpty()) {
                check(referenceImageUrls.all { it in portraitUrls })
                referenceImageCalls.incrementAndGet()
                events += "reference-image"
            }
            if (failPortraits.get() && prompt.contains("Character reference portrait")) {
                error("portrait provider temporarily unavailable")
            }
            if (fail.get()) error("temporary image failure")
            if (blankTalkExpressions.get() && prompt.startsWith("Create a full-body visual-novel character sprite")) {
                return " "
            }
            val url = "https://img/${imageCounter.incrementAndGet()}"
            if (prompt.contains("Character reference portrait")) {
                portraitUrls += url
                events += "portrait-image"
            }
            return url
        }
    }

    companion object {
        private val IMAGE_ELEMENTS = setOf(
            StudyBuildElementKind.WORD_IMAGE,
            StudyBuildElementKind.PORTRAIT,
            StudyBuildElementKind.SENTENCE_IMAGE,
            StudyBuildElementKind.PLAY_COVER,
            StudyBuildElementKind.TALK_SCENE_IMAGE,
            StudyBuildElementKind.TALK_EXPRESSION,
        )
    }
}
