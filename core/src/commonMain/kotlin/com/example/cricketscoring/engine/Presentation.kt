package com.example.cricketscoring.engine

import com.example.cricketscoring.model.DerivedInnings
import com.example.cricketscoring.model.DerivedMatch
import com.example.cricketscoring.model.Match
import kotlin.math.abs
import kotlin.math.round

fun Match.playerName(id: String): String = teamA.players.find { it.id == id }?.name
    ?: teamB.players.find { it.id == id }?.name
    ?: id

fun Match.teamName(id: String): String = when (id) {
    teamA.teamId -> teamA.name
    teamB.teamId -> teamB.name
    else -> id
}

fun Match.teamShort(id: String): String = when (id) {
    teamA.teamId -> teamA.shortName
    teamB.teamId -> teamB.shortName
    else -> id
}

fun DerivedMatch.currentInnings(): DerivedInnings? = currentInningsIndex?.let { innings.getOrNull(it) }

fun DerivedInnings.thisOver() = deliveries.lastOrNull()?.let { last ->
    deliveries.filter { it.overIndex == last.overIndex }
}.orEmpty()

fun DerivedInnings.oversLabel(): String = oversLabel(legalBalls, ballsPerOver)

fun DerivedInnings.scoreLine(): String = "$runs/$wickets (${oversLabel()})"

fun oversLabel(legalBalls: Int, ballsPerOver: Int): String {
    if (ballsPerOver <= 0) return "0"
    val overs = legalBalls / ballsPerOver
    val balls = legalBalls % ballsPerOver
    return if (balls == 0) "$overs" else "$overs.$balls"
}

fun Double.twoDecimals(): String {
    val negative = this < 0
    val scaled = round(abs(this) * 100).toInt()
    val whole = scaled / 100
    val fraction = (scaled % 100).toString().padStart(2, '0')
    val text = "$whole.$fraction"
    return if (negative) "-$text" else text
}
