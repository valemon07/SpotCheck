package com.avalemon.spotcheck.data

import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Read-only access to the curated catalog. The whole (small) catalog loads in two queries:
 * `buildings` plus a `facilities` collection-group query. Documents are mapped by hand
 * (no reflection) so the code stays safe under R8.
 */
class CatalogRepository(private val db: FirebaseFirestore) {

    @Volatile
    private var cached: Catalog? = null

    suspend fun catalog(forceRefresh: Boolean = false): Catalog {
        if (!forceRefresh) cached?.let { return it }
        return coroutineScope {
            val buildings = async { db.collection("buildings").get().await() }
            val facilities = async { db.collectionGroup("facilities").get().await() }
            Catalog(
                buildings = buildings.await().documents.associate { it.id to it.toBuilding() },
                facilities = facilities.await().documents.mapNotNull { it.toFacility() }
                    .sortedWith(compareBy({ it.buildingId }, { it.floor ?: Int.MAX_VALUE }, { it.name })),
            ).also { cached = it }
        }
    }

    fun reviews(buildingId: String, facilityId: String): CollectionReference =
        db.collection("buildings").document(buildingId)
            .collection("facilities").document(facilityId)
            .collection("reviews")

    /** One aggregate query (count + average) per facility, run in parallel. Failures map to null. */
    suspend fun ratingSummaries(facilities: List<Facility>): Map<String, RatingSummary> = coroutineScope {
        facilities.map { f ->
            async {
                f.key to runCatching {
                    val snap = reviews(f.buildingId, f.id)
                        .aggregate(AggregateField.count(), AggregateField.average("rating"))
                        .get(AggregateSource.SERVER).await()
                    RatingSummary(
                        count = snap.get(AggregateField.count()),
                        average = snap.get(AggregateField.average("rating")),
                    )
                }.getOrNull()
            }
        }.awaitAll()
            .mapNotNull { (key, summary) -> summary?.let { key to it } }
            .toMap()
    }
}

private fun DocumentSnapshot.toBuilding() = Building(
    id = id,
    name = getString("name") ?: id,
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    address = getString("address").orEmpty(),
)

private fun DocumentSnapshot.toFacility(): Facility? {
    val buildingId = reference.parent.parent?.id ?: return null
    return Facility(
        buildingId = buildingId,
        id = id,
        name = getString("name") ?: id,
        floor = getLong("floor")?.toInt(),
        locator = getString("locator").orEmpty(),
        category = getString("category").orEmpty(),
        description = getString("description").orEmpty(),
    )
}
