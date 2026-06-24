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
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.attributes.placeholder

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

/**
 * Modal dialog shown when pressing "Book This Service".
 * Displays service name (bold), price summary, message field and required Zalo field.
 */
@Composable
fun BookingDialog(
    visible: Boolean,
    serviceTitle: String,
    breakdown: PriceBreakdown,
    totalLabel: String,
    language: Language,
    strings: apap.oneverandah.i18n.UiStrings,
    initialZalo: String?,
    initialMessage: String?,
    onZaloChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (zalo: String, message: String?) -> Unit,
) {
    if (!visible) return

    Div {
        // Simple overlay container - styles can be added later
        Div(attrs = { classes(Styles.textField) }) {
            P { Text(strings.bookingDialogTitle) }
            // Service name bold
            P { 
                // bold via style or assume
                Text(serviceTitle)
            }

            // Price summary
            PriceSummaryView(breakdown = breakdown, totalLabel = totalLabel, language = language)

            // Message field
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.messageLabel)
                }
                TextArea(value = initialMessage ?: "") {
                    classes(Styles.textArea)
                    placeholder(strings.messagePlaceholder)
                    onInput { onMessageChange(it.value.ifBlank { "" }) }
                }
            }

            // Zalo field (required)
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.zaloLabel)
                }
                TextArea(value = initialZalo ?: "") {
                    classes(Styles.textArea)
                    placeholder(strings.zaloPlaceholder)
                    onInput { onZaloChange(it.value.ifBlank { "" }) }
                }
            }

            // Actions
            Div {
                Button(attrs = {
                    onClick { onDismiss() }
                }) { Text(strings.close) }

                Button(attrs = {
                    onClick {
                        val zalo = initialZalo?.trim() ?: ""
                        if (zalo.isNotEmpty()) {
                            onConfirm(zalo, initialMessage)
                        }
                    }
                }) { Text(strings.confirmBooking) }
            }
        }
    }
}
