package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.oversLabel
import com.example.cricketscoring.engine.playerName
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.scorecardText
import com.example.cricketscoring.engine.teamName
import com.example.cricketscoring.engine.teamShort
import com.example.cricketscoring.engine.twoDecimals
import com.example.cricketscoring.model.BattingRow
import com.example.cricketscoring.model.BowlingRow
import com.example.cricketscoring.model.DerivedInnings
import com.example.cricketscoring.model.DerivedMatch
import com.example.cricketscoring.model.FallOfWicket
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.model.Partnership
import com.example.cricketscoring.navigation.AppRoute
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.rules.styleTitle

@Composable
fun ScorecardScreen(store: CricketStore, matchId: String) {
    val navigator = LocalAppNavigator.current
    val match = store.data.matches.find { it.id == matchId }
    if (match == null) {
        Column(Modifier.padding(16.dp)) {
            Text("This scorecard is not on the device.")
            SecondaryButton("Back", { navigator.back() })
        }
        return
    }
    val derived = remember(match) { MatchEngine.derive(match) }
    val finished = derived.status == MatchStatus.COMPLETED ||
        derived.status == MatchStatus.ABANDONED ||
        derived.status == MatchStatus.CANCELLED
    var index by remember(match.id) { mutableIntStateOf(derived.innings.lastIndex.coerceAtLeast(0)) }
    val innings = derived.innings.getOrNull(index)
    val accent = match.style.accent()

    Column(Modifier.fillMaxSize().background(PitchBlack)) {
        ScreenHeader(
            title = if (finished) "Scorecard" else match.title,
            onBack = { navigator.back() },
            action = "Share",
            onAction = { store.shareText = scorecardText(match) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (finished || derived.result != null) {
                FinalMatchHero(match = match, derived = derived, accent = accent)
                FinalTopPerformers(match = match, derived = derived, accent = accent)
            } else {
                SurfaceCard(elevation = ElevationMid, accent = accent) {
                    Text("In progress", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(match.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    derived.equation?.let { Text(it, color = Gold) }
                }
                MatchScoreboardStrip(match = match, derived = derived)
            }

            if (derived.innings.isNotEmpty()) {
                InningsSelector(
                    match = match,
                    innings = derived.innings,
                    selected = index,
                    accent = accent,
                    onSelect = { index = it },
                )
            }

            if (innings == null) {
                SurfaceCard(elevation = ElevationLow) {
                    Text("No balls scored yet.", color = Muted)
                }
            } else {
                FinalInningsHeader(match = match, innings = innings, accent = accent)
                FinalBattingCard(match = match, innings = innings, accent = accent)
                FinalBowlingCard(match = match, innings = innings)
                if (innings.fallOfWickets.isNotEmpty()) {
                    FinalFallOfWicketsCard(innings.fallOfWickets)
                }
                if (innings.partnerships.isNotEmpty()) {
                    FinalPartnershipsCard(innings.partnerships)
                }
            }

            derived.playerOfTheMatchId?.let { potmId ->
                PlayerOfTheMatchCard(
                    name = match.playerName(potmId),
                    accent = accent,
                )
            }

            if (!finished) {
                SecondaryButton("Resume scoring", { navigator.navigate(AppRoute.Live(match.id)) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun FinalMatchHero(
    match: Match,
    derived: DerivedMatch,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val result = derived.result
    val winnerId = result?.winnerTeamId
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusXl))
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.34f),
                        PitchCard,
                        PitchBlack,
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(RadiusXl))
            .padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResultStatusChip(derived.status)
                Text(styleTitle(match.style), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Text(
                match.title,
                color = Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroTeamScore(
                    name = match.teamA.name,
                    shortName = match.teamA.shortName,
                    color = Color(match.teamA.color),
                    lines = derived.innings.filter { it.battingTeamId == match.teamA.teamId },
                    isWinner = winnerId == match.teamA.teamId,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(PitchRaised)
                            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text("VS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                HeroTeamScore(
                    name = match.teamB.name,
                    shortName = match.teamB.shortName,
                    color = Color(match.teamB.color),
                    lines = derived.innings.filter { it.battingTeamId == match.teamB.teamId },
                    isWinner = winnerId == match.teamB.teamId,
                    alignEnd = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.25f)),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    result?.summary ?: statusFallback(derived.status),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result?.marginRuns?.let { ResultMarginChip("$it runs") }
                    result?.marginWickets?.let { ResultMarginChip("$it wkts") }
                    if (result?.wonBySuperOver == true) ResultMarginChip("Super over")
                    if (result?.wonByInnings == true) ResultMarginChip("Innings")
                }
                val meta = listOfNotNull(
                    match.venue.takeIf { it.isNotBlank() },
                    match.rules.name.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HeroTeamScore(
    name: String,
    shortName: String,
    color: Color,
    lines: List<DerivedInnings>,
    isWinner: Boolean,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusMd))
            .background(if (isWinner) Lawn.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (isWinner) 1.dp else 0.dp,
                color = if (isWinner) Lawn.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(RadiusMd),
            )
            .padding(10.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            if (!alignEnd) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                shortName.ifBlank { name.take(3).uppercase() },
                fontWeight = FontWeight.Black,
                color = if (isWinner) Lawn else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alignEnd) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        }
        if (lines.isEmpty()) {
            Text("—", color = Muted, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        } else {
            lines.forEach { innings ->
                Text(
                    innings.scoreLine() + if (innings.superOver) " SO" else "",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Ink,
                )
                Text(
                    oversLabel(innings.legalBalls, innings.ballsPerOver) + " ov",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (isWinner) {
            Text("Winner", color = Lawn, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
fun FinalTopPerformers(
    match: Match,
    derived: DerivedMatch,
    accent: Color,
) {
    val batters = derived.innings.flatMap { it.battingRows }
    val bowlers = derived.innings.flatMap { it.bowlingRows }
    val topBat = batters.maxWithOrNull(compareBy<BattingRow>({ it.runs }, { it.strikeRate }))
    val topBowl = bowlers.maxWithOrNull(
        compareBy<BowlingRow>({ it.wickets }, { -it.runs }, { -it.economy }),
    )
    if (topBat == null && topBowl == null) return

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        topBat?.let { row ->
            PerformerTile(
                label = "Top bat",
                name = row.name,
                detail = "${row.runs} (${row.balls})",
                accent = accent,
                modifier = Modifier.weight(1f),
            )
        }
        topBowl?.let { row ->
            val innings = derived.innings.firstOrNull { bowl -> bowl.bowlingRows.any { it.playerId == row.playerId } }
            PerformerTile(
                label = "Best bowl",
                name = row.name,
                detail = "${row.wickets}/${row.runs} · ${oversLabel(row.legalBalls, innings?.ballsPerOver ?: match.rules.overs.ballsPerOver)}",
                accent = Lawn,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PerformerTile(
    label: String,
    name: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(RadiusMd))
            .background(PitchCard)
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(RadiusMd))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label.uppercase(), color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.7.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerAvatar(name, accent, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InningsSelector(
    match: Match,
    innings: List<DerivedInnings>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    if (innings.size == 1) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        innings.forEachIndexed { i, item ->
            val active = i == selected
            Column(
                Modifier
                    .widthIn(min = 120.dp)
                    .clip(RoundedCornerShape(RadiusMd))
                    .background(if (active) accent.copy(alpha = 0.18f) else PitchCard)
                    .border(1.dp, if (active) accent else PitchRaised, RoundedCornerShape(RadiusMd))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    buildString {
                        append(match.teamShort(item.battingTeamId))
                        if (item.superOver) append(" · SO") else append(" · Inn ${item.index + 1}")
                    },
                    color = if (active) accent else Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(item.scoreLine(), fontWeight = FontWeight.Black, color = if (active) Ink else SoftWhite)
            }
        }
    }
}

@Composable
private fun ResultStatusChip(status: MatchStatus) {
    val (label, color) = when (status) {
        MatchStatus.COMPLETED -> "FINAL" to Lawn
        MatchStatus.ABANDONED -> "ABANDONED" to Gold
        MatchStatus.CANCELLED -> "CANCELLED" to WicketRed
        else -> status.name to Muted
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun ResultMarginChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(RadiusSm))
            .background(PitchRaised)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun MatchScoreboardStrip(
    match: Match,
    derived: DerivedMatch,
) {
    SurfaceCard(elevation = ElevationMid) {
        Text("Match score", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamScoreCell(
                name = match.teamA.name,
                shortName = match.teamA.shortName,
                color = Color(match.teamA.color),
                lines = derived.innings.filter { it.battingTeamId == match.teamA.teamId },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(PitchRaised)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("VS", color = Muted, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            TeamScoreCell(
                name = match.teamB.name,
                shortName = match.teamB.shortName,
                color = Color(match.teamB.color),
                lines = derived.innings.filter { it.battingTeamId == match.teamB.teamId },
                modifier = Modifier.weight(1f),
                alignEnd = true,
            )
        }
        derived.result?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.summary, color = Lawn, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TeamScoreCell(
    name: String,
    shortName: String,
    color: Color,
    lines: List<DerivedInnings>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            if (!alignEnd) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                shortName.ifBlank { name.take(3).uppercase() },
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alignEnd) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        }
        if (lines.isEmpty()) {
            Text("—", color = Muted, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        } else {
            lines.forEach { innings ->
                Text(
                    innings.scoreLine() + if (innings.superOver) " SO" else "",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Ink,
                )
                Text(
                    oversLabel(innings.legalBalls, innings.ballsPerOver) + " ov",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FinalInningsHeader(
    match: Match,
    innings: DerivedInnings,
    accent: Color,
) {
    SurfaceCard(elevation = ElevationHigh, accent = accent) {
        Text(
            if (innings.superOver) "Super over" else "Innings ${innings.index + 1}",
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
        Text(
            match.teamName(innings.battingTeamId),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(innings.scoreLine(), fontWeight = FontWeight.Black, fontSize = 34.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${oversLabel(innings.legalBalls, innings.ballsPerOver)} overs",
                    color = Muted,
                    fontWeight = FontWeight.SemiBold,
                )
                inningsRunRate(innings)?.let { rr ->
                    Text(
                        "RR ${rr.twoDecimals()}",
                        color = SoftWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                innings.target?.let { Text("Target $it", color = Gold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun FinalBattingCard(
    match: Match,
    innings: DerivedInnings,
    accent: Color,
) {
    SurfaceCard(elevation = ElevationMid) {
        SectionEyebrow("Batting")
        Spacer(Modifier.height(4.dp))
        ScorecardTableHeader(
            columns = listOf("Batter", "R", "B", "4s", "6s", "SR"),
            widths = listOf(28.dp, 28.dp, 28.dp, 28.dp, 44.dp),
        )
        innings.battingRows.forEachIndexed { i, row ->
            FinalBatterRow(row = row, accent = accent, striped = i % 2 == 1)
        }
        FinalTotalRow(label = "Total", value = innings.scoreLine(), accent = accent)
        Spacer(Modifier.height(10.dp))
        FinalExtrasBlock(innings)
        if (innings.yetToBat.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Did not bat",
                color = Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                innings.yetToBat.joinToString { match.playerName(it) },
                color = SoftWhite.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        innings.powerplayRuns?.let {
            Spacer(Modifier.height(8.dp))
            Text("Powerplay $it", color = Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FinalBatterRow(row: BattingRow, accent: Color, striped: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .background(if (striped) PitchRaised.copy(alpha = 0.45f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(row.name, accent, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.dismissalText, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            StatCell("${row.runs}", bold = true, width = 28.dp)
            StatCell("${row.balls}", width = 28.dp)
            StatCell("${row.fours}", width = 28.dp)
            StatCell("${row.sixes}", width = 28.dp)
            StatCell(row.strikeRate.twoDecimals(), width = 44.dp)
        }
    }
}

@Composable
private fun FinalTotalRow(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(RadiusSm))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = accent)
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
private fun FinalBowlingCard(match: Match, innings: DerivedInnings) {
    SurfaceCard(elevation = ElevationMid) {
        SectionEyebrow("Bowling")
        Spacer(Modifier.height(4.dp))
        ScorecardTableHeader(
            columns = listOf("Bowler", "O", "M", "R", "W", "Eco"),
            widths = listOf(32.dp, 24.dp, 28.dp, 24.dp, 40.dp),
        )
        innings.bowlingRows.forEachIndexed { i, row ->
            FinalBowlerRow(
                row = row,
                ballsPerOver = innings.ballsPerOver,
                match = match,
                striped = i % 2 == 1,
                highlight = row.wickets == innings.bowlingRows.maxOfOrNull { it.wickets } && row.wickets > 0,
            )
        }
    }
}

@Composable
private fun FinalBowlerRow(
    row: BowlingRow,
    ballsPerOver: Int,
    match: Match,
    striped: Boolean,
    highlight: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .background(
                when {
                    highlight -> Lawn.copy(alpha = 0.12f)
                    striped -> PitchRaised.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(row.name, if (highlight) Lawn else OpenTeal, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extrasBits = buildList {
                if (match.rules.extras.widesEnabled && row.wides > 0) add("${row.wides} wd")
                if (match.rules.extras.noBallsEnabled && row.noBalls > 0) add("${row.noBalls} nb")
            }.joinToString(" · ")
            if (extrasBits.isNotBlank()) {
                Text(extrasBits, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        StatCell(oversLabel(row.legalBalls, ballsPerOver), width = 32.dp)
        StatCell("${row.maidens}", width = 24.dp)
        StatCell("${row.runs}", width = 28.dp)
        StatCell("${row.wickets}", bold = true, width = 24.dp)
        StatCell(row.economy.twoDecimals(), width = 40.dp)
    }
}

@Composable
private fun FinalExtrasBlock(innings: DerivedInnings) {
    val extras = innings.extras
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PitchRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Extras", fontWeight = FontWeight.Bold)
            Text("${extras.total}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = OpenTeal)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtraMini("Wd", extras.wides, Modifier.weight(1f))
            ExtraMini("Nb", extras.noBalls, Modifier.weight(1f))
            ExtraMini("B", extras.byes, Modifier.weight(1f))
            ExtraMini("Lb", extras.legByes, Modifier.weight(1f))
            ExtraMini("Pen", extras.penalties, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExtraMini(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(RadiusSm))
            .background(PitchCard)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("$value", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun FinalFallOfWicketsCard(falls: List<FallOfWicket>) {
    SurfaceCard(elevation = ElevationMid) {
        SectionEyebrow("Fall of wickets")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            falls.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { fall ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(RadiusSm))
                                .background(PitchRaised)
                                .padding(10.dp),
                        ) {
                            Text("${fall.wicketNumber}-${fall.teamScore}", fontWeight = FontWeight.Black, color = WicketRed)
                            Text(fall.batterName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fall.overLabel} · ${fall.dismissalType.displayName()}", color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FinalPartnershipsCard(partnerships: List<Partnership>) {
    SurfaceCard(elevation = ElevationMid) {
        SectionEyebrow("Partnerships")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            partnerships.forEachIndexed { index, partnership ->
                val best = partnership.runs == partnerships.maxOf { it.runs }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(if (best) Gold.copy(alpha = 0.12f) else PitchRaised)
                        .border(
                            1.dp,
                            if (best) Gold.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(RadiusSm),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            partnership.batterNames.joinToString(" & "),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append("${partnership.balls} balls")
                                if (best) append(" · Highest")
                            },
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Text("${partnership.runs}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Gold)
                }
            }
        }
    }
}

@Composable
fun PlayerOfTheMatchCard(name: String, accent: Color) {
    SurfaceCard(elevation = ElevationHigh, accent = accent) {
        Text("Player of the match", color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerAvatar(name, accent, size = 52.dp)
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Match award", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionEyebrow(label: String) {
    Text(label.uppercase(), color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
}

@Composable
private fun ScorecardTableHeader(columns: List<String>, widths: List<androidx.compose.ui.unit.Dp>) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .background(PitchRaised)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(columns.first(), Modifier.weight(1f), color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        columns.drop(1).zip(widths).forEach { (label, width) ->
            Text(
                label,
                modifier = Modifier.width(width),
                color = Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun StatCell(value: String, bold: Boolean = false, width: androidx.compose.ui.unit.Dp) {
    Text(
        value,
        modifier = Modifier.width(width),
        fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
        textAlign = TextAlign.End,
        fontSize = 13.sp,
        maxLines = 1,
    )
}

private fun statusFallback(status: MatchStatus): String = when (status) {
    MatchStatus.COMPLETED -> "Match finished"
    MatchStatus.ABANDONED -> "Match abandoned"
    MatchStatus.CANCELLED -> "Match cancelled"
    else -> "Scorecard"
}

private fun inningsRunRate(innings: DerivedInnings): Double? {
    if (innings.legalBalls <= 0) return null
    return innings.runs * innings.ballsPerOver.toDouble() / innings.legalBalls
}
