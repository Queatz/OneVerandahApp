package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
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

    Scaffold(title = strings.appTitle, showBanner = true, scrollKey = "home") {
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.homePrompt) }
        Div(attrs = { classes(Styles.textField) }) {
            Input(type = InputType.Text) {
                classes(Styles.textInput)
                value(searchQuery)
                placeholder(strings.searchPlaceholder)
                onInput { searchQuery = it.value }
            }
        }

        if (searchQuery.isBlank()) {
            PinnedRecentSection(strings = strings, language = language, onServiceSelected = onServiceSelected)
            Catalog.superCategories.forEach { superCategory ->
                val categoriesInSuper = Catalog.categories.filter { it.superCategoryId == superCategory.id }
                if (categoriesInSuper.isNotEmpty()) {
                    H2(attrs = { classes(Styles.superCategoryTitle) }) {
                        Text(superCategory.title.resolve(language))
                    }
                    Div(attrs = { classes(Styles.grid) }) {
                        categoriesInSuper.forEach { category ->
                            Div(attrs = {
                                classes(Styles.card)
                                attr("role", "button")
                                onClick { onCategorySelected(category.id) }
                            }) {
                                MaterialSymbol(category.icon)
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
                    Div(attrs = {
                        classes(Styles.listCard)
                        attr("role", "button")
                        onClick { onServiceSelected(service.id) }
                    }) {
                        MaterialSymbol(service.icon, extraClass = Styles.listIcon)
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
) {
    val items = PinnedStore.items
    var showClearConfirm by remember { mutableStateOf(false) }

    Div(attrs = { classes(Styles.pinnedSection) }) {
        Div(attrs = { classes(Styles.pinnedHeader) }) {
            H2(attrs = { classes(Styles.pinnedTitle) }) { Text(strings.pinnedRecentTitle) }
            if (items.isNotEmpty()) {
                Button(attrs = {
                    classes(Styles.pinnedClear)
                    attr("type", "button")
                    onClick { showClearConfirm = true }
                }) { Text(strings.clearAll) }
            }
        }

        if (items.isEmpty()) {
            P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.pinnedEmpty) }
        } else {
            Div(attrs = { classes(Styles.pinnedRow) }) {
                items.forEach { item ->
                    val service = Catalog.serviceById(item.serviceId)
                    val name = service?.title?.resolve(language) ?: item.name
                    val image = service?.icon ?: item.image
                    Div(attrs = {
                        classes(Styles.pinnedCard)
                        attr("role", "button")
                        attr("title", name)
                        onClick { onServiceSelected(item.serviceId) }
                    }) {
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
    }

    BottomDialog(
        visible = showClearConfirm,
        closeLabel = strings.close,
        onDismiss = { showClearConfirm = false },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.clearAllConfirm) }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { showClearConfirm = false }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick {
                    PinnedStore.clearAll()
                    showClearConfirm = false
                }
            }) { Text(strings.confirm) }
        }
    }
}
