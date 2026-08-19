package apap.oneverandah.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LanguageTest {
    @Test
    fun displayNameIsCapitalizedAndOmitsTheTag() {
        assertEquals("English", Language.ENGLISH.displayName)
        assertEquals("Vietnamese", Language.VIETNAMESE.displayName)
        assertEquals("Chinese", Language.CHINESE.displayName)
        assertEquals("Japanese", Language.JAPANESE.displayName)
        assertEquals("Korean", Language.KOREAN.displayName)
        assertEquals("Russian", Language.RUSSIAN.displayName)
        assertEquals("Spanish", Language.SPANISH.displayName)
        Language.entries.forEach { language ->
            assertFalse(language.displayName.contains(language.tag, ignoreCase = false))
            assertEquals(language.displayName.first(), language.displayName.first().uppercaseChar())
        }
    }

    @Test
    fun fromTagResolvesSpanish() {
        assertEquals(Language.SPANISH, Language.fromTag("es"))
        assertEquals(Language.SPANISH, Language.fromTag("es-ES"))
        assertEquals(Language.SPANISH, Language.fromTag("es-MX"))
    }

    @Test
    fun localizedTextResolvesSpanish() {
        val textWithSpanish = LocalizedText(en = "Hello", vi = "Xin chào", es = "Hola")
        assertEquals("Hola", textWithSpanish.resolve(Language.SPANISH))

        val textDefaultSpanish = LocalizedText(en = "Hello", vi = "Xin chào")
        assertEquals("Hello", textDefaultSpanish.resolve(Language.SPANISH))
    }
}
