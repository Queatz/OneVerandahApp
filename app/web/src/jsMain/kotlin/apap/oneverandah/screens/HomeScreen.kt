package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
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

    Scaffold(title = strings.appTitle, showBanner = true) {
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
            Div(attrs = { classes(Styles.grid) }) {
                Catalog.categories.forEach { category ->
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
