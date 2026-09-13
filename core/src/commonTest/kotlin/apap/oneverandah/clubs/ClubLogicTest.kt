package apap.oneverandah.clubs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClubLogicTest {
    @Test
    fun sanitizersClampActivitiesQuestionsAndZalo() {
        val activities = ClubLogic.sanitizeActivities(listOf("  Yoga  ", "", "Run", "Extra", "Too long".repeat(20)))
        assertEquals(3, activities.size)
        assertEquals("Yoga", activities.first())

        val questions = ClubLogic.sanitizeJoinQuestions(listOf(" Why join? ", "", "When?", "Where?", "Who?"))
        assertEquals(listOf("Why join?", "When?", "Where?"), questions)

        assertEquals("090111", ClubLogic.sanitizeZalo("abc090-111"))
        assertEquals("Lan", ClubLogic.sanitizeHostName("  Lan  "))
    }

    @Test
    fun publicCardStripsHostZaloAndKeepsHostName() {
        val card = ClubLogic.toPublicCard(
            club(
                hostName = "Minh",
                hostZalo = "090000",
            ),
        )
        assertEquals("Minh", card.hostName)
        assertFalse(card.toString().contains("090000"))
    }

    @Test
    fun publicCardsNewestFirstAndCategoriesSorted() {
        val older = club(id = "a", name = "Chess", category = "Games", createdAt = 1)
        val newer = club(id = "b", name = "Run Club", category = "Sport", createdAt = 2)
        val cards = ClubLogic.publicCards(listOf(older, newer))
        assertEquals(listOf("Run Club", "Chess"), cards.map { it.name })
        assertEquals(listOf("Games", "Sport"), ClubLogic.categories(listOf(older, newer)))
    }

    @Test
    fun matchesQuerySearchesNameTaglineActivities() {
        val card = ClubLogic.toPublicCard(
            club(name = "Sunrise Yoga", tagline = "Morning stretch", activities = listOf("Breathwork")),
        )
        assertTrue(ClubLogic.matchesQuery(card, "yoga"))
        assertTrue(ClubLogic.matchesQuery(card, "stretch"))
        assertTrue(ClubLogic.matchesQuery(card, "breath"))
        assertFalse(ClubLogic.matchesQuery(card, "chess"))
        assertTrue(ClubLogic.matchesQuery(card, "  "))
    }

    @Test
    fun joinViewIncludesHostZalo() {
        val view = ClubLogic.toJoinView(
            ClubJoin(id = "j1", clubId = "club-1", name = "Lan", zaloNumber = "090111"),
            clubName = "Sunrise Yoga",
            hostZalo = "090000",
        )
        assertEquals("090000", view.hostZalo)
        assertEquals("Sunrise Yoga", view.clubName)
        assertEquals("090111", view.zaloNumber)
    }

    @Test
    fun sanitizeAnswersZipToClubQuestions() {
        val answers = ClubLogic.sanitizeAnswers(
            questions = listOf("Why?", "When?"),
            raw = listOf("  Fun  ", "Tuesday", "ignored"),
        )
        assertEquals(
            listOf(
                ClubJoinAnswer("Why?", "Fun"),
                ClubJoinAnswer("When?", "Tuesday"),
            ),
            answers,
        )
    }

    @Test
    fun sanitizeContentDropsEmptyBlocks() {
        val content = ClubLogic.sanitizeContent(
            listOf(
                ClubContentBlock(type = ClubLogic.BLOCK_TEXT, text = "  Hello  "),
                ClubContentBlock(type = ClubLogic.BLOCK_TEXT, text = " "),
                ClubContentBlock(type = ClubLogic.BLOCK_IMAGE, imageUrl = "photo.jpg"),
                ClubContentBlock(type = ClubLogic.BLOCK_IMAGE, imageUrl = "  "),
            ),
        )
        assertEquals(2, content.size)
        assertEquals(ClubLogic.BLOCK_TEXT, content[0].type)
        assertEquals("Hello", content[0].text)
        assertEquals("photo.jpg", content[1].imageUrl)
    }

    private fun club(
        id: String = "club-1",
        name: String = "Sunrise Yoga",
        category: String = "Wellness",
        activities: List<String> = listOf("Yoga"),
        hostName: String = "Lan",
        hostZalo: String = "",
        createdAt: Long = 0,
        tagline: String = "Breathe together",
    ) = Club(
        id = id,
        name = name,
        tagline = tagline,
        description = "A morning club",
        category = category,
        activities = activities,
        hostName = hostName,
        hostZalo = hostZalo,
        createdAt = createdAt,
    )
}
