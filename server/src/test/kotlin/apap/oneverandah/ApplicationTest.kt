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
}
