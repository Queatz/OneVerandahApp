package apap.oneverandah

import androidx.compose.runtime.Composable
import app.softwork.routingcompose.BrowserRouter
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}

@Composable
fun App() {
    Style(Styles)

    Text("Hello from Compose!")

    BrowserRouter("/") {
        Div(attrs = { classes(Styles.container) }) {
            Nav(attrs = { classes(Styles.nav) }) {
                A(href = "/", attrs = { classes(Styles.navLink) }) { Text("Home") }
                A(href = "/about", attrs = { classes(Styles.navLink) }) { Text("About") }
            }

            route("/") {
                HomePage()
            }
            route("/about") {
                AboutPage()
            }
            noMatch {
                redirect("/", hide = true)
            }
        }
    }
}

@Composable
fun HomePage() {
    Div(attrs = { classes(Styles.content) }) {
        val greeting = Greeting()
        H1 { Text("One Verandah App") }
        P { Text(greeting.greet()) }
        P { Text("Welcome to the home page. This is built with Compose HTML!") }
    }
}

@Composable
fun AboutPage() {
    Div(attrs = { classes(Styles.content) }) {
        H1 { Text("About") }
        P { Text("This is a sample second page using routing-compose.") }
        P { Text("Navigation between Home and About works via client-side routing.") }
    }
}
