package com.avalemon.spotcheck.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user (or null) whenever auth state changes. */
    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createAccount(displayName: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Account creation returned no user")
        if (displayName.isNotBlank()) {
            user.updateProfile(userProfileChangeRequest { this.displayName = displayName.trim() }).await()
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /** Name shown on the user's reviews: Auth display name, else the email prefix. */
    fun authorName(): String {
        val user = auth.currentUser ?: return "Anonymous"
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Anonymous"
        return name.take(MAX_NAME)
    }

    /**
     * Creates users/{uid} only when missing (isAdmin=false). Safe to call repeatedly; an existing
     * profile and admin flag are preserved. Called after every sign-in and on app start.
     */
    suspend fun ensureProfile() {
        val user = auth.currentUser ?: return
        val ref = db.collection("users").document(user.uid)
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) {
                tx.set(
                    ref,
                    mapOf(
                        "displayName" to authorName(),
                        "isAdmin" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.await()
    }

    companion object {
        const val MAX_NAME = 50
    }
}

/** Maps Firebase Auth exceptions to short, actionable messages. */
fun Throwable.toAuthMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
    is FirebaseAuthUserCollisionException ->
        "An account already exists for this email. Sign in instead, or use Google if you registered with it."
    is FirebaseAuthInvalidUserException -> "No account found for this email, or it was disabled."
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a moment and try again."
    else -> localizedMessage ?: "Something went wrong. Please try again."
}
