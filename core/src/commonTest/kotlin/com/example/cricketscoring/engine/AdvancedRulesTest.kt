package com.example.cricketscoring.engine

import com.example.cricketscoring.model.BallInput
import com.example.cricketscoring.model.BoundaryMarker
import com.example.cricketscoring.model.DismissalInput
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.EngineCode
import com.example.cricketscoring.model.LastManRules
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchEvent
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.RosterRules
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.ScoringAwardMode
import com.example.cricketscoring.persist.AppJson
import com.example.cricketscoring.rules.boxDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedRulesTest {

    @Test
    fun customEightUpdatesTheTeamTotal() {
        val event = boxDefaults().additionalScoring.events.first().copy(enabled = true, runs = 8)
        val rules = boxDefaults().copy(additionalScoring = boxDefaults().additionalScoring.copy(events = listOf(event)))
        val innings = play(rules).ball(BallInput(scoringEventId = event.id), "eight").view().innings.single()
        assertEquals(8, innings.runs)
        assertEquals(8, row(innings, "A1").runs)
        assertEquals("Designated zone hit, 8 runs", innings.deliveries.single().commentary)
        assertEquals(1, innings.overs.single().deliveries.size)
    }

    @Test
    fun disabledCustomScoringIsRejected() {
        val event = boxDefaults().additionalScoring.events.first()
        assertFalse(event.enabled)
        val outcome = play(boxDefaults()).tryBall(BallInput(scoringEventId = event.id), "off")
        assertEquals(EngineCode.REJECTED, outcome.code)
    }

    @Test
    fun preMatchTransferMovesThePlayer() {
        val rules = boxDefaults().copy(roster = roster(allowUnevenTeamSizes = true))
        val upcoming = MatchFactory.create(
            id = "upcoming",
            style = rules.style,
            title = "Transfer",
            teamA = teamOf("A", "Team A", listOf("A1", "A2", "A3")),
            teamB = teamOf("B", "Team B", listOf("B1", "B2", "B3")),
            rules = rules,
        )
        val denied = MatchEngine.preMatchTransfer(upcoming, "A1", "A", "B")
        assertEquals(EngineCode.REJECTED, denied.code)
        val moved = MatchEngine.preMatchTransfer(upcoming, "A1", "A", "B", approvalA = true, approvalB = true).match!!
        assertTrue(moved.teamA.players.none { it.id == "A1" })
        assertTrue(moved.teamB.players.any { it.id == "A1" })
    }

    @Test
    fun inMatchSwitchKeepsTheOutgoingPlayersRuns() {
        val rules = boxDefaults().copy(roster = roster(inMatchSwitchingAllowed = true))
        val switched = play(rules)
            .ball(BallInput(boundary = BoundaryMarker.FOUR), "four")
            .event(
                MatchEvent.SwitchPlayer(
                    id = "switch",
                    teamId = "A",
                    outgoingPlayerId = "A1",
                    incoming = MatchPlayer("A9", "A9"),
                    reason = "Balance the sides",
                    approvalA = true,
                    approvalB = true,
                ),
            )
        val derived = switched.view()
        val innings = derived.innings.single()
        assertEquals(4, row(innings, "A1").runs)
        assertEquals(0, row(innings, "A9").runs)
        assertTrue(derived.liveTeamA.any { it.id == "A9" })
        assertTrue(derived.liveTeamA.none { it.id == "A1" })
        assertTrue(derived.rosterChanges.single().outgoingPlayerId == "A1")
    }

    @Test
    fun boxOutAddsAWicketAndKeepsBatRuns() {
        val rules = boxDefaults().copy(boxOut = boxDefaults().boxOut.copy(enabled = true, description = "Ball hits the marked box line.", bowlerCredited = false))
        val innings = play(rules).ball(
            BallInput(batterRuns = 3, dismissal = DismissalInput(DismissalType.BOX_OUT, "A1")),
            "box",
        ).view().innings.single()
        assertEquals(1, innings.wickets)
        assertEquals(3, row(innings, "A1").runs)
        assertTrue(row(innings, "A1").dismissalText.contains("box out"))
        assertEquals(0, innings.bowlingRows.single().wickets)
        assertEquals(1, innings.legalBalls)
        assertTrue(innings.needsNextBatter(play(rules)))
    }

    @Test
    fun lastManActivatesOnlyForTheConfiguredRemainder() {
        val early = reduceToOne(lastMan(minTeamSize = 4, remaining = 2))
        assertFalse(early.view().lastManStanding)
        val ready = reduceToOne(lastMan(minTeamSize = 4, remaining = 1))
        val derived = ready.view()
        assertTrue(derived.lastManStanding)
        assertEquals("A2", derived.innings.single().strikerId)
        assertEquals(null, derived.innings.single().nonStrikerId)
        assertFalse(derived.needsBatter)
        val scored = ready.ball(BallInput(boundary = BoundaryMarker.FOUR), "alone").view()
        assertEquals(4, row(scored.innings.single(), "A2").runs)
    }

    @Test
    fun raisingTheTeamSizeThresholdTurnsLastManOff() {
        val tooSmall = reduceToOne(lastMan(minTeamSize = 5, remaining = 1)).view()
        assertFalse(tooSmall.lastManStanding)
        assertTrue(tooSmall.innings.single().complete)
        val eligible = reduceToOne(lastMan(minTeamSize = 4, remaining = 1)).view()
        assertTrue(eligible.lastManStanding)
        assertFalse(eligible.innings.single().complete)
    }

    @Test
    fun overBreakdownKeepsExtrasOutsideTheLegalCount() {
        val innings = play(boxDefaults())
            .ball(BallInput(batterRuns = 1), "1")
            .ball(BallInput(wide = true), "wd")
            .ball(BallInput(boundary = BoundaryMarker.FOUR), "4")
            .view().innings.single()
        val over = innings.overs.single()
        assertEquals(listOf("1", "wd", "4"), over.symbols)
        assertEquals(3, over.deliveries.size)
        assertEquals(2, innings.legalBalls)
        assertEquals(6, over.runs)
        assertEquals("6/0", "${over.scoreAfter}/${over.wicketsAfter}")
    }

    @Test
    fun noBallFourCreditsTheBatterAndTheExtra() {
        val innings = play(boxDefaults()).ball(BallInput(noBall = true, boundary = BoundaryMarker.FOUR), "nb4").view().innings.single()
        assertEquals(4, row(innings, "A1").runs)
        assertEquals(1, innings.extras.noBalls)
        assertEquals(5, innings.runs)
        assertEquals(0, innings.legalBalls)
        val informal = boxDefaults().copy(extras = boxDefaults().extras.copy(noBallCreditsBatter = false))
        val extrasOnly = play(informal).ball(BallInput(noBall = true, boundary = BoundaryMarker.FOUR), "nbx").view().innings.single()
        assertEquals(0, row(extrasOnly, "A1").runs)
        assertEquals(5, extrasOnly.extras.noBalls)
        assertEquals(5, extrasOnly.runs)
    }

    @Test
    fun noBallRunningRunsStayWithTheBatter() {
        val innings = play(boxDefaults()).ball(BallInput(noBall = true, batterRuns = 2), "nb2").view().innings.single()
        assertEquals(2, row(innings, "A1").runs)
        assertEquals(1, innings.extras.noBalls)
        assertEquals(3, innings.runs)
        assertEquals(1, row(innings, "A1").balls)
    }

    @Test
    fun undoRestoresTheNoBall() {
        val before = play(boxDefaults()).ball(BallInput(batterRuns = 1), "1")
        val after = before.ball(BallInput(noBall = true, boundary = BoundaryMarker.FOUR), "nb4")
        val undone = MatchEngine.undo(after).match!!
        assertEquals(before.view().innings.single().runs, undone.view().innings.single().runs)
        assertEquals(1, row(undone.view().innings.single(), "A1").runs)
        assertEquals(0, undone.view().innings.single().extras.noBalls)
        assertEquals(1, undone.view().innings.single().bowlingRows.single().runs)
    }

    @Test
    fun addPlayerRequiresTheSetting() {
        val blocked = play(boxDefaults()).tryEvent(add("B"))
        assertEquals(EngineCode.REJECTED, blocked.code)
        val rules = boxDefaults().copy(roster = roster(allowAddDuringMatch = true, allowUnevenTeamSizes = true))
        val added = play(rules).ball(BallInput(batterRuns = 1), "1").event(add("A"))
        assertEquals(1, added.view().innings.single().runs)
        assertTrue(added.view().liveTeamA.any { it.id == "C1" })
        assertEquals(1, added.view().rosterChanges.size)
    }

    @Test
    fun loanRequiresBothApprovals() {
        val rules = boxDefaults().copy(roster = roster(allowLoanToOpponent = true, allowUnevenTeamSizes = true))
        val start = play(rules)
        val denied = start.tryEvent(loan(approved = false))
        assertEquals(EngineCode.REJECTED, denied.code)
        val lent = start.event(loan(approved = true)).view()
        assertTrue(lent.liveTeamA.none { it.id == "A3" })
        assertTrue(lent.liveTeamB.any { it.id == "A3" })
        assertEquals(1, lent.rosterChanges.single().let { if (it.approvalA && it.approvalB) 1 else 0 })
    }

    @Test
    fun aPlayerCannotStandForBothTeams() {
        val rules = boxDefaults().copy(roster = roster(allowLoanToOpponent = true, allowUnevenTeamSizes = true, allowMultipleSwitches = false))
        val lent = play(rules).event(loan(approved = true)).view()
        val onA = lent.liveTeamA.map { it.id }.toSet()
        val onB = lent.liveTeamB.map { it.id }.toSet()
        assertTrue(onA.intersect(onB).isEmpty())
        val back = MatchEngine.append(play(rules).event(loan(approved = true)), loan(approved = true, from = "B", to = "A", player = "A3", id = "back"))
        assertEquals(EngineCode.REJECTED, back.code)
    }

    @Test
    fun unevenSquadsFollowTheSetting() {
        val evenRules = boxDefaults()
        assertFailsWith<ScoringException> {
            MatchFactory.create(
                id = "uneven",
                style = evenRules.style,
                title = "Uneven",
                teamA = teamOf("A", "Team A", listOf("A1", "A2")),
                teamB = teamOf("B", "Team B", listOf("B1", "B2", "B3")),
                rules = evenRules,
            )
        }
        val allowed = evenRules.copy(roster = roster(allowUnevenTeamSizes = true))
        val match = MatchFactory.create(
            id = "uneven-ok",
            style = allowed.style,
            title = "Uneven",
            teamA = teamOf("A", "Team A", listOf("A1", "A2")),
            teamB = teamOf("B", "Team B", listOf("B1", "B2", "B3")),
            rules = allowed,
            battingFirstTeamId = "A",
            opening = OpeningPlayers("A1", "A2", "B1"),
        )
        assertEquals(2, match.teamA.players.size)
        assertEquals(3, match.teamB.players.size)
    }

    @Test
    fun restartRestoresScoringAndRosterEvents() {
        val rules = boxDefaults().copy(
            additionalScoring = boxDefaults().additionalScoring.copy(events = listOf(boxDefaults().additionalScoring.events.first().copy(enabled = true))),
            roster = roster(inMatchSwitchingAllowed = true),
        )
        val event = rules.additionalScoring.events.first()
        val match = play(rules)
            .ball(BallInput(scoringEventId = event.id), "eight")
            .event(
                MatchEvent.SwitchPlayer("switch", "A", "A1", MatchPlayer("A9", "A9"), "Balance", approvalA = true, approvalB = true),
            )
        val restored = AppJson.decodeMatch(AppJson.encodeMatch(match))
        assertEquals(match.view().innings.single().runs, restored.view().innings.single().runs)
        assertEquals(match.view().rosterChanges.map { it.playerId }, restored.view().rosterChanges.map { it.playerId })
        assertEquals(8, row(restored.view().innings.single(), "A1").runs)
    }

    @Test
    fun scorecardReconcilesWithDeliveries() {
        val rules = boxDefaults().copy(boxOut = boxDefaults().boxOut.copy(enabled = true))
        val match = play(rules)
            .ball(BallInput(noBall = true, batterRuns = 2), "nb")
            .ball(BallInput(scoringEventId = null, batterRuns = 1), "1")
            .ball(BallInput(boundary = BoundaryMarker.SIX), "6")
            .ball(BallInput(dismissal = DismissalInput(DismissalType.BOX_OUT, "A2")), "out")
        assertEquals(emptyList(), MatchInvariants.problems(match))
        val innings = match.view().innings.single()
        assertEquals(innings.runs, innings.deliveries.sumOf { it.totalRuns })
        assertEquals(innings.runs, innings.overs.sumOf { it.runs })
    }

    private fun lastMan(minTeamSize: Int, remaining: Int): Match {
        val rules = boxDefaults().copy(
            lastMan = LastManRules(
                enabled = true,
                minTeamSize = minTeamSize,
                remainingBattersToActivate = remaining,
                allowScoring = true,
                allowRunning = false,
                endOnDismissal = true,
            ),
            roster = roster(),
        )
        return MatchFactory.create(
            id = "last",
            style = rules.style,
            title = "Last",
            teamA = teamOf("A", "Team A", listOf("A1", "A2", "A3", "A4")),
            teamB = teamOf("B", "Team B", listOf("B1", "B2", "B3", "B4")),
            rules = rules.copy(overs = rules.overs.copy(oversPerInnings = 10, maxOversPerBowler = 10)),
            battingFirstTeamId = "A",
            opening = OpeningPlayers("A1", "A2", "B1"),
        )
    }

    private fun reduceToOne(start: Match): Match {
        var match = start
        for (index in 0..2) {
            val striker = match.view().innings.single().strikerId ?: return match
            match = match.ball(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, striker)), "w$index")
            val derived = match.view()
            if (!derived.needsBatter) continue
            val next = derived.innings.single().yetToBat.firstOrNull() ?: return match
            match = match.event(MatchEvent.NextBatter("next$index", next))
        }
        return match
    }
}

