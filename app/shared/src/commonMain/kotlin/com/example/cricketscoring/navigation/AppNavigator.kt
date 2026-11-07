package com.example.cricketscoring.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.ui.CricketStore

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator> {
    error("AppNavigator not provided")
}

class AppNavigator(
    private val navController: NavHostController,
    private val store: CricketStore,
) {
    fun back() {
        if (!navController.popBackStack()) {
            navigateRoot(AppRoute.Home)
        }
    }

    fun navigate(route: AppRoute) {
        navController.navigate(route)
    }

    fun navigateRoot(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openStyle(style: CricketStyle) {
        navigate(AppRoute.StyleHub.of(style))
    }

    fun openWizard(style: CricketStyle, match: Match? = null) {
        store.openWizard(style, match)
        navigate(AppRoute.Wizard.of(style))
    }

    fun duplicate(match: Match) {
        store.duplicate(match)
        navigate(AppRoute.Wizard.of(match.style))
    }

    fun openMatch(match: Match) {
        val derived = MatchEngine.derive(match)
        when (derived.status) {
            MatchStatus.UPCOMING, MatchStatus.CANCELLED -> openWizard(match.style, match)
            MatchStatus.COMPLETED, MatchStatus.ABANDONED -> navigate(AppRoute.Scorecard(match.id))
            else -> navigate(AppRoute.Live(match.id))
        }
    }

    fun afterCommit(match: Match, started: Boolean) {
        navController.navigate(AppRoute.StyleHub.of(match.style)) {
            popUpTo(AppRoute.Home) { inclusive = false }
            launchSingleTop = true
        }
        if (started) {
            navigate(AppRoute.Live(match.id))
        }
    }
}
