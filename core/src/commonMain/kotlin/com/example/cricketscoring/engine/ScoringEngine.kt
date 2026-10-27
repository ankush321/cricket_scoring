package com.example.cricketscoring.engine

import com.example.cricketscoring.model.AppliedDelivery
import com.example.cricketscoring.model.AppliedDismissal
import com.example.cricketscoring.model.BallInput
import com.example.cricketscoring.model.BattingRow
import com.example.cricketscoring.model.BoundaryMarker
import com.example.cricketscoring.model.BowlingRow
import com.example.cricketscoring.model.DerivedInnings
import com.example.cricketscoring.model.DerivedMatch
import com.example.cricketscoring.model.DismissalInput
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.EngineCode
import com.example.cricketscoring.model.EngineOutcome
import com.example.cricketscoring.model.Extras
import com.example.cricketscoring.model.FallOfWicket
import com.example.cricketscoring.model.Match
import com.example.cricketscoring.model.MatchEvent
import com.example.cricketscoring.model.MatchPlayer
import com.example.cricketscoring.model.MatchResult
import com.example.cricketscoring.model.MatchStatus
import com.example.cricketscoring.model.MatchTeam
import com.example.cricketscoring.model.OpeningPlayers
import com.example.cricketscoring.model.OverSummary
import com.example.cricketscoring.model.Partnership
import com.example.cricketscoring.model.ResultKind
import com.example.cricketscoring.model.RosterChangeKind
import com.example.cricketscoring.model.RosterChangeRecord
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.ScoringAwardMode
import com.example.cricketscoring.model.SurfaceContact
import com.example.cricketscoring.model.Toss
import com.example.cricketscoring.rules.withLocalDismissals

class ScoringException(message: String) : IllegalArgumentException(message)

object MatchEngine {
    fun derive(match: Match): DerivedMatch = Replay(match).run()

    fun append(match: Match, event: MatchEvent): EngineOutcome {
        if (match.events.any { it.id == event.id }) {
            return EngineOutcome(EngineCode.DUPLICATE, "This delivery was already recorded.")
        }
        return accept(match.copy(events = match.events + event, updatedAtEpochMs = stamp(event, match.updatedAtEpochMs)))
    }

    fun undo(match: Match): EngineOutcome {
        if (match.events.isEmpty()) return EngineOutcome(EngineCode.REJECTED, "Nothing to undo.")
        return accept(match.copy(events = match.events.dropLast(1)))
    }

    fun replaceEvent(match: Match, eventId: String, event: MatchEvent): EngineOutcome {
        val index = match.events.indexOfFirst { it.id == eventId }
        if (index < 0) return EngineOutcome(EngineCode.REJECTED, "That delivery is not in this match.")
        if (event.id != eventId) return EngineOutcome(EngineCode.REJECTED, "Keep the same delivery id when editing.")
        val events = match.events.toMutableList()
        events[index] = event
        return accept(match.copy(events = events))
    }

    fun begin(
        match: Match,
        battingFirstTeamId: String,
        opening: OpeningPlayers,
        toss: Toss?,
    ): EngineOutcome {
        if (match.events.isNotEmpty()) {
            return EngineOutcome(EngineCode.REJECTED, "The toss can be corrected only before the first ball.")
        }
        val next = match.copy(
            battingFirstTeamId = battingFirstTeamId,
            opening = opening,
            toss = toss,
            cancelled = false,
        )
        return accept(next)
    }

    fun cancel(match: Match): EngineOutcome {
        if (match.events.isNotEmpty() || match.opening != null) {
            return EngineOutcome(EngineCode.REJECTED, "A match that has started must be abandoned, not cancelled.")
        }
        return accept(match.copy(cancelled = true))
    }

    fun preMatchTransfer(
        match: Match,
        playerId: String,
        fromTeamId: String,
        toTeamId: String,
        approvalA: Boolean = false,
        approvalB: Boolean = false,
    ): EngineOutcome {
        if (match.events.isNotEmpty() || match.opening != null) {
            return EngineOutcome(EngineCode.REJECTED, "Pre-match transfers are locked after the match starts.")
        }
        if (!match.rules.roster.preMatchTransfersAllowed) {
            return EngineOutcome(EngineCode.REJECTED, "Pre-match transfers are turned off.")
        }
        if (match.rules.roster.requireBothTeamApproval && (!approvalA || !approvalB)) {
            return EngineOutcome(EngineCode.REJECTED, "Both teams have to approve this transfer.")
        }
        return try {
            accept(movePlayerOnMatch(match, playerId, fromTeamId, toTeamId))
        } catch (error: ScoringException) {
            EngineOutcome(EngineCode.REJECTED, error.message)
        }
    }

    private fun accept(match: Match): EngineOutcome = try {
        EngineOutcome(EngineCode.OK, match = match, derived = derive(match))
    } catch (error: ScoringException) {
        EngineOutcome(EngineCode.REJECTED, error.message)
    }

    private fun stamp(event: MatchEvent, fallback: Long): Long = when (event) {
        is MatchEvent.Ball -> event.input.timestampEpochMs.takeIf { it > 0 } ?: fallback
        else -> fallback
    }
}

fun legalDismissals(
    rules: RulesProfile,
    freeHit: Boolean,
    wide: Boolean,
    noBall: Boolean,
    deadBall: Boolean = false,
): List<DismissalType> {
    val enabled = withLocalDismissals(rules).dismissals.enabled
    if (deadBall) return enabled.filter { it == DismissalType.TIMED_OUT }
    val ballTypes = enabled.filter {
        it != DismissalType.RETIRED_HURT && it != DismissalType.RETIRED_OUT && it != DismissalType.TIMED_OUT
    }
    return ballTypes.filter { type ->
        when {
            freeHit || noBall -> type == DismissalType.RUN_OUT ||
                type == DismissalType.OBSTRUCTING_THE_FIELD ||
                type == DismissalType.HIT_BALL_TWICE
            wide -> type == DismissalType.RUN_OUT ||
                type == DismissalType.STUMPED ||
                type == DismissalType.OBSTRUCTING_THE_FIELD
            else -> true
        }
    }
}

private enum class End { STRIKE, NON_STRIKE }

private class BatterAcc(val id: String) {
    var runs = 0
    var balls = 0
    var fours = 0
    var sixes = 0
    var out = false
    var retiredHurt = false
    var dismissalType: DismissalType? = null
    var fielderId: String? = null
    var bowlerId: String? = null
}

private class BowlerAcc(val id: String) {
    var legalBalls = 0
    var runs = 0
    var wickets = 0
    var wides = 0
    var noBalls = 0
    var maidens = 0
}

private class PartnershipAcc(val batterIds: List<String>) {
    var runs = 0
    var balls = 0
    var startedAt: Long? = null
    var endedAt: Long? = null
}

private class InningsCursor(
    val index: Int,
    val battingTeamId: String,
    val bowlingTeamId: String,
    val superOver: Boolean,
    val target: Int?,
    val maxLegalBalls: Int?,
    var maxWickets: Int,
    val ballsPerOver: Int,
    val battingOrderIds: MutableList<String>,
) {
    var strikerId: String? = null
    var nonStrikerId: String? = null
    var bowlerId: String? = null
    var vacant: End? = null
    var runs = 0
    var wickets = 0
    var legalBalls = 0
    var complete = false
    var declared = false
    var allOut = false
    var overIndex = 0
    var overLegal = 0
    var overSpoilRuns = 0
    val overBowlers = linkedSetOf<String>()
    val batters = linkedMapOf<String, BatterAcc>()
    val batterOrder = mutableListOf<String>()
    val bowlers = linkedMapOf<String, BowlerAcc>()
    val bowlerOrder = mutableListOf<String>()
    val deliveries = mutableListOf<AppliedDelivery>()
    val fall = mutableListOf<FallOfWicket>()
    val closedPartnerships = mutableListOf<PartnershipAcc>()
    var openPartnership: PartnershipAcc? = null
    var extras = Extras()
    var previousOverBowlerId: String? = null
    var lastManStanding = false
    val batLocked = mutableSetOf<String>()
    val bowlLocked = mutableSetOf<String>()
}

private class Replay(val match: Match) {
    val rules = withLocalDismissals(match.rules)
    val innings = mutableListOf<InningsCursor>()
    var current: InningsCursor? = null
    var needsBatter = false
    var needsBowler = false
    var breakPending = false
    var superOverPending = false
    var nextIsSuper = false
    var followOn = false
    var finished = false
    var freeHit = false
    var result: MatchResult? = null
    var playerOfTheMatchId: String? = null
    var status = MatchStatus.LIVE
    val names = mutableMapOf<String, String>()
    val teamAIds = match.teamA.players.map { it.id }.toMutableList()
    val teamBIds = match.teamB.players.map { it.id }.toMutableList()
    val rosterChanges = mutableListOf<RosterChangeRecord>()
    val switchedAway = mutableSetOf<String>()

