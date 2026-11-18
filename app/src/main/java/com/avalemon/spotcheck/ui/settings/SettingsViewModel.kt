package com.avalemon.spotcheck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.avalemon.spotcheck.appContainer
import com.avalemon.spotcheck.data.AuthRepository
import com.avalemon.spotcheck.data.DistanceUnit
import com.avalemon.spotcheck.data.ExploreView
import com.avalemon.spotcheck.data.Settings
import com.avalemon.spotcheck.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    /** Null until DataStore has been read, so the screen never flashes default values. */
    val settings: StateFlow<Settings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accountLabel: String
        get() = auth.currentUser?.let { it.email ?: it.displayName } ?: "Signed in"

    fun setUnits(v: DistanceUnit) = viewModelScope.launch { repo.setUnits(v) }
    fun setDefaultCategory(v: String?) = viewModelScope.launch { repo.setDefaultCategory(v) }
    fun setDefaultView(v: ExploreView) = viewModelScope.launch { repo.setDefaultView(v) }
    fun signOut() = auth.signOut()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SettingsViewModel(c.settingsRepository, c.authRepository)
            }
        }
    }
}
