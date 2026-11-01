package com.example.cricketscoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.persist.CodecRepository
import com.example.cricketscoring.persist.STORE_KEY
import com.example.cricketscoring.persist.copyToClipboard
import com.example.cricketscoring.persist.platformStore
import com.example.cricketscoring.ui.CricketStore
import com.example.cricketscoring.ui.HomeScreen
import com.example.cricketscoring.ui.Lawn
import com.example.cricketscoring.ui.LiveScreen
import com.example.cricketscoring.ui.MatchesHome
import com.example.cricketscoring.ui.MoreScreen
import com.example.cricketscoring.ui.Muted
import com.example.cricketscoring.ui.NavGlyph
import com.example.cricketscoring.ui.PitchBlack
import com.example.cricketscoring.ui.PitchCard
import com.example.cricketscoring.ui.PitchTheme
import com.example.cricketscoring.ui.PlayersScreen
import com.example.cricketscoring.ui.RulesGuideScreen
import com.example.cricketscoring.ui.ScorecardScreen
import com.example.cricketscoring.ui.Screen
import com.example.cricketscoring.ui.SettingsScreen
import com.example.cricketscoring.ui.StatsScreen
import com.example.cricketscoring.ui.StyleHubScreen
import com.example.cricketscoring.ui.TeamEditScreen
import com.example.cricketscoring.ui.TeamsScreen
import com.example.cricketscoring.ui.TournamentDetailScreen
import com.example.cricketscoring.ui.TournamentsScreen
import com.example.cricketscoring.ui.WizardScreen

@Composable
fun App() {
    val store = remember {
        val storage = platformStore()
        CricketStore(CodecRepository(read = { storage.getString(STORE_KEY) }, write = { storage.putString(STORE_KEY, it) }))
    }
    PitchTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeContentPadding()) {
            AppFrame(store)
        }
    }
}

@Composable
private fun AppFrame(store: CricketStore) {
    val screen = store.screen
    val hideNav = screen is Screen.Wizard || screen is Screen.Live || screen is Screen.Scorecard
    Scaffold(
        containerColor = PitchBlack,
        bottomBar = { if (!hideNav) AppNav(store) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            store.message?.let { message ->
                PitchCard(Modifier.padding(12.dp)) {
                    Text(message)
                    TextButton(onClick = { store.dismissMessage() }) { Text("Dismiss") }
                }
            }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.Home -> HomeScreen(store)
                    Screen.Matches -> MatchesHome(store)
                    is Screen.StyleHub -> StyleHubScreen(store, screen.style)
                    Screen.Wizard -> WizardScreen(store)
                    is Screen.Live -> LiveScreen(store, screen.matchId)
                    is Screen.Scorecard -> ScorecardScreen(store, screen.matchId)
                    Screen.Teams -> TeamsScreen(store)
                    is Screen.TeamEdit -> TeamEditScreen(store, screen.teamId)
                    Screen.Players -> PlayersScreen(store)
                    Screen.Tournaments -> TournamentsScreen(store)
                    is Screen.TournamentDetail -> TournamentDetailScreen(store, screen.tournamentId)
                    Screen.Stats -> StatsScreen(store)
                    Screen.More -> MoreScreen(store)
                    Screen.Settings -> SettingsScreen(store)
                    Screen.RulesGuide -> RulesGuideScreen(store)
                }
            }
        }
    }
    store.shareText?.let { text ->
        AlertDialog(
            onDismissRequest = { store.shareText = null },
            title = { Text("Scorecard") },
            text = { SelectionContainer { Text(text) } },
            confirmButton = {
                TextButton(onClick = {
                    val copied = copyToClipboard(text)
                    store.message = if (copied) "Scorecard copied." else "Select the scorecard text to copy it."
                    store.shareText = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { store.shareText = null }) { Text("Close") } },
        )
    }
}

private enum class AppTab { Home, Fixtures, Teams, Players, More }

@Composable
private fun AppNav(store: CricketStore) {
    val tab = when (val screen = store.screen) {
        Screen.Home -> AppTab.Home
        Screen.Matches, is Screen.StyleHub -> AppTab.Fixtures
        Screen.Teams, is Screen.TeamEdit -> AppTab.Teams
        Screen.Players -> AppTab.Players
        else -> AppTab.More
    }
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = Lawn,
        selectedTextColor = Lawn,
        indicatorColor = Color.Transparent,
        unselectedIconColor = Muted,
        unselectedTextColor = Muted,
    )
    NavigationBar(containerColor = PitchBlack) {
        NavigationBarItem(
            selected = tab == AppTab.Home,
            onClick = { store.go(Screen.Home) },
            icon = { NavGlyph("HM", tab == AppTab.Home) },
            label = { Text("Home") },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == AppTab.Fixtures,
            onClick = { store.go(Screen.Matches) },
            icon = { NavGlyph("FX", tab == AppTab.Fixtures) },
            label = { Text("Fixtures") },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == AppTab.Teams,
            onClick = { store.go(Screen.Teams) },
            icon = { NavGlyph("TM", tab == AppTab.Teams) },
            label = { Text("Teams") },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == AppTab.Players,
            onClick = { store.go(Screen.Players) },
            icon = { NavGlyph("PL", tab == AppTab.Players) },
            label = { Text("Players") },
            colors = colors,
        )
        NavigationBarItem(
            selected = tab == AppTab.More,
            onClick = { store.go(Screen.More) },
            icon = { NavGlyph("···", tab == AppTab.More) },
            label = { Text("More") },
            colors = colors,
        )
    }
}
