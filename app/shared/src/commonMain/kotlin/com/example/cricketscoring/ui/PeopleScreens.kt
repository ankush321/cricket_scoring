package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.engine.PlayerStyleStats
import com.example.cricketscoring.engine.playerStats
import com.example.cricketscoring.engine.teamRecord
import com.example.cricketscoring.engine.twoDecimals
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Player
import com.example.cricketscoring.model.PlayingRole
import com.example.cricketscoring.navigation.AppRoute
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TeamsScreen(store: CricketStore) {
    val navigator = LocalAppNavigator.current
    var creating by remember { mutableStateOf(false) }
    CommonColumn(scrollable = true, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        store.data.teams.filterNot { it.archived }.forEach { team ->
            SurfaceCard(elevation = ElevationMid, onClick = { navigator.navigate(AppRoute.TeamEdit(team.id)) }) {
                Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(team.playerIds.joinToString { store.playerName(it) }.ifBlank { "No players" }, color = Muted)
            }
        }
        if (store.data.teams.none { !it.archived }) Text("No teams yet. Create one to start a match.", color = Muted)
        PrimaryButton("New team", { creating = true })
    }
    if (creating) {
        CreateTeamSheet(
            onDismiss = { creating = false },
            onCreate = { teamName, players ->
                val team = store.createTeam(teamName, players, teamPalette[store.data.teams.size % teamPalette.size])
                if (team != null) {
                    creating = false
                    navigator.navigate(AppRoute.TeamEdit(team.id))
                }
            },
        )
    }
}

@Composable
fun TeamEditScreen(store: CricketStore, teamId: String?) {
    val navigator = LocalAppNavigator.current
    val existing = store.data.teams.find { it.id == teamId }
    if (existing == null) {
        CreateTeamSheet(
            onDismiss = { navigator.back() },
            onCreate = { teamName, players ->
                val team = store.createTeam(teamName, players, teamPalette[store.data.teams.size % teamPalette.size])
                if (team != null) {
                    navigator.back()
                    navigator.navigate(AppRoute.TeamEdit(team.id))
                }
            },
        )
        return
    }
    var name by remember(teamId) { mutableStateOf(existing.name) }
    var playerName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Team", onBack = { navigator.back() })
        CommonColumn(
            modifier = Modifier.weight(1f),
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContentBlock("Team details", "Update the name shown on fixtures.") {
                NameField("Team name", name) { name = it }
                PrimaryButton(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = "Save name",
                    onClick = {
                        store.saveTeam(
                            existing.copy(
                                name = name.trim().ifBlank { existing.name },
                                shortName = name.trim().take(3).uppercase()
                                    .ifBlank { existing.shortName },
                            ),
                        )
                        store.showToast("Team saved.", AppToastType.Success)
                    })
            }
            ContentBlock("Players", "Add names one at a time. Assign captain and keeper below.") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Squad list", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    PlayerCountBadge(existing.playerIds.size)
                }
                existing.playerIds.forEach { id ->
                    val player = store.data.players.find { it.id == id }
                    Text(
                        store.playerName(id) +
                            " · ${player?.role?.displayName() ?: "Batter"}" +
                            (if (id == existing.captainId) " · captain" else "") +
                            (if (id == existing.wicketkeeperId) " · keeper" else ""),
                    )
                }

                NameField("Add a player", playerName) { playerName = it }

                SecondaryButton(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "Add player",
                    onClick = {
                        store.addPlayerToTeam(existing.id, playerName)
                        playerName = ""
                    }
                )
            }
            ContentBlock("Roles", "Set each player's role, then pick captain and keeper.") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    existing.playerIds.forEach { id ->
                        val player = store.data.players.find { it.id == id } ?: return@forEach
                        PlayerRoleCard(
                            player = player,
                            isCaptain = existing.captainId == id,
                            isKeeper = existing.wicketkeeperId == id,
                            onRole = { role -> store.savePlayer(player.copy(role = role)) },
                            onCaptain = { store.saveTeam(existing.copy(captainId = id)) },
                            onKeeper = { store.saveTeam(existing.copy(wicketkeeperId = id)) },
                        )
                    }
                }
            }
            ContentBlock("Record", "Results stay separated by cricket style.") {
                teamRecord(store.data, existing.id).forEach { record ->
                    Text("${styleTitle(record.style)}  P ${record.played}  W ${record.won}  L ${record.lost}  T ${record.tied}  NR ${record.noResult}")
                }
                SecondaryButton(if (existing.archived) "Restore team" else "Archive team", { store.archiveTeam(existing.id, !existing.archived) })
            }
        }
    }
}

