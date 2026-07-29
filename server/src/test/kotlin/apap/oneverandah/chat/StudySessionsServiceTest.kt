package apap.oneverandah.chat

import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudySessionsServiceTest {
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
        assertEquals(StudyTokenStatus.READY, second.session.sentences.single().tokens.single().status)
        assertEquals(1, service.list(token).items.size)
        Unit
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
        assertEquals(1, listed.session.playScenarios.size)
        val scenario = listed.session.playScenarios.single()
        assertEquals("scenario-1", scenario.id)
        assertEquals(3, scenario.messages.size)
        assertEquals(StudyPlaySpeaker.USER, scenario.messages[1].speaker)
        assertEquals("Chào bạn!", scenario.messages[1].text)
        assertEquals("https://cdn.example/play2.png", scenario.messages[2].imageUrl)
        Unit
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
