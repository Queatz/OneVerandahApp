package apap.oneverandah.chat

import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.FactMiniApp
import apap.oneverandah.api.SaveChatMessageRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedMessagesServiceTest {
    @Test
    fun `save list and delete are scoped by device token`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = sequenceTokenGenerator(),
        )
        val service = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
            deviceService = deviceService,
            clock = incrementalClock(1_000L),
        )
        val tokenA = deviceService.issueToken().deviceToken
        val tokenB = deviceService.issueToken().deviceToken

        val savedA = service.save(
            tokenA,
            SaveChatMessageRequest(
                sourceMessageId = "msg-1",
                sender = ChatSender.AI,
                text = "Hello from A",
                createdAt = 10L,
                miniApp = FactMiniApp("fact-1", "Title", "Body", "info"),
                followUpQuestions = listOf("More?"),
            ),
        )
        service.save(
            tokenB,
            SaveChatMessageRequest(
                sender = ChatSender.YOU,
                text = "Hello from B",
                createdAt = 11L,
            ),
        )

        val listA = service.list(tokenA).items
        assertEquals(1, listA.size)
        assertEquals(savedA.id, listA.single().id)
        assertEquals("Hello from A", listA.single().text)
        assertNotNull(listA.single().miniApp)
        assertEquals(listOf("More?"), listA.single().followUpQuestions)

        assertFalse(service.delete(tokenB, savedA.id))
        assertEquals(1, service.list(tokenA).items.size)
        assertTrue(service.delete(tokenA, savedA.id))
        assertTrue(service.list(tokenA).items.isEmpty())
        Unit
    }

    @Test
    fun `saving the same sourceMessageId upserts instead of duplicating`() = runBlocking {
        var now = 100L
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "same-source-token-abcdefghijklmnopqrstuv" },
        )
        val service = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
            deviceService = deviceService,
            clock = { now },
            idGenerator = { "saved-1" },
        )
        val token = deviceService.issueToken().deviceToken

        val first = service.save(
            token,
            SaveChatMessageRequest(
                sourceMessageId = "src-1",
                sender = ChatSender.YOU,
                text = "First",
                createdAt = 1L,
            ),
        )
        now = 200L
        val second = service.save(
            token,
            SaveChatMessageRequest(
                sourceMessageId = "src-1",
                sender = ChatSender.YOU,
                text = "Updated",
                createdAt = 1L,
            ),
        )

        assertEquals(first.id, second.id)
        assertEquals("Updated", second.text)
        assertEquals(200L, second.savedAt)
        assertEquals(1, service.list(token).items.size)
        Unit
    }

    @Test
    fun `cap drops oldest messages for the device`() = runBlocking {
        var now = 0L
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "cap-token-abcdefghijklmnopqrstuvwxyz012" },
        )
        val service = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
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
    fun `blank text is rejected and missing token is unauthorized`() = runBlocking {
        val deviceService = DeviceService(
            repository = InMemoryDeviceRepository(),
            tokenGenerator = { "blank-token-abcdefghijklmnopqrstuvwxy" },
        )
        val service = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
            deviceService = deviceService,
        )
        val token = deviceService.issueToken().deviceToken

        assertFailsWith<IllegalArgumentException> {
            service.save(
                token,
                SaveChatMessageRequest(sender = ChatSender.YOU, text = "   ", createdAt = 1L),
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

    private fun request(text: String) = SaveChatMessageRequest(
        sender = ChatSender.YOU,
        text = text,
        createdAt = 1L,
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
