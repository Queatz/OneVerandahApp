package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.components.ProviderDetailsContent
import apap.oneverandah.components.Scaffold
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Standalone provider screen (route `/provider/{id}`), mirroring the look of
 * [OfferScreen] (app bar + banner) but showing the same details as the
 * [apap.oneverandah.components.ProviderDetailsDialog].
 *
 * Unlike the dialog, this lists **all** services by the provider (the heading
 * reads "All services by ...") instead of only the "other" ones.
 *
 * The provider is identified by its unique Zalo phone number.
 */
@Composable
fun ProviderScreen(
    providerId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onServiceSelected: (String) -> Unit = {},
) {
    val strings = Localization.strings
    val language = Localization.language
    val provider = Catalog.providerById(providerId)

    if (provider == null) {
        Scaffold(title = strings.providerDetails, onBack = onBack, onHome = onHome) {
            P { Text(strings.appTitle) }
        }
        return
    }

    Scaffold(
        title = strings.providerDetails,
        onBack = onBack,
        onHome = onHome,
        showBanner = true,
    ) {
        ProviderDetailsContent(
            provider = provider,
            language = language,
            strings = strings,
            servicesLabel = "${strings.providerAllServicesLabel} ${provider.name}",
            excludeServiceId = null,
            onServiceSelected = onServiceSelected,
        )
    }
}
