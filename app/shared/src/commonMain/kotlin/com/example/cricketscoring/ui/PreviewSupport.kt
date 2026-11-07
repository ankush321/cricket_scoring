package com.example.cricketscoring.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.cricketscoring.engine.MatchFactory
import com.example.cricketscoring.model.AppData
import com.example.cricketscoring.model.CompetitionStage
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.Fixture
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.MatchTeam
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.Player
import com.example.cricketscoring.model.PlayingRole
import com.example.cricketscoring.model.Team
import com.example.cricketscoring.model.Toss
import com.example.cricketscoring.model.TossChoice
import com.example.cricketscoring.model.Tournament
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.navigation.PreviewAppNavigator
import com.example.cricketscoring.persist.MemoryRepository
import com.example.cricketscoring.rules.defaultRules

object PreviewIds {
    const val PlayerA1 = "p-a1"
    const val PlayerA2 = "p-a2"
    const val PlayerA3 = "p-a3"
    const val PlayerB1 = "p-b1"
    const val PlayerB2 = "p-b2"
    const val PlayerB3 = "p-b3"
    const val TeamA = "team-lions"
    const val TeamB = "team-hawks"
    const val MatchLive = "match-live"
    const val MatchUpcoming = "match-upcoming"
    const val Tournament = "tournament-box"
    const val Fixture = "fixture-1"
}

object PreviewSampleData {
    val players = listOf(
        Player(PreviewIds.PlayerA1, "Aarav Shah", role = PlayingRole.BATTER),
        Player(PreviewIds.PlayerA2, "Kabir Mehta", role = PlayingRole.ALL_ROUNDER),
        Player(PreviewIds.PlayerA3, "Rohan Desai", role = PlayingRole.BOWLER),
        Player(PreviewIds.PlayerB1, "Vihaan Patel", role = PlayingRole.BATTER),
        Player(PreviewIds.PlayerB2, "Ishaan Rao", role = PlayingRole.WICKET_KEEPER),
        Player(PreviewIds.PlayerB3, "Dev Kapoor", role = PlayingRole.BOWLER),
    )

    val teams = listOf(
        Team(
            id = PreviewIds.TeamA,
            name = "Lions",
            shortName = "LIO",
            color = 0xFFFFB020.toInt(),
            playerIds = listOf(PreviewIds.PlayerA1, PreviewIds.PlayerA2, PreviewIds.PlayerA3),
            captainId = PreviewIds.PlayerA1,
            wicketkeeperId = PreviewIds.PlayerA2,
        ),
        Team(
            id = PreviewIds.TeamB,
            name = "Hawks",
            shortName = "HWK",
            color = 0xFF2EC4B6.toInt(),
            playerIds = listOf(PreviewIds.PlayerB1, PreviewIds.PlayerB2, PreviewIds.PlayerB3),
            captainId = PreviewIds.PlayerB1,
            wicketkeeperId = PreviewIds.PlayerB2,
        ),
    )

    private val style = CricketStyle.UNDERARM_BOX
    private val rules = defaultRules(style).copy(id = "rules-preview")

    private fun matchTeam(team: Team) = MatchTeam(
        teamId = team.id,
        name = team.name,
        shortName = team.shortName,
        color = team.color,
        players = team.playerIds.map { id -> MatchPlayer(id, players.first { it.id == id }.name) },
        captainId = team.captainId,
        wicketkeeperId = team.wicketkeeperId,
    )

    val liveMatch = MatchFactory.create(
        id = PreviewIds.MatchLive,
        style = style,
        title = "Lions v Hawks",
        teamA = matchTeam(teams[0]),
        teamB = matchTeam(teams[1]),
        rules = rules,
        venue = "Indoor Arena",
        createdAtEpochMs = 1_700_000_000_000,
        toss = Toss(winnerTeamId = PreviewIds.TeamA, choice = TossChoice.BAT),
        battingFirstTeamId = PreviewIds.TeamA,
        opening = OpeningPlayers(
            strikerId = PreviewIds.PlayerA1,
            nonStrikerId = PreviewIds.PlayerA2,
            bowlerId = PreviewIds.PlayerB3,
        ),
    )

    val upcomingMatch = MatchFactory.create(
        id = PreviewIds.MatchUpcoming,
        style = style,
        title = "Lions v Hawks · Rematch",
        teamA = matchTeam(teams[0]),
        teamB = matchTeam(teams[1]),
        rules = rules,
        venue = "Court 2",
        createdAtEpochMs = 1_700_000_100_000,
    )

    val tournament = Tournament(
        id = PreviewIds.Tournament,
        name = "Friday Box Cup",
        style = style,
        teamIds = listOf(PreviewIds.TeamA, PreviewIds.TeamB),
        rules = rules.copy(id = "rules-tournament"),
        stage = CompetitionStage.LEAGUE,
        fixtures = listOf(
            Fixture(
                id = PreviewIds.Fixture,
                round = "League",
                teamAId = PreviewIds.TeamA,
                teamBId = PreviewIds.TeamB,
                matchId = PreviewIds.MatchLive,
            ),
        ),
    )

    val appData = AppData(
        players = players,
        teams = teams,
        matches = listOf(liveMatch, upcomingMatch),
        tournaments = listOf(tournament),
        ruleProfiles = listOf(rules),
    )
}

fun previewStore(data: AppData = PreviewSampleData.appData): CricketStore =
    CricketStore(MemoryRepository(data))

/**
 * Theme + no-op navigator for Compose Preview.
 *
 * Avoids [androidx.navigation.compose.rememberNavController] so Android Studio can
 * discover `@Preview` in every file (same approach as WSLF fantasy-engine).
 */
@Composable
fun PreviewShell(
    configure: (CricketStore) -> Unit = {},
    content: @Composable (CricketStore) -> Unit,
) {
    val store = remember { previewStore().also(configure) }
    PitchTheme {
        CompositionLocalProvider(LocalAppNavigator provides PreviewAppNavigator()) {
            content(store)
        }
    }
}
