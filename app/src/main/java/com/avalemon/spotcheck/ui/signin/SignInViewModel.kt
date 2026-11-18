package com.avalemon.spotcheck.ui.signin

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
import com.avalemon.spotcheck.data.toAuthMessage
import kotlinx.coroutines.launch

class SignInViewModel(
    private val auth: AuthRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    // Non-secret fields survive process death via SavedStateHandle; the password stays in memory only.
    var email by mutableStateOf(saved.get<String>("email").orEmpty()); private set
    var displayName by mutableStateOf(saved.get<String>("name").orEmpty()); private set
    var isCreateMode by mutableStateOf(saved.get<Boolean>("create") ?: false); private set
    var password by mutableStateOf(""); private set

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var info by mutableStateOf<String?>(null); private set
    /** Flips true once auth + profile setup complete; the screen then navigates to Explore. */
    var signedIn by mutableStateOf(false); private set

    fun onEmail(v: String) { email = v; saved["email"] = v; error = null }
    fun onName(v: String) { displayName = v.take(AuthRepository.MAX_NAME); saved["name"] = displayName }
    fun onPassword(v: String) { password = v; error = null }
    fun toggleMode() { isCreateMode = !isCreateMode; saved["create"] = isCreateMode; error = null }
    fun consumeInfo() { info = null }

    val canSubmit: Boolean
        get() = !loading && email.isNotBlank() && password.isNotEmpty() && (!isCreateMode || displayName.isNotBlank())

    fun submitEmail() {
        if (!canSubmit) return
        run {
            if (isCreateMode) auth.createAccount(displayName, email, password)
            else auth.signInWithEmail(email, password)
        }
    }

    fun signInWithGoogle(idToken: String) = run { auth.signInWithGoogleIdToken(idToken) }

    fun onGoogleError(message: String?) {
        loading = false
        error = message
    }

    fun onGoogleStarted() { loading = true; error = null }

    fun resetPassword() {
        if (email.isBlank()) { error = "Enter your email first, then tap “Forgot password?”."; return }
        viewModelScope.launch {
            runCatching { auth.sendPasswordReset(email) }
                .onSuccess { info = "Password reset email sent to ${email.trim()}." }
                .onFailure { error = it.toAuthMessage() }
        }
    }

    /** Shared post-auth flow for both providers: authenticate, then create the profile if missing. */
    private fun run(block: suspend () -> Unit) {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                block()
                // Profile failures don't block entry; ensureProfile is retried on next launch.
                runCatching { auth.ensureProfile() }
                password = ""
                signedIn = true
            } catch (t: Throwable) {
                error = t.toAuthMessage()
            } finally {
                loading = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SignInViewModel(appContainer().authRepository, createSavedStateHandle()) }
        }
    }
}
