package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.rules.ruleCatalog

@Composable
fun MoreScreen(store: CricketStore) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Menu", style = MaterialTheme.typography.headlineMedium)
        Text("Teams, players, and records live here so scoring stays simple.", color = Muted)
        PitchCard(onClick = { store.push(Screen.Teams) }) {
            Text("Teams", style = MaterialTheme.typography.titleMedium)
            Text("Create sides and their players.", color = Muted)
        }
        PitchCard(onClick = { store.push(Screen.Players) }) {
            Text("Players", style = MaterialTheme.typography.titleMedium)
            Text("Names used across every style.", color = Muted)
        }
        PitchCard(onClick = { store.push(Screen.Tournaments) }) {
            Text("Tournaments", style = MaterialTheme.typography.titleMedium)
            Text("Group matches and share a rules copy.", color = Muted)
        }
        PitchCard(onClick = { store.push(Screen.Stats) }) {
            Text("Statistics", style = MaterialTheme.typography.titleMedium)
            Text("Runs, wickets, and results already scored.", color = Muted)
        }
        PitchCard(onClick = { store.push(Screen.RulesGuide) }) {
            Text("Rules guide", style = MaterialTheme.typography.titleMedium)
            Text("Recommended starting points for each style.", color = Muted)
        }
        PitchCard(onClick = { store.push(Screen.Settings) }) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Text("This device only. Matches save as you score.", color = Muted)
        }
    }
}

@Composable
fun SettingsScreen(store: CricketStore) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Settings", onBack = { store.pop() })
        Text("Scoring is stored on this device and works offline. A match keeps the rules it started with.")
        Text("Recommended rules are a starting point. They are not an official board or tournament publication.", color = Muted)
        SecondaryButton("Rules guide", { store.push(Screen.RulesGuide) })
        SecondaryButton("Clear all local data", { confirm = true })
    }
    if (confirm) {
        ConfirmDialog(
            "Clear every match?",
            "Teams, players, tournaments, and scorecards on this device will be deleted.",
            "Clear data",
            onConfirm = {
                store.clearAll()
                confirm = false
            },
            onDismiss = { confirm = false },
            destructive = true,
        )
    }
}

@Composable
fun RulesGuideScreen(store: CricketStore) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Rules guide", onBack = { store.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Compare the recommended setup for each style. Confirm the rows marked for the organizer before you start.", color = Muted)
            ruleCatalog().forEach { row ->
                PitchCard {
                    Text(row.topic, style = MaterialTheme.typography.titleMedium)
                    Text(row.disposition.displayName(), color = if (row.disposition.name == "CONFIRM_WITH_ORGANIZER") Gold else Lawn, fontWeight = FontWeight.SemiBold)
                    Text("Box · ${row.box}", color = BoxAmber)
                    Text("Open · ${row.open}", color = OpenTeal)
                    Text("Overarm · ${row.overarm}", color = OverarmGreen)
                    Text(row.note, color = Muted)
                }
            }
        }
    }
}