@Composable
fun PlayersScreen(store: CricketStore) {
    var editing by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }
    CommonColumn(scrollable = true, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        store.data.players.sortedBy { it.name }.forEach { player ->
            SurfaceCard(elevation = ElevationMid, onClick = { editing = player }) {
                Text(player.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val teams = store.data.teams.filter { player.id in it.playerIds }.joinToString { it.name }
                Text(
                    listOfNotNull(player.role.displayName(), teams.ifBlank { null }).joinToString(" · "),
                    color = Muted,
                )
            }
        }
        if (store.data.players.isEmpty()) Text("Players appear here when you add them to a team.", color = Muted)
        PrimaryButton("Add player", { creating = true })
    }
    if (creating || editing != null) {
        var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        var role by remember(editing?.id) { mutableStateOf(editing?.role ?: PlayingRole.BATTER) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (editing == null) "New player" else "Player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NameField("Name", name) { name = it }
                    Text("Playing role", color = Muted)
                    ChoiceRow(
                        listOf(
                            PlayingRole.BATTER.displayName() to (role == PlayingRole.BATTER),
                            PlayingRole.BOWLER.displayName() to (role == PlayingRole.BOWLER),
                        ),
                    ) { index -> role = if (index == 0) PlayingRole.BATTER else PlayingRole.BOWLER }
                    ChoiceRow(
                        listOf(
                            PlayingRole.ALL_ROUNDER.displayName() to (role == PlayingRole.ALL_ROUNDER),
                            PlayingRole.WICKET_KEEPER.displayName() to (role == PlayingRole.WICKET_KEEPER),
                        ),
                    ) { index -> role = if (index == 0) PlayingRole.ALL_ROUNDER else PlayingRole.WICKET_KEEPER }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val player = editing?.copy(name = name.trim(), role = role) ?: Player(freshId("player"), name.trim(), role = role)
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
private fun PlayerRoleCard(
    player: Player,
    isCaptain: Boolean,
    isKeeper: Boolean,
    onRole: (PlayingRole) -> Unit,
    onCaptain: () -> Unit,
    onKeeper: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Playing role", color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceRow(
            listOf(
                PlayingRole.BATTER.displayName() to (player.role == PlayingRole.BATTER),
                PlayingRole.BOWLER.displayName() to (player.role == PlayingRole.BOWLER),
            ),
        ) { index ->
            onRole(if (index == 0) PlayingRole.BATTER else PlayingRole.BOWLER)
        }
        ChoiceRow(
            listOf(
                PlayingRole.ALL_ROUNDER.displayName() to (player.role == PlayingRole.ALL_ROUNDER),
                PlayingRole.WICKET_KEEPER.displayName() to (player.role == PlayingRole.WICKET_KEEPER),
            ),
        ) { index ->
            onRole(if (index == 0) PlayingRole.ALL_ROUNDER else PlayingRole.WICKET_KEEPER)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                if (isCaptain) "Captain ✓" else "Make captain",
                onCaptain,
                Modifier.weight(1f),
            )
            SecondaryButton(
                if (isKeeper) "Keeper ✓" else "Make keeper",
                onKeeper,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun StatsScreen(store: CricketStore) {
    val navigator = LocalAppNavigator.current
    var style by remember { mutableStateOf(CricketStyle.UNDERARM_BOX) }
    val accent = style.accent()
    val rows = store.data.players.mapNotNull { player ->
        playerStats(store.data, player.id).find { it.style == style }?.let { player to it }
    }.sortedByDescending { it.second.runs }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Statistics", onBack = { navigator.back() })
        CommonColumn(
            modifier = Modifier.weight(1f),
            scrollable = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsStylePicker(style) { style = it }
            Text(styleTitle(style), color = accent, fontWeight = FontWeight.SemiBold)
            Text(styleSummary(style), color = Muted, style = MaterialTheme.typography.bodySmall)
            if (rows.isEmpty()) {
                PitchCard {
                    Text("No matches in this style yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Player figures show up here after a ${styleTitle(style)} match is scored.", color = Muted)
                }
            }
            rows.forEach { (player, stats) ->
                PlayerStatCard(player.name, stats, accent)
            }
        }
    }
}

@Composable
private fun StatsStylePicker(selected: CricketStyle, onSelect: (CricketStyle) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PitchRaised.copy(alpha = 0.55f))
            .padding(4.dp),
    ) {
        CricketStyle.entries.forEach { item ->
            val on = item == selected
            val accent = item.accent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    styleChipLabel(item),
                    color = if (on) Color(0xFF14110A) else Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PlayerStatCard(name: String, stats: PlayerStyleStats, accent: Color) {
    PitchCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerAvatar(name, accent, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (stats.awards > 0) {
                        Text(
                            if (stats.awards == 1) "Player of the match" else "Player of the match · ${stats.awards}",
                            color = Gold,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${stats.matches}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (stats.matches == 1) "match" else "matches", color = Muted, fontSize = 11.sp)
                }
            }
            StatBlock(
                "Batting",
                listOf(
                    "${stats.runs}" to "Runs",
                    battingAverage(stats) to "Average",
                    "${stats.highest}" to "Best",
                    strikeRate(stats) to "Strike rate",
                ),
            )
            Text(inningsNote(stats), color = Muted, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(1.dp).background(SoftWhite.copy(alpha = 0.08f)))
            StatBlock(
                "Bowling",
                listOf(
                    "${stats.wickets}" to "Wickets",
                    bowlingEconomy(stats) to "Economy",
                    "${stats.catches}" to "Catches",
                ),
            )
        }
    }
}

@Composable
private fun StatBlock(title: String, figures: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth()) {
            figures.forEach { (value, label) ->
                Column(Modifier.weight(1f)) {
                    Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                    Text(label, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun styleChipLabel(style: CricketStyle): String = when (style) {
    CricketStyle.UNDERARM_BOX -> "Box"
    CricketStyle.UNDERARM_OPEN -> "Open"
    CricketStyle.OVERARM -> "Overarm"
}

private fun battingAverage(stats: PlayerStyleStats): String {
    val dismissals = stats.innings - stats.notOuts
    if (dismissals <= 0) return "–"
    return (stats.runs.toDouble() / dismissals).twoDecimals()
}

private fun strikeRate(stats: PlayerStyleStats): String {
    if (stats.balls == 0) return "–"
    return (stats.runs * 100.0 / stats.balls).twoDecimals()
}

private fun bowlingEconomy(stats: PlayerStyleStats): String {
    if (stats.bowlingBalls == 0) return "–"
    return (stats.bowlingRuns * 6.0 / stats.bowlingBalls).twoDecimals()
}

private fun inningsNote(stats: PlayerStyleStats): String {
    val notOut = if (stats.notOuts == 1) "1 not out" else "${stats.notOuts} not out"
    val fours = if (stats.fours == 1) "1 four" else "${stats.fours} fours"
    val sixes = if (stats.sixes == 1) "1 six" else "${stats.sixes} sixes"
    return "${stats.innings} innings · $notOut · $fours · $sixes"
}

@Preview
@Composable
private fun TeamsScreenPreview() {
    PreviewShell { TeamsScreen(it) }
}

@Preview
@Composable
private fun TeamEditScreenPreview() {
    PreviewShell { TeamEditScreen(it, PreviewIds.TeamA) }
}

@Preview
@Composable
private fun PlayersScreenPreview() {
    PreviewShell { PlayersScreen(it) }
}

@Preview
@Composable
private fun StatsScreenPreview() {
    PreviewShell { StatsScreen(it) }
}
