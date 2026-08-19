package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import apap.oneverandah.Styles
import apap.oneverandah.api.ImageApi
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.ChatInput
import apap.oneverandah.components.CoverImage
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.ProviderDetailsDialog
import apap.oneverandah.components.ProviderDetailsSheet
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.ServiceCard
import apap.oneverandah.components.providerProfileUrl
import apap.oneverandah.data.Catalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.model.Service
import apap.oneverandah.state.ImageStore
import apap.oneverandah.state.PinnedStore
import apap.oneverandah.state.Toaster
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** Time to sit still on a fully-typed placeholder before deleting it again. */
private const val PLACEHOLDER_HOLD_MS = 2_500L

/** Per-character delay while deleting the placeholder (fast). */
private const val PLACEHOLDER_DELETE_MS = 25L

/** Per-character delay while typing the placeholder (normal speed). */
private const val PLACEHOLDER_TYPE_MS = 90L

/**
 * How long to wait after the user stops typing before reporting the search
 * query to the backend, so a burst of keystrokes yields a single request.
 */
private const val SEARCH_DEBOUNCE_MS = 500L

/** How many services to reveal per infinite-scroll batch on the home feed. */
internal const val HOME_FEED_PAGE_SIZE = 20

/** Brief pause so batch loads feel intentional rather than instant. */
private const val HOME_FEED_LOAD_DELAY_MS = 280L

/** Browse mode for the blank (non-search) home body. */
private enum class HomeBrowseMode { SERVICES, CATEGORIES }

/**
 * Advances the feed's visible count by one page, never past [total].
 * Extracted for unit testing.
 */
internal fun advanceHomeFeedVisibleCount(
    visibleCount: Int,
    total: Int,
    pageSize: Int = HOME_FEED_PAGE_SIZE,
): Int = (visibleCount + pageSize).coerceAtMost(total.coerceAtLeast(0)).coerceAtLeast(0)

/**
 * Returns the next page of services for the home feed.
 * Extracted for unit testing.
 */
internal fun homeFeedPage(services: List<Service>, visibleCount: Int): List<Service> =
    services.take(visibleCount.coerceAtLeast(0))

/**
 * Home screen: services feed by default, with a toggle back to the category grid.
 */
