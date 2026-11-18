package com.avalemon.spotcheck.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class DistanceUnit(val label: String) { MILES("Miles"), KILOMETERS("Kilometers") }
enum class ExploreView(val label: String) { LIST("List"), MAP("Map") }

/** Local-only preferences that shape Explore. [defaultCategory] null means "All". */
data class Settings(
    val units: DistanceUnit = DistanceUnit.MILES,
    val defaultCategory: String? = null,
    val defaultView: ExploreView = ExploreView.LIST,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val units = stringPreferencesKey("units")
        val category = stringPreferencesKey("default_category")
        val view = stringPreferencesKey("default_view")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            units = p[Keys.units]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.MILES,
            defaultCategory = p[Keys.category]?.takeIf { it.isNotBlank() },
            defaultView = p[Keys.view]?.let { runCatching { ExploreView.valueOf(it) }.getOrNull() } ?: ExploreView.LIST,
        )
    }

    suspend fun setUnits(units: DistanceUnit) {
        context.dataStore.edit { it[Keys.units] = units.name }
    }

    suspend fun setDefaultCategory(category: String?) {
        context.dataStore.edit { it[Keys.category] = category.orEmpty() }
    }

    suspend fun setDefaultView(view: ExploreView) {
        context.dataStore.edit { it[Keys.view] = view.name }
    }
}
