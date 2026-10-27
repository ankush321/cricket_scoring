package com.example.cricketscoring.model

import kotlinx.serialization.Serializable

@Serializable
enum class CricketStyle {
    UNDERARM_BOX,
    UNDERARM_OPEN,
    OVERARM,
}

@Serializable
enum class RuleSource {
    DEFAULT,
    TOURNAMENT,
    CUSTOM,
}

@Serializable
enum class RuleDisposition {
    RECOMMENDED_DEFAULT,
    CONFIGURABLE,
    CONFIRM_WITH_ORGANIZER,
}

@Serializable
enum class DismissalType {
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,
    HIT_WICKET,
    HIT_BALL_TWICE,
    OBSTRUCTING_THE_FIELD,
    TIMED_OUT,
    RETIRED_OUT,
    RETIRED_HURT,
    ONE_TIP_ONE_HAND,
    DIRECT_HIT,
    SINGLE_BOUNCE,
    BOX_WALL,
    DESIGNATED_ZONE,
    BOX_OUT,
}

@Serializable
enum class SurfaceContact {
    NONE,
    SIDE_WALL,
    BACK_WALL,
    CEILING,
    ROOF,
    NET,
    DESIGNATED_ZONE,
}

@Serializable
enum class BoundaryMarker {
    FOUR,
    SIX,
}

@Serializable
enum class TossChoice {
    BAT,
    BOWL,
}

@Serializable
enum class CompetitionStage {
    LEAGUE,
    KNOCKOUT,
}

@Serializable
enum class MatchStatus {
    UPCOMING,
    LIVE,
    INNINGS_BREAK,
    SUPER_OVER,
    COMPLETED,
    ABANDONED,
    CANCELLED,
}

@Serializable
enum class ResultKind {
    WIN,
    TIE,
    NO_RESULT,
    ABANDONED,
}

@Serializable
data class SquadRules(
    val playersPerTeam: Int,
    val minimumPlayers: Int = 2,
    val maximumPlayers: Int = 15,
    val substitutesAllowed: Int = 0,
    val wicketkeeperRequired: Boolean = false,
    val captainRequired: Boolean = false,
    val twoBatters: Boolean = true,
)

@Serializable
data class OverRules(
    val ballsPerOver: Int = 6,
    val oversPerInnings: Int? = null,
    val inningsPerTeam: Int = 1,
    val maxOversPerBowler: Int? = null,
    val mustChangeBowlerEachOver: Boolean = false,
    val underarmOnly: Boolean = false,
    val requireUnderarmConfirm: Boolean = false,
)

@Serializable
data class ExtraRules(
    val widesEnabled: Boolean = true,
    val wideRuns: Int = 1,
    val wideCountsAsLegal: Boolean = false,
    val noBallsEnabled: Boolean = true,
    val noBallRuns: Int = 1,
    val noBallCountsAsLegal: Boolean = false,
    val freeHitEnabled: Boolean = false,
    val byesEnabled: Boolean = true,
    val legByesEnabled: Boolean = true,
    val penaltyRunsEnabled: Boolean = true,
    val overthrowsEnabled: Boolean = true,
    val maxRunsPerDelivery: Int? = null,
    val noBallCreditsBatter: Boolean = true,
    val noBallCountsAsBallFaced: Boolean = true,
)

@Serializable
data class BoundaryRules(
    val foursEnabled: Boolean = true,
    val sixesEnabled: Boolean = true,
    val fourRuns: Int = 4,
    val sixRuns: Int = 6,
    val runningBetweenWicketsEnabled: Boolean = true,
    val sideWallRuns: Int? = null,
    val backWallRuns: Int? = null,
    val ceilingRuns: Int? = null,
    val roofRuns: Int? = null,
    val roofIsDeadBall: Boolean = false,
    val netRuns: Int? = null,
    val zoneBonusRuns: Int? = null,
    val afterWallBoundaryRuns: Int? = null,
)

