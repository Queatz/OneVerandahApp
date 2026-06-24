package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.flagFor
import apap.oneverandah.i18n.stringsFor
import apap.oneverandah.model.Language
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/** Renders a Material Icons ligature, e.g. `MaterialIcon("spa")`. */
@Composable
fun MaterialIcon(name: String, extraClass: String? = null) {
    Span(attrs = {
        classes(Styles.materialIcons)
        if (extraClass != null) classes(extraClass)
    }) { Text(name) }
}

/**
 * Top app bar.
 *
 * @param title optional title text (omitted on the booking screen, like the prototype).
 * @param onBack when provided, shows a back arrow that invokes it; otherwise a menu icon.
 */
@Composable
fun AppBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.appBar) }) {
        if (onBack != null) {
            Span(attrs = {
                classes(Styles.materialIcons, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.back)
                onClick { onBack() }
            }) { Text("arrow_back") }
        } else {
            Span(attrs = {
                classes(Styles.materialIcons, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.menu)
                onClick { onMenu?.invoke() }
            }) { Text("menu") }
        }
        if (title != null) {
            H1(attrs = { classes(Styles.appBarTitle) }) { Text(title) }
        }
        Div(attrs = { classes(Styles.appBarSpacer) })
        Button(attrs = {
            classes(Styles.langToggle)
            attr("aria-label", strings.menuLanguage)
            onClick { Localization.toggleLanguage() }
        }) {
            Span(attrs = { classes(Styles.langFlag) }) { Text(flagFor(Localization.language)) }
            Text(strings.languageName)
        }
    }
}

/**
 * Slide-in navigation drawer with a few basic app options and a language
 * selector (with flags). Visibility is animated via CSS based on [open].
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
                classes(Styles.materialIcons, Styles.appBarIcon)
                attr("role", "button")
                attr("aria-label", strings.close)
                onClick { onClose() }
            }) { Text("close") }
        }

        DrawerItem("home", strings.menuHome) {
            onClose()
            onHome?.invoke()
        }
        DrawerItem("info", strings.menuAbout) { onClose() }
        DrawerItem("help_outline", strings.menuHelp) { onClose() }

        Div(attrs = { classes(Styles.drawerSection) }) { Text(strings.menuLanguage) }
        Language.entries.forEach { lang ->
            Button(attrs = {
                classes(Styles.langOption)
                if (lang == Localization.language) classes(Styles.langOptionActive)
                onClick { Localization.setLanguage(lang) }
            }) {
                Span(attrs = { classes(Styles.langFlag) }) { Text(flagFor(lang)) }
                Text(stringsFor(lang).languageName)
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: String, label: String, onClick: () -> Unit) {
    Button(attrs = {
        classes(Styles.drawerItem)
        onClick { onClick() }
    }) {
        MaterialIcon(icon)
        Text(label)
    }
}

/**
 * Page scaffold: phone-like frame containing an app bar, an optional banner
 * image area, a scrollable content region and an optional sticky footer.
 */
@Composable
fun Scaffold(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    showBanner: Boolean = false,
    bannerImageUrl: String? = null,
    footer: (ContentBuilder<HTMLElement>)? = null,
    overlay: (@Composable () -> Unit)? = null,
    lockScroll: Boolean = false,
    content: ContentBuilder<HTMLDivElement>,
) {
    var drawerOpen by remember { mutableStateOf(false) }
    Div(attrs = { classes(Styles.appShell) }) {
        Div(attrs = { classes(Styles.appFrame) }) {
            AppBar(title = title, onBack = onBack, onMenu = { drawerOpen = true })
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
                    // Drive the banner dimming veil from the scroll position:
                    // 0 at rest, ramping to 1 once the content has scrolled up
                    // to the top bar (~168px, the banner's perceived height).
                    ref { element ->
                        val frame = element.parentElement as? HTMLElement
                        val apply = {
                            val fraction = (element.scrollTop / 168.0).coerceIn(0.0, 1.0)
                            frame?.style?.setProperty("--banner-scroll", fraction.toString())
                        }
                        apply()
                        val listener: (Event) -> Unit = { apply() }
                        element.addEventListener("scroll", listener)
                        onDispose {
                            element.removeEventListener("scroll", listener)
                            frame?.style?.removeProperty("--banner-scroll")
                        }
                    }
                }
                if (lockScroll) classes(Styles.contentLocked)
            }, content = content)
            if (footer != null) {
                Div(attrs = { classes(Styles.footer) }, content = footer)
            }
            // Overlays (e.g. success animation) live inside the frame so they
            // cover only the phone surface, not the whole desktop viewport.
            overlay?.invoke()
            NavigationDrawer(
                open = drawerOpen,
                onClose = { drawerOpen = false },
                onHome = onHome,
            )
        }
    }
}

/** A labelled section header. */
@Composable
fun SectionTitle(text: String) {
    Div(attrs = { classes(Styles.sectionTitle) }) { Text(text) }
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
        MaterialIcon(icon)
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
    Div(attrs = {
        classes(Styles.successOverlay)
        if (visible) classes(Styles.successVisible)
        attr("role", "status")
    }) {
        Div(attrs = { classes(Styles.successCircle) }) {
            MaterialIcon("check", extraClass = Styles.successCheck)
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
