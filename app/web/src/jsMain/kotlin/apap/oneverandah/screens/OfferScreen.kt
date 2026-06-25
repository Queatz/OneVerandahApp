package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.Styles
import apap.oneverandah.components.BodyMapSelector
import apap.oneverandah.components.Counter
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.OptionChip
import apap.oneverandah.components.BookingDialog
import apap.oneverandah.components.ProviderDetailsDialog
import org.jetbrains.compose.web.dom.Span
import apap.oneverandah.components.PriceSummaryView
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.ReviewList
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.components.SuccessOverlay
import apap.oneverandah.components.priceHint
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.model.BodyMapOption
import apap.oneverandah.model.BookingRequest
import apap.oneverandah.model.BookingSelection
import apap.oneverandah.model.CounterOption
import apap.oneverandah.model.MultiSelectOption
import apap.oneverandah.model.PricingCalculator
import apap.oneverandah.model.SingleSelectOption
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

private enum class BookingPhase { IDLE, SUBMITTING, SUCCESS }

/**
 * Booking detail / offer screen (Offer step of
 * Category -> Services -> Offer).
 *
 * Holds the user's [BookingSelection], renders each [apap.oneverandah.model.ServiceOption]
 * with its own UI, shows a live price summary + reviews, and builds an
 * API-ready [BookingRequest] on submit.
 */
