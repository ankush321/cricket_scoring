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
import com.example.cricketscoring.model.Fixture as FixtureModel
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

private var idSequence = 0

fun freshId(prefix: String): String {
    idSequence += 1
    return "$prefix-${Random.nextInt(1, 0xFFFFFF).toString(16)}-$idSequence"
}

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

sealed interface Screen {
    data object Home : Screen
    data object Matches : Screen
    data class StyleHub(val style: CricketStyle) : Screen
    data object Wizard : Screen
    data class Live(val matchId: String) : Screen
    data class Scorecard(val matchId: String) : Screen
    data object Teams : Screen
    data class TeamEdit(val teamId: String?) : Screen
    data object Players : Screen
    data object Tournaments : Screen
    data class TournamentDetail(val tournamentId: String) : Screen
    data object Stats : Screen
    data object More : Screen
    data object Settings : Screen
    data object RulesGuide : Screen
}

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
)

class CricketStore(private val repository: AppRepository) {
    var data by mutableStateOf(repository.load())
        private set
    var stack by mutableStateOf(listOf<Screen>(Screen.Home))
        private set
    var message by mutableStateOf<String?>(null)
    var draft by mutableStateOf<MatchDraft?>(null)
    var shareText by mutableStateOf<String?>(null)

    val screen: Screen get() = stack.last()

    fun dismissMessage() {
        message = null
    }

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun go(screen: Screen) {
        stack = listOf(screen)
    }

    fun openStyle(style: CricketStyle, fromHome: Boolean) {
        stack = listOf(if (fromHome) Screen.Home else Screen.Matches, Screen.StyleHub(style))
    }

    fun openWizard(style: CricketStyle, match: Match? = null) {
        draft = if (match == null) {
            MatchDraft(style = style, rules = defaultRules(style).copy(id = freshId("rules")))
        } else {
            draftFrom(match)
        }
        push(Screen.Wizard)
    }

    fun duplicate(match: Match) {
        draft = draftFrom(match).copy(existingMatchId = null, step = 1, strikerId = null, nonStrikerId = null, bowlerId = null)
        push(Screen.Wizard)
    }

    fun openMatch(match: Match) {
        val derived = MatchEngine.derive(match)
        when (derived.status) {
            MatchStatus.UPCOMING, MatchStatus.CANCELLED -> openWizard(match.style, match)
            MatchStatus.COMPLETED, MatchStatus.ABANDONED -> push(Screen.Scorecard(match.id))
            else -> push(Screen.Live(match.id))
        }
    }

