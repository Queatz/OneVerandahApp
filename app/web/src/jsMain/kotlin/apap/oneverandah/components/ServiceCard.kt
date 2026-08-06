package apap.oneverandah.components

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.model.Language
import apap.oneverandah.model.Service
import apap.oneverandah.state.ImageStore
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Feed-style service card used on the Home screen.
 *
 * Layout mirrors a content post / the top of [apap.oneverandah.screens.OfferScreen]:
 * cover image on top, then name, price, provider, description and rating, with a
 * full-width "Book this service" action at the bottom that opens the offer page.
 */
@Composable
fun ServiceCard(
    service: Service,
    language: Language,
    strings: UiStrings,
    onBook: () -> Unit,
) {
    val cover = ImageStore.coverFor(service.id)

    Div(attrs = { classes(Styles.serviceCard) }) {
        Div(attrs = {
            classes(Styles.serviceCardCover)
            if (cover != null) classes(Styles.serviceCardCovered)
        }) {
            if (cover != null) {
                CoverImage(cover)
            } else {
                Div(attrs = { classes(Styles.serviceCardCoverFallback) }) {
                    MaterialSymbol(service.icon)
                }
            }
        }

        Div(attrs = { classes(Styles.serviceCardBody) }) {
            H2(attrs = { classes(Styles.serviceCardTitle) }) {
                Text(service.title.resolve(language))
            }
            P(attrs = { classes(Styles.serviceCardPrice) }) {
                Text(strings.from)
                Span { Text(" ") }
                Span { Text(service.basePrice.format(language)) }
            }
            Div(attrs = { classes(Styles.serviceCardProvider) }) {
                Span(attrs = { classes(Styles.serviceCardProviderLabel) }) {
                    Text("${strings.providedBy} ")
                }
                Div(attrs = { classes(Styles.avatarSmall) }) {
                    Span(attrs = {
                        classes(Styles.materialSymbols)
                        style { property("font-size", "18px") }
                    }) {
                        Text(service.provider.photo)
                    }
                }
                Span(attrs = { classes(Styles.serviceCardProviderName) }) {
                    Text(service.provider.name)
                }
            }
            P(attrs = { classes(Styles.serviceCardDescription) }) {
                Text(service.description.resolve(language))
            }
            P(attrs = { classes(Styles.serviceCardRating) }) {
                Text("⭐ ${service.rating} ${strings.reviews(service.reviewCount)}")
            }
            Div(attrs = { classes(Styles.serviceCardAction) }) {
                PrimaryButton(
                    label = strings.bookThisService,
                    icon = "calendar_today",
                    onClick = onBook,
                )
            }
        }
    }
}
