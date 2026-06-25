package apap.oneverandah

import androidx.compose.runtime.Composable
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import apap.oneverandah.data.Catalog
import apap.oneverandah.screens.HomeScreen
import apap.oneverandah.screens.OfferScreen
import apap.oneverandah.screens.ServicesScreen
import apap.oneverandah.screens.AboutScreen
import apap.oneverandah.screens.HelpScreen
import org.jetbrains.compose.web.renderComposable

fun main() {
    Styles.install()
    renderComposable(rootElementId = "root") {
        App()
    }
}

/**
 * Root of the app. Implements the Category -> Services -> Offer flow with
 * client-side routing:
 *  - `/`                  -> categories (home)
 *  - `/category/{id}`     -> services in a category
 *  - `/service/{id}`      -> offer / booking details
 */
@Composable
fun App() {
    BrowserRouter("/") {
        val router = Router.current

        route("category") {
            string { categoryId ->
                ServicesScreen(
                    categoryId = categoryId,
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onServiceSelected = { serviceId -> router.navigate("/service/$serviceId") },
                )
            }
        }

        route("service") {
            string { serviceId ->
                OfferScreen(
                    serviceId = serviceId,
                    onBack = {
                        val categoryId = Catalog.serviceById(serviceId)?.categoryId
                        if (categoryId != null) router.navigate("/category/$categoryId") else router.navigate("/")
                    },
                    onHome = { router.navigate("/") },
                    onDone = { router.navigate("/") },
                )
            }
        }

        route("about") {
            noMatch {
                AboutScreen(onBack = { router.navigate("/") })
            }
        }

        route("help") {
            noMatch {
                HelpScreen(onBack = { router.navigate("/") })
            }
        }

        route("/") {
            HomeScreen(
                onCategorySelected = { categoryId -> router.navigate("/category/$categoryId") },
                onServiceSelected = { serviceId -> router.navigate("/service/$serviceId") },
            )
        }

        noMatch {
            HomeScreen(
                onCategorySelected = { categoryId -> router.navigate("/category/$categoryId") },
                onServiceSelected = { serviceId -> router.navigate("/service/$serviceId") },
            )
        }
    }
}