    fun run(): DerivedMatch {
        if (match.events.map { it.id }.distinct().size != match.events.size) {
            throw ScoringException("This delivery was already recorded.")
        }
        if (match.cancelled && match.opening == null) return shell(MatchStatus.CANCELLED)
        (match.teamA.players + match.teamB.players).forEach { names[it.id] = it.name }
        val opening = match.opening
        val battingFirst = match.battingFirstTeamId
        if (opening == null || battingFirst == null) return shell(MatchStatus.UPCOMING)
        validateOpening(battingFirst, opening)
        startInnings(
            battingTeamId = battingFirst,
            strikerId = opening.strikerId,
            nonStrikerId = opening.nonStrikerId,
            bowlerId = opening.bowlerId,
            superOver = false,
        )
        match.events.forEachIndexed { index, event ->
            try {
                apply(event)
            } catch (error: ScoringException) {
                throw ScoringException("Event ${index + 1}: ${error.message}")
            }
        }
        return freeze()
    }

    private fun apply(event: MatchEvent) {
        when (event) {
            is MatchEvent.Ball -> applyBall(event)
            is MatchEvent.NextBatter -> applyNextBatter(event.playerId)
            is MatchEvent.NextBowler -> applyNextBowler(event.playerId)
            is MatchEvent.CloseInnings -> closeInnings(event.reason)
            is MatchEvent.Abandon -> abandon(event.reason)
            is MatchEvent.Concede -> concede(event.teamId)
            is MatchEvent.StartNextInnings -> startFromBreak(event.strikerId, event.nonStrikerId, event.bowlerId)
            is MatchEvent.StartSuperOver -> {
                if (!superOverPending && !nextIsSuper) throw ScoringException("A Super Over is not waiting to start.")
                startFromBreak(event.strikerId, event.nonStrikerId, event.bowlerId)
            }
            is MatchEvent.EnforceFollowOn -> enforceFollowOn()
            is MatchEvent.RetireBatter -> retire(event.playerId, event.hurt)
            is MatchEvent.PlayerOfTheMatch -> award(event.playerId)
            is MatchEvent.SwitchPlayer -> applySwitch(event)
            is MatchEvent.AddPlayer -> applyAdd(event)
            is MatchEvent.LendPlayer -> applyLoan(event)
        }
    }

    private fun applyBall(event: MatchEvent.Ball) {
        val innings = current ?: throw ScoringException("The match has not started.")
        if (finished || innings.complete) throw ScoringException("This innings is already over.")
        if (needsBatter) throw ScoringException("Choose the next batter before the next ball.")
        if (needsBowler) throw ScoringException("Choose the next ${rules.labels.bowlingRole.lowercase()} before the next ball.")
        val input = event.input
        if (input.batterRuns < 0 || input.wideRunningRuns < 0 || input.byeRuns < 0 || input.legByeRuns < 0 ||
            input.overthrowBatterRuns < 0 || input.overthrowExtraRuns < 0 || input.penaltyRuns < 0
        ) {
            throw ScoringException("Runs cannot be negative.")
        }
        if (input.wide && input.noBall) throw ScoringException("A ball cannot be a wide and a no-ball.")
        if (input.wide && !rules.extras.widesEnabled) throw ScoringException("Wides are turned off for this match.")
        if (input.noBall && !rules.extras.noBallsEnabled) throw ScoringException("No-balls are turned off for this match.")
        if (input.byeRuns > 0 && !rules.extras.byesEnabled) throw ScoringException("Byes are turned off for this match.")
        if (input.legByeRuns > 0 && !rules.extras.legByesEnabled) throw ScoringException("Leg-byes are turned off for this match.")
        if (input.penaltyRuns > 0 && !rules.extras.penaltyRunsEnabled) throw ScoringException("Penalty runs are turned off for this match.")
        if ((input.overthrowBatterRuns > 0 || input.overthrowExtraRuns > 0) && !rules.extras.overthrowsEnabled) {
            throw ScoringException("Overthrows are turned off for this match.")
        }
        if (input.byeRuns > 0 && input.legByeRuns > 0) throw ScoringException("A ball cannot be both a bye and a leg-bye.")
        if (rules.overs.requireUnderarmConfirm && !input.underarmConfirmed && !input.deadBall) {
            throw ScoringException("Confirm that this was an underarm delivery.")
        }

        var striker = innings.strikerId ?: throw ScoringException("There is no striker.")
        if (rules.squad.twoBatters && !innings.lastManStanding && innings.nonStrikerId == null) {
            throw ScoringException("There is no non-striker.")
        }
        if (input.strikerOverrideId != null && input.strikerOverrideId != striker) {
            if (input.strikerOverrideId != innings.nonStrikerId) {
                throw ScoringException("The corrected batter has to be one of the two at the crease.")
            }
            swap(innings)
            striker = innings.strikerId ?: throw ScoringException("There is no striker.")
        }
        var bowler = innings.bowlerId ?: throw ScoringException("There is no ${rules.labels.bowlingRole.lowercase()}.")
        if (input.bowlerOverrideId != null) {
            requireBowler(input.bowlerOverrideId, innings.bowlingTeamId, ignoreChangeRule = true)
            bowler = input.bowlerOverrideId
            innings.bowlerId = bowler
        }

        val roofDead = input.surface == SurfaceContact.ROOF && rules.boundaries.roofIsDeadBall
        val dead = input.deadBall || roofDead
        if (dead && !roofDead && (input.batterRuns > 0 || input.boundary != null || input.wide || input.noBall || input.byeRuns > 0 || input.legByeRuns > 0)) {
            throw ScoringException("A dead ball cannot also be scored.")
        }
        if (input.wide && (input.batterRuns > 0 || input.boundary != null || input.byeRuns > 0 || input.legByeRuns > 0 || input.overthrowBatterRuns > 0)) {
            throw ScoringException("Runs on a wide are running extras, not bat runs.")
        }
        if ((input.byeRuns > 0 || input.legByeRuns > 0) && (input.batterRuns > 0 || input.boundary != null || input.overthrowBatterRuns > 0)) {
            throw ScoringException("Byes cannot be combined with runs off the bat.")
        }

        var batterRuns = 0
        var boundary = input.boundary
        var surface = input.surface
        var scoringName: String? = null
        if (!dead) {
            if (input.boundary != null) {
                if (input.boundary == BoundaryMarker.FOUR && !rules.boundaries.foursEnabled) throw ScoringException("Fours are turned off.")
                if (input.boundary == BoundaryMarker.SIX && !rules.boundaries.sixesEnabled) throw ScoringException("Sixes are turned off.")
                val afterWall = rules.boundaries.afterWallBoundaryRuns
                batterRuns = if (afterWall != null && input.surface.isWall()) {
                    afterWall
                } else if (input.boundary == BoundaryMarker.FOUR) {
                    rules.boundaries.fourRuns
                } else {
                    rules.boundaries.sixRuns
                }
            } else if (input.surface.isDirectScore()) {
                batterRuns = configuredSurface(input.surface)
                    ?: throw ScoringException("${input.surface.label()} scoring is not set. Confirm it with the organizer before using it.")
            } else if (input.surface == SurfaceContact.DESIGNATED_ZONE) {
                val bonus = rules.boundaries.zoneBonusRuns
                    ?: throw ScoringException("Zone bonus runs are not set for this match.")
                batterRuns = input.batterRuns + bonus
            } else {
                batterRuns = input.batterRuns
            }
            val scoring = input.scoringEventId?.let { id ->
                rules.additionalScoring.events.find { it.id == id }
                    ?: throw ScoringException("That scoring event is not in this match.")
            }
            if (scoring != null) {
                if (!scoring.enabled) throw ScoringException("${scoring.name} is turned off for this match.")
                scoringName = scoring.name
                batterRuns = when (scoring.awardMode) {
                    ScoringAwardMode.FIXED -> scoring.runs
                    ScoringAwardMode.BONUS -> batterRuns + scoring.runs
                }
            }
            batterRuns += input.overthrowBatterRuns
            val alone = innings.lastManStanding && innings.nonStrikerId == null
            if (alone && !rules.lastMan.allowScoring && batterRuns > 0) {
                throw ScoringException("The last batter is not allowed to score runs under these rules.")
            }
            if (alone && !rules.lastMan.allowRunning) {
                val runningAlone = (if (input.boundary == null && !input.surface.isDirectScore() && scoring == null) input.batterRuns else 0) +
                    input.byeRuns + input.legByeRuns + input.wideRunningRuns + input.overthrowBatterRuns + input.overthrowExtraRuns
                if (runningAlone > 0) throw ScoringException("The last batter is not allowed to run under these rules.")
            }
            if (!rules.boundaries.runningBetweenWicketsEnabled) {
                val running = (if (input.boundary == null && !input.surface.isDirectScore()) input.batterRuns else 0) +
                    input.byeRuns + input.legByeRuns + input.wideRunningRuns + input.overthrowBatterRuns + input.overthrowExtraRuns +
                    if (input.surface == SurfaceContact.DESIGNATED_ZONE) input.batterRuns else 0
                if (running > 0) throw ScoringException("Running between the wickets is turned off for this match.")
            }
        }

        val dismissal = input.dismissal?.let { resolveDismissal(it, input, striker, innings, dead, bowler) }
        if (dismissal != null && dismissal.dismissalType in RUNS_CANCELLED) {
            batterRuns = 0
            boundary = null
        }

        if (input.noBall && !dead && batterRuns > 0 && !rules.extras.noBallCreditsBatter) {
            // Informal convention: bat contact on a no-ball is extra, not batter runs.
        }
        var creditedBatterRuns = batterRuns
        var noBallExtra = if (!dead && input.noBall) rules.extras.noBallRuns else 0
        if (input.noBall && !dead && batterRuns > 0 && !rules.extras.noBallCreditsBatter) {
            noBallExtra += batterRuns
            creditedBatterRuns = 0
        }
        val ballExtras = Extras(
            wides = if (!dead && input.wide) rules.extras.wideRuns + input.wideRunningRuns else 0,
            noBalls = noBallExtra,
            byes = if (dead) 0 else input.byeRuns,
            legByes = if (dead) 0 else input.legByeRuns,
            penalties = input.penaltyRuns,
            overthrows = if (dead) 0 else input.overthrowExtraRuns,
        )
        val total = creditedBatterRuns + ballExtras.total
        batterRuns = creditedBatterRuns
        val cap = rules.extras.maxRunsPerDelivery
        if (cap != null && total > cap) throw ScoringException("This match caps a delivery at $cap runs.")

        val legal = !dead && (!input.wide || rules.extras.wideCountsAsLegal) && (!input.noBall || rules.extras.noBallCountsAsLegal)
        val maxBowler = rules.overs.maxOversPerBowler
        val bowlerAcc = touchBowler(innings, bowler)
        if (legal && maxBowler != null && bowlerAcc.legalBalls >= maxBowler * rules.overs.ballsPerOver) {
            throw ScoringException("${name(bowler)} has already bowled the maximum overs.")
        }
        val faced = !dead && !input.wide && (!input.noBall || rules.extras.noBallCountsAsBallFaced)
        val wasFreeHit = freeHit
        val nonStrikerAtDelivery = innings.nonStrikerId

        val batter = touchBatter(innings, striker)
        if (faced) batter.balls += 1
        batter.runs += batterRuns
        if (boundary == BoundaryMarker.FOUR) batter.fours += 1
        if (boundary == BoundaryMarker.SIX) batter.sixes += 1
        if (legal) {
            bowlerAcc.legalBalls += 1
            innings.legalBalls += 1
            innings.overLegal += 1
        }
        if (input.wide && !dead) bowlerAcc.wides += 1
        if (input.noBall && !dead) bowlerAcc.noBalls += 1
        val chargedToBowler = batterRuns + ballExtras.wides + ballExtras.noBalls
        bowlerAcc.runs += chargedToBowler
        innings.runs += total
        innings.extras += ballExtras
        innings.overSpoilRuns += total
        innings.overBowlers += bowler
        addToPartnership(innings, total, legal, input.timestampEpochMs)

        if (dismissal != null) {
            val victim = touchBatter(innings, dismissal.batterId)
            victim.dismissalType = dismissal.dismissalType
            victim.fielderId = dismissal.fielderId
            victim.bowlerId = bowler
            if (dismissal.isWicket) {
                victim.out = true
                victim.retiredHurt = false
                innings.wickets += 1
                if (dismissal.bowlerCredited) bowlerAcc.wickets += 1
                innings.fall += FallOfWicket(
                    wicketNumber = innings.wickets,
                    teamScore = innings.runs,
                    batterId = dismissal.batterId,
                    batterName = name(dismissal.batterId),
                    overLabel = fallOver(innings.legalBalls, innings.ballsPerOver),
                    dismissalType = dismissal.dismissalType,
                )
            }
            closePartnership(innings, input.timestampEpochMs)
        }

        val crossing = batterRuns + ballExtras.byes + ballExtras.legByes +
            (if (input.wide) input.wideRunningRuns else 0) + ballExtras.overthrows
        if (crossing % 2 == 1 && rules.strike.rotateOnOddRuns && rules.squad.twoBatters) {
            swap(innings)
        }
        if (dismissal != null) {
            val end = when (dismissal.batterId) {
                innings.strikerId -> End.STRIKE
                innings.nonStrikerId -> End.NON_STRIKE
                else -> throw ScoringException("The dismissed batter is not at the crease.")
            }
            if (end == End.STRIKE) innings.strikerId = null else innings.nonStrikerId = null
            innings.vacant = end
        }

        val overCompleted = legal && innings.overLegal == innings.ballsPerOver
        if (overCompleted) {
            if (innings.overBowlers.size == 1 && innings.overSpoilRuns == 0) {
                innings.bowlers.getValue(innings.overBowlers.first()).maidens += 1
            }
            innings.previousOverBowlerId = bowler
            innings.overLegal = 0
            innings.overSpoilRuns = 0
            innings.overBowlers.clear()
        }

        refreshLastMan(innings)
        val target = innings.target
        val maxBalls = innings.maxLegalBalls
        val chased = rules.endings.endWhenTargetReached && target != null && innings.runs >= target
        val eligible = lastManEligible(innings.battingTeamId)
        val wicketsEnd = rules.endings.endWhenAllOut && innings.wickets >= innings.maxWickets &&
            (!eligible || rules.lastMan.endOnDismissal)
        val stranded = availableBatters(innings).isEmpty() && innings.strikerId == null
        val lastManEnds = eligible && stranded &&
            (rules.lastMan.endOnDismissal || !rules.lastMan.continueAfterAnotherDismissal || returnableBatters(innings).isEmpty())
        val allOut = wicketsEnd || lastManEnds
        val oversDone = rules.endings.endWhenOversComplete && maxBalls != null && innings.legalBalls >= maxBalls
        val inningsDone = chased || allOut || oversDone
        if (!inningsDone && overCompleted && rules.strike.rotateAtEndOfOver && rules.squad.twoBatters) {
            swap(innings)
        }

        innings.deliveries += AppliedDelivery(
            eventId = event.id,
            inningsIndex = innings.index,
            overIndex = innings.overIndex,
            batterId = striker,
            nonStrikerId = nonStrikerAtDelivery,
            bowlerId = bowler,
            batterRuns = batterRuns,
            extras = ballExtras,
            totalRuns = total,
            legal = legal,
            countsAsBallFaced = faced,
            dismissal = dismissal,
            symbol = symbol(input, dead, batterRuns, ballExtras, boundary, dismissal),
            commentary = input.commentary ?: scoringName?.let { "$it, $batterRuns runs" },
            timestampEpochMs = input.timestampEpochMs,
            surface = surface,
            boundary = boundary,
            wasFreeHit = wasFreeHit,
            scoreAfter = innings.runs,
            wicketsAfter = innings.wickets,
            legalBallsAfter = innings.legalBalls,
            scoringEventId = input.scoringEventId,
            scoringEventName = scoringName,
        )
        if (overCompleted) {
            innings.overIndex += 1
            innings.batLocked.clear()
            innings.bowlLocked.clear()
        }

        if (inningsDone) {
            innings.complete = true
            innings.allOut = innings.wickets >= innings.maxWickets
            needsBatter = false
            needsBowler = false
            freeHit = false
            finishInnings()
        } else {
            needsBatter = innings.vacant != null
            needsBowler = overCompleted
            freeHit = when {
                input.noBall && rules.extras.freeHitEnabled -> true
                wasFreeHit && input.wide && rules.extras.freeHitEnabled -> true
                else -> false
            }
        }
    }

