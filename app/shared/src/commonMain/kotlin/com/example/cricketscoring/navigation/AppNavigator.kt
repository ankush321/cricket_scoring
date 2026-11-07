package com.example.cricketscoring.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.ui.CricketStore

/**
 * Navigation API used by screens. Real [AppNavigator] wires NavHost;
 * [PreviewAppNavigator] is a no-op so Compose Preview does not need a NavController.
 */
interface AppNavigation {
    fun back()
    fun navigate(route: AppRoute)
    fun navigateRoot(route: AppRoute)
    fun openStyle(style: CricketStyle)
    fun openWizard(style: CricketStyle, match: Match? = null)
    fun duplicate(match: Match)
    fun openMatch(match: Match)
    fun afterCommit(match: Match, started: Boolean)
}

val LocalAppNavigator = staticCompositionLocalOf<AppNavigation> {
    error("AppNavigator not provided")
}

class AppNavigator(
    private val navController: NavHostController,
    private val store: CricketStore,
) : AppNavigation {
    override fun back() {
        if (!navController.popBackStack()) {
            navigateRoot(AppRoute.Home)
        }
    }

    override fun navigate(route: AppRoute) {
        navController.navigate(route)
    }

    override fun navigateRoot(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    override fun openStyle(style: CricketStyle) {
        navigate(AppRoute.StyleHub.of(style))
    }

    override fun openWizard(style: CricketStyle, match: Match?) {
        store.openWizard(style, match)
        navigate(AppRoute.Wizard.of(style))
    }

    override fun duplicate(match: Match) {
        store.duplicate(match)
        navigate(AppRoute.Wizard.of(match.style))
    }

    override fun openMatch(match: Match) {
        val derived = MatchEngine.derive(match)
        when (derived.status) {
            MatchStatus.UPCOMING, MatchStatus.CANCELLED -> openWizard(match.style, match)
            MatchStatus.COMPLETED, MatchStatus.ABANDONED -> navigate(AppRoute.Scorecard(match.id))
            else -> navigate(AppRoute.Live(match.id))
        }
    }

    override fun afterCommit(match: Match, started: Boolean) {
        navController.navigate(AppRoute.StyleHub.of(match.style)) {
            popUpTo(AppRoute.Home) { inclusive = false }
            launchSingleTop = true
        }
        if (started) {
            navigate(AppRoute.Live(match.id))
        }
    }
}

/** No-op navigator for Android Studio / IDE Compose Preview (matches WSLF preview pattern). */
class PreviewAppNavigator : AppNavigation {
    override fun back() = Unit
    override fun navigate(route: AppRoute) = Unit
    override fun navigateRoot(route: AppRoute) = Unit
    override fun openStyle(style: CricketStyle) = Unit
    override fun openWizard(style: CricketStyle, match: Match?) = Unit // match optional via interface default
    override fun duplicate(match: Match) = Unit
    override fun openMatch(match: Match) = Unit
    override fun afterCommit(match: Match, started: Boolean) = Unit
}
