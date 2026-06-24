package apap.oneverandah.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import apap.oneverandah.model.Language
import kotlinx.browser.window

/**
 * Bundle of every user-facing UI string (i.e. app "chrome": titles, buttons,
 * section headers). Catalog content (category/service names) is localized on
 * the data itself via [apap.oneverandah.model.LocalizedText].
 */
data class UiStrings(
    val appTitle: String,
    val homePrompt: String,
    val reviews: (count: Int) -> String,
    val sectionBookingType: String,
    val sectionDuration: String,
    val sectionTargetArea: String,
    val sectionComments: String,
    val sectionReviews: String,
    val minutes: (minutes: Int) -> String,
    val from: String,
    val total: String,
    val priceSummary: String,
    val tapToSelect: String,
    val bookThisService: String,
    val booking: String,
    val bookingSuccessTitle: String,
    val bookingSuccessMessage: String,
    val done: String,
    val back: String,
    val menu: String,
    val languageName: String,
    // Navigation drawer
    val menuTitle: String,
    val menuHome: String,
    val menuAbout: String,
    val menuLanguage: String,
    val menuHelp: String,
    val menuTheme: String,
    val close: String,
)

private val English = UiStrings(
    appTitle = "One Verandah App",
    homePrompt = "What are you looking for today?",
    reviews = { count -> "($count reviews)" },
    sectionBookingType = "Booking Type",
    sectionDuration = "Duration",
    sectionTargetArea = "Target Area",
    sectionComments = "Comments",
    sectionReviews = "Reviews",
    minutes = { minutes -> "$minutes minutes" },
    from = "from",
    total = "Total",
    priceSummary = "Price summary",
    tapToSelect = "Tap the body to choose areas",
    bookThisService = "Book This Service",
    booking = "Booking...",
    bookingSuccessTitle = "Booking confirmed!",
    bookingSuccessMessage = "We've sent the details to your provider. See you soon!",
    done = "Done",
    back = "Back",
    menu = "Menu",
    languageName = "English",
    menuTitle = "One Verandah App",
    menuHome = "Home",
    menuAbout = "About",
    menuLanguage = "Language",
    menuHelp = "Help & support",
    menuTheme = "Appearance follows your system",
    close = "Close",
)

private val Vietnamese = UiStrings(
    appTitle = "Ứng dụng One Verandah",
    homePrompt = "Hôm nay bạn đang tìm kiếm điều gì?",
    reviews = { count -> "($count đánh giá)" },
    sectionBookingType = "Loại đặt lịch",
    sectionDuration = "Thời lượng",
    sectionTargetArea = "Vùng cần chăm sóc",
    sectionComments = "Ghi chú",
    sectionReviews = "Đánh giá",
    minutes = { minutes -> "$minutes phút" },
    from = "từ",
    total = "Tổng cộng",
    priceSummary = "Tóm tắt giá",
    tapToSelect = "Chạm vào cơ thể để chọn vùng",
    bookThisService = "Đặt dịch vụ này",
    booking = "Đang đặt...",
    bookingSuccessTitle = "Đã xác nhận đặt lịch!",
    bookingSuccessMessage = "Chúng tôi đã gửi thông tin cho người cung cấp. Hẹn gặp lại bạn!",
    done = "Xong",
    back = "Quay lại",
    menu = "Menu",
    languageName = "Tiếng Việt",
    menuTitle = "Ứng dụng One Verandah",
    menuHome = "Trang chủ",
    menuAbout = "Giới thiệu",
    menuLanguage = "Ngôn ngữ",
    menuHelp = "Trợ giúp & hỗ trợ",
    menuTheme = "Giao diện theo hệ thống của bạn",
    close = "Đóng",
)

fun stringsFor(language: Language): UiStrings = when (language) {
    Language.ENGLISH -> English
    Language.VIETNAMESE -> Vietnamese
}

/** Returns the flag emoji used to represent [language] in the UI. */
fun flagFor(language: Language): String = when (language) {
    Language.ENGLISH -> "\uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    Language.VIETNAMESE -> "\uD83C\uDDFB\uD83C\uDDF3" // 🇻🇳
}

/**
 * Holds the currently selected [Language] as Compose state so the whole UI
 * recomposes when it changes.
 *
 * The initial value is derived from the browser: if the user's preferred
 * language is Vietnamese we use Vietnamese, otherwise we fall back to English.
 */
object Localization {
    var language: Language by mutableStateOf(detectBrowserLanguage())
        private set

    val strings: UiStrings get() = stringsFor(language)

    fun setLanguage(newLanguage: Language) {
        language = newLanguage
    }

    /** Cycles between the supported languages (used by the header toggle). */
    fun toggleLanguage() {
        language = if (language == Language.VIETNAMESE) Language.ENGLISH else Language.VIETNAMESE
    }
}

/**
 * Inspects the browser's preferred languages and returns Vietnamese when the
 * user prefers it, otherwise English.
 *
 * `navigator.languages` is checked first (ordered by user preference), then we
 * fall back to the single `navigator.language` value.
 */
private fun detectBrowserLanguage(): Language {
    val navigator = window.navigator
    val candidates: List<String> = buildList {
        navigator.languages.forEach { add(it) }
        navigator.language.let { if (it.isNotBlank()) add(it) }
    }
    for (tag in candidates) {
        if (Language.fromTag(tag) == Language.VIETNAMESE) return Language.VIETNAMESE
    }
    return Language.DEFAULT
}
