package apap.oneverandah

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import apap.oneverandah.data.Catalog
import apap.oneverandah.state.ImageStore
import kotlinx.coroutines.delay
import apap.oneverandah.screens.HomeScreen
import apap.oneverandah.screens.OfferScreen
import apap.oneverandah.screens.ProviderScreen
import apap.oneverandah.screens.ServicesScreen
import apap.oneverandah.screens.AboutScreen
import apap.oneverandah.screens.AdminScreen
import apap.oneverandah.screens.HelpScreen
import apap.oneverandah.screens.ListServiceScreen
import apap.oneverandah.screens.ChatScreen
import apap.oneverandah.screens.ComingSoonStoriesScreen
import apap.oneverandah.screens.PhrasesScreen
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
    var pendingChatMessage by remember { mutableStateOf<String?>(null) }
    var pendingChatPanel by remember { mutableStateOf<ChatInitialPanel?>(null) }
    // Load generated cover images on launch and keep them fresh by polling the
    // backend. Failures are swallowed inside ImageStore so the UI always renders.
    LaunchedEffect(Unit) {
        while (true) {
            ImageStore.refresh()
            delay(60_000)
        }
    }

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
                    onServiceSelected = { id -> router.navigate("/service/$id") },
                )
            }
        }

        route("provider") {
            string { providerId ->
                ProviderScreen(
                    providerId = providerId,
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onServiceSelected = { id -> router.navigate("/service/$id") },
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

        route("list") {
            noMatch {
                ListServiceScreen(onBack = { router.navigate("/") })
            }
        }

        route("chat") {
            noMatch {
                ChatScreen(
                    initialMessage = pendingChatMessage,
                    onInitialMessageConsumed = { pendingChatMessage = null },
                     initialPanel = pendingChatPanel,
                    onInitialPanelConsumed = { pendingChatPanel = null },
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenPhrases = { router.navigate("/phrases") },
                    onOpenStories = { router.navigate("/stories") },
                )
            }
        }

        route("phrases") {
            noMatch {
                PhrasesScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                )
            }
        }

        route("stories") {
            noMatch {
                ComingSoonStoriesScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenPhrases = { router.navigate("/phrases") },
                )
            }
        }

        route("admin") {
            noMatch {
                AdminScreen(onBack = { router.navigate("/") })
            }
        }

        route("/") {
            HomeScreen(
                onCategorySelected = { categoryId -> router.navigate("/category/$categoryId") },
                onServiceSelected = { serviceId -> router.navigate("/service/$serviceId") },
                onChatRequested = { message ->
                    pendingChatMessage = message
                    pendingChatPanel = null
                    router.navigate("/chat")
                },
                onOpenStudy = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Study
                    router.navigate("/chat")
                },
                onOpenSaved = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Saved
                    router.navigate("/chat")
                },
                onOpenPhrases = { router.navigate("/phrases") },
                onOpenStories = { router.navigate("/stories") },
            )
        }

        noMatch {
            HomeScreen(
                onCategorySelected = { categoryId -> router.navigate("/category/$categoryId") },
                onServiceSelected = { serviceId -> router.navigate("/service/$serviceId") },
                onChatRequested = { message ->
                    pendingChatMessage = message
                    pendingChatPanel = null
                    router.navigate("/chat")
                },
                onOpenStudy = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Study
                    router.navigate("/chat")
                },
                onOpenSaved = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Saved
                    router.navigate("/chat")
                },
                onOpenPhrases = { router.navigate("/phrases") },
                onOpenStories = { router.navigate("/stories") },
            )
        }
    }
}

/** Optional panel to open immediately when entering chat from home shortcuts. */
enum class ChatInitialPanel {
    Study,
    Saved,
}
