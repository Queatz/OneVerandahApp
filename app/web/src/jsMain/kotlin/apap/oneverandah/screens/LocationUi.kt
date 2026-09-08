package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import apap.oneverandah.Styles
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.locations.LocationCard
import apap.oneverandah.locations.LocationHours
import apap.oneverandah.locations.LocationHoursSnapshot
import apap.oneverandah.locations.LocationLogic
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

fun locationStatusLabel(displayStatus: String, strings: UiStrings): String = when (displayStatus) {
    LocationLogic.DISPLAY_COMING_SOON -> strings.locationsStatusComingSoon
    LocationLogic.DISPLAY_OPEN -> strings.locationsStatusOpen
    LocationLogic.DISPLAY_CLOSED -> strings.locationsStatusClosed
    else -> strings.locationsStatusPlanning
}

fun locationHoursLabel(snapshot: LocationHoursSnapshot, strings: UiStrings): String? = when {
    snapshot.displayStatus == LocationLogic.DISPLAY_OPEN && snapshot.closesAt != null ->
        strings.locationsClosesAt(snapshot.closesAt!!)
    snapshot.displayStatus == LocationLogic.DISPLAY_CLOSED && snapshot.opensAt != null -> {
        val day = snapshot.opensDayOfWeek
        if (day == null) strings.locationsOpensAt(snapshot.opensAt!!)
        else strings.locationsOpensDayAt(locationDayName(day, strings), snapshot.opensAt!!)
    }
    else -> null
}

fun locationHoursLabel(card: LocationCard, nowMs: Long, strings: UiStrings): String? =
    locationHoursLabel(LocationLogic.hoursSnapshot(card.status, card.schedule, nowMs), strings)

fun locationDayName(day: Int, strings: UiStrings): String = when (day) {
    1 -> strings.locationsDayMon
    2 -> strings.locationsDayTue
    3 -> strings.locationsDayWed
    4 -> strings.locationsDayThu
    5 -> strings.locationsDayFri
    6 -> strings.locationsDaySat
    else -> strings.locationsDaySun
}

fun locationHoursRange(slot: LocationHours): String =
    "${LocationLogic.formatMinutes(slot.openMinutes)}–${LocationLogic.formatMinutes(slot.closeMinutes)}"

fun locationDayHoursLabel(slots: List<LocationHours>, strings: UiStrings): String {
    if (slots.isEmpty()) return strings.locationsStatusClosed
    return slots.joinToString(", ") { locationHoursRange(it) }
}

@Composable
internal fun LocationHoursSection(
    snapshot: LocationHoursSnapshot,
    schedule: List<LocationHours>,
    nowMs: Long,
    strings: UiStrings,
) {
    val hours = locationHoursLabel(snapshot, strings)
    Div(attrs = { classes(Styles.locationHoursInline) }) {
        Span(attrs = {
            classes(Styles.locationStatus)
            classes(locationStatusClass(snapshot.displayStatus))
        }) { Text(locationStatusLabel(snapshot.displayStatus, strings)) }
        if (hours != null) {
            Span(attrs = { classes(Styles.locationCardHours) }) { Text(hours) }
        }
    }
    if (schedule.isEmpty()) return
    val today = LocationLogic.saigonDayOfWeek(nowMs)
    val byDay = schedule.groupBy { it.dayOfWeek }
    Div(attrs = { classes(Styles.locationWeeklyHours) }) {
        (1..7).forEach { day ->
            val slots = byDay[day].orEmpty()
            Div(attrs = {
                classes(Styles.locationWeeklyHoursRow)
                if (day == today) classes(Styles.locationWeeklyHoursToday)
            }) {
                Span(attrs = { classes(Styles.locationWeeklyHoursDay) }) {
                    Text(locationDayName(day, strings))
                }
                Span(attrs = {
                    classes(Styles.locationWeeklyHoursTime)
                    if (slots.isEmpty()) classes(Styles.locationWeeklyHoursClosed)
                }) {
                    Text(locationDayHoursLabel(slots, strings))
                }
            }
        }
    }
}

fun locationShareUrl(id: String): String {
    val origin = window.location.origin.trimEnd('/')
    return "$origin/locations/$id"
}

@Composable
internal fun LocationHighlights(highlights: List<String>) {
    val items = highlights.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
    if (items.isEmpty()) return
    Div(attrs = { classes(Styles.locationHighlights) }) {
        items.forEachIndexed { index, highlight ->
            P(attrs = {
                classes(Styles.locationHighlight, locationHighlightTone(index))
            }) {
                MaterialSymbol(locationHighlightIcon(index))
                Text(highlight)
            }
        }
    }
}

internal fun locationHighlightTone(index: Int): String = when (index % 3) {
    0 -> Styles.locationHighlightTone0
    1 -> Styles.locationHighlightTone1
    else -> Styles.locationHighlightTone2
}

private fun locationHighlightIcon(index: Int): String = when (index % 3) {
    0 -> "auto_awesome"
    1 -> "star"
    else -> "bolt"
}

fun formatVnd(amount: Long): String {
    val digits = amount.toString()
    val grouped = buildString {
        digits.reversed().forEachIndexed { index, c ->
            if (index > 0 && index % 3 == 0) append('.')
            append(c)
        }
    }.reversed()
    return "$grouped ₫"
}
