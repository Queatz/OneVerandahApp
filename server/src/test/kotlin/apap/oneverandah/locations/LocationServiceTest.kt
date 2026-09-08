package apap.oneverandah.locations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LocationServiceTest {
    @Test
    fun upsertCreatesPublicCardNewestFirst() = runBlocking {
        val service = service()
        service.upsert(request(name = "Old Cafe", category = "Food"))
        service.upsert(request(name = "New Studio", category = "Wellness"))
        val feed = service.feed(null)
        assertEquals(listOf("New Studio", "Old Cafe"), feed.locations.map { it.name })
        assertEquals(listOf("Food", "Wellness"), feed.categories)
        assertTrue(feed.locations.none { it.mapsUrl.contains("090") })
    }

    @Test
    fun commitFundsOnlyWhilePlanning() = runBlocking {
        val service = service()
        val planning = service.upsert(request(name = "Garden", category = "Food", status = LocationLogic.STATUS_PLANNING))
        service.commitFunds(planning.id, 50_000, "Love this", "090111", "visitor")
        assertEquals(1, service.listAdmin().locations.single().commitments.size)

        val soon = service.upsert(
            request(id = planning.id, name = "Garden", category = "Food", status = LocationLogic.STATUS_COMING_SOON),
        )
        assertFailsWith<IllegalArgumentException> {
            service.commitFunds(soon.id, 50_000, "Too late", "090111", "visitor")
        }
        service.contactHost(soon.id, "When do you open?", "090222", "visitor")
        assertEquals(1, service.listAdmin().locations.single().contacts.size)
    }

    @Test
    fun likeTogglesPerDevice() = runBlocking {
        val service = service()
        val created = service.upsert(request())
        val liked = service.toggleLike(created.id, "hash-lan")
        assertTrue(liked.liked)
        assertEquals(1, liked.likeCount)
        val again = service.feed("hash-lan").locations.single()
        assertTrue(again.liked)
        val unliked = service.toggleLike(created.id, "hash-lan")
        assertFalse(unliked.liked)
        assertEquals(0, unliked.likeCount)
        assertFalse(service.feed("hash-minh").locations.single().liked)
    }

    @Test
    fun contactRejectedWhilePlanning() = runBlocking {
        val service = service()
        val created = service.upsert(request(status = LocationLogic.STATUS_PLANNING))
        assertFailsWith<IllegalArgumentException> {
            service.contactHost(created.id, "Hi", "090111", "visitor")
        }
        Unit
    }

    @Test
    fun deleteRemovesCommitmentsAndLikes() = runBlocking {
        val service = service()
        val created = service.upsert(request())
        service.toggleLike(created.id, "hash-lan")
        service.commitFunds(created.id, 20_000, "Yes", "090111", "visitor")
        assertTrue(service.delete(created.id))
        assertNull(service.getPublic(created.id, "hash-lan"))
        assertTrue(service.listAdmin().locations.isEmpty())
    }

    @Test
    fun requireNameCategoryZaloAndAmount() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> { service.upsert(request(name = "  ")) }
        assertFailsWith<IllegalArgumentException> { service.upsert(request(category = " ")) }
        val created = service.upsert(request())
        assertFailsWith<IllegalArgumentException> {
            service.commitFunds(created.id, 10, "Hi", "090111", "visitor")
        }
        assertFailsWith<IllegalArgumentException> {
            service.commitFunds(created.id, 20_000, "Hi", "abc", "visitor")
        }
        Unit
    }

    private fun service(): LocationService {
        var next = 0
        return LocationService(
            repository = InMemoryLocationRepository(),
            clock = { 1_700_000_000_000L + next },
            idGenerator = { "id-${next++}" },
        )
    }

    private fun request(
        id: String? = null,
        name: String = "River Cafe",
        category: String = "Food",
        status: String = LocationLogic.STATUS_PLANNING,
    ) = LocationUpsertRequest(
        id = id,
        name = name,
        type = "Cafe",
        category = category,
        status = status,
        address = "Lobby 2",
        highlights = listOf("Wifi", "Quiet"),
        hostZalo = "090000",
    )
}
