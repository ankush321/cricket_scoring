package com.example.cricketscoring.rules

import com.example.cricketscoring.model.AdditionalScoringEvent
import com.example.cricketscoring.model.AdditionalScoringRules
import com.example.cricketscoring.model.BoundaryRules
import com.example.cricketscoring.model.BowlingLabels
import com.example.cricketscoring.model.BoxOutRules
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.DismissalRules
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.EndingRules
import com.example.cricketscoring.model.ExtraRules
import com.example.cricketscoring.model.GroundRules
import com.example.cricketscoring.model.LastManRules
import com.example.cricketscoring.model.LocalRules
import com.example.cricketscoring.model.OverRules
import com.example.cricketscoring.model.RosterRules
import com.example.cricketscoring.model.RuleSource
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.ScoringAwardMode
import com.example.cricketscoring.model.SquadRules
import com.example.cricketscoring.model.StrikeRules
import com.example.cricketscoring.model.TieBreakRules

private val strikerDismissals = listOf(
    DismissalType.BOWLED,
    DismissalType.CAUGHT,
    DismissalType.LBW,
    DismissalType.STUMPED,
    DismissalType.HIT_WICKET,
    DismissalType.HIT_BALL_TWICE,
    DismissalType.ONE_TIP_ONE_HAND,
    DismissalType.SINGLE_BOUNCE,
    DismissalType.BOX_OUT,
)

private val fielderDismissals = listOf(
    DismissalType.CAUGHT,
    DismissalType.RUN_OUT,
    DismissalType.STUMPED,
    DismissalType.ONE_TIP_ONE_HAND,
    DismissalType.DIRECT_HIT,
    DismissalType.SINGLE_BOUNCE,
)

private val bowlerWickets = listOf(
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
)

fun defaultRules(style: CricketStyle, id: String = "default-${style.name}"): RulesProfile = when (style) {
    CricketStyle.UNDERARM_BOX -> boxDefaults(id)
    CricketStyle.UNDERARM_OPEN -> openDefaults(id)
    CricketStyle.OVERARM -> overarmLimited(20, "T20", id)
}

fun boxDefaults(id: String = "default-box"): RulesProfile = RulesProfile(
    id = id,
    name = "Underarm box — recommended",
    style = CricketStyle.UNDERARM_BOX,
    source = RuleSource.DEFAULT,
    squad = SquadRules(playersPerTeam = 6, wicketkeeperRequired = false, captainRequired = false, twoBatters = true),
    overs = OverRules(
        ballsPerOver = 6,
        oversPerInnings = 5,
        inningsPerTeam = 1,
        maxOversPerBowler = 1,
        underarmOnly = true,
        requireUnderarmConfirm = false,
    ),
    extras = ExtraRules(
        widesEnabled = true,
        noBallsEnabled = true,
        freeHitEnabled = false,
        byesEnabled = false,
        legByesEnabled = false,
        maxRunsPerDelivery = null,
    ),
    boundaries = BoundaryRules(
        fourRuns = 4,
        sixRuns = 6,
        runningBetweenWicketsEnabled = true,
        sideWallRuns = 1,
        backWallRuns = 2,
        ceilingRuns = 6,
        roofRuns = null,
        roofIsDeadBall = true,
        netRuns = 1,
        zoneBonusRuns = null,
        afterWallBoundaryRuns = null,
    ),
    dismissals = DismissalRules(
        enabled = listOf(
            DismissalType.BOWLED,
            DismissalType.CAUGHT,
            DismissalType.RUN_OUT,
            DismissalType.HIT_WICKET,
            DismissalType.OBSTRUCTING_THE_FIELD,
            DismissalType.RETIRED_OUT,
            DismissalType.RETIRED_HURT,
        ),
        bowlerCredited = bowlerWickets,
        strikerOnly = strikerDismissals,
        fielderRequired = fielderDismissals,
        catchOffWallAllowed = false,
    ),
    strike = StrikeRules(),
    endings = EndingRules(powerplayOvers = null),
    tieBreak = TieBreakRules(superOverEnabled = false, superOverLegalBalls = 6, superOverWickets = 2),
    ground = GroundRules(hasWalls = true, hasCeiling = true, boundaryMeters = null, pitchLengthMeters = null),
    local = LocalRules(),
    labels = BowlingLabels(bowlingRole = "Bowler"),
    organizerPrompts = listOf(
        "Side-wall, back-wall, net, and ceiling runs are a recommended starting point. Confirm the numbers used at this ground.",
        "Catches off the wall or net are off until you turn them on.",
        "Running between the wickets is on. Turn it off for grounds that only score off the walls.",
        "Byes and leg-byes are off. Turn them on if this ground counts them.",
        "Wides and no-balls add one run and are bowled again. Confirm both.",
        "A Super Over is off. Enable it only if the competition uses one.",
        "Additional scoring values such as 8 or 9 stay off until you enable them.",
        "Box Out is off until you define what it means at this ground.",
        "Last-man standing is off. The team-size threshold and remaining-batter trigger are separate settings.",
    ),
    additionalScoring = optionalScoring("zone", "target"),
    lastMan = LastManRules(),
    roster = RosterRules(),
    boxOut = BoxOutRules(description = "Define the local box-out event before enabling it."),
)