@Composable
fun HomeScreen(
    onCategorySelected: (String) -> Unit,
    onServiceSelected: (String) -> Unit,
    onChatRequested: (String) -> Unit,
    onOpenStudy: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenPhrases: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenChallenges: () -> Unit = {},
) {
    val strings = Localization.strings
    val language = Localization.language
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var providerSheet by remember { mutableStateOf(ProviderDetailsSheet()) }
    var browseMode by remember { mutableStateOf(HomeBrowseMode.SERVICES) }
    var feedVisibleCount by remember { mutableStateOf(HOME_FEED_PAGE_SIZE) }
    var feedLoadingMore by remember { mutableStateOf(false) }
    var animatedPlaceholder by remember(language) { mutableStateOf(strings.searchPlaceholder) }

    // Debounce the search query and report it to the backend. Searching itself
    // still happens on the client; this only records what people look for.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        ImageApi.logSearch(searchQuery)
    }

    LaunchedEffect(language) {
        animatedPlaceholder = strings.searchPlaceholder
        delay(PLACEHOLDER_HOLD_MS)
        var lastName: String? = null
        while (true) {
            // Quickly delete whatever is currently shown, one character at a time.
            while (animatedPlaceholder.isNotEmpty()) {
                animatedPlaceholder = animatedPlaceholder.dropLast(1)
                delay(PLACEHOLDER_DELETE_MS)
            }

            // Pick a random category or service name, avoiding an immediate repeat.
            val names = Catalog.categories.map { it.title.resolve(language) } +
                    Catalog.services.map { it.title.resolve(language) }
            val nextName = names.filter { it != lastName }.randomOrNull()
                ?: names.randomOrNull()
                ?: strings.searchPlaceholder
            lastName = nextName

            // Type the new name out at a normal speed.
            for (length in 1..nextName.length) {
                animatedPlaceholder = nextName.substring(0, length)
                delay(PLACEHOLDER_TYPE_MS)
            }

            delay(PLACEHOLDER_HOLD_MS)
        }
    }

    Scaffold(
        title = strings.appTitle,
        showBanner = true,
        scrollKey = "home",
        lockScroll = showClearConfirm || providerSheet.visible,
        overlay = {
            ClearAllConfirmDialog(
                visible = showClearConfirm,
                strings = strings,
                onDismiss = { showClearConfirm = false },
                onConfirm = {
                    PinnedStore.clearAll()
                    showClearConfirm = false
                },
            )
            providerSheet.service?.let { service ->
                val provider = service.provider
                ProviderDetailsDialog(
                    visible = providerSheet.visible,
                    provider = provider,
                    language = language,
                    strings = strings,
                    currentServiceId = service.id,
                    onDismiss = { providerSheet = providerSheet.dismiss() },
                    onServiceSelected = { id ->
                        providerSheet = providerSheet.dismiss()
                        onServiceSelected(id)
                    },
                    onCopyLink = {
                        val providerLink = providerProfileUrl(window.location.origin, provider.id)
                        val clipboard = window.navigator.asDynamic().clipboard
                        if (clipboard != null) {
                            try {
                                clipboard.writeText(providerLink)
                            } catch (t: Throwable) {
                                console.log("Failed to copy link: $t")
                            }
                        }
                        Toaster.show(strings.copiedToast)
                    },
                )
            }
        },
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) { Text(strings.homePrompt) }
        Div(attrs = { classes(Styles.textField) }) {
            ChatInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                onSend = { value ->
                    value?.trim()?.takeIf { it.isNotEmpty() }?.let(onChatRequested)
                },
                placeholder = animatedPlaceholder,
                autoFocus = true,
            )
        }

        if (searchQuery.isBlank()) {
            Div(attrs = { classes(Styles.homeExploreActions) }) {
                Button(attrs = {
                    classes(Styles.homeExploreCard)
                    attr("type", "button")
                    onClick { onOpenStudy() }
                }) {
                    MaterialSymbol("menu_book")
                    Div {
                        Span(attrs = { classes(Styles.homeExploreTitle) }) { Text(strings.chatExploreStudy) }
                        Span(attrs = { classes(Styles.homeExploreSub) }) { Text(strings.chatExploreStudySub) }
                    }
                    MaterialSymbol("north_east")
                }
                Button(attrs = {
                    classes(Styles.homeExploreCard)
                    attr("type", "button")
                    onClick { onOpenSaved() }
                }) {
                    MaterialSymbol("bookmarks")
                    Div {
                        Span(attrs = { classes(Styles.homeExploreTitle) }) { Text(strings.chatExploreSaved) }
                        Span(attrs = { classes(Styles.homeExploreSub) }) { Text(strings.chatExploreSavedSub) }
                    }
                    MaterialSymbol("north_east")
                }
                Button(attrs = {
                    classes(Styles.homeExploreCard)
                    attr("type", "button")
                    onClick { onOpenPhrases() }
                }) {
                    MaterialSymbol("import_contacts")
                    Div {
                        Span(attrs = { classes(Styles.homeExploreTitle) }) { Text(strings.chatExplorePhrases) }
                        Span(attrs = { classes(Styles.homeExploreSub) }) { Text(strings.chatExplorePhrasesSub) }
                    }
                    MaterialSymbol("north_east")
                }
                Button(attrs = {
                    classes(Styles.homeExploreCard)
                    attr("type", "button")
                    onClick { onOpenStories() }
                }) {
                    MaterialSymbol("auto_stories")
                    Div {
                        Span(attrs = { classes(Styles.homeExploreTitle) }) { Text(strings.chatExploreStories) }
                        Span(attrs = { classes(Styles.homeExploreSub) }) { Text(strings.chatExploreStoriesSub) }
                    }
                    MaterialSymbol("north_east")
                }
                Button(attrs = {
                    classes(Styles.homeExploreCard)
                    attr("type", "button")
                    onClick { onOpenChallenges() }
                }) {
                    MaterialSymbol("emoji_events")
                    Div {
                        Span(attrs = { classes(Styles.homeExploreTitle) }) { Text(strings.chatExploreChallenges) }
                        Span(attrs = { classes(Styles.homeExploreSub) }) { Text(strings.chatExploreChallengesSub) }
                    }
                    MaterialSymbol("north_east")
                }
            }
        }

        if (searchQuery.isBlank()) {
            PinnedRecentSection(
                strings = strings,
                language = language,
                onServiceSelected = onServiceSelected,
                onClearAll = { showClearConfirm = true },
            )

            Div(attrs = {
                classes(Styles.homeBrowseToggle)
                attr("role", "tablist")
                attr("aria-label", strings.homeViewServices)
            }) {
                Button(attrs = {
                    classes(Styles.chip)
                    if (browseMode == HomeBrowseMode.SERVICES) classes(Styles.chipActive)
                    attr("type", "button")
                    attr("role", "tab")
                    attr("aria-selected", (browseMode == HomeBrowseMode.SERVICES).toString())
                    onClick { browseMode = HomeBrowseMode.SERVICES }
                }) { Text(strings.homeViewServices) }
                Button(attrs = {
                    classes(Styles.chip)
                    if (browseMode == HomeBrowseMode.CATEGORIES) classes(Styles.chipActive)
                    attr("type", "button")
                    attr("role", "tab")
                    attr("aria-selected", (browseMode == HomeBrowseMode.CATEGORIES).toString())
                    onClick { browseMode = HomeBrowseMode.CATEGORIES }
                }) { Text(strings.homeViewCategories) }
            }

            when (browseMode) {
                HomeBrowseMode.SERVICES -> {
                    val allServices = Catalog.services
                    val visibleServices = homeFeedPage(allServices, feedVisibleCount)
                    val hasMore = feedVisibleCount < allServices.size

                    // Keep the visible window valid if the catalog shrinks.
                    LaunchedEffect(allServices.size) {
                        if (feedVisibleCount > allServices.size) {
                            feedVisibleCount = allServices.size.coerceAtLeast(HOME_FEED_PAGE_SIZE)
                                .coerceAtMost(allServices.size)
                        }
                    }

                    Div(attrs = { classes(Styles.serviceFeed) }) {
                        visibleServices.forEach { service ->
                            ServiceCard(
                                service = service,
                                language = language,
                                strings = strings,
                                onBook = { onServiceSelected(service.id) },
                                onProviderClick = { providerSheet = providerSheet.open(service) },
                            )
                        }

                        if (hasMore) {
                            // Remount the sentinel after each page so IntersectionObserver
                            // re-fires when the end marker is still on-screen (short viewports).
                            key(feedVisibleCount) {
                                Div(attrs = {
                                    classes(Styles.serviceFeedSentinel)
                                    ref { element ->
                                        val root = findScrollRoot(element)
                                        val onIntersect: () -> Unit = {
                                            if (!feedLoadingMore && feedVisibleCount < allServices.size) {
                                                feedLoadingMore = true
                                            }
                                        }
                                        val observer = observeNearViewport(
                                            element = element,
                                            root = root,
                                            onIntersect = onIntersect,
                                        )
                                        onDispose { observer.disconnect() }
                                    }
                                })
                            }

                            if (feedLoadingMore) {
                                Div(attrs = { classes(Styles.serviceFeedLoading) }) {
                                    MaterialSymbol("progress_activity")
                                    Span { Text(strings.homeFeedLoadingMore) }
                                }
                                LaunchedEffect(feedVisibleCount, allServices.size) {
                                    delay(HOME_FEED_LOAD_DELAY_MS)
                                    feedVisibleCount = advanceHomeFeedVisibleCount(
                                        visibleCount = feedVisibleCount,
                                        total = allServices.size,
                                    )
                                    feedLoadingMore = false
                                }
                            }
                        }
                    }
                }

                HomeBrowseMode.CATEGORIES -> {
                    Catalog.superCategories.forEach { superCategory ->
                        val categoriesInSuper = Catalog.categories.filter { it.superCategoryId == superCategory.id }
                        if (categoriesInSuper.isNotEmpty()) {
                            H2(attrs = { classes(Styles.superCategoryTitle) }) {
                                Text(superCategory.title.resolve(language))
                            }
                            Div(attrs = { classes(Styles.grid) }) {
                                categoriesInSuper.forEach { category ->
                                    val cover = ImageStore.coverFor(category.id)
                                    Div(attrs = {
                                        classes(Styles.card)
                                        if (cover != null) classes(Styles.cardCovered)
                                        attr("role", "button")
                                        onClick { onCategorySelected(category.id) }
                                    }) {
                                        if (cover != null) CoverImage(cover)
                                        Div(attrs = { classes(Styles.pinnedCardIcon) }) {
                                            MaterialSymbol(category.icon)
                                        }
                                        Span(attrs = { classes(Styles.cardLabel) }) {
                                            Text(category.title.resolve(language))
                                        }
                                    }
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
                    val cover = ImageStore.coverFor(service.id)
                    Div(attrs = {
                        classes(Styles.listCard)
                        attr("role", "button")
                        onClick { onServiceSelected(service.id) }
                    }) {
                        if (cover != null) {
                            Div(attrs = {
                                classes(Styles.listCover)
                                style { property("background-image", "url('$cover')") }
                            })
                        } else {
                            MaterialSymbol(service.icon, extraClass = Styles.listIcon)
                        }
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
    onClearAll: () -> Unit,
) {
    val items = PinnedStore.items
    // When the user has no saved or recent services, fall back to a curated
    // "Popular" list (if the catalog has any services to show).
    val popular = if (items.isEmpty()) Catalog.popular else emptyList()
    val showPopular = items.isEmpty() && popular.isNotEmpty()

    Div(attrs = { classes(Styles.pinnedSection) }) {
        Div(attrs = { classes(Styles.pinnedHeader) }) {
            H2(attrs = { classes(Styles.pinnedTitle) }) {
                Text(if (showPopular) strings.popularTitle else strings.pinnedRecentTitle)
            }
            if (items.isNotEmpty()) {
                Button(attrs = {
                    classes(Styles.pinnedClear)
                    attr("type", "button")
                    onClick { onClearAll() }
                }) { Text(strings.clearAll) }
            }
        }

        when {
            items.isNotEmpty() -> {
                Div(attrs = { classes(Styles.pinnedRow) }) {
                    items.forEach { item ->
                        val service = Catalog.serviceById(item.serviceId)
                        val name = service?.title?.resolve(language) ?: item.name
                        val image = service?.icon ?: item.image
                        val cover = ImageStore.coverFor(item.serviceId)
                        Div(attrs = {
                            classes(Styles.pinnedCard)
                            if (cover != null) classes(Styles.pinnedCardCovered)
                            attr("role", "button")
                            attr("title", name)
                            onClick { onServiceSelected(item.serviceId) }
                        }) {
                            if (cover != null) CoverImage(cover)
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

            showPopular -> {
                Div(attrs = { classes(Styles.pinnedRow) }) {
                    popular.forEach { service ->
                        val name = service.title.resolve(language)
                        val cover = ImageStore.coverFor(service.id)
                        Div(attrs = {
                            classes(Styles.pinnedCard)
                            if (cover != null) classes(Styles.pinnedCardCovered)
                            attr("role", "button")
                            attr("title", name)
                            onClick { onServiceSelected(service.id) }
                        }) {
                            if (cover != null) CoverImage(cover)
                            Div(attrs = { classes(Styles.pinnedCardIcon) }) {
                                MaterialSymbol(service.icon)
                            }
                            Span(attrs = { classes(Styles.pinnedCardLabel) }) { Text(name) }
                        }
                    }
                }
            }

            else -> {
                P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.pinnedEmpty) }
            }
        }
    }
}

/**
 * Confirmation bottom sheet for the "Clear All" action. Rendered at the screen
 * level (via the scaffold overlay slot) so it stays pinned to the bottom of the
 * frame instead of scrolling with the page content.
 */
@Composable
private fun ClearAllConfirmDialog(
    visible: Boolean,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.clearAllConfirm) }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick { onConfirm() }
            }) { Text(strings.confirm) }
        }
    }
}

/** Walks up from [element] to find the scaffold scroll container (`.content`). */
private fun findScrollRoot(element: Element): Element? =
    generateSequence(element.parentElement) { it.parentElement }
        .filterIsInstance<HTMLElement>()
        .firstOrNull { it.classList.contains(Styles.content) }

/**
 * Observes [element] and invokes [onIntersect] when it enters (or nears) the
 * scroll root's visible area. Uses the browser IntersectionObserver so the
 * home feed can page in batches without a manual "Load more" control.
 */
private fun observeNearViewport(
    element: Element,
    root: Element?,
    onIntersect: () -> Unit,
): IntersectionObserverHandle {
    val options = js("({ rootMargin: '200px 0px', threshold: 0 })")
    options.root = root
    val callback: (Array<dynamic>) -> Unit = { entries ->
        if (entries.any { it.isIntersecting == true }) {
            onIntersect()
        }
    }
    // Browser constructors require `new`; a plain function call throws at runtime.
    val observer = JsIntersectionObserver(callback, options)
    observer.observe(element)
    return IntersectionObserverHandle(observer)
}

/** Thin wrapper so callers can disconnect without depending on typed DOM APIs. */
private class IntersectionObserverHandle(private val observer: JsIntersectionObserver) {
    fun disconnect() {
        try {
            observer.disconnect()
        } catch (_: Throwable) {
            // Best-effort cleanup; ignore if the browser already tore it down.
        }
    }
}

/** Kotlin/JS binding for the browser IntersectionObserver constructor (uses `new`). */
@JsName("IntersectionObserver")
private external class JsIntersectionObserver(
    callback: (Array<dynamic>) -> Unit,
    options: dynamic,
) {
    fun observe(target: Element)
    fun disconnect()
}
