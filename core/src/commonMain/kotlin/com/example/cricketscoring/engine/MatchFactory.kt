package com.example.cricketscoring.engine

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.MatchTeam
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.Toss
import com.example.cricketscoring.rules.withLocalDismissals

object MatchFactory {
    fun create(
        id: String,
        style: CricketStyle,
        title: String,
        teamA: MatchTeam,
        teamB: MatchTeam,
        rules: RulesProfile,
        venue: String = "",
        tournamentId: String? = null,
        fixtureId: String? = null,
        scheduledAtEpochMs: Long? = null,
        createdAtEpochMs: Long = 0,
        toss: Toss? = null,
        battingFirstTeamId: String? = null,
        opening: OpeningPlayers? = null,
    ): Match {
        if (teamA.teamId == teamB.teamId) throw ScoringException("Choose two different teams.")
        if (teamA.players.size < 1 || teamB.players.size < 1) throw ScoringException("Each team needs at least one player.")
        if (teamA.players.map { it.id }.distinct().size != teamA.players.size ||
            teamB.players.map { it.id }.distinct().size != teamB.players.size
        ) {
            throw ScoringException("A player cannot be selected twice in the same team.")
        }
        val shared = teamA.players.map { it.id }.intersect(teamB.players.map { it.id }.toSet())
        if (shared.isNotEmpty()) throw ScoringException("A player cannot appear for both teams.")
        val snapshot = withLocalDismissals(rules)
        if (teamA.players.size != teamB.players.size && !snapshot.roster.allowUnevenTeamSizes) {
            throw ScoringException("Both teams need the same number of players, or turn on uneven team sizes before the match.")
        }
        if (snapshot.squad.twoBatters && teamA.players.size < 2) {
            throw ScoringException("This match needs two batters, so each squad needs at least two players.")
        }
        if (snapshot.squad.wicketkeeperRequired && (teamA.wicketkeeperId == null || teamB.wicketkeeperId == null)) {
            throw ScoringException("Choose a wicketkeeper for each team.")
        }
        if (snapshot.squad.captainRequired && (teamA.captainId == null || teamB.captainId == null)) {
            throw ScoringException("Choose a captain for each team.")
        }
        if (opening != null && battingFirstTeamId == null) {
            throw ScoringException("Choose the batting side before the opening batters.")
        }
        return Match(
            id = id,
            style = style,
            title = title.ifBlank { "${teamA.name} v ${teamB.name}" },
            teamA = teamA,
            teamB = teamB,
            rules = snapshot,
            tournamentId = tournamentId,
            fixtureId = fixtureId,
            venue = venue,
            scheduledAtEpochMs = scheduledAtEpochMs,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = createdAtEpochMs,
            toss = toss,
            battingFirstTeamId = battingFirstTeamId,
            opening = opening,
        )
    }
}

fun teamOf(id: String, name: String, players: List<String>, keeper: String? = null, captain: String? = null) = MatchTeam(
    teamId = id,
    name = name,
    shortName = name.take(3).uppercase(),
    color = 0xFF1B8A4A.toInt(),
    players = players.map { MatchPlayer(it, it) },
    captainId = captain ?: players.firstOrNull(),
    wicketkeeperId = keeper ?: players.firstOrNull(),
)
