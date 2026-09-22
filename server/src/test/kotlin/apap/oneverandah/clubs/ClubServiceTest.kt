package apap.oneverandah.clubs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ClubServiceTest {
    @Test
    fun upsertCreatesPublicCardNewestFirst() = runBlocking {
        val service = service()
        service.upsert(request(name = "Old Club", category = "Games"))
        service.upsert(request(name = "New Club", category = "Wellness"))
        val feed = service.feed()
        assertEquals(listOf("New Club", "Old Club"), feed.clubs.map { it.name })
        assertEquals(listOf("Games", "Wellness"), feed.categories)
        assertEquals("090000", feed.clubs.first().hostZalo)
    }

    @Test
    fun joinStoresAnswersAndStripsZaloFromPublicDetail() = runBlocking {
        val service = service()
        val created = service.upsert(
            request(joinQuestions = listOf("Why join?", "When can you meet?")),
        )
        service.join(created.id, "Lan", "090111", listOf("Fun", "Tuesdays"), "Hello", "visitor")
        val admin = service.listAdmin()
        assertEquals(1, admin.joins.size)
        assertEquals("Lan", admin.joins.single().name)
        assertEquals("090111", admin.joins.single().zaloNumber)
        assertEquals("090000", admin.joins.single().hostZalo)
        assertEquals("Fun", admin.joins.single().answers.first().answer)
        assertEquals("090000", admin.clubs.single().joins.single().hostZalo)

        val detail = service.getPublic(created.id)
        assertEquals(listOf("Why join?", "When can you meet?"), detail?.joinQuestions)
        assertEquals("090000", detail?.club?.hostZalo)
        assertFalse(detail.toString().contains("090111"))
    }

    @Test
    fun createRequestRequiresNameHostAndZalo() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> {
            service.requestCreate("  ", "tag", "desc", "Lan", "090111", "visitor")
        }
        assertFailsWith<IllegalArgumentException> {
            service.requestCreate("Yoga", "tag", "desc", "  ", "090111", "visitor")
        }
        assertFailsWith<IllegalArgumentException> {
            service.requestCreate("Yoga", "tag", "desc", "Lan", "abc", "visitor")
        }
        service.requestCreate("Yoga", "Morning", "Stretch", "Lan", "090111", "visitor")
        assertEquals(1, service.listAdmin().createRequests.size)
        assertEquals("Yoga", service.listAdmin().createRequests.single().name)
    }

    @Test
    fun deleteRemovesJoins() = runBlocking {
        val service = service()
        val created = service.upsert(request())
        service.join(created.id, "Lan", "090111", emptyList(), "Hi", "visitor")
        assertTrue(service.delete(created.id))
        assertNull(service.getPublic(created.id))
        assertTrue(service.listAdmin().clubs.isEmpty())
        assertTrue(service.listAdmin().joins.isEmpty())
    }

    @Test
    fun requireNameAndCategory() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> { service.upsert(request(name = "  ")) }
        assertFailsWith<IllegalArgumentException> { service.upsert(request(category = " ")) }
        Unit
    }

    @Test
    fun dismissCreateAndJoinRequests() = runBlocking {
        val service = service()
        val created = service.upsert(request())
        val request = service.requestCreate("Chess", "", "", "Minh", "090222", "visitor")
        val join = service.join(created.id, "Lan", "090111", emptyList(), "", "visitor")
        assertTrue(service.deleteCreateRequest(request.id))
        assertTrue(service.deleteJoin(join.id))
        assertTrue(service.listAdmin().createRequests.isEmpty())
        assertTrue(service.listAdmin().joins.isEmpty())
    }

    private fun service(): ClubService {
        var next = 0
        return ClubService(
            repository = InMemoryClubRepository(),
            clock = { 1_700_000_000_000L + next },
            idGenerator = { "id-${next++}" },
        )
    }

    private fun request(
        name: String = "Sunrise Yoga",
        category: String = "Wellness",
        joinQuestions: List<String> = emptyList(),
    ) = ClubUpsertRequest(
        name = name,
        tagline = "Breathe together",
        description = "A morning club",
        category = category,
        activities = listOf("Yoga", "Breathwork"),
        joinQuestions = joinQuestions,
        hostName = "Lan",
        hostZalo = "090000",
    )
}
