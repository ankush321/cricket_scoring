package com.example.cricketscoring.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cricketscoring.engine.MatchEngine
import com.example.cricketscoring.engine.MatchFactory
import com.example.cricketscoring.engine.ScoringException
import com.example.cricketscoring.model.AppData
import com.example.cricketscoring.model.BallInput
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.EngineCode
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchEvent
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.model.MatchTeam
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.Player
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.Team
import com.example.cricketscoring.model.Toss
import com.example.cricketscoring.model.TossChoice
import com.example.cricketscoring.model.Tournament
import com.example.cricketscoring.persist.AppRepository
import com.example.cricketscoring.rules.defaultRules
import kotlin.random.Random
import kotlin.time.Clock
import com.example.cricketscoring.model.Fixture as FixtureModel

private var idSequence = 0

fun freshId(prefix: String): String {
    idSequence += 1
    return "$prefix-${Random.nextInt(1, 0xFFFFFF).toString(16)}-$idSequence"
}

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

data class MatchDraft(
    val style: CricketStyle,
    val step: Int = 1,
    val existingMatchId: String? = null,
    val teamAId: String? = null,
    val teamBId: String? = null,
    val squadA: List<String> = emptyList(),
    val squadB: List<String> = emptyList(),
    val captainA: String? = null,
    val captainB: String? = null,
    val keeperA: String? = null,
    val keeperB: String? = null,
    val rules: RulesProfile = defaultRules(style),
    val tournamentId: String? = null,
    val fixtureId: String? = null,
    val venue: String = "",
    val title: String = "",
    val skipToss: Boolean = false,
    val tossWinnerId: String? = null,
    val tossChoice: TossChoice? = null,
    val manualBattingId: String? = null,
    val strikerId: String? = null,
    val nonStrikerId: String? = null,
    val bowlerId: String? = null,
    val guidance: String? = null,
    val focus: String? = null,
)

data class WizardIssue(
    val step: Int,
    val message: String,
    val focus: String,
)

class CricketStore(private val repository: AppRepository) {
    var data by mutableStateOf(repository.load())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageType by mutableStateOf(AppToastType.Info)
        private set
    var draft by mutableStateOf<MatchDraft?>(null)
    var shareText by mutableStateOf<String?>(null)

    fun showToast(message: String, type: AppToastType = AppToastType.Info) {
        messageType = type
        this.message = message
    }

    fun dismissMessage() {
        message = null
    }

    fun openWizard(style: CricketStyle, match: Match? = null) {
        draft = if (match == null) {
            MatchDraft(style = style, rules = defaultRules(style).copy(id = freshId("rules")))
        } else {
            draftFrom(match)
        }
    }

    fun duplicate(match: Match) {
        draft = draftFrom(match).copy(existingMatchId = null, step = 1, strikerId = null, nonStrikerId = null, bowlerId = null)
    }

