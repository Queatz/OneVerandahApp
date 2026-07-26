package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.PhraseApi
import apap.oneverandah.components.CoverImage
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.data.PhrasesCatalog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.phrases.Phrase
import apap.oneverandah.phrases.PhraseReactions
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun PhrasesScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    val language = Localization.language
    val scope = rememberCoroutineScope()
    var phrases by remember { mutableStateOf(PhrasesCatalog.all) }
    var coverImageUrl by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var locationFilter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val remote = PhraseApi.fetchPhrases()
        phrases = remote.phrases.ifEmpty { PhrasesCatalog.all }
        coverImageUrl = remote.coverImageUrl ?: PhraseApi.fetchCover()
        loading = false
    }

    val bannerUrl = coverImageUrl
        ?: phrases.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl

    val locations = phrases
        .map { it.locationKey to it.location.resolve(language) }
        .distinctBy { it.first }

    val visible = phrases
        .filter { locationFilter == null || it.locationKey == locationFilter }
        .sortedWith(
            compareByDescending<Phrase> { it.reactions.values.sum() }
                .thenBy { it.location.resolve(language) }
                .thenBy { it.spoken.resolve(language) },
        )

    val grouped = visible.groupBy { it.locationKey }

    Scaffold(
        title = strings.phrasesTitle,
        onBack = onBack,
        onHome = onHome,
        showBanner = true,
        showPhrasesAction = false,
        bannerImageUrl = bannerUrl,
        scrollKey = "phrases",
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.phrasesBlurb)
        }

        if (locations.size > 1) {
            Div(attrs = { classes(Styles.chipGroup) }) {
                Button(attrs = {
                    classes(Styles.chip)
                    if (locationFilter == null) classes(Styles.chipActive)
                    attr("type", "button")
                    onClick { locationFilter = null }
                }) { Text(strings.phrasesAllLocations) }

                locations.forEach { (key, label) ->
                    Button(attrs = {
                        classes(Styles.chip)
                        if (locationFilter == key) classes(Styles.chipActive)
                        attr("type", "button")
                        onClick { locationFilter = key }
                    }) { Text(label) }
                }
            }
        }

        if (loading && phrases.isEmpty()) {
            P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.phrasesLoading) }
        } else if (visible.isEmpty()) {
            P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.phrasesEmpty) }
        } else {
            grouped.forEach { (_, items) ->
                val locationLabel = items.first().location.resolve(language)
                val icon = items.first().icon
                Div(attrs = { classes(Styles.phraseLocationHeader) }) {
                    MaterialSymbol(icon)
                    SectionTitle(locationLabel)
                }
                items.forEach { phrase ->
                    PhraseCard(
                        phrase = phrase,
                        expanded = expandedId == phrase.id,
                        onToggle = {
                            expandedId = if (expandedId == phrase.id) null else phrase.id
                        },
                        onReact = { emoji ->
                            scope.launch {
                                val updated = PhraseApi.react(phrase.id, emoji)
                                if (updated != null) {
                                    phrases = phrases.map { if (it.id == updated.id) updated else it }
                                } else {
                                    // Optimistic local bump when offline / backend unavailable.
                                    phrases = phrases.map {
                                        if (it.id != phrase.id) it
                                        else it.copy(
                                            reactions = it.reactions + (
                                                emoji to ((it.reactions[emoji] ?: 0) + 1)
                                                ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        P(attrs = { classes(Styles.phrasesOriginStory) }) {
            Text(strings.phrasesOriginStory)
        }
    }
}

@Composable
private fun PhraseCard(
    phrase: Phrase,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReact: (String) -> Unit,
) {
    val strings = Localization.strings
    val language = Localization.language
    val cover = phrase.imageUrl

    Div(attrs = {
        classes(Styles.phraseCard)
        if (cover != null) classes(Styles.phraseCardCovered)
        attr("role", "button")
        onClick { onToggle() }
    }) {
        if (cover != null) CoverImage(cover)

        Div(attrs = { classes(Styles.phraseCardBody) }) {
            Span(attrs = { classes(Styles.phraseLocationTag) }) {
                MaterialSymbol(phrase.icon, extraClass = Styles.phraseLocationIcon)
                Text(phrase.location.resolve(language))
            }

            Span(attrs = { classes(Styles.phraseSpokenLabel) }) {
                Text(strings.phrasesSpokenIn)
            }
            H2(attrs = { classes(Styles.phraseSpoken) }) {
                Text(phrase.spoken.resolve(language))
            }
            P(attrs = { classes(Styles.phraseRawAsk) }) {
                Text(phrase.rawAsk.resolve(language))
            }

            if (expanded) {
                Div(attrs = { classes(Styles.phraseVerseBlock) }) {
                    P(attrs = { classes(Styles.phraseVerse) }) {
                        Span(attrs = { classes(Styles.phraseInstructionsLabel) }) {
                            Text(strings.phrasesVerse)
                        }
                        Div { Text(phrase.verse.resolve(language)) }
                    }
                }
                Div(attrs = { classes(Styles.phraseInstructions) }) {
                    Span(attrs = { classes(Styles.phraseInstructionsLabel) }) {
                        Text(strings.phrasesHowToUse)
                    }
                    P { Text(phrase.instructions.resolve(language)) }
                }
            } else {
                Span(attrs = { classes(Styles.phraseExpandHint) }) {
                    Text(strings.phrasesShowVerse)
                }
            }

            Div(attrs = {
                classes(Styles.phraseReactions)
                onClick { it.stopPropagation() }
            }) {
                PhraseReactions.allowed.forEach { emoji ->
                    val count = phrase.reactions[emoji] ?: 0
                    Button(attrs = {
                        classes(Styles.phraseReactionBtn)
                        attr("type", "button")
                        attr("aria-label", strings.phrasesReact)
                        onClick { onReact(emoji) }
                    }) {
                        Text(if (count > 0) "$emoji $count" else emoji)
                    }
                }
            }
        }
    }
}
