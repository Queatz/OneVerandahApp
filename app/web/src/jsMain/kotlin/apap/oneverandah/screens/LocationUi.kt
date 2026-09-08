package apap.oneverandah.screens

import apap.oneverandah.i18n.UiStrings
import apap.oneverandah.locations.LocationCard
import apap.oneverandah.locations.LocationHoursSnapshot
import apap.oneverandah.locations.LocationLogic
import kotlinx.browser.window

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

fun locationShareUrl(id: String): String {
    val origin = window.location.origin.trimEnd('/')
    return "$origin/locations/$id"
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
