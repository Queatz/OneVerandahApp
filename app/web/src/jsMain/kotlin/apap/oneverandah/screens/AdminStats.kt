@file:OptIn(ExperimentalComposeWebSvgApi::class)

package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.stats.AdminStatsResponse
import apap.oneverandah.stats.DailyVisitStat
import apap.oneverandah.stats.NamedCountStat
import org.jetbrains.compose.web.ExperimentalComposeWebSvgApi
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.svg.Path
import org.jetbrains.compose.web.svg.Rect
import org.jetbrains.compose.web.svg.Svg

@Composable
fun AdminStatsPanel(
    data: AdminStatsResponse,
    loading: Boolean,
) {
    val strings = Localization.strings
    var rangeDays by remember { mutableStateOf(7) }
    val daily = if (rangeDays == 7) data.daily.takeLast(7) else data.daily
    var selected by remember(daily.size, daily.lastOrNull()?.date) {
        mutableStateOf(daily.lastIndex.coerceAtLeast(0))
    }
    val safeSelected = selected.coerceIn(0, (daily.size - 1).coerceAtLeast(0))
    val selectedDay = daily.getOrNull(safeSelected)

    SectionTitle(strings.adminStatsTab)
    P(attrs = { classes(Styles.homePrompt) }) {
        Text(strings.adminStatsHint)
    }

    if (loading && data.daily.isEmpty()) {
        P { Text(strings.phrasesLoading) }
        return
    }

    Div(attrs = { classes(Styles.adminStatsCards) }) {
        StatCard(strings.adminStatsToday, data.totals.visitorsToday, strings.adminStatsVisitors)
        StatCard(strings.adminStatsLast7Days, data.totals.visitors7d, strings.adminStatsVisitors)
        StatCard(strings.adminStatsLast30Days, data.totals.visitors30d, strings.adminStatsVisitors)
        StatCard(strings.adminStatsAllTime, data.totals.visitorsAllTime, strings.adminStatsVisitors)
    }

    Div(attrs = { classes(Styles.adminToolbar) }) {
        Div(attrs = { classes(Styles.chipGroup) }) {
            RangeChip(strings.adminStats7Days, rangeDays == 7) { rangeDays = 7 }
            RangeChip(strings.adminStats30Days, rangeDays == 30) { rangeDays = 30 }
        }
        Span(attrs = { classes(Styles.adminStatsTimezone) }) {
            Text(strings.adminStatsTimezone)
        }
    }

    if (daily.all { it.visits == 0L }) {
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.adminStatsNoData) }
    } else {
        VisitorChart(
            days = daily,
            selected = safeSelected,
            onSelect = { selected = it },
        )
    }

    selectedDay?.let { day ->
        Div(attrs = { classes(Styles.adminStatsSelected) }) {
            H2 { Text("${strings.adminStatsSelected}: ${day.date}") }
            P {
                Text(
                    "${strings.adminStatsVisitors}: ${day.visitors} · ${strings.adminStatsVisits}: ${day.visits} · ${strings.adminStatsNewVisitors}: ${day.newVisitors}",
                )
            }
        }
    }

    if (data.topPaths.isNotEmpty()) {
        SectionTitle(strings.adminStatsTopScreens)
        data.topPaths.forEach { row -> StatRow(row) }
    }
    if (data.languages.isNotEmpty()) {
        SectionTitle(strings.adminStatsLanguages)
        data.languages.forEach { row -> StatRow(row) }
    }
}

@Composable
private fun RangeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Button(attrs = {
        classes(Styles.chip)
        if (active) classes(Styles.chipActive)
        attr("type", "button")
        onClick { onClick() }
    }) { Text(label) }
}

@Composable
private fun StatCard(label: String, value: Long, hint: String) {
    Div(attrs = { classes(Styles.adminStatsCard) }) {
        Span(attrs = { classes(Styles.adminStatsCardLabel) }) { Text(label) }
        Span(attrs = { classes(Styles.adminStatsCardValue) }) { Text(value.toString()) }
        Span(attrs = { classes(Styles.adminStatsCardHint) }) { Text(hint) }
    }
}

