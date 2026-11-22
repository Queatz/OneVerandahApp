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
    const val appBody = "app-body"
    const val appMain = "app-main"
    const val sidebar = "sidebar"
    const val layoutDesktop = "layout-desktop"
    const val layoutMobile = "layout-mobile"
    const val layoutFullscreen = "layout-fullscreen"
    const val fullscreenExit = "fullscreen-exit"
    const val appBar = "app-bar"
    const val appBarIcon = "app-bar-icon"
    const val appBarTitle = "app-bar-title"
    const val langToggle = "lang-toggle"

    // Banner
    const val banner = "banner"

    // Spacer
    const val spacer = "spacer"

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
    // Cover-image variant that dims the photo with a 50% surface veil
    const val coverImageVeiled = "cover-image-veiled"

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
    const val textHint = "text-hint"

    // Footer / primary button
    const val footer = "footer"
    const val btnPrimary = "btn-primary"
    const val btnSecondary = "btn-secondary"

    // Material icon helper
    const val materialSymbols = "material-symbols-outlined"

    // Success animation
    const val successOverlay = "success-overlay"
    const val successVisible = "visible"
    const val successCircle = "success-circle"
    const val successCheck = "success-check"
    const val successTitle = "success-title"
    const val successMessage = "success-message"

    // Fullscreen photo viewer
    const val photoDialog = "photo-dialog"
    const val photoDialogVisible = "visible"
    const val photoDialogImage = "photo-dialog-image"
    const val photoDialogClose = "photo-dialog-close"
    const val photoDialogNav = "photo-dialog-nav"
    const val photoDialogNavPrev = "photo-dialog-nav-prev"
    const val photoDialogNavNext = "photo-dialog-nav-next"
    const val photoThumbButton = "photo-thumb-button"
    const val photoScroller = "photo-scroller"
    const val photoScrollerViewport = "photo-scroller-viewport"
    const val photoScrollerTrack = "photo-scroller-track"
    const val photoScrollerDragging = "dragging"
    const val photoScrollerSlide = "photo-scroller-slide"
    const val photoScrollerImage = "photo-scroller-image"
    const val photoScrollerNav = "photo-scroller-nav"
    const val photoScrollerNavPrev = "photo-scroller-nav-prev"
    const val photoScrollerNavNext = "photo-scroller-nav-next"
    const val photoScrollerDots = "photo-scroller-dots"
    const val photoScrollerDot = "photo-scroller-dot"
    const val photoScrollerDotActive = "active"
    const val photoScrollerThumbs = "photo-scroller-thumbs"
    const val photoScrollerThumb = "photo-scroller-thumb"
    const val photoScrollerThumbActive = "active"

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
    const val drawerDivider = "drawer-divider"
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

    // Provider skills
    const val skillSection = "skill-section"
    const val skillGrid = "skill-grid"
    const val skillCard = "skill-card"
    const val skillEmoji = "skill-emoji"
    const val skillBody = "skill-body"
    const val skillName = "skill-name"
    const val skillMeter = "skill-meter"
    const val skillPip = "skill-pip"
    const val skillPipFilled = "filled"
    const val skillLevelLabel = "skill-level-label"

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
    /** Tall sheets that should open at max height and scroll internally. */
    const val dialogSheetExpanded = "dialog-sheet-expanded"
    const val dialogGrip = "dialog-grip"
    const val dialogHandle = "dialog-handle"
    const val dialogContent = "dialog-content"
    const val dialogContentWithFooter = "dialog-content-with-footer"
    const val dialogFooter = "dialog-footer"
    const val dialogHeader = "dialog-header"
    const val dialogTitle = "dialog-title"
    const val dialogService = "dialog-service"
    const val dialogBody = "dialog-body"
    const val dialogActions = "dialog-actions"
    const val textInput = "text-input"
    const val textError = "text-error"

    // Chat-style input (text field with a send button that appears when typing)
    const val chatInput = "chat-input"
    const val chatInputListening = "chat-input-listening"
    const val chatInputConnecting = "chat-input-connecting"
    const val chatInputHighlighted = "chat-input-highlighted"
    const val chatInputField = "chat-input-field"
    const val chatInputFieldMultiline = "chat-input-field-multiline"
    const val chatInputFieldFilled = "chat-input-field-filled"
    const val chatSend = "chat-send"
    const val chatVoiceIndicator = "chat-voice-indicator"
    const val btnText = "btn-text"
    const val contentLocked = "content-locked"

    // AI chat and integrated mini apps
    const val aiChat = "ai-chat"
    const val chatMessageRow = "chat-message-row"
    const val chatMessageRowYou = "chat-message-row-you"
    const val chatMessageMeta = "chat-message-meta"
    const val chatBubble = "chat-bubble"
    const val chatBubbleYou = "chat-bubble-you"
    const val chatStatus = "chat-status"
    const val chatError = "chat-error"
    const val chatEmptyState = "chat-empty-state"
    const val chatEmptyIcon = "chat-empty-icon"
    const val chatSuggestion = "chat-suggestion"
    const val chatFollowUps = "chat-follow-ups"
    const val chatFollowUp = "chat-follow-up"
    const val miniAppCard = "mini-app-card"
    const val languageStudyCard = "language-study-card"
    const val languageSentence = "language-sentence"
    const val languageToken = "language-token"
    const val languageTokenSelected = "language-token-selected"
    const val sentencePlay = "sentence-play"
    const val wordDefinition = "word-definition"
    const val wordDefinitionTitle = "word-definition-title"
    const val wordDefinitionLabel = "word-definition-label"
    const val residentChatCard = "resident-chat-card"
    const val residentChatAvatar = "resident-chat-avatar"
    const val residentChatBody = "resident-chat-body"
    const val residentChatName = "resident-chat-name"
    const val residentChatAction = "resident-chat-action"
    const val factCard = "fact-card"
    const val factIcon = "fact-icon"
    const val factBody = "fact-body"
    const val factTitle = "fact-title"

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

    // Home chat explore shortcuts (compact)
    const val homeExploreActions = "home-explore-actions"
    const val homeExploreCard = "home-explore-card"
    const val homeExploreTitle = "home-explore-title"
    const val homeExploreSub = "home-explore-sub"

    // Home services feed / category toggle
    const val homeBrowseToggle = "home-browse-toggle"
    const val serviceFeed = "service-feed"
    const val serviceFeedSentinel = "service-feed-sentinel"
    const val serviceFeedLoading = "service-feed-loading"
    const val serviceCard = "service-card"
    const val serviceCardCover = "service-card-cover"
    const val serviceCardCovered = "service-card-covered"
    const val serviceCardCoverFallback = "service-card-cover-fallback"
    const val serviceCardBody = "service-card-body"
    const val serviceCardTitle = "service-card-title"
    const val serviceCardPrice = "service-card-price"
    const val serviceCardProvider = "service-card-provider"
    const val serviceCardProviderLabel = "service-card-provider-label"
    const val serviceCardProviderName = "service-card-provider-name"
    const val serviceCardDescription = "service-card-description"
    const val serviceCardRating = "service-card-rating"
    const val serviceCardAction = "service-card-action"

    // Phrases / Earth-Mystical Parlance
    const val phraseCard = "phrase-card"
    const val phraseCardCovered = "phrase-card-covered"
    const val phraseCardBody = "phrase-card-body"
    const val phraseLocationHeader = "phrase-location-header"
    const val phraseLocationTag = "phrase-location-tag"
    const val phraseLocationIcon = "phrase-location-icon"
    const val phraseSpoken = "phrase-spoken"
    const val phraseRawAsk = "phrase-raw-ask"
    const val phraseVerse = "phrase-verse"
    const val phraseVerseBlock = "phrase-verse-block"
    const val phraseExpandHint = "phrase-expand-hint"
    const val phraseReactions = "phrase-reactions"
    const val phraseReactionBtn = "phrase-reaction-btn"

    // Admin portal
    const val adminToolbar = "admin-toolbar"
    const val adminActions = "admin-actions"
    const val adminRowActions = "admin-row-actions"
    const val adminPhotoCard = "admin-photo-card"
    const val adminChallengeRow = "admin-challenge-row"
    const val adminChallengeVideo = "admin-challenge-video"
    const val adminPhotoThumb = "admin-photo-thumb"
    const val adminUploadBlock = "admin-upload-block"
    const val adminCoverCard = "admin-cover-card"
    const val adminCoverPreview = "admin-cover-preview"
    const val adminPhraseThumb = "admin-phrase-thumb"
    const val adminLocationSuggestions = "admin-location-suggestions"
    const val adminIconField = "admin-icon-field"
    const val adminIconInput = "admin-icon-input"
    const val adminIconPreview = "admin-icon-preview"
    const val adminRefGrid = "admin-ref-grid"
    const val adminRefTile = "admin-ref-tile"
    const val adminRefTileSelected = "selected"
    const val adminRefThumb = "admin-ref-thumb"
    const val adminStatsCards = "admin-stats-cards"
    const val adminStatsCard = "admin-stats-card"
    const val adminStatsCardLabel = "admin-stats-card-label"
    const val adminStatsCardValue = "admin-stats-card-value"
    const val adminStatsCardHint = "admin-stats-card-hint"
    const val adminStatsTimezone = "admin-stats-timezone"
    const val adminStatsChart = "admin-stats-chart"
    const val adminStatsSvg = "admin-stats-svg"
    const val adminStatsBar = "admin-stats-bar"
    const val adminStatsBarActive = "admin-stats-bar-active"
    const val adminStatsAxis = "admin-stats-axis"
    const val adminStatsAxisLabel = "admin-stats-axis-label"
    const val adminStatsAxisLabelActive = "admin-stats-axis-label-active"
    const val adminStatsAxisLabelHidden = "admin-stats-axis-label-hidden"
    const val adminStatsLegend = "admin-stats-legend"
    const val adminStatsLegendVisitors = "admin-stats-legend-visitors"
    const val adminStatsLegendVisits = "admin-stats-legend-visits"
    const val adminStatsSelected = "admin-stats-selected"
    const val phraseSpokenLabel = "phrase-spoken-label"
    const val phrasesOriginStory = "phrases-origin-story"
    const val phraseInstructions = "phrase-instructions"
    const val phraseInstructionsLabel = "phrase-instructions-label"
    const val chatEmptyLead = "chat-empty-lead"
    const val chatEmptyActions = "chat-empty-actions"
    const val chatEmptyOr = "chat-empty-or"
    const val chatExploreCard = "chat-explore-card"
    const val chatExploreTitle = "chat-explore-title"
    const val chatExploreSub = "chat-explore-sub"
    const val chatBookmarkBtn = "chat-bookmark-btn"
    const val savedPanelBody = "saved-panel-body"
    const val studyExactRow = "study-exact-row"
    const val savedPanelEmpty = "saved-panel-empty"
    const val savedItem = "saved-item"
    const val savedItemSender = "saved-item-sender"
    const val savedItemText = "saved-item-text"
    const val savedItemActions = "saved-item-actions"
    const val savedItemAction = "saved-item-action"
    const val savedItemActionDanger = "saved-item-action-danger"
    const val studySessionPhraseRow = "study-session-phrase-row"
    const val studySessionPhraseImage = "study-session-phrase-image"
    const val studySessionCard = "study-session-card"
    const val studySessionCardTitle = "study-session-card-title"
    const val studySessionExamGradeBadge = "study-session-exam-grade-badge"
    const val studySessionHeaderExamGradeBadge = "study-session-header-exam-grade-badge"
    const val studyExamResult = "study-exam-result"
    const val studyExamHero = "study-exam-hero"
    const val studyExamGradeMark = "study-exam-grade-mark"
    const val studyExamGradeValue = "study-exam-grade-value"
    const val studyExamGradeNumber = "study-exam-grade-number"
    const val studyExamGradeOutOf = "study-exam-grade-out-of"
    const val studyExamHeroCopy = "study-exam-hero-copy"
    const val studyExamLevel = "study-exam-level"
    const val studyExamCheer = "study-exam-cheer"
    const val studyExamSkills = "study-exam-skills"
    const val studyExamSkill = "study-exam-skill"
    const val studyExamSkillTrack = "study-exam-skill-track"
    const val studyExamSkillFill = "study-exam-skill-fill"
    const val studyExamSkillPips = "study-exam-skill-pips"
    const val studyExamSkillPip = "study-exam-skill-pip"
    const val studyExamSkillPipOn = "study-exam-skill-pip-on"
    const val studyExamSkillScore = "study-exam-skill-score"
    const val studyExamMoments = "study-exam-moments"
    const val studyExamMoment = "study-exam-moment"
    const val studyExamMomentPoints = "study-exam-moment-points"
    const val studyExamBriefing = "study-exam-briefing"
    const val studyExamOutlineTitle = "study-exam-outline-title"
    const val studyExamOutline = "study-exam-outline"
    const val studyExamOutlineItem = "study-exam-outline-item"
    const val studyExamOutlineIndex = "study-exam-outline-index"
    const val studyExamStart = "study-exam-start"
    const val studySessionCardActions = "study-session-card-actions"
    const val studySessionSectionTitle = "study-session-section-title"
    const val studyWordsTable = "study-words-table"
    const val studyWordsWord = "study-words-word"
    const val studySessionRemoveBtn = "study-session-remove-btn"
    const val studySessionBody = "study-session-body"
    const val studySessionHint = "study-session-hint"
    const val studySessionError = "study-session-error"
    const val studyCreationProgress = "study-creation-progress"
    const val studyCreationProgressHeader = "study-creation-progress-header"
    const val studyCreationProgressTitle = "study-creation-progress-title"
    const val studyCreationProgressPercent = "study-creation-progress-percent"
    const val studyCreationProgressBar = "study-creation-progress-bar"
    const val studyCreationProgressFill = "study-creation-progress-fill"
    const val studyCreationProgressSteps = "study-creation-progress-steps"
    const val studyCreationProgressStep = "study-creation-progress-step"
    const val studyCreationProgressStepRunning = "study-creation-progress-step-running"
    const val studyCreationProgressStepDone = "study-creation-progress-step-done"
    const val studyCreationProgressStepUpcoming = "study-creation-progress-step-upcoming"
    const val studyCreationProgressStepLabel = "study-creation-progress-step-label"
    const val studyCreationProgressStepCount = "study-creation-progress-step-count"
    const val studyModeTabs = "study-mode-tabs"
    const val studyWorksheetPrint = "study-worksheet-print"
    const val studySearchToggle = "study-search-toggle"
    const val studySearchPanel = "study-search-panel"
    const val studySearchFields = "study-search-fields"
    const val studySearchField = "study-search-field"
    const val studyReadBlock = "study-read-block"
    const val studyReadSentence = "study-read-sentence"
    const val studyReadBreak = "study-read-break"
    const val studyReadPlay = "study-read-play"
    const val studyWriteBlock = "study-write-block"
    const val studyWritePrompt = "study-write-prompt"
    const val studyWriteCorrect = "study-write-correct"
    const val studyWriteFeedback = "study-write-feedback"
    const val studyWriteChecking = "study-write-checking"
    const val studyPracticeInputRow = "study-practice-input-row"
    const val studyPracticeRevealButton = "study-practice-reveal-button"
    const val studyPracticeHint = "study-practice-hint"
    const val studySpeakBlock = "study-speak-block"
    const val studySpeakPrompt = "study-speak-prompt"
    const val studySpeakPhases = "study-speak-phases"
    const val studySpeakPhase = "study-speak-phase"
    const val studySpeakPhaseActive = "study-speak-phase-active"
    const val studySpeakRound = "study-speak-round"
    const val studySpeakActions = "study-speak-actions"
    const val studySpeakAttempts = "study-speak-attempts"
    const val studySpeakAttemptButton = "study-speak-attempt-button"
    const val studyRecallShell = "study-recall-shell"
    const val studyRecallFullscreen = "study-recall-fullscreen"
    const val studyRecallHeader = "study-recall-header"
    const val studyRecallIconButton = "study-recall-icon-button"
    const val studyRecallTitle = "study-recall-title"
    const val studyRecallBlock = "study-recall-block"
    const val studyRecallImage = "study-recall-image"
    const val studyRecallPrompt = "study-recall-prompt"
    const val studyRecallSource = "study-recall-source"
    const val studyRecallTimer = "study-recall-timer"
    const val studyRecallAnswerLabel = "study-recall-answer-label"
    const val studyRecallAnswer = "study-recall-answer"
    const val studyRecallActions = "study-recall-actions"
    const val studyRecallButton = "study-recall-button"
    const val studyRecallCorrect = "study-recall-correct"
    const val studyRecallIncorrect = "study-recall-incorrect"
    const val studyRecallTimeUp = "study-recall-time-up"
    const val studyRecallProgress = "study-recall-progress"
    const val studyRecallScore = "study-recall-score"
    const val studyPlayBlock = "study-play-block"
    const val studyPlayDescription = "study-play-description"
    const val studyPlayLine = "study-play-line"
    const val studyPlayLineYou = "study-play-line-you"
    const val studyPlayLineOther = "study-play-line-other"
    const val studyPlaySpeaker = "study-play-speaker"
    const val studyPlayStatus = "study-play-status"
    const val studySentenceBlock = "study-sentence-block"
    const val studySentenceImage = "study-sentence-image"
    const val studySentenceImagePlaceholder = "study-sentence-image-placeholder"
    const val studyBreakdownButton = "study-breakdown-button"
    const val studyBreakdownSheet = "study-breakdown-sheet"
    const val studyBreakdownSteps = "study-breakdown-steps"
    const val studyBreakdownStep = "study-breakdown-step"
    const val studyBreakdownStepActive = "study-breakdown-step-active"
    const val studyBreakdownSentence = "study-breakdown-sentence"
    const val studyBreakdownPrompt = "study-breakdown-prompt"
    const val studyBreakdownChunk = "study-breakdown-chunk"
    const val studyBreakdownChunkActive = "study-breakdown-chunk-active"
    const val studyBreakdownActions = "study-breakdown-actions"
    const val studyBreakdownFooter = "study-breakdown-footer"
    const val studyBreakdownMeaning = "study-breakdown-meaning"
    const val studyBreakdownYourTurn = "study-breakdown-your-turn"
    const val studyBreakdownListen = "study-breakdown-listen"
    const val studyBreakdownPhotos = "study-breakdown-photos"
    const val studyBreakdownPhotoRow = "study-breakdown-photo-row"
    const val studyBreakdownPhoto = "study-breakdown-photo"
    const val studySentenceImageStatus = "study-sentence-image-status"
    const val studySentenceImageStatusBusy = "study-sentence-image-status-busy"
    const val studySentenceError = "study-sentence-error"
    const val studyTokenPlain = "study-token-plain"
    const val studyTokenPending = "study-token-pending"
    const val studyTokenDetail = "study-token-detail"
    const val studyWordImage = "study-word-image"
    const val studyWordImagePlaceholder = "study-word-image-placeholder"
    const val studyExampleSentence = "study-example-sentence"
    const val studyExampleSentenceLoading = "study-example-sentence-loading"
    const val studyExampleWord = "study-example-word"
    const val studyExampleTranslation = "study-example-translation"
    const val studyPlayground = "study-playground"
    const val studyPlaygroundHint = "study-playground-hint"
    const val studyPlaygroundRow = "study-playground-row"
    const val studyPlaygroundInput = "study-playground-input"
    const val studyPlaygroundSend = "study-playground-send"
    const val studyPlaygroundError = "study-playground-error"
    const val studyPlaygroundResult = "study-playground-result"
    const val studyPlaygroundBadge = "study-playground-badge"
    const val studyPlaygroundBadgeNatural = "study-playground-badge-natural"
    const val studyPlaygroundBadgeUnnatural = "study-playground-badge-unnatural"
    const val studyPlaygroundSummary = "study-playground-summary"
    const val studyPlaygroundImage = "study-playground-image"
    const val studyPlaygroundImageStatus = "study-playground-image-status"
    const val studyPlaygroundImageStatusBusy = "study-playground-image-status-busy"
    const val studyPlaygroundSendBusy = "study-playground-send-busy"
    const val studyPreparingMeanings = "study-preparing-meanings"
    const val studyStoryShell = "study-story-shell"
    const val studyAdventureShell = "study-adventure-shell"
    const val studyAdventureFullscreen = "study-adventure-fullscreen"
    const val studyAdventureHeader = "study-adventure-header"
    const val studyAdventureIconButton = "study-adventure-icon-button"
    const val studyAdventureTitle = "study-adventure-title"
    const val studyAdventureHint = "study-adventure-hint"
    const val studyAdventureEmpty = "study-adventure-empty"
    const val studyAdventureGrid = "study-adventure-grid"
    const val studyAdventureLevel = "study-adventure-level"
    const val studyAdventureLocked = "study-adventure-locked"
    const val studyAdventureLevelNumber = "study-adventure-level-number"
    const val studyAdventureLevelStatus = "study-adventure-level-status"
    const val studyAdventureCard = "study-adventure-card"
    const val studyAdventureIntro = "study-adventure-intro"
    const val studyAdventureStage = "study-adventure-stage"
    const val studyAdventurePromptSentence = "study-adventure-prompt-sentence"
    const val studyAdventureTranslation = "study-adventure-translation"
    const val studyAdventureReview = "study-adventure-review"
    const val studyAdventureReviewSentence = "study-adventure-review-sentence"
    const val studyAdventureReviewWord = "study-adventure-review-word"
    const val studyAdventurePrimary = "study-adventure-primary"
    const val studyAdventurePlay = "study-adventure-play"
    const val studyAdventureBackdrop = "study-adventure-backdrop"
    const val studyAdventureTop = "study-adventure-top"
    const val studyAdventureProgressTrack = "study-adventure-progress-track"
    const val studyAdventureProgressFill = "study-adventure-progress-fill"
    const val studyAdventureWords = "study-adventure-words"
    const val studyAdventureWord = "study-adventure-word"
    const val studyAdventureWordCorrect = "study-adventure-word-correct"
    const val studyAdventureWordText = "study-adventure-word-text"
    const val studyAdventureHealth = "study-adventure-health"
    const val studyAdventureHealthMastered = "study-adventure-health-mastered"
    const val studyAdventureHealthSegment = "study-adventure-health-segment"
    const val studyAdventureHealthFilled = "study-adventure-health-filled"
    const val studyAdventurePrompt = "study-adventure-prompt"
    const val studyAdventurePromptWord = "study-adventure-prompt-word"
    const val studyAdventureTimer = "study-adventure-timer"
    const val studyAdventureTimerFill = "study-adventure-timer-fill"
    const val studyAdventureCorrect = "study-adventure-correct"
    const val studyAdventureCorrectMark = "study-adventure-correct-mark"
    const val studyAdventureMessage = "study-adventure-message"
    const val studyAdventureMessageTitle = "study-adventure-message-title"
    const val studyStoryCard = "study-story-card"
    const val studyStoryCardFullscreen = "study-story-card-fullscreen"
    const val studyStoryHeader = "study-story-header"
    const val studyStoryProgress = "study-story-progress"
    const val studyStoryImage = "study-story-image"
    const val studyStoryImagePlaceholder = "study-story-image-placeholder"
    const val studyStoryWord = "study-story-word"
    const val studyStorySentence = "study-story-sentence"
    const val studyStoryMeaningLabel = "study-story-meaning-label"
    const val studyStoryMeaning = "study-story-meaning"
    const val studyStoryActions = "study-story-actions"
    const val studyStoryBack = "study-story-back"
    const val studyStoryContinue = "study-story-continue"
    const val studyStoryFullscreen = "study-story-fullscreen"
    const val studyWatchPlayer = "study-watch-player"
    const val studyWatchStage = "study-watch-stage"
    const val studyWatchImage = "study-watch-image"
    const val studyWatchImageBkg = "study-watch-image-bkg"
    const val studyWatchStageStatus = "study-watch-stage-status"
    const val studyWatchControls = "study-watch-controls"
    const val studyWatchSeek = "study-watch-seek"
    const val studyWatchSeekTrack = "study-watch-seek-track"
    const val studyWatchSeekFill = "study-watch-seek-fill"
    const val studyWatchSeekMarker = "study-watch-seek-marker"
    const val studyWatchSeekThumb = "study-watch-seek-thumb"
    const val studyWatchTime = "study-watch-time"
    const val studyWatchFullscreen = "study-watch-fullscreen"
    const val studyWatchPlayerFullscreen = "study-watch-player-fullscreen"
    const val storiesSoon = "stories-soon"
    const val storiesGrid = "stories-grid"
    const val challengeStartCard = "challenge-start-card"
    const val meetPitchClamp = "meet-pitch-clamp"
    const val meetCardBody = "meet-card-body"
    const val meetDetailPhoto = "meet-detail-photo"
    const val meetDetailPitch = "meet-detail-pitch"
    const val meetPickPhoto = "meet-pick-photo"
    const val meetAdminPhoto = "meet-admin-photo"
    const val locationCard = "location-card"
    const val locationCardMain = "location-card-main"
    const val locationCardPhotos = "location-card-photos"
    const val locationCardPhotosTrack = "location-card-photos-track"
    const val locationCardPhoto = "location-card-photo"
    const val locationCardPhotoFallback = "location-card-photo-fallback"
    const val locationCardCategory = "location-card-category"
    const val locationCardBody = "location-card-body"
    const val locationCardName = "location-card-name"
    const val locationCardType = "location-card-type"
    const val locationCardMeta = "location-card-meta"
    const val locationCardHours = "location-card-hours"
    const val locationHoursInline = "location-hours-inline"
    const val locationWeeklyHours = "location-weekly-hours"
    const val locationWeeklyHoursRow = "location-weekly-hours-row"
    const val locationWeeklyHoursDay = "location-weekly-hours-day"
    const val locationWeeklyHoursTime = "location-weekly-hours-time"
    const val locationWeeklyHoursClosed = "location-weekly-hours-closed"
    const val locationWeeklyHoursToday = "location-weekly-hours-today"
    const val locationCardActions = "location-card-actions"
    const val locationHighlights = "location-highlights"
    const val locationHighlight = "location-highlight"
    const val locationHighlightTone0 = "location-highlight-0"
    const val locationHighlightTone1 = "location-highlight-1"
    const val locationHighlightTone2 = "location-highlight-2"
    const val locationStatus = "location-status"
    const val locationStatusPlanning = "location-status-planning"
    const val locationStatusSoon = "location-status-soon"
    const val locationStatusOpen = "location-status-open"
    const val locationStatusClosed = "location-status-closed"
    const val locationDetailPhotos = "location-detail-photos"
    const val locationDetailPhoto = "location-detail-photo"
    const val locationContentPhoto = "location-content-photo"
    const val locationFooter = "location-footer"
    const val locationMarkdown = "location-markdown"
    const val zaloContactBtn = "zalo-contact-btn"
    const val zaloContactIcon = "zalo-contact-icon"
    const val zaloContactCopy = "zalo-contact-copy"
    const val zaloContactBrand = "zalo-contact-brand"
    const val zaloContactNumber = "zalo-contact-number"
    const val zaloQrDialog = "zalo-qr-dialog"
    const val zaloQrFrame = "zalo-qr-frame"
    const val zaloQrImage = "zalo-qr-image"
    const val zaloQrNumber = "zalo-qr-number"
    const val locationMarkdownEm = "location-markdown-em"
    const val locationAdminPhotos = "location-admin-photos"
    const val locationAdminPhotoWrap = "location-admin-photo-wrap"
    const val locationAdminPhoto = "location-admin-photo"
    const val locationScheduleRow = "location-schedule-row"
    const val challengeCover = "challenge-cover"
    const val challengeCoverMedia = "challenge-cover-media"
    const val challengePickVideo = "challenge-pick-video"
    const val challengePickPreview = "challenge-pick-preview"
    const val challengesReviewList = "challenges-review-list"
    const val challengeReviewCard = "challenge-review-card"
    const val challengeReviewThumb = "challenge-review-thumb"
    const val challengeReviewThumbImg = "challenge-review-thumb-img"
    const val challengeProgressTrack = "challenge-progress-track"
    const val challengeProgressFill = "challenge-progress-fill"
    const val challengePlayer = "challenge-player"
    const val challengePlayerEmpty = "challenge-player-empty"
    const val challengeStage = "challenge-stage"
    const val challengeVideoWrap = "challenge-video-wrap"
    const val challengeVideo = "challenge-video"
    const val challengePreload = "challenge-preload"
    const val challengeFlyEmoji = "challenge-fly-emoji"
    const val challengeClose = "challenge-close"
    const val challengeTopMeta = "challenge-top-meta"
    const val challengeMedal = "challenge-medal"
    const val challengeMedalGold = "challenge-medal-gold"
    const val challengeMedalSilver = "challenge-medal-silver"
    const val challengeMedalBronze = "challenge-medal-bronze"
    const val challengeVoteCount = "challenge-vote-count"
    const val challengeName = "challenge-name"
    const val challengeCaption = "challenge-caption"
    const val challengePauseHint = "challenge-pause-hint"
    const val challengeBottom = "challenge-bottom"
    const val challengeOverlayBar = "challenge-overlay-bar"
    const val challengeNavBtns = "challenge-nav-btns"
    const val challengeDock = "challenge-dock"
    const val challengeNextBtn = "challenge-next-btn"
    const val challengeEmojiRow = "challenge-emoji-row"
    const val challengeEmojiBtn = "challenge-emoji-btn"
    const val challengeEmojiBtnActive = "challenge-emoji-btn-active"
    const val challengeVoteBtn = "challenge-vote-btn"
    const val challengeVoteBtnOn = "challenge-vote-btn-on"
    const val storyCoverCard = "story-cover-card"
    const val storyCoverThumb = "story-cover-thumb"
    const val storyCoverPlaceholder = "story-cover-placeholder"
    const val storyCoverTitle = "story-cover-title"
    const val storyCoverCategory = "story-cover-category"
    const val storyCoverMeta = "story-cover-meta"
    const val storyReaderCover = "story-reader-cover"
    const val storyReaderTitle = "story-reader-title"
    const val storyReaderNav = "story-reader-nav"
    const val storyLearningRow = "story-learning-row"
    const val storyPageMeta = "story-page-meta"
    const val storyPagePhoto = "story-page-photo"
    const val storyPageText = "story-page-text"
    const val storyStudyBlock = "story-study-block"
    const val hiddenAdminWrap = "hidden-admin-wrap"
    const val hiddenAdminButton = "hidden-admin-button"

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
            --md-sys-color-surface-container: #F3EDF7;
            --md-sys-color-surface-container-high: #ECE6F0;
            --md-sys-color-on-surface: #1D1B20;
            --md-sys-color-on-surface-variant: #49454F;
            --md-sys-color-surface-variant: #E7E0EC;
            --md-sys-color-outline: #79747E;
            --md-sys-color-error: #B3261E;
            --md-sys-color-success: #46ba11;
            --md-app-background: #f4f4f4;
            --md-banner-fade: #FEF7FF;
            --md-shadow: rgba(0, 0, 0, 0.15);
            --md-shadow-light: rgba(255, 255, 255, 0.6);
            --md-sys-color-scrim: rgba(0, 0, 0, 0.4);
        }

        @media (prefers-color-scheme: dark) {
            :root {
                --md-sys-color-primary: #D0BCFF;
                --md-sys-color-primary-dark: #b69df8;
                --md-sys-color-on-primary: #381E72;
                --md-sys-color-primary-container: #4F378B;
                --md-sys-color-on-primary-container: #EADDFF;
                --md-sys-color-surface: #141218;
                --md-sys-color-surface-container: #211F26;
                --md-sys-color-surface-container-high: #2B2930;
                --md-sys-color-on-surface: #E6E0E9;
                --md-sys-color-on-surface-variant: #CAC4D0;
                --md-sys-color-surface-variant: #2B2930;
                --md-sys-color-outline: #938F99;
                --md-sys-color-error: #F2B8B5;
                --md-sys-color-success: #46ba11;
                --md-app-background: #000000;
                --md-banner-fade: #141218;
                --md-shadow: rgba(0, 0, 0, 0.6);
                --md-shadow-light: rgba(255, 255, 255, 0.6);
                --md-sys-color-scrim: rgba(0, 0, 0, 0.8);
            }
        }

        * { box-sizing: border-box; }

        html, body {
            margin: 0;
            padding: 0;
            height: 100%;
            background-color: var(--md-app-background);
        }

        body {
            font-family: 'Quicksand', sans-serif;
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

        /* Phone-like frame by default / in mobile layout; full-bleed on small
           screens. Desktop layout overrides below. */
        .${appFrame} {
            position: relative;
            display: flex;
            flex-direction: column;
            width: 100%;
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

        /* Phone-frame chrome when the user is in mobile layout on a wide screen. */
        @media (min-width: 600px) {
            .${appFrame} { max-width: 480px; }
            .${appShell}.${layoutMobile} { align-items: center; padding: 32px 16px; }
            .${appShell}.${layoutMobile} .${appFrame} {
                min-height: 0;
                max-height: calc(100vh - 64px);
                border-radius: 28px;
                box-shadow: 0 10px 30px var(--md-shadow);
            }
        }

        /* ---- Desktop shell ----
           Full-width top bar, permanent left sidebar, main column fills the
           leftover space with page content centered at max-width 800px. */
        .${appShell}.${layoutDesktop} {
            align-items: stretch;
            padding: 0;
            min-height: 100vh;
            min-height: 100dvh;
        }
        .${appShell}.${layoutDesktop} .${appFrame} {
            max-width: none;
            width: 100%;
            height: 100vh;
            height: 100dvh;
            max-height: none;
            border-radius: 0;
            box-shadow: none;
        }
        .${appShell}.${layoutDesktop} .${appBar} {
            border-radius: 0;
            /* Flat full-width bar; ignore phone-frame scroll radius. */
            --appbar-radius: 0px;
        }
        .${appBody} {
            display: flex;
            flex: 1 1 auto;
            min-height: 0;
            min-width: 0;
            overflow: hidden;
        }
        .${appMain} {
            position: relative;
            display: flex;
            flex-direction: column;
            flex: 1 1 auto;
            min-width: 0;
            min-height: 0;
            overflow: hidden;
        }
        .${sidebar} {
            flex: 0 0 280px;
            width: 280px;
            max-width: 32vw;
            min-height: 0;
            display: flex;
            flex-direction: column;
            padding: 12px;
            overflow-y: auto;
            z-index: 1;
            background-color: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            border-right: 1px solid color-mix(in srgb, var(--md-sys-color-outline) 28%, transparent);
            box-shadow: 8px 0 24px -12px var(--md-shadow);
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
        .${sidebar}::-webkit-scrollbar { display: none; }
        /* Center main column content/footer within a 800px readable measure. */
        .${appShell}.${layoutDesktop} .${appMain} > .${content},
        .${appShell}.${layoutDesktop} .${appMain} > .${footer} {
            padding-left: max(24px, calc((100% - 800px) / 2));
            padding-right: max(24px, calc((100% - 800px) / 2));
        }
        /* Banner stays behind the main column only (not under the sidebar). */
        .${appShell}.${layoutDesktop} .${appMain} > .${banner} {
            left: 0;
            right: 0;
        }
        /* Mild density bump so home grids use the wider measure. */
        .${appShell}.${layoutDesktop} .${grid} {
            grid-template-columns: repeat(4, 1fr);
        }
        @media (min-width: 1280px) {
            .${appShell}.${layoutDesktop} .${grid} {
                grid-template-columns: repeat(5, 1fr);
            }
        }
        .${appShell}.${layoutDesktop} .${homeExploreActions} {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        /* Bottom sheets stay docked to the bottom edge on desktop (no floating
           centered dialog). Square off the bottom corners so the sheet meets
           the frame edge cleanly. */
        .${appShell}.${layoutDesktop} .${dialogScrim} {
            align-items: flex-end;
            justify-content: center;
            padding: 0;
        }
        .${appShell}.${layoutDesktop} .${dialogSheet} {
            width: 100%;
            max-width: 800px;
            max-height: 90%;
            border-radius: 28px 28px 0 0;
            transform: translateY(100%);
        }
        .${appShell}.${layoutDesktop} .${dialogScrim}.${dialogScrimVisible} .${dialogSheet} {
            transform: translateY(0);
        }
        
        /* Spacer */
        .${spacer} {
            height: 16px;
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
            background-position: 50% 75%;
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
            // background-color: var(--md-sys-color-surface);
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
            transition: background-color 0.2s, transform 0.1s, box-shadow 0.2s;
            user-select: none;
        }
        .${card}:hover { background-color: var(--md-sys-color-primary-container); box-shadow: 0 0 16px -4px #ffffff36; }
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
            align-items: center;
            border: none;
        }
        .${card}.${cardCovered} .${cardLabel} {
            position: relative;
            z-index: 1;
            width: 100%;
            padding: 0 12px;
            color: #ffffff;
            text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
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
            box-shadow:
                inset 0 0 0 1px #ffffff16,
                inset 0 4px 8px -8px var(--md-shadow-light);
            opacity: 0;
            transition: opacity 0.45s ease, filter 0.45s ease;
            pointer-events: none;
        }
        .${card}:hover.${cardCovered} .${coverImage} { filter: brightness(1.6); }
        .${coverImage}.${coverImageLoaded} { opacity: 1; }
        /* Veiled variant: dims the photo with a 50% surface layer painted
           directly onto the cover image (a huge inset box-shadow), so the
           light border/glow — listed first — still renders on top of both the
           image and the veil, all on the cover image itself (no extra layers). */
        .${coverImageVeiled} {
            position: absolute;
            inset: 0;
            border-radius: 28px;
            background-color: color-mix(in srgb, var(--md-sys-color-surface) 50%, transparent);
            pointer-events: none;
            transition: inset 0.2s ease;
        }
         .${pinnedCard}.${pinnedCardCovered} .${coverImageVeiled} {
            border-radius: 16px;
         }
        .${card}:hover .${coverImageVeiled},
        .${pinnedCard}:hover .${coverImageVeiled} {
            inset: 0px 1px 1px 1px;
        }
        /* Services list */
        .${listCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            padding: 16px;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            cursor: pointer;
            gap: 8px;
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            transition: background-color 0.2s, transform 0.1s, box-shadow 0.2s;
            user-select: none;
        }
        .${listCard}:hover { background-color: var(--md-sys-color-primary-container); box-shadow: 0 0 16px -4px #ffffff36; }
        .${listCard}:active { transform: scale(0.99); }
        .${listIcon} {
            font-size: 40px;
            color: var(--md-sys-color-primary);
        }
        /* Thumbnail cover for a service row (falls back to the icon when the
           image has not been generated yet). */
        .${listCover} {
            width: 56px;
            height: 56px;
            border-radius: 16px;
            flex: 0 0 auto;
            background-size: cover;
            background-position: center;
            background-color: var(--md-sys-color-surface-variant);
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
        }
        .${chip}:disabled {
            opacity: 0.4;
            cursor: not-allowed;
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
        .${textHint} {
            color: var(--md-sys-color-on-surface-variant);
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
        .${btnSecondary} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            width: 100%;
            background: transparent;
            color: var(--md-sys-color-primary);
            border: 1px solid var(--md-sys-color-primary);
            border-radius: 100px;
            padding: 12px 20px;
            font-size: 15px;
            font-weight: 700;
            font-family: inherit;
            cursor: pointer;
            transition: background-color 0.2s, filter 0.2s, transform 0.1s;
        }
        .${btnSecondary}:hover { background-color: var(--md-sys-color-surface-variant); }
        .${btnSecondary}:active { transform: scale(0.98); }
        .${btnSecondary}:disabled { filter: grayscale(0.3) brightness(0.7); cursor: default; pointer-events: none; }

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

        /* Fullscreen photo viewer */
        .${photoDialog} {
            position: absolute;
            inset: 0;
            background: rgba(0, 0, 0, 0.92);
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 56px 16px 16px;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
            z-index: 50;
        }
        .${photoDialog}.${photoDialogVisible} {
            opacity: 1;
            pointer-events: auto;
        }
        .${photoDialogImage} {
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
            border-radius: 12px;
        }
        .${photoDialogClose} {
            position: absolute;
            top: 12px;
            right: 12px;
            width: 44px;
            height: 44px;
            padding: 0;
            border: none;
            border-radius: 50%;
            background: color-mix(in srgb, #000 45%, transparent);
            color: #fff;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
        }
        .${photoDialogClose}:hover {
            background: color-mix(in srgb, #000 65%, transparent);
        }
        .${photoThumbButton} {
            display: block;
            padding: 0;
            border: none;
            background: transparent;
            cursor: pointer;
            line-height: 0;
            border-radius: 12px;
            flex: 0 0 auto;
            max-width: 100%;
        }
        .${photoThumbButton}:has(.${adminCoverPreview}) {
            width: 100%;
        }

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
        .${drawerItem}.${drawerItemActive} {
            background-color: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
        }
        .${drawerItem} .${materialSymbols} { color: var(--md-sys-color-primary); font-size: 22px; }
        .${drawerSection} {
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 16px 12px 4px 12px;
        }
        .${drawerDivider} {
            height: 0;
            margin: 12px;
            border-top: 1px solid var(--md-sys-color-outline);
            opacity: 0.45;
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

        /* Provider skills — primary profile highlight */
        .${skillSection} {
            margin-bottom: 20px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .${skillGrid} {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 10px;
        }
        .${skillCard} {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 14px 14px;
            border-radius: 20px;
            background:
                linear-gradient(
                    145deg,
                    color-mix(in srgb, var(--md-sys-color-primary-container) 55%, var(--md-sys-color-surface-variant)),
                    var(--md-sys-color-surface-variant)
                );
            border: 1px solid color-mix(in srgb, var(--md-sys-color-primary) 18%, transparent);
            box-shadow: 0 6px 16px -12px var(--md-shadow);
            min-width: 0;
        }
        .${skillEmoji} {
            flex: 0 0 auto;
            width: 44px;
            height: 44px;
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            line-height: 1;
            background: color-mix(in srgb, var(--md-sys-color-surface) 80%, transparent);
            box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--md-sys-color-outline) 20%, transparent);
        }
        .${skillBody} {
            flex: 1 1 auto;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .${skillName} {
            margin: 0;
            font-size: 13px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
            line-height: 1.25;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .${skillMeter} {
            display: flex;
            align-items: center;
            gap: 4px;
        }
        .${skillPip} {
            width: 10px;
            height: 10px;
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-outline) 35%, transparent);
            transition: background-color 0.15s ease, transform 0.15s ease;
        }
        .${skillPip}.${skillPipFilled} {
            background: linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-primary-dark));
            box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-sys-color-primary) 18%, transparent);
            transform: scale(1.05);
        }
        .${skillLevelLabel} {
            margin-left: 4px;
            font-size: 11px;
            font-weight: 700;
            color: var(--md-sys-color-primary);
            letter-spacing: 0.02em;
            white-space: nowrap;
        }

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
            position: fixed;
            inset: 0;
            background: var(--md-sys-color-scrim);
            display: flex;
            align-items: flex-end;
            justify-content: center;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease, filter 0.25s ease;
            z-index: 40;
        }
        .${dialogScrim}.${dialogScrimVisible} {
            opacity: 1;
            pointer-events: auto;
            backdrop-filter: blur(12px);
        }
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
        /* Pin height for large/async content so progressive loads only scroll
           inside the sheet instead of resizing it (and jolting the frame). */
        .${dialogSheet}.${dialogSheetExpanded} {
            height: 90%;
            max-height: 90%;
        }
        .${dialogScrim}.${dialogScrimVisible} .${dialogSheet} { transform: translateY(0); }
        /* Scrollable content region; the grip handle above it stays fixed. */
        .${dialogContent} {
            overflow-y: auto;
            padding: 0 24px 24px 24px;
            flex: 1 1 auto;
            /* Allow the flex child to shrink so overflow-y scrolling works when
               the sheet is height-pinned (expanded). */
            min-height: 0;
        }
        .${dialogContent}.${dialogContentWithFooter} {
            padding-bottom: 12px;
        }
        /* Pinned action row: stays below the scrollable body. */
        .${dialogFooter} {
            flex: 0 0 auto;
            padding: 12px 24px 24px;
            background-color: var(--md-sys-color-surface);
        }
        .${dialogFooter} .${dialogActions} {
            margin-top: 0;
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

        /* Chat-style input: a text field with a send button that appears on the
           right once the user starts typing. */
        .${chatInput} {
            position: relative;
            display: flex;
            align-items: center;
        }
        .${chatInputField} {
            transition: padding-right 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
        }
        .${chatInputListening} .${chatInputField} {
            padding-right: 60px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 10%, transparent);
            box-shadow: 0 0 0 2px var(--md-sys-color-primary);
        }
        .${chatInputConnecting} .${chatInputField} {
            background: color-mix(in srgb, var(--md-sys-color-primary) 6%, transparent);
            box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-sys-color-primary) 55%, transparent);
        }
        .${chatInputHighlighted} .${chatInputField} {
            background: color-mix(in srgb, var(--md-sys-color-primary) 10%, transparent);
            box-shadow: 0 0 0 2px var(--md-sys-color-primary);
        }
        .${chatInputConnecting} .${chatVoiceIndicator} .${materialSymbols} {
            animation: spin 1.1s linear infinite;
        }
        .${chatInputFieldMultiline} {
            min-height: 56px;
            max-height: 104px;
            line-height: 24px;
            resize: none;
            overflow-y: auto;
        }
        .${chatInputField}.${chatInputFieldFilled} {
            padding-right: 60px;
        }
        .${chatSend} {
            position: absolute;
            right: 8px;
            top: 50%;
            transform: translateY(-50%);
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: none;
            display: flex;
            align-items: center;
            justify-content: center;
            background: none; //linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-primary-dark));
            color: var(--md-sys-color-primary);
            cursor: pointer;
            // box-shadow: 0 4px 12px var(--md-shadow), 0 4px 8px -8px var(--md-shadow-light) inset;
            // border: 1px solid #ffffff16;
            transition: filter 0.2s, transform 0.1s;
        }
        .${chatSend} .${materialSymbols} { font-size: 22px; }
        .${chatSend}:hover { filter: brightness(1.07); }
        .${chatSend}:active { transform: translateY(-50%) scale(0.94); }
        .${chatVoiceIndicator} {
            position: absolute;
            right: 16px;
            top: 50%;
            transform: translateY(-50%);
            display: grid;
            place-items: center;
            color: var(--md-sys-color-primary);
            pointer-events: none;
            animation: voice-pulse 1s ease-in-out infinite;
        }
        .${chatVoiceIndicator} .${materialSymbols} { font-size: 24px; }
        @keyframes voice-pulse {
            0%, 100% { opacity: 0.55; transform: translateY(-50%) scale(0.9); }
            50% { opacity: 1; transform: translateY(-50%) scale(1.12); }
        }

        .${aiChat} {
            display: flex;
            flex-direction: column;
            gap: 18px;
            min-height: 100%;
            padding: 8px 0 20px;
        }
        .${chatMessageRow} {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 5px;
            max-width: 88%;
        }
        .${chatMessageRowYou} {
            align-self: flex-end;
            align-items: flex-end;
        }
        .${chatMessageMeta} {
            padding: 0 8px;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.03em;
        }
        .${chatBubble} {
            padding: 12px 15px;
            border-radius: 6px 20px 20px 20px;
            background: var(--md-sys-color-surface-variant);
            color: var(--md-sys-color-on-surface);
            box-shadow: 0 2px 8px var(--md-shadow);
        }
        .${chatBubbleYou} {
            border-radius: 20px 6px 20px 20px;
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${chatBubble} p { margin: 0; line-height: 1.5; white-space: pre-wrap; }
        .${chatStatus}, .${chatError} {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 4px 8px;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 14px;
        }
        .${chatStatus} .${materialSymbols} { animation: spin 1.2s linear infinite; }
        .${chatError} { color: var(--md-sys-color-error); }
        .${chatEmptyState} {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 11px;
            flex: 1 1 auto;
            width: min(100%, 520px);
            min-height: 55vh;
            margin: auto;
            text-align: center;
        }
        .${chatEmptyIcon} {
            display: grid;
            place-items: center;
            width: 58px;
            height: 58px;
            margin-bottom: 7px;
            border-radius: 20px;
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${chatEmptyIcon} .${materialSymbols} { font-size: 30px; }
        .${chatSuggestion} {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 14px;
            width: 100%;
            border: 1px solid color-mix(in srgb, var(--md-sys-color-outline) 55%, transparent);
            border-radius: 18px;
            padding: 13px 16px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            box-shadow: 0 3px 12px var(--md-shadow);
            font: inherit;
            text-align: left;
            cursor: pointer;
            transition: background 0.15s, border-color 0.15s, transform 0.15s;
        }
        .${chatSuggestion}:hover {
            border-color: var(--md-sys-color-primary);
            background: var(--md-sys-color-surface-variant);
        }
        .${chatSuggestion}:active { transform: scale(0.97); }
        .${chatSuggestion} .${materialSymbols} {
            flex: 0 0 auto;
            color: var(--md-sys-color-primary);
            font-size: 20px;
        }
        .${chatFollowUps} {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 6px;
            width: 100%;
            margin-top: 6px;
        }
        .${chatFollowUp} {
            border: 1px solid color-mix(in srgb, var(--md-sys-color-outline) 35%, transparent);
            border-radius: 999px;
            padding: 7px 12px;
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            font: inherit;
            font-size: 13px;
            line-height: 1.3;
            text-align: left;
            cursor: pointer;
            transition: background 0.15s, border-color 0.15s, color 0.15s;
        }
        .${chatFollowUp}:hover {
            border-color: color-mix(in srgb, var(--md-sys-color-primary) 65%, transparent);
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${chatFollowUp}:active { filter: brightness(0.96); }
        @keyframes spin { to { transform: rotate(360deg); } }
        .${miniAppCard} {
            width: 100%;
            margin-top: 5px;
            border: 1px solid color-mix(in srgb, var(--md-sys-color-outline) 45%, transparent);
            border-radius: 20px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            box-shadow: 0 5px 18px var(--md-shadow);
        }
        .${languageStudyCard} { padding: 16px; }
        .${languageSentence} {
            display: flex;
            flex-wrap: wrap;
            gap: 7px;
            margin-bottom: 14px;
        }
        .${languageToken} {
            border: 0;
            border-radius: 10px;
            padding: 6px 8px;
            background: var(--md-sys-color-surface-variant);
            color: var(--md-sys-color-on-surface);
            font: inherit;
            font-size: 18px;
            cursor: pointer;
            transition: background 0.15s, color 0.15s, transform 0.15s;
        }
        .${languageToken}:hover { transform: translateY(-1px); }
        .${languageTokenSelected} {
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${sentencePlay} {
            display: inline-flex;
            align-items: center;
            gap: 7px;
            border: 0;
            border-radius: 999px;
            padding: 9px 13px;
            background: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
            font: inherit;
            font-weight: 700;
            cursor: pointer;
            transition: filter 0.15s, transform 0.15s;
        }
        .${sentencePlay}:hover { filter: brightness(1.07); }
        .${sentencePlay}:active { transform: scale(0.98); }
        .${sentencePlay}:disabled { opacity: 0.48; cursor: wait; }
        .${wordDefinition} {
            display: flex;
            flex-direction: column;
            gap: 5px;
            margin-top: 14px;
            padding: 14px;
            border-radius: 14px;
            background: var(--md-sys-color-surface-variant);
        }
        .${wordDefinition} p { margin: 0 0 7px; line-height: 1.45; }
        .${wordDefinitionTitle} { font-size: 20px; font-weight: 800; }
        .${wordDefinitionLabel} {
            margin-top: 3px;
            color: var(--md-sys-color-primary);
            font-size: 11px;
            font-weight: 800;
            letter-spacing: 0.07em;
            text-transform: uppercase;
        }
        .${factCard} {
            display: flex;
            align-items: flex-start;
            gap: 14px;
            padding: 17px;
            background: linear-gradient(145deg, var(--md-sys-color-surface), var(--md-sys-color-primary-container));
        }
        .${factIcon} {
            display: grid;
            place-items: center;
            flex: 0 0 48px;
            width: 48px;
            height: 48px;
            border-radius: 15px;
            background: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
        }
        .${factIcon} .${materialSymbols} { font-size: 27px; }
        .${factBody} { min-width: 0; }
        .${factTitle} {
            margin: 1px 0 6px;
            color: var(--md-sys-color-on-surface);
            font-size: 17px;
            font-weight: 800;
        }
        .${factBody} p {
            margin: 0;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 14px;
            line-height: 1.5;
        }
        .${residentChatCard} {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 14px;
            text-align: left;
            font: inherit;
            cursor: pointer;
        }
        .${residentChatAvatar} {
            display: grid;
            place-items: center;
            flex: 0 0 48px;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${residentChatBody} {
            display: flex;
            flex: 1;
            min-width: 0;
            flex-direction: column;
            gap: 3px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${residentChatName} { color: var(--md-sys-color-on-surface); font-weight: 800; }
        .${residentChatAction} { color: var(--md-sys-color-primary); font-size: 12px; font-weight: 700; }
        @media (max-width: 420px) {
            .${chatMessageRow} { max-width: 94%; }
            .${languageToken} { font-size: 17px; }
        }

        .${dialogActions} {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: 24px;
        }
        .${btnText} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: transparent;
            border: none;
            color: var(--md-sys-color-primary);
            font-family: inherit;
            font-size: 15px;
            font-weight: 700;
            text-align: center;
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

        /* Home latest-content previews under search */
        .${homeExploreActions} {
            display: flex;
            flex-direction: column;
            width: 100%;
            margin: -8px 0 12px 0;
        }
        .${homeExploreActions} .${sectionTitle} {
            margin-top: 8px;
        }
        .${homeExploreActions} .${sectionTitle}:first-child {
            margin-top: 0;
        }
        .${homeExploreActions} .${storiesGrid} {
            margin-top: 0;
            margin-bottom: 8px;
        }
        .${homeExploreActions} .${btnSecondary} {
            margin: 4px 0 12px 0;
        }
        .${homeExploreCard} {
            display: flex;
            align-items: center;
            gap: 8px;
            width: 100%;
            box-sizing: border-box;
            text-align: left;
            padding: 10px 12px;
            border-radius: 14px;
            border: 1px solid var(--md-sys-color-outline);
            background: color-mix(in srgb, var(--md-sys-color-primary) 8%, var(--md-sys-color-surface));
            color: var(--md-sys-color-on-surface);
            font: inherit;
            cursor: pointer;
            min-width: 0;
            background-image: linear-gradient(to bottom right, #ffffff1c, transparent);
        }
        .${homeExploreCard} > div {
            display: flex;
            flex-direction: column;
            gap: 1px;
            flex: 1 1 auto;
            min-width: 0;
        }
        .${homeExploreCard} .${materialSymbols} {
            font-size: 24px;
            flex: 0 0 auto;
        }
        .${homeExploreTitle} {
            font-weight: 700;
            font-size: 13px;
            line-height: 1.2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .${homeExploreSub} {
            font-size: 11px;
            line-height: 1.25;
            color: var(--md-sys-color-on-surface-variant);
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        /* Home browse mode toggle (Services feed vs Categories) */
        .${homeBrowseToggle} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin: 4px 0 16px 0;
        }

        /* Infinite services feed */
        .${serviceFeed} {
            display: flex;
            flex-direction: column;
            gap: 16px;
            margin-bottom: 8px;
        }
        .${serviceFeedSentinel} {
            min-height: 1px;
            width: 100%;
        }
        .${serviceFeedLoading} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding: 12px 8px 4px 8px;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
            font-weight: 600;
        }
        .${serviceFeedLoading} .${materialSymbols} {
            font-size: 18px;
            animation: spin 1.1s linear infinite;
        }

        /* Service feed card (content-post style) */
        .${serviceCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            overflow: hidden;
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            color: var(--md-sys-color-on-surface);
            transition: box-shadow 0.2s, transform 0.1s;
        }
        .${serviceCard}:hover {
            box-shadow: 0 0 16px -4px #ffffff36;
        }
        .${serviceCardCover} {
            position: relative;
            width: 100%;
            aspect-ratio: 16 / 10;
            min-height: 160px;
            background: linear-gradient(
                135deg,
                color-mix(in srgb, var(--md-sys-color-primary) 22%, var(--md-sys-color-surface-variant)),
                var(--md-sys-color-surface-variant)
            );
            overflow: hidden;
        }
        .${serviceCardCover}.${serviceCardCovered} {
            background-color: var(--md-sys-color-surface-variant);
        }
        .${serviceCardCoverFallback} {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .${serviceCardCoverFallback} .${materialSymbols} {
            font-size: 56px;
            color: var(--md-sys-color-primary);
        }
        .${serviceCardCover} .${coverImageVeiled} {
            border-radius: 0;
            background-color: color-mix(in srgb, var(--md-sys-color-surface) 18%, transparent);
        }
        .${serviceCardBody} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 16px 16px 14px 16px;
        }
        .${serviceCardTitle} {
            margin: 0;
            font-size: 20px;
            font-weight: 700;
            line-height: 1.25;
        }
        .${serviceCardPrice} {
            margin: 0;
            font-size: 15px;
            font-weight: 700;
            color: var(--md-sys-color-primary);
        }
        .${serviceCardProvider} {
            display: inline-flex;
            align-items: center;
            flex-wrap: wrap;
            gap: 8px;
            font-size: 14px;
            cursor: pointer;
        }
        .${serviceCardProviderLabel} {
            font-weight: 700;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${serviceCardProviderName} {
            font-weight: 700;
            color: var(--md-sys-color-primary);
        }
        .${serviceCardDescription} {
            margin: 0;
            font-size: 14px;
            line-height: 1.45;
            color: var(--md-sys-color-on-surface-variant);
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .${serviceCardRating} {
            margin: 0;
            font-size: 14px;
            font-weight: 700;
            color: var(--md-sys-color-primary);
        }
        .${serviceCardAction} {
            margin-top: 4px;
        }
        .${serviceCardAction} .${btnPrimary} {
            padding: 14px 20px;
            font-size: 15px;
        }

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
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            cursor: pointer;
            scroll-snap-align: start;
            transition: background-color 0.2s, transform 0.1s, box-shadow 0.2s;
        }
        .${pinnedCard}:hover { background-color: var(--md-sys-color-surface-variant); box-shadow: 0 0 16px -4px #ffffff36; }
        .${pinnedCard}:active { transform: scale(0.97); }
        /* Service card with a generated cover image. The cover — including its
           50% surface veil and the light border/glow — is rendered entirely by
           the reusable fade-in layer (see .${coverImage}.${coverImageVeiled}),
           which sits at the back; the icon/label just sit on top of it. A
           missing/broken URL simply falls back to the base card colour (no
           broken-image glyph). */
        .${pinnedCard}.${pinnedCardCovered} {
            background-color: var(--md-sys-color-surface);
            border: none;
        }
        .${pinnedCard}.${pinnedCardCovered}:hover .${coverImage} { filter: brightness(1.6); }
        .${card}.${cardCovered} .${pinnedCardIcon},
        .${pinnedCard}.${pinnedCardCovered} .${pinnedCardIcon},
        .${pinnedCard}.${pinnedCardCovered} .${pinnedCardLabel} {
            position: relative;
            z-index: 1;
        }
        .${pinnedCardIcon} {
            width: 48px;
            height: 48px;
            margin-bottom: 16px;
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
            z-index: 1;
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

        /* ---- Phrases ---- */
        .${phraseLocationHeader} {
            display: flex;
            align-items: center;
            gap: 8px;
            margin: 20px 0 8px;
        }
        .${phraseLocationHeader} .${sectionTitle} {
            margin: 0;
        }
        .${phraseLocationHeader} .${materialSymbols} {
            color: var(--md-sys-color-primary);
            font-size: 22px;
        }
        .${phraseCard} {
            position: relative;
            display: flex;
            flex-direction: column;
            gap: 8px;
            padding: 16px;
            margin-bottom: 12px;
            border-radius: 28px; // Must be the same as `cover-image-veiled`
            background: var(--md-sys-color-surface);
            border: 1px solid #ffffff16;
            box-shadow: 0 8px 24px -18px var(--md-shadow);
            cursor: pointer;
            overflow: hidden;
            transition: transform 0.12s ease, box-shadow 0.2s ease;
        }
        .${phraseCard}:active { transform: scale(0.99); }
        .${phraseCard}.${phraseCardCovered} {
            min-height: 160px;
            border: none;
        }
        .${phraseCardBody} {
            position: relative;
            z-index: 1;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .${phraseLocationTag} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 12px;
            font-weight: 600;
            color: var(--md-sys-color-on-surface-variant);
            text-transform: uppercase;
            letter-spacing: 0.04em;
        }
        .${phraseLocationIcon} { font-size: 16px !important; }
        .${phraseSpoken} {
            margin: 0;
            font-size: 18px;
            line-height: 1.35;
            font-weight: 650;
            color: var(--md-sys-color-on-surface);
            font-style: italic;
        }
        .${phraseRawAsk} {
            margin: 0;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${phraseVerseBlock} {
            margin-top: 12px;
        }

        .${phraseVerse} {
            margin: 4px 0 0;
            font-size: 14px;
            line-height: 1.5;
            color: var(--md-sys-color-on-surface);
            background: color-mix(in srgb, var(--md-sys-color-primary-container) 45%, transparent);
            border-radius: 14px;
            padding: 12px;
        }
        .${phraseExpandHint} {
            font-size: 12px;
            color: var(--md-sys-color-primary);
            font-weight: 600;
        }
        .${phraseReactions} {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            margin-top: 4px;
        }
        .${phraseReactionBtn} {
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface-variant);
            color: var(--md-sys-color-on-surface);
            border-radius: 999px;
            padding: 4px 10px;
            font-size: 13px;
            cursor: pointer;
        }
        .${phraseReactionBtn}:hover {
            background: var(--md-sys-color-primary-container);
        }

        .${phrasesOriginStory} {
            margin: 28px 4px 12px 4px;
            padding: 0 4px;
            font-size: 14px;
            line-height: 1.55;
            font-style: italic;
            opacity: 0.5;
            color: var(--md-sys-color-on-surface);
        }

        /* ---- Admin ---- */
        .${adminToolbar} {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            margin-bottom: 12px;
            flex-wrap: wrap;
        }
        .${adminStatsCards} {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 10px;
            margin: 4px 0 16px;
        }
        .${adminStatsCard} {
            display: flex;
            flex-direction: column;
            gap: 2px;
            padding: 14px 16px;
            border-radius: 16px;
            background: var(--md-sys-color-surface);
            border: 1px solid #ffffff16;
        }
        .${adminStatsCardLabel} {
            font-size: 12px;
            font-weight: 600;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${adminStatsCardValue} {
            font-size: 28px;
            font-weight: 750;
            line-height: 1.15;
            font-variant-numeric: tabular-nums;
            color: var(--md-sys-color-on-surface);
        }
        .${adminStatsCardHint} {
            font-size: 12px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${adminStatsTimezone} {
            font-size: 12px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${adminStatsChart} {
            margin: 4px 0 16px;
            padding: 12px 10px 8px;
            border-radius: 16px;
            background: var(--md-sys-color-surface);
            border: 1px solid #ffffff16;
        }
        .${adminStatsSvg} {
            display: block;
            width: 100%;
            height: 180px;
        }
        .${adminStatsBar} {
            fill: color-mix(in srgb, var(--md-sys-color-primary) 55%, transparent);
            cursor: pointer;
        }
        .${adminStatsBar}:hover,
        .${adminStatsBarActive} {
            fill: var(--md-sys-color-primary);
        }
        .${adminStatsAxis} {
            display: flex;
            justify-content: space-between;
            gap: 2px;
            margin-top: 6px;
        }
        .${adminStatsAxisLabel} {
            flex: 1;
            min-width: 0;
            text-align: center;
            font-size: 10px;
            font-variant-numeric: tabular-nums;
            color: var(--md-sys-color-on-surface-variant);
            overflow: hidden;
            text-overflow: clip;
            white-space: nowrap;
        }
        .${adminStatsAxisLabelActive} {
            color: var(--md-sys-color-primary);
            font-weight: 700;
        }
        .${adminStatsAxisLabelHidden} {
            color: transparent;
        }
        .${adminStatsLegend} {
            display: flex;
            gap: 14px;
            margin-top: 8px;
            font-size: 12px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${adminStatsLegendVisitors}::before,
        .${adminStatsLegendVisits}::before {
            content: "";
            display: inline-block;
            width: 10px;
            height: 10px;
            margin-right: 6px;
            border-radius: 3px;
            vertical-align: -1px;
        }
        .${adminStatsLegendVisitors}::before {
            background: var(--md-sys-color-primary);
        }
        .${adminStatsLegendVisits}::before {
            background: var(--md-sys-color-success);
            border-radius: 99px;
            height: 3px;
            vertical-align: 2px;
        }
        .${adminStatsSelected} {
            margin: 0 0 16px;
            padding: 12px 14px;
            border-radius: 16px;
            background: var(--md-sys-color-primary-container);
            color: var(--md-sys-color-on-primary-container);
        }
        .${adminStatsSelected} h2 {
            margin: 0 0 4px;
            font-size: 15px;
        }
        .${adminStatsSelected} p {
            margin: 0;
            font-size: 13px;
        }
        .${adminActions} {
            margin-bottom: 12px;
        }
        .${adminRowActions} {
            display: flex;
            flex-direction: column;
            gap: 4px;
            margin-left: auto;
        }
        .${adminPhotoCard} {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px;
            margin-bottom: 10px;
            border-radius: 16px;
            background: var(--md-sys-color-surface);
            border: 1px solid #ffffff16;
        }
        .${adminChallengeRow} {
            flex-direction: column;
            align-items: stretch;
        }
        .${adminChallengeVideo} {
            width: 100%;
            max-height: 320px;
            border-radius: 12px;
            background: #000;
            object-fit: contain;
        }
        .${adminPhotoThumb} {
            width: 64px;
            height: 64px;
            border-radius: 12px;
            object-fit: cover;
            flex: 0 0 auto;
            background: var(--md-sys-color-surface-variant);
        }

        .${adminUploadBlock} {
            margin: 8px 0 20px 0;
        }
        .${adminCoverCard} {
            display: flex;
            flex-direction: column;
            gap: 12px;
            padding: 16px;
            border-radius: 20px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 6%, var(--md-sys-color-surface));
            border: 1px solid var(--md-sys-color-outline, var(--md-sys-color-outline));
            margin-bottom: 16px;
        }
        .${adminCoverPreview} {
            width: 100%;
            object-fit: cover;
            border-radius: 16px;
            display: block;
        }
        .${adminLocationSuggestions} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin: -4px 0 12px 0;
        }
        .${adminIconField} {
            position: relative;
            display: flex;
            align-items: center;
        }
        .${adminIconInput} {
            padding-right: 52px;
        }
        .${adminIconPreview} {
            position: absolute;
            right: 14px;
            top: 50%;
            transform: translateY(-50%);
            display: inline-flex;
            color: var(--md-sys-color-primary);
            pointer-events: none;
        }
        .${adminPhraseThumb} {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            object-fit: cover;
            flex-shrink: 0;
            background: var(--ov-surface-2, rgba(0,0,0,0.06));
        }

        .${adminRefGrid} {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 10px;
            margin-top: 8px;
        }
        .${adminRefTile} {
            display: flex;
            flex-direction: column;
            gap: 6px;
            align-items: stretch;
            text-align: left;
            padding: 8px;
            border-radius: 16px;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface);
            color: inherit;
            font: inherit;
            cursor: pointer;
        }
        .${adminRefTile}.${adminRefTileSelected} {
            border-color: var(--md-sys-color-primary);
            box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-sys-color-primary) 35%, transparent);
        }
        .${adminRefTile} .${photoThumbButton} {
            width: 100%;
        }
        .${adminRefThumb} {
            width: 100%;
            object-fit: cover;
            border-radius: 12px;
            display: block;
        }
        .${phraseSpokenLabel} {
            display: block;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.4px;
            text-transform: uppercase;
            color: var(--md-sys-color-primary);
            margin: 8px 0 2px 0;
        }
        .${phraseInstructions} {
            margin-top: 12px;
            padding: 12px 14px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-tertiary, var(--md-sys-color-primary)) 10%, transparent);
        }
        .${phraseInstructionsLabel} {
            display: block;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.4px;
            margin-bottom: 4px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${phraseInstructions} p {
            margin: 0;
            font-size: 14px;
            line-height: 1.45;
        }
        .${chatEmptyLead} {
            margin: 0 0 12px 0;
            text-align: center;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 15px;
            line-height: 1.45;
        }
        .${chatEmptyActions} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            width: 100%;
            margin-bottom: 8px;
        }
        .${chatEmptyOr} {
            display: block;
            width: 100%;
            text-align: center;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            color: var(--md-sys-color-on-surface-variant);
            opacity: 0.75;
            margin: 4px 0 2px 0;
        }
        .${chatExploreCard} {
            display: flex;
            align-items: center;
            gap: 12px;
            width: 100%;
            box-sizing: border-box;
            text-align: left;
            padding: 14px 16px;
            border-radius: 18px;
            border: 1px solid var(--md-sys-color-outline);
            background: color-mix(in srgb, var(--md-sys-color-primary) 8%, var(--md-sys-color-surface));
            color: var(--md-sys-color-on-surface);
            font: inherit;
            cursor: pointer;
            background-image: linear-gradient(to bottom right, #ffffff1c, transparent);
        }
        .${chatExploreCard} > div {
            display: flex;
            flex-direction: column;
            gap: 2px;
            flex: 1 1 auto;
            min-width: 0;
        }
        .${chatExploreTitle} {
            font-weight: 700;
            font-size: 15px;
        }
        .${chatExploreSub} {
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${chatMessageMeta} {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .${chatBookmarkBtn} {
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface-variant);
            padding: 2px;
            margin: 0;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border-radius: 999px;
        }
        .${chatBookmarkBtn}:hover {
            color: var(--md-sys-color-primary);
            background: color-mix(in srgb, var(--md-sys-color-primary) 10%, transparent);
        }
        .${chatBookmarkBtn} .${materialSymbols} {
            font-size: 18px;
        }
        /* Saved-message list body inside BottomDialog, and study-session list
           on the Study screen. Do not nest a second capped scroll region. */
        .${savedPanelBody} {
            display: flex;
            flex-direction: column;
            gap: 12px;
            padding-bottom: 8px;
        }
        .${studyExactRow} {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: -4px;
            cursor: pointer;
            font-size: 14px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${savedPanelEmpty} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 10px;
            text-align: center;
            padding: 36px 12px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${savedPanelEmpty} p {
            margin: 0;
            max-width: 28ch;
            line-height: 1.45;
        }
        .${savedItem} {
            display: flex;
            flex-direction: column;
            gap: 8px;
            padding: 14px;
            border-radius: 18px;
            border: 1px solid var(--md-sys-color-outline);
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${savedItemSender} {
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.3px;
            text-transform: uppercase;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${savedItemText} {
            margin: 0;
            font-size: 15px;
            line-height: 1.45;
            white-space: pre-wrap;
            word-break: break-word;
        }
        .${savedItemActions} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-top: 2px;
        }
        .${savedItemAction} {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            border-radius: 999px;
            padding: 6px 12px;
            font: inherit;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
        }
        .${savedItemAction} .${materialSymbols} {
            font-size: 16px;
        }
        .${savedItemActionDanger} {
            color: var(--md-sys-color-error, #b3261e);
            border-color: color-mix(in srgb, var(--md-sys-color-error, #b3261e) 35%, var(--md-sys-color-outline));
        }
        .${studySessionPhraseRow} {
            display: flex;
            flex-direction: row;
            gap: 8px;
            overflow-x: auto;
            margin: -2px -2px 2px;
            padding: 2px;
        }
        .${studySessionPhraseImage} {
            flex: 0 0 auto;
            width: 72px;
            height: 72px;
            border-radius: 12px;
            object-fit: cover;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface-variant);
        }
        .${studySessionSectionTitle} {
            margin: 16px 0 0;
            font-size: 15px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
        }
        .${studyWordsTable} {
            width: 100%;
            border-collapse: collapse;
        }
        .${studyWordsTable} th,
        .${studyWordsTable} td {
            padding: 9px 12px;
            text-align: left;
        }
        .${studyWordsWord} {
            padding: 4px 0;
            border: 0;
            background: transparent;
            color: var(--md-sys-color-primary);
            font: inherit;
            font-weight: 600;
            cursor: pointer;
            text-align: left;
        }
        .${studySessionCard} {
            position: relative;
            border: none;
        }
        .${studySessionCardTitle} {
            margin: 0 88px 8px 0;
            font-size: 17px;
            line-height: 1.35;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
            overflow-wrap: anywhere;
        }
        .${studySessionExamGradeBadge} {
            display: inline-flex;
            align-items: center;
            align-self: flex-start;
            gap: 4px;
            width: fit-content;
            margin: 0 0 8px;
            padding: 4px 9px;
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 9%, var(--md-sys-color-surface));
            color: var(--md-sys-color-primary);
            font-size: 12px;
            font-weight: 700;
            font-variant-numeric: tabular-nums;
        }
        .${studySessionExamGradeBadge} .${materialSymbols} {
            font-size: 14px;
        }
        .${studySessionHeaderExamGradeBadge} {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-width: 42px;
            height: 30px;
            margin: 0 4px;
            padding: 0 8px;
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 9%, var(--md-sys-color-surface));
            color: var(--md-sys-color-primary);
            font-size: 12px;
            font-weight: 700;
            font-variant-numeric: tabular-nums;
        }
        .${studySessionCardActions} {
            position: absolute;
            top: 8px;
            right: 8px;
            z-index: 1;
            display: flex;
            gap: 4px;
        }
        .${studySessionRemoveBtn} {
            width: 36px;
            height: 36px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 0;
            border: none;
            border-radius: 50%;
            background: color-mix(in srgb, var(--md-sys-color-surface) 88%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            box-shadow: 0 1px 6px color-mix(in srgb, var(--md-sys-color-shadow, #000) 22%, transparent);
            cursor: pointer;
        }
        .${studySessionCardActions} .${studySessionRemoveBtn}:last-child {
            color: var(--md-sys-color-error, #b3261e);
        }
        .${studySessionRemoveBtn} .${materialSymbols} {
            font-size: 20px;
        }
        .${savedItem} .${btnPrimary} {
            width: 100%;
        }
        /* Study session content scrolls with the scaffold pane. */
        .${studySessionBody} {
            display: flex;
            flex-direction: column;
            gap: 14px;
            padding-bottom: 8px;
        }
        .${studyCreationProgress} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 6%, var(--md-sys-color-surface));
        }
        .${studyCreationProgressHeader} {
            display: flex;
            align-items: baseline;
            justify-content: space-between;
            gap: 12px;
        }
        .${studyCreationProgressTitle} {
            font-size: 14px;
            font-weight: 700;
            color: var(--md-sys-color-on-surface);
        }
        .${studyCreationProgressPercent} {
            font-size: 13px;
            font-weight: 700;
            color: var(--md-sys-color-primary);
        }
        .${studyCreationProgressBar} {
            height: 6px;
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-outline) 35%, transparent);
            overflow: hidden;
        }
        .${studyCreationProgressFill} {
            height: 100%;
            border-radius: inherit;
            background: var(--md-sys-color-primary);
        }
        .${studyCreationProgressSteps} {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }
        .${studyCreationProgressStep} {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface);
        }
        .${studyCreationProgressStep} .${materialSymbols} {
            font-size: 18px;
        }
        .${studyCreationProgressStepRunning} .${materialSymbols} {
            animation: spin 1.2s linear infinite;
            color: var(--md-sys-color-primary);
        }
        .${studyCreationProgressStepDone} .${materialSymbols} {
            color: var(--md-sys-color-primary);
        }
        .${studyCreationProgressStepUpcoming} {
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyCreationProgressStepLabel} {
            flex: 1 1 auto;
        }
        .${studyCreationProgressStepCount} {
            font-variant-numeric: tabular-nums;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyModeTabs} {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 8px;
        }
        .${studyWorksheetPrint} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .${studyWorksheetPrint} .${materialSymbols} {
            font-size: 16px;
            line-height: 1;
        }
        .${studySearchToggle} {
            display: grid;
            flex: 0 0 40px;
            margin-left: auto;
            place-items: center;
            width: 40px;
            height: 40px;
            padding: 0;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 999px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface-variant);
            cursor: pointer;
        }
        .${studySearchToggle}.${chipActive} {
            border-color: var(--md-sys-color-primary);
            background: color-mix(in srgb, var(--md-sys-color-primary) 12%, var(--md-sys-color-surface));
            color: var(--md-sys-color-primary);
        }
        .${studySearchToggle}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${studySearchToggle} .${materialSymbols} { font-size: 20px; }
        .${studySearchPanel} {
            margin: 12px 0;
            padding: 14px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 16px;
            background: var(--md-sys-color-surface);
        }
        .${studySearchFields} {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 12px;
        }
        .${studySearchField} {
            display: flex;
            flex-direction: column;
            gap: 6px;
            min-width: 0;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
            font-weight: 600;
        }
        .${studySearchField} .${textInput} {
            padding: 12px;
            border-radius: 12px;
        }
        .${studyReadPlay} {
            align-self: stretch;
            justify-content: center;
            width: 100%;
            box-sizing: border-box;
        }
        .${studyReadBlock} {
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${studyReadSentence} {
            margin-bottom: 10px;
            line-height: 1.7;
        }
        .${studyReadSentence}:last-child {
            margin-bottom: 0;
        }
        .${studyReadBreak} {
            display: none;
        }
        .${studyWriteBlock} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${studyPracticeInputRow} {
            display: flex;
            align-items: stretch;
            gap: 8px;
        }
        .${studyPracticeInputRow} > :first-child {
            flex: 1;
            min-width: 0;
        }
        .${studyPracticeHint} {
            margin: -4px 0 0;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 12px;
            line-height: 1.4;
        }
        .${studyPracticeRevealButton} {
            display: grid;
            flex: 0 0 44px;
            place-items: center;
            min-height: 44px;
            padding: 0;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 14px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-primary);
            cursor: pointer;
        }
        .${studyPracticeRevealButton}:hover {
            background: color-mix(in srgb, var(--md-sys-color-primary) 8%, var(--md-sys-color-surface));
        }
        .${studyPracticeRevealButton}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${studyPracticeRevealButton} .${materialSymbols} {
            font-size: 20px;
        }
        .${studyWritePrompt} {
            margin: 0;
            font-size: 18px;
            line-height: 1.45;
            font-weight: 600;
        }
        .${studySpeakBlock} {
            display: flex;
            flex-direction: column;
            gap: 12px;
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${studySpeakPrompt} {
            margin: 0;
            font-size: 18px;
            line-height: 1.45;
            font-weight: 600;
        }
        .${studySpeakPhases} {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 8px;
        }
        .${studySpeakPhase} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 999px;
            color: var(--md-sys-color-on-surface-variant);
            background: var(--md-sys-color-surface);
            font-size: 13px;
            font-weight: 600;
        }
        .${studySpeakPhaseActive} {
            border-color: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary-container);
            background: var(--md-sys-color-primary-container);
        }
        .${studySpeakRound} {
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
            font-variant-numeric: tabular-nums;
        }
        .${studySpeakActions} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
        .${studySpeakAttempts} {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
        .${studySpeakAttemptButton} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            min-height: 40px;
            padding: 8px 12px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 999px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-primary);
            cursor: pointer;
        }
        .${studySpeakAttemptButton}:hover {
            background: color-mix(in srgb, var(--md-sys-color-primary) 8%, var(--md-sys-color-surface));
        }
        .${studySpeakAttemptButton}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${studyRecallShell} {
            display: flex;
            flex-direction: column;
            gap: 16px;
            min-height: 300px;
            padding: 16px;
            overflow: hidden;
            border-radius: 24px;
            background: radial-gradient(circle at 50% 0%, #4f46e5 0, #18172d 48%, #10111b 100%);
            color: #fff;
            box-shadow: 0 16px 42px rgba(8, 8, 20, .28);
        }
        .${studyRecallFullscreen} {
            position: fixed;
            inset: 0;
            z-index: 70;
            width: 100%;
            height: 100vh;
            height: 100dvh;
            max-height: 100dvh;
            min-height: 0;
            box-sizing: border-box;
            padding: 14px max(14px, env(safe-area-inset-right)) max(14px, env(safe-area-inset-bottom)) max(14px, env(safe-area-inset-left));
            overflow: auto;
            border-radius: 0;
        }
        .${studyRecallHeader} {
            display: flex;
            align-items: center;
            gap: 10px;
            min-height: 42px;
        }
        .${studyRecallTitle} {
            flex: 1;
            font-size: 17px;
            font-weight: 800;
            letter-spacing: .03em;
        }
        .${studyRecallIconButton} {
            display: grid;
            place-items: center;
            min-width: 40px;
            min-height: 40px;
            padding: 0 10px;
            border: 1px solid rgba(255,255,255,.2);
            border-radius: 14px;
            background: rgba(255,255,255,.1);
            color: #fff;
            font: inherit;
            font-size: 20px;
            cursor: pointer;
            transition: transform 140ms ease, background 140ms ease;
        }
        .${studyRecallIconButton}:active { transform: scale(.94); }
        .${studyRecallIconButton}:hover { background: rgba(255,255,255,.18); }
        .${studyRecallBlock} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 12px;
            padding: 16px;
            border: 1px solid var(--md-sys-color-outline-variant);
            border-radius: 18px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
            text-align: center;
        }
        .${studyRecallFullscreen} .${studyRecallBlock} {
            flex: 1;
            justify-content: center;
            width: min(100%, 760px);
            min-height: 0;
            align-self: center;
            box-sizing: border-box;
            overflow: auto;
        }
        .${studyRecallImage} {
            width: min(100%, 320px);
            max-height: 260px;
            object-fit: cover;
            border-radius: 14px;
        }
        .${studyRecallPrompt}, .${studyRecallSource}, .${studyRecallAnswer}, .${studyRecallScore} {
            margin: 0;
        }
        .${studyRecallPrompt} {
            color: var(--md-sys-color-on-surface-variant);
            font-size: 15px;
            line-height: 1.45;
        }
        .${studyRecallSource}, .${studyRecallAnswer} {
            font-size: 24px;
            line-height: 1.4;
            font-weight: 700;
        }
        .${studyRecallTimer}, .${studyRecallAnswerLabel}, .${studyRecallProgress} {
            margin: 0;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 14px;
            font-variant-numeric: tabular-nums;
        }
        .${studyRecallTimer} {
            color: var(--md-sys-color-primary);
            font-size: 22px;
            font-weight: 700;
        }
        .${studyRecallActions} {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 10px;
        }
        .${studyRecallButton} {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            min-height: 44px;
            padding: 9px 16px;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 999px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-primary);
            font: inherit;
            font-weight: 700;
            cursor: pointer;
        }
        .${studyRecallButton}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${studyRecallCorrect} {
            border-color: color-mix(in srgb, #2e7d32 45%, var(--md-sys-color-outline));
            background: color-mix(in srgb, #2e7d32 14%, var(--md-sys-color-surface));
            color: #1b5e20;
        }
        .${studyRecallIncorrect}, .${studyRecallTimeUp} {
            border-color: color-mix(in srgb, #c62828 38%, var(--md-sys-color-outline));
            background: color-mix(in srgb, #c62828 12%, var(--md-sys-color-surface));
            color: #b71c1c;
        }
        .${studyRecallTimeUp} {
            margin: 0;
            padding: 7px 12px;
            border: 1px solid color-mix(in srgb, #c62828 38%, var(--md-sys-color-outline));
            border-radius: 999px;
            font-weight: 700;
        }
        .${studyRecallScore} {
            font-size: 22px;
            font-weight: 700;
        }
        .${studyWriteCorrect} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            align-self: flex-start;
            border-radius: 999px;
            padding: 6px 12px;
            font-size: 14px;
            font-weight: 700;
            background: color-mix(in srgb, #2e7d32 16%, var(--md-sys-color-surface));
            color: #1b5e20;
        }
        .${studyWriteFeedback} {
            margin: 0;
            font-size: 13px;
            line-height: 1.45;
            color: var(--md-sys-color-on-surface-variant);
            padding: 8px 10px;
            border-radius: 12px;
            background: color-mix(in srgb, var(--md-sys-color-tertiary) 10%, var(--md-sys-color-surface));
            border: 1px solid color-mix(in srgb, var(--md-sys-color-tertiary) 28%, var(--md-sys-color-outline));
        }
        .${studyWriteChecking} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyWriteChecking} .${materialSymbols} { animation: spin 1.2s linear infinite; }
        .${studyWriteCorrect} .${materialSymbols} {
            font-size: 18px;
            color: #2e7d32;
        }
        .${studyPlayBlock} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${studyPlayDescription} {
            margin: 0;
            font-size: 14px;
            line-height: 1.45;
            font-style: italic;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlayLine} {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding: 10px 12px;
            border-radius: 14px;
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, transparent);
        }
        .${studyPlayLineYou} {
            background: color-mix(in srgb, var(--md-sys-color-primary) 12%, var(--md-sys-color-surface));
        }
        .${studyPlayLineOther} {
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 70%, transparent);
        }
        .${studyPlaySpeaker} {
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.02em;
            text-transform: uppercase;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlayLine} p {
            margin: 0;
            font-size: 15px;
            line-height: 1.45;
        }
        .${studyPlayStatus} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlayStatus} .${materialSymbols} { animation: spin 1.2s linear infinite; }
        .${studySessionHint} {
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
            line-height: 1.4;
        }
        .${studySessionError} {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 10px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studySessionError} p {
            margin: 0;
            line-height: 1.45;
        }
        .${studySentenceBlock} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 12px;
            border-radius: 16px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 5%, var(--md-sys-color-surface));
        }
        .${studySentenceBlock} .${languageSentence} {
            margin-bottom: 0;
        }
        .${studySentenceImage} {
            display: block;
            width: 100%;
            border-radius: 14px;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface-variant);
            aspect-ratio: 1 / 1;
            object-fit: cover;
        }
        .${studySentenceImagePlaceholder} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            max-width: 420px;
            min-height: 160px;
            border-radius: 14px;
            border: 1px dashed color-mix(in srgb, var(--md-sys-color-outline) 70%, transparent);
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
        }
        .${studySentenceImageStatus} {
            display: inline-flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studySentenceImageStatusBusy} .${materialSymbols} {
            animation: spin 1.2s linear infinite;
        }
        .${studyTokenPlain} {
            display: inline-flex;
            align-items: center;
            padding: 6px 2px;
            font-size: 18px;
            line-height: 1.3;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyTokenPending} {
            display: inline-flex;
            align-items: center;
            border: 1px dashed color-mix(in srgb, var(--md-sys-color-outline) 70%, transparent);
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 45%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            border-radius: 10px;
            padding: 4px 8px;
            margin: 2px;
            font: inherit;
            font-size: 17px;
            line-height: 1.3;
            opacity: 0.78;
            cursor: default;
        }
        .${studySentenceError} {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-error, #b3261e);
        }
        .${studyPreparingMeanings} .${materialSymbols} { animation: spin 1.2s linear infinite; }
        .${studyPreparingMeanings} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyBreakdownButton} {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 7px;
            min-height: 40px;
            padding: 8px 13px;
            border: 1px solid color-mix(in srgb, var(--md-sys-color-primary) 30%, var(--md-sys-color-outline));
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-primary) 9%, var(--md-sys-color-surface));
            color: var(--md-sys-color-primary);
            font: inherit;
            font-weight: 650;
            cursor: pointer;
        }
        .${studyBreakdownSheet} {
            display: flex;
            flex-direction: column;
            gap: 14px;
            width: 100%;
            max-width: 720px;
            margin: 0 auto;
        }
        .${studyBreakdownSteps} {
            display: flex;
            gap: 7px;
            overflow-x: auto;
            padding: 2px 0 6px;
        }
        .${studyBreakdownStep} {
            flex: 1 0 auto;
            padding: 7px 10px;
            border-radius: 999px;
            background: var(--md-sys-color-surface-variant);
            color: var(--md-sys-color-on-surface-variant);
            font-size: 12px;
            text-align: center;
        }
        .${studyBreakdownStepActive} {
            background: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
            font-weight: 700;
        }
        .${studyBreakdownSentence} {
            margin: 4px 0;
            font-size: clamp(20px, 5vw, 30px);
            font-weight: 650;
            line-height: 1.45;
            text-align: center;
        }
        .${studyBreakdownPrompt} {
            margin: 0;
            color: var(--md-sys-color-on-surface-variant);
            text-align: center;
        }
        .${studyBreakdownListen} {
            margin: 4px 0 0;
            color: var(--md-sys-color-primary);
            font-size: 22px;
            font-weight: 750;
            line-height: 1.3;
        }
        .${studyBreakdownPhotos} {
            width: 100%;
            margin: 4px 0 8px;
            overflow-x: auto;
            scrollbar-width: none;
        }
        .${studyBreakdownPhotos}::-webkit-scrollbar {
            display: none;
        }
        .${studyBreakdownPhotoRow} {
            display: flex;
            align-items: center;
            justify-content: center;
            width: max-content;
            min-width: 100%;
            box-sizing: border-box;
            padding: 5px 12px;
        }
        .${studyBreakdownPhoto} {
            position: relative;
            width: 120px;
            height: 120px;
            flex: 0 0 72px;
            object-fit: cover;
            border: 3px solid var(--md-sys-color-surface);
            border-radius: 20px;
            box-shadow: 0 5px 13px rgba(0, 0, 0, .2);
        }
        .${studyBreakdownPhoto} + .${studyBreakdownPhoto} {
            margin-left: -16px;
        }
        .${studyBreakdownPhoto}:nth-of-type(4n + 1) { transform: rotate(-7deg); z-index: 1; }
        .${studyBreakdownPhoto}:nth-of-type(4n + 2) { transform: rotate(5deg); z-index: 2; }
        .${studyBreakdownPhoto}:nth-of-type(4n + 3) { transform: rotate(-3deg); z-index: 3; }
        .${studyBreakdownPhoto}:nth-of-type(4n) { transform: rotate(7deg); z-index: 4; }
        .${studyBreakdownChunk} {
            display: inline-flex;
            margin: 3px;
            padding: 7px 11px;
            border: 1px solid var(--md-sys-color-outline-variant);
            border-radius: 12px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            font: inherit;
            cursor: pointer;
        }
        .${studyBreakdownChunkActive} {
            border-color: var(--md-sys-color-primary);
            background: color-mix(in srgb, var(--md-sys-color-primary) 15%, var(--md-sys-color-surface));
            box-shadow: 0 0 0 2px color-mix(in srgb, var(--md-sys-color-primary) 24%, transparent);
        }
        .${studyBreakdownActions} {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 9px;
        }
        .${studyBreakdownFooter} {
            display: flex;
            width: 100%;
            gap: 9px;
        }
        .${studyBreakdownFooter} .${studyBreakdownButton} {
            flex: 1 1 0;
            min-width: 0;
        }
        .${studyBreakdownMeaning} {
            margin: 2px 0 0;
            font-size: 18px;
            font-weight: 750;
            line-height: 1.45;
            text-align: center;
            color: var(--md-sys-color-on-surface);
        }
        .${studyBreakdownYourTurn} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            margin: 4px 0 0;
            padding: 0;
            border: none;
            background: transparent;
            color: var(--md-sys-color-primary);
            font-size: 22px;
            font-weight: 750;
            line-height: 1.3;
            text-align: center;
            pointer-events: none;
            animation: study-breakdown-your-turn-pulse 1.7s ease-in-out infinite;
        }
        .${studyBreakdownYourTurn} .${materialSymbols} {
            font-size: 26px;
        }
        @keyframes study-breakdown-your-turn-pulse {
            0%, 100% { opacity: 0.48; transform: scale(0.97); }
            50% { opacity: 1; transform: scale(1.05); }
        }
        """.trimIndent() + """
        .study-adventure-shell {
            display: flex;
            flex-direction: column;
            gap: 16px;
            min-height: 420px;
            padding: 16px;
            overflow: hidden;
            border-radius: 24px;
            background: radial-gradient(circle at 50% 0%, #4f46e5 0, #18172d 48%, #10111b 100%);
            color: #fff;
            box-shadow: 0 16px 42px rgba(8, 8, 20, .28);
        }
        .study-adventure-shell.study-adventure-fullscreen {
            position: fixed;
            inset: 0;
            z-index: 70;
            width: 100%;
            height: 100vh;
            height: 100dvh;
            max-height: 100dvh;
            min-height: 0;
            box-sizing: border-box;
            padding: 14px max(14px, env(safe-area-inset-right)) max(14px, env(safe-area-inset-bottom)) max(14px, env(safe-area-inset-left));
            overflow: hidden;
            border-radius: 0;
        }
        .study-adventure-header {
            display: flex;
            align-items: center;
            gap: 10px;
            min-height: 42px;
        }
        .study-adventure-title {
            flex: 1;
            font-size: 17px;
            font-weight: 800;
            letter-spacing: .03em;
        }
        .study-adventure-icon-button {
            display: grid;
            place-items: center;
            min-width: 40px;
            min-height: 40px;
            padding: 0 10px;
            border: 1px solid rgba(255,255,255,.2);
            border-radius: 14px;
            background: rgba(255,255,255,.1);
            color: #fff;
            font: inherit;
            font-size: 20px;
            cursor: pointer;
            transition: transform 140ms ease, background 140ms ease;
        }
        .study-adventure-icon-button:active { transform: scale(.94); }
        .study-adventure-icon-button:hover { background: rgba(255,255,255,.18); }
        .study-adventure-hint {
            margin: 0;
            color: rgba(255,255,255,.7);
            font-size: 13px;
            line-height: 1.5;
            text-align: center;
        }
        .study-adventure-empty {
            display: grid;
            flex: 1;
            place-items: center;
            color: rgba(255,255,255,.72);
        }
        .study-adventure-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(min(150px, 42vw), 1fr));
            gap: 12px;
            overflow: auto;
            padding: 2px 2px 8px;
        }
        .study-adventure-level {
            display: flex;
            position: relative;
            align-items: flex-start;
            justify-content: space-between;
            min-height: 128px;
            padding: 14px;
            overflow: hidden;
            border: 1px solid rgba(255,255,255,.24);
            border-radius: 20px;
            background-color: #363553;
            background-position: center;
            background-size: cover;
            color: #fff;
            text-align: left;
            cursor: pointer;
            box-shadow: 0 8px 24px rgba(0,0,0,.22);
            transition: transform 160ms ease, border-color 160ms ease, filter 160ms ease;
            animation: adventure-pop 280ms ease both;
        }
        .study-adventure-level:hover { transform: translateY(-3px); border-color: rgba(255,255,255,.62); }
        .study-adventure-locked { filter: grayscale(.7) brightness(.62); cursor: not-allowed; }
        .study-adventure-level-number {
            font-size: 18px;
            font-weight: 900;
            text-shadow: 0 2px 10px rgba(0,0,0,.8);
        }
        .study-adventure-level-status {
            display: grid;
            place-items: center;
            width: 34px;
            height: 34px;
            border-radius: 50%;
            background: rgba(8,8,20,.48);
            font-size: 19px;
            font-weight: 800;
            text-shadow: 0 1px 5px rgba(0,0,0,.8);
        }
        .study-adventure-card {
            display: flex;
            flex: 1;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 18px;
            min-height: 280px;
            padding: 24px;
            border: 1px solid rgba(255,255,255,.15);
            border-radius: 24px;
            background: rgba(255,255,255,.08);
            text-align: center;
            backdrop-filter: blur(12px);
            animation: adventure-slide-in 300ms ease both;
        }
        .study-adventure-prompt-sentence {
            display: block;
            width: 100%;
            max-width: 900px;
            margin: 0;
            padding: 0;
            border: 0;
            background: transparent;
            color: inherit;
            font: inherit;
            font-size: clamp(24px, 5vw, 46px);
            font-weight: 800;
            line-height: 1.25;
            text-align: center;
            cursor: pointer;
        }
        .study-adventure-prompt-sentence:disabled {
            opacity: .6;
            cursor: wait;
        }
        .study-adventure-prompt-sentence:focus-visible {
            outline: 2px solid rgba(255,255,255,.8);
            outline-offset: 8px;
            border-radius: 4px;
        }
        .study-adventure-translation {
            margin: -8px 0 0;
            color: rgba(255,255,255,.76);
            font-size: clamp(14px, 2.4vw, 20px);
            line-height: 1.4;
            animation: adventure-slide-in 350ms ease 2s backwards;
        }
        .study-adventure-review { gap: 24px; }
        .study-adventure-review-sentence {
            max-width: 900px;
            margin: 0;
            font-size: clamp(24px, 5vw, 46px);
            font-weight: 800;
            line-height: 1.35;
        }
        .study-adventure-review-word {
            display: inline;
            padding: 0 2px;
            border: 0;
            border-radius: 4px;
            background: transparent;
            color: inherit;
            font: inherit;
            cursor: pointer;
            text-decoration: underline dotted rgba(255,255,255,.45);
            text-underline-offset: 5px;
        }
        .study-adventure-review-word:hover { color: #c4b5fd; }
        .study-adventure-stage {
            margin: 0;
            color: #c4b5fd;
            font-size: 12px;
            font-weight: 800;
            letter-spacing: .12em;
            text-transform: uppercase;
        }
        .study-adventure-primary {
            min-height: 48px;
            padding: 0 24px;
            border: 0;
            border-radius: 16px;
            background: linear-gradient(135deg, #a78bfa, #6366f1);
            color: #fff;
            font: inherit;
            font-size: 16px;
            font-weight: 800;
            cursor: pointer;
            box-shadow: 0 8px 24px rgba(99,102,241,.34);
            transition: transform 150ms ease, filter 150ms ease;
        }
        .study-adventure-primary:hover { transform: translateY(-2px); filter: brightness(1.08); }
        .study-adventure-primary:disabled { opacity: .48; cursor: wait; transform: none; }
        .study-adventure-play {
            display: flex;
            position: relative;
            isolation: isolate;
            flex: 1;
            flex-direction: column;
            justify-content: space-between;
            gap: 24px;
            min-height: 360px;
            padding: clamp(16px, 4vw, 32px);
            overflow: hidden;
            border: 1px solid rgba(255,255,255,.15);
            border-radius: 24px;
            background-color: #17172a;
            color: #fff;
        }
        .study-adventure-backdrop {
            position: absolute;
            z-index: -1;
            inset: -24px;
            background-position: center;
            background-size: cover;
            background-repeat: no-repeat;
            filter: blur(6px) brightness(.45) saturate(1.1);
            transform: scale(1.06);
            pointer-events: none;
        }
        .study-adventure-top {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .study-adventure-progress-track,
        .study-adventure-timer {
            width: 100%;
            height: 9px;
            overflow: hidden;
            border-radius: 999px;
            background: rgba(255,255,255,.2);
        }
        .study-adventure-progress-fill {
            height: 100%;
            border-radius: inherit;
            background: linear-gradient(90deg, #34d399, #a3e635);
            transition: width 180ms ease;
        }
        .study-adventure-words {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: clamp(8px, 2vw, 16px);
            margin: auto 0;
        }
        .study-adventure-word {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            max-width: 100%;
            padding: 6px 6px 10px;
            border: 0;
            border-radius: 14px;
            background: transparent;
            color: #fff;
            font: inherit;
            cursor: pointer;
            transition: transform 120ms ease, color 120ms ease;
            animation: adventure-pop 250ms ease both;
        }
        .study-adventure-word:active { transform: scale(.92); }
        .study-adventure-word-correct {
            color: #bbf7d0;
            animation: adventure-correct-pop 360ms ease both;
        }
        .study-adventure-word-text {
            font-size: clamp(23px, 5vw, 42px);
            font-weight: 800;
            line-height: 1.2;
            overflow-wrap: anywhere;
            text-shadow: 0 2px 12px rgba(0,0,0,.48);
        }
        .study-adventure-health { display: flex; gap: 3px; }
        .study-adventure-health-segment {
            display: block;
            width: 13px;
            height: 5px;
            border-radius: 999px;
            background: rgba(255,255,255,.2);
        }
        .study-adventure-health-filled { background: #a3e635; box-shadow: 0 0 8px rgba(163,230,53,.7); }
        .study-adventure-health-mastered .study-adventure-health-segment {
            animation: adventure-health-mastered 700ms ease-in-out both;
        }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(2) { animation-delay: 35ms; }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(3) { animation-delay: 70ms; }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(4) { animation-delay: 105ms; }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(5) { animation-delay: 140ms; }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(6) { animation-delay: 175ms; }
        .study-adventure-health-mastered .study-adventure-health-segment:nth-child(7) { animation-delay: 210ms; }
        .study-adventure-prompt {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 9px;
            width: min(100%, 560px);
            margin: 0 auto;
            padding: 18px;
            border: 1px solid rgba(255,255,255,.2);
            border-radius: 20px;
            background: rgba(12,12,25,.58);
            backdrop-filter: blur(10px);
            animation: adventure-slide-in 200ms ease both;
        }
        .study-adventure-prompt-word {
            margin: 0;
            font-size: clamp(18px, 2vw, 38px);
            font-weight: 800;
            text-align: center;
        }
        .study-adventure-timer-fill {
            height: 100%;
            border-radius: inherit;
            background: linear-gradient(90deg, #fb7185, #fbbf24);
            transition: width 100ms linear;
        }
        .study-adventure-correct {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            min-height: calc(clamp(24px, 5vw, 38px) * 1.2 + 18px);
            color: #bbf7d0;
            font-size: clamp(24px, 5vw, 38px);
            font-weight: 900;
            animation: adventure-burst 420ms ease both;
        }
        .study-adventure-correct-mark {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 1.1em;
            height: 1.1em;
            font-size: .9em;
            border-radius: 50%;
            background: #22c55e;
            color: #052e16;
        }
        .study-adventure-message { min-height: 240px; }
        .study-adventure-message-title {
            margin: 0;
            font-size: clamp(28px, 5vw, 50px);
            font-weight: 900;
            line-height: 1.15;
            animation: adventure-burst 420ms ease both;
        }
        @keyframes adventure-pop {
            from { opacity: 0; transform: scale(.92); }
            to { opacity: 1; transform: scale(1); }
        }
        @keyframes adventure-shake {
            20%, 60% { transform: translateX(-5px); }
            40%, 80% { transform: translateX(5px); }
        }
        @keyframes adventure-slide-in {
            from { opacity: 0; transform: translateY(12px); }
            to { opacity: 1; transform: translateY(0); }
        }
        @keyframes adventure-burst {
            0% { opacity: 0; transform: scale(.72); }
            70% { opacity: 1; transform: scale(1.08); }
            100% { transform: scale(1); }
        }
        @keyframes adventure-correct-pop {
            0% { transform: scale(.88); }
            65% { transform: scale(1.08); }
            100% { transform: scale(1); }
        }
        @keyframes adventure-health-mastered {
            0%, 100% { background-color: #a3e635; box-shadow: 0 0 8px rgba(163,230,53,.7); }
            45% { background-color: #fde047; box-shadow: 0 0 14px rgba(253,224,71,.95); }
        }
        @media (max-width: 560px) {
            .study-adventure-shell { min-height: 340px; padding: 12px; }
            .study-adventure-level { min-height: 105px; padding: 11px; }
            .study-adventure-play { min-height: 330px; padding: 16px; }
        }
        .study-adventure-shell.study-adventure-fullscreen .study-adventure-words {
            display: flex;
            flex-wrap: wrap;
            align-content: flex-start;
            align-items: flex-start;
            justify-content: center;
            gap: clamp(8px, 1.8vw, 14px);
            flex: 1 1 0;
            min-height: 0;
            margin: 0;
            padding: 4px 2px 8px;
            overflow-y: auto;
            overflow-x: hidden;
            overscroll-behavior: contain;
            -webkit-overflow-scrolling: touch;
            height: 0;
            flex-grow: 1;
        }
        .study-adventure-shell.study-adventure-fullscreen .study-adventure-prompt {
            flex-shrink: 0;
            margin-top: auto;
            margin-bottom: 0;
            width: min(100%, 560px);
            padding: 14px 18px;
            box-sizing: border-box;
        }
        """.trimIndent() + """
        .${studyStoryCard} {
            display: flex;
            flex-direction: column;
            align-items: stretch;
            gap: 16px;
            padding: 18px 16px 16px;
            border-radius: 22px;
            background:
                radial-gradient(circle at top, color-mix(in srgb, var(--md-sys-color-primary) 14%, transparent), transparent 58%),
                color-mix(in srgb, var(--md-sys-color-primary) 4%, var(--md-sys-color-surface));
            box-shadow: 0 10px 28px color-mix(in srgb, var(--md-shadow) 35%, transparent);
        }
        .${studyStoryHeader} {
            display: flex;
            align-items: center;
            gap: 8px;
            min-height: 40px;
        }
        .${studyStoryHeader} .${studyStoryProgress} {
            align-self: auto;
        }
        .${studyStoryFullscreen} {
            margin-left: auto;
            padding: 9px;
        }
        .${studyStoryShell}.${studyStoryCardFullscreen} {
            display: flex;
            flex-direction: column;
            position: fixed;
            inset: 0;
            z-index: 70;
            width: 100%;
            height: 100vh;
            box-sizing: border-box;
            padding: 12px 12px 16px;
            overflow: auto;
            background: var(--md-sys-color-surface);
        }
        .${studyStoryShell}.${studyStoryCardFullscreen} .${studyStoryCard} {
            flex: 1;
            min-height: 0;
            height: 100%;
            max-height: 100%;
            overflow: auto;
            border-radius: 0;
            box-sizing: border-box;
        }
        .${studyStoryProgress} {
            align-self: center;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            text-transform: uppercase;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyStoryImage} {
            display: block;
            width: 100%;
            max-width: 440px;
            margin: 0 auto;
            border-radius: 18px;
            border: 1px solid color-mix(in srgb, var(--md-sys-color-outline) 70%, transparent);
            background: var(--md-sys-color-surface-variant);
            aspect-ratio: 1 / 1;
            object-fit: cover;
            box-shadow: 0 12px 30px color-mix(in srgb, var(--md-shadow) 28%, transparent);
            cursor: pointer;
        }
        .${studyStoryImagePlaceholder} {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            max-width: 440px;
            min-height: 220px;
            margin: 0 auto;
            border-radius: 18px;
            border: 1px dashed color-mix(in srgb, var(--md-sys-color-outline) 70%, transparent);
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
        }
        .${studyStoryImagePlaceholder} .${materialSymbols} {
            animation: spin 1.2s linear infinite;
        }
        .${studyStoryWord} {
            margin: 0;
            text-align: center;
            font-size: 34px;
            line-height: 1.5;
            font-weight: 750;
            letter-spacing: -0.02em;
            color: var(--md-sys-color-on-surface);
            cursor: pointer;
        }
        .${studyStorySentence} {
            margin: 0;
            text-align: center;
            font-size: 24px;
            line-height: 1.45;
            font-weight: 650;
            color: var(--md-sys-color-on-surface);
            cursor: pointer;
        }
        .${studyStoryMeaningLabel} {
            display: block;
            margin: 4px 0 2px;
            text-align: center;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 0.05em;
            text-transform: uppercase;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyStoryMeaning} {
            margin: 0 0 6px;
            text-align: center;
            font-size: 17px;
            line-height: 1.5;
            color: var(--md-sys-color-on-surface);
        }
        .${studyStoryActions} {
            display: flex;
            gap: 10px;
            margin-top: 4px;
        }
        .${studyStoryActions} > button {
            flex: 1 1 0;
            min-width: 0;
            justify-content: center;
            min-height: 48px;
            border-radius: 999px;
            font-size: 16px;
            font-weight: 700;
        }
        .${studyStoryContinue} {
            width: 100%;
        }
        .${studyWatchPlayer} {
            display: flex;
            flex-direction: column;
            align-items: stretch;
            gap: 10px;
            padding: 0 0 16px;
            border-radius: 22px;
            background:
                radial-gradient(circle at top, color-mix(in srgb, var(--md-sys-color-primary) 14%, transparent), transparent 58%),
                color-mix(in srgb, var(--md-sys-color-primary) 4%, var(--md-sys-color-surface));
            box-shadow: 0 10px 28px color-mix(in srgb, var(--md-shadow) 35%, transparent);
        }
        .${studyWatchStage} {
            position: relative;
            width: 100%;
            aspect-ratio: 16 / 9;
            overflow: hidden;
            border-radius: 21px 21px 0 0;
            background: var(--md-sys-color-surface);
        }
        .${studyWatchImage} {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            display: block;
            object-fit: contain;
            object-position: center;
            border: none;
        }
        .${studyWatchImage}.${studyWatchImageBkg} {
            object-fit: cover;
            filter: blur(8px) brightness(1.25);
            scale: 1.05;
        }
        @media (prefers-color-scheme: dark) {
            .${studyWatchImage}.${studyWatchImageBkg} {
                filter: blur(8px) brightness(.75);
            }
        }
        .${studyWatchStageStatus} {
            position: absolute;
            inset: 0;
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: 10px;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 70%, transparent);
        }
        .${studyWatchControls} {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 0 16px;
        }
        .${studyWatchControls} .${sentencePlay} {
            flex: 0 0 auto;
            white-space: nowrap;
        }
        .${studyWatchPlayer} .${studyPreparingMeanings} {
            padding: 0 16px;
        }
        .${studyWatchSeek} {
            position: relative;
            width: 100%;
            flex: none;
            height: 28px;
            display: flex;
            align-items: center;
            cursor: pointer;
            touch-action: none;
            user-select: none;
            padding: 0 16px;
        }
        .${studyWatchSeekTrack} {
            position: relative;
            width: 100%;
            height: 6px;
            border-radius: 99px;
            background: color-mix(in srgb, var(--md-sys-color-outline) 55%, var(--md-sys-color-surface-variant));
        }
        .${studyWatchSeekFill} {
            position: absolute;
            left: 0;
            top: 0;
            bottom: 0;
            border-radius: 99px;
            background: var(--md-sys-color-primary);
        }
        .${studyWatchSeekMarker} {
            position: absolute;
            top: 50%;
            width: 2px;
            height: 12px;
            transform: translate(-50%, -50%);
            background: var(--md-sys-color-on-surface);
            opacity: 0.55;
            pointer-events: none;
        }
        .${studyWatchSeekThumb} {
            position: absolute;
            top: 50%;
            width: 14px;
            height: 14px;
            border-radius: 50%;
            transform: translate(-50%, -50%);
            background: var(--md-sys-color-primary);
            box-shadow: 0 0 0 3px color-mix(in srgb, var(--md-sys-color-primary) 22%, transparent);
            pointer-events: none;
        }
        .${studyWatchTime} {
            flex: 0 0 auto;
            margin-left: auto;
            font-size: 12px;
            font-variant-numeric: tabular-nums;
            color: var(--md-sys-color-on-surface-variant);
            white-space: nowrap;
        }
        .${studyWatchFullscreen} {
            padding: 9px;
        }
        .${studyWatchPlayer}.${studyWatchPlayerFullscreen} {
            position: fixed;
            inset: 0;
            z-index: 70;
            width: 100%;
            height: 100%;
            max-width: none;
            box-sizing: border-box;
            border-radius: 0;
            border: none;
            padding: 12px 0 16px;
            box-shadow: none;
        }
        .${studyWatchPlayer}.${studyWatchPlayerFullscreen} .${studyWatchStage} {
            flex: 1 1 auto;
            aspect-ratio: auto;
            min-height: 0;
            border-radius: 0;
        }
        .${studyTokenDetail} {
            margin-top: 2px;
        }
        .${studyWordImage} {
            display: block;
            width: 100%;
            border-radius: 14px;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface-variant);
            aspect-ratio: 1 / 1;
            object-fit: cover;
            margin: 8px 0;
            max-width: 16rem;
        }
        .${studyWordImagePlaceholder} {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            width: 100%;
            max-width: 280px;
            min-height: 120px;
            border-radius: 14px;
            border: 1px dashed color-mix(in srgb, var(--md-sys-color-outline) 70%, transparent);
            background: color-mix(in srgb, var(--md-sys-color-surface-variant) 55%, transparent);
            color: var(--md-sys-color-on-surface-variant);
            font-size: 13px;
            margin-bottom: 8px;
        }
        .${studyWordImagePlaceholder} .${materialSymbols} {
            animation: spin 1.2s linear infinite;
        }
        .${studyExampleSentence} {
            margin: 0 0 10px;
            padding-left: 10px;
            border-left: 3px solid color-mix(in srgb, var(--md-sys-color-primary) 45%, transparent);
            cursor: pointer;
        }
        .${studyExampleSentenceLoading} {
            animation: study-example-alpha-pulse 0.9s ease-in-out infinite;
        }
        @keyframes study-example-alpha-pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.35; }
        }
        .${studyExampleSentence} p {
            margin: 0 !important;
            line-height: 1.4;
        }
        .${studyExampleWord} {
            font-weight: 700;
        }
        .${studyExampleTranslation} {
            margin-top: 2px !important;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlayground} {
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-top: 12px;
            padding-top: 10px;
            border-top: 1px dashed color-mix(in srgb, var(--md-sys-color-outline) 55%, transparent);
        }
        .${studyPlaygroundHint} {
            margin: 0 !important;
            font-size: 13px;
            line-height: 1.4;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlaygroundRow} {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .${studyPlaygroundInput} {
            flex: 1;
            min-width: 0;
            box-sizing: border-box;
            border: 1px solid var(--md-sys-color-outline);
            border-radius: 12px;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            padding: 10px 12px;
            font: inherit;
            font-size: 14px;
        }
        .${studyPlaygroundInput}:focus {
            outline: 2px solid var(--md-sys-color-primary);
            border-color: transparent;
        }
        .${studyPlaygroundInput}:disabled {
            opacity: 0.7;
        }
        .${studyPlaygroundSend} {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 40px;
            height: 40px;
            border: none;
            border-radius: 999px;
            background: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
            cursor: pointer;
            flex-shrink: 0;
        }
        .${studyPlaygroundSend}:disabled {
            opacity: 0.45;
            cursor: default;
        }
        .${studyPlaygroundSend} .${materialSymbols} {
            font-size: 20px;
        }
        .${studyPlaygroundError} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            color: var(--md-sys-color-error, #b3261e);
        }
        .${studyPlaygroundResult} {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .${studyPlaygroundBadge} {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            align-self: flex-start;
            border-radius: 999px;
            padding: 4px 10px;
            font-size: 13px;
            font-weight: 700;
        }
        .${studyPlaygroundBadge} .${materialSymbols} {
            font-size: 16px;
        }
        .${studyPlaygroundBadgeNatural} {
            background: color-mix(in srgb, #2e7d32 16%, var(--md-sys-color-surface));
            color: #1b5e20;
        }
        .${studyPlaygroundBadgeUnnatural} {
            background: color-mix(in srgb, var(--md-sys-color-error, #b3261e) 14%, var(--md-sys-color-surface));
            color: var(--md-sys-color-error, #b3261e);
        }
        .${studyPlaygroundSummary} {
            margin: 0 !important;
            font-size: 14px;
            line-height: 1.45;
        }
        .${studyPlaygroundImage} {
            display: block;
            width: 100%;
            max-width: 360px;
            border-radius: 14px;
            border: 1px solid var(--md-sys-color-outline);
            background: var(--md-sys-color-surface-variant);
            aspect-ratio: 1 / 1;
            object-fit: cover;
        }
        .${studyPlaygroundImageStatus} {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${studyPlaygroundImageStatusBusy} .${materialSymbols},
        .${studyPlaygroundSendBusy} .${materialSymbols} {
            animation: spin 1.2s linear infinite;
        }
        .${storiesSoon} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 12px;
            padding: 24px 8px 40px;
            text-align: center;
            height: 100%;
            justify-content: center;
            align-items: center;
        }
        .${storiesGrid} {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
            margin: 8px 0 24px;
        }
        .${storyCoverCard} {
            display: flex;
            flex-direction: column;
            align-items: stretch;
            gap: 8px;
            padding: 0;
            border: none;
            background: var(--md-sys-color-surface-container);
            box-shadow: 8px 8px 4px var(--md-shadow);
            border-radius: 16px;
            overflow: hidden;
            cursor: pointer;
            text-align: left;
            color: inherit;
        }
        .${storyCoverThumb}, .${storyCoverPlaceholder} {
            width: 100%;
            aspect-ratio: 3 / 4;
            object-fit: cover;
            background: var(--md-sys-color-surface-container-high);
        }
        .${storyCoverPlaceholder} {
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 36px;
            color: var(--md-sys-color-primary);
        }
        .${storyCoverTitle} {
            font-weight: 700;
            padding: 0 10px;
        }
        .${storyCoverCategory} {
            padding: 0 10px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${storyCoverMeta} {
            padding: 0 10px 12px;
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${storyReaderCover}, .${storyPagePhoto} {
            width: 100%;
            border-radius: 16px;
            object-fit: cover;
            margin-bottom: 12px;
        }
        .${storyReaderCover} {
            aspect-ratio: 3 / 4;
        }
        .${storyReaderTitle} {
            margin: 0 0 16px;
            font-size: 32px;
            font-weight: 700;
            line-height: 1.2;
            text-align: center;
        }
        .${storyLearningRow} {
            display: flex;
            align-items: center;
            gap: 8px;
            margin: 12px 0 16px;
            cursor: pointer;
        }
        .${storyReaderNav} {
            display: flex;
            align-items: center;
            gap: 12px;
            width: 100%;
        }
        .${storyReaderNav} .${btnPrimary} {
            flex: 1;
        }
        .${storyPageMeta} {
            font-size: 13px;
            color: var(--md-sys-color-on-surface-variant);
            margin: 0 0 8px;
        }
        .${storyPageText} {
            font-size: 24px;
            line-height: 1.65;
            white-space: pre-wrap;
        }
        .${storyStudyBlock} {
            margin-top: 20px;
            padding-top: 12px;
            border-top: 1px solid var(--md-sys-color-outline);
        }
        .${storiesSoon} h2 {
            margin: 0;
            font-size: 22px;
        }
        .${hiddenAdminWrap} {
            display: flex;
            justify-content: center;
            margin-top: 28px;
            opacity: 0.18;
        }
        .${hiddenAdminButton} {
            border: none;
            background: transparent;
            color: var(--md-sys-color-on-surface-variant);
            font-size: 18px;
            line-height: 1;
            padding: 12px 24px;
            cursor: default;
        }
        .${hiddenAdminButton}:hover {
            opacity: 1;
            cursor: pointer;
        }
        .${challengeStartCard} {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 10px;
            min-height: 180px;
            border: 2px solid var(--md-sys-color-primary-dark);
            background: linear-gradient(to bottom right, var(--md-sys-color-surface-container), var(--md-sys-color-primary-container));
            border-radius: 16px;
            cursor: pointer;
            color: inherit;
        }
        .${challengeCover} {
            position: relative;
            width: 100%;
            aspect-ratio: 1 / 1;
            overflow: hidden;
            background: #111;
        }
        .${challengeCoverMedia} {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            background: #111;
        }
        .${challengePickVideo} {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 10px;
            width: 100%;
            min-height: 132px;
            box-sizing: border-box;
            padding: 14px;
            border: 2px solid var(--md-sys-color-outline);
            border-radius: 16px;
            background: var(--md-sys-color-surface-container);
            cursor: pointer;
            text-align: center;
        }
        .${challengePickPreview} {
            width: 100%;
            max-height: 220px;
            border-radius: 12px;
            object-fit: cover;
            background: #000;
            display: block;
        }
        .${challengesReviewList} {
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin: 0 0 16px;
        }
        .${challengeReviewCard} {
            display: flex;
            gap: 12px;
            align-items: center;
            padding: 10px;
            border-radius: 14px;
            background: var(--md-sys-color-surface-container);
            overflow: hidden;
        }
        .${challengeReviewThumb} {
            position: relative;
            flex: 0 0 64px;
            width: 64px;
            height: 64px;
            border-radius: 10px;
            overflow: hidden;
            background: var(--md-sys-color-surface-container-high);
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .${challengeReviewThumbImg} {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            border-radius: 0;
        }
        .${challengeProgressTrack} {
            height: 8px;
            border-radius: 99px;
            background: var(--md-sys-color-surface-container-high);
            overflow: hidden;
            margin: 12px 0 6px;
        }
        .${challengeProgressFill} {
            height: 100%;
            background: var(--md-sys-color-primary);
            transition: width 180ms ease;
        }
        .${challengePlayer} {
            position: relative;
            display: flex;
            flex-direction: column;
            height: 100%;
            min-height: 100%;
            color: #fff;
            padding: 10px;
            box-sizing: border-box;
        }
        .${challengePlayerEmpty} {
            margin: auto;
            padding: 24px;
        }
        .${challengeStage} {
            position: relative;
            flex: 1;
            overflow: hidden;
            background: #000;
            background-size: cover;
            background-position: center;
            border-radius: 24px;
        }
        .${challengeVideoWrap}, .${challengeVideo} {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 24px;
            z-index: 1;
        }
        .${challengePreload} {
            position: absolute;
            inset: 0;
            width: 100%;
            height: 100%;
            overflow: hidden;
            opacity: 0;
            pointer-events: none;
            z-index: 0;
        }
        .${challengeFlyEmoji} {
            position: absolute;
            bottom: 28%;
            font-size: 28px;
            animation: challenge-fly 2.2s ease-out forwards;
            pointer-events: none;
            z-index: 3;
        }
        @keyframes challenge-fly {
            0% { transform: translateY(0) scale(0.8); opacity: 0; }
            12% { opacity: 1; }
            100% { transform: translateY(-220px) scale(1.15); opacity: 0; }
        }
        .${challengeClose} {
            position: absolute;
            top: 12px;
            left: 12px;
            z-index: 4;
            border: none;
            background: rgba(0,0,0,0.35);
            color: #fff;
            border-radius: 999px;
            width: 48px;
            height: 48px;
            line-height: 0;
        }
        .${challengeTopMeta} {
            position: absolute;
            top: 16px;
            right: 16px;
            z-index: 4;
            display: flex;
            gap: 8px;
            align-items: center;
        }
        .${challengeMedal} {
            padding: 4px 10px;
            border-radius: 999px;
            font-weight: 700;
            font-size: 13px;
        }
        .${challengeMedalGold} { background: #f5c518; color: #2b2100; }
        .${challengeMedalSilver} { background: #cfd4da; color: #222; }
        .${challengeMedalBronze} { background: #cd7f32; color: #fff; }
        .${challengeVoteCount} {
            font-size: 18px;
            font-weight: 700;
            text-shadow: 0 1px 6px rgba(0,0,0,0.6);
        }
        .${challengeName} {
            flex: 1;
            min-width: 0;
            font-size: 28px;
            line-height: 1.1;
            font-weight: 800;
            text-shadow: 0 1px 2px rgba(0,0,0,0.95);
        }
        .${challengeCaption} {
            font-size: 36px;
            text-align: center;
            font-weight: 700;
            text-shadow: 0 1px 2px rgba(0,0,0,0.95);
        }
        .${challengePauseHint} {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 64px;
            opacity: 0.8;
            pointer-events: none;
        }
        .${challengeBottom} {
            position: absolute;
            left: 0;
            right: 0;
            bottom: 0;
            z-index: 5;
            display: flex;
            flex-direction: column;
            align-items: stretch;
            gap: 10px;
            padding: 28px 16px 20px;
            background: linear-gradient(transparent, rgba(0,0,0,0.72));
            border-radius: 0 0 24px 24px;
            pointer-events: none;
        }
        .${challengeOverlayBar} {
            display: flex;
            align-items: flex-end;
            justify-content: space-between;
            gap: 12px;
        }
        .${challengeNavBtns} {
            display: flex;
            flex-direction: column;
            gap: 8px;
            flex: 0 0 auto;
            pointer-events: auto;
        }
        .${challengeDock} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 10px;
            pointer-events: auto;
        }
        .${challengeNextBtn} {
            width: 48px;
            height: 48px;
            border: none;
            border-radius: 999px;
            background: rgba(255,255,255,0.2);
            color: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            backdrop-filter: blur(8px);
            cursor: pointer;
            pointer-events: auto;
        }
        .${challengeEmojiRow} {
            display: flex;
            gap: 8px;
        }
        .${challengeEmojiBtn} {
            border: none;
            background: rgba(255,255,255,0.12);
            font-size: 24px;
            width: 44px;
            height: 44px;
            border-radius: 999px;
        }
        .${challengeEmojiBtnActive} {
            background: rgba(255,255,255,0.35);
        }
        .${challengeVoteBtn} {
            border: none;
            background: #fff;
            color: #111;
            border-radius: 999px;
            padding: 12px 22px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .${challengeVoteBtn}:disabled {
            opacity: .25;
        }
        .${challengeVoteBtnOn} {
            background: #ff4d6d;
            color: #fff;
        }
        .${meetCardBody} {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding: 4px 16px 16px;
            min-width: 0;
        }
        .${meetCardBody} .${storyCoverTitle},
        .${meetCardBody} .${storyCoverMeta} {
            padding: 0;
        }
        .${meetPitchClamp} {
            margin: 0;
            padding: 0;
            font-size: 13px;
            line-height: 1.4;
            color: var(--md-sys-color-on-surface-variant);
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
            overflow-wrap: anywhere;
            word-break: break-word;
            mask-image: linear-gradient(to right, #000 88%, transparent);
            -webkit-mask-image: linear-gradient(to right, #000 88%, transparent);
        }
        .${meetDetailPhoto} {
            display: block;
            width: 100%;
            aspect-ratio: 3 / 4;
            object-fit: cover;
            border-radius: 16px;
            margin-bottom: 12px;
            background: var(--md-sys-color-surface-container-high);
        }
        .${meetDetailPitch} {
            font-size: 24px;
        }
        .${meetPickPhoto} {
            display: block;
            width: 100%;
            max-height: 220px;
            object-fit: cover;
            border-radius: 12px;
        }
        .${meetAdminPhoto} {
            display: block;
            width: 100%;
            object-fit: cover;
            border-radius: 12px;
            margin-bottom: 8px;
            aspect-ratio: 16/9;
        }
        .${locationCard} {
            background-color: var(--md-sys-color-surface-variant);
            border-radius: 28px;
            padding: 12px;
            margin-bottom: 12px;
            display: flex;
            flex-direction: column;
            gap: 10px;
            cursor: pointer;
            border: 1px solid #ffffff16;
            box-shadow: 0 4px 8px -8px var(--md-shadow-light) inset;
            transition: background-color 0.2s, transform 0.1s, box-shadow 0.2s;
            user-select: none;
        }
        .${locationCardMain} {
            display: flex;
            align-items: stretch;
            gap: 12px;
            min-width: 0;
        }
        .${locationCard}:hover {
            background-color: var(--md-sys-color-primary-container);
            box-shadow: 0 0 16px -4px #ffffff36;
        }
        .${locationCard}:active { transform: scale(0.99); }
        .${locationCardPhotos} {
            position: relative;
            flex: 0 0 35%;
            width: 35%;
            min-width: 0;
            align-self: stretch;
            overflow: hidden;
            border-radius: 20px;
            background: var(--md-sys-color-surface-container-high);
        }
        .${appShell}.${layoutDesktop} .${locationCardPhotos} {
            flex-basis: 50%;
            width: 50%;
        }
        .${locationCardPhotosTrack} {
            position: absolute;
            inset: 0;
            display: flex;
            overflow-x: auto;
            overflow-y: hidden;
            scroll-snap-type: x mandatory;
            -webkit-overflow-scrolling: touch;
            scrollbar-width: none;
        }
        .${locationCardPhotosTrack}::-webkit-scrollbar { display: none; }
        .${locationCardPhoto} {
            flex: 0 0 100%;
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: center;
            scroll-snap-align: start;
            display: block;
        }
        .${locationCardPhotoFallback} {
            position: absolute;
            inset: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--md-sys-color-primary);
            font-size: 40px;
        }
        .${locationCardCategory} {
            position: absolute;
            left: 8px;
            top: 8px;
            z-index: 1;
            padding: 4px 8px;
            border-radius: 999px;
            background: color-mix(in srgb, var(--md-sys-color-surface) 82%, transparent);
            color: var(--md-sys-color-on-surface);
            font-size: 11px;
            font-weight: 600;
            pointer-events: none;
            backdrop-filter: blur(8px);
        }
        .${locationCardBody} {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }
        .${locationCardName} {
            margin: 0;
            font-size: 18px;
            line-height: 1.25;
        }
        .${locationCardType},
        .${locationCardMeta},
        .${locationCardHours} {
            margin: 0;
            font-size: 13px;
            line-height: 1.35;
            color: var(--md-sys-color-on-surface-variant);
        }
        .${locationCardHours} { font-weight: 600; color: var(--md-sys-color-primary); }
        .${locationHighlights} {
            display: flex;
            flex-direction: column;
            gap: 6px;
            width: 100%;
            margin: 4px 0;
        }
        .${locationHighlight} {
            display: flex;
            align-items: center;
            gap: 10px;
            width: 100%;
            margin: 0;
            padding: 10px 14px;
            border-radius: 16px;
            font-size: 14px;
            font-weight: 800;
            line-height: 1.3;
            letter-spacing: 0.01em;
            box-sizing: border-box;
        }
        .${locationHighlight} .${materialSymbols} {
            font-size: 18px;
            flex: 0 0 auto;
        }
        .${locationHighlightTone0} {
            background: linear-gradient(90deg, #ff8a6540, #ffcc8040);
            color: #c45c26;
        }
        .${locationHighlightTone1} {
            background: linear-gradient(90deg, #26a69a40, #80cbc440);
            color: #0f766e;
        }
        .${locationHighlightTone2} {
            background: linear-gradient(90deg, #7e57c240, #ce93d840);
            color: #6a1b9a;
        }
        @media (prefers-color-scheme: dark) {
            .${locationHighlightTone0} { color: #ffc59a; }
            .${locationHighlightTone1} { color: #7ee0d0; }
            .${locationHighlightTone2} { color: #e0b8ff; }
        }
        .${locationCardActions} {
            display: flex;
            gap: 8px;
            margin-top: auto;
            padding-top: 6px;
        }
        .${locationStatus} {
            align-self: flex-start;
            padding: 2px 8px;
            border-radius: 999px;
            font-size: 13px;
            font-weight: 700;
            letter-spacing: .02em;
        }
        .${locationStatusPlanning} { background: #f5d98a33; color: #c9a227; }
        .${locationStatusSoon} { background: #8ab4f533; color: #5b8def; }
        .${locationStatusOpen} { background: #7dcea033; color: #2e8b57; }
        .${locationStatusClosed} { background: #00000014; color: var(--md-sys-color-on-surface-variant); }
        .${locationHoursInline} {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
            margin: 8px 0;
        }
        .${locationHoursInline} .${locationCardHours} { margin: 0; }
        .${locationWeeklyHours} {
            display: flex;
            flex-direction: column;
            gap: 2px;
            width: 100%;
            margin: 0 0 12px;
            padding: 8px 12px;
            box-sizing: border-box;
            border-radius: 16px;
            background: var(--md-sys-color-surface-container-high);
        }
        .${locationWeeklyHoursRow} {
            display: flex;
            align-items: baseline;
            justify-content: space-between;
            gap: 12px;
            padding: 4px 0;
            font-size: 13px;
            line-height: 1.4;
        }
        .${locationWeeklyHoursDay} {
            font-weight: 600;
            min-width: 2.6em;
            color: var(--md-sys-color-on-surface);
        }
        .${locationWeeklyHoursTime} {
            color: var(--md-sys-color-on-surface-variant);
            text-align: right;
            font-variant-numeric: tabular-nums;
        }
        .${locationWeeklyHoursClosed} { color: var(--md-sys-color-outline); }
        .${locationWeeklyHoursToday} .${locationWeeklyHoursDay},
        .${locationWeeklyHoursToday} .${locationWeeklyHoursTime} {
            color: var(--md-sys-color-primary);
            font-weight: 700;
        }
        .${locationDetailPhotos} {
            display: flex;
            gap: 8px;
            overflow-x: auto;
            scroll-snap-type: x mandatory;
            -webkit-overflow-scrolling: touch;
            margin-bottom: 12px;
        }
        .${locationDetailPhoto} {
            flex: 0 0 1;
            width: 100%;
            aspect-ratio: 16 / 9;
            object-fit: cover;
            border-radius: 20px;
            scroll-snap-align: start;
            background: var(--md-sys-color-surface-container-high);
        }
        .${locationContentPhoto} {
            display: block;
            width: 100%;
            border-radius: 16px;
            margin: 12px 0;
            object-fit: cover;
        }
        .${locationFooter} {
            display: flex;
            gap: 8px;
            align-items: stretch;
        }
        .${locationFooter} .${btnPrimary},
        .${locationFooter} .${btnText} {
            flex: 1;
        }
        .${locationMarkdown} p { margin: 0 0 12px; line-height: 1.55; }
        .${locationMarkdown} h2, .${locationMarkdown} h3 { margin: 16px 0 8px; }
        .${locationMarkdown} ul { margin: 0 0 12px; padding-left: 18px; }
        .${locationMarkdownEm} { font-style: italic; }
        .${locationAdminPhotos} {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-bottom: 8px;
        }
        .${locationAdminPhotoWrap} { width: 96px; }
        .${locationAdminPhoto} {
            width: 96px;
            height: 96px;
            object-fit: cover;
            border-radius: 12px;
            display: block;
        }
        .${locationScheduleRow} {
            display: flex;
            gap: 8px;
            align-items: center;
            margin-bottom: 8px;
            flex-wrap: wrap;
        }
        .${locationScheduleRow} .${textInput} { flex: 1; }
    """.trimIndent() + studyExamCss() + photoGalleryCss()

    private fun studyExamCss(): String = """
        .${studyExamResult},
        .${studyExamBriefing} {
            --exam-accent: var(--md-sys-color-primary);
            display: flex;
            flex-direction: column;
            gap: 18px;
            padding: 22px 18px 18px;
            border-radius: 28px;
            border: 1px solid color-mix(in srgb, var(--exam-accent) 28%, transparent);
            background:
                radial-gradient(90% 70% at 50% -20%, color-mix(in srgb, var(--exam-accent) 34%, transparent), transparent 62%),
                linear-gradient(180deg, color-mix(in srgb, var(--exam-accent) 10%, var(--md-sys-color-surface)), var(--md-sys-color-surface));
            box-shadow: 0 22px 48px -30px var(--exam-accent);
        }
        .${studyExamResult}[data-level="5"] { --exam-accent: #e8c35a; }
        .${studyExamResult}[data-level="4"] { --exam-accent: var(--md-sys-color-primary); }
        .${studyExamResult}[data-level="3"] { --exam-accent: color-mix(in srgb, var(--md-sys-color-primary) 82%, #7ad0c8); }
        .${studyExamResult}[data-level="2"] { --exam-accent: color-mix(in srgb, var(--md-sys-color-primary) 55%, var(--md-sys-color-on-surface)); }
        .${studyExamResult}[data-level="1"] { --exam-accent: color-mix(in srgb, var(--md-sys-color-on-surface) 62%, var(--md-sys-color-primary)); }
        .${studyExamHero} {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            gap: 14px;
        }
        .${studyExamGradeMark} {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 128px;
            height: 128px;
            flex: 0 0 auto;
            border-radius: 50%;
            border: 6px solid var(--exam-accent);
            background:
                radial-gradient(circle at 50% 38%, color-mix(in srgb, var(--exam-accent) 22%, var(--md-sys-color-surface)), var(--md-sys-color-surface) 64%);
            color: var(--exam-accent);
            box-shadow:
                0 0 0 10px color-mix(in srgb, var(--exam-accent) 16%, transparent),
                0 18px 40px -20px var(--exam-accent);
        }
        .${studyExamGradeValue} {
            display: inline-flex;
            align-items: baseline;
        }
        .${studyExamGradeNumber} {
            font-size: 56px;
            font-weight: 800;
            line-height: 1;
            letter-spacing: -0.04em;
            font-variant-numeric: tabular-nums;
        }
        .${studyExamGradeOutOf} {
            font-size: 18px;
            font-weight: 700;
            opacity: 0.72;
            margin-left: 1px;
        }
        .${studyExamHeroCopy} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 6px;
        }
        .${studyExamLevel} {
            margin: 0;
            font-size: 28px;
            font-weight: 800;
            letter-spacing: 0.01em;
        }
        .${studyExamCheer} {
            margin: 0;
            max-width: 28rem;
            line-height: 1.45;
            color: color-mix(in srgb, var(--md-sys-color-on-surface) 78%, transparent);
        }
        .${studyExamSkills} {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }
        .${studyExamSkill} {
            display: grid;
            grid-template-columns: 1fr auto;
            grid-template-rows: auto auto;
            align-items: center;
            gap: 8px 10px;
            padding: 14px;
            border-radius: 18px;
            background: color-mix(in srgb, var(--exam-accent) 10%, var(--md-sys-color-surface));
            border: 1px solid color-mix(in srgb, var(--exam-accent) 18%, transparent);
            font-size: 13px;
            font-weight: 700;
        }
        .${studyExamSkillTrack} {
            display: none;
        }
        .${studyExamSkillFill} {
            display: none;
        }
        .${studyExamSkillPips} {
            display: flex;
            gap: 6px;
            grid-column: 1;
        }
        .${studyExamSkillPip} {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: color-mix(in srgb, var(--exam-accent) 18%, var(--md-sys-color-surface));
        }
        .${studyExamSkillPipOn} {
            background: var(--exam-accent);
            box-shadow: 0 0 10px color-mix(in srgb, var(--exam-accent) 55%, transparent);
        }
        .${studyExamSkillScore} {
            grid-column: 2;
            grid-row: 1 / span 2;
            font-size: 28px;
            font-weight: 800;
            line-height: 1;
            font-variant-numeric: tabular-nums;
            color: var(--exam-accent);
        }
        .${studyExamMoments} {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .${studyExamMoment} {
            display: flex;
            gap: 10px;
            align-items: baseline;
            padding: 10px 12px;
            border-radius: 14px;
            background: color-mix(in srgb, var(--exam-accent) 8%, var(--md-sys-color-surface));
            font-size: 14px;
        }
        .${studyExamMomentPoints} {
            flex: 0 0 auto;
            font-weight: 800;
            color: var(--exam-accent);
        }
        .${studyExamOutlineTitle} {
            margin: 4px 0 0;
            font-size: 18px;
            font-weight: 800;
        }
        .${studyExamOutline} {
            margin: 0;
            padding: 0 0 0 1.5em;
            list-style: decimal;
            list-style-position: outside;
        }
        .${studyExamOutlineItem} {
            display: list-item;
            padding: 8px 0;
            line-height: 1.45;
        }
        .${studyExamOutlineItem} + .${studyExamOutlineItem} {
            border-top: 1px solid color-mix(in srgb, var(--md-sys-color-outline-variant, var(--md-sys-color-on-surface)) 16%, transparent);
        }
        .${studyExamOutlineItem}::marker {
            font-weight: 800;
            color: var(--exam-accent);
        }
        .${studyExamStart} {
            align-self: center;
            min-width: 132px;
            background: var(--exam-accent);
            color: var(--md-sys-color-on-primary);
            border-color: transparent;
            font-weight: 700;
        }
        .${studyExamBriefing} .${btnPrimary} {
            width: 100%;
            align-self: stretch;
        }
        .${studyExamResult} .${btnPrimary} { width: 100%; }
        @media (max-width: 520px) {
            .${studyExamSkills} { grid-template-columns: 1fr; }
            .${studyExamGradeMark} { width: 112px; height: 112px; }
            .${studyExamGradeNumber} { font-size: 48px; }
        }
    """.trimIndent()

    private fun photoGalleryCss(): String = """
        .${photoDialogNav} {
            position: absolute;
            top: 50%;
            transform: translateY(-50%);
            width: 44px;
            height: 44px;
            padding: 0;
            border: none;
            border-radius: 50%;
            background: color-mix(in srgb, #000 45%, transparent);
            color: #fff;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
        }
        .${photoDialogNav}:hover {
            background: color-mix(in srgb, #000 65%, transparent);
        }
        .${photoDialogNavPrev} { left: 12px; }
        .${photoDialogNavNext} { right: 12px; }
        .${photoScroller} { margin-bottom: 12px; }
        .${photoScrollerViewport} {
            position: relative;
            overflow: hidden;
            border-radius: 20px;
            aspect-ratio: 16 / 9;
            background: var(--md-sys-color-surface-container-high);
            touch-action: pan-y;
            user-select: none;
            cursor: grab;
        }
        .${photoScrollerTrack} {
            display: flex;
            height: 100%;
            transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
            will-change: transform;
        }
        .${photoScrollerTrack}.${photoScrollerDragging} { transition: none; }
        .${photoScrollerSlide} {
            flex: 0 0 100%;
            width: 100%;
            height: 100%;
        }
        .${photoScrollerImage} {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            pointer-events: none;
            -webkit-user-drag: none;
        }
        .${photoScrollerNav} {
            position: absolute;
            top: 50%;
            transform: translateY(-50%);
            width: 36px;
            height: 36px;
            padding: 0;
            border: none;
            border-radius: 50%;
            background: color-mix(in srgb, #000 40%, transparent);
            color: #fff;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            z-index: 1;
        }
        .${photoScrollerNav}:hover {
            background: color-mix(in srgb, #000 60%, transparent);
        }
        .${photoScrollerNavPrev} { left: 16px; }
        .${photoScrollerNavNext} { right: 16px; }
        .${photoScrollerDots} {
            position: absolute;
            left: 0;
            right: 0;
            bottom: 10px;
            display: flex;
            justify-content: center;
            gap: 6px;
            pointer-events: none;
            z-index: 1;
        }
        .${photoScrollerDot} {
            width: 6px;
            height: 6px;
            padding: 0;
            border: none;
            border-radius: 999px;
            background: #fff;
            opacity: 0.45;
            pointer-events: auto;
            cursor: pointer;
            transition: width 0.2s ease, opacity 0.2s ease;
        }
        .${photoScrollerDot}.${photoScrollerDotActive} {
            width: 16px;
            opacity: 1;
        }
        .${photoScrollerThumbs} {
            display: flex;
            gap: 8px;
            overflow-x: auto;
            margin-top: 8px;
            padding: 2px;
            -webkit-overflow-scrolling: touch;
        }
        .${photoScrollerThumb} {
            display: block;
            padding: 0;
            width: 56px;
            height: 56px;
            border: 2px solid transparent;
            border-radius: 12px;
            overflow: hidden;
            background: var(--md-sys-color-surface-container-high);
            cursor: pointer;
            flex: 0 0 auto;
            opacity: 0.62;
            transition: opacity 0.2s, border-color 0.2s;
        }
        .${photoScrollerThumb}:hover { opacity: 1; }
        .${photoScrollerThumb}.${photoScrollerThumbActive} {
            opacity: 1;
            border-color: var(--md-sys-color-primary);
        }
        .${photoScrollerThumb} img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            pointer-events: none;
            -webkit-user-drag: none;
        }
        .${photoScrollerNav}:focus-visible,
        .${photoScrollerDot}:focus-visible,
        .${photoScrollerThumb}:focus-visible,
        .${photoDialogNav}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${zaloContactBtn} {
            display: flex;
            align-items: center;
            gap: 12px;
            width: 100%;
            margin: 0 0 12px;
            padding: 12px 14px;
            box-sizing: border-box;
            text-align: left;
            border: none;
            border-radius: 16px;
            background: var(--md-sys-color-surface-container-high);
            color: var(--md-sys-color-on-surface);
            cursor: pointer;
        }
        .${zaloContactBtn}:hover {
            background: var(--md-sys-color-surface-container-highest);
        }
        .${zaloContactBtn}:focus-visible {
            outline: 2px solid var(--md-sys-color-primary);
            outline-offset: 2px;
        }
        .${zaloContactIcon} {
            flex: 0 0 auto;
            font-size: 28px;
            color: var(--md-sys-color-primary);
        }
        .${zaloContactCopy} {
            display: flex;
            flex-direction: column;
            gap: 2px;
            min-width: 0;
        }
        .${zaloContactBrand} {
            font-size: 15px;
            font-weight: 700;
            line-height: 1.2;
        }
        .${zaloContactNumber} {
            font-size: 13px;
            font-weight: 500;
            line-height: 1.3;
            color: var(--md-sys-color-on-surface-variant);
            font-variant-numeric: tabular-nums;
        }
        .${zaloQrDialog} {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 16px;
            padding: 8px 0 4px;
        }
        .${zaloQrFrame} {
            position: relative;
            width: min(72vw, 260px);
            aspect-ratio: 1;
            background: #fff;
            border-radius: 16px;
            overflow: hidden;
        }
        .${zaloQrImage} {
            display: block;
            width: 100%;
            height: 100%;
        }
        .${zaloQrNumber} {
            margin: 0;
            font-size: 18px;
            font-weight: 700;
            font-variant-numeric: tabular-nums;
            color: var(--md-sys-color-on-surface);
        }
        .${zaloQrDialog} .${dialogActions} {
            width: 100%;
            justify-content: center;
        }

        .${appShell}.${layoutFullscreen} {
            justify-content: stretch;
            align-items: stretch;
            min-height: 100vh;
            min-height: 100dvh;
            padding: 0;
        }
        .${appShell}.${layoutFullscreen} .${appFrame} {
            width: 100%;
            max-width: none;
            height: 100vh;
            height: 100dvh;
            max-height: none;
            min-height: 0;
            border-radius: 0;
            box-shadow: none;
        }
        .${appShell}.${layoutFullscreen} .${appBody} {
            display: flex;
            flex: 1 1 auto;
            min-height: 0;
            min-width: 0;
        }
        .${appShell}.${layoutFullscreen} .${appMain} {
            flex: 1 1 auto;
            min-height: 0;
            min-width: 0;
            width: 100%;
        }
        .${fullscreenExit} {
            position: fixed;
            top: 12px;
            left: 12px;
            z-index: 1000;
            display: grid;
            place-items: center;
            width: 40px;
            height: 40px;
            padding: 0;
            border: 0;
            border-radius: 50%;
            background: var(--md-sys-color-surface);
            color: var(--md-sys-color-on-surface);
            opacity: 0.28;
            cursor: pointer;
            transition: opacity 150ms ease, background-color 150ms ease;
        }
        .${fullscreenExit}:hover,
        .${fullscreenExit}:focus-visible {
            opacity: 0.9;
        }
    """.trimIndent()
}
