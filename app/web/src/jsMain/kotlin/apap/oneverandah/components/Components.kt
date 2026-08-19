package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.state.LayoutModeStore
import apap.oneverandah.state.Toaster
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.flagFor
import apap.oneverandah.i18n.stringsFor
import apap.oneverandah.model.Language
import app.softwork.routingcompose.Router
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event

/** Per-screen scroll positions, keyed by [Scaffold]'s `scrollKey`. */
private val scrollPositions = mutableMapOf<String, Double>()

/** Renders a Material Symbols ligature, e.g. `MaterialSymbol("spa")`. */
@Composable
fun MaterialSymbol(name: String, extraClass: String? = null) {
    Span(attrs = {
        classes(Styles.materialSymbols)
        if (extraClass != null) classes(extraClass)
    }) { Text(name) }
}

@Composable
fun Spacer() {
    Div(attrs = {
        classes(Styles.spacer)
    })
}

/**
 * A reusable, async-preloaded background image that fades in professionally
 * once the browser has fully decoded it.
 *
 * Drop it as the **first child** of any `position: relative` container (e.g. a
 * `cardCovered` / `pinnedCardCovered` card) to render [url] as a `cover`
 * background that sits at the very back (`z-index: 0`). The image is first
 * loaded off-DOM via a throwaway `<img>`; only after its `load` event does the
 * layer gain the `coverImageLoaded` class, which transitions its opacity from 0
 * to 1. This avoids the "pop-in" of a half-decoded image and causes no layout
 * shift. A missing/broken URL simply never fades in, leaving the container's
 * own background as a graceful fallback.
 *
 * @param url the fully-resolved image URL to preload and display.
 * @param extraClass an optional additional CSS class for container-specific
 *   tweaks (e.g. sizing or border-radius overrides).
 */
@Composable
fun CoverImage(url: String) {
    var loaded by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        loaded = false
        val preloader = document.createElement("img") as HTMLImageElement
        preloader.onload = { _: Event -> loaded = true }
        preloader.src = url
        // A cached image may already be decoded before onload is wired up.
        if (preloader.complete) loaded = true
    }
    Div(attrs = {
        classes(Styles.coverImage)
        if (loaded) classes(Styles.coverImageLoaded)
        style { property("background-image", "url('$url')") }
    }) {
        Div(attrs = { classes(Styles.coverImageVeiled) })
    }
}

/**
 * Top app bar.
 *
 * @param title optional title text (omitted on the booking screen, like the prototype).
 * @param onBack when provided, shows a back arrow that invokes it.
 * @param onMenu when provided (mobile layout), shows a hamburger that opens the drawer.
 * @param onLanguageClick opens the language selection dialog (replaces cycling toggle).
 */
@Composable
fun AppBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onLanguageClick: (() -> Unit)? = null,
    showChatAction: Boolean = true,
    showPhrasesAction: Boolean = true,
    appBarAction: (@Composable () -> Unit)? = null,
) {
    val strings = Localization.strings
    val router = Router.current
    Div(attrs = { classes(Styles.appBar) }) {
        when {
            onBack != null -> Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.back)
                onClick { onBack() }
            }) { Text("arrow_back") }
            onMenu != null -> Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.menu)
                onClick { onMenu() }
            }) { Text("menu") }
        }
        if (title != null) {
            H1(attrs = { classes(Styles.appBarTitle) }) { Text(title) }
        } else {
            // Keep trailing actions right-aligned when there is no title.
            Div(attrs = { classes(Styles.appBarTitle) })
        }
        appBarAction?.invoke()
        if (showPhrasesAction) {
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.challengesTitle)
                onClick { router.navigate("/challenges") }
            }) { Text("emoji_events") }
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.storiesTitle)
                onClick { router.navigate("/stories") }
            }) { Text("auto_stories") }
        }
        if (showChatAction) {
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.chatTitle)
                onClick { router.navigate("/chat") }
            }) { Text("chat_bubble") }
        }
        Button(attrs = {
            classes(Styles.langToggle)
            attr("aria-label", strings.menuLanguage)
            onClick { onLanguageClick?.invoke() }
        }) {
            Span(attrs = { classes(Styles.langFlag) }) { Text(flagFor(Localization.language)) }
            Text(strings.languageName)
        }
    }
}

