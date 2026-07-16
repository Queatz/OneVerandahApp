package apap.oneverandah

import kotlinx.browser.document
import org.w3c.dom.HTMLStyleElement

/**
 * Centralised styling for the app.
 *
 * Rather than fight the typed Compose HTML [org.jetbrains.compose.web.css.StyleSheet]
 * DSL (which makes CSS custom properties, `@media (prefers-color-scheme)`,
 * keyframes and `@import` awkward), we keep one authoritative stylesheet here as
 * raw CSS and inject it once into the document `<head>`.
 *
 * Class names are exposed as constants so the Composable code references them in
 * a type-safe-ish way ([Styles.card], [Styles.btnPrimary], ...).
 */
object Styles {
    // App chrome
    const val appShell = "app-shell"
    const val appFrame = "app-frame"
    const val appBar = "app-bar"
    const val appBarIcon = "app-bar-icon"
    const val appBarTitle = "app-bar-title"
    const val langToggle = "lang-toggle"

    // Banner
    const val banner = "banner"

    // Content
    const val content = "content"
    const val contentHasBanner = "content-has-banner"
    const val homePrompt = "home-prompt"
    const val homePromptLarge = "home-prompt-lg"

    // Home grid
    const val grid = "grid"
    const val card = "card"
    const val cardLabel = "card-label"
    // Generated cover images
    const val cardCovered = "card-covered"
    // Reusable async-preloaded, fade-in background image layer
    const val coverImage = "cover-image"
    const val coverImageLoaded = "cover-image-loaded"

    // Services list
    const val listCard = "list-card"
    const val listIcon = "list-icon"
    const val listCover = "list-cover"
    const val listDetails = "list-details"

    // Offer / booking details
    const val providerHeader = "provider-header"
    const val avatar = "avatar"
    const val avatarSmall = "avatar-small"
    const val rating = "rating"
    const val sectionTitle = "section-title"
    const val superCategoryTitle = "super-category-title"
    const val chipGroup = "chip-group"
    const val chip = "chip"
    const val chipActive = "active"
    const val bodyMap = "body-map"
    const val figure = "figure"
    const val hotspot = "hotspot"
    const val bodyMapLabel = "body-map-label"
    const val textField = "text-field"
    const val textArea = "text-area"
    const val textLabel = "text-label"

    // Footer / primary button
    const val footer = "footer"
    const val btnPrimary = "btn-primary"

    // Material icon helper
    const val materialSymbols = "material-symbols-outlined"

    // Success animation
    const val successOverlay = "success-overlay"
    const val successVisible = "visible"
    const val successCircle = "success-circle"
    const val successCheck = "success-check"
    const val successTitle = "success-title"
    const val successMessage = "success-message"

    // Language flag
    const val langFlag = "lang-flag"

    // Navigation drawer
    const val scrim = "scrim"
    const val scrimVisible = "visible"
    const val drawer = "drawer"
    const val drawerOpen = "open"
    const val drawerHeader = "drawer-header"
    const val drawerTitle = "drawer-title"
    const val drawerItem = "drawer-item"
    const val drawerItemActive = "active"
    const val drawerSection = "drawer-section"
    const val drawerNote = "drawer-note"
    const val langOption = "lang-option"
    const val langOptionActive = "active"

    // Service list "from" price
    const val listPrice = "list-price"

    // Counter / stepper
    const val counter = "counter"
    const val counterBtn = "counter-btn"
    const val counterValue = "counter-value"
    const val counterUnit = "counter-unit"
    const val counterInput = "counter-input"

    // Option price hint
    const val chipPrice = "chip-price"

    // SVG body map
    const val bodyMapSvg = "body-map-svg"
    const val bodyRegion = "body-region"
    const val bodyRegionActive = "active"
    const val bodyMapHint = "body-map-hint"
    const val bodyMapLegend = "body-map-legend"
    const val bodySilhouette = "body-silhouette"
    const val bodySilhouetteDetail = "body-silhouette-detail"
    const val bodyControlLeft = "body-control-left"
    const val bodyControlRight = "body-control-right"
    const val bodyControlBtn = "body-control-btn"
    const val bodyControlBtnActive = "body-control-btn-active"

    // Reviews
    const val reviewList = "review-list"
    const val reviewCard = "review-card"
    const val reviewHead = "review-head"
    const val reviewAuthor = "review-author"
    const val reviewStars = "review-stars"
    const val reviewBody = "review-body"

    // Info cards (non-clickable)
    const val infoList = "info-list"
    const val infoCard = "info-card"
    const val infoIcon = "info-icon"
    const val infoText = "info-text"

    // Price summary
    const val priceSummary = "price-summary"
    const val priceLine = "price-line"
    const val priceLineAmount = "price-line-amount"
    const val priceTotal = "price-total"