    private fun resolveDismissal(
        input: DismissalInput,
        ball: BallInput,
        striker: String,
        innings: InningsCursor,
        dead: Boolean,
        bowler: String,
    ): AppliedDismissal {
        val type = input.dismissalType
        if (type == DismissalType.RETIRED_HURT || type == DismissalType.RETIRED_OUT) {
            throw ScoringException("Use retire to record a retired batter. It does not use up a ball.")
        }
        if (type !in rules.dismissals.enabled) throw ScoringException("${type.label()} is turned off for this match.")
        if (dead && type != DismissalType.TIMED_OUT) throw ScoringException("Only timed out can be recorded on a dead ball.")
        if (!dead && type == DismissalType.TIMED_OUT) throw ScoringException("Timed out is recorded as a dead ball.")
        val allowed = legalDismissals(rules, freeHit, ball.wide, ball.noBall, dead)
        if (type !in allowed) {
            throw ScoringException("${type.label()} is not allowed on this delivery.")
        }
        val occupants = listOfNotNull(innings.strikerId, innings.nonStrikerId)
        if (input.batterId !in occupants) throw ScoringException("The dismissed batter is not at the crease.")
        if (type in rules.dismissals.strikerOnly && input.batterId != striker) {
            throw ScoringException("${type.label()} applies to the striker.")
        }
        if (type in rules.dismissals.fielderRequired && input.fielderId.isNullOrBlank()) {
            throw ScoringException("${type.label()} needs the fielder involved.")
        }
        if (input.fielderId != null && input.fielderId == input.batterId) {
            throw ScoringException("The fielder and the dismissed batter must be different people.")
        }
        val wallCatch = type == DismissalType.CAUGHT || type == DismissalType.ONE_TIP_ONE_HAND || type == DismissalType.SINGLE_BOUNCE
        if (wallCatch && ball.surface.isWall() && !rules.dismissals.catchOffWallAllowed) {
            throw ScoringException("A catch off the wall is turned off. Confirm it with the organizer before using it.")
        }
        val credited = type in rules.dismissals.bowlerCredited
        return AppliedDismissal(type, input.batterId, input.fielderId, credited, isWicket = true).also {
            if (credited && bowler.isBlank()) throw ScoringException("A bowler is required.")
        }
    }

