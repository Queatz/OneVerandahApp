package apap.oneverandah.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProviderSkillTest {

    @Test
    fun levelIsClampedByFactory() {
        assertEquals(1, skill(level = 0).level)
        assertEquals(1, skill(level = -3).level)
        assertEquals(1, skill(level = 1).level)
        assertEquals(3, skill(level = 3).level)
        assertEquals(5, skill(level = 5).level)
        assertEquals(5, skill(level = 9).level)
    }

    @Test
    fun constructorRejectsOutOfRangeLevel() {
        assertFailsWith<IllegalArgumentException> {
            ProviderSkill(
                name = LocalizedText(en = "X", vi = "X"),
                emoji = "✨",
                level = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProviderSkill(
                name = LocalizedText(en = "X", vi = "X"),
                emoji = "✨",
                level = 6,
            )
        }
    }

    @Test
    fun serviceProviderKeepsSkillsInOrder() {
        val skills = listOf(
            skill(name = "A", level = 2),
            skill(name = "B", level = 5),
            skill(name = "C", level = 4),
        )
        val provider = ServiceProvider(
            name = "Test",
            sex = Sex.MALE,
            blockAddress = "Block A",
            phone = "+84 000",
            photo = "face",
            skills = skills,
        )
        assertEquals(listOf("A", "B", "C"), provider.skills.map { it.name.en })
        assertEquals(listOf(2, 5, 4), provider.skills.map { it.level })
    }

    private fun skill(
        name: String = "Skill",
        emoji: String = "✨",
        level: Int = 3,
    ) = ProviderSkill.of(
        name = LocalizedText(en = name, vi = name),
        emoji = emoji,
        level = level,
    )
}
