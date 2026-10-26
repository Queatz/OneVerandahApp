package apap.oneverandah.i18n

import apap.oneverandah.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationTest {
    @Test
    fun germanStringsArePopulated() {
        val german = stringsFor(Language.GERMAN)
        assertEquals("Deutsch", german.languageName)
        assertEquals("Startseite", german.menuHome)
        assertEquals("Über uns", german.menuAbout)
        assertEquals("Hilfe & Support", german.menuHelp)
        assertEquals("Diesen Service buchen", german.bookThisService)
        assertEquals("Angebotsdetails", german.serviceDetails)
        assertEquals("Wie können wir dir heute helfen?", german.homePrompt)
        assertEquals("Admin", german.adminTitle)
        assertEquals("5 Minuten", german.minutes(5))
        assertEquals("2 Stunden", german.hours("2"))
        assertEquals("(3 Bewertungen)", german.reviews(3))
        assertEquals("Seite 1 von 5", german.storiesPageOf(1, 5))
        assertEquals("1 Seite", german.storiesPages(1))
        assertEquals("4 Seiten", german.storiesPages(4))
        assertEquals("1 Anfrage übrig", german.meetsRemaining(1))
        assertEquals("3 Anfragen übrig", german.meetsRemaining(3))
        assertEquals("Anfrage an Jacob senden", german.meetsPitchPerson("Jacob"))

        val english = stringsFor(Language.ENGLISH)
        assertEquals("Fullscreen", english.layoutFullscreen)
        assertEquals("Exit fullscreen", english.layoutExitFullscreen)
        assertEquals("Vollbild", german.layoutFullscreen)
        assertEquals("Vollbild beenden", german.layoutExitFullscreen)
    }

    @Test
    fun flagForGermanReturnsCorrectFlag() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", flagFor(Language.GERMAN))
    }

    @Test
    fun allLanguagesHaveNonEmptyStringsAndFlags() {
        Language.entries.forEach { lang ->
            val strings = stringsFor(lang)
            assertTrue(strings.appTitle.isNotBlank())
            assertTrue(strings.languageName.isNotBlank())
            assertTrue(flagFor(lang).isNotBlank())
        }
    }

    @Test
    fun localizationStateSwitchesToGerman() {
        val original = Localization.language
        try {
            Localization.setLanguage(Language.GERMAN)
            assertEquals(Language.GERMAN, Localization.language)
            assertEquals("Deutsch", Localization.strings.languageName)
        } finally {
            Localization.setLanguage(original)
        }
    }
}
