package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialIcon
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    Scaffold(
        title = strings.menuHelp,
        onBack = onBack,
    ) {
        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { /* Send feedback action */ }
        }) {
            MaterialIcon("feedback", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpFeedback) }
            }
        }

        Div(attrs = {
            classes(Styles.listCard)
            attr("role", "button")
            onClick { /* Report a problem action */ }
        }) {
            MaterialIcon("report_problem", extraClass = Styles.listIcon)
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(strings.helpReportProblem) }
            }
        }
    }
}
