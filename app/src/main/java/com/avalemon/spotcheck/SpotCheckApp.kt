package com.avalemon.spotcheck

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.avalemon.spotcheck.data.AuthRepository
import com.avalemon.spotcheck.data.CatalogRepository
import com.avalemon.spotcheck.data.LocationRepository
import com.avalemon.spotcheck.data.ReviewRepository
import com.avalemon.spotcheck.data.SettingsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/** Manual dependency container: one instance of each repository for the whole app. */
class AppContainer(app: Application) {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    val authRepository = AuthRepository(auth, db)
    val catalogRepository = CatalogRepository(db)
    val reviewRepository = ReviewRepository(db, auth, catalogRepository)
    val settingsRepository = SettingsRepository(app)
    val locationRepository = LocationRepository(app)
}

class SpotCheckApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Lets ViewModel factories reach the container: `initializer { Foo(appContainer()) }`. */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as SpotCheckApp).container
