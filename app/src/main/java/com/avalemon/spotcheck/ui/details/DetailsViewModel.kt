package com.avalemon.spotcheck.ui.details

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.avalemon.spotcheck.appContainer
import com.avalemon.spotcheck.data.AuthRepository
import com.avalemon.spotcheck.data.Building
import com.avalemon.spotcheck.data.CatalogRepository
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.RatingSummary
import com.avalemon.spotcheck.data.Review
import com.avalemon.spotcheck.data.ReviewRepository
import com.avalemon.spotcheck.data.locationLine
import com.avalemon.spotcheck.ui.components.formatAverage
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val catalogRepo: CatalogRepository,
    private val reviewRepo: ReviewRepository,
    private val auth: AuthRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    val buildingId: String = checkNotNull(saved["buildingId"])
    val facilityId: String = checkNotNull(saved["facilityId"])

    var facility by mutableStateOf<Facility?>(null); private set
    var building by mutableStateOf<Building?>(null); private set
    var reviews by mutableStateOf<List<Review>?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var deleting by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    private val myUid get() = auth.currentUser?.uid

    val myReview: Review? by derivedStateOf { reviews?.firstOrNull { it.userId == myUid } }
    val otherReviews: List<Review> by derivedStateOf { reviews.orEmpty().filter { it.userId != myUid } }

    /** Average and count computed from the reviews already loaded for display. */
    val summary: RatingSummary by derivedStateOf {
        val list = reviews.orEmpty()
        if (list.isEmpty()) RatingSummary.Empty else RatingSummary(list.size.toLong(), list.map { it.rating }.average())
    }

    init {
        load()
        viewModelScope.launch {
            reviewRepo.reviewsFlow(buildingId, facilityId).collect { result ->
                result.onSuccess { reviews = it }
                    .onFailure { error = it.localizedMessage ?: "Couldn't load reviews." }
            }
        }
    }

    fun load() = viewModelScope.launch {
        error = null
        runCatching {
            val c = catalogRepo.catalog().let { cat ->
                // A facility added after the cached load: refetch once.
                if (cat.facility(buildingId, facilityId) == null) catalogRepo.catalog(forceRefresh = true) else cat
            }
            facility = c.facility(buildingId, facilityId) ?: error("This facility no longer exists.")
            building = c.buildings[buildingId]
        }.onFailure { error = it.localizedMessage ?: "Couldn't load facility." }
    }

    fun deleteMyReview() {
        if (deleting) return
        deleting = true
        viewModelScope.launch {
            runCatching { reviewRepo.delete(buildingId, facilityId) }
                .onSuccess { message = "Review deleted" }
                .onFailure { message = "Couldn't delete review: ${it.localizedMessage}" }
            deleting = false
        }
    }

    fun consumeMessage() { message = null }

    /** Plain-text recommendation for the Android share sheet. */
    fun shareText(): String? {
        val f = facility ?: return null
        val where = f.locationLine(building?.name ?: f.buildingId)
        val rating = summary.average?.let {
            "Rated ${formatAverage(it)}/5 from ${summary.count} review${if (summary.count == 1L) "" else "s"} on SpotCheck."
        } ?: "No reviews yet on SpotCheck — be the first!"
        return buildString {
            append("Check out ${f.name} — $where.")
            if (f.description.isNotBlank()) append("\n${f.description}")
            append("\n$rating")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                DetailsViewModel(c.catalogRepository, c.reviewRepository, c.authRepository, createSavedStateHandle())
            }
        }
    }
}
