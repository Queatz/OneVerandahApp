package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Placeholder destination for Mini Stories until the full module ships.
 * Keeps chat / navigation entry points honest without a dead route.
 */
@Composable
fun ComingSoonStoriesScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenPhrases: () -> Unit,
) {
    val strings = Localization.strings
    Scaffold(
        title = strings.storiesTitle,
        onBack = onBack,
        onHome = onHome,
        scrollKey = "stories-soon",
    ) {
        Div(attrs = { classes(Styles.storiesSoon) }) {
            Div(attrs = { classes(Styles.chatEmptyIcon) }) {
                MaterialSymbol("auto_stories")
            }
            H2 { Text(strings.storiesComingSoon) }
            P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
                Text(strings.storiesComingSoonBody)
            }
            PrimaryButton(
                label = strings.chatExplorePhrases,
                icon = "import_contacts",
                onClick = onOpenPhrases,
            )
        }
    }
}