/**
 * Shared navigation destinations + language list used by both the mobile
 * overlay drawer and the permanent desktop sidebar.
 */
@Composable
private fun NavMenuItems(
    onHome: (() -> Unit)? = null,
    onNavigate: () -> Unit = {},
) {
    val strings = Localization.strings
    val router = Router.current
    val preferDesktop = LayoutModeStore.preference == LayoutModeStore.Preference.DESKTOP
    // Only offer the layout switcher when the viewport is wide enough for desktop.
    val showLayoutToggle = LayoutModeStore.viewportWide

    DrawerItem("home", strings.menuHome) {
        onNavigate()
        if (onHome != null) onHome() else router.navigate("/")
    }
    DrawerItem("chat_bubble", strings.chatTitle) {
        onNavigate()
        router.navigate("/chat")
    }
    DrawerItem("auto_stories", strings.storiesTitle) {
        onNavigate()
        router.navigate("/stories")
    }
    DrawerItem("emoji_events", strings.challengesTitle) {
        onNavigate()
        router.navigate("/challenges")
    }
    DrawerItem("import_contacts", strings.phrasesTitle) {
        onNavigate()
        router.navigate("/phrases")
    }
    DrawerItem("info", strings.menuAbout) {
        onNavigate()
        router.navigate("/about")
    }
    DrawerItem("add_business", strings.helpListService) {
        onNavigate()
        router.navigate("/list")
    }
    DrawerItem("help_outline", strings.menuHelp) {
        onNavigate()
        router.navigate("/help")
    }
    if (showLayoutToggle) {
        DrawerItem(
            icon = if (preferDesktop) "smartphone" else "computer",
            label = if (preferDesktop) strings.layoutSwitchToMobile else strings.layoutSwitchToDesktop,
        ) {
            LayoutModeStore.toggle()
            // Keep the permanent sidebar open; close the mobile drawer after toggling.
            if (preferDesktop) onNavigate()
        }
    }

    Div(attrs = { classes(Styles.drawerSection) }) { Text(strings.menuLanguage) }
    LanguageOptions(onSelected = { /* stay open so user can compare */ })
}

/**
 * Slide-in navigation drawer with a few basic app options and a language
 * selector (with flags). Visibility is animated via CSS based on [open].
 * Used only in the mobile / phone-frame layout.
 */
@Composable
fun NavigationDrawer(
    open: Boolean,
    onClose: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings

    // Scrim behind the drawer; clicking it closes the drawer.
    Div(attrs = {
        classes(Styles.scrim)
        if (open) classes(Styles.scrimVisible)
        onClick { onClose() }
    })

    Div(attrs = {
        classes(Styles.drawer)
        if (open) classes(Styles.drawerOpen)
        attr("role", "menu")
    }) {
        Div(attrs = { classes(Styles.drawerHeader) }) {
            H1(attrs = { classes(Styles.drawerTitle) }) { Text(strings.menuTitle) }
            Span(attrs = {
                classes(Styles.materialSymbols, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.close)
                onClick { onClose() }
            }) { Text("close") }
        }
        NavMenuItems(onHome = onHome, onNavigate = onClose)
    }
}

/**
 * Permanent left-hand navigation rail used in desktop layout.
 */
@Composable
fun NavigationSidebar(
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    Div(attrs = {
        classes(Styles.sidebar)
        attr("role", "navigation")
        attr("aria-label", strings.menu)
    }) {
        // Title is already shown in the desktop top app bar — skip the duplicate header.
        NavMenuItems(onHome = onHome)
    }
}

/**
 * Bottom-sheet dialog listing every supported language with flags.
 * Used by the app-bar language button when there are too many languages
 * to cycle through.
 */
@Composable
fun LanguageDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val strings = Localization.strings
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.menuLanguage) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            LanguageOptions(onSelected = { onDismiss() })
        }
    }
}

