package com.example.cricketscoring.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.currentInnings
import com.example.cricketscoring.engine.legalDismissals
import com.example.cricketscoring.engine.oversLabel
import com.example.cricketscoring.engine.playerName
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.teamName
import com.example.cricketscoring.engine.thisOver
import com.example.cricketscoring.engine.twoDecimals
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

@Composable
fun LiveScreen(store: CricketStore, matchId: String) {
    val match = store.data.matches.find { it.id == matchId }
    if (match == null) {
        Column(Modifier.padding(16.dp)) {
            Text("This match is no longer on the device.")
            SecondaryButton("Back", { store.pop() })
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
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(match.title, onBack = { store.pop() }, action = "Undo", onAction = { store.undo(match.id) })
        when {
            derived.status == MatchStatus.COMPLETED || derived.status == MatchStatus.ABANDONED -> ResultPanel(store, match)
            innings == null -> Column(Modifier.padding(16.dp)) { Text("This match has not started.") }
            derived.needsBatter -> BatterPrompt(store, match, innings)
            derived.needsBowler -> BowlerPrompt(store, match, innings)
            derived.breakPending || derived.superOverPending -> NextInningsPrompt(store, match, derived.superOverPending)
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    ChoiceRow(listOf("Live" to (panel == 0), "Scorecard" to (panel == 1), "Overs" to (panel == 2), "More" to (panel == 3))) {
                        panel = it
                    }
                    when (panel) {
                        0 -> {
                            ScoreHeader(match, derived, innings)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                BatterCard(match, innings, innings.strikerId, onStrike = true, Modifier.weight(1f))
                                if (match.rules.squad.twoBatters && !derived.lastManStanding) {
                                    BatterCard(match, innings, innings.nonStrikerId, onStrike = false, Modifier.weight(1f))
                                }
                            }
                            BowlerLine(match, innings)
                            ThisOver(match, innings) { editing = it }
                            if (derived.freeHit) Notice("Free hit. Only a run out, obstructing the field, or hit the ball twice can dismiss the batter.")
                            if (derived.lastManStanding) Notice("Last man standing.")
                        }
                        1 -> InningsTables(match, innings)
                        2 -> OversPanel(match, innings)
                        else -> {
                            CommentaryPanel(match, innings)
                            MatchDetails(store, match, derived)
                        }
                    }
                }
                if (moreOpen) {
                    MoreDialog(store, match, onWicket = { moreOpen = false; wicketOpen = true }, onWide = {
                        store.score(match.id, BallInput(wide = true, underarmConfirmed = true)); moreOpen = false
                    }, onNoBall = { moreOpen = false; noBallOpen = true }, onBye = { moreOpen = false; byeOpen = true }, onLegBye = { moreOpen = false; legByeOpen = true }, onFive = {
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
                    ScoringPad(onRuns = { runs -> store.score(match.id, runsInput(match, runs)) }, onMore = { moreOpen = true })
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
    if (noBallOpen) RunsDialog("No-ball", "Runs off the bat", onDismiss = { noBallOpen = false }) { runs ->
        val boundary = boundaryFor(match, runs)
        store.score(match.id, BallInput(noBall = true, batterRuns = if (boundary == null) runs else 0, boundary = boundary, underarmConfirmed = true))
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
private fun ScoreHeader(match: Match, derived: com.example.cricketscoring.model.DerivedMatch, innings: com.example.cricketscoring.model.DerivedInnings) {
    Text("${match.teamName(innings.battingTeamId)} batting", color = Muted)
    Text("${match.teamName(innings.bowlingTeamId)} bowling", color = Muted)
    AnimatedContent(targetState = "${innings.runs}/${innings.wickets}") { score ->
        Text(score, fontSize = 64.sp, fontWeight = FontWeight.Black, color = match.style.accent())
    }
    val maxOvers = innings.maxLegalBalls?.let { oversLabel(it, innings.ballsPerOver) }
    val left = innings.maxLegalBalls?.let { max -> oversLabel((max - innings.legalBalls).coerceAtLeast(0), innings.ballsPerOver) }
    Text(
        if (maxOvers != null && left != null) "${innings.oversLabel()} of $maxOvers overs · $left left" else "Overs ${innings.oversLabel()}",
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("CRR ${derived.currentRunRate?.twoDecimals() ?: "0.00"}", color = Muted)
        derived.requiredRunRate?.let { Text("RRR ${it.twoDecimals()}", color = Gold) }
    }
    innings.target?.let { Text("Target $it", color = Gold) }
    derived.equation?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    if (match.rules.overs.underarmOnly) Text("Underarm", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BatterCard(
    match: Match,
    innings: com.example.cricketscoring.model.DerivedInnings,
    playerId: String?,
    onStrike: Boolean,
    modifier: Modifier = Modifier,
) {
    val row = innings.battingRows.find { it.playerId == playerId }
    val name = playerId?.let { match.playerName(it) } ?: "—"
    PitchCard(modifier) {
        Text(if (onStrike) "On strike" else "Other batter", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${row?.runs ?: 0} (${row?.balls ?: 0})", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BowlerLine(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    val row = innings.bowlingRows.find { it.playerId == innings.bowlerId }
    val name = innings.bowlerId?.let { match.playerName(it) } ?: "—"
    Text(
        "${match.rules.labels.bowlingRole} $name  ${oversLabel(row?.legalBalls ?: 0, innings.ballsPerOver)}-${row?.maidens ?: 0}-${row?.runs ?: 0}-${row?.wickets ?: 0}  Econ ${(row?.economy ?: 0.0).twoDecimals()}",
        color = Muted,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ThisOver(match: Match, innings: com.example.cricketscoring.model.DerivedInnings, onTap: (AppliedDelivery) -> Unit) {
    val over = innings.thisOver()
    val number = (over.lastOrNull()?.overIndex ?: 0) + 1
    Text("Over $number · ${over.sumOf { it.totalRuns }} runs · ${over.count { it.dismissal?.isWicket == true }} wickets", color = Muted)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (over.isEmpty()) Text("No balls yet", color = Muted)
        over.forEach { ball ->
            TextButton(onClick = { onTap(ball) }) { Text(ball.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
    over.lastOrNull()?.let { ball ->
        Text("${match.playerName(ball.batterId)} to ${match.playerName(ball.bowlerId)}", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScoringPad(onRuns: (Int) -> Unit, onMore: () -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3).forEach { runs -> ScoreKey("$runs", { onRuns(runs) }, Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreKey("4", { onRuns(4) }, Modifier.weight(1f))
            ScoreKey("6", { onRuns(6) }, Modifier.weight(1f))
            ScoreKey("More", onMore, Modifier.weight(1f), filled = false)
        }
    }
}

@Composable
private fun ScoreKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, wicket: Boolean = false) {
    val colors = when {
        wicket -> ButtonDefaults.buttonColors(containerColor = WicketRed, contentColor = androidx.compose.ui.graphics.Color.White)
        filled -> ButtonDefaults.buttonColors(containerColor = PitchRaised, contentColor = Ink)
        else -> ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    }
    if (filled || wicket) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), colors = colors) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    } else {
        androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}

@Composable
private fun BatterPrompt(store: CricketStore, match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    val ids = innings.yetToBat + innings.battingRows.filter { it.retiredHurt }.map { it.playerId }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (superOver) "Super Over" else "Next innings", style = MaterialTheme.typography.headlineSmall)
        Text("${batting.name} bat. ${bowling.name} bowl.", color = Muted)
        derived.innings.lastOrNull { it.complete }?.target?.let { Text("Target $it", color = Gold) }
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
private fun PickButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) PrimaryButton(label, onClick, Modifier.fillMaxWidth()) else SecondaryButton(label, onClick, Modifier.fillMaxWidth())
}

@Composable
private fun ResultPanel(store: CricketStore, match: Match) {
    val derived = MatchEngine.derive(match)
    var awarding by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(derived.result?.summary ?: "Match over", style = MaterialTheme.typography.headlineSmall, color = Lawn)
        derived.playerOfTheMatchId?.let { Text("Player of the match: ${match.playerName(it)}") }
        PrimaryButton("Scorecard", { store.push(Screen.Scorecard(match.id)) }, Modifier.fillMaxWidth())
        SecondaryButton("Choose player of the match", { awarding = true }, Modifier.fillMaxWidth())
    }
    if (awarding) {
        AlertDialog(
            onDismissRequest = { awarding = false },
            title = { Text("Player of the match") },
            text = {
                Column {
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
    var type by remember(types) { mutableStateOf(types.firstOrNull() ?: DismissalType.BOWLED) }
    val crease = listOfNotNull(innings.strikerId, innings.nonStrikerId)
    var batter by remember { mutableStateOf(innings.strikerId ?: crease.first()) }
    var fielder by remember { mutableStateOf(if (innings.bowlingTeamId == match.teamA.teamId) match.teamA.players.first().id else match.teamB.players.first().id) }
    var runs by remember { mutableStateOf(0) }
    if (type !in types && types.isNotEmpty()) type = types.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wicket") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { option ->
                    TextButton(onClick = { type = option }) { Text(if (option == type) "● ${option.displayName()}" else option.displayName()) }
                }
                if (!freeHit) {
                    ToggleRow("Wide", wide) { wide = it; if (it) noBall = false }
                    ToggleRow("No-ball", noBall) { noBall = it; if (it) wide = false }
                }
                if (type !in match.rules.dismissals.strikerOnly) {
                    Text("Batter", color = Muted)
                    crease.forEach { id -> TextButton(onClick = { batter = id }) { Text(if (batter == id) "● ${match.playerName(id)}" else match.playerName(id)) } }
                }
                if (type in match.rules.dismissals.fielderRequired) {
                    Text("Fielder", color = Muted)
                    val fielders = if (innings.bowlingTeamId == match.teamA.teamId) match.teamA.players else match.teamB.players
                    fielders.forEach { player -> TextButton(onClick = { fielder = player.id }) { Text(if (fielder == player.id) "● ${player.name}" else player.name) } }
                }
                if (type == DismissalType.RUN_OUT || type == DismissalType.DIRECT_HIT) {
                    Text("Runs completed: $runs", color = Muted)
                    Row {
                        listOf(0, 1, 2, 3).forEach { value -> TextButton(onClick = { runs = value }) { Text("$value") } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val strikerOnly = type in match.rules.dismissals.strikerOnly
                onConfirm(
                    BallInput(
                        batterRuns = if (type == DismissalType.RUN_OUT || type == DismissalType.DIRECT_HIT) runs else 0,
                        wide = wide,
                        noBall = noBall,
                        deadBall = type == DismissalType.TIMED_OUT,
                        dismissal = DismissalInput(type, if (strikerOnly) innings.strikerId ?: batter else batter, if (type in match.rules.dismissals.fielderRequired) fielder else null),
                        underarmConfirmed = true,
                    ),
                )
            }) { Text("Save wicket") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun MoreDialog(
    store: CricketStore,
    match: Match,
    onWicket: () -> Unit,
    onWide: () -> Unit,
    onNoBall: () -> Unit,
    onBye: () -> Unit,
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
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryButton("Wicket", onWicket, Modifier.fillMaxWidth())
                if (rules.boxOut.enabled) SecondaryButton("Box Out", onBoxOut, Modifier.fillMaxWidth())
                if (rules.extras.widesEnabled) SecondaryButton("Wide", onWide, Modifier.fillMaxWidth())
                if (rules.extras.noBallsEnabled) SecondaryButton("No-ball", onNoBall, Modifier.fillMaxWidth())
                if (rules.extras.byesEnabled) SecondaryButton("Bye", onBye, Modifier.fillMaxWidth())
                if (rules.extras.legByesEnabled) SecondaryButton("Leg-bye", onLegBye, Modifier.fillMaxWidth())
                SecondaryButton("5 runs", onFive, Modifier.fillMaxWidth())
                rules.additionalScoring.events.filter { it.enabled }.forEach { event ->
                    SecondaryButton("${event.name} · ${event.runs}", { onBonus(event.id) }, Modifier.fillMaxWidth())
                }
                surfaceKeys(rules).forEach { (label, surface) ->
                    SecondaryButton(label, { onSurface(surface) }, Modifier.fillMaxWidth())
                }
                Text("Penalty runs: $penalty", color = Muted)
                Row { (1..5).forEach { TextButton(onClick = { penalty = it }) { Text("$it") } } }
                SecondaryButton("Add penalty", {
                    store.score(match.id, BallInput(deadBall = true, penaltyRuns = penalty))
                    onDismiss()
                }, Modifier.fillMaxWidth())
                Text("Overthrow off the bat. Completed $completed, extra $extra", color = Muted)
                Row { (0..3).forEach { TextButton(onClick = { completed = it }) { Text("Hit $it") } } }
                Row { (1..6).forEach { TextButton(onClick = { extra = it }) { Text("+$it") } } }
                SecondaryButton("Add overthrow", {
                    store.score(match.id, BallInput(batterRuns = completed, overthrowBatterRuns = extra, underarmConfirmed = true))
                    onDismiss()
                }, Modifier.fillMaxWidth())
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
private fun InningsTables(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    Text("${match.teamName(innings.battingTeamId)}  ${innings.scoreLine()}", style = MaterialTheme.typography.titleLarge)
    ScoreLine("Batter    Dismissal    R   B   4s  6s  SR", header = true)
    innings.battingRows.forEach { row ->
        ScoreLine("${row.name}    ${row.dismissalText}    ${row.runs}   ${row.balls}   ${row.fours}  ${row.sixes}  ${row.strikeRate.twoDecimals()}")
    }
    if (innings.yetToBat.isNotEmpty()) Text("Yet to bat: ${innings.yetToBat.joinToString { match.playerName(it) }}", color = Muted)
    val extras = innings.extras
    Text("Extras ${extras.total}  wd ${extras.wides}  nb ${extras.noBalls}  b ${extras.byes}  lb ${extras.legByes}  p ${extras.penalties}")
    if (innings.fallOfWickets.isNotEmpty()) {
        Text("Fall of wickets", fontWeight = FontWeight.SemiBold)
        Text(innings.fallOfWickets.joinToString("   ") { "${it.wicketNumber}-${it.teamScore} ${it.batterName}" })
    }
    innings.partnerships.lastOrNull { it.endedAtEpochMs == null || it == innings.partnerships.last() }?.let { partnership ->
        Text("Partnership ${partnership.batterNames.joinToString(" & ")}  ${partnership.runs} (${partnership.balls})", color = Muted)
    }
    ScoreLine("Bowler    O    M    R    W" + (if (match.rules.extras.widesEnabled) "    WD" else "") + (if (match.rules.extras.noBallsEnabled) "    NB" else "") + "    Econ", header = true)
    innings.bowlingRows.forEach { row ->
        val wide = if (match.rules.extras.widesEnabled) "    ${row.wides}" else ""
        val noBall = if (match.rules.extras.noBallsEnabled) "    ${row.noBalls}" else ""
        ScoreLine("${row.name}    ${oversLabel(row.legalBalls, innings.ballsPerOver)}    ${row.maidens}    ${row.runs}    ${row.wickets}$wide$noBall    ${row.economy.twoDecimals()}")
    }
}

@Composable
private fun ScoreLine(text: String, header: Boolean = false) {
    Text(
        text,
        color = if (header) Muted else Ink,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        softWrap = false,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun OversPanel(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    var open by remember { mutableStateOf(setOf<Int>()) }
    if (innings.overs.isEmpty()) Text("No overs yet.", color = Muted)
    innings.overs.forEach { over ->
        val number = over.overIndex + 1
        val expanded = over.overIndex in open
        SecondaryButton("Over $number  ${over.bowlerName}  ${over.symbols.joinToString(", ")}  ${over.runs} runs  ${over.wickets} wkts  ${over.scoreAfter}/${over.wicketsAfter}", {
            open = if (expanded) open - over.overIndex else open + over.overIndex
        })
        if (expanded) {
            over.deliveries.forEach { ball ->
                Text("${ball.symbol}  ${match.playerName(ball.batterId)}  ${ball.commentary ?: if (ball.legal) "legal" else "extra"}", color = Muted)
            }
        }
    }
}

@Composable
private fun CommentaryPanel(match: Match, innings: com.example.cricketscoring.model.DerivedInnings) {
    if (innings.deliveries.isEmpty()) Text("No balls yet.", color = Muted)
    innings.deliveries.asReversed().forEach { ball ->
        val note = ball.commentary ?: ball.scoringEventName
        Text("${ball.symbol}  ${match.playerName(ball.batterId)} to ${match.playerName(ball.bowlerId)}" + (note?.let { "  $it" } ?: ""))
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
                if (name.isBlank()) store.message = "Name the player being added."
                else store.addMatchPlayer(match.id, team.teamId, MatchPlayer(freshId("player"), name.trim()), reason, approvalA, approvalB)
            })
        }
    }
    if (roster.inMatchSwitchingAllowed || roster.allowInjuredReplacement) {
        derived.liveTeamA.forEach { player ->
            SecondaryButton("Replace ${player.name}", {
                if (name.isBlank()) store.message = "Name the incoming player."
                else store.switchPlayer(match.id, match.teamA.teamId, player.id, MatchPlayer(freshId("player"), name.trim()), reason, injured = roster.allowInjuredReplacement && !roster.inMatchSwitchingAllowed, approvalA, approvalB)
            })
        }
        derived.liveTeamB.forEach { player ->
            SecondaryButton("Replace ${player.name}", {
                if (name.isBlank()) store.message = "Name the incoming player."
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
