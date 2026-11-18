package com.avalemon.spotcheck.ui.explore

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
import com.avalemon.spotcheck.data.ExploreView
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.RatingSummary
import com.avalemon.spotcheck.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) { RATING("Top rated"), NAME("Name") }

class ExploreViewModel(
    private val catalogRepo: CatalogRepository,
    private val settingsRepo: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    var catalog by mutableStateOf<Catalog?>(null); private set
    var summaries by mutableStateOf<Map<String, RatingSummary>>(emptyMap()); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    // Explore controls survive rotation, backgrounding and process death.
    var query by mutableStateOf(saved.get<String>("query").orEmpty()); private set
    var category by mutableStateOf(saved.get<String>("category")); private set
    var buildingId by mutableStateOf(saved.get<String>("building")); private set
    var sort by mutableStateOf(saved.get<String>("sort")?.let(SortOrder::valueOf) ?: SortOrder.RATING); private set
    var view by mutableStateOf(saved.get<String>("view")?.let(ExploreView::valueOf) ?: ExploreView.LIST); private set

    fun onQuery(v: String) { query = v; saved["query"] = v }
    fun onCategory(v: String?) { category = v; saved["category"] = v }
    fun onBuilding(v: String?) { buildingId = v; saved["building"] = v }
    fun onSort(v: SortOrder) { sort = v; saved["sort"] = v.name }
    fun onView(v: ExploreView) { view = v; saved["view"] = v.name }

    val categories: List<String> by derivedStateOf {
        catalog?.facilities?.map { it.category }?.filter { it.isNotBlank() }?.distinct()?.sorted().orEmpty()
    }

    val visibleFacilities: List<Facility> by derivedStateOf {
        val c = catalog ?: return@derivedStateOf emptyList()
        val q = query.trim()
        c.facilities
            .filter { category == null || it.category == category }
            .filter { buildingId == null || it.buildingId == buildingId }
            .filter {
                q.isEmpty() || it.name.contains(q, true) || c.buildingName(it).contains(q, true) ||
                    it.locator.contains(q, true)
            }
            .let { list ->
                when (sort) {
                    SortOrder.NAME -> list.sortedWith(compareBy({ c.buildingName(it) }, { it.floor ?: Int.MAX_VALUE }, { it.name }))
                    SortOrder.RATING -> list.sortedWith(
                        compareByDescending<Facility> { summaries[it.key]?.average ?: -1.0 }
                            .thenByDescending { summaries[it.key]?.count ?: 0 }
                            .thenBy { it.name },
                    )
                }
            }
    }

    init {
        viewModelScope.launch {
            // Apply DataStore defaults once per Explore session; later user changes win.
            if (saved.get<Boolean>("defaultsApplied") != true) {
                val s = settingsRepo.settings.first()
                onCategory(s.defaultCategory)
                onView(s.defaultView)
                saved["defaultsApplied"] = true
            }
            load(force = false)
        }
    }

    fun retry() = viewModelScope.launch { load(force = true) }

    fun refresh() = viewModelScope.launch {
        refreshing = true
        load(force = true)
        refreshing = false
    }

    /** Called when Explore resumes (e.g. after saving a review) so averages stay current. */
    fun refreshRatings() {
        val c = catalog ?: return
        viewModelScope.launch { summaries = catalogRepo.ratingSummaries(c.facilities) }
    }

    private suspend fun load(force: Boolean) {
        if (catalog == null) loading = true
        error = null
        try {
            val c = catalogRepo.catalog(forceRefresh = force)
            catalog = c
            summaries = catalogRepo.ratingSummaries(c.facilities)
        } catch (t: Throwable) {
            error = t.localizedMessage ?: "Couldn't load facilities."
        } finally {
            loading = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ExploreViewModel(c.catalogRepository, c.settingsRepository, createSavedStateHandle())
            }
        }
    }
}
