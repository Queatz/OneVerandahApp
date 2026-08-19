package apap.oneverandah.model

/**
 * Supported user interface languages.
 *
 * [tag] is the primary ISO 639-1 code used both for matching the browser
 * language and (later) for an `Accept-Language` style API contract.
 */
enum class Language(val tag: String) {
    ENGLISH("en"),
    VIETNAMESE("vi"),
    CHINESE("zh"),
    JAPANESE("ja"),
    KOREAN("ko"),
    RUSSIAN("ru");

    /** Capitalized English name for menus — never the ISO tag. */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.titlecase() }

    companion object {
        /** The language used when nothing else matches. */
        val DEFAULT: Language = ENGLISH

        /**
         * Resolves a [Language] from a raw browser/locale tag such as
         * `"vi"`, `"vi-VN"` or `"en-US"`. Falls back to [DEFAULT] when the
         * tag is unknown so callers never have to deal with `null`.
         */
        fun fromTag(tag: String?): Language {
            val normalized = tag?.trim()?.lowercase()?.substringBefore('-') ?: return DEFAULT
            return entries.firstOrNull { it.tag == normalized } ?: DEFAULT
        }
    }
}

/**
 * A piece of text that carries every translation we support.
 *
 * Keeping translations on the data itself (rather than a separate key lookup)
 * means the same model can be serialized straight to/from an API later without
 * the client needing a bundled dictionary for catalog content.
 *
 * Missing language fields fall back to English so catalog entries can grow
 * translations incrementally.
 */
@kotlinx.serialization.Serializable
data class LocalizedText(
    val en: String,
    val vi: String,
    val zh: String = en,
    val ja: String = en,
    val ko: String = en,
    val ru: String = en,
) {
    fun resolve(language: Language): String = when (language) {
        Language.ENGLISH -> en
        Language.VIETNAMESE -> vi
        Language.CHINESE -> zh
        Language.JAPANESE -> ja
        Language.KOREAN -> ko
        Language.RUSSIAN -> ru
    }
}

/**
 * A grouping of [Category] objects (e.g. "Relax", "Train").
 */
data class SuperCategory(
    val id: String,
    val title: LocalizedText,
)

/**
 * A top level grouping shown on the home screen (e.g. "Massages").
 */
data class Category(
    val id: String,
    val superCategoryId: String,
    val title: LocalizedText,
    /** Material Symbols ligature name, e.g. `"spa"`. */
    val icon: String,
)

enum class Sex {
    MALE, FEMALE
}

/**
 * Operational status of a [Service], used to manually adjust its visibility
 * and bookability without removing the service definition from the catalog.
 */
enum class ServiceStatus {
    /** Normal behaviour: the service is listed and bookable. */
    AVAILABLE,

    /**
     * The service is still listed and visible on the offer screen, but a banner
     * is shown and the "Book This Service" button is disabled.
     */
    TEMPORARILY_UNAVAILABLE,

    /** The service is completely hidden from listings, search and lookups. */
    UNAVAILABLE,
}

/**
 * A skill advertised on a [ServiceProvider]'s profile.
 *
 * [level] is a 1–5 proficiency score shown as a visual meter in the UI.
 * [emoji] is a short emoji (or emoji sequence) used as the skill's icon.
 * Out-of-range levels are clamped so catalog data stays resilient.
 */
data class ProviderSkill(
    val name: LocalizedText,
    val emoji: String,
    val level: Int,
) {
    init {
        require(level in 1..5) {
            "Provider skill level must be between 1 and 5 (was $level)"
        }
    }

    companion object {
        /** Builds a [ProviderSkill], clamping [level] into 1..5. */
        fun of(name: LocalizedText, emoji: String, level: Int): ProviderSkill =
            ProviderSkill(name = name, emoji = emoji, level = level.coerceIn(1, 5))
    }
}

/**
 * A service provider who performs a [Service].
 */
