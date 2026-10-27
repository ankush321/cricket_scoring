package com.example.cricketscoring.rules

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.RuleDisposition

data class RuleCatalogRow(
    val topic: String,
    val box: String,
    val open: String,
    val overarm: String,
    val disposition: RuleDisposition,
    val note: String,
)

fun ruleCatalog(): List<RuleCatalogRow> = listOf(
    RuleCatalogRow(
        topic = "Players a side",
        box = "6",
        open = "8",
        overarm = "11",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Informal matches can use any squad from 2 to 15. The playing list on the match is the one that is scored.",
    ),
    RuleCatalogRow(
        topic = "Batters at the crease",
        box = "Two",
        open = "Two",
        overarm = "Two",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Turn off the second batter for single-wicket games. Wickets needed for all-out then equal the squad size.",
    ),
    RuleCatalogRow(
        topic = "Balls in an over",
        box = "6",
        open = "6",
        overarm = "6",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Illegal deliveries do not count toward the over unless a profile explicitly says they do.",
    ),
    RuleCatalogRow(
        topic = "Overs in an innings",
        box = "5",
        open = "8",
        overarm = "T10, T20, 50, or custom. Multi-innings has no over cap until you set one.",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Presets fill a starting point. A competition can use any over limit.",
    ),
    RuleCatalogRow(
        topic = "Innings a side",
        box = "1",
        open = "1",
        overarm = "1 for limited overs. 2 only on the multi-innings profile.",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Declarations and follow-on appear only when the profile allows them.",
    ),
    RuleCatalogRow(
        topic = "Overs per bowler",
        box = "1",
        open = "2",
        overarm = "One fifth of the overs, rounded down, for limited overs. No cap on multi-innings.",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "The same bowler may bowl the next over unless you require a change.",
    ),
    RuleCatalogRow(
        topic = "Delivery style",
        box = "Underarm",
        open = "Underarm",
        overarm = "Overarm",
        disposition = RuleDisposition.RECOMMENDED_DEFAULT,
        note = "A strict underarm confirmation can be turned on. It stays off so a live scorer is not blocked on every ball.",
    ),
    RuleCatalogRow(
        topic = "Running between wickets",
        box = "On",
        open = "On",
        overarm = "On",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Some box grounds score only off the walls. Confirm before the first ball.",
    ),
    RuleCatalogRow(
        topic = "Boundary values",
        box = "4 and 6, plus separate wall values",
        open = "4 and 6",
        overarm = "4 and 6",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Buttons show the configured value. Fours and sixes are counted from the boundary marker, not from a guessed running total.",
    ),
    RuleCatalogRow(
        topic = "Wall, net, and ceiling",
        box = "Side wall 1, back wall 2, net 1, ceiling 6. Roof is a dead ball.",
        open = "Not used",
        overarm = "Not used",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "These box numbers are a recommendation. Grounds do not agree. A blank value hides that button.",
    ),
    RuleCatalogRow(
        topic = "Ball after touching a wall",
        box = "Unset. Direct boundaries and wall hits are separate until you set an after-wall value.",
        open = "Not used",
        overarm = "Not used",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Set this only when the ground scores a wall deflection differently from a clean boundary.",
    ),
    RuleCatalogRow(
        topic = "Wides",
        box = "1 run, bowled again",
        open = "1 run, bowled again",
        overarm = "1 run, bowled again",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "A wide is not a legal delivery on the recommended profiles. Running on a wide is extra and can change strike.",
    ),
    RuleCatalogRow(
        topic = "No-balls",
        box = "1 run, bowled again, no free hit",
        open = "1 run, bowled again, no free hit",
        overarm = "1 run, bowled again, free hit on limited-overs profiles",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Free hit is a playing condition, not a Law. A wide or no-ball on a free hit keeps the free hit for the next ball.",
    ),
    RuleCatalogRow(
        topic = "Byes and leg-byes",
        box = "Off",
        open = "On",
        overarm = "On",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "They go to extras, not to the batter or the bowler's runs. They still stop a maiden.",
    ),
    RuleCatalogRow(
        topic = "Maximum runs on one ball",
        box = "None",
        open = "None",
        overarm = "None",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Leave this empty unless the ground actually caps a delivery.",
    ),
    RuleCatalogRow(
        topic = "Bowled, caught, run out, hit wicket",
        box = "On",
        open = "On",
        overarm = "On",
        disposition = RuleDisposition.RECOMMENDED_DEFAULT,
        note = "Caught, bowled, and hit wicket credit the bowler. Run out does not.",
    ),
    RuleCatalogRow(
        topic = "Catch off a wall or net",
        box = "Off",
        open = "Off",
        overarm = "Off",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "A catch combined with a wall contact is rejected until this is on.",
    ),
    RuleCatalogRow(
        topic = "LBW",
        box = "Off",
        open = "Off",
        overarm = "On",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Leave LBW off for underarm games unless the competition uses it.",
    ),
    RuleCatalogRow(
        topic = "Stumped",
        box = "Off",
        open = "Off",
        overarm = "On",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Stumping needs the dismissal switched on. It is allowed off a wide, and not off a no-ball or free hit.",
    ),
    RuleCatalogRow(
        topic = "One-tip-one-hand",
        box = "Off",
        open = "Off",
        overarm = "Off",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Treated as a catch and credited to the bowler only after you enable it. It is never assumed.",
    ),
    RuleCatalogRow(
        topic = "Direct hit and single bounce",
        box = "Off",
        open = "Off",
        overarm = "Off",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Direct hit is scored like a run out and does not credit the bowler. Single bounce is scored like a catch.",
    ),
    RuleCatalogRow(
        topic = "Retired hurt and retired out",
        box = "On",
        open = "On",
        overarm = "On",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Retired hurt is not a wicket and the batter may return. Retired out is a wicket and does not credit the bowler.",
    ),
    RuleCatalogRow(
        topic = "Handled the ball",
        box = "Not a separate dismissal",
        open = "Not a separate dismissal",
        overarm = "Not a separate dismissal",
        disposition = RuleDisposition.RECOMMENDED_DEFAULT,
        note = "Use obstructing the field when the competition still wants that outcome.",
    ),
    RuleCatalogRow(
        topic = "Tie-breaker",
        box = "Scores level is a tie",
        open = "Scores level is a tie",
        overarm = "Super Over on T10 and T20. Off for 50 overs and multi-innings.",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "The side that chased bats first in the Super Over. One Super Over is played. If that is level, the match stays tied.",
    ),
    RuleCatalogRow(
        topic = "Rain",
        box = "Record no result",
        open = "Record no result",
        overarm = "Record no result",
        disposition = RuleDisposition.CONFIRM_WITH_ORGANIZER,
        note = "Duckworth-Lewis-Stern and other rain formulas are not calculated.",
    ),
    RuleCatalogRow(
        topic = "Bowling label",
        box = "Bowler",
        open = "Bowler",
        overarm = "Bowler",
        disposition = RuleDisposition.CONFIGURABLE,
        note = "Change the label to Thrower only when the competition uses that word.",
    ),
)

fun styleSummary(style: CricketStyle): String = when (style) {
    CricketStyle.UNDERARM_BOX -> "Fast-paced cricket played inside a box or enclosed arena."
    CricketStyle.UNDERARM_OPEN -> "Underarm cricket played in an open playing area."
    CricketStyle.OVERARM -> "Traditional cricket with configurable formats, overs, and innings."
}

fun styleTitle(style: CricketStyle): String = when (style) {
    CricketStyle.UNDERARM_BOX -> "Underarm Box Cricket"
    CricketStyle.UNDERARM_OPEN -> "Underarm Open Cricket"
    CricketStyle.OVERARM -> "Overarm Cricket"
}
