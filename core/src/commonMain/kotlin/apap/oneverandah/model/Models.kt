package apap.oneverandah.model

/**
 * Supported user interface languages.
 *
 * [tag] is the primary ISO 639-1 code used both for matching the browser
 * language and (later) for an `Accept-Language` style API contract.
 */
enum class Language(val tag: String) {
    ENGLISH("en"),
    VIETNAMESE("vi");

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
 */
data class LocalizedText(
    val en: String,
    val vi: String,
) {
    fun resolve(language: Language): String = when (language) {
        Language.ENGLISH -> en
        Language.VIETNAMESE -> vi
    }
}

/**
 * A top level grouping shown on the home screen (e.g. "Massages").
 */
data class Category(
    val id: String,
    val title: LocalizedText,
    /** Material Icons ligature name, e.g. `"spa"`. */
    val icon: String,
)

/**
 * A bookable offering inside a [Category] (e.g. "Word Massages by Jacob").
 */
data class Service(
    val id: String,
    val categoryId: String,
    /** Material Icons ligature name. */
    val icon: String,
    val title: LocalizedText,
    val description: LocalizedText,
    val rating: Double,
    val reviewCount: Int,
    /** The cheapest bookable price, shown as a "from" hint in lists. */
    val basePrice: Money,
    val bookingTypes: List<BookingTypeOption>,
    val durations: List<DurationOption>,
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