    private fun applyNextBatter(playerId: String) {
        val innings = current ?: throw ScoringException("The match has not started.")
        if (!needsBatter || innings.vacant == null) throw ScoringException("A new batter is not required.")
        if (playerId !in innings.battingOrderIds) throw ScoringException("That player is not in the batting side.")
        if (playerId in innings.batLocked) throw ScoringException("${name(playerId)} cannot bat until this over ends.")
        val acc = innings.batters[playerId]
        if (acc?.out == true) throw ScoringException("${name(playerId)} is already out.")
        if (playerId == innings.strikerId || playerId == innings.nonStrikerId) {
            throw ScoringException("${name(playerId)} is already at the crease.")
        }
        when (innings.vacant) {
            End.STRIKE -> innings.strikerId = playerId
            End.NON_STRIKE -> innings.nonStrikerId = playerId
            null -> throw ScoringException("A new batter is not required.")
        }
        innings.vacant = null
        val returning = touchBatter(innings, playerId)
        returning.retiredHurt = false
        needsBatter = false
        openPartnership(innings)
    }

    private fun applyNextBowler(playerId: String) {
        val innings = current ?: throw ScoringException("The match has not started.")
        if (!needsBowler) throw ScoringException("A new ${rules.labels.bowlingRole.lowercase()} is not required.")
        requireBowler(playerId, innings.bowlingTeamId, ignoreChangeRule = false)
        innings.bowlerId = playerId
        touchBowler(innings, playerId)
        needsBowler = false
    }

    private fun requireBowler(playerId: String, bowlingTeamId: String, ignoreChangeRule: Boolean) {
        if (playerId !in squadIds(bowlingTeamId)) {
            throw ScoringException("That ${rules.labels.bowlingRole.lowercase()} is not on the bowling side.")
        }
        if (playerId in (current?.bowlLocked ?: emptySet())) {
            throw ScoringException("${name(playerId)} cannot bowl until this over ends.")
        }
        val innings = current ?: return
        if (!ignoreChangeRule && rules.overs.mustChangeBowlerEachOver && playerId == innings.previousOverBowlerId) {
            throw ScoringException("This match requires a different ${rules.labels.bowlingRole.lowercase()} each over.")
        }
        val cap = rules.overs.maxOversPerBowler
        val already = innings.bowlers[playerId]?.legalBalls ?: 0
        if (cap != null && already >= cap * rules.overs.ballsPerOver) {
            throw ScoringException("${name(playerId)} has already bowled the maximum overs.")
        }
    }

    private fun bowlingOptionsExhausted(innings: InningsCursor): Boolean {
        val cap = rules.overs.maxOversPerBowler ?: return false
        val limit = cap * rules.overs.ballsPerOver
        return squadIds(innings.bowlingTeamId).all { (innings.bowlers[it]?.legalBalls ?: 0) >= limit }
    }

    private fun closeInnings(reason: String) {
        val innings = current ?: throw ScoringException("The match has not started.")
        if (innings.complete || finished) throw ScoringException("This innings is already over.")
        if (needsBatter) throw ScoringException("Send the next batter in, or undo the wicket, before ending the innings.")
        if (!rules.endings.declarationsAllowed && !bowlingOptionsExhausted(innings)) {
            throw ScoringException("This match does not allow the innings to be closed early.")
        }
        innings.declared = true
        innings.complete = true
        closePartnership(innings, null)
        needsBatter = false
        needsBowler = false
        freeHit = false
        if (reason.isBlank()) throw ScoringException("Add a reason for ending the innings.")
        finishInnings()
    }

    private fun retire(playerId: String, hurt: Boolean) {
        val innings = current ?: throw ScoringException("The match has not started.")
        if (innings.complete || finished) throw ScoringException("This innings is already over.")
        if (needsBowler) throw ScoringException("Choose the bowler before retiring a batter.")
        val allowed = if (hurt) DismissalType.RETIRED_HURT else DismissalType.RETIRED_OUT
        if (allowed !in rules.dismissals.enabled) throw ScoringException("${allowed.label()} is turned off for this match.")
        if (playerId != innings.strikerId && playerId != innings.nonStrikerId) {
            throw ScoringException("Only a batter at the crease can retire.")
        }
        val acc = touchBatter(innings, playerId)
        acc.dismissalType = allowed
        if (hurt) {
            acc.retiredHurt = true
            acc.out = false
        } else {
            acc.out = true
            acc.retiredHurt = false
            innings.wickets += 1
            innings.fall += FallOfWicket(
                wicketNumber = innings.wickets,
                teamScore = innings.runs,
                batterId = playerId,
                batterName = name(playerId),
                overLabel = fallOver(innings.legalBalls, innings.ballsPerOver),
                dismissalType = allowed,
            )
        }
        innings.vacant = if (playerId == innings.strikerId) End.STRIKE else End.NON_STRIKE
        if (playerId == innings.strikerId) innings.strikerId = null else innings.nonStrikerId = null
        closePartnership(innings, null)
        val allOut = !hurt && rules.endings.endWhenAllOut && innings.wickets >= innings.maxWickets
        if (allOut) {
            innings.complete = true
            innings.allOut = true
            needsBatter = false
            finishInnings()
        } else {
            needsBatter = true
        }
    }

    private fun abandon(reason: String) {
        if (finished) throw ScoringException("The match is already over.")
        if (reason.isBlank()) throw ScoringException("Add a reason for abandoning the match.")
        finished = true
        status = MatchStatus.ABANDONED
        current?.let {
            if (!it.complete) {
                it.complete = true
                closePartnership(it, null)
            }
        }
        needsBatter = false
        needsBowler = false
        breakPending = false
        superOverPending = false
        result = MatchResult(
            winnerTeamId = null,
            kind = ResultKind.ABANDONED,
            summary = "No result. ${reason.trim()}",
        )
    }

    private fun concede(teamId: String) {
        if (finished) throw ScoringException("The match is already over.")
        if (teamId != match.teamA.teamId && teamId != match.teamB.teamId) throw ScoringException("Unknown team.")
        val winner = other(teamId)
        finished = true
        status = MatchStatus.COMPLETED
        current?.let {
            if (!it.complete) {
                it.complete = true
                closePartnership(it, null)
            }
        }
        needsBatter = false
        needsBowler = false
        breakPending = false
        superOverPending = false
        result = MatchResult(
            winnerTeamId = winner,
            kind = ResultKind.WIN,
            summary = "${teamName(winner)} won by concession.",
        )
    }

    private fun enforceFollowOn() {
        if (!breakPending) throw ScoringException("Follow-on can be enforced only during the innings break.")
        if (!followOnAvailableNow()) throw ScoringException("Follow-on is not available.")
        followOn = true
    }

    private fun award(playerId: String) {
        if (!(finished && status == MatchStatus.COMPLETED)) {
            throw ScoringException("Player of the match is chosen after the match is complete.")
        }
        if (playerId !in squadIds(match.teamA.teamId) && playerId !in squadIds(match.teamB.teamId)) {
            throw ScoringException("Player of the match must be in one of the squads.")
        }
        playerOfTheMatchId = playerId
    }

    private fun startFromBreak(strikerId: String, nonStrikerId: String?, bowlerId: String) {
        if (finished) throw ScoringException("The match is already over.")
        if (!breakPending && !superOverPending) throw ScoringException("The next innings is not ready to start.")
        val batting = nextBattingTeam()
        startInnings(batting, strikerId, nonStrikerId, bowlerId, nextIsSuper)
        breakPending = false
        superOverPending = false
        status = MatchStatus.LIVE
    }

    private fun startInnings(
        battingTeamId: String,
        strikerId: String,
        nonStrikerId: String?,
        bowlerId: String,
        superOver: Boolean,
    ) {
        if (finished) throw ScoringException("The match is already over.")
        val bowlingTeamId = other(battingTeamId)
        if (strikerId !in squadIds(battingTeamId)) throw ScoringException("The striker is not in the batting side.")
        if (rules.squad.twoBatters) {
            if (nonStrikerId == null) throw ScoringException("Choose a non-striker.")
            if (nonStrikerId !in squadIds(battingTeamId)) throw ScoringException("The non-striker is not in the batting side.")
            if (nonStrikerId == strikerId) throw ScoringException("The striker and non-striker must be different.")
        } else if (nonStrikerId != null) {
            throw ScoringException("This match uses one batter at a time.")
        }
        if (bowlerId !in squadIds(bowlingTeamId)) throw ScoringException("The opening ${rules.labels.bowlingRole.lowercase()} is not on the bowling side.")
        val maxBalls = if (superOver) {
            rules.tieBreak.superOverLegalBalls
        } else {
            rules.overs.oversPerInnings?.times(rules.overs.ballsPerOver)
        }
        val squadSize = squadIds(battingTeamId).size
        val maxWickets = wicketsLimit(squadSize, superOver)
        val target = targetFor(battingTeamId, superOver)
        val cursor = InningsCursor(
            index = innings.size,
            battingTeamId = battingTeamId,
            bowlingTeamId = bowlingTeamId,
            superOver = superOver,
            target = target,
            maxLegalBalls = maxBalls,
            maxWickets = maxWickets,
            ballsPerOver = rules.overs.ballsPerOver,
            battingOrderIds = squadIds(battingTeamId).toMutableList(),
        )
        cursor.strikerId = strikerId
        cursor.nonStrikerId = nonStrikerId
        cursor.bowlerId = bowlerId
        touchBatter(cursor, strikerId)
        if (nonStrikerId != null) touchBatter(cursor, nonStrikerId)
        touchBowler(cursor, bowlerId)
        openPartnership(cursor)
        innings += cursor
        current = cursor
        needsBatter = false
        needsBowler = false
        freeHit = false
    }