    fun createTeam(name: String, playerLines: String, color: Int): Team? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showToast("Give the team a name.", AppToastType.Warning)
            return null
        }
        val names = playerLines.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) {
            showToast("Add at least one player.", AppToastType.Warning)
            return null
        }
        val players = names.map { playerName -> Player(freshId("player"), playerName) }
        val team = Team(
            id = freshId("team"),
            name = trimmed,
            shortName = shortName(trimmed),
            color = color,
            playerIds = players.map { it.id },
            captainId = players.first().id,
            wicketkeeperId = players.first().id,
        )
        update { it.copy(players = it.players + players, teams = it.teams + team) }
        return team
    }

    fun saveTeam(team: Team) {
        update { current ->
            val teams = if (current.teams.any { it.id == team.id }) {
                current.teams.map { if (it.id == team.id) team else it }
            } else {
                current.teams + team
            }
            current.copy(teams = teams)
        }
    }

    fun addPlayerToTeam(teamId: String, playerName: String) {
        val trimmed = playerName.trim()
        if (trimmed.isEmpty()) return
        val player = Player(freshId("player"), trimmed)
        update { current ->
            current.copy(
                players = current.players + player,
                teams = current.teams.map { team ->
                    if (team.id == teamId) team.copy(playerIds = team.playerIds + player.id) else team
                },
            )
        }
    }

    fun archiveTeam(teamId: String, archived: Boolean) {
        update { current ->
            current.copy(teams = current.teams.map { if (it.id == teamId) it.copy(archived = archived) else it })
        }
    }

    fun savePlayer(player: Player) {
        if (player.name.isBlank()) {
            showToast("A player needs a name.", AppToastType.Warning)
            return
        }
        update { current ->
            val players = if (current.players.any { it.id == player.id }) {
                current.players.map { if (it.id == player.id) player else it }
            } else current.players + player
            current.copy(players = players)
        }
    }

    fun deletePlayer(playerId: String) {
        val used = data.teams.any { playerId in it.playerIds } || data.matches.any { match ->
            match.teamA.players.any { it.id == playerId } || match.teamB.players.any { it.id == playerId }
        }
        if (used) {
            showToast("This player is still on a team or in a match.", AppToastType.Error)
            return
        }
        update { it.copy(players = it.players.filterNot { player -> player.id == playerId }) }
    }

    fun commitDraft(start: Boolean): Match? {
        val currentDraft = draft ?: return null
        val issue = wizardIssue(this, currentDraft, requireStartFields = start)
        if (issue != null) {
            draft = currentDraft.copy(step = issue.step, guidance = issue.message, focus = issue.focus)
            showToast(issue.message, AppToastType.Warning)
            return null
        }
        try {
            val teamA = data.teams.find { it.id == currentDraft.teamAId } ?: error("Choose the first team.")
            val teamB = data.teams.find { it.id == currentDraft.teamBId } ?: error("Choose the second team.")
            val size = currentDraft.rules.squad.playersPerTeam
            val squadA = currentDraft.squadA.ifEmpty { defaultSquad(teamA, size) }
            val squadB = currentDraft.squadB.ifEmpty { defaultSquad(teamB, size) }
            val battingFirst = battingFirstId(currentDraft)
            val opening = if (!start) {
                null
            } else {
                OpeningPlayers(
                    currentDraft.strikerId!!,
                    if (currentDraft.rules.squad.twoBatters) currentDraft.nonStrikerId else null,
                    currentDraft.bowlerId!!,
                )
            }
            val captainA = currentDraft.captainA ?: squadA.first()
            val captainB = currentDraft.captainB ?: squadB.first()
            val keeperA = currentDraft.keeperA ?: squadA.first()
            val keeperB = currentDraft.keeperB ?: squadB.first()
            val toss = Toss(
                winnerTeamId = if (currentDraft.skipToss) null else currentDraft.tossWinnerId,
                choice = if (currentDraft.skipToss) null else currentDraft.tossChoice,
                skipped = currentDraft.skipToss,
            )
            val id = currentDraft.existingMatchId ?: freshId("match")
            val previous = data.matches.find { it.id == id }
            if (previous != null && previous.events.isNotEmpty()) error("This match has already started.")
            val created = MatchFactory.create(
                id = id,
                style = currentDraft.style,
                title = currentDraft.title.ifBlank { "${teamA.name} v ${teamB.name}" },
                teamA = matchTeam(teamA, squadA, captainA, keeperA),
                teamB = matchTeam(teamB, squadB, captainB, keeperB),
                rules = currentDraft.rules.copy(id = currentDraft.rules.id.ifBlank { freshId("rules") }),
                venue = currentDraft.venue,
                tournamentId = currentDraft.tournamentId,
                fixtureId = currentDraft.fixtureId,
                createdAtEpochMs = previous?.createdAtEpochMs ?: nowMillis(),
                toss = toss,
                battingFirstTeamId = if (start) battingFirst else battingFirst,
                opening = opening,
            ).copy(updatedAtEpochMs = nowMillis())
            update { current ->
                val matches = if (current.matches.any { it.id == created.id }) {
                    current.matches.map { if (it.id == created.id) created else it }
                } else current.matches + created
                val tournaments = current.tournaments.map { tournament ->
                    if (tournament.id != created.tournamentId) tournament
                    else tournament.copy(
                        fixtures = tournament.fixtures.map { fixture ->
                            if (fixture.id == created.fixtureId) fixture.copy(matchId = created.id) else fixture
                        },
                    )
                }
                current.copy(matches = matches, tournaments = tournaments)
            }
            draft = null
            showToast(if (start) "Match started." else "Match saved.", AppToastType.Success)
            return created
        } catch (error: ScoringException) {
            showToast(error.message ?: "Could not start the match.", AppToastType.Error)
        } catch (error: IllegalStateException) {
            showToast(error.message ?: "Could not start the match.", AppToastType.Error)
        }
        return null
    }

    fun deleteMatch(matchId: String) {
        update { current ->
            current.copy(
                matches = current.matches.filterNot { it.id == matchId },
                tournaments = current.tournaments.map { tournament ->
                    tournament.copy(fixtures = tournament.fixtures.map { if (it.matchId == matchId) it.copy(matchId = null) else it })
                },
            )
        }
        showToast("Match deleted.", AppToastType.Success)
    }

    fun apply(matchId: String, block: (Match) -> com.example.cricketscoring.model.EngineOutcome) {
        val match = data.matches.find { it.id == matchId } ?: return
        val outcome = block(match)
        val updated = outcome.match
        if (outcome.code == EngineCode.OK && updated != null) {
            val saved = updated.copy(updatedAtEpochMs = nowMillis())
            update { current -> current.copy(matches = current.matches.map { if (it.id == matchId) saved else it }) }
        } else {
            showToast(outcome.message ?: "That action is not allowed.", AppToastType.Error)
        }
    }

    fun score(matchId: String, input: BallInput) {
        apply(matchId) { MatchEngine.append(it, MatchEvent.Ball(freshId("ball"), input.copy(timestampEpochMs = nowMillis()))) }
    }

    fun undo(matchId: String) = apply(matchId) { MatchEngine.undo(it) }

    fun replaceBall(matchId: String, eventId: String, input: BallInput) {
        apply(matchId) { match ->
            MatchEngine.replaceEvent(match, eventId, MatchEvent.Ball(eventId, input.copy(timestampEpochMs = nowMillis())))
        }
    }

    fun nextBatter(matchId: String, playerId: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.NextBatter(freshId("batter"), playerId)) }

    fun nextBowler(matchId: String, playerId: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.NextBowler(freshId("bowler"), playerId)) }

    fun setBreakMinutes(matchId: String, minutes: Int) {
        val bounded = minutes.coerceIn(0, 60)
        update { current ->
            current.copy(matches = current.matches.map { match ->
                if (match.id != matchId) match
                else match.copy(rules = match.rules.copy(endings = match.rules.endings.copy(inningsBreakMinutes = bounded)))
            })
        }
    }

    fun startNext(matchId: String, striker: String, nonStriker: String?, bowler: String, superOver: Boolean) {
        val event = if (superOver) {
            MatchEvent.StartSuperOver(freshId("super"), striker, nonStriker, bowler)
        } else {
            MatchEvent.StartNextInnings(freshId("innings"), striker, nonStriker, bowler)
        }
        apply(matchId) { MatchEngine.append(it, event) }
    }

    fun followOn(matchId: String) = apply(matchId) { MatchEngine.append(it, MatchEvent.EnforceFollowOn(freshId("follow"))) }

    fun closeInnings(matchId: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.CloseInnings(freshId("close"), "Ended by the scorer")) }

    fun abandon(matchId: String, reason: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.Abandon(freshId("abandon"), reason.ifBlank { "Abandoned" })) }

    fun concede(matchId: String, teamId: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.Concede(freshId("concede"), teamId)) }

    fun retire(matchId: String, playerId: String, hurt: Boolean) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.RetireBatter(freshId("retire"), playerId, hurt)) }

    fun award(matchId: String, playerId: String) =
        apply(matchId) { MatchEngine.append(it, MatchEvent.PlayerOfTheMatch(freshId("award"), playerId)) }

    fun switchPlayer(matchId: String, teamId: String, outgoingId: String, incoming: MatchPlayer, reason: String, injured: Boolean, approvalA: Boolean, approvalB: Boolean) =
        apply(matchId) {
            MatchEngine.append(it, MatchEvent.SwitchPlayer(freshId("switch"), teamId, outgoingId, incoming, reason, injured, approvalA, approvalB, timestampEpochMs = nowMillis()))
        }

    fun addMatchPlayer(matchId: String, teamId: String, player: MatchPlayer, reason: String, approvalA: Boolean, approvalB: Boolean) =
        apply(matchId) {
            MatchEngine.append(it, MatchEvent.AddPlayer(freshId("add"), teamId, player, reason, approvalA, approvalB, timestampEpochMs = nowMillis()))
        }

    fun lendPlayer(matchId: String, fromTeamId: String, toTeamId: String, playerId: String, reason: String, temporary: Boolean, approvalA: Boolean, approvalB: Boolean) =
        apply(matchId) {
            MatchEngine.append(it, MatchEvent.LendPlayer(freshId("loan"), fromTeamId, toTeamId, playerId, reason, temporary, approvalA, approvalB, timestampEpochMs = nowMillis()))
        }

    fun saveProfile(rules: RulesProfile) {
        val stored = rules.copy(id = freshId("profile"), source = com.example.cricketscoring.model.RuleSource.CUSTOM)
        update { it.copy(ruleProfiles = it.ruleProfiles + stored) }
        showToast("Rules saved for later matches.", AppToastType.Success)
    }

    fun createTournament(name: String, style: CricketStyle): String? {
        if (name.isBlank()) {
            showToast("Give the tournament a name.", AppToastType.Warning)
            return null
        }
        val tournament = Tournament(
            id = freshId("tournament"),
            name = name.trim(),
            style = style,
            rules = defaultRules(style).copy(id = freshId("rules"), source = com.example.cricketscoring.model.RuleSource.TOURNAMENT),
        )
        update { it.copy(tournaments = it.tournaments + tournament) }
        return tournament.id
    }

    fun saveTournament(tournament: Tournament) {
        update { current -> current.copy(tournaments = current.tournaments.map { if (it.id == tournament.id) tournament else it }) }
    }

    fun clearAll() {
        data = AppData()
        repository.save(data)
        draft = null
        showToast("Local scores cleared.", AppToastType.Success)
    }

    fun playerName(id: String): String = data.players.find { it.id == id }?.name ?: id

    private fun update(block: (AppData) -> AppData) {
        data = block(data)
        repository.save(data)
    }

    private fun matchTeam(team: Team, squad: List<String>, captain: String?, keeper: String?) = MatchTeam(
        teamId = team.id,
        name = team.name,
        shortName = team.shortName,
        color = team.color,
        players = squad.map { id -> MatchPlayer(id, playerName(id)) },
        captainId = captain,
        wicketkeeperId = keeper,
    )

    internal fun defaultSquad(team: Team, size: Int): List<String> {
        val preferred = listOfNotNull(team.captainId, team.wicketkeeperId) + team.playerIds
        return preferred.distinct().filter { it in team.playerIds }.take(size)
    }

    private fun draftFrom(match: Match): MatchDraft {
        val derived = MatchEngine.derive(match)
        val step = when {
            match.opening != null || derived.status != MatchStatus.UPCOMING -> 3
            match.toss != null -> 3
            else -> 3
        }
        return MatchDraft(
            style = match.style,
            step = step,
            existingMatchId = match.id,
            teamAId = match.teamA.teamId,
            teamBId = match.teamB.teamId,
            squadA = match.teamA.players.map { it.id },
            squadB = match.teamB.players.map { it.id },
            captainA = match.teamA.captainId,
            captainB = match.teamB.captainId,
            keeperA = match.teamA.wicketkeeperId,
            keeperB = match.teamB.wicketkeeperId,
            rules = match.rules,
            tournamentId = match.tournamentId,
            fixtureId = match.fixtureId,
            venue = match.venue,
            title = match.title,
            skipToss = match.toss?.skipped == true,
            tossWinnerId = match.toss?.winnerTeamId,
            tossChoice = match.toss?.choice,
            manualBattingId = match.battingFirstTeamId,
            strikerId = match.opening?.strikerId,
            nonStrikerId = match.opening?.nonStrikerId,
            bowlerId = match.opening?.bowlerId,
        )
    }
}

