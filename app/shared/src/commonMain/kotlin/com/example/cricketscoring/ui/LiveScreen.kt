package com.example.cricketscoring.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.currentInnings
import com.example.cricketscoring.engine.legalDismissals
import com.example.cricketscoring.engine.oversLabel
import com.example.cricketscoring.engine.playerName
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.teamName
import com.example.cricketscoring.engine.thisOver
import com.example.cricketscoring.engine.twoDecimals
import com.example.cricketscoring.model.AdditionalScoringEvent
import com.example.cricketscoring.model.AppliedDelivery
import com.example.cricketscoring.model.BallInput
import com.example.cricketscoring.model.BoundaryMarker
import com.example.cricketscoring.model.DismissalInput
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchEvent
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.model.SurfaceContact
import com.example.cricketscoring.model.isZoneOrTargetHit
import com.example.cricketscoring.navigation.AppRoute
import com.example.cricketscoring.navigation.LocalAppNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LiveScreen(store: CricketStore, matchId: String) {
    val navigator = LocalAppNavigator.current
    val match = store.data.matches.find { it.id == matchId }
    if (match == null) {
        CommonColumn(scrollable = false) {
            Text("This match is no longer on the device.")
            SecondaryButton("Back", { navigator.back() })
        }
        return
    }
    val derived = remember(match) { MatchEngine.derive(match) }
    val innings = derived.currentInnings()
    var wicketOpen by remember { mutableStateOf(false) }
    var noBallOpen by remember { mutableStateOf(false) }
    var byeOpen by remember { mutableStateOf(false) }
    var legByeOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppliedDelivery?>(null) }
    var panel by remember { mutableIntStateOf(0) }
    var scorecardInnings by remember(match.id) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().background(PitchBlack)) {
        LiveTopBar(
            title = match.title,
            live = derived.status == MatchStatus.LIVE || derived.status == MatchStatus.SUPER_OVER,
            onBack = { navigator.back() },
            onUndo = { store.undo(match.id) },
        )
        when {
            derived.status == MatchStatus.COMPLETED || derived.status == MatchStatus.ABANDONED -> ResultPanel(store, match)
            innings == null -> CommonColumn(scrollable = false) { Text("This match has not started.") }
            derived.needsBatter -> BatterPrompt(store, match, innings)
            derived.needsBowler -> BowlerPrompt(store, match, innings)
            derived.breakPending || derived.superOverPending -> NextInningsPrompt(store, match, derived.superOverPending)
            else -> {
                val waitingForOvers = panel == 2 && innings.overs.isEmpty()
                CommonColumn(
                    modifier = Modifier.weight(1f),
                    scrollable = !waitingForOvers,
                    verticalPadding = 0.dp,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PanelTabs(modifier = Modifier.padding(top = 16.dp), listOf("Live" to (panel == 0), "Scorecard" to (panel == 1), "Overs" to (panel == 2), "More" to (panel == 3))) {
                        panel = it
                    }
                    if (waitingForOvers) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            OversWaiting(match, innings)
                        }
                    } else {
                        AnimatedContent(targetState = panel, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "panel") { current ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when (current) {
                                    0 -> LiveBoard(match, derived, innings) { editing = it }
                                    1 -> LiveScorecard(match, derived, scorecardInnings) { scorecardInnings = it }
                                    2 -> OversPanel(match, innings)
                                    else -> {
                                        CommentaryPanel(match, innings)
                                        MatchDetails(store, match, derived)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (moreOpen) {
                    MoreDialog(store, match, onLegBye = { moreOpen = false; legByeOpen = true }, onFive = {
                        store.score(match.id, runsInput(match, 5)); moreOpen = false
                    }, onBonus = { eventId ->
                        store.score(match.id, BallInput(scoringEventId = eventId, underarmConfirmed = true)); moreOpen = false
                    }, onSurface = { surface ->
                        store.score(match.id, BallInput(surface = surface, underarmConfirmed = true)); moreOpen = false
                    }, onBoxOut = {
                        val striker = innings.strikerId
                        if (striker != null) store.score(match.id, BallInput(dismissal = DismissalInput(DismissalType.BOX_OUT, striker), underarmConfirmed = true))
                        moreOpen = false
                    }, onDismiss = { moreOpen = false })
                } else {
                    ScoringPad(
                        match = match,
                        onRuns = { runs -> store.score(match.id, runsInput(match, runs)) },
                        onDesignated = { eventId ->
                            store.score(match.id, BallInput(scoringEventId = eventId, underarmConfirmed = true))
                        },
                        onWide = { store.score(match.id, BallInput(wide = true, underarmConfirmed = true)) },
                        onNoBall = { noBallOpen = true },
                        onBye = { byeOpen = true },
                        onWicket = { wicketOpen = true },
                        onMore = { moreOpen = true },
                    )
                }
            }
        }
    }
    if (wicketOpen && innings != null) {
        WicketDialog(match, innings, derived.freeHit, onDismiss = { wicketOpen = false }) { input ->
            store.score(match.id, input)
            wicketOpen = false
        }
    }
    if (noBallOpen) NoBallDialog(match, onDismiss = { noBallOpen = false }) { runs ->
        store.score(match.id, noBallInput(match, runs))
        noBallOpen = false
    }
    if (byeOpen) RunsDialog("Bye", "Byes", onDismiss = { byeOpen = false }, minimum = 1) { runs ->
        store.score(match.id, BallInput(byeRuns = runs, underarmConfirmed = true))
        byeOpen = false
    }
    if (legByeOpen) RunsDialog("Leg-bye", "Leg-byes", onDismiss = { legByeOpen = false }, minimum = 1) { runs ->
        store.score(match.id, BallInput(legByeRuns = runs, underarmConfirmed = true))
        legByeOpen = false
    }
    editing?.let { delivery ->
        DeliveryDialog(match, delivery, onDismiss = { editing = null }, onSave = { input ->
            store.replaceBall(match.id, delivery.eventId, input)
            editing = null
        })
    }
}

@Composable
private fun LiveTopBar(title: String, live: Boolean, onBack: () -> Unit, onUndo: () -> Unit) {
    AppToolbar(
        title = title,
        onBack = onBack,
        actions = {
            if (live) {
                LiveBadge(Modifier.padding(end = 4.dp))
            }
            TextButton(onClick = onUndo) {
                Text("Undo", color = Lawn, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun LiveBoard(
    match: Match,
    derived: com.example.cricketscoring.model.DerivedMatch,
    innings: com.example.cricketscoring.model.DerivedInnings,
    onBall: (AppliedDelivery) -> Unit,
) {
    ScoreboardHero(match, derived, innings)
    BattersCard(match, derived, innings)
    BowlerCard(match, innings, onBall)
    if (derived.freeHit) Notice("Free hit. Only a run out, obstructing the field, or hit the ball twice can dismiss the batter.")
    if (derived.lastManStanding) Notice("Last man standing.")
}

@Composable
private fun ScoreboardHero(
    match: Match,
    derived: com.example.cricketscoring.model.DerivedMatch,
    innings: com.example.cricketscoring.model.DerivedInnings,
) {
    val accent = match.style.accent()
    val active = derived.currentInnings()
    val showingLive = active != null && active.index == innings.index && active.superOver == innings.superOver
    val rate = if (showingLive) {
        derived.currentRunRate
    } else if (innings.legalBalls == 0) {
        0.0
    } else {
        innings.runs * innings.ballsPerOver.toDouble() / innings.legalBalls
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(PitchRaised, PitchCard, PitchBlack)))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${match.teamName(innings.battingTeamId).uppercase()} VS ${match.teamName(innings.bowlingTeamId).uppercase()}",
                color = Muted,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedContent(targetState = "${innings.runs}/${innings.wickets}", label = "score") { score ->
                    Text(score, fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 56.sp)
                }
                Text(innings.oversLabel(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (showingLive) "CURRENT RR" else "RUN RATE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(rate?.twoDecimals() ?: "0.00", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricChip("Overs", innings.maxLegalBalls?.let { "${innings.oversLabel()} / ${oversLabel(it, innings.ballsPerOver)}" } ?: innings.oversLabel(), Modifier.weight(1f))
                if (showingLive) {
                    innings.target?.let { MetricChip("Target", "$it", Modifier.weight(1f), highlight = true) }
                    derived.requiredRunRate?.let { MetricChip("Req RR", it.twoDecimals(), Modifier.weight(1f), highlight = true) }
                }
            }
            if (showingLive) {
                derived.equation?.let { Text(it, color = Gold, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Gold.copy(alpha = 0.12f) else PitchBlack.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (highlight) Gold else Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun BattersCard(
    match: Match,
    derived: com.example.cricketscoring.model.DerivedMatch,
    innings: com.example.cricketscoring.model.DerivedInnings,
) {
    PitchCard {
        Text("CURRENT BATTERS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        BatterRow(match, innings, innings.strikerId, onStrike = true)
        if (match.rules.squad.twoBatters && !derived.lastManStanding) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PitchRaised))
            Spacer(Modifier.height(12.dp))
            BatterRow(match, innings, innings.nonStrikerId, onStrike = false)
        }
    }
}

@Composable
private fun BatterRow(
    match: Match,
    innings: com.example.cricketscoring.model.DerivedInnings,
    playerId: String?,
    onStrike: Boolean,
) {
    val row = innings.battingRows.find { it.playerId == playerId }
    val name = playerId?.let { match.playerName(it) } ?: "—"
    val accent = match.style.accent()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerAvatar(name, if (onStrike) accent else Muted)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (onStrike) {
                    Text("★", color = Gold, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "${row?.runs ?: 0}  (${row?.balls ?: 0} balls · ${row?.fours ?: 0}x4 · ${row?.sixes ?: 0}x6)",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("STR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text((row?.strikeRate ?: 0.0).twoDecimals(), fontWeight = FontWeight.Bold, color = if (onStrike) accent else Ink)
        }
    }
}

@Composable
private fun BowlerCard(
    match: Match,
    innings: com.example.cricketscoring.model.DerivedInnings,
    onBall: (AppliedDelivery) -> Unit,
) {
    val row = innings.bowlingRows.find { it.playerId == innings.bowlerId }
    val name = innings.bowlerId?.let { match.playerName(it) } ?: "—"
    val over = innings.thisOver()
    val number = (over.lastOrNull()?.overIndex ?: 0) + 1
    PitchCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerAvatar(name, Lawn)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${row?.wickets ?: 0}/${row?.runs ?: 0}  (${oversLabel(row?.legalBalls ?: 0, innings.ballsPerOver)} overs)",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ECO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text((row?.economy ?: 0.0).twoDecimals(), fontWeight = FontWeight.Bold, color = Lawn)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("OVER $number", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (over.isEmpty()) {
                repeat(match.rules.overs.ballsPerOver.coerceAtMost(6)) {
                    Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(PitchRaised.copy(alpha = 0.45f)))
                }
            } else {
                over.forEach { ball -> BallChip(ball.symbol, onClick = { onBall(ball) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${over.sumOf { it.totalRuns }} runs · ${over.count { it.dismissal?.isWicket == true }} wickets",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ScoringPad(
    match: Match,
    onRuns: (Int) -> Unit,
    onDesignated: (String) -> Unit,
    onWide: () -> Unit,
    onNoBall: () -> Unit,
    onBye: () -> Unit,
    onWicket: () -> Unit,
    onMore: () -> Unit,
) {
    val rules = match.rules
    val designated = designatedScores(match)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(PitchCard)
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScorePadKey("0", "DOT", { onRuns(0) }, Modifier.weight(1f))
            ScorePadKey("1", "RUN", { onRuns(1) }, Modifier.weight(1f))
            ScorePadKey("2", "RUNS", { onRuns(2) }, Modifier.weight(1f))
            ScorePadKey("3", "RUNS", { onRuns(3) }, Modifier.weight(1f))
            ScorePadKey("4", "FOUR", { onRuns(4) }, Modifier.weight(1f), accent = Gold, contentColor = Color(0xFF241A05))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScorePadKey("6", "SIX", { onRuns(6) }, Modifier.weight(1f), accent = Gold.copy(alpha = 0.85f), contentColor = Color(0xFF241A05))
            designated.forEach { event ->
                ScorePadKey(
                    "${event.runs}",
                    "ZONE",
                    { onDesignated(event.id) },
                    Modifier.weight(1f),
                    accent = Gold.copy(alpha = 0.7f),
                    contentColor = Color(0xFF241A05),
                )
            }
            if (rules.extras.widesEnabled) {
                ScorePadKey("WD", "WIDE", onWide, Modifier.weight(1f))
            }
            if (rules.extras.noBallsEnabled) {
                ScorePadKey("NB", "NO BALL", onNoBall, Modifier.weight(1f))
            }
            if (rules.extras.byesEnabled) {
                ScorePadKey("BY", "BYE", onBye, Modifier.weight(1f))
            }
            ScorePadKey("W", "WICKET", onWicket, Modifier.weight(1f), accent = WicketRed, contentColor = Color.White)
            ScorePadKey("···", "MORE", onMore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BatterPrompt(store: CricketStore, match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    val ids = innings.yetToBat + innings.battingRows.filter { it.retiredHurt }.map { it.playerId }
    CommonColumn(scrollable = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next batter", style = MaterialTheme.typography.headlineSmall)
        ids.distinct().forEach { id ->
            PrimaryButton(match.playerName(id), { store.nextBatter(match.id, id) }, Modifier.fillMaxWidth())
        }
        if (ids.isEmpty()) Text("No batter is left.", color = Muted)
    }
}

@Composable
private fun BowlerPrompt(store: CricketStore, match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    val bowling = if (innings.bowlingTeamId == match.teamA.teamId) match.teamA else match.teamB
    val cap = match.rules.overs.maxOversPerBowler?.times(match.rules.overs.ballsPerOver)
    val last = innings.deliveries.lastOrNull()?.bowlerId
    val options = bowling.players.filter { player ->
        val bowled = innings.bowlingRows.find { it.playerId == player.id }?.legalBalls ?: 0
        val underCap = cap == null || bowled < cap
        val change = !match.rules.overs.mustChangeBowlerEachOver || player.id != last
        underCap && change
    }
    CommonColumn(scrollable = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next ${match.rules.labels.bowlingRole.lowercase()}", style = MaterialTheme.typography.headlineSmall)
        options.forEach { player ->
            PrimaryButton(player.name, { store.nextBowler(match.id, player.id) }, Modifier.fillMaxWidth())
        }
        if (options.isEmpty()) {
            Notice("Nobody can bowl the next over under these rules.")
            SecondaryButton("End the innings", { store.closeInnings(match.id) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NextInningsPrompt(store: CricketStore, match: Match, superOver: Boolean) {
    val derived = MatchEngine.derive(match)
    val battingId = derived.nextBattingTeamId ?: return
    val batting = if (battingId == match.teamA.teamId) match.teamA else match.teamB
    val bowling = if (battingId == match.teamA.teamId) match.teamB else match.teamA
    var striker by remember(battingId) { mutableStateOf(batting.players.first().id) }
    var nonStriker by remember(battingId) { mutableStateOf(batting.players.getOrNull(1)?.id) }
    var bowler by remember(battingId) { mutableStateOf(bowling.players.first().id) }
    val finished = derived.innings.lastOrNull { it.complete }
    val completed = derived.innings.filter { it.complete }
    var review by remember { mutableIntStateOf(0) }
    var reviewed by remember(completed.size) {
        mutableIntStateOf(completed.indexOfFirst { !it.superOver }.coerceAtLeast(0))
    }
    val reviewedInnings = completed.getOrNull(reviewed)
    val breakStartedAt = remember(match.id, finished?.index) {
        finished?.deliveries?.lastOrNull()?.timestampEpochMs?.takeIf { it > 0L } ?: nowMillis()
    }
    CommonColumn(scrollable = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelTabs(
            modifier = Modifier.fillMaxWidth(),
            options = listOf("Break" to (review == 0), "Scorecard" to (review == 1), "Breakdown" to (review == 2)),
        ) { review = it }
        if (review != 0) {
            CompletedInningsPicker(match, completed, reviewed) { reviewed = it }
            reviewedInnings?.let { innings ->
                Text(
                    "${inningsPlace(innings)} · ${match.teamName(innings.battingTeamId)} ${innings.scoreLine()}",
                    color = Muted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (reviewedInnings == null) {
                Text("No completed innings to review yet.", color = Muted)
            } else if (review == 1) {
                InningsTables(match, reviewedInnings)
            } else {
                OversPanel(match, reviewedInnings)
            }
            return@CommonColumn
        }
        Text(if (superOver) "Super Over" else "Innings break", style = MaterialTheme.typography.headlineSmall)
        InningsBreakClock(
            minutes = match.rules.endings.inningsBreakMinutes,
            startedAtEpochMs = breakStartedAt,
            onMinutes = { store.setBreakMinutes(match.id, it) },
        )
        SectionLabel("Score")
        derived.innings.filter { it.complete }.forEach { innings ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(match.teamName(innings.battingTeamId), modifier = Modifier.weight(1f))
                Text(innings.scoreLine(), fontWeight = FontWeight.Bold)
            }
        }
        upcomingTarget(match, derived, superOver)?.let { Text("Target $it", color = Gold, fontWeight = FontWeight.Bold) }
        Text("${batting.name} bat. ${bowling.name} bowl.", color = Muted)
        if (derived.followOnAvailable) SecondaryButton("Enforce follow-on", { store.followOn(match.id) }, Modifier.fillMaxWidth())
        SectionLabel("Striker")
        batting.players.forEach { PickButton(it.name, striker == it.id) { striker = it.id } }
        if (match.rules.squad.twoBatters) {
            SectionLabel("Non-striker")
            batting.players.filter { it.id != striker }.forEach { player ->
                PickButton(player.name, nonStriker == player.id) { nonStriker = player.id }
            }
        }
        SectionLabel(match.rules.labels.bowlingRole)
        bowling.players.forEach { PickButton(it.name, bowler == it.id) { bowler = it.id } }
        PrimaryButton("Start innings", {
            store.startNext(match.id, striker, if (match.rules.squad.twoBatters) nonStriker else null, bowler, superOver || derived.superOverPending)
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun InningsBreakClock(minutes: Int, startedAtEpochMs: Long, onMinutes: (Int) -> Unit) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            now = nowMillis()
        }
    }
    val remaining = breakRemainingMillis(minutes, startedAtEpochMs, now)
    val choices = listOf(2, 5, 10, 15).let { presets -> if (minutes in presets || minutes == 0) presets else (presets + minutes).sorted() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (minutes == 0) "No timer" else clockLabel(remaining),
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = if (remaining == 0L && minutes > 0) Lawn else Gold,
        )
        Text(
            when {
                minutes == 0 -> "Set a break if the sides want one. The next innings can start now."
                remaining == 0L -> "The break is over. Start when both sides are ready."
                else -> "Review the score during the break. You can start the next innings before the timer ends."
            },
            color = Muted,
        )
        Text("Break length", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { choice ->
                val selected = choice == minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Gold else PitchRaised)
                        .clickable { onMinutes(choice) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$choice min", color = if (selected) Color(0xFF241A05) else Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun breakRemainingMillis(minutes: Int, startedAtEpochMs: Long, nowEpochMs: Long): Long {
    if (minutes <= 0) return 0L
    return (startedAtEpochMs + minutes * 60_000L - nowEpochMs).coerceAtLeast(0L)
}

private fun clockLabel(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun CompletedInningsPicker(
    match: Match,
    innings: List<com.example.cricketscoring.model.DerivedInnings>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (innings.size <= 1) return
    val accent = match.style.accent()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        innings.forEachIndexed { index, item ->
            val active = index == selected
            Text(
                inningsPlace(item),
                color = if (active) Color(0xFF14110A) else Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) accent else PitchRaised)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun inningsPlace(innings: com.example.cricketscoring.model.DerivedInnings): String {
    if (innings.superOver) return "Super Over"
    val number = innings.index + 1
    val suffix = when (number) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$number$suffix innings"
}

private fun upcomingTarget(match: Match, derived: com.example.cricketscoring.model.DerivedMatch, superOver: Boolean): Int? {
    val completed = derived.innings.filter { it.complete }
    if (superOver) return completed.lastOrNull { it.superOver }?.runs?.plus(1)
    if (match.rules.overs.inningsPerTeam == 1 && completed.count { !it.superOver } == 1) {
        return completed.first { !it.superOver }.runs + 1
    }
    return null
}

@Composable
private fun PickButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) PrimaryButton(label, onClick, Modifier.fillMaxWidth()) else SecondaryButton(label, onClick, Modifier.fillMaxWidth())
}

@Composable
private fun ResultPanel(store: CricketStore, match: Match) {
    val navigator = LocalAppNavigator.current
    val derived = MatchEngine.derive(match)
    val accent = match.style.accent()
    var awarding by remember { mutableStateOf(false) }
    CommonColumn(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FinalMatchHero(match = match, derived = derived, accent = accent)
        FinalTopPerformers(match = match, derived = derived, accent = accent)
        derived.playerOfTheMatchId?.let { potmId ->
            PlayerOfTheMatchCard(name = match.playerName(potmId), accent = accent)
        }
        PrimaryButton("Open full scorecard", { navigator.navigate(AppRoute.Scorecard(match.id)) }, Modifier.fillMaxWidth())
        SecondaryButton(
            if (derived.playerOfTheMatchId == null) "Choose player of the match" else "Change player of the match",
            { awarding = true },
            Modifier.fillMaxWidth(),
        )
    }
    if (awarding) {
        AlertDialog(
            onDismissRequest = { awarding = false },
            title = { Text("Player of the match") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (match.teamA.players + match.teamB.players).forEach { player ->
                        TextButton(onClick = {
                            store.award(match.id, player.id)
                            awarding = false
                        }) { Text(player.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { awarding = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun WicketDialog(
    match: Match,
    innings: com.example.cricketscoring.model.DerivedInnings,
    freeHit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BallInput) -> Unit,
) {
    var wide by remember { mutableStateOf(false) }
    var noBall by remember { mutableStateOf(false) }
    val types = legalDismissals(match.rules, freeHit, wide, noBall)
    var type by remember(types) {
        mutableStateOf(
            if (wide && DismissalType.STUMPED in types) DismissalType.STUMPED
            else types.firstOrNull() ?: DismissalType.BOWLED
        )
    }
    val crease = listOfNotNull(innings.strikerId, innings.nonStrikerId)
    var batter by remember { mutableStateOf(innings.strikerId ?: crease.first()) }
    var fielder by remember { mutableStateOf(if (innings.bowlingTeamId == match.teamA.teamId) match.teamA.players.first().id else match.teamB.players.first().id) }
    var runs by remember { mutableStateOf(0) }
    if (type !in types && types.isNotEmpty()) type = types.first()
    val strikerOnly = type in match.rules.dismissals.strikerOnly
    val dismissedId = if (strikerOnly) innings.strikerId ?: batter else batter
    val wideAllowed = !freeHit && match.rules.extras.widesEnabled
    val noBallAllowed = !freeHit && match.rules.extras.noBallsEnabled
    val fielders = if (innings.bowlingTeamId == match.teamA.teamId) match.teamA.players else match.teamB.players
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PitchCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Wicket", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(match.playerName(dismissedId), color = WicketRed, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (freeHit) {
                    Text("Free hit. Only a run out, obstructing the field, or hitting the ball twice can be out.", color = Muted, fontSize = 13.sp)
                }
                if (wideAllowed || noBallAllowed) {
                    WicketSection("This ball") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WicketChoice("Legal", !wide && !noBall, { wide = false; noBall = false }, Modifier.weight(1f))
                            if (wideAllowed) WicketChoice("Wide", wide, { wide = true; noBall = false }, Modifier.weight(1f))
                            if (noBallAllowed) WicketChoice("No-ball", noBall, { noBall = true; wide = false }, Modifier.weight(1f))
                        }
                    }
                }
                val ballNote = when {
                    noBall -> "Only a run out is possible. Either batter can be out."
                    wide && type == DismissalType.STUMPED -> "Stumped. Only the striker is out."
                    wide && type == DismissalType.RUN_OUT -> "Run out. Either batter can be out."
                    else -> null
                }
                if (ballNote != null) Text(ballNote, color = Muted, fontSize = 13.sp)
                val choices = if (wide) {
                    listOf(DismissalType.STUMPED, DismissalType.RUN_OUT).filter { it in types }
                } else {
                    types
                }
                val runOutDetails = !wide || type == DismissalType.RUN_OUT
                if (choices.isEmpty()) {
                    Text("No dismissal is legal on this ball.", color = Muted)
                } else if (!noBall && (wide || choices.size > 1)) {
                    WicketSection(if (wide) "On this wide" else "How") {
                        choices.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { option ->
                                    WicketChoice(option.displayName(), option == type, { type = option }, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (runOutDetails && !strikerOnly && crease.size > 1) {
                    WicketSection("Batter") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            crease.forEach { id ->
                                WicketChoice(match.playerName(id), batter == id, { batter = id }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (runOutDetails && type in match.rules.dismissals.fielderRequired) {
                    WicketSection("Fielder") {
                        fielders.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { player ->
                                    WicketChoice(player.name, fielder == player.id, { fielder = player.id }, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (runOutDetails && (type == DismissalType.RUN_OUT || type == DismissalType.DIRECT_HIT)) {
                    WicketSection("Runs completed") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 2, 3).forEach { value ->
                                WicketChoice("$value", runs == value, { runs = value }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WicketChoice("Cancel", false, onDismiss, Modifier.weight(1f))
                Box(
                    Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (types.isEmpty()) PitchRaised else WicketRed)
                        .clickable(enabled = types.isNotEmpty()) {
                            onConfirm(
                                BallInput(
                                    batterRuns = if (type == DismissalType.RUN_OUT || type == DismissalType.DIRECT_HIT) runs else 0,
                                    wide = wide,
                                    noBall = noBall,
                                    deadBall = type == DismissalType.TIMED_OUT,
                                    dismissal = DismissalInput(
                                        type,
                                        dismissedId,
                                        if (type in match.rules.dismissals.fielderRequired) fielder else null,
                                    ),
                                    underarmConfirmed = true,
                                ),
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Save wicket", color = if (types.isEmpty()) Muted else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WicketSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun WicketChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) WicketRed else PitchRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RunsDialog(title: String, label: String, onDismiss: () -> Unit, minimum: Int = 0, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(label, color = Muted)
                Row { (minimum..6).forEach { runs -> TextButton(onClick = { onPick(runs) }) { Text("$runs") } } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NoBallDialog(match: Match, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val choices = noBallChoices(match)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No-ball") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Runs off the bat", color = Muted)
                choices.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { choice ->
                            val accent = choice.highlight
                            ScorePadKey(
                                "${choice.runs}",
                                choice.label,
                                { onPick(choice.runs) },
                                Modifier.weight(1f),
                                accent = if (accent) Gold.copy(alpha = 0.85f) else PitchRaised,
                                contentColor = if (accent) Color(0xFF241A05) else Ink,
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoreDialog(
    store: CricketStore,
    match: Match,
    onLegBye: () -> Unit,
    onFive: () -> Unit,
    onBonus: (String) -> Unit,
    onSurface: (SurfaceContact) -> Unit,
    onBoxOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    var penalty by remember { mutableStateOf(1) }
    var completed by remember { mutableStateOf(1) }
    var extra by remember { mutableStateOf(1) }
    var abandon by remember { mutableStateOf(false) }
    var endInnings by remember { mutableStateOf(false) }
    val rules = match.rules
    PitchCard(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("More", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        val deliveryCap = rules.extras.maxRunsPerDelivery
        val fiveAllowed = rules.boundaries.runningBetweenWicketsEnabled && (deliveryCap == null || deliveryCap >= 5)
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rules.boxOut.enabled) SecondaryButton("Box Out", onBoxOut, Modifier.fillMaxWidth())
                if (rules.extras.legByesEnabled) SecondaryButton("Leg-bye", onLegBye, Modifier.fillMaxWidth())
                if (fiveAllowed) SecondaryButton("5 runs", onFive, Modifier.fillMaxWidth())
                rules.additionalScoring.events.filter { it.enabled && !it.isDesignatedScore() }.forEach { event ->
                    SecondaryButton("${event.name} · ${event.runs}", { onBonus(event.id) }, Modifier.fillMaxWidth())
                }
                surfaceKeys(rules).forEach { (label, surface) ->
                    SecondaryButton(label, { onSurface(surface) }, Modifier.fillMaxWidth())
                }
                if (rules.extras.penaltyRunsEnabled) {
                    Text("Penalty runs: $penalty", color = Muted)
                    Row { (1..5).forEach { TextButton(onClick = { penalty = it }) { Text("$it") } } }
                    SecondaryButton("Add penalty", {
                        store.score(match.id, BallInput(deadBall = true, penaltyRuns = penalty))
                        onDismiss()
                    }, Modifier.fillMaxWidth())
                }
                if (rules.extras.overthrowsEnabled) {
                    Text("Overthrow off the bat. Completed $completed, extra $extra", color = Muted)
                    Row { (0..3).forEach { TextButton(onClick = { completed = it }) { Text("Hit $it") } } }
                    Row { (1..6).forEach { TextButton(onClick = { extra = it }) { Text("+$it") } } }
                    SecondaryButton("Add overthrow", {
                        store.score(match.id, BallInput(batterRuns = completed, overthrowBatterRuns = extra, underarmConfirmed = true))
                        onDismiss()
                    }, Modifier.fillMaxWidth())
                }
                SecondaryButton("Dead ball", {
                    store.score(match.id, BallInput(deadBall = true))
                    onDismiss()
                }, Modifier.fillMaxWidth())
                val crease = MatchEngine.derive(match).currentInnings()
                crease?.strikerId?.let { id ->
                    SecondaryButton("Retire ${match.playerName(id)} hurt", { store.retire(match.id, id, true); onDismiss() }, Modifier.fillMaxWidth())
                    SecondaryButton("Retire ${match.playerName(id)} out", { store.retire(match.id, id, false); onDismiss() }, Modifier.fillMaxWidth())
                }
                SecondaryButton("End innings", { endInnings = true }, Modifier.fillMaxWidth())
                SecondaryButton("Abandon match", { abandon = true }, Modifier.fillMaxWidth())
                SecondaryButton("${match.teamA.name} concedes", { store.concede(match.id, match.teamA.teamId); onDismiss() }, Modifier.fillMaxWidth())
                SecondaryButton("${match.teamB.name} concedes", { store.concede(match.id, match.teamB.teamId); onDismiss() }, Modifier.fillMaxWidth())
            }
    }
    if (abandon) {
        ConfirmDialog("Abandon the match?", "The result will be no result. You can undo this.", "Abandon", {
            store.abandon(match.id, "Abandoned")
            abandon = false
            onDismiss()
        }, { abandon = false }, destructive = true)
    }
    if (endInnings) {
        ConfirmDialog("End this innings?", "The score already recorded stays as it is. You can undo this.", "End innings", {
            store.closeInnings(match.id)
            endInnings = false
            onDismiss()
        }, { endInnings = false }, destructive = true)
    }
}

@Composable
private fun DeliveryDialog(match: Match, delivery: AppliedDelivery, onDismiss: () -> Unit, onSave: (BallInput) -> Unit) {
    val event = match.events.find { it.id == delivery.eventId } as? MatchEvent.Ball
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(delivery.symbol) },
        text = {
            Column {
                Text("${match.playerName(delivery.batterId)} · ${match.playerName(delivery.bowlerId)}")
                Text("${delivery.totalRuns} runs · ${if (delivery.legal) "legal ball" else "not a legal ball"}", color = Muted)
                delivery.dismissal?.let { Text(it.dismissalType.displayName(), color = WicketRed) }
                delivery.commentary?.let { Text(it) }
                if (event != null && event.input.dismissal == null) {
                    Text("Replace this ball", color = Muted, modifier = Modifier.padding(top = 8.dp))
                    Row { (0..6).forEach { runs -> TextButton(onClick = { onSave(runsInput(match, runs)) }) { Text("$runs") } } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LiveScorecard(
    match: Match,
    derived: com.example.cricketscoring.model.DerivedMatch,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val list = derived.innings
    val selected = selectedIndex.takeIf { it in list.indices } ?: list.lastIndex
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InningsSelector(
            match = match,
            innings = list,
            selected = selected,
            accent = match.style.accent(),
            onSelect = onSelect,
        )
        list.getOrNull(selected)?.let { shown ->
            ScoreboardHero(match, derived, shown)
            InningsTables(match, shown)
        }
    }
}

@Composable
private fun InningsTables(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    var yetToBatExpanded by remember { mutableStateOf(false) }
    PitchCard {
        Text("Batting", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        innings.battingRows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(row.name, match.style.accent(), size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.name, fontWeight = FontWeight.SemiBold)
                    Text(row.dismissalText, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${row.runs}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${row.balls}b · ${row.fours}x4 · ${row.sixes}x6", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (innings.yetToBat.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            YetToBatSection(
                match = match,
                playerIds = innings.yetToBat,
                expanded = yetToBatExpanded,
                onToggle = { yetToBatExpanded = !yetToBatExpanded },
            )
        }
        Spacer(Modifier.height(12.dp))
        ExtrasCard(innings.extras)
        if (innings.fallOfWickets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Fall of wickets", fontWeight = FontWeight.SemiBold)
            Text(innings.fallOfWickets.joinToString("   ") { "${it.wicketNumber}-${it.teamScore} ${it.batterName}" }, color = Muted)
        }
        innings.partnerships.lastOrNull { it.endedAtEpochMs == null || it == innings.partnerships.last() }?.let { partnership ->
            Spacer(Modifier.height(12.dp))
            PartnershipCard(partnership)
        }
    }
    PitchCard {
        Text("Bowling", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        innings.bowlingRows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(row.name, Lawn, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${oversLabel(row.legalBalls, innings.ballsPerOver)} ov · ${row.maidens} m" +
                            (if (match.rules.extras.widesEnabled) " · ${row.wides} wd" else "") +
                            (if (match.rules.extras.noBallsEnabled) " · ${row.noBalls} nb" else ""),
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${row.wickets}/${row.runs}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Eco ${row.economy.twoDecimals()}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun YetToBatSection(
    match: Match,
    playerIds: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val visible = if (expanded) playerIds else playerIds.take(2)
    val hiddenCount = (playerIds.size - visible.size).coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(PitchRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = playerIds.size > 2, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Yet to bat", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    if (expanded) "${playerIds.size} in the queue" else "Next up",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (playerIds.size > 2) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(match.style.accent().copy(alpha = 0.16f))
                        .border(1.dp, match.style.accent().copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (expanded) "Collapse ▲" else "Expand +$hiddenCount ▼",
                        color = match.style.accent(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        visible.forEach { id ->
            val name = match.playerName(id)
            val queueNumber = playerIds.indexOf(id) + 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(PitchCard)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayerAvatar(name, match.style.accent(), size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (queueNumber == 1) "Next batter" else "Waiting · position $queueNumber",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("#$queueNumber", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ExtrasCard(extras: com.example.cricketscoring.model.Extras) {
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
            ExtraStatChip("Wd", extras.wides, Modifier.weight(1f))
            ExtraStatChip("Nb", extras.noBalls, Modifier.weight(1f))
            ExtraStatChip("B", extras.byes, Modifier.weight(1f))
            ExtraStatChip("Lb", extras.legByes, Modifier.weight(1f))
            ExtraStatChip("Pen", extras.penalties, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExtraStatChip(label: String, value: Int, modifier: Modifier = Modifier) {
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
private fun PartnershipCard(partnership: com.example.cricketscoring.model.Partnership) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusMd))
            .background(Gold.copy(alpha = 0.12f))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(RadiusMd))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Partnership", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            partnership.batterNames.take(2).forEachIndexed { index, name ->
                if (index > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Gold.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("&", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(PitchCard)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerAvatar(name, Gold, size = 30.dp)
                    Text(name, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(PitchCard)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Runs", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${partnership.runs}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Gold)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(PitchCard)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Balls", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${partnership.balls}", fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun OversPanel(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    var open by remember { mutableStateOf(setOf<Int>()) }
    if (innings.overs.isEmpty()) {
        OversWaiting(match, innings)
        return
    }
    innings.overs.asReversed().forEach { over ->
        val number = over.overIndex + 1
        val expanded = over.overIndex in open
        PitchCard(onClick = { open = if (expanded) open - over.overIndex else open + over.overIndex }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Over $number · ${over.bowlerName}", fontWeight = FontWeight.Bold)
                    Text("${over.runs} runs · ${over.wickets} wkts · ${over.scoreAfter}/${over.wicketsAfter}", color = Muted)
                }
                Text(if (expanded) "Hide" else "Open", color = Lawn, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                over.symbols.forEach { symbol -> BallChip(symbol) }
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                over.deliveries.forEach { ball ->
                    Text(
                        "${ball.symbol}  ${match.playerName(ball.batterId)}  ${ball.commentary ?: if (ball.legal) "legal" else "extra"}",
                        color = Muted,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OversWaiting(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    val accent = match.style.accent()
    val slots = innings.ballsPerOver.coerceIn(1, 8)
    val bowler = innings.bowlerId?.let { match.playerName(it) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(PitchRaised, PitchCard, PitchBlack)))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.1f))
                .border(1.5.dp, accent.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OVER", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("1", color = accent, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 26.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "The first over has not started",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (bowler != null) "$bowler has the ball. Score a delivery and it will open this over."
            else "Score a delivery and it will open this over.",
            color = Muted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(slots) { index ->
                val next = index == 0
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (next) accent.copy(alpha = 0.16f) else PitchBlack.copy(alpha = 0.45f))
                        .border(1.dp, if (next) accent else Muted.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (next) accent else Muted.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentaryPanel(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    PitchCard {
        Text("Ball by ball", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        if (innings.deliveries.isEmpty()) Text("No balls yet.", color = Muted)
        innings.deliveries.asReversed().forEach { ball ->
            val note = ball.commentary ?: ball.scoringEventName
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BallChip(ball.symbol)
                Column(Modifier.weight(1f)) {
                    Text("${match.playerName(ball.batterId)} to ${match.playerName(ball.bowlerId)}", fontWeight = FontWeight.SemiBold)
                    note?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                Text("${ball.totalRuns}", color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchDetails(store: CricketStore, match: Match, derived: com.example.cricketscoring.model.DerivedMatch) {
    val rules = match.rules
    Text("${derived.liveTeamA.size} eligible for ${match.teamA.name}", color = Muted)
    Text(derived.liveTeamA.joinToString { it.name })
    Text("${derived.liveTeamB.size} eligible for ${match.teamB.name}", color = Muted)
    Text(derived.liveTeamB.joinToString { it.name })
    if (!rules.roster.allowUnevenTeamSizes && derived.liveTeamA.size != derived.liveTeamB.size) {
        Notice("The teams are uneven, and this match did not allow that.")
    }
    rules.boxOut.takeIf { it.enabled }?.let { Text("Box Out: ${it.description.ifBlank { "Enabled for this ground." }}") }
    rules.additionalScoring.events.filter { it.enabled }.forEach { Text("${it.name}: ${it.runs}") }
    if (rules.lastMan.enabled) Text("Last-man standing from team size ${rules.lastMan.minTeamSize}, when ${rules.lastMan.remainingBattersToActivate} remain.")
    SectionLabel("Change history")
    if (derived.rosterChanges.isEmpty()) Text("No roster changes.", color = Muted)
    derived.rosterChanges.forEach { change ->
        Text("${change.kind.name.lowercase().replace('_', ' ')} · ${change.playerName}")
        Text("${change.reason}  ${change.score ?: 0}/${change.wickets ?: 0}", color = Muted)
    }
    RosterActions(store, match, derived)
}

@Composable
private fun RosterActions(store: CricketStore, match: Match, derived: com.example.cricketscoring.model.DerivedMatch) {
    val roster = match.rules.roster
    if (!roster.inMatchSwitchingAllowed && !roster.allowInjuredReplacement && !roster.allowAddDuringMatch && !roster.allowLoanToOpponent) return
    var approvalA by remember { mutableStateOf(!roster.requireBothTeamApproval) }
    var approvalB by remember { mutableStateOf(!roster.requireBothTeamApproval) }
    var reason by remember { mutableStateOf("Agreed by both sides") }
    var name by remember { mutableStateOf("") }
    if (roster.requireBothTeamApproval) {
        ToggleRow("${match.teamA.name} approves", approvalA) { approvalA = it }
        ToggleRow("${match.teamB.name} approves", approvalB) { approvalB = it }
    }
    NameField("Reason", reason) { reason = it }
    if (roster.allowAddDuringMatch) {
        NameField("New player", name) { name = it }
        listOf(match.teamA, match.teamB).forEach { team ->
            SecondaryButton("Add to ${team.name}", {
                if (name.isBlank()) store.showToast("Name the player being added.", AppToastType.Warning)
                else store.addMatchPlayer(match.id, team.teamId, MatchPlayer(freshId("player"), name.trim()), reason, approvalA, approvalB)
            })
        }
    }
    if (roster.inMatchSwitchingAllowed || roster.allowInjuredReplacement) {
        derived.liveTeamA.forEach { player ->
            SecondaryButton("Replace ${player.name}", {
                if (name.isBlank()) store.showToast("Name the incoming player.", AppToastType.Warning)
                else store.switchPlayer(match.id, match.teamA.teamId, player.id, MatchPlayer(freshId("player"), name.trim()), reason, injured = roster.allowInjuredReplacement && !roster.inMatchSwitchingAllowed, approvalA, approvalB)
            })
        }
        derived.liveTeamB.forEach { player ->
            SecondaryButton("Replace ${player.name}", {
                if (name.isBlank()) store.showToast("Name the incoming player.", AppToastType.Warning)
                else store.switchPlayer(match.id, match.teamB.teamId, player.id, MatchPlayer(freshId("player"), name.trim()), reason, injured = roster.allowInjuredReplacement && !roster.inMatchSwitchingAllowed, approvalA, approvalB)
            })
        }
    }
    if (roster.allowLoanToOpponent) {
        derived.liveTeamA.forEach { player ->
            SecondaryButton("Lend ${player.name} to ${match.teamB.name}", {
                store.lendPlayer(match.id, match.teamA.teamId, match.teamB.teamId, player.id, reason, temporary = true, approvalA, approvalB)
            })
        }
        derived.liveTeamB.forEach { player ->
            SecondaryButton("Lend ${player.name} to ${match.teamA.name}", {
                store.lendPlayer(match.id, match.teamB.teamId, match.teamA.teamId, player.id, reason, temporary = true, approvalA, approvalB)
            })
        }
    }
}

private fun AdditionalScoringEvent.isDesignatedScore(): Boolean = isZoneOrTargetHit()

private fun designatedScores(match: Match): List<AdditionalScoringEvent> =
    match.rules.additionalScoring.events.filter { it.enabled && it.isDesignatedScore() }

private data class NoBallChoice(val runs: Int, val label: String, val highlight: Boolean = false)

private fun noBallChoices(match: Match): List<NoBallChoice> {
    val rules = match.rules
    val room = rules.extras.maxRunsPerDelivery?.let { (it - rules.extras.noBallRuns).coerceAtLeast(0) }
    fun allowed(runs: Int) = runs >= 0 && (room == null || runs <= room)
    val choices = linkedMapOf<Int, NoBallChoice>()
    fun add(runs: Int, label: String, highlight: Boolean = false) {
        if (allowed(runs) && runs !in choices) choices[runs] = NoBallChoice(runs, label, highlight)
    }
    add(0, "DOT")
    if (rules.boundaries.runningBetweenWicketsEnabled) {
        add(1, "RUN")
        add(2, "RUNS")
        add(3, "RUNS")
        add(5, "RUNS")
    }
    if (rules.boundaries.foursEnabled) add(rules.boundaries.fourRuns, "FOUR", highlight = true)
    if (rules.boundaries.sixesEnabled) add(rules.boundaries.sixRuns, "SIX", highlight = true)
    rules.additionalScoring.events.filter { it.enabled }.sortedBy { it.runs }.forEach { event ->
        add(event.runs, if (event.isDesignatedScore()) "ZONE" else "BONUS", highlight = true)
    }
    return choices.values.sortedBy { it.runs }
}

private fun noBallInput(match: Match, runs: Int): BallInput {
    val boundary = boundaryFor(match, runs)
    val event = if (boundary == null) scoringEventForRuns(match, runs) else null
    return BallInput(
        noBall = true,
        batterRuns = if (boundary == null && event == null) runs else 0,
        boundary = boundary,
        scoringEventId = event?.id,
        underarmConfirmed = true,
    )
}

private fun scoringEventForRuns(match: Match, runs: Int): AdditionalScoringEvent? {
    if (runs == 0 || runs == 5 || runs in 1..3) return null
    return match.rules.additionalScoring.events
        .filter { it.enabled && it.runs == runs }
        .minByOrNull { if (it.isDesignatedScore()) 0 else 1 }
}

private fun runsInput(match: Match, runs: Int): BallInput {
    val boundary = boundaryFor(match, runs)
    return BallInput(
        batterRuns = if (boundary == null) runs else 0,
        boundary = boundary,
        underarmConfirmed = true,
    )
}

private fun boundaryFor(match: Match, runs: Int): BoundaryMarker? = when {
    match.rules.boundaries.foursEnabled && runs == match.rules.boundaries.fourRuns -> BoundaryMarker.FOUR
    match.rules.boundaries.sixesEnabled && runs == match.rules.boundaries.sixRuns && runs != match.rules.boundaries.fourRuns -> BoundaryMarker.SIX
    else -> null
}

private fun surfaceKeys(rules: com.example.cricketscoring.model.RulesProfile) = buildList {
    rules.boundaries.sideWallRuns?.let { add("Side $it" to SurfaceContact.SIDE_WALL) }
    rules.boundaries.backWallRuns?.let { add("Back $it" to SurfaceContact.BACK_WALL) }
    rules.boundaries.ceilingRuns?.let { add("Ceiling $it" to SurfaceContact.CEILING) }
    rules.boundaries.netRuns?.let { add("Net $it" to SurfaceContact.NET) }
    rules.boundaries.zoneBonusRuns?.let { add("Zone +$it" to SurfaceContact.DESIGNATED_ZONE) }
    if (rules.boundaries.roofIsDeadBall && rules.style == com.example.cricketscoring.model.CricketStyle.UNDERARM_BOX) {
        add("Roof" to SurfaceContact.ROOF)
    }
}

@Preview
@Composable
private fun LiveScreenPreview() {
    PreviewShell { LiveScreen(it, PreviewIds.MatchLive) }
}
