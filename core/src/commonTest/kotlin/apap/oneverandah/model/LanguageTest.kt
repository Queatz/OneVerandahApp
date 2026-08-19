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
        Language.entries.forEach { language ->
            assertFalse(language.displayName.contains(language.tag, ignoreCase = false))
            assertEquals(language.displayName.first(), language.displayName.first().uppercaseChar())
        }
    }
}