fun battingFirstId(draft: MatchDraft): String? {
    if (draft.skipToss) return draft.manualBattingId
    val winner = draft.tossWinnerId ?: return null
    val choice = draft.tossChoice ?: return null
    val other = if (winner == draft.teamAId) draft.teamBId else draft.teamAId
    return if (choice == TossChoice.BAT) winner else other
}

fun wizardIssue(store: CricketStore, draft: MatchDraft, requireStartFields: Boolean): WizardIssue? {
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    when {
        teamA == null -> return WizardIssue(1, "Pick Team A to continue.", "teamA")
        teamB == null -> return WizardIssue(1, "Pick Team B to continue.", "teamB")
        teamA.id == teamB.id -> return WizardIssue(1, "Choose two different teams.", "teamB")
    }
    val size = draft.rules.squad.playersPerTeam
    when {
        size !in 2..15 -> return WizardIssue(2, "Set players per team between 2 and 15.", "players")
        draft.rules.overs.ballsPerOver !in 1..10 -> return WizardIssue(2, "Set balls in an over between 1 and 10.", "balls")
        draft.rules.overs.inningsPerTeam !in 1..2 -> return WizardIssue(2, "Use 1 or 2 innings a side.", "innings")
        draft.rules.overs.oversPerInnings == null && draft.rules.overs.inningsPerTeam == 1 ->
            return WizardIssue(2, "Set overs for the innings, or switch to multi-innings.", "overs")
    }
    if (!requireStartFields) return null
    val sideA = teamA ?: return WizardIssue(1, "Pick Team A to continue.", "teamA")
    val sideB = teamB ?: return WizardIssue(1, "Pick Team B to continue.", "teamB")
    val squadA = draft.squadA.ifEmpty { store.defaultSquad(sideA, size) }
    val squadB = draft.squadB.ifEmpty { store.defaultSquad(sideB, size) }
    val uneven = draft.rules.roster.allowUnevenTeamSizes
    when {
        !uneven && squadA.size != size ->
            return WizardIssue(3, "${sideA.name} needs $size players selected for this match.", "squadA")
        !uneven && squadB.size != size ->
            return WizardIssue(3, "${sideB.name} needs $size players selected for this match.", "squadB")
        uneven && (squadA.isEmpty() || squadB.isEmpty()) ->
            return WizardIssue(3, "Each side needs at least one selected player.", "squadA")
        draft.rules.squad.twoBatters && (squadA.size < 2 || squadB.size < 2) ->
            return WizardIssue(3, "Each squad needs at least two players for two batters.", "squadA")
        battingFirstId(draft) == null ->
            return WizardIssue(
                3,
                if (draft.skipToss) "Choose which team bats first." else "Record the toss winner and bat/bowl decision.",
                "toss",
            )
    }
    val battingFirst = battingFirstId(draft)
    val battingSquad = if (battingFirst == sideB.id) squadB else squadA
    val bowlingSquad = if (battingFirst == sideB.id) squadA else squadB
    val bowlingRole = draft.rules.labels.bowlingRole.lowercase()
    return when {
        draft.strikerId == null || draft.strikerId !in battingSquad ->
            WizardIssue(3, "Choose the striker from the batting squad.", "striker")
        draft.rules.squad.twoBatters && (draft.nonStrikerId == null || draft.nonStrikerId !in battingSquad) ->
            WizardIssue(3, "Choose the non-striker from the batting squad.", "nonStriker")
        draft.rules.squad.twoBatters && draft.nonStrikerId == draft.strikerId ->
            WizardIssue(3, "Striker and non-striker must be different players.", "nonStriker")
        draft.bowlerId == null || draft.bowlerId !in bowlingSquad ->
            WizardIssue(3, "Choose the opening $bowlingRole from the bowling side.", "bowler")
        !draft.rules.confirmedByOrganizer ->
            WizardIssue(3, "Tick that the organizer checked these rules before the first ball.", "confirm")
        else -> null
    }
}

private fun shortName(name: String): String {
    val letters = name.filter { it.isLetterOrDigit() }
    return letters.take(3).uppercase().ifBlank { "TM" }
}

val teamPalette = listOf(
    0xFF1B8A4A.toInt(),
    0xFF1565C0.toInt(),
    0xFFC45C26.toInt(),
    0xFF6A4C93.toInt(),
    0xFFB08900.toInt(),
    0xFF0E7490.toInt(),
)

fun newFixture(round: String, teamAId: String, teamBId: String) = FixtureModel(
    id = freshId("fixture"),
    round = round.ifBlank { "League" },
    teamAId = teamAId,
    teamBId = teamBId,
)
