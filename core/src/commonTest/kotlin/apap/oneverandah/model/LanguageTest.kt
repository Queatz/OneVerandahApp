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
        assertEquals("German", Language.GERMAN.displayName)
        assertEquals("Burmese", Language.BURMESE.displayName)
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
    fun fromTagResolvesGerman() {
        assertEquals(Language.GERMAN, Language.fromTag("de"))
        assertEquals(Language.GERMAN, Language.fromTag("de-DE"))
        assertEquals(Language.GERMAN, Language.fromTag("de-AT"))
        assertEquals(Language.GERMAN, Language.fromTag("de-CH"))
    }

    @Test
    fun fromTagResolvesBurmese() {
        assertEquals(Language.BURMESE, Language.fromTag("my"))
        assertEquals(Language.BURMESE, Language.fromTag("my-MM"))
    }

    @Test
    fun contentLanguageTagsPreserveLanguagesOutsideTheUiSet() {
        assertEquals("my", Language.normalizeContentTag("my-MM"))
        assertEquals("bur", Language.normalizeContentTag("BUR"))
        assertEquals("vi", Language.normalizeContentTag("vi_VN"))
        assertEquals("en", Language.normalizeContentTag("invalid-tag"))
    }

    @Test
    fun localizedTextResolvesSpanish() {
        val textWithSpanish = LocalizedText(en = "Hello", vi = "Xin chào", es = "Hola")
        assertEquals("Hola", textWithSpanish.resolve(Language.SPANISH))

        val textDefaultSpanish = LocalizedText(en = "Hello", vi = "Xin chào")
        assertEquals("Hello", textDefaultSpanish.resolve(Language.SPANISH))
    }

    @Test
    fun localizedTextResolvesGerman() {
        val textWithGerman = LocalizedText(en = "Hello", vi = "Xin chào", de = "Hallo")
        assertEquals("Hallo", textWithGerman.resolve(Language.GERMAN))

        val textDefaultGerman = LocalizedText(en = "Hello", vi = "Xin chào")
        assertEquals("Hello", textDefaultGerman.resolve(Language.GERMAN))
    }

    @Test
    fun localizedTextResolvesBurmese() {
        val textWithBurmese = LocalizedText(en = "Hello", vi = "Xin chào", my = "မင်္ဂလာပါ")
        assertEquals("မင်္ဂလာပါ", textWithBurmese.resolve(Language.BURMESE))

        val textDefaultBurmese = LocalizedText(en = "Hello", vi = "Xin chào")
        assertEquals("Hello", textDefaultBurmese.resolve(Language.BURMESE))
    }
}
