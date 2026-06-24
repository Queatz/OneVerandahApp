package apap.oneverandah.model

/**
 * A customer review shown on a service's offer screen.
 */
data class Review(
    val id: String,
    val author: String,
    /** Whole-star rating in the 1..5 range. */
    val rating: Int,
    val comment: LocalizedText,
)

/**
 * A selectable choice inside a [SingleSelectOption] or [MultiSelectOption].
 *
 * [priceDelta] is added to the booking total when the choice is selected.
 */
data class Choice(
    val id: String,
    val label: LocalizedText,
    val priceDelta: Money = Money.ZERO,
)

/**
 * A clickable region of the [BodyMapOption] SVG body diagram.
 *
 * [id] must match one of the known region ids the renderer knows how to draw
 * (e.g. `head`, `shoulders`, `upper-back`, `lower-back`, `arms`, `legs`, `feet`).
 */
data class BodyPart(
    val id: String,
    val label: LocalizedText,
    val priceDelta: Money = Money.ZERO,
)

/**
 * A unit of per-service customisation. Each concrete type renders with its own
 * UI, which is what makes every service feel unique:
 *  - [SingleSelectOption] -> a single-choice chip group
 *  - [MultiSelectOption]  -> a multi-choice chip group
 *  - [CounterOption]      -> a quantity stepper
 *  - [BodyMapOption]      -> an interactive SVG body-part selector
 *
 * Modelled as a sealed hierarchy of plain data classes so the whole catalog can
 * be serialised to/from a future API without behaviour leaking into the model.
 */
sealed interface ServiceOption {
    val id: String
    val title: LocalizedText
}

data class SingleSelectOption(
    override val id: String,
    override val title: LocalizedText,
    val choices: List<Choice>,
    val defaultChoiceId: String = choices.first().id,
) : ServiceOption

data class MultiSelectOption(
    override val id: String,
    override val title: LocalizedText,
    val choices: List<Choice>,
    val defaultSelectedIds: Set<String> = emptySet(),
) : ServiceOption

data class CounterOption(
    override val id: String,
    override val title: LocalizedText,
    val unitLabel: LocalizedText,
    val unitPrice: Money,
    val min: Int = 1,
    val max: Int = 10,
    val default: Int = 1,
) : ServiceOption

data class BodyMapOption(
    override val id: String,
    override val title: LocalizedText,
    val parts: List<BodyPart>,
    /** When true the user may select multiple body parts at once. */
    val multiSelect: Boolean = true,
    val defaultSelectedIds: Set<String> = emptySet(),
) : ServiceOption

/**
 * The user's in-progress selections for a [Service]. Built up by the UI and
 * fed to [PricingCalculator] and into a [BookingRequest].
 */
data class BookingSelection(
    val bookingTypeId: String,
    val durationId: String,
    /** optionId -> selected choiceId. */
    val singleSelects: Map<String, String> = emptyMap(),
    /** optionId -> selected choiceIds. */
    val multiSelects: Map<String, Set<String>> = emptyMap(),
    /** optionId -> chosen quantity. */
    val counters: Map<String, Int> = emptyMap(),
    /** optionId -> selected body-part ids. */
    val bodyParts: Map<String, Set<String>> = emptyMap(),
    val comment: String? = null,
    val zalo: String? = null,
)