@Serializable
data class DismissalRules(
    val enabled: List<DismissalType>,
    val bowlerCredited: List<DismissalType>,
    val strikerOnly: List<DismissalType>,
    val fielderRequired: List<DismissalType>,
    val catchOffWallAllowed: Boolean = false,
)

@Serializable
data class StrikeRules(
    val rotateOnOddRuns: Boolean = true,
    val rotateAtEndOfOver: Boolean = true,
)

@Serializable
data class EndingRules(
    val endWhenAllOut: Boolean = true,
    val endWhenOversComplete: Boolean = true,
    val endWhenTargetReached: Boolean = true,
    val declarationsAllowed: Boolean = false,
    val followOnAllowed: Boolean = false,
    val followOnDeficit: Int? = null,
    val maximumWickets: Int? = null,
    val powerplayOvers: Int? = null,
)

@Serializable
data class TieBreakRules(
    val superOverEnabled: Boolean = false,
    val superOverLegalBalls: Int = 6,
    val superOverWickets: Int = 2,
)

@Serializable
data class GroundRules(
    val pitchLengthMeters: Double? = null,
    val pitchWidthMeters: Double? = null,
    val boundaryMeters: Double? = null,
    val hasWalls: Boolean = false,
    val hasCeiling: Boolean = false,
    val deadBallZones: List<String> = emptyList(),
    val ballNote: String? = null,
)

@Serializable
data class LocalRules(
    val oneTipOneHandEnabled: Boolean = false,
    val directHitDismissalEnabled: Boolean = false,
    val singleBounceDismissalEnabled: Boolean = false,
    val boxWallDismissalEnabled: Boolean = false,
    val designatedZoneDismissalEnabled: Boolean = false,
    val restrictedBattingZones: Boolean = false,
)

@Serializable
data class BowlingLabels(
    val bowlingRole: String = "Bowler",
)

@Serializable
enum class ScoringAwardMode {
    FIXED,
    BONUS,
}

@Serializable
data class AdditionalScoringEvent(
    val id: String,
    val name: String,
    val runs: Int,
    val enabled: Boolean = true,
    val awardMode: ScoringAwardMode = ScoringAwardMode.FIXED,
    val description: String = "",
)

@Serializable
data class AdditionalScoringRules(
    val events: List<AdditionalScoringEvent> = emptyList(),
)

@Serializable
data class LastManRules(
    val enabled: Boolean = false,
    val minTeamSize: Int = 4,
    val remainingBattersToActivate: Int = 1,
    val allowScoring: Boolean = true,
    val allowRunning: Boolean = false,
    val continueAfterAnotherDismissal: Boolean = false,
    val endOnDismissal: Boolean = true,
)

@Serializable
data class RosterRules(
    val preMatchTransfersAllowed: Boolean = true,
    val inMatchSwitchingAllowed: Boolean = false,
    val requireBothTeamApproval: Boolean = true,
    val allowInjuredReplacement: Boolean = true,
    val transferredPlayerMayRepresentReceiver: Boolean = true,
    val allowAddDuringMatch: Boolean = false,
    val allowUnevenTeamSizes: Boolean = false,
    val allowLoanToOpponent: Boolean = false,
    val receiverChoosesLoanPlayer: Boolean = false,
    val allowMultipleSwitches: Boolean = false,
    val newPlayerMayBatImmediately: Boolean = true,
    val newPlayerMayBowlImmediately: Boolean = true,
    val additionsOnlyEndOfOver: Boolean = false,
)

@Serializable
data class BoxOutRules(
    val enabled: Boolean = false,
    val description: String = "",
    val bowlerCredited: Boolean = false,
)

@Serializable
enum class RosterChangeKind {
    PRE_MATCH_TRANSFER,
    SWITCH,
    ADD,
    LOAN,
    REMOVE,
}

/**
 * Immutable playing conditions. A match stores its own copy when play starts.
 * Critical behaviour is typed. Nothing that changes a total, a legal ball, or a wicket
 * is read from an untyped map.
 */