fun openDefaults(id: String = "default-open"): RulesProfile = RulesProfile(
    id = id,
    name = "Underarm open — recommended",
    style = CricketStyle.UNDERARM_OPEN,
    source = RuleSource.DEFAULT,
    squad = SquadRules(playersPerTeam = 8, twoBatters = true),
    overs = OverRules(
        ballsPerOver = 6,
        oversPerInnings = 8,
        inningsPerTeam = 1,
        maxOversPerBowler = 2,
        underarmOnly = true,
    ),
    extras = ExtraRules(
        widesEnabled = true,
        noBallsEnabled = true,
        freeHitEnabled = false,
        byesEnabled = true,
        legByesEnabled = true,
    ),
    boundaries = BoundaryRules(
        fourRuns = 4,
        sixRuns = 6,
        runningBetweenWicketsEnabled = true,
    ),
    dismissals = DismissalRules(
        enabled = listOf(
            DismissalType.BOWLED,
            DismissalType.CAUGHT,
            DismissalType.RUN_OUT,
            DismissalType.HIT_WICKET,
            DismissalType.OBSTRUCTING_THE_FIELD,
            DismissalType.RETIRED_OUT,
            DismissalType.RETIRED_HURT,
        ),
        bowlerCredited = bowlerWickets,
        strikerOnly = strikerDismissals,
        fielderRequired = fielderDismissals,
        catchOffWallAllowed = false,
    ),
    strike = StrikeRules(),
    endings = EndingRules(),
    tieBreak = TieBreakRules(superOverEnabled = false, superOverLegalBalls = 6, superOverWickets = 2),
    ground = GroundRules(hasWalls = false, hasCeiling = false),
    local = LocalRules(
        oneTipOneHandEnabled = false,
        directHitDismissalEnabled = false,
        singleBounceDismissalEnabled = false,
    ),
    labels = BowlingLabels(bowlingRole = "Bowler"),
    organizerPrompts = listOf(
        "One-tip-one-hand, direct-hit, and single-bounce dismissals are off. Enable only the ones this ground actually uses.",
        "There is no maximum runs per ball until you set one.",
        "Byes and leg-byes are on. Turn them off for street rules that ignore them.",
        "LBW and stumpings are off. Turn them on only if the match uses them.",
        "A Super Over is off until the competition asks for one.",
        "Pitch length and boundary distance are unset. Record them if the ground cares.",
        "Custom scoring values, last-man standing, and player loans stay off until the organizer turns them on.",
    ),
    additionalScoring = optionalScoring("zone", "special"),
    lastMan = LastManRules(),
    roster = RosterRules(),
    boxOut = BoxOutRules(),
)

fun overarmLimited(overs: Int, name: String, id: String = "default-overarm-$overs"): RulesProfile {
    val maxBowler = maxOf(1, overs / 5)
    val superOver = overs <= 20
    return RulesProfile(
        id = id,
        name = name,
        style = CricketStyle.OVERARM,
        source = RuleSource.DEFAULT,
        squad = SquadRules(playersPerTeam = 11, wicketkeeperRequired = true, captainRequired = true, twoBatters = true),
        overs = OverRules(
            ballsPerOver = 6,
            oversPerInnings = overs,
            inningsPerTeam = 1,
            maxOversPerBowler = maxBowler,
            underarmOnly = false,
        ),
        extras = ExtraRules(
            widesEnabled = true,
            wideCountsAsLegal = false,
            noBallsEnabled = true,
            noBallCountsAsLegal = false,
            freeHitEnabled = true,
            byesEnabled = true,
            legByesEnabled = true,
        ),
        boundaries = BoundaryRules(fourRuns = 4, sixRuns = 6, runningBetweenWicketsEnabled = true),
        dismissals = overarmDismissals(),
        strike = StrikeRules(),
        endings = EndingRules(
            declarationsAllowed = false,
            followOnAllowed = false,
            powerplayOvers = if (overs >= 20) 6 else if (overs >= 10) 3 else 2,
        ),
        tieBreak = TieBreakRules(
            superOverEnabled = superOver,
            superOverLegalBalls = 6,
            superOverWickets = 2,
        ),
        ground = GroundRules(),
        local = LocalRules(),
        labels = BowlingLabels(bowlingRole = "Bowler"),
        organizerPrompts = listOf(
            "Free hit after a no-ball is a common limited-overs playing condition. It is not a Law of Cricket. Turn it off if this competition does not use it.",
            if (superOver) {
                "A Super Over is on for this short format. Confirm that this competition uses it. A tied Super Over stays a tie."
            } else {
                "A Super Over is off. Enable it if this competition uses that tie-breaker."
            },
            "The same bowler may bowl the next over. The Laws do not forbid it. Turn on a mandatory bowling change if your competition requires one.",
            "Rain interruptions are stored as no result. This app does not calculate Duckworth-Lewis-Stern or another rain rule.",
            "These defaults are a practical limited-overs baseline. They are not an official board publication.",
            "No-ball runs off the bat go to the batter, plus the no-ball extra to the team.",
        ),
        additionalScoring = optionalScoring("custom", "bonus"),
        lastMan = LastManRules(),
        roster = RosterRules(preMatchTransfersAllowed = true, inMatchSwitchingAllowed = false),
        boxOut = BoxOutRules(),
    )
}

