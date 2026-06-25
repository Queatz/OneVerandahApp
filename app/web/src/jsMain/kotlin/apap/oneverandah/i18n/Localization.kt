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
    val close: String,
    val bookingDialogTitle: String,
    val zaloLabel: String,
    val zaloPlaceholder: String,
    val zaloError: String,
    val messageLabel: String,
    val messagePlaceholder: String,
    val confirmBooking: String,
    val male: String,
    val female: String,
    val viewFront: String,
    val viewBack: String,
    val aboutText: String,
    val helpFeedback: String,
    val helpReportProblem: String,
    val helpSubmit: String,
    val helpPlaceholder: String,
    val helpErrorRequired: String,
    val helpSuccessTitle: String,
    val helpSuccessMessage: String,
    val helpListService: String,
    val listServiceDialogTitle: String,
    val serviceDescriptionLabel: String,
    val serviceDescriptionPlaceholder: String,
    val serviceDescriptionError: String,
    val yourNameLabel: String,
    val yourNamePlaceholder: String,
    val yourNameError: String,
    val blockAddressPlaceholder: String,
    val blockAddressError: String,
    val listServiceSuccessTitle: String,
    val listServiceSuccessMessage: String,
    val searchPlaceholder: String,
    val noResults: String,
    val providedBy: String,
    val providerBlockLabel: String,
    val providerPhoneLabel: String,
    val providerSexLabel: String,
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
    close = "Close",
    bookingDialogTitle = "Confirm your booking",
    zaloLabel = "Zalo number",
    zaloPlaceholder = "Your Zalo-enabled phone number",
    zaloError = "Zalo number is required",
    messageLabel = "Additional comments",
    messagePlaceholder = "Optional message for the provider",
    confirmBooking = "Confirm Booking",
    male = "Boy",
    female = "Girl",
    viewFront = "Front",
    viewBack = "Back",
    aboutText = "Made by One Verandah residents, for One Verandah residents.",
    helpFeedback = "Send us feedback",
    helpReportProblem = "Report a problem",
    helpSubmit = "Submit",
    helpPlaceholder = "Type your message here...",
    helpErrorRequired = "Message is required",
    helpSuccessTitle = "Thank you!",
    helpSuccessMessage = "Your submission has been received. We appreciate your feedback!",
    helpListService = "List a service",
    listServiceDialogTitle = "List a service",
    serviceDescriptionLabel = "Describe the service",
    serviceDescriptionPlaceholder = "What services do you provide?",
    serviceDescriptionError = "Service description is required",
    yourNameLabel = "Your name",
    yourNamePlaceholder = "Enter your name",
    yourNameError = "Your name is required",
    blockAddressPlaceholder = "Enter your block and unit number (e.g. Block A - A.12.03)",
    blockAddressError = "Block/Address is required",
    listServiceSuccessTitle = "Service listing submitted!",
    listServiceSuccessMessage = "Your service listing details have been sent. The host will review and add it manually soon!",
    searchPlaceholder = "Search for services...",
    noResults = "No services found",
    providedBy = "Provided by",
    providerBlockLabel = "Block/Address",
    providerPhoneLabel = "Zalo Phone",
    providerSexLabel = "Sex",
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
    close = "Đóng",
    bookingDialogTitle = "Xác nhận đặt lịch",
    zaloLabel = "Số Zalo",
    zaloPlaceholder = "Số điện thoại hỗ trợ Zalo",
    zaloError = "Vui lòng nhập số Zalo",
    messageLabel = "Bình luận thêm",
    messagePlaceholder = "Tin nhắn tùy chọn cho nhà cung cấp",
    confirmBooking = "Xác nhận đặt lịch",
    male = "Nam",
    female = "Nữ",
    viewFront = "Trước",
    viewBack = "Sau",
    aboutText = "Được thực hiện bởi cư dân One Verandah, dành cho cư dân One Verandah.",
    helpFeedback = "Gửi phản hồi cho chúng tôi",
    helpReportProblem = "Báo cáo sự cố",
    helpSubmit = "Gửi",
    helpPlaceholder = "Nhập tin nhắn của bạn tại đây...",
    helpErrorRequired = "Vui lòng nhập tin nhắn",
    helpSuccessTitle = "Cảm ơn bạn!",
    helpSuccessMessage = "Ý kiến của bạn đã được ghi nhận. Chúng tôi rất trân trọng phản hồi của bạn!",
    helpListService = "Đăng ký dịch vụ",
    listServiceDialogTitle = "Đăng ký dịch vụ",
    serviceDescriptionLabel = "Mô tả dịch vụ",
    serviceDescriptionPlaceholder = "Bạn cung cấp những dịch vụ gì?",
    serviceDescriptionError = "Vui lòng nhập mô tả dịch vụ",
    yourNameLabel = "Tên của bạn",
    yourNamePlaceholder = "Nhập tên của bạn",
    yourNameError = "Vui lòng nhập tên",
    blockAddressPlaceholder = "Nhập số căn hộ và tòa (ví dụ: Block A - A.12.03)",
    blockAddressError = "Vui lòng nhập căn hộ/tòa",
    listServiceSuccessTitle = "Đã gửi thông tin dịch vụ!",
    listServiceSuccessMessage = "Thông tin dịch vụ của bạn đã được gửi. Người quản trị sẽ xem xét và tự tay thêm vào ứng dụng sớm!",
    searchPlaceholder = "Tìm kiếm dịch vụ...",
    noResults = "Không tìm thấy dịch vụ",
    providedBy = "Được cung cấp bởi",
    providerBlockLabel = "Căn hộ/Tòa",
    providerPhoneLabel = "Số điện thoại Zalo",
    providerSexLabel = "Giới tính",
)