private fun roster(
    allowUnevenTeamSizes: Boolean = false,
    inMatchSwitchingAllowed: Boolean = false,
    allowAddDuringMatch: Boolean = false,
    allowLoanToOpponent: Boolean = false,
    allowMultipleSwitches: Boolean = false,
) = RosterRules(
    preMatchTransfersAllowed = true,
    inMatchSwitchingAllowed = inMatchSwitchingAllowed,
    requireBothTeamApproval = true,
    allowAddDuringMatch = allowAddDuringMatch,
    allowUnevenTeamSizes = allowUnevenTeamSizes,
    allowLoanToOpponent = allowLoanToOpponent,
    allowMultipleSwitches = allowMultipleSwitches,
)

private fun play(rules: RulesProfile) = MatchFactory.create(
    id = "match",
    style = rules.style,
    title = "Test",
    teamA = teamOf("A", "Team A", listOf("A1", "A2", "A3")),
    teamB = teamOf("B", "Team B", listOf("B1", "B2", "B3")),
    rules = rules.copy(overs = rules.overs.copy(oversPerInnings = 5, ballsPerOver = 6, maxOversPerBowler = rules.overs.maxOversPerBowler ?: 2)),
    battingFirstTeamId = "A",
    opening = OpeningPlayers("A1", "A2", "B1"),
)

