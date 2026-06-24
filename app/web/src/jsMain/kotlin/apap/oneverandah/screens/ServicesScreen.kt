package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Services list for a given category (Services step of
 * Category -> Services -> Offer).
 */
@Composable
fun ServicesScreen(
    categoryId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onServiceSelected: (String) -> Unit,
) {
    val strings = Localization.strings
    val language = Localization.language
    val category = Catalog.categoryById(categoryId)
    val services = Catalog.servicesByCategory(categoryId)

    Scaffold(
        title = category?.title?.resolve(language) ?: strings.appTitle,
        onBack = onBack,
        onHome = onHome,
        showBanner = true,
    ) {
        services.forEach { service ->
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
