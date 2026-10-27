package com.example.cricketscoring.engine

import com.example.cricketscoring.model.BallInput
import com.example.cricketscoring.model.BoundaryMarker
import com.example.cricketscoring.model.DismissalInput
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.EngineCode
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchEvent
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.ResultKind
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.SurfaceContact
import com.example.cricketscoring.persist.AppJson
import com.example.cricketscoring.rules.boxDefaults
import com.example.cricketscoring.rules.openDefaults
import com.example.cricketscoring.rules.overarmLimited
import com.example.cricketscoring.rules.overarmMultiInnings
import com.example.cricketscoring.rules.ruleCatalog
import com.example.cricketscoring.model.RuleDisposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringEngineTest {

    @Test
    fun dotBall() {
        val derived = box().ball(BallInput(), "d").view()
        val innings = derived.innings.single()
        assertEquals(0, innings.runs)
        assertEquals(0, innings.wickets)
        assertEquals(1, innings.legalBalls)
        assertEquals("·", innings.deliveries.single().symbol)
        assertEquals(0, innings.battingRows.first().runs)
        assertEquals(1, innings.battingRows.first().balls)
        assertEquals(0, innings.bowlingRows.single().runs)
    }

    @Test
    fun singleDoubleAndTripleRotateStrike() {
        val derived = box().ball(BallInput(batterRuns = 1), "1")
            .ball(BallInput(batterRuns = 2), "2")
            .ball(BallInput(batterRuns = 3), "3")
            .view()
        val innings = derived.innings.single()
        assertEquals(6, innings.runs)
        assertEquals(1, row(innings, "A1").runs)
        assertEquals(1, row(innings, "A1").balls)
        assertEquals(5, row(innings, "A2").runs)
        assertEquals(2, row(innings, "A2").balls)
        assertEquals("A1", innings.strikerId)
    }

    @Test
    fun fourAndSixCountBoundaries() {
        val innings = box().ball(BallInput(boundary = BoundaryMarker.FOUR), "4")
            .ball(BallInput(boundary = BoundaryMarker.SIX), "6")
            .view().innings.single()
        val batter = row(innings, "A1")
        assertEquals(10, batter.runs)
        assertEquals(1, batter.fours)
        assertEquals(1, batter.sixes)
        assertEquals(10, innings.runs)
        assertEquals("A1", innings.strikerId)
    }

    @Test
    fun wideAndNoBallAreNotLegal() {
        val innings = box().ball(BallInput(wide = true), "w")
            .ball(BallInput(noBall = true), "n")
            .view().innings.single()
        assertEquals(2, innings.runs)
        assertEquals(0, innings.legalBalls)
        assertEquals(1, innings.extras.wides)
        assertEquals(1, innings.extras.noBalls)
        assertEquals(0, row(innings, "A1").runs)
        assertEquals(1, row(innings, "A1").balls)
        assertEquals(2, innings.bowlingRows.single().runs)
        assertEquals(1, innings.bowlingRows.single().wides)
        assertEquals(1, innings.bowlingRows.single().noBalls)
        assertEquals("A1", innings.strikerId)
    }

    @Test
    fun byesAndLegByesStayOffTheBatterAndBowler() {
        val innings = open().ball(BallInput(byeRuns = 1), "b")
            .ball(BallInput(legByeRuns = 2), "lb")
            .view().innings.single()
        assertEquals(3, innings.runs)
        assertEquals(0, row(innings, "A1").runs)
        assertEquals(0, row(innings, "A2").runs)
        assertEquals(1, innings.extras.byes)
        assertEquals(2, innings.extras.legByes)
        assertEquals(0, innings.bowlingRows.single().runs)
        assertEquals(2, innings.legalBalls)
        assertEquals("A2", innings.strikerId)
    }

    @Test
    fun overthrowAddsToTheBatter() {
        val innings = open().ball(BallInput(batterRuns = 1, overthrowBatterRuns = 3), "o").view().innings.single()
        assertEquals(4, innings.runs)
        assertEquals(4, row(innings, "A1").runs)
        assertEquals(4, innings.bowlingRows.single().runs)
        assertEquals("A1", innings.strikerId)
    }

    @Test
    fun bowledWicketCreditsTheBowler() {
        val derived = box().ball(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, "A1")), "w").view()
        val innings = derived.innings.single()
        assertEquals(1, innings.wickets)
        assertEquals(1, innings.bowlingRows.single().wickets)
        assertEquals("b B1", row(innings, "A1").dismissalText)
        assertTrue(derived.needsBatter)
        assertEquals("A2", innings.nonStrikerId)
        assertNull(innings.strikerId)
    }

    @Test
    fun illegalDeliveryAllowsOnlySomeWickets() {
        val noBallBowled = overarm().tryBall(BallInput(noBall = true, dismissal = DismissalInput(DismissalType.BOWLED, "A1")), "bad")
        assertEquals(EngineCode.REJECTED, noBallBowled.code)

        val runOut = overarm().ball(BallInput(noBall = true, dismissal = DismissalInput(DismissalType.RUN_OUT, "A1", "B2")), "ro").view()
        assertEquals(1, runOut.innings.single().wickets)
        assertEquals(1, runOut.innings.single().extras.noBalls)
        assertEquals(0, runOut.innings.single().legalBalls)
        assertEquals(0, runOut.innings.single().bowlingRows.single().wickets)

        val freeHit = overarm().ball(BallInput(noBall = true), "nb")
        assertTrue(freeHit.view().freeHit)
        val bowledOnFreeHit = freeHit.tryBall(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, "A1")), "fh")
        assertEquals(EngineCode.REJECTED, bowledOnFreeHit.code)
        val runOutOnFreeHit = freeHit.ball(
            BallInput(dismissal = DismissalInput(DismissalType.RUN_OUT, "A2", "B3")),
            "fhr",
        ).view()
        assertEquals(1, runOutOnFreeHit.innings.single().wickets)
        assertEquals(0, runOutOnFreeHit.innings.single().bowlingRows.single().wickets)
    }

    @Test
    fun runOutKeepsCompletedRunsAndDoesNotCreditTheBowler() {
        val derived = box().ball(
            BallInput(batterRuns = 1, dismissal = DismissalInput(DismissalType.RUN_OUT, "A1", "B2")),
            "ro",
        ).view()
        val innings = derived.innings.single()
        assertEquals(1, innings.runs)
        assertEquals(1, innings.wickets)
        assertEquals(1, row(innings, "A1").runs)
        assertEquals(0, innings.bowlingRows.single().wickets)
        assertEquals("A2", innings.strikerId)
        assertTrue(derived.needsBatter)
        assertEquals("run out (B2)", row(innings, "A1").dismissalText)
    }

    @Test
    fun caughtDismissal() {
        val innings = box().ball(
            BallInput(dismissal = DismissalInput(DismissalType.CAUGHT, "A1", "B2")),
            "c",
        ).view().innings.single()
        assertEquals(0, innings.runs)
        assertEquals(1, innings.bowlingRows.single().wickets)
        assertEquals("c B2 b B1", row(innings, "A1").dismissalText)
        assertEquals(1, innings.fallOfWickets.single().wicketNumber)
        assertEquals(0, innings.fallOfWickets.single().teamScore)
    }

    @Test
    fun strikeRotatesOnOddRunsOnly() {
        val afterSingle = box().ball(BallInput(batterRuns = 1), "1").view().innings.single()
        assertEquals("A2", afterSingle.strikerId)
        val afterTwo = box().ball(BallInput(batterRuns = 2), "2").view().innings.single()
        assertEquals("A1", afterTwo.strikerId)
        val afterWide = box().ball(BallInput(wide = true), "w").view().innings.single()
        assertEquals("A1", afterWide.strikerId)
        val afterWideRun = box().ball(BallInput(wide = true, wideRunningRuns = 1), "wr").view().innings.single()
        assertEquals("A2", afterWideRun.strikerId)
    }

    @Test
    fun overCompletesAfterTheConfiguredLegalBalls() {
        var match = box(overs = 2)
        repeat(6) { index -> match = match.ball(BallInput(), "d$index") }
        val derived = match.view()
        val innings = derived.innings.single()
        assertEquals("1", innings.oversLabel())
        assertEquals(1, innings.bowlingRows.single().maidens)
        assertTrue(derived.needsBowler)
        assertEquals("A2", innings.strikerId)
        assertEquals(6, innings.thisOver().size)
    }

    @Test
    fun bowlerChangeCreditsTheNewBowler() {
        var match = box(overs = 2)
        repeat(6) { index -> match = match.ball(BallInput(), "d$index") }
        match = match.event(MatchEvent.NextBowler("next", "B2"))
        val innings = match.ball(BallInput(batterRuns = 1), "bat").view().innings.single()
        assertEquals(0, innings.bowlingRows.first { it.playerId == "B1" }.runs)
        assertEquals(1, innings.bowlingRows.first { it.playerId == "B2" }.runs)
        assertEquals(1, innings.bowlingRows.first { it.playerId == "B2" }.legalBalls)
    }

    @Test
    fun maximumOversPerBowlerIsEnforced() {
        var match = box(balls = 1, overs = 3, maxBowler = 1)
        match = match.ball(BallInput(), "only")
        val rejected = match.tryEvent(MatchEvent.NextBowler("again", "B1"))
        assertEquals(EngineCode.REJECTED, rejected.code)
        val accepted = match.event(MatchEvent.NextBowler("other", "B2")).ball(BallInput(batterRuns = 2), "ok").view()
        assertEquals(2, accepted.innings.single().runs)
        assertEquals(1, accepted.innings.single().bowlingRows.first { it.playerId == "B1" }.legalBalls)
    }

    @Test
    fun allOutEndsTheInnings() {
        val first = box().ball(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, "A1")), "w1")
        val second = first.event(MatchEvent.NextBatter("in", "A3"))
            .ball(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, "A3")), "w2")
            .view()
        val innings = second.innings.single()
        assertTrue(innings.complete)
        assertTrue(innings.allOut)
        assertEquals(2, innings.wickets)
        assertEquals(false, second.needsBatter)
        assertTrue(second.breakPending)
        assertEquals(2, innings.fallOfWickets.size)
    }

    @Test
    fun targetReachedWinsByWickets() {
        val first = chaseMatch().ball(BallInput(batterRuns = 1), "a")
        val second = first.event(MatchEvent.StartNextInnings("next", "B1", "B2", "A1"))
            .ball(BallInput(batterRuns = 2), "b")
            .view()
        assertEquals(ResultKind.WIN, second.result?.kind)
        assertEquals("B", second.result?.winnerTeamId)
        assertEquals("Team B won by 2 wickets.", second.result?.summary)
        assertEquals(2, second.innings[1].target)
    }

    @Test
    fun levelScoresAreATieWhenSuperOverIsOff() {
        val first = chaseMatch().ball(BallInput(), "a")
        val derived = first.event(MatchEvent.StartNextInnings("next", "B1", "B2", "A1"))
            .ball(BallInput(), "b")
            .view()
        assertEquals(ResultKind.TIE, derived.result?.kind)
        assertEquals("Match tied.", derived.result?.summary)
    }

    @Test
    fun undoRestoresThePreviousScore() {
        val scored = box().ball(BallInput(batterRuns = 1), "1")
        val undone = MatchEngine.undo(scored)
        assertEquals(EngineCode.OK, undone.code)
        assertEquals(0, undone.derived?.innings?.single()?.runs)
        assertEquals(0, undone.match?.events?.size)
        assertTrue(MatchInvariants.problems(undone.match!!).isEmpty())
    }

    @Test
    fun editingAnEarlierBallReplaysStrikeAndTotals() {
        val played = box(overs = 2)
            .ball(BallInput(batterRuns = 1), "e1")
            .ball(BallInput(batterRuns = 1), "e2")
        val edited = MatchEngine.replaceEvent(played, "e1", MatchEvent.Ball("e1", BallInput(boundary = BoundaryMarker.FOUR)))
        assertEquals(EngineCode.OK, edited.code)
        val innings = edited.derived!!.innings.single()
        assertEquals(5, innings.runs)
        assertEquals(5, row(innings, "A1").runs)
        assertEquals(0, row(innings, "A2").runs)
        assertEquals(1, row(innings, "A1").fours)
    }

    @Test
    fun aSavedMatchRestoresTheSameScore() {
        val played = box().ball(BallInput(batterRuns = 4), "4").ball(BallInput(wide = true), "w")
        val restored = AppJson.decodeMatch(AppJson.encodeMatch(played))
        val original = MatchEngine.derive(played)
        val again = MatchEngine.derive(restored)
        assertEquals(original, again)
        assertEquals(5, again.innings.single().runs)
        assertTrue(MatchInvariants.problems(restored).isEmpty())
    }

    @Test
    fun boxWallAndCeilingUseTheConfiguredValues() {
        val innings = box().ball(BallInput(surface = SurfaceContact.SIDE_WALL), "side")
            .ball(BallInput(surface = SurfaceContact.CEILING), "ceil")
            .view().innings.single()
        assertEquals(7, innings.runs)
        assertEquals(1, row(innings, "A1").runs)
        assertEquals(6, row(innings, "A2").runs)
        assertEquals(0, row(innings, "A2").sixes)
        val caughtOffWall = box().tryBall(
            BallInput(surface = SurfaceContact.SIDE_WALL, dismissal = DismissalInput(DismissalType.CAUGHT, "A1", "B2")),
            "wall",
        )
        assertEquals(EngineCode.REJECTED, caughtOffWall.code)
        val noRunning = box().rules.boundaries.copy(runningBetweenWicketsEnabled = false)
        val stopped = live(box().rules.copy(boundaries = noRunning)).tryBall(BallInput(batterRuns = 1), "run")
        assertEquals(EngineCode.REJECTED, stopped.code)
    }

    @Test
    fun oneTipOneHandIsOptInAndCreditsTheBowler() {
        val blocked = open().tryBall(
            BallInput(dismissal = DismissalInput(DismissalType.ONE_TIP_ONE_HAND, "A1", "B2")),
            "tip",
        )
        assertEquals(EngineCode.REJECTED, blocked.code)
        val enabled = live(
            openDefaults().copy(local = openDefaults().local.copy(oneTipOneHandEnabled = true)),
            overs = 2,
        )
        val innings = enabled.ball(
            BallInput(dismissal = DismissalInput(DismissalType.ONE_TIP_ONE_HAND, "A1", "B2")),
            "tip",
        ).view().innings.single()
        assertEquals(1, innings.wickets)
        assertEquals(1, innings.bowlingRows.single().wickets)
        assertTrue(row(innings, "A1").dismissalText.contains("one tip"))
        val overarmBlocked = overarm().tryBall(
            BallInput(dismissal = DismissalInput(DismissalType.ONE_TIP_ONE_HAND, "A1", "B2")),
            "no",
        )
        assertEquals(EngineCode.REJECTED, overarmBlocked.code)
    }

    @Test
    fun maximumRunsPerDeliveryIsRejected() {
        val rules = openDefaults().copy(extras = openDefaults().extras.copy(maxRunsPerDelivery = 4))
        val match = live(rules, overs = 2)
        assertEquals(EngineCode.REJECTED, match.tryBall(BallInput(boundary = BoundaryMarker.SIX), "6").code)
        val innings = match.ball(BallInput(boundary = BoundaryMarker.FOUR), "4").view().innings.single()
        assertEquals(4, innings.runs)
    }

    @Test
    fun multiInningsMatchProgressesToAResult() {
        var match = multi().ball(BallInput(batterRuns = 1), "i1")
        match = match.event(MatchEvent.StartNextInnings("s2", "B1", "B2", "A1")).ball(BallInput(batterRuns = 1), "i2")
        match = match.event(MatchEvent.StartNextInnings("s3", "A1", "A2", "B1")).ball(BallInput(batterRuns = 1), "i3")
        val derived = match.event(MatchEvent.StartNextInnings("s4", "B1", "B2", "A1"))
            .ball(BallInput(), "i4")
            .view()
        assertEquals(4, derived.innings.size)
        assertEquals(2, derived.innings[3].target)
        assertEquals(ResultKind.WIN, derived.result?.kind)
        assertEquals("A", derived.result?.winnerTeamId)
        assertEquals("Team A won by 1 run.", derived.result?.summary)
        assertTrue(MatchInvariants.problems(match.event(MatchEvent.StartNextInnings("s4b", "B1", "B2", "A1")).ball(BallInput(), "i4b")).isEmpty())
    }

    @Test
    fun superOverResolvesATie() {
        val first = chaseMatch(superOver = true).ball(BallInput(), "a")
        val tied = first.event(MatchEvent.StartNextInnings("reg", "B1", "B2", "A1")).ball(BallInput(), "b")
        assertTrue(tied.view().superOverPending)
        val afterFirst = tied.event(MatchEvent.StartSuperOver("so1", "B1", "B2", "A1"))
            .ball(BallInput(batterRuns = 1), "sb")
        val derived = afterFirst.event(MatchEvent.StartSuperOver("so2", "A1", "A2", "B1"))
            .ball(BallInput(), "sa")
            .view()
        assertEquals(ResultKind.WIN, derived.result?.kind)
        assertEquals("B", derived.result?.winnerTeamId)
        assertEquals(true, derived.result?.wonBySuperOver)
        assertEquals("Team B won the Super Over by 1 run.", derived.result?.summary)
    }

    @Test
    fun duplicateDeliveryIsRejected() {
        val ball = MatchEvent.Ball("same", BallInput(batterRuns = 1))
        val once = MatchEngine.append(box(), ball)
        val twice = MatchEngine.append(once.match!!, ball)
        assertEquals(EngineCode.OK, once.code)
        assertEquals(EngineCode.DUPLICATE, twice.code)
        assertEquals(1, once.match.events.size)
        assertEquals(1, MatchEngine.derive(once.match).innings.single().runs)
    }

    @Test
    fun correctionAfterTheInningsChangesTheResult() {
        val first = chaseMatch().ball(BallInput(boundary = BoundaryMarker.FOUR), "first")
        val tied = first.event(MatchEvent.StartNextInnings("next", "B1", "B2", "A1"))
            .ball(BallInput(boundary = BoundaryMarker.FOUR), "second")
        assertEquals(ResultKind.TIE, tied.view().result?.kind)
        val corrected = MatchEngine.replaceEvent(
            tied,
            "first",
            MatchEvent.Ball("first", BallInput(boundary = BoundaryMarker.SIX)),
        )
        assertEquals(EngineCode.OK, corrected.code)
        assertEquals(7, corrected.derived?.innings?.get(1)?.target)
        assertEquals("Team A won by 2 runs.", corrected.derived?.result?.summary)
        assertTrue(MatchInvariants.problems(corrected.match!!).isEmpty())
    }

    @Test
    fun replayingDeliveriesKeepsTheScorecardConsistent() {
        val played = open(overs = 2)
            .ball(BallInput(), "dot")
            .ball(BallInput(batterRuns = 1), "one")
            .ball(BallInput(wide = true), "wide")
            .ball(BallInput(noBall = true, boundary = BoundaryMarker.FOUR), "nb")
            .ball(BallInput(byeRuns = 1), "bye")
            .ball(BallInput(batterRuns = 1, overthrowBatterRuns = 2), "ot")
            .ball(BallInput(dismissal = DismissalInput(DismissalType.BOWLED, "A2")), "w")
        val derived = played.view()
        assertTrue(MatchInvariants.problems(played, derived).isEmpty())
        assertEquals(derived, MatchEngine.derive(played))
        val batterRuns = derived.innings.single().battingRows.sumOf { it.runs }
        val extras = derived.innings.single().extras.total
        assertEquals(derived.innings.single().runs, batterRuns + extras)
        assertEquals(derived.innings.single().runs, derived.innings.single().deliveries.sumOf { it.totalRuns })
    }

    @Test
    fun ruleSnapshotDoesNotFollowALaterEdit() {
        val original = boxDefaults()
        val match = live(original)
        val edited = original.copy(boundaries = original.boundaries.copy(sideWallRuns = 9))
        assertEquals(1, match.rules.boundaries.sideWallRuns)
        assertEquals(9, edited.boundaries.sideWallRuns)
        assertTrue(ruleCatalog().any { it.disposition == RuleDisposition.CONFIRM_WITH_ORGANIZER })
        assertTrue(ruleCatalog().none { it.box.contains("official", ignoreCase = true) })
    }
}

