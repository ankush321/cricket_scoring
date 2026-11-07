package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.scorecardText
import com.example.cricketscoring.engine.teamShort
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.navigation.AppRoute
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.rules.defaultRules
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle

private val tabs = listOf("Live", "Upcoming", "Completed", "Rules")

@Composable
fun StyleHubScreen(store: CricketStore, style: CricketStyle) {
    val navigator = LocalAppNavigator.current
    var tab by remember(style) { mutableIntStateOf(0) }
    var query by remember(style) { mutableStateOf("") }
    var sort by remember(style) { mutableStateOf("Recent") }
    var pendingDelete by remember { mutableStateOf<Match?>(null) }
    val tournamentName = store.data.tournaments.associate { it.id to it.name }
    val filtered = store.data.matches.filter { match ->
        match.style == style && match.matchesQuery(query, tournamentName[match.tournamentId].orEmpty()) && match.matchesTab(tab)
    }.let { matches ->
        when (sort) {
            "Upcoming" -> matches.sortedBy { it.scheduledAtEpochMs ?: Long.MAX_VALUE }
            "Oldest" -> matches.sortedBy { it.createdAtEpochMs }
            else -> matches.sortedByDescending { it.updatedAtEpochMs }
        }
    }
    val styleMatches = store.data.matches.filter { it.style == style }
    val live = styleMatches.filter { match ->
        val status = MatchEngine.derive(match).status
        status == MatchStatus.LIVE || status == MatchStatus.INNINGS_BREAK || status == MatchStatus.SUPER_OVER
    }
    val completed = styleMatches.filter { match ->
        val status = MatchEngine.derive(match).status
        status == MatchStatus.COMPLETED || status == MatchStatus.ABANDONED
    }.sortedByDescending { it.updatedAtEpochMs }.take(5)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(styleTitle(style), onBack = { navigator.back() })
        CommonColumn(
            modifier = Modifier.weight(1f),
            scrollable = true,
            verticalPadding = AppScreenDefaults.Vertical,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PageHeading("Fixtures", "Resume a live game, review recent results, or start a new match.")
            SurfaceCard(elevation = ElevationHigh, accent = style.accent()) {
                Text("Start a new match", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Pick teams, confirm the format, then begin scoring.", color = Muted)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Start new match", { navigator.openWizard(style) }, Modifier.fillMaxWidth())
            }
            if (live.isNotEmpty()) {
                ContentBlock("Live now", "Tap a fixture to continue scoring.") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        live.forEach { match ->
                            MatchRow(match, onOpen = { navigator.openMatch(match) }, onDuplicate = { navigator.duplicate(match) }, onDelete = { pendingDelete = match }, onShare = { store.shareText = scorecardText(match) })
                        }
                    }
                }
            }
            if (completed.isNotEmpty()) {
                ContentBlock("Recent results", "Open a completed scorecard or copy the setup.") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        completed.forEach { match ->
                            MatchRow(match, onOpen = { navigator.openMatch(match) }, onDuplicate = { navigator.duplicate(match) }, onDelete = { pendingDelete = match }, onShare = { store.shareText = scorecardText(match) })
                        }
                    }
                }
            }
            Disclosure("All fixtures", "Search, upcoming games, and the recommended rules for this style.") {
                StyleMatchBrowser(store, style, tab, { tab = it }, query, { query = it }, sort, { sort = it }, filtered) { pendingDelete = it }
            }
        }
    }
    pendingDelete?.let { match ->
        val liveMatch = MatchEngine.derive(match).status == MatchStatus.LIVE
        ConfirmDialog(
            title = if (liveMatch) "Delete this live match?" else "Delete this match?",
            body = if (liveMatch) {
                "This match is live. Deleting it removes the ball-by-ball record from this device."
            } else {
                "The scorecard will be removed from this device."
            },
            confirmLabel = if (liveMatch) "Delete live match" else "Delete match",
            destructive = true,
            onConfirm = {
                store.deleteMatch(match.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun StyleMatchBrowser(
    store: CricketStore,
    style: CricketStyle,
    tab: Int,
    onTab: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    filtered: List<Match>,
    onDelete: (Match?) -> Unit,
) {
    val navigator = LocalAppNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = PitchBlack, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { onTab(index) }, text = { Text(label) })
            }
        }
        if (tab == 3) {
            RulesTab(style, store)
        } else {
            NameField("Search teams, players, tournaments", query, onValue = onQuery)
            ChoiceRow(
                listOf("Recent" to (sort == "Recent"), "Upcoming" to (sort == "Upcoming"), "Oldest" to (sort == "Oldest")),
            ) { index -> onSort(listOf("Recent", "Upcoming", "Oldest")[index]) }
            if (filtered.isEmpty()) {
                Text("No ${tabs[tab].lowercase()} matches yet.", color = Muted)
            } else {
                filtered.forEach { match ->
                    MatchRow(match, onOpen = { navigator.openMatch(match) }, onDuplicate = { navigator.duplicate(match) }, onDelete = { onDelete(match) }, onShare = {
                        store.shareText = scorecardText(match)
                    })
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: Match, onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    val derived = MatchEngine.derive(match)
    SurfaceCard(elevation = ElevationMid, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(derived.status.displayName(), color = match.style.accent(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(match.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = WicketRed,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (derived.innings.isEmpty()) {
            Text(match.venue.ifBlank { "Squads and rules are saved" }, color = Muted)
        } else {
            derived.innings.filter { !it.superOver }.forEach { innings ->
                Text("${match.teamShort(innings.battingTeamId)} ${innings.scoreLine()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            derived.equation?.let { Text(it, color = Gold) }
            derived.result?.summary?.let { Text(it, color = Lawn) }
        }
        Spacer(Modifier.height(8.dp))
        InlineActions(
            "Open" to onOpen,
            "Copy setup" to onDuplicate,
            "Share" to onShare,
        )
    }
}

@Composable
private fun RulesTab(style: CricketStyle, store: CricketStore) {
    val navigator = LocalAppNavigator.current
    val rules = defaultRules(style)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(styleSummary(style), color = Muted)
        Text(rules.name, style = MaterialTheme.typography.titleMedium)
        rules.organizerPrompts.forEach { prompt -> Notice(prompt) }
        Text("These are recommended starting points, not an official publication. A match keeps the rules it started with.", color = Muted)
        SecondaryButton("Open the full comparison", { navigator.navigate(AppRoute.RulesGuide) })
        PrimaryButton("Start a match with these rules", { navigator.openWizard(style) })
    }
}

private fun Match.matchesTab(tab: Int): Boolean {
    val status = MatchEngine.derive(this).status
    return when (tab) {
        0 -> status == MatchStatus.LIVE || status == MatchStatus.INNINGS_BREAK || status == MatchStatus.SUPER_OVER
        1 -> status == MatchStatus.UPCOMING
        else -> status == MatchStatus.COMPLETED || status == MatchStatus.ABANDONED || status == MatchStatus.CANCELLED
    }
}

private fun Match.matchesQuery(query: String, tournament: String): Boolean {
    if (query.isBlank()) return true
    val haystack = listOf(title, venue, teamA.name, teamB.name, tournament, teamA.players.joinToString { it.name }, teamB.players.joinToString { it.name })
        .joinToString(" ")
        .lowercase()
    return haystack.contains(query.trim().lowercase())
}

private fun MatchStatus.displayName(): String = when (this) {
    MatchStatus.UPCOMING -> "Upcoming"
    MatchStatus.LIVE -> "Live"
    MatchStatus.INNINGS_BREAK -> "Innings break"
    MatchStatus.SUPER_OVER -> "Super Over"
    MatchStatus.COMPLETED -> "Completed"
    MatchStatus.ABANDONED -> "Abandoned"
    MatchStatus.CANCELLED -> "Cancelled"
}
