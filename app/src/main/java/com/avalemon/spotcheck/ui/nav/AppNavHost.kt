package com.avalemon.spotcheck.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.avalemon.spotcheck.SpotCheckApp
import com.avalemon.spotcheck.ui.details.DetailsScreen
import com.avalemon.spotcheck.ui.explore.ExploreScreen
import com.avalemon.spotcheck.ui.review.ReviewScreen
import com.avalemon.spotcheck.ui.settings.SettingsScreen
import com.avalemon.spotcheck.ui.signin.SignInScreen

private object Routes {
    const val SIGN_IN = "signin"
    const val EXPLORE = "explore"
    const val DETAILS = "details/{buildingId}/{facilityId}"
    const val REVIEW = "review?buildingId={buildingId}&facilityId={facilityId}&locked={locked}"
    const val SETTINGS = "settings"

    fun details(b: String, f: String) = "details/${Uri.encode(b)}/${Uri.encode(f)}"
    fun review(b: String?, f: String?, locked: Boolean) = buildString {
        append("review?locked=$locked")
        b?.let { append("&buildingId=${Uri.encode(it)}") }
        f?.let { append("&facilityId=${Uri.encode(it)}") }
    }
}

// M3 shared-axis (X) transition. Every spec is a finite tween so predictive back can scrub it and
// finish from wherever the gesture is released; pop transitions mirror push transitions exactly.
private const val DURATION = 350
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val SLIDE_FRACTION = 0.1f

private fun enter(fromEnd: Boolean): EnterTransition =
    slideInHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) {
        val d = (it * SLIDE_FRACTION).toInt(); if (fromEnd) d else -d
    } + fadeIn(tween(DURATION / 2, delayMillis = DURATION / 4, easing = EmphasizedDecelerate))

private fun exit(toStart: Boolean): ExitTransition =
    slideOutHorizontally(tween(DURATION, easing = EmphasizedAccelerate)) {
        val d = (it * SLIDE_FRACTION).toInt(); if (toStart) -d else d
    } + fadeOut(tween(DURATION / 4, easing = EmphasizedAccelerate))

private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(fromEnd = true) }
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(toStart = true) }
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(fromEnd = false) }
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(toStart = false) }

// Predictive back gestures get no custom animation for now (the screen switches when the gesture
// commits). A proper follow-the-finger animation is planned with the move to Navigation 3.
private val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition =
    { EnterTransition.None }
private val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition =
    { ExitTransition.None }

/** Key used to hand a one-shot snackbar message back to the screen that opened the review form. */
private const val RESULT_MESSAGE = "result_message"

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    val container = (LocalContext.current.applicationContext as SpotCheckApp).container
    val auth = container.authRepository
    val start = remember { if (auth.currentUser != null) Routes.EXPLORE else Routes.SIGN_IN }

    // Retry-safe profile initialization on every launch while signed in.
    LaunchedEffect(Unit) { if (auth.currentUser != null) runCatching { auth.ensureProfile() } }

    fun toSignIn() = nav.navigate(Routes.SIGN_IN) { popUpTo(0) { inclusive = true } }

    // Opaque themed backdrop so fades never reveal the (light) window background.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = pushEnter,
            exitTransition = pushExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
            predictivePopEnterTransition = predictivePopEnter,
            predictivePopExitTransition = predictivePopExit,
        ) {
            composable(Routes.SIGN_IN) {
                SignInScreen(onSignedIn = { nav.navigate(Routes.EXPLORE) { popUpTo(0) { inclusive = true } } })
            }
            composable(Routes.EXPLORE) { entry ->
                val (message, clear) = entry.resultMessage()
                ExploreScreen(
                    onOpenFacility = { nav.navigate(Routes.details(it.buildingId, it.id)) },
                    onWriteReview = { b -> nav.navigate(Routes.review(b, null, locked = false)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    message = message,
                    onMessageShown = clear,
                )
            }
            composable(
                Routes.DETAILS,
                arguments = listOf(
                    navArgument("buildingId") { type = NavType.StringType },
                    navArgument("facilityId") { type = NavType.StringType },
                ),
            ) { entry ->
                val (message, clear) = entry.resultMessage()
                DetailsScreen(
                    onBack = { nav.popBackStack() },
                    onWriteReview = { b, f -> nav.navigate(Routes.review(b, f, locked = true)) },
                    message = message,
                    onMessageShown = clear,
                )
            }
            composable(
                Routes.REVIEW,
                arguments = listOf(
                    navArgument("buildingId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("facilityId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("locked") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                ReviewScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = { msg ->
                        // Return to whichever screen opened the form (Explore or Details) with a confirmation.
                        nav.previousBackStackEntry?.savedStateHandle?.set(RESULT_MESSAGE, msg)
                        nav.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onSignedOut = ::toSignIn)
            }
        }
    }
}

@Composable
private fun NavBackStackEntry.resultMessage(): Pair<String?, () -> Unit> {
    val message by savedStateHandle.getStateFlow<String?>(RESULT_MESSAGE, null).collectAsStateWithLifecycle()
    return message to { savedStateHandle[RESULT_MESSAGE] = null }
}