    private fun targetFor(battingTeamId: String, superOver: Boolean): Int? {
        if (superOver) {
            val previous = innings.lastOrNull { it.superOver && it.complete } ?: return null
            return previous.runs + 1
        }
        if (rules.overs.inningsPerTeam == 1) {
            val first = innings.firstOrNull { !it.superOver && it.complete } ?: return null
            return first.runs + 1
        }
        val completed = innings.count { !it.superOver && it.complete }
        if (completed != 3) return null
        val own = innings.filter { !it.superOver && it.battingTeamId == battingTeamId }.sumOf { it.runs }
        val opponent = innings.filter { !it.superOver && it.battingTeamId != battingTeamId }.sumOf { it.runs }
        return opponent - own + 1
    }

    private fun finishInnings() {
        val inningsNow = current ?: return
        closePartnership(inningsNow, null)
        if (inningsNow.superOver) {
            val done = innings.count { it.superOver && it.complete }
            if (done < 2) {
                breakPending = true
                nextIsSuper = true
                status = MatchStatus.INNINGS_BREAK
                return
            }
            finishSuperOver()
            return
        }
        if (inningsNow.target != null) {
            finishChase(inningsNow)
            return
        }
        if (maybeInningsWin()) return
        val planned = rules.overs.inningsPerTeam * 2
        val done = innings.count { !it.superOver && it.complete }
        if (done >= planned) {
            finishByTotals()
            return
        }
        breakPending = true
        nextIsSuper = false
        status = MatchStatus.INNINGS_BREAK
    }

    private fun finishChase(inningsNow: InningsCursor) {
        val target = inningsNow.target ?: return
        val score = inningsNow.runs
        val batting = inningsNow.battingTeamId
        val bowling = inningsNow.bowlingTeamId
        when {
            score >= target -> finishWin(
                winner = batting,
                marginRuns = null,
                marginWickets = (inningsNow.maxWickets - inningsNow.wickets).coerceAtLeast(0),
                summary = "${teamName(batting)} won by ${wicketsPhrase((inningsNow.maxWickets - inningsNow.wickets).coerceAtLeast(0))}.",
            )
            score == target - 1 && rules.tieBreak.superOverEnabled && rules.overs.inningsPerTeam == 1 && innings.none { it.superOver } -> {
                superOverPending = true
                nextIsSuper = true
                breakPending = false
                status = MatchStatus.SUPER_OVER
                result = null
                finished = false
            }
            score == target - 1 -> finishTie("Match tied.")
            else -> {
                val margin = target - 1 - score
                finishWin(
                    winner = bowling,
                    marginRuns = margin,
                    marginWickets = null,
                    summary = "${teamName(bowling)} won by ${runsPhrase(margin)}.",
                )
            }
        }
    }

    private fun finishSuperOver() {
        val supers = innings.filter { it.superOver }
        val first = supers.getOrNull(0) ?: return
        val second = supers.getOrNull(1) ?: return
        val target = second.target ?: (first.runs + 1)
        when {
            second.runs >= target -> {
                val wickets = (second.maxWickets - second.wickets).coerceAtLeast(0)
                finished = true
                status = MatchStatus.COMPLETED
                result = MatchResult(
                    winnerTeamId = second.battingTeamId,
                    kind = ResultKind.WIN,
                    marginWickets = wickets,
                    wonBySuperOver = true,
                    summary = "${teamName(second.battingTeamId)} won the Super Over by ${wicketsPhrase(wickets)}.",
                )
            }
            second.runs == target - 1 -> finishTie("Match tied after the Super Over.")
            else -> {
                val margin = target - 1 - second.runs
                finished = true
                status = MatchStatus.COMPLETED
                result = MatchResult(
                    winnerTeamId = first.battingTeamId,
                    kind = ResultKind.WIN,
                    marginRuns = margin,
                    wonBySuperOver = true,
                    summary = "${teamName(first.battingTeamId)} won the Super Over by ${runsPhrase(margin)}.",
                )
            }
        }
    }

    private fun maybeInningsWin(): Boolean {
        val perTeam = rules.overs.inningsPerTeam
        if (perTeam < 2) return false
        val first = match.battingFirstTeamId ?: return false
        val second = other(first)
        val firstInnings = regulationInnings(first)
        val secondInnings = regulationInnings(second)
        val firstRuns = regulationRuns(first)
        val secondRuns = regulationRuns(second)
        if (firstInnings >= perTeam && firstRuns < secondRuns && secondInnings < perTeam) {
            val margin = secondRuns - firstRuns
            finishWin(
                winner = second,
                marginRuns = margin,
                marginWickets = null,
                summary = "${teamName(second)} won by an innings and ${runsPhrase(margin)}.",
                byInnings = true,
            )
            return true
        }
        if (secondInnings >= perTeam && secondRuns < firstRuns && firstInnings < perTeam) {
            val margin = firstRuns - secondRuns
            finishWin(
                winner = first,
                marginRuns = margin,
                marginWickets = null,
                summary = "${teamName(first)} won by an innings and ${runsPhrase(margin)}.",
                byInnings = true,
            )
            return true
        }
        return false
    }

    private fun finishByTotals() {
        val first = match.battingFirstTeamId ?: return
        val second = other(first)
        val a = regulationRuns(first)
        val b = regulationRuns(second)
        when {
            a > b -> finishWin(first, a - b, null, "${teamName(first)} won by ${runsPhrase(a - b)}.")
            b > a -> finishWin(second, b - a, null, "${teamName(second)} won by ${runsPhrase(b - a)}.")
            else -> finishTie("Match tied.")
        }
    }

    private fun finishWin(
        winner: String,
        marginRuns: Int?,
        marginWickets: Int?,
        summary: String,
        byInnings: Boolean = false,
    ) {
        finished = true
        status = MatchStatus.COMPLETED
        breakPending = false
        superOverPending = false
        result = MatchResult(
            winnerTeamId = winner,
            kind = ResultKind.WIN,
            marginRuns = marginRuns,
            marginWickets = marginWickets,
            wonByInnings = byInnings,
            summary = summary,
        )
    }

    private fun finishTie(summary: String) {
        finished = true
        status = MatchStatus.COMPLETED
        breakPending = false
        superOverPending = false
        result = MatchResult(winnerTeamId = null, kind = ResultKind.TIE, summary = summary)
    }

    private fun nextBattingTeam(): String {
        val first = match.battingFirstTeamId ?: throw ScoringException("Choose who bats first.")
        val second = other(first)
        if (nextIsSuper) {
            val done = innings.count { it.superOver && it.complete }
            return if (done == 0) second else first
        }
        val done = innings.count { !it.superOver && it.complete }
        if (followOn && done == 2) return second
        if (followOn && done == 3) return first
        return if (done % 2 == 0) first else second
    }

    private fun followOnAvailableNow(): Boolean {
        if (followOn || !rules.endings.followOnAllowed || !breakPending || nextIsSuper) return false
        val deficitNeeded = rules.endings.followOnDeficit ?: return false
        val first = match.battingFirstTeamId ?: return false
        if (regulationInnings(first) != 1 || regulationInnings(other(first)) != 1) return false
        return regulationRuns(first) - regulationRuns(other(first)) >= deficitNeeded
    }

    private fun regulationRuns(teamId: String) = innings.filter { !it.superOver && it.battingTeamId == teamId }.sumOf { it.runs }
    private fun regulationInnings(teamId: String) = innings.count { !it.superOver && it.complete && it.battingTeamId == teamId }

    private fun freeze(): DerivedMatch {
        val derivedInnings = innings.map { freezeInnings(it) }
        val index = current?.index
        val active = index?.let { derivedInnings.getOrNull(it) }
        val equation = equation(active)
        val runRate = active?.let { rate(it.runs, it.legalBalls, it.ballsPerOver) }
        val required = active?.let { requiredRate(it) }
        return DerivedMatch(
            status = if (finished) status else if (superOverPending) MatchStatus.SUPER_OVER else if (breakPending) MatchStatus.INNINGS_BREAK else MatchStatus.LIVE,
            innings = derivedInnings,
            currentInningsIndex = index,
            result = result,
            needsBatter = needsBatter && !finished,
            needsBowler = needsBowler && !finished,
            breakPending = breakPending && !finished,
            superOverPending = superOverPending && !finished,
            followOnAvailable = followOnAvailableNow() && !finished,
            freeHit = freeHit && !finished,
            playerOfTheMatchId = playerOfTheMatchId,
            equation = equation,
            currentRunRate = runRate,
            requiredRunRate = required,
            nextBattingTeamId = if ((breakPending || superOverPending) && !finished) nextBattingTeam() else null,
            lastManStanding = current?.lastManStanding == true && !finished,
            liveTeamA = teamAIds.map { MatchPlayer(it, name(it)) },
            liveTeamB = teamBIds.map { MatchPlayer(it, name(it)) },
            rosterChanges = rosterChanges.toList(),
        )
    }

