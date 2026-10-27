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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.oversLabel
import com.example.cricketscoring.engine.playerName
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.scorecardText
import com.example.cricketscoring.engine.teamName
import com.example.cricketscoring.engine.twoDecimals

@Composable
fun ScorecardScreen(store: CricketStore, matchId: String) {
    val match = store.data.matches.find { it.id == matchId }
    if (match == null) {
        Column(Modifier.padding(16.dp)) {
            Text("This scorecard is not on the device.")
            SecondaryButton("Back", { store.pop() })
        }
        return
    }
    val derived = remember(match) { MatchEngine.derive(match) }
    var index by remember(match.id) { mutableIntStateOf(derived.innings.lastIndex.coerceAtLeast(0)) }
    val innings = derived.innings.getOrNull(index)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(match.title, onBack = { store.pop() }, action = "Share", onAction = { store.shareText = scorecardText(match) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(match.rules.name, color = Muted)
            derived.result?.let { Text(it.summary, style = MaterialTheme.typography.titleLarge, color = Lawn) }
            derived.equation?.let { Text(it, color = Gold) }
            if (derived.innings.size > 1) {
                ChoiceRow(derived.innings.map { inningsItem ->
                    val label = match.teamName(inningsItem.battingTeamId).take(8) + if (inningsItem.superOver) " SO" else ""
                    label to (derived.innings.indexOf(inningsItem) == index)
                }) { chosen -> index = chosen }
            }
            if (innings == null) {
                Text("No balls yet.", color = Muted)
            } else {
                Text("${match.teamName(innings.battingTeamId)}  ${innings.scoreLine()}", style = MaterialTheme.typography.headlineSmall)
                innings.target?.let { Text("Target $it", color = Gold) }
                SectionLabel("Batting")
                innings.battingRows.forEach { row ->
                    Text("${row.name}  ${row.dismissalText}", fontWeight = FontWeight.SemiBold)
                    Text("${row.runs} (${row.balls})   4s ${row.fours}   6s ${row.sixes}   SR ${row.strikeRate.twoDecimals()}", color = Muted)
                }
                if (innings.yetToBat.isNotEmpty()) Text("Yet to bat: ${innings.yetToBat.joinToString { match.playerName(it) }}", color = Muted)
                val extras = innings.extras
                Text("Extras ${extras.total}  (wd ${extras.wides}, nb ${extras.noBalls}, b ${extras.byes}, lb ${extras.legByes}, p ${extras.penalties})")
                innings.powerplayRuns?.let { Text("Powerplay $it", color = Muted) }
                SectionLabel("Bowling")
                innings.bowlingRows.forEach { row ->
                    Text("${row.name}  ${oversLabel(row.legalBalls, innings.ballsPerOver)}-${row.maidens}-${row.runs}-${row.wickets}")
                    Text("Wd ${row.wides}  Nb ${row.noBalls}  Econ ${row.economy.twoDecimals()}", color = Muted)
                }
                if (innings.fallOfWickets.isNotEmpty()) {
                    SectionLabel("Fall of wickets")
                    Text(innings.fallOfWickets.joinToString("  ") { "${it.wicketNumber}-${it.teamScore} ${it.batterName}" })
                }
                if (innings.partnerships.isNotEmpty()) {
                    SectionLabel("Partnerships")
                    innings.partnerships.forEach { partnership ->
                        Text("${partnership.batterNames.joinToString(" & ")}  ${partnership.runs} (${partnership.balls})")
                    }
                }
            }
            derived.playerOfTheMatchId?.let { Text("Player of the match: ${match.playerName(it)}") }
            SecondaryButton("Resume scoring", { store.push(Screen.Live(match.id)) })
        }
    }
}