private fun box(overs: Int? = 5, balls: Int = 6, maxBowler: Int? = 2) = live(boxDefaults(), overs, balls, maxBowler)

private fun open(overs: Int? = 5, balls: Int = 6) = live(openDefaults(), overs, balls, maxBowler = 4)

private fun overarm() = live(overarmLimited(20, "T20"), overs = 2, balls = 6, maxBowler = 2)

private fun chaseMatch(superOver: Boolean = false): Match {
    val rules = overarmLimited(1, "One ball").let { base ->
        base.copy(
            tieBreak = base.tieBreak.copy(superOverEnabled = superOver, superOverLegalBalls = 1),
            endings = base.endings.copy(powerplayOvers = null),
        )
    }
    return live(rules, overs = 1, balls = 1, maxBowler = 1, players = 3)
}

private fun multi(): Match {
    val base = overarmMultiInnings()
    val rules = base.copy(
        overs = base.overs.copy(ballsPerOver = 1, oversPerInnings = 1, inningsPerTeam = 2),
        endings = base.endings.copy(declarationsAllowed = false, followOnAllowed = false, followOnDeficit = null),
    )
    return live(rules, overs = 1, balls = 1, maxBowler = null, players = 2, innings = 2)
}

private fun live(
    rules: RulesProfile,
    overs: Int? = rules.overs.oversPerInnings,
    balls: Int = rules.overs.ballsPerOver,
    maxBowler: Int? = rules.overs.maxOversPerBowler,
    players: Int = 3,
    innings: Int = rules.overs.inningsPerTeam,
): Match {
    val namesA = listOf("A1", "A2", "A3").take(players)
    val namesB = listOf("B1", "B2", "B3").take(players)
    val profile = rules.copy(
        overs = rules.overs.copy(oversPerInnings = overs, ballsPerOver = balls, maxOversPerBowler = maxBowler, inningsPerTeam = innings),
    )
    return MatchFactory.create(
        id = "match",
        style = profile.style,
        title = "Test",
        teamA = teamOf("A", "Team A", namesA),
        teamB = teamOf("B", "Team B", namesB),
        rules = profile,
        battingFirstTeamId = "A",
        opening = OpeningPlayers("A1", "A2", "B1"),
    )
}

private fun Match.ball(input: BallInput, id: String) = event(MatchEvent.Ball(id, input))

private fun Match.event(event: MatchEvent): Match {
    val outcome = MatchEngine.append(this, event)
    if (outcome.code != EngineCode.OK || outcome.match == null) {
        error(outcome.message ?: "Scoring action was rejected.")
    }
    return outcome.match
}

private fun Match.tryBall(input: BallInput, id: String) = tryEvent(MatchEvent.Ball(id, input))

private fun Match.tryEvent(event: MatchEvent) = MatchEngine.append(this, event)

private fun Match.view() = MatchEngine.derive(this)

private fun row(innings: com.example.cricketscoring.model.DerivedInnings, id: String) =
    innings.battingRows.first { it.playerId == id }
