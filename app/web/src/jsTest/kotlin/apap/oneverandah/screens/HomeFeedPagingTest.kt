package apap.oneverandah.screens

import apap.oneverandah.data.Catalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedPagingTest {
    @Test
    fun `first page is limited to the feed page size`() {
        val services = Catalog.services
        assertTrue(services.size > HOME_FEED_PAGE_SIZE, "catalog should have more than one page")

        val page = homeFeedPage(services, HOME_FEED_PAGE_SIZE)
        assertEquals(HOME_FEED_PAGE_SIZE, page.size)
        assertEquals(services.take(HOME_FEED_PAGE_SIZE), page)
    }

    @Test
    fun `advance adds a full batch until the last page`() {
        val total = Catalog.services.size
        var visible = HOME_FEED_PAGE_SIZE

        visible = advanceHomeFeedVisibleCount(visible, total)
        assertEquals((HOME_FEED_PAGE_SIZE * 2).coerceAtMost(total), visible)

        // Keep advancing until everything is visible.
        var guard = 0
        while (visible < total && guard < 20) {
            visible = advanceHomeFeedVisibleCount(visible, total)
            guard++
        }
        assertEquals(total, visible)
        // One more advance should stay clamped at total.
        assertEquals(total, advanceHomeFeedVisibleCount(visible, total))
    }

    @Test
    fun `page request never exceeds available services`() {
        val services = Catalog.services
        val page = homeFeedPage(services, services.size + 50)
        assertEquals(services, page)
    }

    @Test
    fun `negative or zero visible counts yield an empty page`() {
        assertTrue(homeFeedPage(Catalog.services, 0).isEmpty())
        assertTrue(homeFeedPage(Catalog.services, -5).isEmpty())
    }
}
