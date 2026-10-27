package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElementKind
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
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StudyBuildServiceTest {
    @Test
    fun `build completes without a client and duplicate create reuses the record`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val devices = DeviceService(InMemoryDeviceRepository())
        val token = devices.issueToken().deviceToken
        val events = CopyOnWriteArrayList<String>()
        val ai = FakeBuildStudyAi(events)
        val images = FakeBuildImageGenerator(events)
        val (_, service) = harness(repository, devices, ai, images, scope)
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
            assertTrue(completed.session.sentences.single().tokens.filter { it.isDissectable }
                .all { !it.imageUrl.isNullOrBlank() })
            assertTrue(completed.session.sentences.single().imageUrl?.isNotBlank() == true)
            assertTrue(completed.session.characters.single().imageUrl?.isNotBlank() == true)
            assertEquals(4, completed.session.playScenarios.size)
            assertTrue(completed.session.playScenarios.all { !it.imageUrl.isNullOrBlank() })
            assertTrue(completed.session.examScene?.let { it.beats.size == 20 } == true)
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

            images.fail.set(false)
            val selectedFailure = partial.build!!.failed.first()
            service.retry(token, created.id, StudyBuildRetryRequest(elements = listOf(selectedFailure)))
            val retriedOne = awaitTerminal(service, created.id)
            assertEquals(StudyBuildStatus.PARTIAL, retriedOne.build?.status)
            assertFalse(retriedOne.build?.failed.orEmpty().any { it.key == selectedFailure.key })
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
    ) : StudyAiClient {
        val skeletonCalls = AtomicInteger()
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
            )
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

        override suspend fun generate(prompt: String, referenceImageUrls: List<String>): String {
            if (referenceImageUrls.isNotEmpty()) {
                check(referenceImageUrls.all { it in portraitUrls })
                referenceImageCalls.incrementAndGet()
                events += "reference-image"
            }
            if (fail.get()) error("temporary image failure")
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
        )
    }
}