    fun createTeam(name: String, playerLines: String, color: Int): Team? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            message = "Give the team a name."
            return null
        }
        val names = playerLines.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) {
            message = "Add at least one player."
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
            message = "A player needs a name."
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
            message = "This player is still on a team or in a match."
            return
        }
        update { it.copy(players = it.players.filterNot { player -> player.id == playerId }) }
    }

    fun commitDraft(start: Boolean) {
        val currentDraft = draft ?: return
        try {
            val teamA = data.teams.find { it.id == currentDraft.teamAId } ?: error("Choose the first team.")
            val teamB = data.teams.find { it.id == currentDraft.teamBId } ?: error("Choose the second team.")
            if (teamA.id == teamB.id) error("Choose two different teams.")
            val size = currentDraft.rules.squad.playersPerTeam
            val squadA = currentDraft.squadA.ifEmpty { defaultSquad(teamA, size) }
            val squadB = currentDraft.squadB.ifEmpty { defaultSquad(teamB, size) }
            val uneven = currentDraft.rules.roster.allowUnevenTeamSizes
            if (!uneven && squadA.size != size) error("${teamA.name} needs $size players in the squad.")
            if (!uneven && squadB.size != size) error("${teamB.name} needs $size players in the squad.")
            if (uneven && (squadA.isEmpty() || squadB.isEmpty())) error("Each team needs at least one player.")
            if (currentDraft.rules.squad.twoBatters && (squadA.size < 2 || squadB.size < 2)) {
                error("This match needs two batters, so each squad needs at least two players.")
            }
            if (start && !currentDraft.rules.confirmedByOrganizer) error("Confirm the match rules before the first ball.")
            val battingFirst = battingFirstId(currentDraft)
            if (start && battingFirst == null) error("Choose who bats first.")
            val battingSquad = if (battingFirst == teamB.id) squadB else squadA
            val bowlingSquad = if (battingFirst == teamB.id) squadA else squadB
            val opening = if (!start) {
                null
            } else {
                val striker = currentDraft.strikerId ?: error("Choose the striker.")
                val nonStriker = if (currentDraft.rules.squad.twoBatters) {
                    currentDraft.nonStrikerId ?: error("Choose the non-striker.")
                } else {
                    null
                }
                val bowler = currentDraft.bowlerId ?: error("Choose the opening ${currentDraft.rules.labels.bowlingRole.lowercase()}.")
                if (striker !in battingSquad || (nonStriker != null && nonStriker !in battingSquad)) {
                    error("Opening batters have to be in the batting squad.")
                }
                if (bowler !in bowlingSquad) error("The opening bowler has to be in the bowling squad.")
                if (nonStriker != null && nonStriker == striker) error("The striker and non-striker must be different.")
                OpeningPlayers(striker, nonStriker, bowler)
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
            message = if (start) "Match started." else "Match saved."
            stack = listOf(
                Screen.Home,
                Screen.StyleHub(created.style),
                if (start) Screen.Live(created.id) else Screen.StyleHub(created.style),
            ).distinct()
        } catch (error: ScoringException) {
            message = error.message
        } catch (error: IllegalStateException) {
            message = error.message
        }
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
        if (stack.lastOrNull() is Screen.Live || stack.lastOrNull() is Screen.Scorecard) pop()
        message = "Match deleted."
    }

    fun apply(matchId: String, block: (Match) -> com.example.cricketscoring.model.EngineOutcome) {
        val match = data.matches.find { it.id == matchId } ?: return
        val outcome = block(match)
        val updated = outcome.match
        if (outcome.code == EngineCode.OK && updated != null) {
            val saved = updated.copy(updatedAtEpochMs = nowMillis())
            update { current -> current.copy(matches = current.matches.map { if (it.id == matchId) saved else it }) }
        } else {
            message = outcome.message ?: "That action is not allowed."
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
        message = "Rules saved for later matches."
    }

    fun createTournament(name: String, style: CricketStyle) {
        if (name.isBlank()) {
            message = "Give the tournament a name."
            return
        }
        val tournament = Tournament(
            id = freshId("tournament"),
            name = name.trim(),
            style = style,
            rules = defaultRules(style).copy(id = freshId("rules"), source = com.example.cricketscoring.model.RuleSource.TOURNAMENT),
        )
        update { it.copy(tournaments = it.tournaments + tournament) }
        push(Screen.TournamentDetail(tournament.id))
    }

    fun saveTournament(tournament: Tournament) {
        update { current -> current.copy(tournaments = current.tournaments.map { if (it.id == tournament.id) tournament else it }) }
    }

    fun clearAll() {
        data = AppData()
        repository.save(data)
        draft = null
        stack = listOf(Screen.Home)
        message = "Local scores cleared."
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

    private fun defaultSquad(team: Team, size: Int): List<String> {
        val preferred = listOfNotNull(team.captainId, team.wicketkeeperId) + team.playerIds
        return preferred.distinct().filter { it in team.playerIds }.take(size)
    }

    private fun draftFrom(match: Match): MatchDraft {
        val derived = MatchEngine.derive(match)
        val step = if (match.opening != null || derived.status != MatchStatus.UPCOMING) 5 else if (match.toss != null) 4 else 3
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
