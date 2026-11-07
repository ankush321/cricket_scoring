package com.example.cricketscoring.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cricketscoring.di.AppContainer
import com.example.cricketscoring.presentation.fixtures.FixturesRoute
import com.example.cricketscoring.presentation.home.HomeRoute
import com.example.cricketscoring.presentation.more.MoreRoute
import com.example.cricketscoring.ui.AppBottomBar
import com.example.cricketscoring.ui.AppToastDefaults
import com.example.cricketscoring.ui.AppToastHost
import com.example.cricketscoring.ui.AppToolbar
import com.example.cricketscoring.ui.AppToolbarUiState
import com.example.cricketscoring.ui.LiveScreen
import com.example.cricketscoring.ui.PitchBlack
import com.example.cricketscoring.ui.PlayersScreen
import com.example.cricketscoring.ui.RulesGuideScreen
import com.example.cricketscoring.ui.ScorecardScreen
import com.example.cricketscoring.ui.SettingsScreen
import com.example.cricketscoring.ui.StatsScreen
import com.example.cricketscoring.ui.StyleHubScreen
import com.example.cricketscoring.ui.TeamEditScreen
import com.example.cricketscoring.ui.TeamsScreen
import com.example.cricketscoring.ui.TournamentDetailScreen
import com.example.cricketscoring.ui.TournamentsScreen
import com.example.cricketscoring.ui.WizardScreen

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val navigator = remember(navController, container.store) {
        AppNavigator(navController, container.store)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val hideNav = currentRoute?.hierarchy?.any {
        it.hasRoute<AppRoute.Wizard>() ||
            it.hasRoute<AppRoute.Live>() ||
            it.hasRoute<AppRoute.Scorecard>()
    } == true
    val rootToolbar = rootToolbarState(currentRoute)

    CompositionLocalProvider(LocalAppNavigator provides navigator) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = PitchBlack,
                // Toolbar / bottom bar own status + nav insets; avoid double-padding content.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    rootToolbar?.let { state ->
                        AppToolbar(title = state.title)
                    }
                },
                bottomBar = {
                    if (!hideNav) {
                        AppBottomBar(
                            selected = selectedTab(currentRoute),
                            onSelect = { navigator.navigateRoot(it.route) },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    composable<AppRoute.Home> {
                        HomeRoute(
                            store = container.store,
                            onOpenStyle = { navigator.openStyle(it) },
                        )
                    }
                    composable<AppRoute.Fixtures> {
                        FixturesRoute(
                            store = container.store,
                            onOpenStyle = { navigator.openStyle(it) },
                        )
                    }
                    composable<AppRoute.StyleHub> { entry ->
                        val route = entry.toRoute<AppRoute.StyleHub>()
                        StyleHubScreen(container.store, route.style())
                    }
                    composable<AppRoute.Wizard> {
                        WizardScreen(container.store)
                    }
                    composable<AppRoute.Live> { entry ->
                        val route = entry.toRoute<AppRoute.Live>()
                        LiveScreen(container.store, route.matchId)
                    }
                    composable<AppRoute.Scorecard> { entry ->
                        val route = entry.toRoute<AppRoute.Scorecard>()
                        ScorecardScreen(container.store, route.matchId)
                    }
                    composable<AppRoute.Teams> {
                        TeamsScreen(container.store)
                    }
                    composable<AppRoute.TeamEdit> { entry ->
                        val route = entry.toRoute<AppRoute.TeamEdit>()
                        TeamEditScreen(container.store, route.teamId)
                    }
                    composable<AppRoute.Players> {
                        PlayersScreen(container.store)
                    }
                    composable<AppRoute.More> {
                        MoreRoute()
                    }
                    composable<AppRoute.Tournaments> {
                        TournamentsScreen(container.store)
                    }
                    composable<AppRoute.TournamentDetail> { entry ->
                        val route = entry.toRoute<AppRoute.TournamentDetail>()
                        TournamentDetailScreen(container.store, route.tournamentId)
                    }
                    composable<AppRoute.Stats> {
                        StatsScreen(container.store)
                    }
                    composable<AppRoute.Settings> {
                        SettingsScreen(container.store)
                    }
                    composable<AppRoute.RulesGuide> {
                        RulesGuideScreen(container.store)
                    }
                }
            }
            AppToastHost(
                store = container.store,
                bottomInset = if (hideNav) AppToastDefaults.BottomPadding else AppToastDefaults.HostBottomClearance,
            )
        }
    }
}

private fun rootToolbarState(destination: NavDestination?): AppToolbarUiState? {
    destination ?: return null
    return when {
        destination.hasRoute<AppRoute.Home>() -> AppToolbarUiState(title = "Cricket Scoring")
        destination.hasRoute<AppRoute.Fixtures>() -> AppToolbarUiState(title = "Fixtures")
        destination.hasRoute<AppRoute.Teams>() -> AppToolbarUiState(title = "Teams")
        destination.hasRoute<AppRoute.Players>() -> AppToolbarUiState(title = "Players")
        destination.hasRoute<AppRoute.More>() -> AppToolbarUiState(title = "More")
        else -> null
    }
}

private fun selectedTab(destination: NavDestination?): AppTab {
    destination ?: return AppTab.Home
    return AppTab.entries.firstOrNull { tab ->
        destination.hierarchy.any { dest ->
            when (tab) {
                AppTab.Home -> dest.hasRoute<AppRoute.Home>()
                AppTab.Fixtures -> dest.hasRoute<AppRoute.Fixtures>() || dest.hasRoute<AppRoute.StyleHub>()
                AppTab.Teams -> dest.hasRoute<AppRoute.Teams>() || dest.hasRoute<AppRoute.TeamEdit>()
                AppTab.Players -> dest.hasRoute<AppRoute.Players>()
                AppTab.More -> dest.hasRoute<AppRoute.More>() ||
                    dest.hasRoute<AppRoute.Tournaments>() ||
                    dest.hasRoute<AppRoute.Stats>() ||
                    dest.hasRoute<AppRoute.Settings>() ||
                    dest.hasRoute<AppRoute.RulesGuide>() ||
                    dest.hasRoute<AppRoute.TournamentDetail>()
            }
        }
    } ?: AppTab.Home
}