/** Renders a flag + native name button for each [Language]. */
@Composable
private fun LanguageOptions(onSelected: (Language) -> Unit) {
    Language.entries.forEach { lang ->
        Button(attrs = {
            classes(Styles.langOption)
            if (lang == Localization.language) classes(Styles.langOptionActive)
            onClick {
                Localization.setLanguage(lang)
                onSelected(lang)
            }
        }) {
            Span(attrs = { classes(Styles.langFlag) }) { Text(flagFor(lang)) }
            Text(stringsFor(lang).languageName)
        }
    }
}

@Composable
private fun DrawerItem(icon: String, label: String, onClick: () -> Unit) {
    Button(attrs = {
        classes(Styles.drawerItem)
        onClick { onClick() }
    }) {
        MaterialSymbol(icon)
        Text(label)
    }
}

/**
 * Page scaffold: app chrome containing an app bar, an optional banner image
 * area, a scrollable content region and an optional sticky footer.
 *
 * On wide viewports with desktop preference, the shell is full-width with a
 * permanent left sidebar. Otherwise it uses the phone-frame mobile chrome
 * (including when the user forces mobile layout on desktop).
 */
@Composable
fun Scaffold(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    showChatAction: Boolean = true,
    showPhrasesAction: Boolean = true,
    appBarAction: (@Composable () -> Unit)? = null,
    showBanner: Boolean = false,
    bannerImageUrl: String? = null,
    footer: (ContentBuilder<HTMLElement>)? = null,
    overlay: (@Composable () -> Unit)? = null,
    lockScroll: Boolean = false,
    scrollKey: String? = null,
    content: ContentBuilder<HTMLDivElement>,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }

    // Read state fields directly so Compose tracks preference + viewport.
    val viewportWide = LayoutModeStore.viewportWide
    val layoutPreference = LayoutModeStore.preference
    val isDesktop = viewportWide && layoutPreference == LayoutModeStore.Preference.DESKTOP

    LaunchedEffect(Unit) {
        LayoutModeStore.ensureViewportListener()
    }
    LaunchedEffect(isDesktop) {
        if (isDesktop) drawerOpen = false
    }

    Div(attrs = {
        classes(Styles.appShell)
        if (isDesktop) classes(Styles.layoutDesktop) else classes(Styles.layoutMobile)
    }) {
        Div(attrs = { classes(Styles.appFrame) }) {
            AppBar(
                title = title,
                onBack = onBack,
                // Hamburger only in mobile layout; desktop keeps the rail open.
                onMenu = if (isDesktop) null else ({ drawerOpen = true }),
                onLanguageClick = { languageDialogOpen = true },
                showChatAction = showChatAction,
                showPhrasesAction = showPhrasesAction,
                appBarAction = appBarAction,
            )

            if (isDesktop) {
                Div(attrs = { classes(Styles.appBody) }) {
                    NavigationSidebar(onHome = onHome)
                    Div(attrs = { classes(Styles.appMain) }) {
                        ScaffoldMainColumn(
                            showBanner = showBanner,
                            bannerImageUrl = bannerImageUrl,
                            footer = footer,
                            lockScroll = lockScroll,
                            scrollKey = scrollKey,
                            content = content,
                        )
                    }
                }
            } else {
                ScaffoldMainColumn(
                    showBanner = showBanner,
                    bannerImageUrl = bannerImageUrl,
                    footer = footer,
                    lockScroll = lockScroll,
                    scrollKey = scrollKey,
                    content = content,
                )
                NavigationDrawer(
                    open = drawerOpen,
                    onClose = { drawerOpen = false },
                    onHome = onHome,
                )
            }

            // Overlays live inside the frame so they cover the app surface
            // (phone chrome or full desktop shell), not the page outside it.
            overlay?.invoke()
            ToastHost()
            LanguageDialog(
                visible = languageDialogOpen,
                onDismiss = { languageDialogOpen = false },
            )
        }
    }
}

/**
 * Banner + scrollable content + optional footer. Used as the full mobile body
 * and as the main column beside the desktop sidebar.
 */
