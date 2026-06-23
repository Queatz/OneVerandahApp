package apap.oneverandah

import org.jetbrains.compose.web.css.*

object Styles : StyleSheet() {
    val container by style {
        fontFamily("system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Start)
        minHeight(100.vh)
        margin(0.px)
        paddingTop(50.px)
        backgroundColor(Color("#f5f5f5"))
    }

    val nav by style {
        display(DisplayStyle.Flex)
        gap(20.px)
        marginBottom(30.px)
    }

    val navLink by style {
        color(Color("#0066cc"))
        textDecoration("none")
        padding(8.px, 16.px)
        borderRadius(4.px)

        hover {
            backgroundColor(Color("#e0e0e0"))
        }
    }

    val activeLink by style {
        color(Color("#003366"))
        fontWeight("bold")
    }

    val content by style {
        padding(20.px)
        backgroundColor(Color.white)
        borderRadius(8.px)
        property("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
    }
}
