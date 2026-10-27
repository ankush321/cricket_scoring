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
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.engine.playerStats
import com.example.cricketscoring.engine.teamRecord
import com.example.cricketscoring.engine.twoDecimals
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Player
import com.example.cricketscoring.rules.styleTitle

@Composable
fun TeamsScreen(store: CricketStore) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Teams", style = MaterialTheme.typography.headlineMedium)
        Text("Teams and players can be used in every cricket style.", color = Muted)
        store.data.teams.filterNot { it.archived }.forEach { team ->
            PitchCard(onClick = { store.push(Screen.TeamEdit(team.id)) }) {
                Text(team.name, style = MaterialTheme.typography.titleMedium)
                Text(team.playerIds.joinToString { store.playerName(it) }.ifBlank { "No players" }, color = Muted)
            }
        }
        if (store.data.teams.none { !it.archived }) Text("No teams yet.", color = Muted)
        PrimaryButton("New team", { store.push(Screen.TeamEdit(null)) })
    }
}

@Composable
fun TeamEditScreen(store: CricketStore, teamId: String?) {
    val existing = store.data.teams.find { it.id == teamId }
    var name by remember(teamId) { mutableStateOf(existing?.name.orEmpty()) }
    var playerName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(teamId == null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (existing == null) "New team" else "Team", onBack = { store.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing == null || creating) {
                CreateTeamDialog(
                    onDismiss = { if (existing == null) store.pop() else creating = false },
                    onCreate = { teamName, players ->
                        val team = store.createTeam(teamName, players, teamPalette[store.data.teams.size % teamPalette.size])
                        if (team != null) {
                            store.pop()
                            store.push(Screen.TeamEdit(team.id))
                        }
                    },
                )
            }
            existing?.let { team ->
                NameField("Team name", name) { name = it }
                PrimaryButton("Save name", {
                    store.saveTeam(team.copy(name = name.trim().ifBlank { team.name }, shortName = name.trim().take(3).uppercase().ifBlank { team.shortName }))
                    store.message = "Team saved."
                })
                SectionLabel("Players")
                team.playerIds.forEach { id ->
                    Text(
                        store.playerName(id) +
                            (if (id == team.captainId) " · captain" else "") +
                            (if (id == team.wicketkeeperId) " · keeper" else ""),
                    )
                }
                NameField("Add a player", playerName) { playerName = it }
                SecondaryButton("Add player", {
                    store.addPlayerToTeam(team.id, playerName)
                    playerName = ""
                })
                Text("Tap a player to make them captain, then keeper.", color = Muted)
                team.playerIds.forEach { id ->
                    SecondaryButton("Captain ${store.playerName(id)}", { store.saveTeam(team.copy(captainId = id)) })
                }
                team.playerIds.forEach { id ->
                    SecondaryButton("Keeper ${store.playerName(id)}", { store.saveTeam(team.copy(wicketkeeperId = id)) })
                }
                SectionLabel("Record")
                teamRecord(store.data, team.id).forEach { record ->
                    Text("${styleTitle(record.style)}  P ${record.played}  W ${record.won}  L ${record.lost}  T ${record.tied}  NR ${record.noResult}")
                }
                SecondaryButton(if (team.archived) "Restore team" else "Archive team", { store.archiveTeam(team.id, !team.archived) })
            }
        }
    }
}

@Composable
fun PlayersScreen(store: CricketStore) {
    var editing by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Players", onBack = { store.pop() })
        store.data.players.sortedBy { it.name }.forEach { player ->
            PitchCard(onClick = { editing = player }) {
                Text(player.name, style = MaterialTheme.typography.titleMedium)
                val teams = store.data.teams.filter { player.id in it.playerIds }.joinToString { it.name }
                if (teams.isNotBlank()) Text(teams, color = Muted)
            }
        }
        if (store.data.players.isEmpty()) Text("Players appear here when you add them to a team.", color = Muted)
        PrimaryButton("Add player", { creating = true })
    }
    if (creating || editing != null) {
        var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (editing == null) "New player" else "Player") },
            text = { NameField("Name", name) { name = it } },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val player = editing?.copy(name = name.trim()) ?: Player(freshId("player"), name.trim())
                    store.savePlayer(player)
                    creating = false
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    editing?.let { store.deletePlayer(it.id) }
                    creating = false
                    editing = null
                }) { Text(if (editing == null) "Cancel" else "Delete") }
            },
        )
    }
}

@Composable
fun StatsScreen(store: CricketStore) {
    var style by remember { mutableStateOf(CricketStyle.UNDERARM_BOX) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Statistics", onBack = { store.pop() })
        Text("Figures stay separated by cricket style.", color = Muted)
        ChoiceRow(CricketStyle.entries.map { styleTitle(it).substringBefore(" ") to (style == it) }) { index ->
            style = CricketStyle.entries[index]
        }
        val rows = store.data.players.mapNotNull { player ->
            playerStats(store.data, player.id).find { it.style == style }?.let { player to it }
        }.sortedByDescending { it.second.runs }
        if (rows.isEmpty()) {
            Text("No ${styleTitle(style)} statistics yet.", color = Muted)
        }
        rows.forEach { (player, stats) ->
            PitchCard {
                Text(player.name, style = MaterialTheme.typography.titleMedium)
                Text("${styleTitle(style)} · ${stats.matches} matches", color = style.accent())
                val average = if (stats.innings - stats.notOuts == 0) "-" else (stats.runs.toDouble() / (stats.innings - stats.notOuts)).twoDecimals()
                Text("Batting  ${stats.runs} runs  Ave $average  HS ${stats.highest}  SR ${if (stats.balls == 0) "0.00" else (stats.runs * 100.0 / stats.balls).twoDecimals()}")
                val economy = if (stats.bowlingBalls == 0) "0.00" else (stats.bowlingRuns * 6.0 / stats.bowlingBalls).twoDecimals()
                Text("Bowling  ${stats.wickets} wickets  Econ $economy  Ct ${stats.catches}")
                if (stats.awards > 0) Text("Player of the match ${stats.awards}", color = Gold)
            }
        }
    }
}
