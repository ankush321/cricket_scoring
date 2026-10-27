package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.scoreLine
import com.example.cricketscoring.engine.teamShort
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle

@Composable
fun HomeScreen(store: CricketStore) {
    val matches = store.data.matches
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cricket Scoring", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a style, then start or resume a match.", color = Muted)
        CricketStyle.entries.forEach { style ->
            val count = matches.count { it.style == style }
            PitchCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyleMark(style)
                    Column(Modifier.weight(1f)) {
                        Text(styleTitle(style), style = MaterialTheme.typography.titleLarge)
                        Text(styleSummary(style), color = Muted)
                        Text(if (count == 0) "No matches yet" else "$count matches", color = style.accent(), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("View Matches", { store.openStyle(style, fromHome = true) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MatchSnippet(match: com.example.cricketscoring.model.Match, onClick: () -> Unit) {
    val derived = MatchEngine.derive(match)
    PitchCard(onClick = onClick) {
        Text(styleTitle(match.style), color = match.style.accent(), style = MaterialTheme.typography.bodySmall)
        Text(match.title, style = MaterialTheme.typography.titleMedium)
        Text(
            derived.result?.summary
                ?: derived.equation
                ?: derived.innings.lastOrNull()?.let { "${match.teamShort(it.battingTeamId)} ${it.scoreLine()}" }
                ?: "Upcoming",
            color = Muted,
        )
    }
}
