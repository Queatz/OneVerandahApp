package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import apap.oneverandah.Styles
import apap.oneverandah.api.ImageApi
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.CoverImage
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.state.ImageStore
import apap.oneverandah.state.PinnedStore
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Time to sit still on a fully-typed placeholder before deleting it again. */
private const val PLACEHOLDER_HOLD_MS = 2_500L

/** Per-character delay while deleting the placeholder (fast). */
private const val PLACEHOLDER_DELETE_MS = 25L

/** Per-character delay while typing the placeholder (normal speed). */
private const val PLACEHOLDER_TYPE_MS = 90L

/**
 * How long to wait after the user stops typing before reporting the search
 * query to the backend, so a burst of keystrokes yields a single request.
 */
private const val SEARCH_DEBOUNCE_MS = 500L

/**
 * Category dashboard (Category step of Category -> Services -> Offer).
 */
@Composable
fun HomeScreen(
    onCategorySelected: (String) -> Unit,
    onServiceSelected: (String) -> Unit,
) {
    val strings = Localization.strings
    val language = Localization.language
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var animatedPlaceholder by remember(language) { mutableStateOf(strings.searchPlaceholder) }

    // Debounce the search query and report it to the backend. Searching itself
    // still happens on the client; this only records what people look for.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        ImageApi.logSearch(searchQuery)
    }

    LaunchedEffect(language) {
        animatedPlaceholder = strings.searchPlaceholder
        delay(PLACEHOLDER_HOLD_MS)
        var lastName: String? = null
        while (true) {
            // Quickly delete whatever is currently shown, one character at a time.
            while (animatedPlaceholder.isNotEmpty()) {
                animatedPlaceholder = animatedPlaceholder.dropLast(1)
                delay(PLACEHOLDER_DELETE_MS)
            }

            // Pick a random category or service name, avoiding an immediate repeat.
            val names = Catalog.categories.map { it.title.resolve(language) } +
                Catalog.services.map { it.title.resolve(language) }
            val nextName = names.filter { it != lastName }.randomOrNull()
                ?: names.randomOrNull()
                ?: strings.searchPlaceholder
            lastName = nextName

            // Type the new name out at a normal speed.
            for (length in 1..nextName.length) {
                animatedPlaceholder = nextName.substring(0, length)
                delay(PLACEHOLDER_TYPE_MS)
            }

            delay(PLACEHOLDER_HOLD_MS)
        }
    }

    Scaffold(
        title = strings.appTitle,
        showBanner = true,
        scrollKey = "home",
        lockScroll = showClearConfirm,
        overlay = {
            ClearAllConfirmDialog(
                visible = showClearConfirm,
                strings = strings,
                onDismiss = { showClearConfirm = false },
                onConfirm = {
                    PinnedStore.clearAll()
                    showClearConfirm = false
                },
            )
        },
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) { Text(strings.homePrompt) }
        Div(attrs = { classes(Styles.textField) }) {
            Input(type = InputType.Text) {
                classes(Styles.textInput)
                value(searchQuery)
                placeholder(animatedPlaceholder)
                onInput { searchQuery = it.value }
                ref { inputRef ->
                    inputRef.focus()

                    onDispose {  }
                }
            }
        }

        if (searchQuery.isBlank()) {
            PinnedRecentSection(
                strings = strings,
                language = language,
                onServiceSelected = onServiceSelected,
                onClearAll = { showClearConfirm = true },
            )
            Catalog.superCategories.forEach { superCategory ->
                val categoriesInSuper = Catalog.categories.filter { it.superCategoryId == superCategory.id }
                if (categoriesInSuper.isNotEmpty()) {
                    H2(attrs = { classes(Styles.superCategoryTitle) }) {
                        Text(superCategory.title.resolve(language))
                    }
                    Div(attrs = { classes(Styles.grid) }) {
                        categoriesInSuper.forEach { category ->
                            val cover = ImageStore.coverFor(category.id)
                            Div(attrs = {
                                classes(Styles.card)
                                if (cover != null) classes(Styles.cardCovered)
                                attr("role", "button")
                                onClick { onCategorySelected(category.id) }
                            }) {
                                if (cover != null) CoverImage(cover)
                                Div(attrs = { classes(Styles.pinnedCardIcon) }) {
                                    MaterialSymbol(category.icon)
                                }
                                Span(attrs = { classes(Styles.cardLabel) }) {
                                    Text(category.title.resolve(language))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val normalizedQuery = searchQuery.lowercase().filter { !it.isWhitespace() }
            val matchedServices = Catalog.services.filter { service ->
                val titleEn = service.title.en.lowercase().filter { !it.isWhitespace() }
                val titleVi = service.title.vi.lowercase().filter { !it.isWhitespace() }
                val titleZh = service.title.zh.lowercase().filter { !it.isWhitespace() }
                val descEn = service.description.en.lowercase().filter { !it.isWhitespace() }
                val descVi = service.description.vi.lowercase().filter { !it.isWhitespace() }
                val descZh = service.description.zh.lowercase().filter { !it.isWhitespace() }

                titleEn.contains(normalizedQuery) ||
                titleVi.contains(normalizedQuery) ||
                titleZh.contains(normalizedQuery) ||
                descEn.contains(normalizedQuery) ||
                descVi.contains(normalizedQuery) ||
                descZh.contains(normalizedQuery)
            }

            if (matchedServices.isEmpty()) {
                P { Text(strings.noResults) }
            } else {
                matchedServices.forEach { service ->
                    val cover = ImageStore.coverFor(service.id)
                    Div(attrs = {
                        classes(Styles.listCard)
                        attr("role", "button")
                        onClick { onServiceSelected(service.id) }
                    }) {
                        if (cover != null) {
                            Div(attrs = {
                                classes(Styles.listCover)
                                style { property("background-image", "url('$cover')") }
                            })
                        } else {
                            MaterialSymbol(service.icon, extraClass = Styles.listIcon)
                        }
                        Div(attrs = { classes(Styles.listDetails) }) {
                            H2 { Text(service.title.resolve(language)) }
                            P { Text(service.description.resolve(language)) }
                        }
                        Div(attrs = { classes(Styles.listPrice) }) {
                            Text(strings.from)
                            Span { Text(" ") }
                            Span { Text(service.basePrice.format(language)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The "Pinned & Recent" smart tray at the very top of the home screen.
 *
 * Shows manually-saved and recently-booked services (see
 * [apap.oneverandah.state.PinnedStore]) as a horizontally scrollable row, with a
 * "Clear All" action guarded by a confirmation bottom sheet and a friendly
 * empty-state placeholder.
 */
@Composable
private fun PinnedRecentSection(
    strings: UiStrings,
    language: apap.oneverandah.model.Language,
    onServiceSelected: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val items = PinnedStore.items
    // When the user has no saved or recent services, fall back to a curated
    // "Popular" list (if the catalog has any services to show).
    val popular = if (items.isEmpty()) Catalog.popular else emptyList()
    val showPopular = items.isEmpty() && popular.isNotEmpty()

    Div(attrs = { classes(Styles.pinnedSection) }) {
        Div(attrs = { classes(Styles.pinnedHeader) }) {
            H2(attrs = { classes(Styles.pinnedTitle) }) {
                Text(if (showPopular) strings.popularTitle else strings.pinnedRecentTitle)
            }
            if (items.isNotEmpty()) {
                Button(attrs = {
                    classes(Styles.pinnedClear)
                    attr("type", "button")
                    onClick { onClearAll() }
                }) { Text(strings.clearAll) }
            }
        }

        when {
            items.isNotEmpty() -> {
                Div(attrs = { classes(Styles.pinnedRow) }) {
                    items.forEach { item ->
                        val service = Catalog.serviceById(item.serviceId)
                        val name = service?.title?.resolve(language) ?: item.name
                        val image = service?.icon ?: item.image
                        val cover = ImageStore.coverFor(item.serviceId)
                        Div(attrs = {
                            classes(Styles.pinnedCard)
                            if (cover != null) classes(Styles.pinnedCardCovered)
                            attr("role", "button")
                            attr("title", name)
                            onClick { onServiceSelected(item.serviceId) }
                        }) {
                            if (cover != null) CoverImage(cover)
                            if (item.isManuallySaved) {
                                Span(attrs = {
                                    classes(Styles.materialSymbols, Styles.pinnedBadge)
                                    style { property("font-variation-settings", "'FILL' 1") }
                                }) { Text("bookmark") }
                            }
                            Div(attrs = { classes(Styles.pinnedCardIcon) }) {
                                MaterialSymbol(image)
                            }
                            Span(attrs = { classes(Styles.pinnedCardLabel) }) { Text(name) }
                        }
                    }
                }
            }

            showPopular -> {
                Div(attrs = { classes(Styles.pinnedRow) }) {
                    popular.forEach { service ->
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

            else -> {
                P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.pinnedEmpty) }
            }
        }
    }
}

/**
 * Confirmation bottom sheet for the "Clear All" action. Rendered at the screen
 * level (via the scaffold overlay slot) so it stays pinned to the bottom of the
 * frame instead of scrolling with the page content.
 */
@Composable
private fun ClearAllConfirmDialog(
    visible: Boolean,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.clearAllConfirm) }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick { onConfirm() }
            }) { Text(strings.confirm) }
        }
    }
}
