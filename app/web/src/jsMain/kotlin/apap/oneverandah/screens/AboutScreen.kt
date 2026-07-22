package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.InfoList
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import app.softwork.routingcompose.Router
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    val router = Router.current
    Scaffold(
        title = strings.menuAbout,
        onBack = onBack,
    ) {
        P(attrs = { classes(Styles.homePrompt) }) {
            Text(strings.aboutText)
        }
        InfoList(
            items = listOf(
                "workspace_premium" to strings.aboutExclusive,
                "verified" to strings.aboutTrusted,
                "redeem" to strings.aboutFree,
            )
        )
        // Hidden admin entry: a tiny, low-contrast control at the bottom of About.
        Div(attrs = { classes(Styles.hiddenAdminWrap) }) {
            Button(attrs = {
                classes(Styles.hiddenAdminButton)
                attr("type", "button")
                attr("aria-label", strings.adminTitle)
                onClick { router.navigate("/admin") }
            }) {
                Text("·")
            }
        }
    }
}