private fun add(teamId: String) = MatchEvent.AddPlayer(
    id = "add",
    teamId = teamId,
    player = MatchPlayer("C1", "C1"),
    reason = "Arrived late",
    approvalA = true,
    approvalB = true,
)

private fun loan(approved: Boolean, from: String = "A", to: String = "B", player: String = "A3", id: String = "loan") = MatchEvent.LendPlayer(
    id = id,
    fromTeamId = from,
    toTeamId = to,
    playerId = player,
    reason = "Even the numbers",
    temporary = true,
    approvalA = approved,
    approvalB = approved,
)

private fun Match.ball(input: BallInput, id: String) = event(MatchEvent.Ball(id, input))

private fun Match.event(event: MatchEvent): Match {
    val outcome = MatchEngine.append(this, event)
    if (outcome.code != EngineCode.OK || outcome.match == null) error(outcome.message ?: "Rejected")
    return outcome.match
}

private fun Match.tryBall(input: BallInput, id: String) = MatchEngine.append(this, MatchEvent.Ball(id, input))

private fun Match.tryEvent(event: MatchEvent) = MatchEngine.append(this, event)

private fun Match.view() = MatchEngine.derive(this)

private fun row(innings: com.example.cricketscoring.model.DerivedInnings, id: String) = innings.battingRows.first { it.playerId == id }

private fun com.example.cricketscoring.model.DerivedInnings.needsNextBatter(ignored: Match) = yetToBat.isNotEmpty()