    // Booking dialog (modal bottom sheet)
    const val dialogScrim = "dialog-scrim"
    const val dialogScrimVisible = "visible"
    const val dialogSheet = "dialog-sheet"
    const val dialogSheetVisible = "visible"
    const val dialogGrip = "dialog-grip"
    const val dialogHandle = "dialog-handle"
    const val dialogContent = "dialog-content"
    const val dialogHeader = "dialog-header"
    const val dialogTitle = "dialog-title"
    const val dialogService = "dialog-service"
    const val dialogBody = "dialog-body"
    const val dialogActions = "dialog-actions"
    const val textInput = "text-input"
    const val textError = "text-error"
    const val btnText = "btn-text"
    const val contentLocked = "content-locked"

    // Offer toolbar (Save / Share)
    const val offerToolbar = "offer-toolbar"
    const val toolbarBtn = "toolbar-btn"
    const val toolbarBtnActive = "toolbar-btn-active"

    // Toast notification
    const val toast = "toast"
    const val toastVisible = "visible"

    // Home "Pinned & Recent" section
    const val pinnedSection = "pinned-section"
    const val pinnedHeader = "pinned-header"
    const val pinnedTitle = "pinned-title"
    const val pinnedClear = "pinned-clear"
    const val pinnedRow = "pinned-row"
    const val pinnedRowWrap = "pinned-row-wrap"
    const val pinnedCard = "pinned-card"
    const val pinnedCardCovered = "pinned-card-covered"
    const val pinnedCardIcon = "pinned-card-icon"
    const val pinnedCardLabel = "pinned-card-label"
    const val pinnedBadge = "pinned-badge"
    const val pinnedEmpty = "pinned-empty"

    /**
     * Injects [css] into the document head exactly once. Safe to call from
     * `main()` on every load (guarded by an id).
     */
    fun install() {
        if (document.getElementById("app-styles") != null) return
        val style = document.createElement("style") as HTMLStyleElement
        style.id = "app-styles"
        style.textContent = css
        document.head?.appendChild(style)
    }

