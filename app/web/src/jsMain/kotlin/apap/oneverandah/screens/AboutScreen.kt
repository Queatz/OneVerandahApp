package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    Scaffold(
        title = strings.menuAbout,
        onBack = onBack,
    ) {
        P(attrs = { classes(Styles.homePrompt) }) {
            Text(strings.aboutText)
        }
        Ul {
            Li { Text(strings.aboutExclusive) }
            Li { Text(strings.aboutTrusted) }
            Li { Text(strings.aboutFree) }
        }
    }
}
