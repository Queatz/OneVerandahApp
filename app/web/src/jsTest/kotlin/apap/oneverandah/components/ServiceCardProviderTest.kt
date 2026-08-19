package apap.oneverandah.components

import apap.oneverandah.data.Catalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceCardProviderTest {
    @Test
    fun `provider profile url joins origin and provider id`() {
        assertEquals(
            "https://oneverandah.app/provider/linh",
            providerProfileUrl("https://oneverandah.app", "linh"),
        )
    }

    @Test
    fun `provider profile url trims a trailing slash on origin`() {
        assertEquals(
            "https://oneverandah.app/provider/linh",
            providerProfileUrl("https://oneverandah.app/", "linh"),
        )
    }

    @Test
    fun `provider profile url stays empty-safe when the provider id is blank`() {
        assertEquals("", providerProfileUrl("", ""))
        assertEquals("https://oneverandah.app", providerProfileUrl("https://oneverandah.app/", "  "))
    }

    @Test
    fun `sheet starts closed and opens for a service`() {
        val service = Catalog.services.first()
        var sheet = ProviderDetailsSheet()

        assertFalse(sheet.visible)
        assertNull(sheet.service)
        assertNull(sheet.provider)
        assertNull(sheet.currentServiceId)

        sheet = sheet.open(service)
        assertTrue(sheet.visible)
        assertEquals(service, sheet.service)
        assertEquals(service.provider, sheet.provider)
        assertEquals(service.id, sheet.currentServiceId)
    }

    @Test
    fun `dismissing the sheet hides it but keeps the service for the exit animation`() {
        val service = Catalog.services.first()
        val sheet = ProviderDetailsSheet().open(service).dismiss()

        assertFalse(sheet.visible)
        assertEquals(service, sheet.service)
        assertEquals(service.id, sheet.currentServiceId)
    }

    @Test
    fun `opening another service replaces the previous one`() {
        val first = Catalog.services[0]
        val second = Catalog.services[1]
        assertNotEquals(first.id, second.id)

        val sheet = ProviderDetailsSheet().open(first).open(second)
        assertEquals(second, sheet.service)
        assertEquals(second.provider, sheet.provider)
        assertEquals(second.id, sheet.currentServiceId)
    }
}