    private fun freezeInnings(cursor: InningsCursor): DerivedInnings {
        val rows = cursor.batterOrder.map { id ->
            val acc = cursor.batters.getValue(id)
            val atCrease = id == cursor.strikerId || id == cursor.nonStrikerId
            val retired = acc.retiredHurt && !atCrease && !acc.out
            BattingRow(
                playerId = id,
                name = name(id),
                dismissalText = dismissalText(acc, retired),
                runs = acc.runs,
                balls = acc.balls,
                fours = acc.fours,
                sixes = acc.sixes,
                strikeRate = if (acc.balls == 0) 0.0 else acc.runs * 100.0 / acc.balls,
                isOut = acc.out,
                retiredHurt = retired,
                yetToBat = false,
            )
        }
        val bowling = cursor.bowlerOrder.map { id ->
            val acc = cursor.bowlers.getValue(id)
            BowlingRow(
                playerId = id,
                name = name(id),
                legalBalls = acc.legalBalls,
                maidens = acc.maidens,
                runs = acc.runs,
                wickets = acc.wickets,
                wides = acc.wides,
                noBalls = acc.noBalls,
                economy = if (acc.legalBalls == 0) 0.0 else acc.runs * cursor.ballsPerOver.toDouble() / acc.legalBalls,
            )
        }
        val powerplay = rules.endings.powerplayOvers?.let { overs ->
            val limit = overs * cursor.ballsPerOver
            cursor.deliveries.filter { delivery ->
                val before = if (delivery.legal) delivery.legalBallsAfter - 1 else delivery.legalBallsAfter
                before < limit
            }.sumOf { it.totalRuns }
        }
        val yet = cursor.battingOrderIds.filter { id ->
            val acc = cursor.batters[id]
            id !in cursor.batLocked && (acc == null || (!acc.out && id != cursor.strikerId && id != cursor.nonStrikerId && !acc.retiredHurt))
        }
        val partnerships = cursor.closedPartnerships.map { it.toModel() } + listOfNotNull(cursor.openPartnership?.toModel())
        return DerivedInnings(
            index = cursor.index,
            battingTeamId = cursor.battingTeamId,
            bowlingTeamId = cursor.bowlingTeamId,
            superOver = cursor.superOver,
            target = cursor.target,
            maxLegalBalls = cursor.maxLegalBalls,
            maxWickets = cursor.maxWickets,
            ballsPerOver = cursor.ballsPerOver,
            runs = cursor.runs,
            wickets = cursor.wickets,
            legalBalls = cursor.legalBalls,
            complete = cursor.complete,
            declared = cursor.declared,
            allOut = cursor.allOut,
            deliveries = cursor.deliveries.toList(),
            battingRows = rows,
            bowlingRows = bowling,
            fallOfWickets = cursor.fall.toList(),
            partnerships = partnerships,
            extras = cursor.extras,
            powerplayRuns = powerplay,
            yetToBat = yet,
            strikerId = cursor.strikerId,
            nonStrikerId = cursor.nonStrikerId,
            bowlerId = cursor.bowlerId,
            lastManStanding = cursor.lastManStanding,
            overs = overSummaries(cursor),
        )
    }

    private fun PartnershipAcc.toModel() = Partnership(
        batterIds = batterIds,
        batterNames = batterIds.map { name(it) },
        runs = runs,
        balls = balls,
        startedAtEpochMs = startedAt,
        endedAtEpochMs = endedAt,
    )

    private fun dismissalText(acc: BatterAcc, retired: Boolean): String {
        if (!acc.out && retired) return "retired hurt"
        if (!acc.out) return "not out"
        val bowler = acc.bowlerId?.let { name(it) }.orEmpty()
        val fielder = acc.fielderId?.let { name(it) }.orEmpty()
        return when (acc.dismissalType) {
            DismissalType.BOWLED -> "b $bowler"
            DismissalType.CAUGHT -> "c $fielder b $bowler"
            DismissalType.LBW -> "lbw b $bowler"
            DismissalType.RUN_OUT -> if (fielder.isBlank()) "run out" else "run out ($fielder)"
            DismissalType.STUMPED -> "st $fielder b $bowler"
            DismissalType.HIT_WICKET -> "hit wicket b $bowler"
            DismissalType.HIT_BALL_TWICE -> "hit the ball twice b $bowler"
            DismissalType.OBSTRUCTING_THE_FIELD -> "obstructing the field"
            DismissalType.TIMED_OUT -> "timed out"
            DismissalType.RETIRED_OUT -> "retired out"
            DismissalType.RETIRED_HURT -> "retired hurt"
            DismissalType.ONE_TIP_ONE_HAND -> "c $fielder b $bowler (one tip, one hand)"
            DismissalType.DIRECT_HIT -> if (fielder.isBlank()) "direct hit" else "direct hit ($fielder)"
            DismissalType.SINGLE_BOUNCE -> "c $fielder b $bowler (one bounce)"
            DismissalType.BOX_WALL -> "box wall b $bowler"
            DismissalType.DESIGNATED_ZONE -> "zone out b $bowler"
            DismissalType.BOX_OUT -> if (bowler.isBlank()) "box out" else "box out b $bowler"
            null -> "out"
        }
    }

    private fun equation(active: DerivedInnings?): String? {
        if (active == null || active.complete || active.target == null) return null
        val need = active.target - active.runs
        if (need <= 0) return null
        val name = teamName(active.battingTeamId)
        val maxBalls = active.maxLegalBalls ?: return "$name needs $need runs."
        val left = (maxBalls - active.legalBalls).coerceAtLeast(0)
        return "$name needs $need runs from $left balls."
    }

    private fun requiredRate(innings: DerivedInnings): Double? {
        val target = innings.target ?: return null
        val maxBalls = innings.maxLegalBalls ?: return null
        val need = target - innings.runs
        val left = maxBalls - innings.legalBalls
        if (need <= 0 || left <= 0) return 0.0
        return need * innings.ballsPerOver.toDouble() / left
    }

    private fun shell(status: MatchStatus) = DerivedMatch(
        status = status,
        innings = emptyList(),
        currentInningsIndex = null,
        result = null,
        needsBatter = false,
        needsBowler = false,
        breakPending = false,
        superOverPending = false,
        followOnAvailable = false,
        freeHit = false,
        playerOfTheMatchId = null,
        equation = null,
        currentRunRate = null,
        requiredRunRate = null,
        nextBattingTeamId = null,
    )

    private fun validateOpening(battingFirst: String, opening: OpeningPlayers) {
        if (battingFirst != match.teamA.teamId && battingFirst != match.teamB.teamId) {
            throw ScoringException("Choose a batting side from this match.")
        }
        startGuard(battingFirst, opening.strikerId, opening.nonStrikerId, opening.bowlerId)
    }

    private fun startGuard(batting: String, striker: String, nonStriker: String?, bowler: String) {
        if (striker !in squadIds(batting)) throw ScoringException("The striker is not in the batting side.")
        if (rules.squad.twoBatters) {
            if (nonStriker == null || nonStriker !in squadIds(batting) || nonStriker == striker) {
                throw ScoringException("Choose two different opening batters.")
            }
        }
        if (bowler !in squadIds(other(batting))) throw ScoringException("The opening bowler is not on the bowling side.")
    }

    private fun touchBatter(innings: InningsCursor, id: String): BatterAcc {
        return innings.batters.getOrPut(id) {
            innings.batterOrder += id
            BatterAcc(id)
        }
    }

    private fun touchBowler(innings: InningsCursor, id: String): BowlerAcc {
        return innings.bowlers.getOrPut(id) {
            innings.bowlerOrder += id
            BowlerAcc(id)
        }
    }

    private fun openPartnership(innings: InningsCursor) {
        if (innings.openPartnership != null) return
        val ids = listOfNotNull(innings.strikerId, innings.nonStrikerId).distinct()
        if (rules.squad.twoBatters && !innings.lastManStanding && ids.size < 2) return
        if (ids.isEmpty()) return
        innings.openPartnership = PartnershipAcc(ids)
    }

    private fun closePartnership(innings: InningsCursor, at: Long?) {
        val open = innings.openPartnership ?: return
        if (at != null && at > 0) open.endedAt = at
        innings.closedPartnerships += open
        innings.openPartnership = null
    }

    private fun addToPartnership(innings: InningsCursor, runs: Int, legal: Boolean, at: Long) {
        val open = innings.openPartnership ?: return
        open.runs += runs
        if (legal) open.balls += 1
        if (open.startedAt == null && at > 0) open.startedAt = at
        if (at > 0) open.endedAt = at
    }

    private fun swap(innings: InningsCursor) {
        if (!rules.squad.twoBatters || innings.strikerId == null || innings.nonStrikerId == null) return
        val striker = innings.strikerId
        innings.strikerId = innings.nonStrikerId
        innings.nonStrikerId = striker
        innings.vacant = innings.vacant?.opposite()
    }

    private fun configuredSurface(surface: SurfaceContact): Int? = when (surface) {
        SurfaceContact.SIDE_WALL -> rules.boundaries.sideWallRuns
        SurfaceContact.BACK_WALL -> rules.boundaries.backWallRuns
        SurfaceContact.CEILING -> rules.boundaries.ceilingRuns
        SurfaceContact.NET -> rules.boundaries.netRuns
        SurfaceContact.ROOF -> rules.boundaries.roofRuns
        else -> null
    }

