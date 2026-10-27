package apap.oneverandah.chat

import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionsServiceTest {
    @Test
    fun `incomplete build query includes preparing and building records only`() = runBlocking {
        val repository = InMemoryStudySessionsRepository()
        val session = StudySessionResponse(text = "Hello.", textLanguageTag = "en", sentences = emptyList())
        val statuses = listOf(
            "preparing" to StudyBuildStatus.PREPARING,
            "building" to StudyBuildStatus.BUILDING,
            "complete" to StudyBuildStatus.COMPLETE,
            "partial" to StudyBuildStatus.PARTIAL,
        )
        statuses.forEachIndexed { index, (id, status) ->
            repository.upsert(
                SavedStudySessionRecord(
                    id = id,
                    deviceTokenHash = "device-hash",
                    languageTag = "en",
                    text = session.text,
                    textLanguageTag = session.textLanguageTag,
                    session = session,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    build = StudyBuildState(status = status),
                ),
            )
        }
        repository.upsert(
            SavedStudySessionRecord(
                id = "legacy",
                deviceTokenHash = "device-hash",
                languageTag = "en",
                text = session.text,
                textLanguageTag = session.textLanguageTag,
                session = session,
                createdAt = 5L,
                updatedAt = 5L,
            ),
        )

        assertEquals(setOf("preparing", "building"), repository.listIncompleteBuilds().toSet())
    }

    @Test
    fun `summary responses include nonblank sentence image urls in order`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "summary-image-study-token-abcdefghijklmnop" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
        )
        val token = deviceService.issueToken().deviceToken
        val session = StudySessionResponse(
            text = "Hello. Hi. Bye.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(text = "Hello.", tokens = emptyList(), imageUrl = " https://img/a "),
                StudySentence(text = "Hi.", tokens = emptyList(), imageUrl = "  "),
                StudySentence(text = "Bye.", tokens = emptyList(), imageUrl = "https://img/b"),
            ),
        )

        val saved = service.save(token, SaveStudySessionRequest(languageTag = "en", session = session))
        val listed = service.list(token).items.single()

        assertEquals(listOf("https://img/a", "https://img/b"), saved.sentenceImageUrls)
        assertEquals(saved.sentenceImageUrls, listed.sentenceImageUrls)
    }

    @Test
    fun `save list and delete are scoped by device token`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            clock = incrementalClock(1_000L),
        )
        val tokenA = deviceService.issueToken().deviceToken
        val tokenB = deviceService.issueToken().deviceToken

        val savedA = service.save(
            tokenA,
            SaveStudySessionRequest(
                sourceMessageId = "msg-1",
                languageTag = "en",
                session = sampleSession("Hello from A"),
            ),
        )
        service.save(
            tokenB,
            SaveStudySessionRequest(
                languageTag = "en",
                session = sampleSession("Hello from B"),
            ),
        )

        val listA = service.list(tokenA).items
        assertEquals(1, listA.size)
        assertEquals(savedA.id, listA.single().id)
        assertEquals("Hello from A", listA.single().text)
        assertEquals("en", listA.single().languageTag)

        val fetchedByB = service.get(tokenB, savedA.id)
        assertEquals(savedA.id, fetchedByB?.id)
        assertEquals("Hello from A", fetchedByB?.text)
        assertNull(service.get(tokenA, "missing-id"))
        assertFailsWith<UnauthorizedDeviceException> {
            service.get(null, savedA.id)
        }

        assertFalse(service.delete(tokenB, savedA.id))
        assertEquals(1, service.list(tokenA).items.size)
        assertTrue(service.delete(tokenA, savedA.id))
        assertTrue(service.list(tokenA).items.isEmpty())
        Unit
    }

    @Test
    fun `saving the same sourceMessageId upserts progressive payload`() = runBlocking {
        var now = 100L
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "same-source-study-token-abcdefghijklmnop" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            clock = { now },
            idGenerator = { "study-1" },
        )
        val token = deviceService.issueToken().deviceToken

        val first = service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = "src-1",
                languageTag = "en",
                session = sampleSession("First", pending = true),
            ),
        )
        now = 200L
        val second = service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = "src-1",
                languageTag = "en",
                session = sampleSession("First", pending = false),
            ),
        )

        assertEquals(first.id, second.id)
        assertEquals(100L, second.createdAt)
        assertEquals(200L, second.updatedAt)
        val fullSession = service.get(token, second.id)
        assertEquals(StudyTokenStatus.READY, fullSession?.session?.sentences?.single()?.tokens?.single()?.status)
        assertEquals(1, service.list(token).items.size)
        Unit
    }

    @Test
    fun `AI generated session name is preserved across progressive saves`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "named-study-token-abcdefghijklmnop" },
        )
        val aiClient = FakeStudyAiClient(sessionName = "Ordering food politely")
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            studyAiClient = aiClient,
            idGenerator = { "named-study-1" },
        )
        val token = deviceService.issueToken().deviceToken

        val first = service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = "named-source",
                languageTag = "en",
                session = sampleSession("Xin chào.", pending = true),
            ),
        )
        val second = service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = "named-source",
                languageTag = "en",
                session = sampleSession("Xin chào.", pending = false),
            ),
        )

        assertEquals("Ordering food politely", first.name)
        assertEquals(first.name, second.name)
        assertEquals(1, aiClient.studySessionNameCalls)
    }

    @Test
    fun `AI name failure falls back to a bounded study text title`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "fallback-study-token-abcdefghijklmnop" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            studyAiClient = FakeStudyAiClient(sessionNameFailure = true),
        )
        val token = deviceService.issueToken().deviceToken
        val text = "A useful phrase for ordering meals ".repeat(5).trim()

        val saved = service.save(token, SaveStudySessionRequest(languageTag = "en", session = sampleSession(text)))

        assertTrue(saved.name.length <= 80)
        assertTrue(saved.name.endsWith("…"))
        assertEquals(text.take(79).trimEnd() + "…", saved.name)
    }

    @Test
    fun `rename is restricted to owning device and updates the session`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            idGenerator = { "rename-study-1" },
        )
        val ownerToken = deviceService.issueToken().deviceToken
        val otherToken = deviceService.issueToken().deviceToken
        val saved = service.save(ownerToken, request("Hello there"))

        assertNull(service.rename(otherToken, saved.id, "Foreign name"))
        val renamed = service.rename(ownerToken, saved.id, "  A warm greeting  ")

        assertEquals("A warm greeting", renamed?.name)
        assertEquals("A warm greeting", service.get(ownerToken, saved.id)?.name)
        assertNull(service.rename(ownerToken, "missing", "Name"))
    }

    @Test
    fun `cap drops oldest sessions for the device`() = runBlocking {
        var now = 0L
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "cap-study-token-abcdefghijklmnopqrstuv" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            clock = { ++now },
            maxPerDevice = 2,
        )
        val token = deviceService.issueToken().deviceToken

        val first = service.save(token, request("one"))
        service.save(token, request("two"))
        service.save(token, request("three"))

        val items = service.list(token).items
        assertEquals(2, items.size)
        assertEquals(listOf("three", "two"), items.map { it.text })
        assertNull(items.find { it.id == first.id })
        Unit
    }

    @Test
    fun `cancelling while generating a session name still persists the session`() = runBlocking {
        val namingStarted = CompletableDeferred<Unit>()
        val finishNaming = CompletableDeferred<String>()
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "cancel-study-token-abcdefghijklmnopqrstuv" },
        )
        val service = StudySessionsService(
            repository = repository,
            deviceService = deviceService,
            idGenerator = { "study-cancel-1" },
            studyAiClient = object : StudyAiClient by FakeStudyAiClient() {
                override suspend fun generateStudySessionName(text: String, languageTag: String): String {
                    namingStarted.complete(Unit)
                    return finishNaming.await()
                }
            },
        )
        val token = deviceService.issueToken().deviceToken
        val saveJob = launch {
            service.save(token, request("A session that must be saved"))
        }

        namingStarted.await()
        saveJob.cancel()
        finishNaming.complete("A saved session")
        saveJob.join()

        assertEquals(
            "A session that must be saved", repository.listByDevice(
                deviceService.findValid(token)!!.tokenHash,
            ).single().text
        )
    }

    @Test
    fun `cancelling while generating a skeleton still persists the session`() = runBlocking {
        val skeletonStarted = CompletableDeferred<Unit>()
        val finishSkeleton = CompletableDeferred<StudySessionResponse>()
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "cancel-skeleton-token-abcdefghijklmnopqrst" },
        )
        val session = sampleSession("A session that must be saved")
        val studyService = StudyService(
            aiClient = object : StudyAiClient by FakeStudyAiClient(skeleton = session) {
                override suspend fun generateSkeleton(request: StudyGenerationRequest): StudySessionResponse {
                    skeletonStarted.complete(Unit)
                    return finishSkeleton.await()
                }
            },
        )
        val service = StudySessionsService(
            repository = repository,
            deviceService = deviceService,
            idGenerator = { "study-skeleton-cancel-1" },
        )
        val token = deviceService.issueToken().deviceToken
        val job = launch {
            service.createFromStudyRequest(
                token,
                StudySessionRequest(
                    text = "A session that must be saved",
                    languageTag = "en",
                    sourceMessageId = "msg-1",
                ),
            ) { studyService.skeleton(it) }
        }

        skeletonStarted.await()
        job.cancel()
        finishSkeleton.complete(session)
        job.join()

        val stored = repository.listByDevice(deviceService.findValid(token)!!.tokenHash).single()
        assertEquals("A session that must be saved", stored.text)
        assertEquals("msg-1", stored.sourceMessageId)
    }

    @Test
    fun `play scenarios and follow-up chats are persisted on the session`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "play-study-token-abcdefghijklmnopqrstuvw" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            idGenerator = { "study-play-1" },
        )
        val token = deviceService.issueToken().deviceToken

        val session = sampleSession("Xin chào.").copy(
            playScenarios = listOf(
                StudyPlayScenario(
                    id = "scenario-1",
                    description = "A neighbor greets you.",
                    openingLine = "Xin chào!",
                    imageId = "img-1",
                    imageUrl = "https://cdn.example/play.png",
                    messages = listOf(
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.OTHER,
                            text = "Xin chào!",
                            description = "A neighbor greets you.",
                            imageId = "img-1",
                            imageUrl = "https://cdn.example/play.png",
                        ),
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.USER,
                            text = "Chào bạn!",
                        ),
                        StudyPlayChatMessage(
                            speaker = StudyPlaySpeaker.OTHER,
                            text = "Bạn khỏe không?",
                            description = "They smile.",
                            imageId = "img-2",
                            imageUrl = "https://cdn.example/play2.png",
                        ),
                    ),
                ),
            ),
        )
        val saved = service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = "msg-play",
                languageTag = "en",
                session = session,
            ),
        )

        val listed = service.list(token).items.single()
        assertEquals(saved.id, listed.id)
        val fullSession = service.get(token, saved.id)!!
        assertEquals(1, fullSession.session.playScenarios.size)
        val scenario = fullSession.session.playScenarios.single()
        assertEquals("scenario-1", scenario.id)
        assertEquals(3, scenario.messages.size)
        assertEquals(StudyPlaySpeaker.USER, scenario.messages[1].speaker)
        assertEquals("Chào bạn!", scenario.messages[1].text)
        assertEquals("https://cdn.example/play2.png", scenario.messages[2].imageUrl)
        Unit
    }

    @Test
    fun `progressive saves preserve exam scene and owner-only grade`() = runBlocking {
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = StudySessionsService(repository = repository, deviceService = deviceService)
        val ownerToken = deviceService.issueToken().deviceToken
        val otherToken = deviceService.issueToken().deviceToken
        val scene = StudyExamScene(id = "scene-1", setting = "At a market.")
        val grade = StudyExamGrade(
            overall = 4,
            listening = 5,
            speaking = 3,
            feedback = "A strong attempt.",
            scoredAt = 100L,
        )
        val initial = service.save(
            ownerToken,
            SaveStudySessionRequest(
                sourceMessageId = "exam-session",
                languageTag = "en",
                session = sampleSession("Xin chào.").copy(examScene = scene),
            ),
        )
        service.saveExamGrade(ownerToken, initial.id, grade)

        val progressive = service.save(
            ownerToken,
            SaveStudySessionRequest(
                sourceMessageId = "exam-session",
                languageTag = "en",
                session = sampleSession("Xin chào. Cảm ơn."),
            ),
        )

        assertEquals(grade, progressive.examGrade)
        assertEquals(grade, service.list(ownerToken).items.single().examGrade)
        assertEquals(scene, service.get(ownerToken, initial.id)?.session?.examScene)
        assertEquals(grade, service.get(ownerToken, initial.id)?.examGrade)
        assertNull(service.get(otherToken, initial.id)?.examGrade)
        assertNull(service.listCommunity(otherToken).items.single().examGrade)
    }

    @Test
    fun `stale client save cannot downgrade built meanings and images`() = runBlocking {
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = StudySessionsService(repository = repository, deviceService = deviceService)
        val token = deviceService.issueToken().deviceToken
        val source = "stale-merge"
        val skeleton = StudySessionResponse(
            text = "Xin chào.",
            textLanguageTag = "vi",
            sentences = listOf(
                StudySentence(
                    text = "Xin chào.",
                    tokens = listOf(StudyToken(text = "Xin", status = StudyTokenStatus.PENDING)),
                ),
            ),
        )
        val created = service.save(
            token,
            SaveStudySessionRequest(sourceMessageId = source, languageTag = "en", session = skeleton),
        )
        service.sessionStore.update(created.id, immediate = true) { stored ->
            stored.copy(
                build = StudyBuildState(status = StudyBuildStatus.BUILDING),
                session = stored.session.copy(
                    sentences = listOf(
                        StudySentence(
                            text = "Xin chào.",
                            tokens = listOf(
                                StudyToken(
                                    text = "Xin",
                                    status = StudyTokenStatus.READY,
                                    literalMeaning = "hello",
                                    imageId = "word-image",
                                    imageUrl = "https://img/word",
                                ),
                            ),
                            imageId = "sentence-image",
                            imageUrl = "https://img/sentence",
                        ),
                    ),
                ),
            )
        }

        service.save(
            token,
            SaveStudySessionRequest(
                sourceMessageId = source,
                languageTag = "en",
                session = skeleton.copy(
                    practiceSentences = listOf(StudyExampleSentence("Practice.", "Try this.")),
                ),
            ),
        )

        val saved = service.get(token, created.id)!!
        val sentence = saved.session.sentences.single()
        assertEquals(StudyTokenStatus.READY, sentence.tokens.single().status)
        assertEquals("hello", sentence.tokens.single().literalMeaning)
        assertEquals("https://img/word", sentence.tokens.single().imageUrl)
        assertEquals("https://img/sentence", sentence.imageUrl)
        assertEquals(listOf(StudyExampleSentence("Practice.", "Try this.")), saved.session.practiceSentences)
        assertEquals(StudyBuildStatus.BUILDING, saved.build?.status)
    }

    @Test
    fun `blank session is rejected and missing token is unauthorized`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "blank-study-token-abcdefghijklmnopqrst" },
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
        )
        val token = deviceService.issueToken().deviceToken

        assertFailsWith<IllegalArgumentException> {
            service.save(
                token,
                SaveStudySessionRequest(
                    languageTag = "en",
                    session = StudySessionResponse(text = "   ", textLanguageTag = "en", sentences = emptyList()),
                ),
            )
        }
        assertFailsWith<UnauthorizedDeviceException> {
            service.list(null)
        }
        assertFailsWith<UnauthorizedDeviceException> {
            service.list("not-a-real-token")
        }
        Unit
    }

    @Test
    fun `community list excludes own sessions and pages by cursor`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            clock = incrementalClock(1_000L),
        )
        val tokenMine = deviceService.issueToken().deviceToken
        val tokenOther = deviceService.issueToken().deviceToken

        service.save(tokenMine, request("mine"))
        val first = service.save(tokenOther, request("community-1"))
        val second = service.save(tokenOther, request("community-2"))
        val third = service.save(tokenOther, request("community-3"))

        val firstPage = service.listCommunity(tokenMine, limit = 2)
        assertEquals(listOf(third.text, second.text), firstPage.items.map { it.text })
        assertEquals(studyCommunityCursor(second.updatedAt, second.id), firstPage.nextCursor)
        assertTrue(firstPage.items.none { it.text == "mine" })

        val secondPage = service.listCommunity(tokenMine, cursor = firstPage.nextCursor, limit = 2)
        assertEquals(listOf(first.text), secondPage.items.map { it.text })
        assertNull(secondPage.nextCursor)

        assertFailsWith<UnauthorizedDeviceException> {
            service.listCommunity(null)
        }
        Unit
    }

    @Test
    fun `community cursor parsing accepts updatedAt and id`() {
        assertEquals(1_700L to "abc", parseStudyCommunityCursor("1700:abc"))
        assertNull(parseStudyCommunityCursor(null))
        assertNull(parseStudyCommunityCursor(" "))
        assertNull(parseStudyCommunityCursor("abc"))
        assertNull(parseStudyCommunityCursor("1700:"))
        assertEquals("1700:abc", studyCommunityCursor(1_700L, "abc"))
    }

    private fun request(text: String) = SaveStudySessionRequest(
        languageTag = "en",
        session = sampleSession(text),
    )

    private fun sampleSession(text: String, pending: Boolean = true) = StudySessionResponse(
        text = text,
        textLanguageTag = "en",
        sentences = listOf(
            StudySentence(
                text = text,
                tokens = listOf(
                    StudyToken(
                        text = text.split(" ").first(),
                        status = if (pending) StudyTokenStatus.PENDING else StudyTokenStatus.READY,
                        literalMeaning = if (pending) "" else "meaning",
                    ),
                ),
            ),
        ),
    )

    private fun sequenceTokenGenerator(): () -> String {
        var counter = 0
        return { "device-token-${++counter}-xxxxxxxxxxxxxxxxxxxxxxx" }
    }

    private fun incrementalClock(start: Long): () -> Long {
        var value = start
        return { value++ }
    }
}
