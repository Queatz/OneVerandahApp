package apap.oneverandah.components

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.model.CounterOption
import apap.oneverandah.model.Language
import apap.oneverandah.model.Money
import apap.oneverandah.model.PriceBreakdown
import apap.oneverandah.model.Review
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Formats a price delta as a signed hint, e.g. `+40.000 ₫`. Blank when zero. */
fun priceHint(amount: Money, language: Language): String {
    if (amount.isZero) return ""
    val sign = if (amount.amountVnd > 0) "+" else ""
    return "$sign${amount.format(language)}"
}

/**
 * A single selectable chip carrying a label and an optional price hint. Works
 * for both single- and multi-select groups (the caller controls [active]).
 */
@Composable
fun OptionChip(
    label: String,
    priceHint: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Div(attrs = {
        classes(Styles.chip)
        if (active) classes(Styles.chipActive)
        attr("role", "button")
        onClick { onClick() }
    }) {
        Text(label)
        if (priceHint.isNotEmpty()) {
            Span(attrs = { classes(Styles.chipPrice) }) { Text(priceHint) }
        }
    }
}

/** A quantity stepper for a [CounterOption]. */
@Composable
fun Counter(
    option: CounterOption,
    value: Int,
    language: Language,
    onChange: (Int) -> Unit,
) {
    Div(attrs = { classes(Styles.counter) }) {
        Button(attrs = {
            classes(Styles.counterBtn)
            if (value <= option.min) attr("disabled", "")
            attr("aria-label", "-")
            onClick { if (value > option.min) onChange(value - 1) }
        }) { Text("-") }
        Span(attrs = { classes(Styles.counterValue) }) { Text(value.toString()) }
        Button(attrs = {
            classes(Styles.counterBtn)
            if (value >= option.max) attr("disabled", "")
            attr("aria-label", "+")
            onClick { if (value < option.max) onChange(value + 1) }
        }) { Text("+") }
        Span(attrs = { classes(Styles.counterUnit) }) {
            Text("${option.unitLabel.resolve(language)} · ${option.unitPrice.format(language)}")
        }
    }
}

/** Renders the list of customer [reviews]. */
@Composable
fun ReviewList(reviews: List<Review>, language: Language) {
    Div(attrs = { classes(Styles.reviewList) }) {
        reviews.forEach { review ->
            Div(attrs = { classes(Styles.reviewCard) }) {
                Div(attrs = { classes(Styles.reviewHead) }) {
                    Span(attrs = { classes(Styles.reviewAuthor) }) { Text(review.author) }
                    Span(attrs = { classes(Styles.reviewStars) }) {
                        Text("★".repeat(review.rating) + "☆".repeat((5 - review.rating).coerceAtLeast(0)))
                    }
                }
                P(attrs = { classes(Styles.reviewBody) }) {
                    Text(review.comment.resolve(language))
                }
            }
        }
    }
}

/** Itemised price summary with a bold total. */
@Composable
fun PriceSummaryView(
    breakdown: PriceBreakdown,
    totalLabel: String,
    language: Language,
) {
    Div(attrs = { classes(Styles.priceSummary) }) {
        breakdown.lines.forEach { line ->
            Div(attrs = { classes(Styles.priceLine) }) {
                Span { Text(line.label.resolve(language)) }
                Span(attrs = { classes(Styles.priceLineAmount) }) {
                    Text(line.amount.format(language))
                }
            }
        }
        Div(attrs = { classes(Styles.priceTotal) }) {
            Span { Text(totalLabel) }
            Span { Text(breakdown.total.format(language)) }
        }
    }
}