@Serializable
data class RulesProfile(
    val id: String,
    val name: String,
    val style: CricketStyle,
    val source: RuleSource,
    val confirmedByOrganizer: Boolean = false,
    val squad: SquadRules,
    val overs: OverRules,
    val extras: ExtraRules,
    val boundaries: BoundaryRules,
    val dismissals: DismissalRules,
    val strike: StrikeRules,
    val endings: EndingRules,
    val tieBreak: TieBreakRules,
    val ground: GroundRules,
    val local: LocalRules,
    val labels: BowlingLabels = BowlingLabels(),
    val organizerPrompts: List<String> = emptyList(),
    val additionalScoring: AdditionalScoringRules = AdditionalScoringRules(),
    val lastMan: LastManRules = LastManRules(),
    val roster: RosterRules = RosterRules(),
    val boxOut: BoxOutRules = BoxOutRules(),
)

@Serializable
data class Player(
    val id: String,
    val name: String,
    val photoUri: String? = null,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val color: Int,
    val playerIds: List<String> = emptyList(),
    val captainId: String? = null,
    val wicketkeeperId: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class MatchPlayer(
    val id: String,
    val name: String,
)

@Serializable
data class MatchTeam(
    val teamId: String,
    val name: String,
    val shortName: String,
    val color: Int,
    val players: List<MatchPlayer>,
    val captainId: String? = null,
    val wicketkeeperId: String? = null,
)

@Serializable
data class Toss(
    val winnerTeamId: String? = null,
    val choice: TossChoice? = null,
    val skipped: Boolean = false,
)

@Serializable
data class OpeningPlayers(
    val strikerId: String,
    val nonStrikerId: String? = null,
    val bowlerId: String,
)

@Serializable
data class DismissalInput(
    val dismissalType: DismissalType,
    val batterId: String,
    val fielderId: String? = null,
)

@Serializable
data class BallInput(
    val batterRuns: Int = 0,
    val boundary: BoundaryMarker? = null,
    val surface: SurfaceContact = SurfaceContact.NONE,
    val wide: Boolean = false,
    val wideRunningRuns: Int = 0,
    val noBall: Boolean = false,
    val byeRuns: Int = 0,
    val legByeRuns: Int = 0,
    val overthrowBatterRuns: Int = 0,
    val overthrowExtraRuns: Int = 0,
    val penaltyRuns: Int = 0,
    val deadBall: Boolean = false,
    val dismissal: DismissalInput? = null,
    val bowlerOverrideId: String? = null,
    val strikerOverrideId: String? = null,
    val underarmConfirmed: Boolean = false,
    val commentary: String? = null,
    val timestampEpochMs: Long = 0,
    val scoringEventId: String? = null,
)

@Serializable
sealed interface MatchEvent {
    val id: String

    @Serializable
    data class Ball(
        override val id: String,
        val input: BallInput,
    ) : MatchEvent

    @Serializable
    data class NextBatter(
        override val id: String,
        val playerId: String,
    ) : MatchEvent

    @Serializable
    data class NextBowler(
        override val id: String,
        val playerId: String,
    ) : MatchEvent

    @Serializable
    data class CloseInnings(
        override val id: String,
        val reason: String,
    ) : MatchEvent

    @Serializable
    data class Abandon(
        override val id: String,
        val reason: String,
    ) : MatchEvent

    @Serializable
    data class Concede(
        override val id: String,
        val teamId: String,
    ) : MatchEvent

    @Serializable
    data class StartNextInnings(
        override val id: String,
        val strikerId: String,
        val nonStrikerId: String?,
        val bowlerId: String,
    ) : MatchEvent

    @Serializable
    data class StartSuperOver(
        override val id: String,
        val strikerId: String,
        val nonStrikerId: String?,
        val bowlerId: String,
    ) : MatchEvent

    @Serializable
    data class EnforceFollowOn(
        override val id: String,
    ) : MatchEvent

    @Serializable
    data class RetireBatter(
        override val id: String,
        val playerId: String,
        val hurt: Boolean,
    ) : MatchEvent

    @Serializable
    data class PlayerOfTheMatch(
        override val id: String,
        val playerId: String,
    ) : MatchEvent

    @Serializable
    data class SwitchPlayer(
        override val id: String,
        val teamId: String,
        val outgoingPlayerId: String,
        val incoming: MatchPlayer,
        val reason: String,
        val injured: Boolean = false,
        val approvalA: Boolean = false,
        val approvalB: Boolean = false,
        val organizerId: String? = null,
        val timestampEpochMs: Long = 0,
    ) : MatchEvent

    @Serializable
    data class AddPlayer(
        override val id: String,
        val teamId: String,
        val player: MatchPlayer,
        val reason: String,
        val approvalA: Boolean = false,
        val approvalB: Boolean = false,
        val organizerId: String? = null,
        val timestampEpochMs: Long = 0,
    ) : MatchEvent

    @Serializable
    data class LendPlayer(
        override val id: String,
        val fromTeamId: String,
        val toTeamId: String,
        val playerId: String,
        val reason: String,
        val temporary: Boolean = true,
        val approvalA: Boolean = false,
        val approvalB: Boolean = false,
        val organizerId: String? = null,
        val timestampEpochMs: Long = 0,
    ) : MatchEvent
}

@Serializable
data class Match(
    val id: String,
    val style: CricketStyle,
    val title: String,
    val teamA: MatchTeam,
    val teamB: MatchTeam,
    val rules: RulesProfile,
    val tournamentId: String? = null,
    val fixtureId: String? = null,
    val venue: String = "",
    val scheduledAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0,
    val updatedAtEpochMs: Long = 0,
    val toss: Toss? = null,
    val battingFirstTeamId: String? = null,
    val opening: OpeningPlayers? = null,
    val events: List<MatchEvent> = emptyList(),
    val cancelled: Boolean = false,
)

@Serializable
data class Fixture(
    val id: String,
    val round: String,
    val teamAId: String,
    val teamBId: String,
    val matchId: String? = null,
)

@Serializable
data class Tournament(
    val id: String,
    val name: String,
    val style: CricketStyle,
    val teamIds: List<String> = emptyList(),
    val rules: RulesProfile,
    val stage: CompetitionStage = CompetitionStage.LEAGUE,
    val pointsForWin: Int = 2,
    val pointsForTie: Int = 1,
    val pointsForNoResult: Int = 1,
    val fixtures: List<Fixture> = emptyList(),
)

@Serializable
data class AppData(
    val players: List<Player> = emptyList(),
    val teams: List<Team> = emptyList(),
    val matches: List<Match> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val ruleProfiles: List<RulesProfile> = emptyList(),
)

data class Extras(
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
    val penalties: Int = 0,
    val overthrows: Int = 0,
) {
    val total: Int get() = wides + noBalls + byes + legByes + penalties + overthrows

    operator fun plus(other: Extras) = Extras(
        wides = wides + other.wides,
        noBalls = noBalls + other.noBalls,
        byes = byes + other.byes,
        legByes = legByes + other.legByes,
        penalties = penalties + other.penalties,
        overthrows = overthrows + other.overthrows,
    )
}

data class AppliedDismissal(
    val dismissalType: DismissalType,
    val batterId: String,
    val fielderId: String?,
    val bowlerCredited: Boolean,
    val isWicket: Boolean,
)

data class AppliedDelivery(
    val eventId: String,
    val inningsIndex: Int,
    val overIndex: Int,
    val batterId: String,
    val nonStrikerId: String?,
    val bowlerId: String,
    val batterRuns: Int,
    val extras: Extras,
    val totalRuns: Int,
    val legal: Boolean,
    val countsAsBallFaced: Boolean,
    val dismissal: AppliedDismissal?,
    val symbol: String,
    val commentary: String?,
    val timestampEpochMs: Long,
    val surface: SurfaceContact,
    val boundary: BoundaryMarker?,
    val wasFreeHit: Boolean,
    val scoreAfter: Int,
    val wicketsAfter: Int,
    val legalBallsAfter: Int,
    val scoringEventId: String? = null,
    val scoringEventName: String? = null,
)

data class OverSummary(
    val overIndex: Int,
    val bowlerId: String?,
    val bowlerName: String,
    val symbols: List<String>,
    val deliveries: List<AppliedDelivery>,
    val runs: Int,
    val wickets: Int,
    val scoreAfter: Int,
    val wicketsAfter: Int,
)

data class RosterChangeRecord(
    val eventId: String,
    val kind: RosterChangeKind,
    val playerId: String,
    val playerName: String,
    val fromTeamId: String?,
    val toTeamId: String?,
    val outgoingPlayerId: String? = null,
    val reason: String,
    val timestampEpochMs: Long,
    val inningsIndex: Int?,
    val score: Int?,
    val wickets: Int?,
    val organizerId: String? = null,
    val approvalA: Boolean = false,
    val approvalB: Boolean = false,
    val temporary: Boolean = false,
)

data class BattingRow(
    val playerId: String,
    val name: String,
    val dismissalText: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val isOut: Boolean,
    val retiredHurt: Boolean,
    val yetToBat: Boolean,
)

data class BowlingRow(
    val playerId: String,
    val name: String,
    val legalBalls: Int,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val wides: Int,
    val noBalls: Int,
    val economy: Double,
)

data class FallOfWicket(
    val wicketNumber: Int,
    val teamScore: Int,
    val batterId: String,
    val batterName: String,
    val overLabel: String,
    val dismissalType: DismissalType,
)

data class Partnership(
    val batterIds: List<String>,
    val batterNames: List<String>,
    val runs: Int,
    val balls: Int,
    val startedAtEpochMs: Long?,
    val endedAtEpochMs: Long?,
)

data class DerivedInnings(
    val index: Int,
    val battingTeamId: String,
    val bowlingTeamId: String,
    val superOver: Boolean,
    val target: Int?,
    val maxLegalBalls: Int?,
    val maxWickets: Int,
    val ballsPerOver: Int,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    val complete: Boolean,
    val declared: Boolean,
    val allOut: Boolean,
    val deliveries: List<AppliedDelivery>,
    val battingRows: List<BattingRow>,
    val bowlingRows: List<BowlingRow>,
    val fallOfWickets: List<FallOfWicket>,
    val partnerships: List<Partnership>,
    val extras: Extras,
    val powerplayRuns: Int?,
    val yetToBat: List<String>,
    val strikerId: String?,
    val nonStrikerId: String?,
    val bowlerId: String?,
    val lastManStanding: Boolean = false,
    val overs: List<OverSummary> = emptyList(),
)

data class MatchResult(
    val winnerTeamId: String?,
    val kind: ResultKind,
    val marginRuns: Int? = null,
    val marginWickets: Int? = null,
    val wonByInnings: Boolean = false,
    val wonBySuperOver: Boolean = false,
    val summary: String,
)

data class DerivedMatch(
    val status: MatchStatus,
    val innings: List<DerivedInnings>,
    val currentInningsIndex: Int?,
    val result: MatchResult?,
    val needsBatter: Boolean,
    val needsBowler: Boolean,
    val breakPending: Boolean,
    val superOverPending: Boolean,
    val followOnAvailable: Boolean,
    val freeHit: Boolean,
    val playerOfTheMatchId: String?,
    val equation: String?,
    val currentRunRate: Double?,
    val requiredRunRate: Double?,
    val nextBattingTeamId: String? = null,
    val lastManStanding: Boolean = false,
    val liveTeamA: List<MatchPlayer> = emptyList(),
    val liveTeamB: List<MatchPlayer> = emptyList(),
    val rosterChanges: List<RosterChangeRecord> = emptyList(),
)

enum class EngineCode {
    OK,
    DUPLICATE,
    REJECTED,
}

data class EngineOutcome(
    val code: EngineCode,
    val message: String? = null,
    val match: Match? = null,
    val derived: DerivedMatch? = null,
)