    private fun lastManEligible(teamId: String) =
        rules.lastMan.enabled && squadIds(teamId).size >= rules.lastMan.minTeamSize

    private fun wicketsLimit(squadSize: Int, superOver: Boolean): Int {
        val standardMax = if (rules.squad.twoBatters && squadSize > 1) squadSize - 1 else squadSize
        val eligible = rules.lastMan.enabled && squadSize >= rules.lastMan.minTeamSize
        val base = if (eligible) squadSize else standardMax
        val configured = rules.endings.maximumWickets?.let { minOf(it, base) } ?: base
        return if (superOver) minOf(rules.tieBreak.superOverWickets, configured) else configured
    }

    private fun availableBatters(innings: InningsCursor): List<String> {
        return squadIds(innings.battingTeamId).filter { id ->
            val acc = innings.batters[id]
            acc == null || !acc.out
        }
    }

    private fun returnableBatters(innings: InningsCursor): List<String> {
        return innings.batters.filter { (_, acc) -> acc.retiredHurt && !acc.out }.keys.toList()
    }

    private fun refreshLastMan(innings: InningsCursor) {
        if (!lastManEligible(innings.battingTeamId) || !rules.squad.twoBatters) {
            innings.lastManStanding = false
            return
        }
        val available = availableBatters(innings)
        val atCrease = listOfNotNull(innings.strikerId, innings.nonStrikerId).filter { it in available }
        val waiting = available.filter { it !in atCrease }
        val activate = available.isNotEmpty() &&
            available.size == rules.lastMan.remainingBattersToActivate &&
            waiting.isEmpty()
        if (!activate) {
            innings.lastManStanding = false
            return
        }
        innings.lastManStanding = true
        if (available.size == 1) {
            innings.strikerId = atCrease.firstOrNull() ?: available.first()
            innings.nonStrikerId = null
            innings.vacant = null
            needsBatter = false
            if (innings.openPartnership == null) openPartnership(innings)
        }
    }

    private fun requireApprovals(approvalA: Boolean, approvalB: Boolean) {
        if (rules.roster.requireBothTeamApproval && (!approvalA || !approvalB)) {
            throw ScoringException("Both teams have to approve this change.")
        }
    }

    private fun activeIds(): Set<String> {
        val innings = current ?: return emptySet()
        return setOfNotNull(innings.strikerId, innings.nonStrikerId, innings.bowlerId)
    }

    private fun applySwitch(event: MatchEvent.SwitchPlayer) {
        if (finished) throw ScoringException("The match is already over.")
        val allowed = rules.roster.inMatchSwitchingAllowed || (event.injured && rules.roster.allowInjuredReplacement)
        if (!allowed) throw ScoringException("In-match player switching is turned off.")
        if (event.injured && !rules.roster.allowInjuredReplacement) {
            throw ScoringException("An injured-player replacement is turned off for this match.")
        }
        requireApprovals(event.approvalA, event.approvalB)
        if (!rules.roster.allowMultipleSwitches && event.outgoingPlayerId in switchedAway) {
            throw ScoringException("${name(event.outgoingPlayerId)} has already changed teams in this match.")
        }
        val squad = squadList(event.teamId)
        if (event.outgoingPlayerId !in squad) throw ScoringException("The outgoing player is not on that team.")
        if (event.incoming.id == event.outgoingPlayerId) throw ScoringException("Choose a different incoming player.")
        if (event.incoming.id in teamAIds || event.incoming.id in teamBIds) {
            throw ScoringException("That player is already in this match.")
        }
        val index = squad.indexOf(event.outgoingPlayerId)
        squad[index] = event.incoming.id
        names[event.incoming.id] = event.incoming.name
        switchedAway += event.outgoingPlayerId
        current?.let { innings ->
            if (innings.battingTeamId == event.teamId) replaceBatter(innings, event.outgoingPlayerId, event.incoming.id, event.injured)
            if (innings.bowlingTeamId == event.teamId && innings.bowlerId == event.outgoingPlayerId) {
                if (!rules.roster.newPlayerMayBowlImmediately && innings.overLegal != 0) {
                    throw ScoringException("The replacement cannot bowl until this over ends.")
                }
                innings.bowlerId = event.incoming.id
                touchBowler(innings, event.incoming.id)
            }
            innings.maxWickets = maxOf(innings.maxWickets, wicketsLimit(squadIds(innings.battingTeamId).size, innings.superOver))
        }
        recordRoster(event.id, RosterChangeKind.SWITCH, event.incoming.id, event.incoming.name, event.teamId, event.teamId, event.outgoingPlayerId, event.reason, event.timestampEpochMs, event.organizerId, event.approvalA, event.approvalB, temporary = false)
    }

    private fun replaceBatter(innings: InningsCursor, outgoingId: String, incomingId: String, injured: Boolean) {
        val order = innings.battingOrderIds
        val place = order.indexOf(outgoingId)
        if (place >= 0) order[place] = incomingId else order += incomingId
        if (outgoingId == innings.strikerId) innings.strikerId = incomingId
        if (outgoingId == innings.nonStrikerId) innings.nonStrikerId = incomingId
        if (injured) {
            innings.batters[outgoingId]?.let { acc ->
                acc.retiredHurt = true
                acc.dismissalType = DismissalType.RETIRED_HURT
            }
        }
        if (!rules.roster.newPlayerMayBatImmediately && innings.overLegal != 0 && incomingId != innings.strikerId && incomingId != innings.nonStrikerId) {
            innings.batLocked += incomingId
        }
        if (incomingId == innings.strikerId || incomingId == innings.nonStrikerId) touchBatter(innings, incomingId)
    }

    private fun applyAdd(event: MatchEvent.AddPlayer) {
        if (finished) throw ScoringException("The match is already over.")
        if (!rules.roster.allowAddDuringMatch) throw ScoringException("Adding a player during the match is turned off.")
        val innings = current
        if (rules.roster.additionsOnlyEndOfOver && innings != null && innings.overLegal != 0) {
            throw ScoringException("Players can be added only at the end of an over.")
        }
        requireApprovals(event.approvalA, event.approvalB)
        if (event.player.id in teamAIds || event.player.id in teamBIds) {
            throw ScoringException("That player is already in this match.")
        }
        if (!rules.roster.allowUnevenTeamSizes) {
            throw ScoringException("Adding a player would make the teams uneven. That is turned off for this match.")
        }
        squadList(event.teamId) += event.player.id
        names[event.player.id] = event.player.name
        innings?.let { cursor ->
            if (cursor.battingTeamId == event.teamId) {
                cursor.battingOrderIds += event.player.id
                if (!rules.roster.newPlayerMayBatImmediately && cursor.overLegal != 0) cursor.batLocked += event.player.id
            }
            if (cursor.bowlingTeamId == event.teamId && !rules.roster.newPlayerMayBowlImmediately && cursor.overLegal != 0) {
                cursor.bowlLocked += event.player.id
            }
            cursor.maxWickets = maxOf(cursor.maxWickets, wicketsLimit(squadIds(cursor.battingTeamId).size, cursor.superOver))
        }
        recordRoster(event.id, RosterChangeKind.ADD, event.player.id, event.player.name, null, event.teamId, null, event.reason, event.timestampEpochMs, event.organizerId, event.approvalA, event.approvalB, temporary = false)
    }

    private fun applyLoan(event: MatchEvent.LendPlayer) {
        if (finished) throw ScoringException("The match is already over.")
        if (!rules.roster.allowLoanToOpponent) throw ScoringException("Lending a player to the other team is turned off.")
        if (event.fromTeamId == event.toTeamId) throw ScoringException("Choose two different teams.")
        requireApprovals(event.approvalA, event.approvalB)
        if (!rules.roster.allowMultipleSwitches && event.playerId in switchedAway) {
            throw ScoringException("${name(event.playerId)} has already changed teams in this match.")
        }
        val from = squadList(event.fromTeamId)
        if (event.playerId !in from) throw ScoringException("That player is not on the lending team.")
        val to = squadList(event.toTeamId)
        if (event.playerId in to) throw ScoringException("That player is already on the receiving team.")
        if (event.playerId in activeIds()) {
            throw ScoringException("Retire that player or finish the over before lending them. They cannot be active for both teams.")
        }
        if (!rules.roster.allowUnevenTeamSizes && from.size - 1 != to.size + 1) {
            throw ScoringException("This loan would make the teams uneven. That is turned off for this match.")
        }
        if (from.size <= 1) throw ScoringException("The lending team needs to keep at least one player.")
        from.remove(event.playerId)
        to += event.playerId
        switchedAway += event.playerId
        current?.let { innings ->
            if (innings.battingTeamId == event.fromTeamId) {
                innings.battingOrderIds.remove(event.playerId)
            }
            if (innings.battingTeamId == event.toTeamId) {
                innings.battingOrderIds += event.playerId
                if (!rules.roster.newPlayerMayBatImmediately && innings.overLegal != 0) innings.batLocked += event.playerId
            }
            if (innings.bowlingTeamId == event.toTeamId && !rules.roster.newPlayerMayBowlImmediately && innings.overLegal != 0) {
                innings.bowlLocked += event.playerId
            }
            innings.maxWickets = maxOf(innings.maxWickets, wicketsLimit(squadIds(innings.battingTeamId).size, innings.superOver))
        }
        recordRoster(event.id, RosterChangeKind.LOAN, event.playerId, name(event.playerId), event.fromTeamId, event.toTeamId, null, event.reason, event.timestampEpochMs, event.organizerId, event.approvalA, event.approvalB, event.temporary)
    }

