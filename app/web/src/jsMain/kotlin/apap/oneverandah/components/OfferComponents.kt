package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.model.CounterOption
import apap.oneverandah.model.Language
import apap.oneverandah.model.Money
import apap.oneverandah.model.PriceBreakdown
import apap.oneverandah.model.ProviderSkill
import apap.oneverandah.model.Review
import apap.oneverandah.model.ServiceProvider
import apap.oneverandah.state.ImageStore
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px

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

/**
 * A quantity stepper for a [CounterOption].
 *
 * The center number is tappable and invokes [onEditRequest] so the hosting
 * screen can open a [CounterDialog] (a bottom sheet anchored to the screen,
 * not to this inline control) for typing/nudging the value.
 */
@Composable
fun Counter(
    option: CounterOption,
    value: Int,
    language: Language,
    onEditRequest: () -> Unit,
    onChange: (Int) -> Unit,
) {
    Div(attrs = { classes(Styles.counter) }) {
        Button(attrs = {
            classes(Styles.counterBtn)
            if (value <= option.min) attr("disabled", "")
            attr("aria-label", "-")
            onClick { if (value > option.min) onChange(value - 1) }
        }) { Text("-") }
        Span(attrs = {
            classes(Styles.counterValue)
            attr("role", "button")
            attr("tabindex", "0")
            onClick { onEditRequest() }
        }) { Text(value.toString()) }
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

/**
 * Modal bottom sheet for editing a [CounterOption]'s quantity.
 *
 * Rendered at the screen level (e.g. via the scaffold overlay slot) so it
 * sticks to the bottom of the screen rather than the inline [Counter]. It edits
 * a local draft and only commits to [onConfirm] when the user confirms. The
 * sheet is shown whenever [option] is non-null.
 */
@Composable
fun CounterDialog(
    option: CounterOption?,
    value: Int,
    language: Language,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val strings = Localization.strings
    // Draft value edited inside the dialog; reset whenever a new option opens.
    var draft by remember(option, value) { mutableStateOf(value) }

    BottomDialog(
        visible = option != null,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        if (option == null) return@BottomDialog

        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(option.title.resolve(language)) }
            P(attrs = { classes(Styles.dialogService) }) {
                Text("${option.unitLabel.resolve(language)} · ${option.unitPrice.format(language)}")
            }
        }

        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.counter) }) {
                Button(attrs = {
                    classes(Styles.counterBtn)
                    if (draft <= option.min) attr("disabled", "")
                    attr("aria-label", "-")
                    onClick { if (draft > option.min) draft -= 1 }
                }) { Text("-") }
                Input(type = InputType.Number) {
                    classes(Styles.counterInput)
                    value(draft.toString())
                    attr("min", option.min.toString())
                    attr("max", option.max.toString())
                    attr("inputmode", "numeric")
                    onInput {
                        val typed = it.value?.toInt()
                        if (typed != null) draft = typed.coerceIn(option.min, option.max)
                    }
                }
                Button(attrs = {
                    classes(Styles.counterBtn)
                    if (draft >= option.max) attr("disabled", "")
                    attr("aria-label", "+")
                    onClick { if (draft < option.max) draft += 1 }
                }) { Text("+") }
            }
        }

        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                onClick { onDismiss() }
            }) { Text(strings.close) }

            PrimaryButton(
                label = strings.confirm,
                icon = "check_circle",
                enabled = true,
            ) {
                onConfirm(draft.coerceIn(option.min, option.max))
            }
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

/**
 * A single non-clickable info card carrying an [icon] (a Material Symbols
 * ligature name) on the left and a line of [text]. Mirrors the look of
 * [ReviewList]'s cards (surface-variant background, rounded corners) but
 * conveys static information rather than a tappable action.
 */
@Composable
fun InfoCard(icon: String, text: String) {
    Div(attrs = { classes(Styles.infoCard) }) {
        MaterialSymbol(icon, Styles.infoIcon)
        P(attrs = { classes(Styles.infoText) }) { Text(text) }
    }
}

/**
 * Renders a vertical list of non-clickable [InfoCard]s from [items], each a
 * pair of a Material Symbols icon name and its text.
 */
