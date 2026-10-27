package com.example.cricketscoring.engine

import com.example.cricketscoring.model.AppData
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.model.ResultKind
import com.example.cricketscoring.model.Tournament

data class PlayerStyleStats(
    val style: CricketStyle,
    val matches: Int,
    val innings: Int,
    val notOuts: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val highest: Int,
    val wickets: Int,
    val bowlingRuns: Int,
    val bowlingBalls: Int,
    val catches: Int,
    val awards: Int,
)

data class TeamStyleRecord(
    val style: CricketStyle,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val noResult: Int,
)

data class PointsRow(
    val teamId: String,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
    val noResult: Int,
    val points: Int,
    val runsScored: Int,
    val oversFaced: Double,
    val runsConceded: Int,
    val oversBowled: Double,
    val netRunRate: Double,
)

fun playerStats(data: AppData, playerId: String): List<PlayerStyleStats> {
    return CricketStyle.entries.mapNotNull { style ->
        val matches = data.matches.filter { it.style == style && it.includes(playerId) }
        if (matches.isEmpty()) return@mapNotNull null
        var innings = 0
        var notOuts = 0
        var runs = 0
        var balls = 0
        var fours = 0
        var sixes = 0
        var highest = 0
        var wickets = 0
        var bowlingRuns = 0
        var bowlingBalls = 0
        var catches = 0
        var awards = 0
        matches.forEach { match ->
            val derived = MatchEngine.derive(match)
            if (derived.playerOfTheMatchId == playerId) awards += 1
            derived.innings.forEach { inn ->
                inn.battingRows.find { it.playerId == playerId }?.let { row ->
                    innings += 1
                    if (!row.isOut) notOuts += 1
                    runs += row.runs
                    balls += row.balls
                    fours += row.fours
                    sixes += row.sixes
                    if (row.runs > highest) highest = row.runs
                }
                inn.bowlingRows.find { it.playerId == playerId }?.let { row ->
                    wickets += row.wickets
                    bowlingRuns += row.runs
                    bowlingBalls += row.legalBalls
                }
                catches += inn.deliveries.count {
                    val dismissal = it.dismissal
                    dismissal?.fielderId == playerId && (
                        dismissal.dismissalType == DismissalType.CAUGHT ||
                            dismissal.dismissalType == DismissalType.ONE_TIP_ONE_HAND ||
                            dismissal.dismissalType == DismissalType.SINGLE_BOUNCE
                        )
                }
            }
        }
        PlayerStyleStats(style, matches.size, innings, notOuts, runs, balls, fours, sixes, highest, wickets, bowlingRuns, bowlingBalls, catches, awards)
    }
}

fun teamRecord(data: AppData, teamId: String): List<TeamStyleRecord> {
    return CricketStyle.entries.mapNotNull { style ->
        val matches = data.matches.filter { it.style == style && (it.teamA.teamId == teamId || it.teamB.teamId == teamId) }
        val finished = matches.map { it to MatchEngine.derive(it) }.filter { (_, derived) ->
            derived.status == MatchStatus.COMPLETED || derived.status == MatchStatus.ABANDONED
        }
        if (finished.isEmpty()) return@mapNotNull null
        var won = 0
        var lost = 0
        var tied = 0
        var noResult = 0
        finished.forEach { (_, derived) ->
            when (derived.result?.kind) {
                ResultKind.WIN -> if (derived.result.winnerTeamId == teamId) won += 1 else lost += 1
                ResultKind.TIE -> tied += 1
                ResultKind.NO_RESULT, ResultKind.ABANDONED -> noResult += 1
                null -> Unit
            }
        }
        TeamStyleRecord(style, finished.size, won, lost, tied, noResult)
    }
}

fun headToHead(data: AppData, teamA: String, teamB: String, style: CricketStyle): TeamStyleRecord {
    val filtered = data.copy(
        matches = data.matches.filter {
            it.style == style &&
                ((it.teamA.teamId == teamA && it.teamB.teamId == teamB) || (it.teamA.teamId == teamB && it.teamB.teamId == teamA))
        },
    )
    return teamRecord(filtered, teamA).find { it.style == style }
        ?: TeamStyleRecord(style, 0, 0, 0, 0, 0)
}

fun pointsTable(tournament: Tournament, matches: List<Match>): List<PointsRow> {
    val rows = tournament.teamIds.associateWith {
        PointsRow(it, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, 0.0, 0.0)
    }.toMutableMap()
    matches.filter { it.tournamentId == tournament.id && it.style == tournament.style }.forEach { match ->
        val derived = MatchEngine.derive(match)
        val result = derived.result ?: return@forEach
        if (derived.status != MatchStatus.COMPLETED && derived.status != MatchStatus.ABANDONED) return@forEach
        listOf(match.teamA.teamId, match.teamB.teamId).forEach { teamId ->
            val current = rows[teamId] ?: return@forEach
            val won = result.kind == ResultKind.WIN && result.winnerTeamId == teamId
            val lost = result.kind == ResultKind.WIN && result.winnerTeamId != teamId
            val tied = result.kind == ResultKind.TIE
            val noResult = result.kind == ResultKind.NO_RESULT || result.kind == ResultKind.ABANDONED
            val points = when {
                won -> tournament.pointsForWin
                tied -> tournament.pointsForTie
                noResult -> tournament.pointsForNoResult
                else -> 0
            }
            rows[teamId] = current.copy(
                played = current.played + 1,
                won = current.won + if (won) 1 else 0,
                lost = current.lost + if (lost) 1 else 0,
                tied = current.tied + if (tied) 1 else 0,
                noResult = current.noResult + if (noResult) 1 else 0,
                points = current.points + points,
            )
        }
        derived.innings.filter { !it.superOver }.forEach { innings ->
            val ballsPerOver = innings.ballsPerOver.coerceAtLeast(1)
            val faced = nrrOvers(innings.legalBalls, innings.maxLegalBalls, innings.allOut, ballsPerOver)
            val batting = rows[innings.battingTeamId]
            val bowling = rows[innings.bowlingTeamId]
            if (batting != null) {
                rows[innings.battingTeamId] = batting.copy(
                    runsScored = batting.runsScored + innings.runs,
                    oversFaced = batting.oversFaced + faced,
                )
            }
            if (bowling != null) {
                rows[innings.bowlingTeamId] = bowling.copy(
                    runsConceded = bowling.runsConceded + innings.runs,
                    oversBowled = bowling.oversBowled + faced,
                )
            }
        }
    }
    return rows.values.map { row ->
        val forRate = if (row.oversFaced == 0.0) 0.0 else row.runsScored / row.oversFaced
        val against = if (row.oversBowled == 0.0) 0.0 else row.runsConceded / row.oversBowled
        row.copy(netRunRate = forRate - against)
    }.sortedWith(compareByDescending<PointsRow> { it.points }.thenByDescending { it.netRunRate })
}

private fun nrrOvers(legalBalls: Int, maxLegalBalls: Int?, allOut: Boolean, ballsPerOver: Int): Double {
    val balls = if (allOut && maxLegalBalls != null) maxLegalBalls else legalBalls
    return balls.toDouble() / ballsPerOver
}

private fun Match.includes(playerId: String) = teamA.players.any { it.id == playerId } || teamB.players.any { it.id == playerId }
