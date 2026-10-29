package apap.oneverandah

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.BrowserRouter
import app.softwork.routingcompose.Router
import apap.oneverandah.api.StatsApi
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.ImageStore
import kotlinx.browser.window
import kotlinx.coroutines.delay
import apap.oneverandah.screens.HomeScreen
import apap.oneverandah.screens.OfferScreen
import apap.oneverandah.screens.ProviderScreen
import apap.oneverandah.screens.ServicesScreen
import apap.oneverandah.screens.AdminScreen
import apap.oneverandah.screens.HelpScreen
import apap.oneverandah.screens.ListServiceScreen
import apap.oneverandah.screens.ChatScreen
import apap.oneverandah.screens.StudyScreen
import apap.oneverandah.screens.studySessionPath
import apap.oneverandah.screens.PhrasesScreen
import apap.oneverandah.screens.ChallengePlayerScreen
import apap.oneverandah.screens.ChallengesScreen
import apap.oneverandah.screens.ClubDetailScreen
import apap.oneverandah.screens.ClubsScreen
import apap.oneverandah.screens.LocationDetailScreen
import apap.oneverandah.screens.LocationsScreen
import apap.oneverandah.screens.MeetDetailScreen
import apap.oneverandah.screens.MeetScreen
import apap.oneverandah.screens.StoriesScreen
import apap.oneverandah.screens.StoryReaderScreen
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
 *  - `/study`             -> study sessions
 *  - `/study/{id}`        -> a specific shareable study session
 */
@Composable
fun App() {
    var pendingChatMessage by remember { mutableStateOf<String?>(null) }
    var pendingChatPanel by remember { mutableStateOf<ChatInitialPanel?>(null) }
    var pendingStudyText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        ImageStore.refresh()
    }
    LaunchedEffect(Unit) {
        runCatching {
            StatsApi.recordVisit(window.location.pathname, Localization.language.tag)
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
                    onOpenStudy = { text ->
                        pendingStudyText = text
                        router.navigate("/study")
                    },
                )
            }
        }

        route("study") {
            string { studyId ->
                StudyScreen(
                    sessionId = studyId,
                    initialText = pendingStudyText,
                    onInitialTextConsumed = { pendingStudyText = null },
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenSession = { id ->
                        val path = studySessionPath(id)
                        if (router.currentPath.path != path) router.navigate(path)
                    },
                    onCloseSession = { router.navigate("/study") },
                )
            }
            noMatch {
                StudyScreen(
                    initialText = pendingStudyText,
                    onInitialTextConsumed = { pendingStudyText = null },
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenSession = { id -> router.navigate(studySessionPath(id)) },
                    onCloseSession = { router.navigate("/study") },
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

        route("challenges") {
            string { challengeId ->
                ChallengePlayerScreen(
                    challengeId = challengeId,
                    onBack = { router.navigate("/challenges") },
                    onHome = { router.navigate("/") },
                )
            }
            noMatch {
                ChallengesScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenChallenge = { id -> router.navigate("/challenges/$id") },
                )
            }
        }

        route("locations") {
            string { locationId ->
                LocationDetailScreen(
                    locationId = locationId,
                    onBack = { router.navigate("/locations") },
                    onHome = { router.navigate("/") },
                )
            }
            noMatch {
                LocationsScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenLocation = { id -> router.navigate("/locations/$id") },
                )
            }
        }

        route("clubs") {
            string { clubId ->
                ClubDetailScreen(
                    clubId = clubId,
                    onBack = { router.navigate("/clubs") },
                    onHome = { router.navigate("/") },
                )
            }
            noMatch {
                ClubsScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenClub = { id -> router.navigate("/clubs/$id") },
                )
            }
        }

        route("meets") {
            string { meetId ->
                MeetDetailScreen(
                    meetId = meetId,
                    onBack = { router.navigate("/meets") },
                    onHome = { router.navigate("/") },
                )
            }
            noMatch {
                MeetScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenMeet = { id -> router.navigate("/meets/$id") },
                )
            }
        }

        route("stories") {
            string { storyId ->
                StoryReaderScreen(
                    storyId = storyId,
                    onBack = { router.navigate("/stories") },
                    onHome = { router.navigate("/") },
                )
            }
            noMatch {
                StoriesScreen(
                    onBack = { router.navigate("/") },
                    onHome = { router.navigate("/") },
                    onOpenStory = { id -> router.navigate("/stories/$id") },
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
                    pendingStudyText = null
                    router.navigate("/study")
                },
                onOpenStudySession = { id -> router.navigate(studySessionPath(id)) },
                onOpenSaved = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Saved
                    router.navigate("/chat")
                },
                onOpenPhrases = { router.navigate("/phrases") },
                onOpenStories = { router.navigate("/stories") },
                onOpenChallenges = { router.navigate("/challenges") },
                onOpenMeets = { router.navigate("/meets") },
                onOpenLocations = { router.navigate("/locations") },
                onOpenClubs = { router.navigate("/clubs") },
                onOpenStory = { id -> router.navigate("/stories/$id") },
                onOpenChallenge = { id -> router.navigate("/challenges/$id") },
                onOpenMeet = { id -> router.navigate("/meets/$id") },
                onOpenLocation = { id -> router.navigate("/locations/$id") },
                onOpenClub = { id -> router.navigate("/clubs/$id") },
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
                    pendingStudyText = null
                    router.navigate("/study")
                },
                onOpenStudySession = { id -> router.navigate(studySessionPath(id)) },
                onOpenSaved = {
                    pendingChatMessage = null
                    pendingChatPanel = ChatInitialPanel.Saved
                    router.navigate("/chat")
                },
                onOpenPhrases = { router.navigate("/phrases") },
                onOpenStories = { router.navigate("/stories") },
                onOpenChallenges = { router.navigate("/challenges") },
                onOpenMeets = { router.navigate("/meets") },
                onOpenLocations = { router.navigate("/locations") },
                onOpenClubs = { router.navigate("/clubs") },
                onOpenStory = { id -> router.navigate("/stories/$id") },
                onOpenChallenge = { id -> router.navigate("/challenges/$id") },
                onOpenMeet = { id -> router.navigate("/meets/$id") },
                onOpenLocation = { id -> router.navigate("/locations/$id") },
                onOpenClub = { id -> router.navigate("/clubs/$id") },
            )
        }
    }
}

/** Optional panel to open immediately when entering chat from home shortcuts. */
enum class ChatInitialPanel {
    Saved,
}