fun overarmMultiInnings(id: String = "default-overarm-multi"): RulesProfile = RulesProfile(
    id = id,
    name = "Multi-innings",
    style = CricketStyle.OVERARM,
    source = RuleSource.DEFAULT,
    squad = SquadRules(playersPerTeam = 11, wicketkeeperRequired = true, captainRequired = true, twoBatters = true),
    overs = OverRules(
        ballsPerOver = 6,
        oversPerInnings = null,
        inningsPerTeam = 2,
        maxOversPerBowler = null,
        underarmOnly = false,
    ),
    extras = ExtraRules(
        freeHitEnabled = false,
        byesEnabled = true,
        legByesEnabled = true,
    ),
    boundaries = BoundaryRules(),
    dismissals = overarmDismissals(),
    strike = StrikeRules(),
    endings = EndingRules(
        declarationsAllowed = true,
        followOnAllowed = true,
        followOnDeficit = 200,
        powerplayOvers = null,
    ),
    tieBreak = TieBreakRules(superOverEnabled = false),
    ground = GroundRules(),
    local = LocalRules(),
    labels = BowlingLabels(bowlingRole = "Bowler"),
    organizerPrompts = listOf(
        "Declarations and follow-on are on because this profile has two innings a side.",
        "The follow-on deficit starts at 200, which is a long-match figure. Change it for a shorter match.",
        "Free hit and Super Over are off.",
        "There is no over limit until you set one.",
        "This is not an official Test playing-conditions document.",
    ),
    additionalScoring = AdditionalScoringRules(),
    lastMan = LastManRules(),
    roster = RosterRules(),
    boxOut = BoxOutRules(),
)

private fun overarmDismissals() = DismissalRules(
    enabled = listOf(
        DismissalType.BOWLED,
        DismissalType.CAUGHT,
        DismissalType.LBW,
        DismissalType.RUN_OUT,
        DismissalType.STUMPED,
        DismissalType.HIT_WICKET,
        DismissalType.HIT_BALL_TWICE,
        DismissalType.OBSTRUCTING_THE_FIELD,
        DismissalType.TIMED_OUT,
        DismissalType.RETIRED_OUT,
        DismissalType.RETIRED_HURT,
    ),
    bowlerCredited = bowlerWickets,
    strikerOnly = strikerDismissals,
    fielderRequired = fielderDismissals,
    catchOffWallAllowed = false,
)

fun withLocalDismissals(rules: RulesProfile): RulesProfile {
    val gated = mapOf(
        DismissalType.ONE_TIP_ONE_HAND to rules.local.oneTipOneHandEnabled,
        DismissalType.DIRECT_HIT to rules.local.directHitDismissalEnabled,
        DismissalType.SINGLE_BOUNCE to rules.local.singleBounceDismissalEnabled,
        DismissalType.BOX_WALL to rules.local.boxWallDismissalEnabled,
        DismissalType.DESIGNATED_ZONE to rules.local.designatedZoneDismissalEnabled,
        DismissalType.BOX_OUT to rules.boxOut.enabled,
    )
    val enabled = rules.dismissals.enabled.filter { gated[it] != false }.toMutableList()
    gated.forEach { (type, on) ->
        if (on && type !in enabled) enabled += type
    }
    val credited = rules.dismissals.bowlerCredited.toMutableList()
    if (rules.boxOut.enabled && rules.boxOut.bowlerCredited && DismissalType.BOX_OUT !in credited) {
        credited += DismissalType.BOX_OUT
    }
    if (!rules.boxOut.bowlerCredited) credited.remove(DismissalType.BOX_OUT)
    return rules.copy(dismissals = rules.dismissals.copy(enabled = enabled.distinct(), bowlerCredited = credited.distinct()))
}

private fun optionalScoring(first: String, second: String) = AdditionalScoringRules(
    events = listOf(
        AdditionalScoringEvent("${first}-8", "Designated zone hit", 8, enabled = false, awardMode = ScoringAwardMode.FIXED, description = "Direct hit into a designated scoring zone."),
        AdditionalScoringEvent("${second}-9", "Special target hit", 9, enabled = false, awardMode = ScoringAwardMode.FIXED, description = "Hitting a specific wall, target, or local scoring event."),
    ),
)