@Composable
private fun ScaffoldMainColumn(
    showBanner: Boolean,
    bannerImageUrl: String?,
    footer: (ContentBuilder<HTMLElement>)?,
    lockScroll: Boolean,
    scrollKey: String?,
    content: ContentBuilder<HTMLDivElement>,
) {
    if (showBanner) {
        Div(attrs = {
            classes(Styles.banner)
            if (bannerImageUrl != null) {
                style { property("background-image", "url('$bannerImageUrl')") }
            }
        })
    }
    Div(attrs = {
        classes(Styles.content)
        if (showBanner) {
            classes(Styles.contentHasBanner)
        }
        ref { element ->
            // Prefer the app frame for CSS custom properties so app-bar radius
            // and banner dimming stay frame-scoped in both layouts.
            val host = generateSequence(element.parentElement) { it.parentElement }
                .filterIsInstance<HTMLElement>()
                .firstOrNull { it.classList.contains(Styles.appFrame) }
                ?: (element.parentElement as? HTMLElement)
            val apply = {
                val scrolled = element.scrollTop > 0
                if (scrolled) {
                    host?.style?.setProperty("--appbar-radius", "16px")
                } else {
                    host?.style?.setProperty("--appbar-radius", "28px")
                }
                if (showBanner) {
                    val fraction = (element.scrollTop / 168.0).coerceIn(0.0, 1.0)
                    host?.style?.setProperty("--banner-scroll", fraction.toString())
                }
                if (scrollKey != null) scrollPositions[scrollKey] = element.scrollTop
            }
            if (scrollKey != null) {
                element.scrollTop = scrollPositions[scrollKey] ?: 0.0
            }
            apply()
            val listener: (Event) -> Unit = { apply() }
            element.addEventListener("scroll", listener)
            onDispose {
                element.removeEventListener("scroll", listener)
                host?.style?.removeProperty("--appbar-radius")
                if (showBanner) {
                    host?.style?.removeProperty("--banner-scroll")
                }
            }
        }
        if (lockScroll) classes(Styles.contentLocked)
    }, content = content)
    if (footer != null) {
        Div(attrs = { classes(Styles.footer) }, content = footer)
    }
}

/** A labelled section header. */
@Composable
fun SectionTitle(text: String) {
    Div(attrs = { classes(Styles.sectionTitle) }) { Text(text) }
}

/**
 * Renders the currently active global toast (see [Toaster]) at the bottom of
 * the phone frame and auto-dismisses it after a short delay. The visibility is
 * driven by [Toaster.message] so the CSS fade transition plays.
 */
@Composable
fun ToastHost() {
    val message = Toaster.message
    val token = Toaster.token

    LaunchedEffect(token) {
        if (Toaster.message != null) {
            delay(2_200)
            Toaster.dismiss()
        }
    }

    Div(attrs = {
        classes(Styles.toast)
        if (message != null) classes(Styles.toastVisible)
        attr("role", "status")
        attr("aria-live", "polite")
    }) {
        if (message != null) Text(message)
    }
}

/** Gradient primary button with a leading material icon. */
@Composable
fun PrimaryButton(
    label: String,
    icon: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes(Styles.btnPrimary)
        if (!enabled) attr("disabled", "")
        onClick { if (enabled) onClick() }
    }) {
        MaterialSymbol(icon)
        Text(label)
    }
}

/**
 * Full-frame success overlay with a popping checkmark and fading-in text.
 * Visibility is driven by [visible] so the CSS transition/animation plays.
 */
@Composable
fun SuccessOverlay(
    visible: Boolean,
    title: String,
    message: String,
    doneLabel: String? = null,
    onDone: (() -> Unit)? = null,
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(2_000)
            onDone?.invoke()
        }
    }

    Div(attrs = {
        classes(Styles.successOverlay)
        if (visible) classes(Styles.successVisible)
        attr("role", "status")
    }) {
        Div(attrs = { classes(Styles.successCircle) }) {
            MaterialSymbol("check", extraClass = Styles.successCheck)
        }
        H1(attrs = { classes(Styles.successTitle) }) { Text(title) }
        P(attrs = { classes(Styles.successMessage) }) { Text(message) }
        if (doneLabel != null && onDone != null) {
            Div(attrs = { classes(Styles.footer) }) {
                PrimaryButton(label = doneLabel, icon = "check_circle", onClick = onDone)
            }
        }
    }
}
