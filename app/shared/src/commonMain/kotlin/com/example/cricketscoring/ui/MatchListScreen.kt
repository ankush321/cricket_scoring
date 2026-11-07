package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    val accent = style.accent()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(styleTitle(style), onBack = { navigator.back() })
        CommonColumn(
            modifier = Modifier.weight(1f),
            scrollable = true,
            verticalPadding = AppScreenDefaults.Vertical,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StyleHubIntro(
                style = style,
                accent = accent,
                onStart = { navigator.openWizard(style) },
            )

            StyleHubTabs(
                selected = tab,
                accent = accent,
                onSelect = { tab = it },
            )

            when (tab) {
                3 -> RulesPanel(style = style, accent = accent)
                else -> FixtureBrowser(
                    store = store,
                    tab = tab,
                    query = query,
                    onQuery = { query = it },
                    sort = sort,
                    onSort = { sort = it },
                    filtered = filtered,
                    onDelete = { pendingDelete = it },
                )
            }

            Spacer(Modifier.height(12.dp))
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
private fun StyleHubIntro(
    style: CricketStyle,
    accent: Color,
    onStart: () -> Unit,
) {
    SurfaceCard(elevation = ElevationHigh, accent = accent) {
        Text(
            styleSummary(style),
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Start new match", onStart, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StyleHubTabs(
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = accent,
        edgePadding = 0.dp,
        divider = {},
        indicator = {
            SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(
                    selectedTabIndex = selected,
                    matchContentSize = false
                ),
                height = 3.dp,
                color = accent
            )
        },
    ) {
        tabs.forEachIndexed { index, label ->
            val active = selected == index
            Tab(
                selected = active,
                onClick = { onSelect(index) },
                selectedContentColor = accent,
                unselectedContentColor = Muted,
                text = {
                    Text(
                        label,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun FixtureBrowser(
    store: CricketStore,
    tab: Int,
    query: String,
    onQuery: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    filtered: List<Match>,
    onDelete: (Match?) -> Unit,
) {
    val navigator = LocalAppNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NameField("Search teams, players, tournaments", query, onValue = onQuery)
        ChoiceRow(
            listOf("Recent" to (sort == "Recent"), "Upcoming" to (sort == "Upcoming"), "Oldest" to (sort == "Oldest")),
        ) { index -> onSort(listOf("Recent", "Upcoming", "Oldest")[index]) }

        if (filtered.isEmpty()) {
            EmptyFixturesHint(label = tabs[tab])
        } else {
            filtered.forEach { match ->
                MatchRow(
                    match = match,
                    onOpen = { navigator.openMatch(match) },
                    onDuplicate = { navigator.duplicate(match) },
                    onDelete = { onDelete(match) },
                    onShare = { store.shareText = scorecardText(match) },
                )
            }
        }
    }
}

@Composable
private fun EmptyFixturesHint(label: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PitchRaised)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("No ${label.lowercase()} matches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Start a match above, or switch tabs to check another list.",
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun MatchRow(
    match: Match,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val derived = MatchEngine.derive(match)
    SurfaceCard(elevation = ElevationMid, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    derived.status.displayName(),
                    color = match.style.accent(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
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
        Spacer(Modifier.height(8.dp))
        if (derived.innings.isEmpty()) {
            Text(match.venue.ifBlank { "Squads and rules are saved" }, color = Muted, lineHeight = 22.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                derived.innings.filter { !it.superOver }.forEach { innings ->
                    Text(
                        "${match.teamShort(innings.battingTeamId)} ${innings.scoreLine()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                derived.equation?.let { Text(it, color = Gold, lineHeight = 22.sp) }
                derived.result?.summary?.let { Text(it, color = Lawn, lineHeight = 22.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        InlineActions(
            "Open" to onOpen,
            "Copy setup" to onDuplicate,
            "Share" to onShare,
        )
    }
}

@Composable
private fun RulesPanel(style: CricketStyle, accent: Color) {
    val navigator = LocalAppNavigator.current
    val rules = defaultRules(style)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SurfaceCard(elevation = ElevationMid, accent = accent) {
            Text(
                "Recommended rules",
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                rules.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                styleSummary(style),
                color = Muted,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
            )
        }

        SurfaceCard(elevation = ElevationMid) {
            Text(
                "Confirm before you start",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Each point is a default for this style. Change only what this ground needs.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rules.organizerPrompts.forEachIndexed { index, prompt ->
                    RulePoint(
                        number = index + 1,
                        text = prompt,
                        accent = accent,
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusMd))
                .background(PitchRaised)
                .border(1.dp, SoftWhite.copy(alpha = 0.06f), RoundedCornerShape(RadiusMd))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Not an official rule book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "These are recommended starting points. A match keeps the rules it started with.",
                color = Muted,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
            )
        }

        SecondaryButton(
            "Open the full comparison",
            { navigator.navigate(AppRoute.RulesGuide) },
            Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            "Start a match with these rules",
            { navigator.openWizard(style) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RulePoint(
    number: Int,
    text: String,
    accent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
            )
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
        )
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
    val haystack = listOf(
        title,
        venue,
        teamA.name,
        teamB.name,
        tournament,
        teamA.players.joinToString { it.name },
        teamB.players.joinToString { it.name },
    )
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

@Preview
@Composable
private fun StyleHubScreenPreview() {
    PreviewShell { StyleHubScreen(it, CricketStyle.UNDERARM_OPEN) }
}

@Preview
@Composable
private fun StyleHubRulesPreview() {
    PreviewShell {
        CommonColumn(scrollable = true, verticalPadding = AppScreenDefaults.Vertical) {
            RulesPanel(style = CricketStyle.UNDERARM_OPEN, accent = CricketStyle.UNDERARM_OPEN.accent())
        }
    }
}
