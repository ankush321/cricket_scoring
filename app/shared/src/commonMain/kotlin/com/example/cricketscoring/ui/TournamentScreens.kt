package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.engine.pointsTable
import com.example.cricketscoring.engine.twoDecimals
import com.example.cricketscoring.model.CompetitionStage
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.rules.styleTitle

@Composable
fun TournamentsScreen(store: CricketStore) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Tournaments", onBack = { store.pop() })
        Text("A tournament keeps its own playing conditions and points.", color = Muted)
        store.data.tournaments.forEach { tournament ->
            PitchCard(onClick = { store.push(Screen.TournamentDetail(tournament.id)) }) {
                Text(tournament.name, style = MaterialTheme.typography.titleMedium)
                Text(styleTitle(tournament.style), color = tournament.style.accent())
                Text("${tournament.teamIds.size} teams · ${tournament.fixtures.size} fixtures", color = Muted)
            }
        }
        if (store.data.tournaments.isEmpty()) Text("No tournaments yet.", color = Muted)
        PrimaryButton("New tournament", { creating = true })
    }
    if (creating) NewTournamentDialog(store, onDismiss = { creating = false })
}

@Composable
private fun NewTournamentDialog(store: CricketStore, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(CricketStyle.UNDERARM_BOX) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tournament") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField("Name", name) { name = it }
                CricketStyle.entries.forEach { option ->
                    SecondaryButton(styleTitle(option), { style = option })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.createTournament(name, style)
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TournamentDetailScreen(store: CricketStore, tournamentId: String) {
    val tournament = store.data.tournaments.find { it.id == tournamentId }
    if (tournament == null) {
        Column(Modifier.padding(16.dp)) {
            Text("This tournament was removed.")
            SecondaryButton("Back", { store.pop() })
        }
        return
    }
    var adding by remember { mutableStateOf(false) }
    val table = pointsTable(tournament, store.data.matches)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(tournament.name, onBack = { store.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(styleTitle(tournament.style), color = tournament.style.accent())
            Text(tournament.rules.name, color = Muted)
            tournament.rules.organizerPrompts.take(2).forEach { Notice(it) }
            IntField("Overs", tournament.rules.overs.oversPerInnings) { value ->
                store.saveTournament(tournament.copy(rules = tournament.rules.copy(overs = tournament.rules.overs.copy(oversPerInnings = value))))
            }
            ToggleRow("Super Over", tournament.rules.tieBreak.superOverEnabled) { on ->
                store.saveTournament(tournament.copy(rules = tournament.rules.copy(tieBreak = tournament.rules.tieBreak.copy(superOverEnabled = on))))
            }
            ChoiceRow(listOf("League" to (tournament.stage == CompetitionStage.LEAGUE), "Knockout" to (tournament.stage == CompetitionStage.KNOCKOUT))) { index ->
                store.saveTournament(tournament.copy(stage = if (index == 0) CompetitionStage.LEAGUE else CompetitionStage.KNOCKOUT))
            }
            SectionLabel("Points")
            if (table.isEmpty()) Text("Add teams to build the table.", color = Muted)
            table.forEach { row ->
                val name = store.data.teams.find { it.id == row.teamId }?.name ?: row.teamId
                Text("$name  P${row.played} W${row.won} L${row.lost} T${row.tied} Pts ${row.points} NRR ${row.netRunRate.twoDecimals()}")
            }
            SectionLabel("Teams")
            store.data.teams.filter { it.id in tournament.teamIds }.forEach { team ->
                Text(team.name)
            }
            store.data.teams.filter { it.id !in tournament.teamIds && !it.archived }.forEach { team ->
                SecondaryButton("Add ${team.name}", {
                    store.saveTournament(tournament.copy(teamIds = tournament.teamIds + team.id))
                })
            }
            SectionLabel(if (tournament.stage == CompetitionStage.KNOCKOUT) "Bracket" else "Fixtures")
            tournament.fixtures.groupBy { it.round }.forEach { (round, fixtures) ->
                Text(round, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                fixtures.forEach { fixture ->
                    val a = store.data.teams.find { it.id == fixture.teamAId }?.name ?: "Team A"
                    val b = store.data.teams.find { it.id == fixture.teamBId }?.name ?: "Team B"
                    val match = store.data.matches.find { it.id == fixture.matchId }
                    PitchCard {
                        Text("$a vs $b")
                        if (match == null) {
                            PrimaryButton("Start match", {
                                store.draft = MatchDraft(
                                    style = tournament.style,
                                    step = 2,
                                    teamAId = fixture.teamAId,
                                    teamBId = fixture.teamBId,
                                    rules = tournament.rules.copy(id = freshId("rules"), source = com.example.cricketscoring.model.RuleSource.TOURNAMENT),
                                    tournamentId = tournament.id,
                                    fixtureId = fixture.id,
                                    title = "$a vs $b",
                                )
                                store.push(Screen.Wizard)
                            })
                        } else {
                            SecondaryButton("Open match", { store.openMatch(match) })
                        }
                    }
                }
            }
            SecondaryButton("Add fixture", { adding = true })
        }
    }
    if (adding) AddFixtureDialog(store, tournament, onDismiss = { adding = false })
}

@Composable
private fun AddFixtureDialog(store: CricketStore, tournament: com.example.cricketscoring.model.Tournament, onDismiss: () -> Unit) {
    val teams = store.data.teams.filter { it.id in tournament.teamIds }
    var round by remember { mutableStateOf(if (tournament.stage == CompetitionStage.KNOCKOUT) "Semi-final" else "League") }
    var teamA by remember { mutableStateOf(teams.getOrNull(0)?.id) }
    var teamB by remember { mutableStateOf(teams.getOrNull(1)?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fixture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField("Round", round) { round = it }
                teams.forEach { team ->
                    TextButton(onClick = { teamA = team.id }) { Text(if (teamA == team.id) "A · ${team.name}" else "Set A · ${team.name}") }
                }
                teams.forEach { team ->
                    TextButton(onClick = { teamB = team.id }) { Text(if (teamB == team.id) "B · ${team.name}" else "Set B · ${team.name}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (teamA != null && teamB != null && teamA != teamB) {
                    store.saveTournament(tournament.copy(fixtures = tournament.fixtures + newFixture(round, teamA!!, teamB!!)))
                    onDismiss()
                } else {
                    store.message = "Choose two different teams."
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
