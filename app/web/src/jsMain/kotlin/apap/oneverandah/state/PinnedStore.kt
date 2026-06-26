package apap.oneverandah.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlin.js.Date
import kotlin.js.Json
import kotlin.js.json

/**
 * A single entry in the Home Screen "Pinned & Recent" tray.
 *
 * An item may appear because the user manually saved it ([isManuallySaved]
 * `true`, [savedAt] set) and/or because they completed a booking for it
 * ([bookedAt] set). [lastInteraction] is the timestamp the tray is sorted by.
 */
data class PinnedItem(
    val id: String,
    val serviceId: String,
    val name: String,
    /** Material Symbols ligature name used as the tile image. */
    val image: String,
    val savedAt: Double?,
    val bookedAt: Double?,
    val isManuallySaved: Boolean,
) {
    /** Most-recent-interaction timestamp (max of save/booking), newest first. */
    val lastInteraction: Double
        get() = maxOf(savedAt ?: 0.0, bookedAt ?: 0.0)
}

/**
 * Unified "smart tray" backing the Home Screen "Pinned & Recent" section.
 *
 * Items enter via two triggers:
 *  - Manual save: the Save button on the offer page ([toggleSave]).
 *  - Auto-save: a successful booking ([autoSaveBooking]).
 *
 * The list is always kept de-duplicated by [PinnedItem.serviceId] and sorted by
 * [PinnedItem.lastInteraction]. State is exposed as Compose state so the whole
 * UI recomposes on change, and mirrored to `localStorage` for persistence.
 */
object PinnedStore {
    private const val STORAGE_KEY = "oneverandah.pinnedRecent"

    var items: List<PinnedItem> by mutableStateOf(load())
        private set

    /** True when [serviceId] is currently in the tray as a manual save. */
    fun isSaved(serviceId: String): Boolean =
        items.any { it.serviceId == serviceId && it.isManuallySaved }

    /**
     * Toggles the manual-save state for a service.
     *
     * If it was already manually saved it is removed from the tray; otherwise it
     * is (re)added/bumped to the front with the save flag set.
     *
     * @return `true` if the service is now saved, `false` if it was removed.
     */
    fun toggleSave(serviceId: String, name: String, image: String): Boolean {
        val existing = items.firstOrNull { it.serviceId == serviceId }
        return if (existing != null && existing.isManuallySaved) {
            items = items.filterNot { it.serviceId == serviceId }
            persist()
            false
        } else {
            val now = Date.now()
            val updated = existing?.copy(
                name = name,
                image = image,
                savedAt = now,
                isManuallySaved = true,
            ) ?: PinnedItem(
                id = serviceId,
                serviceId = serviceId,
                name = name,
                image = image,
                savedAt = now,
                bookedAt = null,
                isManuallySaved = true,
            )
            items = bumpToFront(updated)
            persist()
            true
        }
    }

    /**
     * Auto-saves a service after a successful booking. Existing entries keep
     * their manual-save state and are simply bumped to the front (no duplicate).
     */
    fun autoSaveBooking(serviceId: String, name: String, image: String) {
        val existing = items.firstOrNull { it.serviceId == serviceId }
        val now = Date.now()
        val updated = existing?.copy(
            name = name,
            image = image,
            bookedAt = now,
        ) ?: PinnedItem(
            id = serviceId,
            serviceId = serviceId,
            name = name,
            image = image,
            savedAt = null,
            bookedAt = now,
            isManuallySaved = false,
        )
        items = bumpToFront(updated)
        persist()
    }

    /** Clears the whole tray (does not touch booking history elsewhere). */
    fun clearAll() {
        items = emptyList()
        persist()
    }

    private fun bumpToFront(item: PinnedItem): List<PinnedItem> =
        (items.filterNot { it.serviceId == item.serviceId } + item)
            .sortedByDescending { it.lastInteraction }

    private fun persist() {
        val arr = items.map { item ->
            json(
                "id" to item.id,
                "serviceId" to item.serviceId,
                "name" to item.name,
                "image" to item.image,
                "savedAt" to (item.savedAt ?: -1.0),
                "bookedAt" to (item.bookedAt ?: -1.0),
                "isManuallySaved" to item.isManuallySaved,
            )
        }.toTypedArray()
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(arr))
        } catch (t: Throwable) {
            // Persistence is best-effort; ignore storage failures (e.g. quota).
        }
    }

    private fun load(): List<PinnedItem> {
        val raw = window.localStorage.getItem(STORAGE_KEY) ?: return emptyList()
        return try {
            val parsed = JSON.parse<Array<Json>>(raw)
            parsed.mapNotNull { obj ->
                val serviceId = obj["serviceId"] as? String ?: return@mapNotNull null
                val savedAt = (obj["savedAt"] as? Double)?.takeIf { it >= 0 }
                val bookedAt = (obj["bookedAt"] as? Double)?.takeIf { it >= 0 }
                PinnedItem(
                    id = (obj["id"] as? String) ?: serviceId,
                    serviceId = serviceId,
                    name = (obj["name"] as? String) ?: serviceId,
                    image = (obj["image"] as? String) ?: "spa",
                    savedAt = savedAt,
                    bookedAt = bookedAt,
                    isManuallySaved = (obj["isManuallySaved"] as? Boolean) ?: false,
                )
            }.sortedByDescending { it.lastInteraction }
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
