package apap.oneverandah

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, Ktor!", response.bodyAsText())
    }

    @Test
    fun testServiceSearchNormalization() {
        // Query search
        val searchQuery = "word massage"
        val normalizedQuery = searchQuery.lowercase().filter { !it.isWhitespace() }
        
        val matchedServices = apap.oneverandah.data.Catalog.services.filter { service ->
            val titleEn = service.title.en.lowercase().filter { !it.isWhitespace() }
            val titleVi = service.title.vi.lowercase().filter { !it.isWhitespace() }
            val titleZh = service.title.zh.lowercase().filter { !it.isWhitespace() }
            val descEn = service.description.en.lowercase().filter { !it.isWhitespace() }
            val descVi = service.description.vi.lowercase().filter { !it.isWhitespace() }
            val descZh = service.description.zh.lowercase().filter { !it.isWhitespace() }

            titleEn.contains(normalizedQuery) ||
            titleVi.contains(normalizedQuery) ||
            titleZh.contains(normalizedQuery) ||
            descEn.contains(normalizedQuery) ||
            descVi.contains(normalizedQuery) ||
            descZh.contains(normalizedQuery)
        }
        
        assertTrue(matchedServices.isNotEmpty(), "Should find some services for 'word massage'")
        assertTrue(matchedServices.any { it.id == "word-massages-jacob" }, "Should find massage services")
    }

    @Test
    fun testPricingCalculatorDurationFormatting() {
        val service60 = apap.oneverandah.model.Service(
            id = "test-service-60",
            categoryId = "test",
            icon = "spa",
            title = apap.oneverandah.model.LocalizedText("Test 60", "Test 60"),
            description = apap.oneverandah.model.LocalizedText("Test 60", "Test 60"),
            rating = 5.0,
            reviewCount = 1,
            basePrice = apap.oneverandah.model.Money(300_000L),
            bookingTypes = listOf(apap.oneverandah.model.BookingTypeOption("trial", apap.oneverandah.model.LocalizedText("Trial", "Thử nghiệm"))),
            durations = listOf(apap.oneverandah.model.DurationOption("60", 60, apap.oneverandah.model.Money(300_000L))),
            provider = apap.oneverandah.model.ServiceProvider("Jacob", apap.oneverandah.model.Sex.MALE, "A.12.03", "123", "face")
        )
        val selection60 = service60.defaultSelection()
        val breakdown60 = apap.oneverandah.model.PricingCalculator.breakdown(service60, selection60)
        val durationLine60 = breakdown60.lines.first()
        assertEquals("60 minutes", durationLine60.label.en)
        assertEquals("60 phút", durationLine60.label.vi)
        assertEquals("60 分钟", durationLine60.label.zh)

        val service120 = apap.oneverandah.model.Service(
            id = "test-service-120",
            categoryId = "test",
            icon = "spa",
            title = apap.oneverandah.model.LocalizedText("Test 120", "Test 120"),
            description = apap.oneverandah.model.LocalizedText("Test 120", "Test 120"),
            rating = 5.0,
            reviewCount = 1,
            basePrice = apap.oneverandah.model.Money(900_000L),
            bookingTypes = listOf(apap.oneverandah.model.BookingTypeOption("trial", apap.oneverandah.model.LocalizedText("Trial", "Thử nghiệm"))),
            durations = listOf(apap.oneverandah.model.DurationOption("120", 120, apap.oneverandah.model.Money(900_000L))),
            provider = apap.oneverandah.model.ServiceProvider("Jacob", apap.oneverandah.model.Sex.MALE, "A.12.03", "123", "face")
        )
        val selection120 = service120.defaultSelection()
        val breakdown120 = apap.oneverandah.model.PricingCalculator.breakdown(service120, selection120)
        val durationLine120 = breakdown120.lines.first()
        assertEquals("2 hours", durationLine120.label.en)
        assertEquals("2 giờ", durationLine120.label.vi)
        assertEquals("2 小时", durationLine120.label.zh)

        val service150 = apap.oneverandah.model.Service(
            id = "test-service-150",
            categoryId = "test",
            icon = "spa",
            title = apap.oneverandah.model.LocalizedText("Test 150", "Test 150"),
            description = apap.oneverandah.model.LocalizedText("Test 150", "Test 150"),
            rating = 5.0,
            reviewCount = 1,
            basePrice = apap.oneverandah.model.Money(600_000L),
            bookingTypes = listOf(apap.oneverandah.model.BookingTypeOption("trial", apap.oneverandah.model.LocalizedText("Trial", "Thử nghiệm"))),
            durations = listOf(apap.oneverandah.model.DurationOption("150", 150, apap.oneverandah.model.Money(600_000L))),
            provider = apap.oneverandah.model.ServiceProvider("Jacob", apap.oneverandah.model.Sex.MALE, "A.12.03", "123", "face")
        )
        val selection150 = service150.defaultSelection()
        val breakdown150 = apap.oneverandah.model.PricingCalculator.breakdown(service150, selection150)
        val durationLine150 = breakdown150.lines.first()
        assertEquals("2.5 hours", durationLine150.label.en)
        assertEquals("2.5 giờ", durationLine150.label.vi)
        assertEquals("2.5 小时", durationLine150.label.zh)
    }
}
