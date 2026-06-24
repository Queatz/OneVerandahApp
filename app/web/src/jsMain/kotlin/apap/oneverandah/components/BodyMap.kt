@file:OptIn(ExperimentalComposeWebSvgApi::class)

package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.i18n.stringsFor
import apap.oneverandah.model.BodyMapOption
import apap.oneverandah.model.Language
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.svg.Circle
import org.jetbrains.compose.web.svg.Ellipse
import org.jetbrains.compose.web.svg.Line
import org.jetbrains.compose.web.svg.Path
import org.jetbrains.compose.web.svg.Rect
import org.jetbrains.compose.web.svg.Svg
import org.w3c.dom.svg.SVGElement

enum class Sex {
    MALE, FEMALE
}

enum class Side {
    FRONT, BACK
}

private fun isVisibleOnSide(partId: String, side: Side): Boolean {
    return when (partId) {
        "chest", "belly" -> side == Side.FRONT
        "upper-back", "lower-back" -> side == Side.BACK
        else -> true
    }
}

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
    var sex by remember { mutableStateOf(Sex.MALE) }
    var side by remember { mutableStateOf(Side.BACK) }

    val present: Set<String> = option.parts.map { it.id }.toSet()
    val strings = stringsFor(language)

    fun <E : SVGElement> region(partId: String): (AttrsScope<E>.() -> Unit) = {
        classes(Styles.bodyRegion)
        if (partId in selected) classes(Styles.bodyRegionActive)
        attr("role", "button")
        attr("tabindex", "0")
        onClick { onToggle(partId) }
    }

    Div(attrs = { classes(Styles.bodyMap) }) {
        // M/F toggle in the top left
        Div(attrs = { classes(Styles.bodyControlLeft) }) {
            Button(attrs = {
                classes(Styles.bodyControlBtn)
                if (sex == Sex.MALE) classes(Styles.bodyControlBtnActive)
                onClick { sex = Sex.MALE }
            }) {
                Text(strings.male)
            }
            Button(attrs = {
                classes(Styles.bodyControlBtn)
                if (sex == Sex.FEMALE) classes(Styles.bodyControlBtnActive)
                onClick { sex = Sex.FEMALE }
            }) {
                Text(strings.female)
            }
        }

        // Spin button in the top right
        Div(attrs = { classes(Styles.bodyControlRight) }) {
            Button(attrs = {
                classes(Styles.bodyControlBtn)
                onClick {
                    side = if (side == Side.FRONT) Side.BACK else Side.FRONT
                }
                attr("title", if (side == Side.FRONT) strings.viewBack else strings.viewFront)
            }) {
                MaterialIcon("cached")
            }
        }

        Svg(viewBox = "0 0 120 240", attrs = { classes(Styles.bodyMapSvg) }) {
            // --- BACKGROUND HUMAN SILHOUETTE ---
            // Head
            Ellipse(cx = 60, cy = 22, rx = 15, ry = 18, attrs = { classes(Styles.bodySilhouette) })
            // Neck
            Rect(x = 54, y = 38, width = 12, height = 12, attrs = {
                classes(Styles.bodySilhouette)
                attr("rx", "2")
            })

            if (sex == Sex.MALE) {
                // Male Torso
                Path(d = "M 30,48 Q 60,44 90,48 L 84,120 Q 60,124 36,120 Z", attrs = {
                    classes(Styles.bodySilhouette)
                })
                // Male Arms
                Line(x1 = 28, y1 = 48, x2 = 20, y2 = 114, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "8")
                    attr("stroke-linecap", "round")
                })
                Line(x1 = 92, y1 = 48, x2 = 100, y2 = 114, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "8")
                    attr("stroke-linecap", "round")
                })
                // Male Hands
                Circle(cx = 20, cy = 114, r = 6.5, attrs = { classes(Styles.bodySilhouette) })
                Circle(cx = 100, cy = 114, r = 6.5, attrs = { classes(Styles.bodySilhouette) })
                // Male Legs
                Line(x1 = 46, y1 = 130, x2 = 46, y2 = 214, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "10")
                    attr("stroke-linecap", "round")
                })
                Line(x1 = 74, y1 = 130, x2 = 74, y2 = 214, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "10")
                    attr("stroke-linecap", "round")
                })
            } else {
                // Female Torso
                Path(d = "M 34,48 Q 60,45 86,48 L 81,90 Q 79,100 83,110 L 84,130 Q 60,134 36,130 L 37,110 Q 41,100 39,90 Z", attrs = {
                    classes(Styles.bodySilhouette)
                })
                // Female Arms
                Line(x1 = 32, y1 = 48, x2 = 24, y2 = 114, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "7")
                    attr("stroke-linecap", "round")
                })
                Line(x1 = 88, y1 = 48, x2 = 96, y2 = 114, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "7")
                    attr("stroke-linecap", "round")
                })
                // Female Hands
                Circle(cx = 24, cy = 114, r = 5.85, attrs = { classes(Styles.bodySilhouette) })
                Circle(cx = 96, cy = 114, r = 5.85, attrs = { classes(Styles.bodySilhouette) })
                // Female Legs
                Line(x1 = 47, y1 = 130, x2 = 47, y2 = 214, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "9")
                    attr("stroke-linecap", "round")
                })
                Line(x1 = 73, y1 = 130, x2 = 73, y2 = 214, attrs = {
                    classes(Styles.bodySilhouette)
                    attr("stroke-width", "9")
                    attr("stroke-linecap", "round")
                })
            }

            // Feet silhouette
            Ellipse(cx = 49, cy = 214, rx = 9, ry = 5, attrs = { classes(Styles.bodySilhouette) })
            Ellipse(cx = 71, cy = 214, rx = 9, ry = 5, attrs = { classes(Styles.bodySilhouette) })

            // --- VIEW-SPECIFIC SILHOUETTE DETAILS ---
            if (side == Side.FRONT) {
                // Belly button (umbilicus)
                Circle(cx = 60, cy = 114, r = 1.5, attrs = { classes(Styles.bodySilhouetteDetail) })
                // Collarbones
                Path(d = "M 45,54 Q 60,57 75,54", attrs = {
                    classes(Styles.bodySilhouetteDetail)
                })
                // Chest / Breasts lines
                if (sex == Sex.MALE) {
                    Line(x1 = 42, y1 = 66, x2 = 56, y2 = 66, attrs = { classes(Styles.bodySilhouetteDetail) })
                    Line(x1 = 64, y1 = 66, x2 = 78, y2 = 66, attrs = { classes(Styles.bodySilhouetteDetail) })
                    // Nipples
                    Circle(cx = 49, cy = 70, r = 1.2, attrs = { classes(Styles.bodySilhouetteDetail) })
                    Circle(cx = 71, cy = 70, r = 1.2, attrs = { classes(Styles.bodySilhouetteDetail) })
                } else {
                    Path(d = "M 42,72 C 42,84 58,84 58,72", attrs = {
                        classes(Styles.bodySilhouetteDetail)
                    })
                    Path(d = "M 62,72 C 62,84 78,84 78,72", attrs = {
                        classes(Styles.bodySilhouetteDetail)
                    })
                    // Nipples
                    Circle(cx = 49, cy = 76, r = 1.2, attrs = { classes(Styles.bodySilhouetteDetail) })
                    Circle(cx = 71, cy = 76, r = 1.2, attrs = { classes(Styles.bodySilhouetteDetail) })
                }
            } else {
                // Spine line (dashed)
                Line(x1 = 60, y1 = 56, x2 = 60, y2 = 126, attrs = {
                    classes(Styles.bodySilhouetteDetail)
                    attr("stroke-dasharray", "3,3")
                })
                // Shoulder blades (scapulae)
                Line(x1 = 42, y1 = 64, x2 = 50, y2 = 72, attrs = { classes(Styles.bodySilhouetteDetail) })
                Line(x1 = 78, y1 = 64, x2 = 70, y2 = 72, attrs = { classes(Styles.bodySilhouetteDetail) })
            }

            // --- INTERACTIVE HOTSPOT REGIONS OVERLAYS ---
            if ("head" in present) {
                Ellipse(cx = 60, cy = 22, rx = 15, ry = 18, attrs = region("head"))
            }
            if ("shoulders" in present) {
                Ellipse(cx = 60, cy = 52, rx = 30, ry = 11, attrs = region("shoulders"))
            }

            if (side == Side.FRONT) {
                if ("chest" in present) {
                    Ellipse(cx = 60, cy = 78, rx = 22, ry = 16, attrs = region("chest"))
                }
                if ("belly" in present) {
                    Ellipse(cx = 60, cy = 108, rx = 20, ry = 16, attrs = region("belly"))
                }
            } else {
                if ("upper-back" in present) {
                    Ellipse(cx = 60, cy = 78, rx = 22, ry = 16, attrs = region("upper-back"))
                }
                if ("lower-back" in present) {
                    Ellipse(cx = 60, cy = 108, rx = 20, ry = 16, attrs = region("lower-back"))
                }
            }

            if ("arms" in present) {
                Ellipse(cx = 25, cy = 88, rx = 7, ry = 30, attrs = region("arms"))
                Ellipse(cx = 95, cy = 88, rx = 7, ry = 30, attrs = region("arms"))
            }
            if ("legs" in present) {
                Ellipse(cx = 49, cy = 162, rx = 9, ry = 42, attrs = region("legs"))
                Ellipse(cx = 71, cy = 162, rx = 9, ry = 42, attrs = region("legs"))
            }
            if ("feet" in present) {
                Ellipse(cx = 49, cy = 212, rx = 9, ry = 7, attrs = region("feet"))
                Ellipse(cx = 71, cy = 212, rx = 9, ry = 7, attrs = region("feet"))
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
                    onClick = {
                        onToggle(part.id)
                        // Auto-spin to show the clicked part if it's on the other side
                        if (part.id == "chest" || part.id == "belly") {
                            side = Side.FRONT
                        } else if (part.id == "upper-back" || part.id == "lower-back") {
                            side = Side.BACK
                        }
                    },
                )
            }
        }
    }
}
