package com.avalemon.spotcheck.ui.review

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
import com.avalemon.spotcheck.data.Catalog
import com.avalemon.spotcheck.data.CatalogRepository
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.ReviewRepository
import kotlinx.coroutines.launch

/**
 * One form, two entry points. From Details the facility is preselected and locked; from Explore
 * the user picks building → facility (optionally starting from Explore's selected building).
 * The draft lives in SavedStateHandle so it survives rotation, backgrounding and failed saves.
 */
class ReviewViewModel(
    private val catalogRepo: CatalogRepository,
    private val reviewRepo: ReviewRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val facilityLocked: Boolean = saved.get<Boolean>("locked") ?: false

    var catalog by mutableStateOf<Catalog?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set

    var buildingId by mutableStateOf(saved.get<String>("buildingId")); private set
    var facilityId by mutableStateOf(saved.get<String>("facilityId")); private set
    var rating by mutableStateOf(saved.get<Int>("rating") ?: 0); private set
    var comment by mutableStateOf(saved.get<String>("comment").orEmpty()); private set
    /** True when the selected facility already has this user's review (edit mode). */
    var isEditing by mutableStateOf(saved.get<Boolean>("editing") ?: false); private set
    var checkingExisting by mutableStateOf(false); private set

    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf<String?>(null); private set
    var done by mutableStateOf(false); private set

    val facility: Facility? by derivedStateOf {
        val b = buildingId ?: return@derivedStateOf null
        val f = facilityId ?: return@derivedStateOf null
        catalog?.facility(b, f)
    }

    val facilitiesInBuilding: List<Facility> by derivedStateOf {
        catalog?.facilities?.filter { it.buildingId == buildingId }.orEmpty()
    }

    val canSubmit: Boolean by derivedStateOf {
        facility != null && rating in 1..5 && !saving && !checkingExisting &&
            comment.length <= ReviewRepository.MAX_COMMENT
    }

    init {
        loadCatalog()
    }

    fun loadCatalog() = viewModelScope.launch {
        loadError = null
        runCatching { catalogRepo.catalog() }
            .onSuccess { catalog = it; facility?.let(::loadExisting) }
            .onFailure { loadError = it.localizedMessage ?: "Couldn't load facilities." }
    }

    fun onBuilding(id: String) {
        if (facilityLocked || id == buildingId) return
        buildingId = id; saved["buildingId"] = id
        facilityId = null; saved["facilityId"] = null
        markEditing(false)
    }

    fun onFacility(f: Facility) {
        if (facilityLocked) return
        if (f.id == facilityId) return
        facilityId = f.id; saved["facilityId"] = f.id
        // Don't carry another facility's prefilled review over; keep anything the user typed.
        if (!dirty) setDraft(0, "")
        markEditing(false)
        loadExisting(f)
    }

    /** True once the user has typed or tapped; a prefilled-but-untouched draft is not dirty. */
    private var dirty: Boolean
        get() = saved.get<Boolean>("dirty") ?: false
        set(v) { saved["dirty"] = v }

    fun onRating(v: Int) { setDraft(v, comment); dirty = true }
    fun onComment(v: String) { setDraft(rating, v); dirty = true }

    private fun setDraft(r: Int, c: String) {
        rating = r; saved["rating"] = r
        comment = c.take(ReviewRepository.MAX_COMMENT); saved["comment"] = comment
        saveError = null
    }

    /** Prefills from the user's existing review once per facility, without clobbering a draft. */
    private fun loadExisting(f: Facility) {
        if (saved.get<String>("prefilledFor") == f.key) return
        checkingExisting = true
        viewModelScope.launch {
            runCatching { reviewRepo.myReview(f.buildingId, f.id) }
                .onSuccess { existing ->
                    markEditing(existing != null)
                    if (existing != null && !dirty) setDraft(existing.rating, existing.comment)
                    saved["prefilledFor"] = f.key
                }
            checkingExisting = false
        }
    }

    private fun markEditing(v: Boolean) { isEditing = v; saved["editing"] = v }

    fun submit() {
        val f = facility ?: return
        if (!canSubmit) return
        saving = true
        saveError = null
        viewModelScope.launch {
            runCatching { reviewRepo.save(f.buildingId, f.id, rating, comment.trim()) }
                .onSuccess { done = true }
                .onFailure { saveError = "Couldn't save your review. ${it.localizedMessage ?: ""}".trim() }
            saving = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ReviewViewModel(c.catalogRepository, c.reviewRepository, createSavedStateHandle())
            }
        }
    }
}
