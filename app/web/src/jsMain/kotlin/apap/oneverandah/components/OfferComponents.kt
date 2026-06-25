package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.model.CounterOption
import apap.oneverandah.model.Language
import apap.oneverandah.model.Money
import apap.oneverandah.model.PriceBreakdown
import apap.oneverandah.model.Review
import apap.oneverandah.model.ServiceProvider
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value

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
 * Modal bottom-sheet dialog template.
 * Handles the dimmed scrim, drag-to-dismiss behavior, and drag handle.
 */
@Composable
fun BottomDialog(
    visible: Boolean,
    closeLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Drag-to-dismiss state for the bottom sheet handle.
    // `dragStart` is the pointer Y where the gesture began (null when idle),
    // `dragY` is how far the sheet is currently pulled down (>= 0).
    var dragStart by remember { mutableStateOf<Double?>(null) }
    var dragY by remember { mutableStateOf(0.0) }
    // Distance the sheet must be pulled down before it dismisses on release.
    val dismissThreshold = 120.0

    // Whenever the sheet closes, fully release any in-progress drag so it
    // doesn't reopen stuck in the "grabbing" state (e.g. when the pointer was
    // released outside the sheet and never fired mouseup/touchend here).
    LaunchedEffect(visible) {
        if (!visible) {
            dragStart = null
            dragY = 0.0
        }
    }

    // Scrim covers the frame; clicking it dismisses the sheet.
    Div(attrs = {
        classes(Styles.dialogScrim)
        if (visible) classes(Styles.dialogScrimVisible)
        attr("role", "presentation")
        onClick { onDismiss() }
    }) {
        Div(attrs = {
            classes(Styles.dialogSheet)
            attr("role", "dialog")
            attr("aria-modal", "true")
            // While dragging, follow the pointer and suppress the open
            // transition so the sheet tracks the finger 1:1.
            if (dragStart != null) {
                style {
                    property("transform", "translateY(${dragY}px)")
                    property("transition", "none")
                }
            }
            // Keep clicks inside the sheet from bubbling to the scrim.
            onClick { it.stopPropagation() }
            // Track the drag on the sheet itself: as it translates down, the
            // grip stays under the pointer, so move/up keep firing here.
            onMouseMove { e ->
                if (dragStart != null) dragY = (e.clientY - dragStart!!).coerceAtLeast(0.0)
            }
            onTouchMove { e ->
                val start = dragStart ?: return@onTouchMove
                val touch = e.touches.item(0) ?: return@onTouchMove
                dragY = (touch.clientY - start).coerceAtLeast(0.0)
            }
            onMouseUp {
                if (dragStart != null) {
                    if (dragY > dismissThreshold) onDismiss()
                    dragStart = null
                    dragY = 0.0
                }
            }
            onTouchEnd {
                if (dragStart != null) {
                    if (dragY > dismissThreshold) onDismiss()
                    dragStart = null
                    dragY = 0.0
                }
            }
        }) {
            // Grab handle: start a drag gesture from here.
            Div(attrs = {
                classes(Styles.dialogGrip)
                attr("role", "button")
                attr("aria-label", closeLabel)
                onMouseDown { e ->
                    dragStart = e.clientY.toDouble()
                    dragY = 0.0
                }
                onTouchStart { e ->
                    val touch = e.touches.item(0)
                    if (touch != null) {
                        dragStart = touch.clientY.toDouble()
                        dragY = 0.0
                    }
                }
            }) {
                Div(attrs = { classes(Styles.dialogHandle) })
            }

            content()
        }
    }
}