    private fun recordRoster(
        eventId: String,
        kind: RosterChangeKind,
        playerId: String,
        playerName: String,
        fromTeamId: String?,
        toTeamId: String?,
        outgoingPlayerId: String?,
        reason: String,
        timestampEpochMs: Long,
        organizerId: String?,
        approvalA: Boolean,
        approvalB: Boolean,
        temporary: Boolean,
    ) {
        if (reason.isBlank()) throw ScoringException("Add a reason for this roster change.")
        val innings = current
        rosterChanges += RosterChangeRecord(
            eventId = eventId,
            kind = kind,
            playerId = playerId,
            playerName = playerName,
            fromTeamId = fromTeamId,
            toTeamId = toTeamId,
            outgoingPlayerId = outgoingPlayerId,
            reason = reason.trim(),
            timestampEpochMs = timestampEpochMs,
            inningsIndex = innings?.index,
            score = innings?.runs,
            wickets = innings?.wickets,
            organizerId = organizerId,
            approvalA = approvalA,
            approvalB = approvalB,
            temporary = temporary,
        )
    }

    private fun overSummaries(cursor: InningsCursor): List<OverSummary> {
        return cursor.deliveries.groupBy { it.overIndex }.entries.sortedBy { it.key }.map { (index, balls) ->
            val bowlerId = balls.lastOrNull()?.bowlerId
            OverSummary(
                overIndex = index,
                bowlerId = bowlerId,
                bowlerName = bowlerId?.let { name(it) }.orEmpty(),
                symbols = balls.map { it.symbol },
                deliveries = balls,
                runs = balls.sumOf { it.totalRuns },
                wickets = balls.count { it.dismissal?.isWicket == true },
                scoreAfter = balls.last().scoreAfter,
                wicketsAfter = balls.last().wicketsAfter,
            )
        }
    }

    private fun squadIds(teamId: String): List<String> = squadList(teamId)

    private fun squadList(teamId: String): MutableList<String> = when (teamId) {
        match.teamA.teamId -> teamAIds
        match.teamB.teamId -> teamBIds
        else -> throw ScoringException("Unknown team.")
    }

    private fun other(teamId: String) = if (teamId == match.teamA.teamId) match.teamB.teamId else match.teamA.teamId

    private fun name(id: String): String = names[id]
        ?: match.teamA.players.find { it.id == id }?.name
        ?: match.teamB.players.find { it.id == id }?.name
        ?: id

    private fun teamName(id: String) = if (id == match.teamA.teamId) match.teamA.name else match.teamB.name
}

private fun End.opposite() = if (this == End.STRIKE) End.NON_STRIKE else End.STRIKE

private fun SurfaceContact.isWall() = this == SurfaceContact.SIDE_WALL ||
    this == SurfaceContact.BACK_WALL ||
    this == SurfaceContact.CEILING ||
    this == SurfaceContact.NET ||
    this == SurfaceContact.ROOF

private fun SurfaceContact.isDirectScore() = this == SurfaceContact.SIDE_WALL ||
    this == SurfaceContact.BACK_WALL ||
    this == SurfaceContact.CEILING ||
    this == SurfaceContact.NET

private fun SurfaceContact.label() = when (this) {
    SurfaceContact.SIDE_WALL -> "Side-wall"
    SurfaceContact.BACK_WALL -> "Back-wall"
    SurfaceContact.CEILING -> "Ceiling"
    SurfaceContact.ROOF -> "Roof"
    SurfaceContact.NET -> "Net"
    SurfaceContact.DESIGNATED_ZONE -> "Zone"
    SurfaceContact.NONE -> "Contact"
}

private val RUNS_CANCELLED = setOf(
    DismissalType.BOWLED,
    DismissalType.CAUGHT,
    DismissalType.LBW,
    DismissalType.STUMPED,
    DismissalType.HIT_WICKET,
    DismissalType.HIT_BALL_TWICE,
    DismissalType.ONE_TIP_ONE_HAND,
    DismissalType.SINGLE_BOUNCE,
    DismissalType.BOX_WALL,
    DismissalType.DESIGNATED_ZONE,
    DismissalType.TIMED_OUT,
)

private fun DismissalType.label() = when (this) {
    DismissalType.BOWLED -> "Bowled"
    DismissalType.CAUGHT -> "Caught"
    DismissalType.LBW -> "LBW"
    DismissalType.RUN_OUT -> "Run out"
    DismissalType.STUMPED -> "Stumped"
    DismissalType.HIT_WICKET -> "Hit wicket"
    DismissalType.HIT_BALL_TWICE -> "Hit the ball twice"
    DismissalType.OBSTRUCTING_THE_FIELD -> "Obstructing the field"
    DismissalType.TIMED_OUT -> "Timed out"
    DismissalType.RETIRED_OUT -> "Retired out"
    DismissalType.RETIRED_HURT -> "Retired hurt"
    DismissalType.ONE_TIP_ONE_HAND -> "One-tip-one-hand"
    DismissalType.DIRECT_HIT -> "Direct hit"
    DismissalType.SINGLE_BOUNCE -> "Single bounce"
    DismissalType.BOX_WALL -> "Box wall"
    DismissalType.DESIGNATED_ZONE -> "Designated zone"
    DismissalType.BOX_OUT -> "Box Out"
}

private fun fallOver(legalBalls: Int, ballsPerOver: Int): String {
    if (legalBalls <= 0) return "0.0"
    val remainder = legalBalls % ballsPerOver
    return if (remainder == 0) {
        "${legalBalls / ballsPerOver - 1}.$ballsPerOver"
    } else {
        "${legalBalls / ballsPerOver}.$remainder"
    }
}

private fun symbol(
    input: BallInput,
    dead: Boolean,
    batterRuns: Int,
    extras: Extras,
    boundary: BoundaryMarker?,
    dismissal: AppliedDismissal?,
): String {
    val wicket = dismissal?.isWicket == true
    val base = when {
        dead && extras.penalties > 0 -> "p${extras.penalties}"
        dead -> "db"
        input.wide && input.wideRunningRuns > 0 -> "wd+${input.wideRunningRuns}"
        input.wide -> "wd"
        input.noBall && input.byeRuns > 0 -> "nb+b${input.byeRuns}"
        input.noBall && input.legByeRuns > 0 -> "nb+lb${input.legByeRuns}"
        input.noBall && batterRuns > 0 -> "nb+$batterRuns"
        input.noBall -> "nb"
        input.byeRuns > 0 -> "b${input.byeRuns}"
        input.legByeRuns > 0 -> "lb${input.legByeRuns}"
        boundary == BoundaryMarker.SIX -> "6"
        boundary == BoundaryMarker.FOUR -> "4"
        batterRuns == 0 && extras.total == 0 -> "·"
        else -> batterRuns.toString()
    }
    return if (wicket && base == "·") "W" else if (wicket) "${base}W" else base
}

private fun movePlayerOnMatch(match: Match, playerId: String, fromTeamId: String, toTeamId: String): Match {
    if (fromTeamId == toTeamId) throw ScoringException("Choose two different teams.")
    val from = if (fromTeamId == match.teamA.teamId) match.teamA else if (fromTeamId == match.teamB.teamId) match.teamB else throw ScoringException("Unknown team.")
    val to = if (toTeamId == match.teamA.teamId) match.teamA else if (toTeamId == match.teamB.teamId) match.teamB else throw ScoringException("Unknown team.")
    val player = from.players.find { it.id == playerId } ?: throw ScoringException("That player is not on the lending team.")
    if (to.players.any { it.id == playerId }) throw ScoringException("That player is already on the receiving team.")
    val newFromPlayers = from.players.filter { it.id != playerId }
    val newToPlayers = to.players + player
    if (newFromPlayers.isEmpty()) throw ScoringException("A team needs at least one player.")
    if (match.rules.squad.twoBatters && (newFromPlayers.size < 2 || newToPlayers.size < 2)) {
        throw ScoringException("This match needs two batters, so each team has to keep at least two players.")
    }
    if (!match.rules.roster.allowUnevenTeamSizes && newFromPlayers.size != newToPlayers.size) {
        throw ScoringException("This transfer would make the teams uneven. Turn that on before the match, or swap players.")
    }
    val newFrom = from.copy(
        players = newFromPlayers,
        captainId = from.captainId.takeIf { it != playerId },
        wicketkeeperId = from.wicketkeeperId.takeIf { it != playerId },
    )
    val newTo = to.copy(players = newToPlayers)
    return if (fromTeamId == match.teamA.teamId) match.copy(teamA = newFrom, teamB = newTo) else match.copy(teamA = newTo, teamB = newFrom)
}

private fun runsPhrase(count: Int) = if (count == 1) "1 run" else "$count runs"

private fun wicketsPhrase(count: Int) = if (count == 1) "1 wicket" else "$count wickets"

private fun rate(runs: Int, legalBalls: Int, ballsPerOver: Int): Double? {
    if (legalBalls == 0) return 0.0
    return runs * ballsPerOver.toDouble() / legalBalls
}
