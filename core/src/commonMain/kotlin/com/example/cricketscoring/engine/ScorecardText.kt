package com.example.cricketscoring.engine

import com.example.cricketscoring.model.Match
import com.example.cricketscoring.rules.styleTitle

fun scorecardText(match: Match): String {
    val derived = MatchEngine.derive(match)
    return buildString {
        appendLine(match.title.ifBlank { "${match.teamA.name} v ${match.teamB.name}" })
        appendLine(styleTitle(match.style))
        if (match.venue.isNotBlank()) appendLine(match.venue)
        appendLine(match.rules.name)
        derived.innings.forEach { innings ->
            val batting = match.teamName(innings.battingTeamId)
            appendLine()
            append(batting)
            if (innings.superOver) append(" — Super Over")
            appendLine("  ${innings.scoreLine()}")
            if (innings.target != null) appendLine("Target ${innings.target}")
            innings.battingRows.forEach { row ->
                appendLine("${row.name}  ${row.dismissalText}  ${row.runs} (${row.balls})  4s ${row.fours}  6s ${row.sixes}")
            }
            if (innings.yetToBat.isNotEmpty()) {
                appendLine("Yet to bat: ${innings.yetToBat.joinToString { match.playerName(it) }}")
            }
            val extras = innings.extras
            appendLine("Extras ${extras.total} (wd ${extras.wides}, nb ${extras.noBalls}, b ${extras.byes}, lb ${extras.legByes}, p ${extras.penalties})")
            appendLine("Bowling")
            innings.bowlingRows.forEach { row ->
                appendLine("${row.name}  ${oversLabel(row.legalBalls, innings.ballsPerOver)}-${row.maidens}-${row.runs}-${row.wickets}")
            }
            if (innings.fallOfWickets.isNotEmpty()) {
                appendLine("Fall of wickets: ${innings.fallOfWickets.joinToString { "${it.wicketNumber}-${it.teamScore} (${it.batterName}, ${it.overLabel})" }}")
            }
        }
        appendLine()
        appendLine(derived.result?.summary ?: derived.equation ?: "Live")
        derived.playerOfTheMatchId?.let { appendLine("Player of the match: ${match.playerName(it)}") }
    }.trim()
}