@Composable
fun InfoList(items: List<Pair<String, String>>) {
    Div(attrs = { classes(Styles.infoList) }) {
        items.forEach { (icon, text) ->
            InfoCard(icon, text)
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
 *
 * Open/close animation is driven by CSS transitions on the scrim/sheet. Callers
 * should keep this composable mounted and toggle [visible] — do not wrap it in
 * `if (visible)` or the enter transition will be skipped. This component also
 * self-corrects the first-frame case (mounted already-open) by painting closed
 * for one frame before applying the open class, so every future sheet animates.
 *
 * @param expanded when true, the sheet opens at max height and scrolls
 *   internally. Use for panels whose content starts short (loading) and later
 *   grows, so the sheet size stays stable.
 * @param footer optional pinned action row rendered below the scrollable
 *   content so Close/Save stay visible on tall sheets.
 */
@Composable
fun BottomDialog(
    visible: Boolean,
    closeLabel: String,
    onDismiss: () -> Unit,
    expanded: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Drag-to-dismiss state for the bottom sheet handle.
    // `dragStart` is the pointer Y where the gesture began (null when idle),
    // `dragY` is how far the sheet is currently pulled down (>= 0).
    var dragStart by remember { mutableStateOf<Double?>(null) }
    var dragY by remember { mutableStateOf(0.0) }
    // Distance the sheet must be pulled down before it dismisses on release.
    val dismissThreshold = 120.0

    // `rendered` keeps the dialog in the DOM through the exit animation.
    // `open` is the CSS-visible flag — always starts false on enter so the
    // browser paints translateY(100%) before transitioning to 0.
    var rendered by remember { mutableStateOf(visible) }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            rendered = true
            open = false
            dragStart = null
            dragY = 0.0
            // One frame is enough for the closed styles to apply before open.
            delay(16)
            open = true
        } else if (rendered) {
            open = false
            dragStart = null
            dragY = 0.0
            // Match Styles.dialogSheet transition (~300ms).
            delay(320)
            rendered = false
        }
    }

    if (!rendered) return

    // Scrim covers the frame; clicking it dismisses the sheet.
    Div(attrs = {
        classes(Styles.dialogScrim)
        if (open) classes(Styles.dialogScrimVisible)
        attr("role", "presentation")
        onClick { onDismiss() }
    }) {
        Div(attrs = {
            classes(Styles.dialogSheet)
            if (expanded) classes(Styles.dialogSheetExpanded)
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

            // Scrollable content region; the grip handle above stays fixed.
            Div(attrs = {
                classes(Styles.dialogContent)
                if (footer != null) classes(Styles.dialogContentWithFooter)
            }) {
                content()
            }
            if (footer != null) {
                Div(attrs = { classes(Styles.dialogFooter) }) {
                    footer()
                }
            }
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
    currentServiceId: String? = null,
    onDismiss: () -> Unit,
    onServiceSelected: (String) -> Unit = {},
    onCopyLink: () -> Unit = {},
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss
    ) {
        // Copy-link action, anchored to the top-right of the sheet.
        Div(attrs = {
            style {
                property("display", "flex")
                property("justify-content", "flex-end")
            }
        }) {
            Button(attrs = {
                classes(Styles.toolbarBtn)
                attr("type", "button")
                attr("aria-label", strings.copyLink)
                attr("title", strings.copyLink)
                onClick { onCopyLink() }
            }) {
                MaterialSymbol("content_copy")
                Text(strings.copyLink)
            }
        }

        ProviderDetailsContent(
            provider = provider,
            language = language,
            strings = strings,
            servicesLabel = "${strings.providerServicesLabel} ${provider.name}",
            excludeServiceId = currentServiceId,
            onServiceSelected = onServiceSelected,
        )

        // Close actions
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                onClick { onDismiss() }
            }) { Text(strings.close) }
        }
    }
}

/**
 * Pretty skill grid for a provider profile: emoji badge, skill name, and a
 * 1–5 level meter. Sorted highest-level first so strongest skills lead.
 */
