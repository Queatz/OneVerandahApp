@file:OptIn(ExperimentalComposeWebSvgApi::class)

package apap.oneverandah.components

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.model.BodyMapOption
import apap.oneverandah.model.Language
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.svg.Ellipse
import org.jetbrains.compose.web.svg.Rect
import org.jetbrains.compose.web.svg.Svg
import org.w3c.dom.svg.SVGElement
import org.w3c.dom.svg.SVGRectElement

/**
 * Interactive SVG body-part selector. Renders a simple human silhouette whose
 * regions ([BodyMapOption.parts]) can be tapped to (de)select them. Selection
 * is reflected with a highlight and mirrored by a tappable legend of chips
 * showing each part's price.
 */
@Composable
fun BodyMapSelector(
    option: BodyMapOption,
    selected: Set<String>,
    language: Language,
    hint: String,
    onToggle: (String) -> Unit,
) {
    val present: Set<String> = option.parts.map { it.id }.toSet()

    fun <E : SVGElement> region(partId: String): (AttrsScope<E>.() -> Unit) = {
        classes(Styles.bodyRegion)
        if (partId in selected) classes(Styles.bodyRegionActive)
        attr("role", "button")
        attr("tabindex", "0")
        onClick { onToggle(partId) }
    }

    Div(attrs = { classes(Styles.bodyMap) }) {
        Svg(viewBox = "0 0 120 240", attrs = { classes(Styles.bodyMapSvg) }) {
            if ("head" in present) {
                Ellipse(cx = 60, cy = 22, rx = 15, ry = 18, attrs = region("head"))
            }
            if ("shoulders" in present) {
                Rect(x = 32, y = 42, width = 56, height = 16, attrs = {
                    attr("rx", "8")
                    region<SVGRectElement>("shoulders").invoke(this)
                })
            }
            if ("back" in present) {
                Rect(x = 40, y = 56, width = 40, height = 60, attrs = {
                    attr("rx", "10")
                    region<SVGRectElement>("back").invoke(this)
                })
            }
            if ("arms" in present) {
                Rect(x = 19, y = 58, width = 13, height = 60, attrs = {
                    attr("rx", "6")
                    region<SVGRectElement>("arms").invoke(this)
                })
                Rect(x = 88, y = 58, width = 13, height = 60, attrs = {
                    attr("rx", "6")
                    region<SVGRectElement>("arms").invoke(this)
                })
            }
            if ("legs" in present) {
                Rect(x = 42, y = 118, width = 14, height = 86, attrs = {
                    attr("rx", "7")
                    region<SVGRectElement>("legs").invoke(this)
                })
                Rect(x = 64, y = 118, width = 14, height = 86, attrs = {
                    attr("rx", "7")
                    region<SVGRectElement>("legs").invoke(this)
                })
            }
            if ("feet" in present) {
                Ellipse(cx = 49, cy = 210, rx = 9, ry = 7, attrs = region("feet"))
                Ellipse(cx = 71, cy = 210, rx = 9, ry = 7, attrs = region("feet"))
            }
        }

        P(attrs = { classes(Styles.bodyMapHint) }) { Text(hint) }

        // Legend / fallback chips so prices are visible and parts are tappable
        // even without precise SVG aiming (and for accessibility).
        Div(attrs = { classes(Styles.bodyMapLegend) }) {
            option.parts.forEach { part ->
                OptionChip(
                    label = part.label.resolve(language),
                    priceHint = priceHint(part.priceDelta, language),
                    active = part.id in selected,
                    onClick = { onToggle(part.id) },
                )
            }
        }
    }
}
