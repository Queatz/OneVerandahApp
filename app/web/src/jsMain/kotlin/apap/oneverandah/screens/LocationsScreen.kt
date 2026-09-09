package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.LocationApi
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.ShareDialog
import apap.oneverandah.components.Spacer
import apap.oneverandah.i18n.Localization
import apap.oneverandah.locations.LocationCard
import apap.oneverandah.locations.LocationLogic
import apap.oneverandah.locations.LocationsFeedResponse
import apap.oneverandah.state.Toaster
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenLocation: (String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf(LocationsFeedResponse()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableStateOf(currentTimeMs()) }
    var shareCard by remember { mutableStateOf<LocationCard?>(null) }

    suspend fun refresh() {
        feed = LocationApi.fetchFeed()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = currentTimeMs()
        }
    }

    val visible = feed.locations
        .filter { category == null || it.category == category }
        .filter { LocationLogic.matchesQuery(it, query) }

    Scaffold(
        title = strings.locationsTitle,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "locations",
        lockScroll = shareCard != null,
        overlay = {
            val sharing = shareCard
            if (sharing != null) {
                ShareDialog(
                    visible = true,
                    serviceTitle = sharing.name.ifBlank { strings.locationsUntitled },
                    link = locationShareUrl(sharing.id),
                    strings = strings,
                    onCopy = {
                        val clipboard = window.navigator.asDynamic().clipboard
                        if (clipboard != null) {
                            runCatching { clipboard.writeText(locationShareUrl(sharing.id)) }
                        }
                    },
                    onDismiss = { shareCard = null },
                )
            }
        },
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.locationsPrompt)
        }

        Div(attrs = { classes(Styles.textField) }) {
            Input(type = InputType.Search) {
                classes(Styles.textInput)
                value(query)
                placeholder(strings.locationsSearchPlaceholder)
                onInput { query = it.value }
            }
        }

        Div(attrs = { classes(Styles.chipGroup) }) {
            Button(attrs = {
                classes(Styles.chip)
                if (category == null) classes(Styles.chipActive)
                attr("type", "button")
                onClick { category = null }
            }) { Text(strings.locationsAllCategories) }
            feed.categories.forEach { key ->
                Button(attrs = {
                    classes(Styles.chip)
                    if (category == key) classes(Styles.chipActive)
                    attr("type", "button")
                    onClick { category = key }
                }) { Text(key) }
            }
        }

        Spacer()

        when {
            loading -> P { Text(strings.locationsLoading) }
            visible.isEmpty() -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.locationsEmpty) }
            else -> visible.forEach { card ->
                LocationFeedCard(
                    card = card,
                    nowMs = nowMs,
                    onOpen = { onOpenLocation(card.id) },
                    onLike = {
                        scope.launch {
                            runCatching { LocationApi.toggleLike(card.id) }
                                .onSuccess { updated ->
                                    feed = feed.copy(
                                        locations = feed.locations.map { if (it.id == updated.id) updated else it },
                                    )
                                }
                                .onFailure { Toaster.show(it.message ?: strings.locationsLikeError) }
                        }
                    },
                    onShare = { shareCard = card },
                )
            }
        }
    }
}

@Composable
private fun LocationFeedCard(
    card: LocationCard,
    nowMs: Long,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    val strings = Localization.strings
    val snapshot = LocationLogic.hoursSnapshot(card.status, card.schedule, nowMs)
    val hours = locationHoursLabel(snapshot, strings)
    val photos = card.photoUrls.ifEmpty { emptyList() }

    Div(attrs = {
        classes(Styles.locationCard)
        attr("role", "button")
        onClick { onOpen() }
    }) {
        Div(attrs = { classes(Styles.locationCardMain) }) {
            Div(attrs = { classes(Styles.locationCardPhotos) }) {
                if (photos.isEmpty()) {
                    Div(attrs = { classes(Styles.locationCardPhotoFallback) }) {
                        MaterialSymbol("location_on")
                    }
                } else {
                    photos.forEach { url ->
                        Img(src = url, alt = card.name, attrs = { classes(Styles.locationCardPhoto) })
                    }
                }
                if (card.category.isNotBlank()) {
                    Span(attrs = { classes(Styles.locationCardCategory) }) { Text(card.category) }
                }
            }

            Div(attrs = { classes(Styles.locationCardBody) }) {
                H2(attrs = { classes(Styles.locationCardName) }) {
                    Text(card.name.ifBlank { strings.locationsUntitled })
                }
                if (card.type.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardType) }) { Text(card.type) }
                }
                Span(attrs = {
                    classes(Styles.locationStatus)
                    classes(locationStatusClass(snapshot.displayStatus))
                }) { Text(locationStatusLabel(snapshot.displayStatus, strings)) }
                if (hours != null) {
                    P(attrs = { classes(Styles.locationCardHours) }) { Text(hours) }
                }
                if (card.address.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardMeta) }) { Text(card.address) }
                }
                LocationHighlights(card.highlights)
                Div(attrs = {
                    classes(Styles.locationCardActions)
                    onClick { it.stopPropagation() }
                }) {
                    Button(attrs = {
                        classes(Styles.toolbarBtn)
                        if (card.liked) classes(Styles.toolbarBtnActive)
                        attr("type", "button")
                        onClick { onLike() }
                    }) {
                        Span(attrs = {
                            classes(Styles.materialSymbols)
                            if (card.liked) style { property("font-variation-settings", "'FILL' 1") }
                        }) { Text("favorite") }
                        Text("${card.likeCount}")
                    }
                    Button(attrs = {
                        classes(Styles.toolbarBtn)
                        attr("type", "button")
                        onClick { onShare() }
                    }) {
                        MaterialSymbol("ios_share")
                        Text(strings.share)
                    }
                }
            }
        }
    }
}

internal fun locationStatusClass(displayStatus: String): String = when (displayStatus) {
    LocationLogic.DISPLAY_OPEN -> Styles.locationStatusOpen
    LocationLogic.DISPLAY_CLOSED -> Styles.locationStatusClosed
    LocationLogic.DISPLAY_COMING_SOON -> Styles.locationStatusSoon
    else -> Styles.locationStatusPlanning
}

internal fun currentTimeMs(): Long = kotlin.js.Date.now().toLong()