@Composable
fun ProviderSkillsSection(
    skills: List<ProviderSkill>,
    language: Language,
    skillsLabel: String,
    levelLabel: String,
) {
    if (skills.isEmpty()) return

    val ordered = skills.sortedByDescending { it.level }

    Div(attrs = { classes(Styles.skillSection) }) {
        Span(attrs = {
            style {
                property("font-size", "12px")
                property("color", "var(--md-sys-color-on-surface-variant)")
                property("font-weight", "700")
                property("text-transform", "uppercase")
            }
        }) {
            Text(skillsLabel)
        }

        Div(attrs = { classes(Styles.skillGrid) }) {
            ordered.forEach { skill ->
                Div(attrs = {
                    classes(Styles.skillCard)
                    attr("title", "${skill.name.resolve(language)} · $levelLabel ${skill.level}")
                }) {
                    Div(attrs = { classes(Styles.skillEmoji) }) {
                        Text(skill.emoji)
                    }
                    Div(attrs = { classes(Styles.skillBody) }) {
                        P(attrs = { classes(Styles.skillName) }) {
                            Text(skill.name.resolve(language))
                        }
                        Div(attrs = {
                            classes(Styles.skillMeter)
                            attr("role", "img")
                            attr(
                                "aria-label",
                                "$levelLabel ${skill.level} / 5",
                            )
                        }) {
                            for (pip in 1..5) {
                                Span(attrs = {
                                    classes(Styles.skillPip)
                                    if (pip <= skill.level) classes(Styles.skillPipFilled)
                                }) { }
                            }
                            Span(attrs = { classes(Styles.skillLevelLabel) }) {
                                Text("$levelLabel ${skill.level}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared provider details body: avatar header, intro/about, skills, sex,
 * block/address, Zalo phone and a row of the provider's services. Reused by
 * both the [ProviderDetailsDialog] bottom sheet and the standalone provider
 * screen.
 *
 * [servicesLabel] is the heading shown above the services row; [excludeServiceId]
 * (when non-null) drops that service from the row so the dialog can show "other"
 * services while the screen shows them all.
 */
@Composable
fun ProviderDetailsContent(
    provider: ServiceProvider,
    language: Language,
    strings: apap.oneverandah.i18n.UiStrings,
    servicesLabel: String,
    excludeServiceId: String? = null,
    onServiceSelected: (String) -> Unit = {},
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
            // Intro / About
            val intro = provider.intro.resolve(language)
            if (intro.isNotBlank()) {
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
                        Text(strings.providerAboutLabel)
                    }
                    P(attrs = {
                        style {
                            property("font-size", "16px")
                            property("margin", "4px 0 0 0")
                        }
                    }) {
                        Text(intro)
                    }
                }
            }

            // Skills (primary profile highlight under About)
            if (provider.skills.isNotEmpty()) {
                ProviderSkillsSection(
                    skills = provider.skills,
                    language = language,
                    skillsLabel = strings.providerSkillsLabel,
                    levelLabel = strings.providerSkillLevelLabel,
                )
            }

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
                            property("text-decoration", "none")
                        }
                    }
                ) {
                    Text(provider.phone)
                }
            }

            // Services offered by this provider (optionally excluding one)
            val providerServices = Catalog.servicesByProvider(provider)
                .filter { excludeServiceId == null || it.id != excludeServiceId }
            if (providerServices.isNotEmpty()) {
                Span(attrs = {
                    style {
                        property("font-size", "12px")
                        property("color", "var(--md-sys-color-on-surface-variant)")
                        property("font-weight", "700")
                        property("text-transform", "uppercase")
                    }
                }) {
                    Text(servicesLabel)
                }
                Div(attrs = { classes(Styles.pinnedRow, Styles.pinnedRowWrap) }) {
                    providerServices.forEach { service ->
                        val name = service.title.resolve(language)
                        val cover = ImageStore.coverFor(service.id)
                        Div(attrs = {
                            classes(Styles.pinnedCard)
                            if (cover != null) classes(Styles.pinnedCardCovered)
                            attr("role", "button")
                            attr("title", name)
                            onClick { onServiceSelected(service.id) }
                        }) {
                            if (cover != null) CoverImage(cover)
                            Div(attrs = { classes(Styles.pinnedCardIcon) }) {
                                MaterialSymbol(service.icon)
                            }
                            Span(attrs = { classes(Styles.pinnedCardLabel) }) { Text(name) }
                        }
                    }
                }
            }
        }
}

/**
 * Horizontal toolbar shown on the offer page directly below the
 * "Provided by [Vendor]" line.
 *
 * Left: a Save/Bookmark toggle whose icon is an outlined bookmark when
 * [saved] is `false` and a filled bookmark when `true`.
 * Right: a Share button that opens the [ShareDialog].
 */
@Composable
fun OfferToolbar(
    saved: Boolean,
    saveLabel: String,
    shareLabel: String,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
) {
    Div(attrs = { classes(Styles.offerToolbar) }) {
        Button(attrs = {
            classes(Styles.toolbarBtn)
            if (saved) classes(Styles.toolbarBtnActive)
            attr("type", "button")
            attr("aria-pressed", saved.toString())
            onClick { onToggleSave() }
        }) {
            Span(attrs = {
                classes(Styles.materialSymbols)
                if (saved) style { property("font-variation-settings", "'FILL' 1") }
            }) { Text("bookmark") }
            Text(saveLabel)
        }
        Button(attrs = {
            classes(Styles.toolbarBtn)
            attr("type", "button")
            onClick { onShare() }
        }) {
            MaterialSymbol("ios_share")
            Text(shareLabel)
        }
    }
}

/**
 * Share bottom sheet: shows the service title, the full deep-link URL in a
 * disabled input, and a prominent "Copy Link" CTA. After copying, the CTA text
 * briefly changes to "Copied!".
 */
@Composable
fun ShareDialog(
    visible: Boolean,
    serviceTitle: String,
    link: String,
    strings: apap.oneverandah.i18n.UiStrings,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    var copied by remember(visible) { mutableStateOf(false) }

    // Briefly revert the CTA text back to "Copy Link" after a copy.
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.share) }
            P(attrs = { classes(Styles.dialogService) }) { Text(serviceTitle) }
        }

        Div(attrs = { classes(Styles.dialogBody) }) {
            Input(type = InputType.Text) {
                classes(Styles.textInput)
                value(link)
                attr("disabled", "")
                attr("readonly", "")
                attr("aria-label", strings.share)
            }
        }

        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick {
                    onCopy()
                    copied = true
                }
            }) {
                MaterialSymbol(if (copied) "check" else "content_copy")
                Text(if (copied) strings.copied else strings.copyLink)
            }
        }
    }
}