@Composable
private fun StatRow(row: NamedCountStat) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.listCard) }) {
        Div(attrs = { classes(Styles.listDetails) }) {
            H2 { Text(row.key) }
            P { Text("${strings.adminStatsVisits}: ${row.visits} · ${strings.adminStatsVisitors}: ${row.visitors}") }
        }
    }
}

@Composable
private fun VisitorChart(
    days: List<DailyVisitStat>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val strings = Localization.strings
    val maxVal = days.maxOf { maxOf(it.visitors, it.visits) }.coerceAtLeast(1)
    val chartW = 640.0
    val chartH = 200.0
    val padL = 12.0
    val padR = 12.0
    val padT = 16.0
    val padB = 8.0
    val innerW = chartW - padL - padR
    val innerH = chartH - padT - padB
    val n = days.size.coerceAtLeast(1)
    val gap = if (n <= 7) 12.0 else 4.0
    val barW = ((innerW - gap * (n - 1)) / n).coerceAtLeast(2.0)
    val line = days.mapIndexed { i, day ->
        val x = padL + i * (barW + gap) + barW / 2
        val y = padT + innerH - (day.visits.toDouble() / maxVal) * innerH
        x to y
    }
    val lineD = if (line.isEmpty()) "" else buildString {
        append("M ${line.first().first} ${line.first().second}")
        line.drop(1).forEach { append(" L ${it.first} ${it.second}") }
    }

    Div(attrs = { classes(Styles.adminStatsChart) }) {
        Svg(
            viewBox = "0 0 $chartW $chartH",
            attrs = {
                attr("preserveAspectRatio", "none")
                classes(Styles.adminStatsSvg)
            },
        ) {
            if (lineD.isNotBlank()) {
                Path(d = lineD, attrs = {
                    attr("fill", "none")
                    attr("stroke", "var(--md-sys-color-tertiary)")
                    attr("stroke-width", "2.5")
                    attr("stroke-linejoin", "round")
                    attr("stroke-linecap", "round")
                })
            }
            days.forEachIndexed { i, day ->
                val h = ((day.visitors.toDouble() / maxVal) * innerH).coerceAtLeast(if (day.visitors > 0) 3.0 else 0.0)
                val x = padL + i * (barW + gap)
                val y = if (h == 0.0) padT + innerH - 2 else padT + innerH - h
                Rect(
                    x = x,
                    y = y,
                    width = barW,
                    height = if (h == 0.0) 2.0 else h,
                    attrs = {
                        attr("rx", "4")
                        classes(Styles.adminStatsBar)
                        if (i == selected) classes(Styles.adminStatsBarActive)
                        attr("role", "button")
                        attr("tabindex", "0")
                        attr(
                            "aria-label",
                            "${day.date}: ${day.visitors} ${strings.adminStatsVisitors}, ${day.visits} ${strings.adminStatsVisits}",
                        )
                        onMouseEnter { onSelect(i) }
                        onClick { onSelect(i) }
                    },
                )
            }
        }
        Div(attrs = { classes(Styles.adminStatsAxis) }) {
            days.forEachIndexed { i, day ->
                Span(attrs = {
                    classes(Styles.adminStatsAxisLabel)
                    if (i == selected) classes(Styles.adminStatsAxisLabelActive)
                    if (n > 10 && i != selected && i != 0 && i != n - 1 && i % 4 != 0) {
                        classes(Styles.adminStatsAxisLabelHidden)
                    }
                }) { Text(day.date.takeLast(5)) }
            }
        }
        Div(attrs = { classes(Styles.adminStatsLegend) }) {
            Span(attrs = { classes(Styles.adminStatsLegendVisitors) }) { Text(strings.adminStatsVisitors) }
            Span(attrs = { classes(Styles.adminStatsLegendVisits) }) { Text(strings.adminStatsVisits) }
        }
    }
}