private val Chinese = UiStrings(
    appTitle = "One Verandah 应用",
    homePrompt = "今天您在寻找什么？",
    reviews = { count -> "（$count 条评论）" },
    sectionBookingType = "预订类型",
    sectionDuration = "时长",
    sectionTargetArea = "目标区域",
    sectionComments = "备注",
    sectionReviews = "评论",
    minutes = { minutes -> "$minutes 分钟" },
    from = "起",
    total = "总计",
    priceSummary = "价格摘要",
    tapToSelect = "点击身体选择区域",
    bookThisService = "预订此服务",
    booking = "预订中...",
    bookingSuccessTitle = "预订已确认！",
    bookingSuccessMessage = "我们已将详情发送给服务提供商。期待与您见面！",
    done = "完成",
    back = "返回",
    menu = "菜单",
    languageName = "中文",
    menuTitle = "One Verandah 应用",
    menuHome = "首页",
    menuAbout = "关于",
    menuLanguage = "语言",
    menuHelp = "帮助与支持",
    close = "关闭",
    bookingDialogTitle = "确认您的预订",
    zaloLabel = "Zalo 号码",
    zaloPlaceholder = "您的 Zalo 手机号码",
    zaloError = "请输入您的 Zalo 号码",
    messageLabel = "备注",
    messagePlaceholder = "给服务提供商的可选留言",
    confirmBooking = "确认预订",
    male = "男",
    female = "女",
    viewFront = "正面",
    viewBack = "背面",
    aboutText = "由 One Verandah 居民制作，服务于 One Verandah 居民。",
    helpFeedback = "向我们发送反馈",
    helpReportProblem = "报告问题",
    helpSubmit = "提交",
    helpPlaceholder = "在此输入您的留言...",
    helpErrorRequired = "内容不能为空",
    helpSuccessTitle = "谢谢您！",
    helpSuccessMessage = "我们已收到您的提交。感谢您的反馈！",
    helpListService = "发布服务",
    listServiceDialogTitle = "发布服务",
    serviceDescriptionLabel = "描述服务",
    serviceDescriptionPlaceholder = "您提供什么服务？",
    serviceDescriptionError = "请输入服务描述",
    yourNameLabel = "您的姓名",
    yourNamePlaceholder = "请输入您的姓名",
    yourNameError = "请输入姓名",
    blockAddressPlaceholder = "输入楼栋和单元号 (例如 Block A - A.12.03)",
    blockAddressError = "楼栋/地址是必填项",
    listServiceSuccessTitle = "服务申请已提交！",
    listServiceSuccessMessage = "您的 service 上市详情已发送。平台管理员将很快进行审核并手动添加！",
    searchPlaceholder = "搜索服务...",
    noResults = "未找到服务",
    providedBy = "服务提供者",
    providerBlockLabel = "楼栋/地址",
    providerPhoneLabel = "Zalo 电话",
    providerSexLabel = "性别",
)

fun stringsFor(language: Language): UiStrings = when (language) {
    Language.ENGLISH -> English
    Language.VIETNAMESE -> Vietnamese
    Language.CHINESE -> Chinese
}

/** Returns the flag emoji used to represent [language] in the UI. */
fun flagFor(language: Language): String = when (language) {
    Language.ENGLISH -> "\uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    Language.VIETNAMESE -> "\uD83C\uDDFB\uD83C\uDDF3" // 🇻🇳
    Language.CHINESE -> "\uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
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
        language = when (language) {
            Language.ENGLISH -> Language.VIETNAMESE
            Language.VIETNAMESE -> Language.CHINESE
            Language.CHINESE -> Language.ENGLISH
        }
    }
}

/**
 * Inspects the browser's preferred languages and returns the matching language
 * (Vietnamese or Chinese) when the user prefers it, otherwise English.
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
        val lang = Language.fromTag(tag)
        if (lang != Language.DEFAULT) return lang
    }
    return Language.DEFAULT
}