/**
 * Modal bottom-sheet dialog shown when pressing "Book This Service".
 *
 * Follows the app's Material-3 design language: a dimmed scrim, a rounded
 * bottom sheet with a drag handle, the service name in bold, the same price
 * summary as the offer screen, an optional message field and a required
 * Zalo-enabled phone number. Always rendered so the open/close transition
 * animates via the [visible] toggle.
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
    onConfirm: (zalo: String, message: String?, whenOption: String) -> Unit,
) {
    val zalo = initialZalo.orEmpty()
    val message = initialMessage.orEmpty()

    var showError by remember { mutableStateOf(false) }

    // "When" options offered to the user. The first entry is the default.
    val whenOptions = listOf(
        strings.whenNow,
        strings.whenToday,
        strings.whenTomorrow,
        strings.whenLater,
    )
    var selectedWhen by remember { mutableStateOf(whenOptions.first()) }

    // Whenever the sheet closes, reset showError and the "When" selection.
    LaunchedEffect(visible) {
        if (!visible) {
            showError = false
            selectedWhen = whenOptions.first()
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.bookingDialogTitle) }
            P(attrs = { classes(Styles.dialogService) }) { Text(serviceTitle) }
        }

        // Same price summary as the offer screen.
        PriceSummaryView(breakdown = breakdown, totalLabel = totalLabel, language = language)

        Div(attrs = { classes(Styles.dialogBody) }) {
            // "When" the service is wanted.
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.whenLabel)
                }
                Div(attrs = { classes(Styles.chipGroup) }) {
                    whenOptions.forEach { option ->
                        OptionChip(
                            label = option,
                            priceHint = "",
                            active = selectedWhen == option,
                            onClick = { selectedWhen = option },
                        )
                    }
                }
            }

            // Optional message field.
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.messageLabel)
                }
                TextArea(value = message) {
                    classes(Styles.textArea)
                    placeholder(strings.messagePlaceholder)
                    onInput { onMessageChange(it.value) }
                }
            }

            // Required Zalo-enabled phone number.
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(strings.zaloLabel)
                }
                Input(type = InputType.Tel) {
                    classes(Styles.textInput)
                    value(zalo)
                    placeholder(strings.zaloPlaceholder)
                    attr("required", "")
                    onInput {
                        onZaloChange(it.value)
                        showError = false
                    }
                }
                if (showError && zalo.isBlank()) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(strings.zaloError)
                    }
                }
            }
        }

        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                onClick { onDismiss() }
            }) { Text(strings.close) }

            PrimaryButton(
                label = strings.confirmBooking,
                icon = "check_circle",
                enabled = true,
            ) {
                if (zalo.isNotBlank()) {
                    onConfirm(zalo.trim(), message.ifBlank { null }, selectedWhen)
                } else {
                    showError = true
                }
            }
        }
    }
}

/**
 * Modal bottom-sheet dialog shown when clicking on the provider name or photo.
 * Shows the provider details: Name, Sex, Block/Address, and Zalo phone.
 */
@Composable
fun ProviderDetailsDialog(
    visible: Boolean,
    provider: ServiceProvider,
    language: Language,
    strings: apap.oneverandah.i18n.UiStrings,
    onDismiss: () -> Unit,
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss
    ) {
        // Provider Avatar / Photo inside the header
        Div(attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("align-items", "center")
                property("margin-bottom", "24px")
            }
        }) {
            Div(attrs = { classes(Styles.avatar) }) {
                MaterialSymbol(provider.photo)
            }
            P(attrs = {
                style {
                    property("font-size", "22px")
                    property("font-weight", "bold")
                    property("margin", "8px 0 0 0")
                }
            }) {
                Text(provider.name)
            }
        }

        // Body of the details
        Div(attrs = { classes(Styles.dialogBody) }) {
            // Sex
            Div(attrs = {
                style {
                    property("margin-bottom", "16px")
                    property("display", "flex")
                    property("flex-direction", "column")
                }
            }) {
                Span(attrs = {
                    style {
                        property("font-size", "12px")
                        property("color", "var(--md-sys-color-on-surface-variant)")
                        property("font-weight", "700")
                        property("text-transform", "uppercase")
                    }
                }) {
                    Text(strings.providerSexLabel)
                }
                Span(attrs = {
                    style {
                        property("font-size", "16px")
                        property("margin-top", "4px")
                    }
                }) {
                    Text(if (provider.sex == apap.oneverandah.model.Sex.MALE) strings.male else strings.female)
                }
            }

            // Block/Address
            Div(attrs = {
                style {
                    property("margin-bottom", "16px")
                    property("display", "flex")
                    property("flex-direction", "column")
                }
            }) {
                Span(attrs = {
                    style {
                        property("font-size", "12px")
                        property("color", "var(--md-sys-color-on-surface-variant)")
                        property("font-weight", "700")
                        property("text-transform", "uppercase")
                    }
                }) {
                    Text(strings.providerBlockLabel)
                }
                Span(attrs = {
                    style {
                        property("font-size", "16px")
                        property("margin-top", "4px")
                    }
                }) {
                    Text(provider.blockAddress)
                }
            }

            // Phone/Zalo
            Div(attrs = {
                style {
                    property("margin-bottom", "16px")
                    property("display", "flex")
                    property("flex-direction", "column")
                }
            }) {
                Span(attrs = {
                    style {
                        property("font-size", "12px")
                        property("color", "var(--md-sys-color-on-surface-variant)")
                        property("font-weight", "700")
                        property("text-transform", "uppercase")
                    }
                }) {
                    Text(strings.providerPhoneLabel)
                }
                A(
                    href = "tel:${provider.phone}",
                    attrs = {
                        style {
                            property("font-size", "16px")
                            property("margin-top", "4px")
                            property("color", "var(--md-sys-color-primary)")
                            property("text-decoration", "underline")
                        }
                    }
                ) {
                    Text(provider.phone)
                }
            }
        }

        // Close actions
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                onClick { onDismiss() }
            }) { Text(strings.close) }
        }
    }
}
