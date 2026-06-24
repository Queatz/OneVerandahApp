package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialIcon
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Category dashboard (Category step of Category -> Services -> Offer).
 */
@Composable
fun HomeScreen(onCategorySelected: (String) -> Unit) {
    val strings = Localization.strings
    val language = Localization.language

    Scaffold(title = strings.appTitle, showBanner = true) {
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.homePrompt) }
        Div(attrs = { classes(Styles.grid) }) {
            Catalog.categories.forEach { category ->
                Div(attrs = {
                    classes(Styles.card)
                    attr("role", "button")
                    onClick { onCategorySelected(category.id) }
                }) {
                    MaterialIcon(category.icon)
                    Span(attrs = { classes(Styles.cardLabel) }) {
                        Text(category.title.resolve(language))
                    }
                }
            }
        }
    }
}
