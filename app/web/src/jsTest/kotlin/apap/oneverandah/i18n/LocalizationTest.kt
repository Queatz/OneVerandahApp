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
    fun burmeseStringsArePopulated() {
        val burmese = stringsFor(Language.BURMESE)
        assertEquals("မြန်မာစာ", burmese.languageName)
        assertEquals("ပင်မစာမျက်နှာ", burmese.menuHome)
        assertEquals("ကျွန်ုပ်တို့အကြောင်း", burmese.menuAbout)
        assertEquals("အကူအညီနှင့် ပံ့ပိုးမှု", burmese.menuHelp)
        assertEquals("ဒီဝန်ဆောင်မှုကို ရယူမည်", burmese.bookThisService)
        assertEquals("ဝန်ဆောင်မှုအသေးစိတ်", burmese.serviceDetails)
        assertEquals("ဒီကနေ့ ဘာကူညီပေးရမလဲ။", burmese.homePrompt)
        assertEquals("အက်ဒမင်", burmese.adminTitle)
        assertEquals("5 မိနစ်", burmese.minutes(5))
        assertEquals("2 နာရီ", burmese.hours("2"))
        assertEquals("(3 သုံးသပ်ချက်)", burmese.reviews(3))
        assertEquals("စာမျက်နှာ 1 / 5", burmese.storiesPageOf(1, 5))
        assertEquals("1 မျက်နှာ", burmese.storiesPages(1))
        assertEquals("4 မျက်နှာ", burmese.storiesPages(4))
        assertEquals("ကမ်းလှမ်းချက် 1 ခု ကျန်ရှိပါသည်", burmese.meetsRemaining(1))
        assertEquals("ကမ်းလှမ်းချက် 3 ခု ကျန်ရှိပါသည်", burmese.meetsRemaining(3))
        assertEquals("Jacob ထံ ကမ်းလှမ်းမည်", burmese.meetsPitchPerson("Jacob"))
        assertEquals("မျက်နှာပြင်ပြည့်", burmese.layoutFullscreen)
        assertEquals("မျက်နှာပြင်ပြည့်မှ ထွက်မည်", burmese.layoutExitFullscreen)
    }

    @Test
    fun flagForBurmeseReturnsCorrectFlag() {
        assertEquals("\uD83C\uDDF2\uD83C\uDDF2", flagFor(Language.BURMESE))
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

    @Test
    fun localizationStateSwitchesToBurmese() {
        val original = Localization.language
        try {
            Localization.setLanguage(Language.BURMESE)
            assertEquals(Language.BURMESE, Localization.language)
            assertEquals("မြန်မာစာ", Localization.strings.languageName)
        } finally {
            Localization.setLanguage(original)
        }
    }
}