data class ServiceProvider(
    val name: String,
    val sex: Sex,
    val blockAddress: String,
    val phone: String,
    val photo: String, // Material symbol or image path/URL
    /** A short introduction shown in the provider details dialog. */
    val intro: LocalizedText = LocalizedText("", ""),
    /**
     * Skills highlighted on the provider profile, each with an emoji and a
     * 1–5 level. Treated as a primary piece of profile information.
     */
    val skills: List<ProviderSkill> = emptyList(),
    /**
     * Optional explicit override for [id]. Used to disambiguate providers who
     * share the same [name] (and would otherwise produce the same slug).
     */
    private val idOverride: String? = null,
) {
    /**
     * A unique, URL- and user-friendly identifier used in deep links
     * (e.g. `"Ha"` -> `"ha"` -> `/provider/ha`). Derived from the provider's
     * [name] by default, or taken from [idOverride] when provided to keep ids
     * unique across providers with the same name.
     */
    val id: String = idOverride ?: slugify(name)

    companion object {
        /**
         * Converts an arbitrary display [text] into a lowercase, URL-friendly
         * slug: ASCII letters/digits are kept, runs of anything else collapse
         * to a single hyphen, and leading/trailing hyphens are trimmed.
         */
        fun slugify(text: String): String {
            val slug = StringBuilder()
            var pendingHyphen = false
            for (ch in text.lowercase()) {
                if (ch in 'a'..'z' || ch in '0'..'9') {
                    if (pendingHyphen && slug.isNotEmpty()) slug.append('-')
                    pendingHyphen = false
                    slug.append(ch)
                } else {
                    pendingHyphen = true
                }
            }
            return slug.toString()
        }
    }
}

/**
 * A bookable offering inside a [Category] (e.g. "Word Massages by Jacob").
 */
data class Service(
    val id: String,
    val categoryId: String,
    /** Material Symbols ligature name. */
    val icon: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val rating: Double,
    val reviewCount: Int,
    /** The cheapest bookable price, shown as a "from" hint in lists. */
    val basePrice: Money,
    val bookingTypes: List<BookingTypeOption>,
    val durations: List<DurationOption>,
    val provider: ServiceProvider,
    /** Manually adjustable operational status controlling visibility/bookability. */
    val status: ServiceStatus = ServiceStatus.AVAILABLE,
    /** Per-service customisation options that drive the unique booking UI. */
    val options: List<ServiceOption> = emptyList(),
    /** Customer reviews shown on the offer screen. */
    val reviews: List<Review> = emptyList(),
    /** Optional label/placeholder for the free-form comment field. */
    val commentPrompt: CommentPrompt? = null,
) {
    /** A reasonable initial [BookingSelection] using each option's defaults. */
    fun defaultSelection(): BookingSelection = BookingSelection(
        bookingTypeId = bookingTypes.first().id,
        durationId = durations.first().id,
        singleSelects = options.filterIsInstance<SingleSelectOption>()
            .associate { it.id to it.defaultChoiceId },
        multiSelects = options.filterIsInstance<MultiSelectOption>()
            .associate { it.id to it.defaultSelectedIds },
        counters = options.filterIsInstance<CounterOption>()
            .associate { it.id to it.default },
        bodyParts = options.filterIsInstance<BodyMapOption>()
            .associate { it.id to it.defaultSelectedIds },
    )
}

/**
 * A selectable "kind of booking" chip (e.g. initial trial vs. follow up).
 *
 * [priceDelta] adjusts the total (e.g. a discounted initial trial).
 */
data class BookingTypeOption(
    val id: String,
    val label: LocalizedText,
    val priceDelta: Money = Money.ZERO,
)

/**
 * A selectable duration chip. [minutes] is the canonical, locale-free value and
 * [price] is the base price for booking that duration.
 */
data class DurationOption(
    val id: String,
    val minutes: Int,
    val price: Money,
)

/** Label + placeholder for the comment text field. */
data class CommentPrompt(
    val label: LocalizedText,
    val placeholder: LocalizedText,
)

/**
 * The payload a client would POST to create a booking.
 *
 * Modelled as a plain data class so it round-trips cleanly through a future
 * JSON API.
 */
data class BookingRequest(
    val serviceId: String,
    val selection: BookingSelection,
    /** The total price, in VND, computed at booking time. */
    val totalVnd: Long,
)

/** The server's response to a [BookingRequest]. */
data class BookingConfirmation(
    val confirmationId: String,
    val request: BookingRequest,
)