    private val css = """
        :root {
            --md-sys-color-primary: #6750A4;
            --md-sys-color-primary-dark: #563f93;
            --md-sys-color-on-primary: #FFFFFF;
            --md-sys-color-primary-container: #EADDFF;
            --md-sys-color-on-primary-container: #21005D;
            --md-sys-color-surface: #FEF7FF;
            --md-sys-color-on-surface: #1D1B20;
            --md-sys-color-on-surface-variant: #49454F;
            --md-sys-color-surface-variant: #E7E0EC;
            --md-sys-color-outline: #79747E;
            --md-sys-color-error: #B3261E;
            --md-app-background: #f4f4f4;
            --md-banner-fade: #FEF7FF;
            --md-shadow: rgba(0, 0, 0, 0.15);
            --md-shadow-light: rgba(255, 255, 255, 0.6);
        }

        @media (prefers-color-scheme: dark) {
            :root {
                --md-sys-color-primary: #D0BCFF;
                --md-sys-color-primary-dark: #b69df8;
                --md-sys-color-on-primary: #381E72;
                --md-sys-color-primary-container: #4F378B;
                --md-sys-color-on-primary-container: #EADDFF;
                --md-sys-color-surface: #141218;
                --md-sys-color-on-surface: #E6E0E9;
                --md-sys-color-on-surface-variant: #CAC4D0;
                --md-sys-color-surface-variant: #2B2930;
                --md-sys-color-outline: #938F99;
                --md-sys-color-error: #F2B8B5;
                --md-app-background: #000000;
                --md-banner-fade: #141218;
                --md-shadow: rgba(0, 0, 0, 0.6);
                --md-shadow-light: rgba(255, 255, 255, 0.6);
            }
        }

        * { box-sizing: border-box; }

        html, body {
            margin: 0;
            padding: 0;
            height: 100%;
        }

        body {
            font-family: 'Quicksand', sans-serif;
            background-color: var(--md-app-background);
            color: var(--md-sys-color-on-surface);
        }

        #root { height: 100%; }
        
        button {
            user-select: none;
        }

        .${appShell} {
            display: flex;
            justify-content: center;
            align-items: stretch;
            min-height: 100vh;
            width: 100%;
        }

        /* Phone-like frame on desktop, full-bleed on small screens. */
        .${appFrame} {
            position: relative;
            display: flex;
            flex-direction: column;
            width: 100%;
            max-width: 480px;
            /* Lock the frame to the viewport height so .content is the only
               scroll container. This keeps overlays (scrim/bottom sheet)
               covering exactly the visible surface and lets us lock page
               scrolling while a modal is open. */
            height: 100vh;
            height: 100dvh;
            /* A flex item's default min-height is `auto`, which lets the
               column grow to its content's intrinsic height and overrides the
               fixed height above. That makes the whole frame (app bar
               included) taller than the viewport so the page itself scrolls.
               Pinning min-height to 0 keeps the frame locked to the viewport
               and leaves .content as the only scroll container. */
            min-height: 0;
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            overflow: hidden;
        }

        @media (min-width: 600px) {
            .${appShell} { align-items: center; padding: 32px 16px; }
            .${appFrame} {
                min-height: 0;
                max-height: calc(100vh - 64px);
                border-radius: 28px;
                box-shadow: 0 10px 30px var(--md-shadow);
            }
        }

        /* App bar */
        .${appBar} {
            flex: 0 0 auto;
            height: 64px;
            display: flex;
            align-items: center;
            padding: 0 16px;
            position: relative;
            z-index: 10;
            background-color: color-mix(in srgb, var(--md-sys-color-surface-variant) 85%, transparent);
            background-image: linear-gradient(to top, transparent 55%, #ffffff16);
            border-bottom: 1px solid #ffffff36;
            backdrop-filter: blur(3px);
            border-radius: 0 0 var(--appbar-radius, 28px) var(--appbar-radius, 28px);
            transition: border-radius 0.8s ease-out;
            user-select: none;
        }
        .${appBarIcon} {
            margin-right: 8px;
            cursor: pointer;
            color: var(--md-sys-color-on-surface);
            border-radius: 50%;
            padding: 8px;
            user-select: none;
            transition: background-color 0.2s, border-color 0.2s;
            border: 1px solid transparent;
        }
        .${appBarIcon}:hover {
            background-color: var(--md-sys-color-surface-variant);
            border: 1px solid #ffffff16;
            box-shadow: 0 -4px 8px -8px var(--md-shadow-light) inset;
         }
        .${appBarTitle} {
            font-size: 20px;
            font-weight: bold;
            margin: 0 8px;
            width: 0;
            flex: 1 1 auto;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .${langToggle} {
            border: 1px solid var(--md-sys-color-outline);
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            font-size: 13px;
            font-weight: 700;
            padding: 6px 12px;
            border-radius: 100px;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        .${langToggle}:hover { background-color: var(--md-sys-color-surface-variant); }

        /* Banner image area that fades to the surface colour at the top.
           It is positioned absolutely directly beneath the 64px app bar and
           sits behind .content, so that scrolling the content slides the
           opaque content surface up over the banner (covering it up to the
           perceived top-bar height). */
        .${banner} {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 240px;
            z-index: 0;
            background-color: var(--md-sys-color-primary-container);
            background-image: url(/banner.jpg);
            background-size: cover;
            background-position: 50% 100%;
        }
        .${banner}::after {
            content: "";
            position: absolute;
            inset: 0;
            /* Fades from the surface colour (white in light mode) at the top
               down into the image so the banner blends into the app bar. */
            background: linear-gradient(to bottom, transparent 55%, var(--md-banner-fade) 100%);
        }
        /* Dimming veil over the banner. Its opacity is driven by the
           --banner-scroll custom property (0 at rest, 1 once the content has
           scrolled up to the top bar), which is updated from a scroll listener
           on .content. The veil uses the surface colour (not pure black) so
           the banner dims dramatically toward the content background as it
           gets covered, rather than going darker than the background. */
        .${banner}::before {
            content: "";
            position: absolute;
            inset: 0;
            z-index: 1;
            background-color: var(--md-sys-color-surface);
            opacity: calc(var(--banner-scroll, 0) * 0.92);
            pointer-events: none;
            transition: opacity 0.08s linear;
        }

        /* Scrollable content region */
        .${content} {
            flex: 1 1 auto;
            /* Allow this flex child to shrink below its content size so it
               scrolls internally instead of forcing the frame to grow. */
            min-height: 0;
            overflow-y: auto;
            padding: 16px;
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
        .${content}::-webkit-scrollbar { display: none; }
        .${content}.${contentLocked} { overflow: hidden; }
        /* When a banner is shown, the content scrolls over it. The content
           sits above the banner and is pushed down so the banner is visible
           at rest; its opaque surface background (clipped to the content box,
           so the top padding stays transparent and reveals the banner)
           covers the banner as the user scrolls up to the top bar. */
        .${content}.${contentHasBanner} {
            position: relative;
            z-index: 1;
            padding-top: 168px;
            background-color: var(--md-sys-color-surface);
            background-clip: content-box;
        }

        .${homePrompt} {
            margin: 16px 0;
            color: var(--md-sys-color-on-surface-variant);
            font-weight: bold;
        }
        .${homePrompt}.${homePromptLarge} {
            font-size: 1.5em;
        }

        /* Home grid */
        .${grid} {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
            margin-top: 8px;
        }
        @media (min-width: 420px) {
            .${grid} { grid-template-columns: repeat(3, 1fr); }
        }
        .${card} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            padding: 20px 12px;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            cursor: pointer;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            transition: background-color 0.2s, transform 0.1s;
            user-select: none;
        }
        .${card}:hover { background-color: var(--md-sys-color-primary-container); }
        .${card}:active { transform: scale(0.97); }
        .${card} .${materialSymbols} {
            font-size: 32px;
            color: var(--md-sys-color-primary);
            margin-bottom: 8px;
        }
        .${cardLabel} { font-size: 14px; font-weight: 700; }

        /* Category card with a generated cover image. The cover is rendered by
           a reusable async-preloaded, fade-in layer (see .${coverImage}) so a
           missing/broken URL simply falls back to the base card colour (no
           broken-image glyph). */
        .${card}.${cardCovered} {
            position: relative;
            min-height: 116px;
            padding: 0;
            overflow: hidden;
            justify-content: flex-end;
            align-items: stretch;
        }
        .${card}.${cardCovered} .${materialSymbols} { display: none; }
        .${card}.${cardCovered} .${cardLabel} {
            position: relative;
            z-index: 1;
            width: 100%;
            padding: 22px 10px 10px;
            color: #ffffff;
            text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
            background: linear-gradient(to top, rgba(0, 0, 0, 0.6), rgba(0, 0, 0, 0));
        }

        /* Reusable async-preloaded, fade-in background image layer. Drop a
           CoverImage(url) as the first child of any position:relative container
           to get an image that is preloaded off-DOM and fades in only once the
           browser has fully decoded it (no pop-in, no layout shift). It sits at
           the very back (z-index: 0) so icons/labels/veils layer on top. */
        .${coverImage} {
            position: absolute;
            inset: 0;
            z-index: 0;
            background-size: cover;
            background-position: center;
            background-repeat: no-repeat;
            border-radius: inherit;
            opacity: 0;
            transition: opacity 0.45s ease;
            pointer-events: none;
        }
        .${coverImage}.${coverImageLoaded} { opacity: 1; }

        /* Services list */
        .${listCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            padding: 16px;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            cursor: pointer;
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            transition: background-color 0.2s, transform 0.1s;
            user-select: none;
        }
        .${listCard}:hover { background-color: var(--md-sys-color-primary-container); }
        .${listCard}:active { transform: scale(0.99); }
        .${listIcon} {
            font-size: 40px;
            color: var(--md-sys-color-primary);
            margin-right: 16px;
        }
        /* Thumbnail cover for a service row (falls back to the icon when the
           image has not been generated yet). */
        .${listCover} {
            width: 56px;
            height: 56px;
            border-radius: 16px;
            margin-right: 16px;
            flex: 0 0 auto;
            background-size: cover;
            background-position: center;
            background-color: var(--md-sys-color-surface-variant);
            border: 1px solid #ffffff16;
        }
        .${listDetails} h2 { font-size: 16px; margin: 0 0 4px 0; font-weight: 700; }
        .${listDetails} p { font-size: 14px; margin: 0; color: var(--md-sys-color-on-surface-variant); }

        /* Provider header */
        .${providerHeader} { text-align: center; margin-bottom: 24px; }
        .${avatar} {
            width: 80px;
            height: 80px;
            background-color: var(--md-sys-color-primary-container);
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 36px;
            color: var(--md-sys-color-primary);
            margin-bottom: 12px;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            user-select: none;
        }
        .${avatarSmall} {
            width: 28px;
            height: 28px;
            background-color: var(--md-sys-color-primary-container);
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            color: var(--md-sys-color-primary);
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            user-select: none;
        }
        .${avatar} .${materialSymbols} { font-size: 36px; }
        .${providerHeader} h2 { margin: 0 0 4px 0; font-size: 24px; font-weight: 400; }
        .${rating} { margin: 0; font-size: 14px; color: var(--md-sys-color-primary); font-weight: 700; }

        .${sectionTitle} { font-size: 16px; font-weight: 700; margin: 24px 0 12px 0; }
        /* Super category header rendered as a centered label flanked by
           horizontal rules on either side. */
        .${superCategoryTitle} {
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 16px;
            font-weight: 700;
            text-align: center;
            margin: 28px 0 12px 0;
            color: var(--md-sys-color-on-surface);
            user-select: none;
        }
        .${superCategoryTitle}::before,
        .${superCategoryTitle}::after {
            content: "";
            flex: 1 1 auto;
            height: 2px;
            border-radius: 2px;
            background-color: color-mix(in srgb, var(--md-sys-color-outline) 20%, transparent);
        }

        /* Chips */
        .${chipGroup} { display: flex; gap: 8px; flex-wrap: wrap; }
        .${chip} {
            padding: 8px 16px;
            border-radius: 16px;
            border: 1px solid var(--md-sys-color-outline);
            font-size: 14px;
            font-weight: 700;
            cursor: pointer;
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            transition: background-color 0.2s, border-color 0.2s;
            user-select: none;
        }
        .${chip}.${chipActive} {
            background-color: var(--md-sys-color-primary-container);
            border-color: transparent;
            color: var(--md-sys-color-on-primary-container);
        }

        /* Body map */
        .${bodyMap} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 16px;
            padding: 16px;
            text-align: center;
            position: relative;
            margin-bottom: 16px;
        }
        .${figure} {
            width: 100px;
            height: 180px;
            background: var(--md-sys-color-outline);
            margin: 0 auto;
            border-radius: 50px;
            position: relative;
            opacity: 0.6;
        }
        .${hotspot} {
            position: absolute;
            width: 24px;
            height: 24px;
            background-color: var(--md-sys-color-error);
            border-radius: 50%;
            top: 40%;
            left: 50%;
            transform: translate(-50%, -50%);
            border: 3px solid var(--md-sys-color-surface);
            box-shadow: 0 2px 4px var(--md-shadow);
        }
        .${bodyMapLabel} { margin: 12px 0 0 0; font-size: 14px; font-weight: 700; color: var(--md-sys-color-error); }

        /* Text field */
        .${textField} { position: relative; margin-bottom: 24px; margin-top: 8px; }
        .${textArea} {
            width: 100%;
            box-sizing: border-box;
            padding: 16px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 16px;
            background: transparent;
            font-family: inherit;
            font-size: 16px;
            resize: none;
            min-height: 100px;
            color: var(--md-sys-color-on-surface);
        }
        .${textArea}:focus { outline: 2px solid var(--md-sys-color-primary); border-color: transparent; }
        .${textLabel} {
            position: absolute;
            top: -8px;
            left: 12px;
            background: var(--md-sys-color-surface);
            padding: 0 4px;
            font-size: 12px;
            color: var(--md-sys-color-primary);
            border-radius: 4px;
            user-select: none;
        }
        .${textError} {
            color: var(--md-sys-color-error);
            font-size: 12px;
            margin-top: 4px;
            display: block;
        }

        /* Footer + primary button */
        .${footer} {
            flex: 0 0 auto;
            padding: 12px 16px 16px 16px;
            background: linear-gradient(to top, var(--md-sys-color-surface) 70%, transparent);
        }
        .${btnPrimary} {
            /* Slight gradient on the button */
            background: linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-primary-dark));
            color: var(--md-sys-color-on-primary);
            border: none;
            border-radius: 100px;
            padding: 16px 24px;
            font-size: 16px;
            font-weight: 700;
            font-family: inherit;
            width: 100%;
            cursor: pointer;
            box-shadow: 0 4px 12px var(--md-shadow), 0 4px 8px -8px var(--md-shadow-light) inset;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            border: 1px solid #ffffff16;
            transition: filter 0.2s, transform 0.1s;
        }
        .${btnPrimary}:hover { filter: brightness(1.07); }
        .${btnPrimary}:active { transform: scale(0.98); }
        .${btnPrimary}:disabled { filter: grayscale(0.3) brightness(0.7); cursor: default; pointer-events: none; }

        /* Success animation overlay */
        .${successOverlay} {
            position: absolute;
            inset: 0;
            background-color: var(--md-sys-color-surface);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            padding: 32px;
            gap: 12px;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
            z-index: 20;
        }
        .${successOverlay}.${successVisible} { opacity: 1; pointer-events: auto; }

        .${successCircle} {
            width: 96px;
            height: 96px;
            border-radius: 50%;
            background: linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-primary-dark));
            display: flex;
            align-items: center;
            justify-content: center;
            transform: scale(0);
            animation: pop 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28) forwards;
        }
        .${successOverlay}.${successVisible} .${successCircle} { animation: pop 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28) forwards; }
        .${successCheck} {
            color: var(--md-sys-color-on-primary);
            font-size: 56px;
            opacity: 0;
            animation: fadeInUp 0.3s ease 0.3s forwards;
        }
        .${successTitle} {
            font-size: 22px;
            font-weight: 700;
            margin: 8px 0 0 0;
            opacity: 0;
            animation: fadeInUp 0.3s ease 0.4s forwards;
        }
        .${successMessage} {
            font-size: 15px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 0;
            max-width: 280px;
            opacity: 0;
            animation: fadeInUp 0.3s ease 0.5s forwards;
        }

        @keyframes pop {
            0% { transform: scale(0); }
            100% { transform: scale(1); }
        }
        @keyframes fadeInUp {
            0% { opacity: 0; transform: translateY(8px); }
            100% { opacity: 1; transform: translateY(0); }
        }

        /* Language flag in the app-bar toggle */
        .${langToggle} { display: inline-flex; align-items: center; gap: 6px; }
        .${langFlag} { font-size: 16px; line-height: 1; }

        /* Navigation drawer */
        .${scrim} {
            position: absolute;
            inset: 0;
            background: rgba(0, 0, 0, 0.4);
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
            z-index: 30;
        }
        .${scrim}.${scrimVisible} { opacity: 1; pointer-events: auto; }
        .${drawer} {
            position: absolute;
            top: 0;
            left: 0;
            bottom: 0;
            width: 78%;
            max-width: 300px;
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            box-shadow: 2px 0 16px var(--md-shadow);
            transform: translateX(-105%);
            transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1);
            z-index: 31;
            display: flex;
            flex-direction: column;
            padding: 12px;
            overflow-y: auto;
            border-radius: 0 28px 28px 0;
        }
        .${drawer}.${drawerOpen} { transform: translateX(0); }
        .${drawerHeader} {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 8px 16px 8px;
        }
        .${drawerTitle} { font-size: 18px; font-weight: 700; margin: 0; }
        .${drawerItem} {
            display: flex;
            align-items: center;
            gap: 16px;
            padding: 12px;
            border-radius: 96px;
            cursor: pointer;
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            font-size: 15px;
            font-weight: 700;
            width: 100%;
            text-align: left;
            border: 1px solid transparent;
            transition: background-color 0.2s, border-color 0.2s;
        }
        .${drawerItem}:hover {
            background-color: var(--md-sys-color-surface-variant);
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
        }
        .${drawerItem}.${drawerItemActive} { background-color: var(--md-sys-color-primary-container); color: var(--md-sys-color-on-primary-container); }
        .${drawerItem} .${materialSymbols} { color: var(--md-sys-color-primary); font-size: 22px; }
        .${drawerSection} {
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 16px 12px 4px 12px;
        }
        .${drawerNote} {
            font-size: 12px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 12px;
        }
        .${langOption} {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px;
            border-radius: 96px;
            cursor: pointer;
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            font-size: 15px;
            font-weight: 700;
            width: 100%;
            text-align: left;
            border: 1px solid transparent;
            transition: background-color 0.2s, border 0.2s;
        }
        .${langOption}:hover {
            background-color: var(--md-sys-color-surface-variant);
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            }
        .${langOption}.${langOptionActive} {
            background-color: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
         }
        .${langOption} .${langFlag} { font-size: 22px; }

        /* "from" price in service list */
        .${listPrice} {
            margin-left: auto;
            padding-left: 12px;
            text-align: right;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
            white-space: nowrap;
        }
        .${listPrice} strong { display: block; font-size: 15px; color: var(--md-sys-color-primary); }

        /* Counter / stepper */
        .${counter} {
            display: flex;
            align-items: center;
            gap: 16px;
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 16px;
            padding: 10px 16px;
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            user-select: none;
        }
        .${counterBtn} {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: 1px solid var(--md-sys-color-outline);
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-size: 22px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: background-color 0.2s;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            user-select: none;
        }
        .${counterBtn}:hover:not(:disabled) { background-color: var(--md-sys-color-primary-container); }
        .${counterBtn}:disabled { opacity: 0.4; cursor: default; }
        .${counterValue} { font-size: 20px; font-weight: 700; min-width: 28px; text-align: center; cursor: pointer; }
        .${counterValue}[role="button"]:hover { color: var(--md-sys-color-primary); }
        .${counterUnit} { font-size: 14px; color: var(--md-sys-color-on-surface-variant); margin-left: auto; }
        .${counterInput} {
            flex: 1;
            min-width: 0;
            font-size: 24px;
            font-weight: 700;
            text-align: center;
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface);
            -moz-appearance: textfield;
        }
        .${counterInput}::-webkit-outer-spin-button,
        .${counterInput}::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
        .${counterInput}:focus { outline: none; }

        /* price hint inside chips */
        .${chipPrice} { font-weight: 400; opacity: 0.85; margin-left: 4px; font-size: 12px; }

        /* SVG body map */
        .${bodyMapSvg} {
            display: block;
            margin: 0 auto;
            width: 130px;
            height: auto;
            max-height: 230px;
        }
        .${bodyRegion} {
            fill: var(--md-sys-color-primary-dark);
            opacity: 0.45;
            cursor: pointer;
            transition: fill 0.15s, opacity 0.15s;
        }
        .${bodyRegion}:hover { opacity: 0.7; }
        .${bodyRegion}.${bodyRegionActive} { fill: var(--md-sys-color-primary); opacity: 1; }
        .${bodyMapHint} {
            margin: 12px 0 0 0;
            font-size: 13px;
            text-align: center;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${bodyMapLegend} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            justify-content: center;
            margin-top: 8px;
        }

        /* Silhouette and Controls */
        .${bodySilhouette} {
            fill: var(--md-sys-color-surface);
            stroke: var(--md-sys-color-outline);
            stroke-width: 2.5px;
            opacity: 0.85;
        }
        .${bodySilhouetteDetail} {
            stroke: var(--md-sys-color-outline);
            stroke-width: 1.5px;
            fill: none;
            opacity: 0.45;
        }
        .${bodyControlLeft} {
            position: absolute;
            top: 12px;
            left: 12px;
            display: flex;
            background-color: var(--md-sys-color-surface);
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 1px 3px var(--md-shadow);
        }
        .${bodyControlRight} {
            position: absolute;
            top: 12px;
            right: 12px;
            display: flex;
            background-color: var(--md-sys-color-surface);
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 50%;
            overflow: hidden;
            box-shadow: 0 1px 3px var(--md-shadow);
        }
        .${bodyControlBtn} {
            border: none;
            background: transparent;
            padding: 6px 12px;
            font-size: 13px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface-variant);
            cursor: pointer;
            transition: background-color 0.2s, color 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            border: 1px solid #ffffff16;
            user-select: none;
        }
        .${bodyControlBtn}:hover {
            background-color: var(--md-sys-color-surface-variant);
        }
        .${bodyControlBtn}.${bodyControlBtnActive} {
            background-color: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
        }
        .${bodyControlRight} .${bodyControlBtn} {
            padding: 8px;
            border-radius: 50%;
            width: 34px;
            height: 34px;
        }

        /* Reviews */
        .${reviewList} { display: flex; flex-direction: column; gap: 12px; }
        .${reviewCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 16px;
            padding: 12px 16px;
        }
        .${reviewHead} { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
        .${reviewAuthor} { font-weight: 700; font-size: 14px; }
        .${reviewStars} { font-size: 13px; color: #f5a623; letter-spacing: 1px; }
        .${reviewBody} { margin: 0; font-size: 14px; color: var(--md-sys-color-on-surface-variant); }

        /* Info cards (non-clickable) */
        .${infoList} { display: flex; flex-direction: column; gap: 16px; }
        .${infoCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            padding: 24px;
            display: flex;
            align-items: center;
            gap: 16px;
        }
        .${infoIcon} { font-size: 24px; color: var(--md-sys-color-primary); flex: 0 0 auto; }
        .${infoText} { margin: 0; font-size: 14px; color: var(--md-sys-color-on-surface-variant); }

        /* Price summary */
        .${priceSummary} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 16px;
            padding: 12px 16px;
            margin-top: 8px;
        }
        .${priceLine} {
            display: flex;
            justify-content: space-between;
            gap: 12px;
            font-size: 14px;
            color: var(--md-sys-color-on-surface-variant);
            padding: 3px 0;
        }
        .${priceLineAmount} { white-space: nowrap; }
        .${priceTotal} {
            display: flex;
            justify-content: space-between;
            gap: 12px;
            font-size: 17px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
            margin-top: 8px;
            padding-top: 8px;
            border-top: 1px solid var(--md-sys-color-outline);
        }

        /* Booking dialog (modal bottom sheet) */
        .${dialogScrim} {
            position: absolute;
            inset: 0;
            background: rgba(0, 0, 0, 0.4);
            display: flex;
            align-items: flex-end;
            justify-content: center;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
            z-index: 40;
        }
        .${dialogScrim}.${dialogScrimVisible} { opacity: 1; pointer-events: auto; }
        .${dialogSheet} {
            width: 100%;
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            border-radius: 28px 28px 0 0;
            box-shadow: 0 -8px 24px var(--md-shadow);
            max-height: 90%;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            transform: translateY(100%);
            transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);
        }
        .${dialogScrim}.${dialogScrimVisible} .${dialogSheet} { transform: translateY(0); }
        /* Scrollable content region; the grip handle above it stays fixed. */
        .${dialogContent} {
            overflow-y: auto;
            padding: 0 24px 24px 24px;
            flex: 1 1 auto;
        }
        /* Grab area around the handle: large hit target, no native gestures. */
        .${dialogGrip} {
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 12px 0 16px 0;
            flex: 0 0 auto;
            cursor: grab;
            touch-action: none;
            user-select: none;
        }
        .${dialogGrip}:active { cursor: grabbing; }
        .${dialogHandle} {
            width: 36px;
            height: 4px;
            border-radius: 100px;
            background-color: var(--md-sys-color-outline);
            opacity: 0.4;
        }
        .${dialogHeader} { margin-bottom: 8px; }
        .${dialogTitle} {
            font-size: 14px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 0 0 4px 0;
        }
        .${dialogService} {
            font-size: 22px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
            margin: 0;
        }
        .${dialogBody} { margin-top: 16px; }
        .${textInput} {
            width: 100%;
            box-sizing: border-box;
            padding: 16px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 16px;
            background: transparent;
            font-family: inherit;
            font-size: 16px;
            color: var(--md-sys-color-on-surface);
        }
        .${textInput}:focus { outline: 2px solid var(--md-sys-color-primary); border-color: transparent; }
        .${dialogActions} {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: 24px;
        }
        .${btnText} {
            background: transparent;
            border: none;
            color: var(--md-sys-color-primary);
            font-family: inherit;
            font-size: 15px;
            font-weight: 700;
            padding: 14px 20px;
            border-radius: 100px;
            cursor: pointer;
            border: 1px solid transparent;
            transition: background-color 0.2s, border 0.2s;
        }
        .${btnText}:hover {
            background-color: var(--md-sys-color-surface-variant);
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
        }
        .${dialogActions} .${btnPrimary} { width: auto; flex: 1 1 auto; }

        /* Offer toolbar (Save / Share) */
        .${offerToolbar} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            margin: 12px 0 4px 0;
            padding: 8px 4px;
        }
        .${toolbarBtn} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 8px 16px;
            border-radius: 100px;
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface);
            font-family: inherit;
            font-size: 14px;
            font-weight: 700;
            cursor: pointer;
            transition: background-color 0.2s, color 0.2s;
        }
        .${toolbarBtn} .${materialSymbols} { font-size: 22px; }
        .${toolbarBtn}:hover { background-color: var(--md-sys-color-surface-variant); }
        .${toolbarBtn}.${toolbarBtnActive} { color: var(--md-sys-color-primary); }

        /* Toast notification */
        .${toast} {
            position: absolute;
            left: 50%;
            bottom: 24px;
            transform: translate(-50%, 16px);
            max-width: 80%;
            padding: 12px 20px;
            border-radius: 100px;
            background-color: var(--md-sys-color-on-surface);
            color: var(--md-sys-color-surface);
            font-size: 14px;
            font-weight: 600;
            text-align: center;
            box-shadow: 0 8px 24px var(--md-shadow);
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease, transform 0.25s ease;
            z-index: 60;
        }
        .${toast}.${toastVisible} { opacity: 1; transform: translate(-50%, 0); }

        /* Home "Pinned & Recent" section */
        .${pinnedSection} { }
        .${pinnedHeader} {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }
        .${pinnedTitle} {
            font-size: 16px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
            margin: 0;
        }
        .${pinnedClear} {
            background: transparent;
            border: none;
            color: var(--md-sys-color-primary);
            font-family: inherit;
            font-size: 13px;
            font-weight: 700;
            padding: 6px 10px;
            border-radius: 100px;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        .${pinnedClear}:hover { background-color: var(--md-sys-color-surface-variant); }
        .${pinnedRow} {
            display: flex;
            gap: 12px;
            overflow-x: auto;
            padding: 8px 0px;
            scroll-snap-type: x proximity;
            -webkit-overflow-scrolling: touch;
        }
        /* Wrapping variant: lays cards out over multiple lines instead of
           scrolling horizontally. Used for the provider services list. */
        .${pinnedRowWrap} {
            flex-wrap: wrap;
            overflow-x: visible;
            scroll-snap-type: none;
        }
        .${pinnedCard} {
            position: relative;
            flex: 0 0 auto;
            width: 96px;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            padding: 12px 8px;
            border-radius: 16px;
            background-color: var(--md-sys-color-surface);
            border: 1px solid var(--md-sys-color-surface-variant);
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            cursor: pointer;
            scroll-snap-align: start;
            transition: background-color 0.2s, transform 0.1s;
        }
        .${pinnedCard}:hover { background-color: var(--md-sys-color-surface-variant); }
        .${pinnedCard}:active { transform: scale(0.97); }
        /* Service card with a generated cover image. The cover is rendered by a
           reusable async-preloaded, fade-in layer (see .${coverImage}) that
           sits at the back, preserving the card's normal layout (the icon and
           label stay visible on top). A 50%-opacity surface veil is layered
           over the image (via ::before) so the background reads at half
           strength; the icon/label are lifted above it. A missing/broken URL
           simply falls back to the base card colour (no broken-image glyph). */
        .${pinnedCard}.${pinnedCardCovered} {
            background-color: var(--md-sys-color-surface);
        }
        .${pinnedCard}.${pinnedCardCovered}::before {
            content: "";
            position: absolute;
            inset: 0;
            z-index: 1;
            background-color: var(--md-sys-color-surface);
            opacity: 0.5;
            border-radius: inherit;
            pointer-events: none;
        }
        .${pinnedCard}.${pinnedCardCovered} .${pinnedCardIcon},
        .${pinnedCard}.${pinnedCardCovered} .${pinnedCardLabel} {
            position: relative;
            z-index: 2;
        }
        .${pinnedCardIcon} {
            width: 48px;
            height: 48px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            background-color: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${pinnedCardIcon} .${materialSymbols} { font-size: 26px; }
        .${pinnedCardLabel} {
            font-size: 12px;
            font-weight: 600;
            text-align: center;
            line-height: 1.3;
            color: var(--md-sys-color-on-surface);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .${pinnedBadge} {
            position: absolute;
            top: 6px;
            right: 6px;
            color: var(--md-sys-color-primary);
            font-size: 16px;
        }
        .${pinnedCard}.${pinnedCardCovered} .${pinnedBadge} {
            z-index: 2;
            color: #ffffff;
            text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
        }
        .${pinnedEmpty} {
            font-size: 14px;
            color: var(--md-sys-color-on-surface-variant);
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 16px;
            padding: 16px;
            text-align: center;
        }
    """.trimIndent()
}
