package com.avalemon.spotcheck.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reviews live at buildings/{b}/facilities/{f}/reviews/{uid}. Using the Auth UID as the document
 * ID enforces one review per account per facility.
 */
class ReviewRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val catalog: CatalogRepository,
    private val authRepo: AuthRepository,
) {
    private fun uid(): String = auth.currentUser?.uid ?: error("Not signed in")

    /** Live list of a facility's reviews, newest first. */
    fun reviewsFlow(buildingId: String, facilityId: String): Flow<Result<List<Review>>> = callbackFlow {
        val registration = catalog.reviews(buildingId, facilityId).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(Result.failure(err))
            } else if (snap != null) {
                val list = snap.documents.mapNotNull { it.toReview() }
                    .sortedByDescending { it.updatedAt?.time ?: Long.MAX_VALUE }
                trySend(Result.success(list))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun myReview(buildingId: String, facilityId: String): Review? =
        catalog.reviews(buildingId, facilityId).document(uid()).get().await().toReview()

    /** Creates or updates the signed-in user's review. createdAt and userId never change on edit. */
    suspend fun save(buildingId: String, facilityId: String, rating: Int, comment: String) {
        require(rating in 1..5) { "Rating must be 1–5" }
        val uid = uid()
        val ref = catalog.reviews(buildingId, facilityId).document(uid)
        val authorName = authRepo.authorName()
        db.runTransaction { tx ->
            if (tx.get(ref).exists()) {
                tx.update(
                    ref,
                    mapOf(
                        "rating" to rating,
                        "comment" to comment,
                        "authorName" to authorName,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            } else {
                tx.set(
                    ref,
                    mapOf(
                        "userId" to uid,
                        "authorName" to authorName,
                        "rating" to rating,
                        "comment" to comment,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.await()
    }

    suspend fun delete(buildingId: String, facilityId: String) {
        catalog.reviews(buildingId, facilityId).document(uid()).delete().await()
    }

    companion object {
        const val MAX_COMMENT = 1000
    }
}

private fun DocumentSnapshot.toReview(): Review? {
    if (!exists()) return null
    val behavior = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
    return Review(
        userId = getString("userId") ?: id,
        authorName = getString("authorName") ?: "Anonymous",
        rating = getLong("rating")?.toInt() ?: return null,
        comment = getString("comment").orEmpty(),
        createdAt = getDate("createdAt", behavior),
        updatedAt = getDate("updatedAt", behavior),
    )
}
