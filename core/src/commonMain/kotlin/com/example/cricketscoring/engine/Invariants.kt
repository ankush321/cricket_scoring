package com.example.cricketscoring.engine

import com.example.cricketscoring.model.DerivedMatch
import com.example.cricketscoring.model.Match

object MatchInvariants {
    fun problems(match: Match, derived: DerivedMatch = MatchEngine.derive(match)): List<String> {
        val problems = mutableListOf<String>()
        derived.innings.forEach { innings ->
            val fromBalls = innings.deliveries.sumOf { it.totalRuns }
            if (fromBalls != innings.runs) {
                problems += "Innings ${innings.index + 1} ball totals are ${fromBalls}, score is ${innings.runs}."
            }
            val batterRuns = innings.battingRows.sumOf { it.runs }
            if (batterRuns + innings.extras.total != innings.runs) {
                problems += "Innings ${innings.index + 1} batter runs plus extras do not equal the total."
            }
            val legal = innings.deliveries.count { it.legal }
            if (legal != innings.legalBalls) {
                problems += "Innings ${innings.index + 1} legal balls do not match the over count."
            }
            val credited = innings.deliveries.count { it.dismissal?.bowlerCredited == true }
            val retiredOut = innings.fallOfWickets.count { it.dismissalType.name == "RETIRED_OUT" }
            val bowlerWickets = innings.bowlingRows.sumOf { it.wickets }
            if (bowlerWickets != credited) {
                problems += "Innings ${innings.index + 1} bowling wickets do not match credited dismissals."
            }
            if (innings.fallOfWickets.size != innings.wickets) {
                problems += "Innings ${innings.index + 1} fall of wickets does not match the wicket count."
            }
            val bowlingRuns = innings.bowlingRows.sumOf { it.runs }
            val expectedBowling = batterRuns + innings.extras.wides + innings.extras.noBalls
            if (bowlingRuns != expectedBowling) {
                problems += "Innings ${innings.index + 1} bowling runs do not match bat runs, wides, and no-balls."
            }
            innings.deliveries.forEach { ball ->
                if (ball.batterRuns + ball.extras.total != ball.totalRuns) {
                    problems += "A delivery in innings ${innings.index + 1} does not add up."
                }
            }
            if (innings.wickets > innings.maxWickets) {
                problems += "Innings ${innings.index + 1} has more wickets than the playing conditions allow."
            }
            val outIds = innings.battingRows.filter { it.isOut }.map { it.playerId }.toSet()
            if (innings.strikerId in outIds || innings.nonStrikerId in outIds) {
                problems += "A dismissed batter is still at the crease."
            }
            if (retiredOut > innings.wickets) {
                problems += "Retired-out count is inconsistent."
            }
        }
        return problems
    }
}