@Composable
fun OfferScreen(
    serviceId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onDone: () -> Unit,
) {
    val strings = Localization.strings
    val language = Localization.language
    val service = Catalog.serviceById(serviceId)

    if (service == null) {
        Scaffold(onBack = onBack, onHome = onHome) {
            P { Text(strings.appTitle) }
        }
        return
    }

    var selection by remember(serviceId) { mutableStateOf(service.defaultSelection()) }
    var phase by remember(serviceId) { mutableStateOf(BookingPhase.IDLE) }
    var showBookingDialog by remember(serviceId) { mutableStateOf(false) }
    var showProviderDetails by remember(serviceId) { mutableStateOf(false) }
    var dialogZalo by remember(serviceId) { mutableStateOf<String?>(null) }
    var dialogMessage by remember(serviceId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val breakdown = PricingCalculator.breakdown(service, selection)

    Scaffold(
        onBack = onBack,
        onHome = onHome,
        showBanner = true,
        lockScroll = showBookingDialog || showProviderDetails || phase == BookingPhase.SUCCESS,
        footer = {
            PrimaryButton(
                label = if (phase == BookingPhase.SUBMITTING) strings.booking else strings.bookThisService,
                icon = "calendar_today",
                enabled = phase == BookingPhase.IDLE,
            ) {
                showBookingDialog = true
            }
        },
        overlay = {
            BookingDialog(
                visible = showBookingDialog,
                serviceTitle = service.title.resolve(language),
                breakdown = breakdown,
                totalLabel = strings.total,
                language = language,
                strings = strings,
                initialZalo = dialogZalo ?: selection.zalo,
                initialMessage = dialogMessage ?: selection.comment,
                onZaloChange = { dialogZalo = it.ifBlank { null } },
                onMessageChange = { dialogMessage = it.ifBlank { null } },
                onDismiss = { showBookingDialog = false },
                onConfirm = { zalo, msg ->
                    selection = selection.copy(zalo = zalo, comment = msg ?: selection.comment)
                    showBookingDialog = false
                    @Suppress("UNUSED_VARIABLE")
                    val request = BookingRequest(
                        serviceId = service.id,
                        selection = selection,
                        totalVnd = breakdown.total.amountVnd,
                    )
                    phase = BookingPhase.SUBMITTING
                    val messageDetails = buildString {
                        appendLine("Booking Details:")
                        breakdown.lines.forEach { line ->
                            appendLine("- ${line.label.resolve(language)}: ${line.amount.format(language)}")
                            appendLine()
                        }
                        appendLine("Total: ${breakdown.total.format(language)}")
                        if (!msg.isNullOrBlank()) {
                            appendLine()
                            appendLine("User Comment:")
                            appendLine()
                            appendLine(msg)
                        }
                    }
                    scope.launch {
                        try {
                            sendContactMessage(
                                name = zalo,
                                email = "zalo@oneverandah.app",
                                service = service.title.resolve(language),
                                message = messageDetails
                            )
                        } catch (t: Throwable) {
                            console.log("Failed to book service: $t")
                        } finally {
                            phase = BookingPhase.SUCCESS
                        }
                    }
                },
            )
            SuccessOverlay(
                visible = phase == BookingPhase.SUCCESS,
                title = strings.bookingSuccessTitle,
                message = strings.bookingSuccessMessage,
                doneLabel = strings.done,
                onDone = onDone,
            )
            ProviderDetailsDialog(
                visible = showProviderDetails,
                provider = service.provider,
                language = language,
                strings = strings,
                onDismiss = { showProviderDetails = false }
            )
        },
    ) {
        // Provider header
        Div(attrs = { classes(Styles.providerHeader) }) {
            Div(attrs = { classes(Styles.avatar) }) {
                MaterialSymbol(service.icon)
            }
            H2 { Text(service.title.resolve(language)) }
            P(attrs = { classes(Styles.rating) }) {
                Text("⭐ ${service.rating} ${strings.reviews(service.reviewCount)}")
            }
            P {
                Text(service.description.resolve(language))
            }
            // Provided by (photo) Name of Provider
            Div(attrs = {
                style {
                    property("display", "inline-flex")
                    property("align-items", "center")
                    property("justify-content", "center")
                    property("gap", "8px")
                    property("margin-top", "12px")
                    property("cursor", "pointer")
                }
                onClick {
                    showProviderDetails = true
                }
            }) {
                Span(attrs = {
                    style {
                        property("font-weight", "bold")
                    }
                }) {
                    Text("${strings.providedBy} ")
                }
                // Small photo/avatar of the provider
                Div(attrs = {
                    style {
                        property("width", "28px")
                        property("height", "28px")
                        property("border-radius", "50%")
                        property("background-color", "var(--md-sys-color-primary-container)")
                        property("display", "inline-flex")
                        property("align-items", "center")
                        property("justify-content", "center")
                        property("color", "var(--md-sys-color-primary)")
                    }
                }) {
                    Span(attrs = {
                        classes(Styles.materialSymbols)
                        style {
                            property("font-size", "18px")
                        }
                    }) {
                        Text(service.provider.photo)
                    }
                }
                Span(attrs = {
                    style {
                        property("font-weight", "bold")
                        property("text-decoration", "underline")
                        property("color", "var(--md-sys-color-primary)")
                    }
                }) {
                    Text(service.provider.name)
                }
            }
        }

        // Booking type
        SectionTitle(strings.sectionBookingType)
        Div(attrs = { classes(Styles.chipGroup) }) {
            service.bookingTypes.forEach { option ->
                OptionChip(
                    label = option.label.resolve(language),
                    priceHint = priceHint(option.priceDelta, language),
                    active = selection.bookingTypeId == option.id,
                    onClick = { selection = selection.copy(bookingTypeId = option.id) },
                )
            }
        }

        // Duration
        SectionTitle(strings.sectionDuration)
        Div(attrs = { classes(Styles.chipGroup) }) {
            service.durations.forEach { option ->
                OptionChip(
                    label = strings.minutes(option.minutes),
                    priceHint = option.price.format(language),
                    active = selection.durationId == option.id,
                    onClick = { selection = selection.copy(durationId = option.id) },
                )
            }
        }

        // Per-service options, each with its own UI
        service.options.forEach { option ->
            SectionTitle(option.title.resolve(language))
            when (option) {
                is SingleSelectOption -> {
                    Div(attrs = { classes(Styles.chipGroup) }) {
                        option.choices.forEach { choice ->
                            OptionChip(
                                label = choice.label.resolve(language),
                                priceHint = priceHint(choice.priceDelta, language),
                                active = selection.singleSelects[option.id] == choice.id,
                                onClick = {
                                    selection = selection.copy(
                                        singleSelects = selection.singleSelects + (option.id to choice.id),
                                    )
                                },
                            )
                        }
                    }
                }

                is MultiSelectOption -> {
                    val current = selection.multiSelects[option.id].orEmpty()
                    Div(attrs = { classes(Styles.chipGroup) }) {
                        option.choices.forEach { choice ->
                            val active = choice.id in current
                            OptionChip(
                                label = choice.label.resolve(language),
                                priceHint = priceHint(choice.priceDelta, language),
                                active = active,
                                onClick = {
                                    val next = if (active) current - choice.id else current + choice.id
                                    selection = selection.copy(
                                        multiSelects = selection.multiSelects + (option.id to next),
                                    )
                                },
                            )
                        }
                    }
                }

                is CounterOption -> {
                    Counter(
                        option = option,
                        value = selection.counters[option.id] ?: option.default,
                        language = language,
                        onChange = { value ->
                            selection = selection.copy(
                                counters = selection.counters + (option.id to value),
                            )
                        },
                    )
                }

                is BodyMapOption -> {
                    val current = selection.bodyParts[option.id].orEmpty()
                    BodyMapSelector(
                        option = option,
                        selected = current,
                        language = language,
                        hint = strings.tapToSelect,
                        onToggle = { partId ->
                            val isActive = partId in current
                            val next = when {
                                isActive -> current - partId
                                option.multiSelect -> current + partId
                                else -> setOf(partId)
                            }
                            selection = selection.copy(
                                bodyParts = selection.bodyParts + (option.id to next),
                            )
                        },
                    )
                }
            }
        }

        // Comments (optional)
        service.commentPrompt?.let { prompt ->
            SectionTitle(strings.sectionComments)
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) {
                    Text(prompt.label.resolve(language))
                }
                TextArea(value = selection.comment ?: "") {
                    classes(Styles.textArea)
                    placeholder(prompt.placeholder.resolve(language))
                    onInput { selection = selection.copy(comment = it.value.ifBlank { null }) }
                }
            }
        }

        // Reviews
        if (service.reviews.isNotEmpty()) {
            SectionTitle(strings.sectionReviews)
            ReviewList(service.reviews, language)
        }

        // Live price summary
        SectionTitle(strings.priceSummary)
        PriceSummaryView(breakdown = breakdown, totalLabel = strings.total, language = language)
    }
}
