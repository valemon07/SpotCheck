package com.avalemon.spotcheck.data

import java.util.Date

/** A campus building. Coordinates live here once; all facilities inside share them. */
data class Building(
    val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val address: String,
)

/**
 * One exact, reviewable facility (e.g. "East Study Room" on floor 2 of Clemons).
 * Identified by [buildingId] + [id] because facilities are nested under buildings in Firestore.
 */
data class Facility(
    val buildingId: String,
    val id: String,
    val name: String,
    val floor: Int?,
    val locator: String,
    val category: String,
    val description: String,
) {
    val key: String get() = "$buildingId/$id"
}

/** Average + count for a facility, derived from its reviews (never cached in Firestore). */
data class RatingSummary(val count: Long, val average: Double?) {
    companion object {
        val Empty = RatingSummary(0, null)
    }
}

data class Review(
    val userId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Date?,
    val updatedAt: Date?,
) {
    /** True when the review was edited after it was first posted. */
    val isEdited: Boolean
        get() = createdAt != null && updatedAt != null && updatedAt.time - createdAt.time > 1_000
}

/** Curated categories. Unknown strings from Firestore fall back to a readable label. */
enum class Category(val id: String, val label: String) {
    STUDY("study", "Study spaces"),
    BATHROOM("bathroom", "Bathrooms"),
    LOUNGE("lounge", "Lounges"),
    PRINTER("printer", "Printers"),
    REFILL("refill", "Refill stations"),
    PARKING("parking", "Parking");

    companion object {
        fun labelFor(id: String): String =
            entries.firstOrNull { it.id == id }?.label ?: id.replaceFirstChar { it.uppercase() }
    }
}

data class Catalog(
    val buildings: Map<String, Building>,
    val facilities: List<Facility>,
) {
    fun buildingName(facility: Facility): String =
        buildings[facility.buildingId]?.name ?: facility.buildingId

    fun facility(buildingId: String, facilityId: String): Facility? =
        facilities.firstOrNull { it.buildingId == buildingId && it.id == facilityId }
}

/** "Clemons Library · Floor 2 · East Study Room"-style label used in lists and share text. */
fun Facility.locationLine(buildingName: String): String =
    listOf(buildingName, locator.ifBlank { floor?.let { "Floor $it" } ?: "" })
        .filter { it.isNotBlank() }
        .joinToString(" · ")
