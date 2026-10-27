package com.example.cricketscoring.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.model.AdditionalScoringEvent
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.ScoringAwardMode
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.RuleSource
import com.example.cricketscoring.model.Team
import com.example.cricketscoring.model.TossChoice
import com.example.cricketscoring.rules.boxDefaults
import com.example.cricketscoring.rules.defaultRules
import com.example.cricketscoring.rules.openDefaults
import com.example.cricketscoring.rules.overarmLimited
import com.example.cricketscoring.rules.overarmMultiInnings
import com.example.cricketscoring.rules.styleTitle

@Composable
fun WizardScreen(store: CricketStore) {
    val draft = store.draft
    if (draft == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This setup was closed.")
            SecondaryButton("Back", { store.pop() })
        }
        return
    }
    val edit: ((MatchDraft) -> MatchDraft) -> Unit = { block ->
        store.draft = store.draft?.let(block)
    }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "New match",
            onBack = {
                if (draft.step > 1) edit { it.copy(step = it.step - 1) } else {
                    store.draft = null
                    store.pop()
                }
            },
        )
        Text(
            "Step ${draft.step} of 4 · ${styleTitle(draft.style)}",
            color = draft.style.accent(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (draft.step) {
                1 -> TeamsStep(store, draft, edit, onCreate = { creating = true })
                2 -> FormatStep(store, draft, edit)
                3 -> RulesStep(store, draft, edit)
                else -> ConfirmStep(store, draft, edit)
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.step < 4) {
                PrimaryButton("Continue", { advance(store, draft, edit) }, Modifier.weight(1f))
            } else {
                SecondaryButton("Save for later", { store.commitDraft(start = false) }, Modifier.weight(1f))
                PrimaryButton("Start match", { store.commitDraft(start = true) }, Modifier.weight(1f))
            }
        }
    }
    if (creating) {
        CreateTeamDialog(
            onDismiss = { creating = false },
            onCreate = { name, players ->
                val team = store.createTeam(name, players, teamPalette[store.data.teams.size % teamPalette.size])
                if (team != null) {
                    edit { current ->
                        if (current.teamAId == null) current.copy(teamAId = team.id) else current.copy(teamBId = team.id)
                    }
                    creating = false
                }
            },
        )
    }
}

private fun advance(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val problem = when (draft.step) {
        1 -> when {
            draft.teamAId == null || draft.teamBId == null -> "Choose both teams."
            draft.teamAId == draft.teamBId -> "Choose two different teams."
            else -> null
        }
        2 -> when {
            draft.rules.squad.playersPerTeam !in 2..15 -> "Use between 2 and 15 players."
            draft.rules.overs.ballsPerOver !in 1..10 -> "Use between 1 and 10 balls in an over."
            draft.rules.overs.inningsPerTeam !in 1..2 -> "Use 1 or 2 innings a side."
            draft.rules.overs.oversPerInnings == null && draft.rules.overs.inningsPerTeam == 1 -> "Set the overs, or use more than one innings."
            else -> null
        }
        else -> null
    }
    if (problem != null) {
        store.message = problem
        return
    }
    edit { current ->
        val seeded = if (current.step == 2) seedSquads(store, current) else current
        seeded.copy(step = current.step + 1)
    }
}

private fun seedSquads(store: CricketStore, draft: MatchDraft): MatchDraft {
    val size = draft.rules.squad.playersPerTeam
    fun seed(teamId: String?, current: List<String>): List<String> {
        val team = store.data.teams.find { it.id == teamId } ?: return emptyList()
        if (current.size == size && current.all { it in team.playerIds }) return current
        val preferred = listOfNotNull(team.captainId, team.wicketkeeperId) + team.playerIds
        return preferred.distinct().filter { it in team.playerIds }.take(size)
    }
    return draft.copy(squadA = seed(draft.teamAId, draft.squadA), squadB = seed(draft.teamBId, draft.squadB))
}

@Composable
private fun TeamsStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit, onCreate: () -> Unit) {
    val teams = store.data.teams.filterNot { it.archived }
    Text("Select or create the two teams. Players can be reused in every cricket style.", color = Muted)
    if (teams.isEmpty()) {
        EmptyHint("No teams yet", "Add both sides with one player name on each line.", "Create a team", onCreate)
        return
    }
    SectionLabel("Team A")
    teams.forEach { team ->
        TeamPick(team, draft.teamAId == team.id, store) { edit { it.copy(teamAId = team.id, squadA = emptyList()) } }
    }
    SectionLabel("Team B")
    teams.forEach { team ->
        TeamPick(team, draft.teamBId == team.id, store) { edit { it.copy(teamBId = team.id, squadB = emptyList()) } }
    }
    SecondaryButton("Create a team", onCreate)
}

@Composable
private fun TeamPick(team: Team, selected: Boolean, store: CricketStore, onClick: () -> Unit) {
    PitchCard(onClick = onClick) {
        Text(if (selected) "Selected · ${team.name}" else team.name, color = if (selected) Lawn else MaterialTheme.colorScheme.onSurface)
        Text(team.playerIds.joinToString { store.playerName(it) }, color = Muted)
    }
}

@Composable
private fun FormatStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    NameField("Match title", draft.title.ifBlank { "${teamA?.name.orEmpty()} v ${teamB?.name.orEmpty()}" }) { value ->
        edit { it.copy(title = value) }
    }
    NameField("Ground", draft.venue) { value -> edit { it.copy(venue = value) } }
    if (draft.style == CricketStyle.OVERARM) {
        SectionLabel("Format preset")
        Text("Presets fill a starting point. Change any number after you pick one.", color = Muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("T10" to overarmLimited(10, "T10"), "T20" to overarmLimited(20, "T20"), "50 overs" to overarmLimited(50, "50 overs"), "Multi-innings" to overarmMultiInnings()).forEach { (label, preset) ->
                SecondaryButton(label, {
                    edit { it.copy(rules = preset.copy(id = it.rules.id), squadA = emptyList(), squadB = emptyList()) }
                }, Modifier.fillMaxWidth())
            }
        }
    }
    IntField("Players per team", draft.rules.squad.playersPerTeam) { value ->
        value?.let { count -> edit { it.copy(rules = it.rules.copy(squad = it.rules.squad.copy(playersPerTeam = count)), squadA = emptyList(), squadB = emptyList()) } }
    }
    IntField("Balls in an over", draft.rules.overs.ballsPerOver) { value ->
        value?.let { count -> edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(ballsPerOver = count))) } }
    }
    IntField("Overs in an innings (empty means no limit)", draft.rules.overs.oversPerInnings) { value ->
        edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(oversPerInnings = value))) }
    }
    IntField("Innings per team", draft.rules.overs.inningsPerTeam) { value ->
        value?.let { count ->
            edit {
                it.copy(
                    rules = it.rules.copy(
                        overs = it.rules.overs.copy(inningsPerTeam = count),
                        endings = it.rules.endings.copy(declarationsAllowed = count > 1, followOnAllowed = count > 1),
                    ),
                )
            }
        }
    }
    IntField("Maximum overs per bowler (empty means no cap)", draft.rules.overs.maxOversPerBowler) { value ->
        edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(maxOversPerBowler = value))) }
    }
}

@Composable
private fun RulesStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val rules = draft.rules
    fun rules(block: (com.example.cricketscoring.model.RulesProfile) -> com.example.cricketscoring.model.RulesProfile) {
        edit { it.copy(rules = block(it.rules).copy(source = RuleSource.CUSTOM)) }
    }
    Text("Recommended rules for ${styleTitle(draft.style)} are ready. Open Advanced rules only if this ground plays differently.", color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton("Recommended", {
            val fresh = when (draft.style) {
                CricketStyle.UNDERARM_BOX -> boxDefaults()
                CricketStyle.UNDERARM_OPEN -> openDefaults()
                CricketStyle.OVERARM -> defaultRules(draft.style)
            }
            edit { it.copy(rules = fresh.copy(id = it.rules.id)) }
        }, Modifier.weight(1f))
        SecondaryButton("Save a copy", { store.saveProfile(rules) }, Modifier.weight(1f))
    }
    val saved = store.data.ruleProfiles.filter { it.style == draft.style } + store.data.tournaments.filter { it.style == draft.style }.map { it.rules.copy(name = it.name) }
    saved.forEach { profile ->
        SecondaryButton(profile.name, {
            edit { it.copy(rules = profile.copy(id = it.rules.id), tournamentId = store.data.tournaments.find { tournament -> tournament.rules.id == profile.id }?.id) }
        }, Modifier.fillMaxWidth())
    }
    Disclosure("Advanced rules", "Custom runs, dismissals, player changes, and team sizes stay on their recommended settings until you open this.") {
    SectionLabel("Match format")
    ToggleRow("Two batters at the crease", rules.squad.twoBatters) { on -> rules { it.copy(squad = it.squad.copy(twoBatters = on)) } }
    ToggleRow("Running between the wickets", rules.boundaries.runningBetweenWicketsEnabled) { on ->
        rules { it.copy(boundaries = it.boundaries.copy(runningBetweenWicketsEnabled = on)) }
    }
    SectionLabel("Scoring rules")
    ToggleRow("Wides", rules.extras.widesEnabled) { on -> rules { it.copy(extras = it.extras.copy(widesEnabled = on)) } }
    ToggleRow("No-balls", rules.extras.noBallsEnabled) { on -> rules { it.copy(extras = it.extras.copy(noBallsEnabled = on)) } }
    ToggleRow("Byes", rules.extras.byesEnabled) { on -> rules { it.copy(extras = it.extras.copy(byesEnabled = on)) } }
    ToggleRow("Leg-byes", rules.extras.legByesEnabled) { on -> rules { it.copy(extras = it.extras.copy(legByesEnabled = on)) } }
    ToggleRow("Free hit after a no-ball", rules.extras.freeHitEnabled) { on -> rules { it.copy(extras = it.extras.copy(freeHitEnabled = on)) } }
    ToggleRow("Fours", rules.boundaries.foursEnabled) { on -> rules { it.copy(boundaries = it.boundaries.copy(foursEnabled = on)) } }
    ToggleRow("Sixes", rules.boundaries.sixesEnabled) { on -> rules { it.copy(boundaries = it.boundaries.copy(sixesEnabled = on)) } }
    IntField("Runs for a four", rules.boundaries.fourRuns) { value -> value?.let { count -> rules { it.copy(boundaries = it.boundaries.copy(fourRuns = count)) } } }
    IntField("Runs for a six", rules.boundaries.sixRuns) { value -> value?.let { count -> rules { it.copy(boundaries = it.boundaries.copy(sixRuns = count)) } } }
    ToggleRow("Super Over if the scores are level", rules.tieBreak.superOverEnabled) { on ->
        rules { it.copy(tieBreak = it.tieBreak.copy(superOverEnabled = on, superOverLegalBalls = it.overs.ballsPerOver)) }
    }
    if (draft.style == CricketStyle.UNDERARM_BOX) {
        SectionLabel("Box surfaces")
        Text("Leave a box blank to hide that button. Confirm the numbers with the ground.", color = Muted)
        IntField("Side wall", rules.boundaries.sideWallRuns) { value -> rules { it.copy(boundaries = it.boundaries.copy(sideWallRuns = value)) } }
        IntField("Back wall", rules.boundaries.backWallRuns) { value -> rules { it.copy(boundaries = it.boundaries.copy(backWallRuns = value)) } }
        IntField("Ceiling", rules.boundaries.ceilingRuns) { value -> rules { it.copy(boundaries = it.boundaries.copy(ceilingRuns = value)) } }
        IntField("Net", rules.boundaries.netRuns) { value -> rules { it.copy(boundaries = it.boundaries.copy(netRuns = value)) } }
        ToggleRow("Roof contact is a dead ball", rules.boundaries.roofIsDeadBall) { on -> rules { it.copy(boundaries = it.boundaries.copy(roofIsDeadBall = on)) } }
        ToggleRow("Catch off a wall or net", rules.dismissals.catchOffWallAllowed) { on -> rules { it.copy(dismissals = it.dismissals.copy(catchOffWallAllowed = on)) } }
    }
    SectionLabel("Dismissal rules")
    if (draft.style != CricketStyle.OVERARM) {
        Text("These stay off until this match turns them on.", color = Muted)
        ToggleRow("One-tip-one-hand", rules.local.oneTipOneHandEnabled) { on -> rules { it.copy(local = it.local.copy(oneTipOneHandEnabled = on)) } }
        ToggleRow("Direct hit", rules.local.directHitDismissalEnabled) { on -> rules { it.copy(local = it.local.copy(directHitDismissalEnabled = on)) } }
        ToggleRow("Single bounce", rules.local.singleBounceDismissalEnabled) { on -> rules { it.copy(local = it.local.copy(singleBounceDismissalEnabled = on)) } }
    }
    if (draft.style == CricketStyle.OVERARM) {
        ToggleRow("LBW", DismissalType.LBW in rules.dismissals.enabled) { on -> rules { it.withDismissal(DismissalType.LBW, on) } }
        ToggleRow("Stumped", DismissalType.STUMPED in rules.dismissals.enabled) { on -> rules { it.withDismissal(DismissalType.STUMPED, on) } }
        if (rules.overs.inningsPerTeam > 1) {
            ToggleRow("Declarations", rules.endings.declarationsAllowed) { on -> rules { it.copy(endings = it.endings.copy(declarationsAllowed = on)) } }
            ToggleRow("Follow-on", rules.endings.followOnAllowed) { on -> rules { it.copy(endings = it.endings.copy(followOnAllowed = on)) } }
            IntField("Follow-on deficit", rules.endings.followOnDeficit) { value -> rules { it.copy(endings = it.endings.copy(followOnDeficit = value)) } }
        }
    }
    IntField("Maximum runs on one ball (empty means none)", rules.extras.maxRunsPerDelivery) { value ->
        rules { it.copy(extras = it.extras.copy(maxRunsPerDelivery = value)) }
    }
    NameField("Bowling label", rules.labels.bowlingRole) { value ->
        rules { it.copy(labels = it.labels.copy(bowlingRole = value.ifBlank { "Bowler" })) }
    }
    ToggleRow("Require a different bowler each over", rules.overs.mustChangeBowlerEachOver) { on ->
        rules { it.copy(overs = it.overs.copy(mustChangeBowlerEachOver = on)) }
    }
    ProvisionsEditor(draft) { block -> rules(block) }
    }
}

@Composable
private fun ProvisionsEditor(draft: MatchDraft, rules: ((RulesProfile) -> RulesProfile) -> Unit) {
    val profile = draft.rules
    SectionLabel("Additional scoring rules")
    Text("These values stay locked after the first ball.", color = Muted)
    profile.additionalScoring.events.forEach { event ->
        ToggleRow("${event.name} · ${event.runs}", event.enabled) { on ->
            rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item -> if (item.id == event.id) item.copy(enabled = on) else item })) }
        }
        if (event.enabled) {
            NameField(event.name, event.name) { value ->
                rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item -> if (item.id == event.id) item.copy(name = value.ifBlank { item.name }) else item })) }
            }
            IntField("Runs for ${event.name}", event.runs) { value ->
                value?.let { runs ->
                    rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item -> if (item.id == event.id) item.copy(runs = runs) else item })) }
                }
            }
            ChoiceRow(listOf("Fixed total" to (event.awardMode == ScoringAwardMode.FIXED), "Bonus on top" to (event.awardMode == ScoringAwardMode.BONUS))) { index ->
                val mode = if (index == 0) ScoringAwardMode.FIXED else ScoringAwardMode.BONUS
                rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item -> if (item.id == event.id) item.copy(awardMode = mode) else item })) }
            }
            NameField("How it is awarded", event.description) { value ->
                rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item -> if (item.id == event.id) item.copy(description = value) else item })) }
            }
            SecondaryButton("Remove ${event.name}", {
                rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.filter { item -> item.id != event.id })) }
            })
        }
    }
    SecondaryButton("Add scoring event", {
        rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events + AdditionalScoringEvent(freshId("score"), "Custom scoring event", 10, enabled = true, description = "Local tournament rule"))) }
    })
    SectionLabel("Box Out")
    ToggleRow("Box Out", profile.boxOut.enabled) { on -> rules { it.copy(boxOut = it.boxOut.copy(enabled = on)) } }
    if (profile.boxOut.enabled) {
        NameField("What counts as Box Out", profile.boxOut.description) { value -> rules { it.copy(boxOut = it.boxOut.copy(description = value)) } }
        ToggleRow("Credit the bowler", profile.boxOut.bowlerCredited) { on -> rules { it.copy(boxOut = it.boxOut.copy(bowlerCredited = on)) } }
    }
    SectionLabel("Last-man standing")
    ToggleRow("Allow last-man standing", profile.lastMan.enabled) { on -> rules { it.copy(lastMan = it.lastMan.copy(enabled = on)) } }
    Text("The team-size minimum and the number of batters left are separate. A team of four does not start the rule after the first wicket.", color = Muted)
    if (profile.lastMan.enabled) {
        IntField("Minimum team size", profile.lastMan.minTeamSize) { value -> value?.let { count -> rules { it.copy(lastMan = it.lastMan.copy(minTeamSize = count)) } } }
        IntField("Batters remaining when it starts", profile.lastMan.remainingBattersToActivate) { value ->
            value?.let { count -> rules { it.copy(lastMan = it.lastMan.copy(remainingBattersToActivate = count)) } }
        }
        ToggleRow("Last batter can score", profile.lastMan.allowScoring) { on -> rules { it.copy(lastMan = it.lastMan.copy(allowScoring = on)) } }
        ToggleRow("Last batter can run", profile.lastMan.allowRunning) { on -> rules { it.copy(lastMan = it.lastMan.copy(allowRunning = on)) } }
        ToggleRow("Continue after another dismissal", profile.lastMan.continueAfterAnotherDismissal) { on -> rules { it.copy(lastMan = it.lastMan.copy(continueAfterAnotherDismissal = on)) } }
        ToggleRow("A dismissal can end the innings", profile.lastMan.endOnDismissal) { on -> rules { it.copy(lastMan = it.lastMan.copy(endOnDismissal = on)) } }
    }
    SectionLabel("Player management")
    val roster = profile.roster
    ToggleRow("Allow player changes before the match", roster.preMatchTransfersAllowed) { on -> rules { it.copy(roster = it.roster.copy(preMatchTransfersAllowed = on)) } }
    ToggleRow("Allow player changes during the match", roster.inMatchSwitchingAllowed) { on -> rules { it.copy(roster = it.roster.copy(inMatchSwitchingAllowed = on)) } }
    ToggleRow("Both teams must approve changes", roster.requireBothTeamApproval) { on -> rules { it.copy(roster = it.roster.copy(requireBothTeamApproval = on)) } }
    ToggleRow("Replace an injured player", roster.allowInjuredReplacement) { on -> rules { it.copy(roster = it.roster.copy(allowInjuredReplacement = on)) } }
    ToggleRow("Transferred player can represent the new team", roster.transferredPlayerMayRepresentReceiver) { on -> rules { it.copy(roster = it.roster.copy(transferredPlayerMayRepresentReceiver = on)) } }
    ToggleRow("Add players during the match", roster.allowAddDuringMatch) { on -> rules { it.copy(roster = it.roster.copy(allowAddDuringMatch = on)) } }
    SectionLabel("Team balancing")
    ToggleRow("Allow uneven teams", roster.allowUnevenTeamSizes) { on -> rules { it.copy(roster = it.roster.copy(allowUnevenTeamSizes = on)) } }
    ToggleRow("Allow player loans", roster.allowLoanToOpponent) { on -> rules { it.copy(roster = it.roster.copy(allowLoanToOpponent = on)) } }
    ToggleRow("Receiving team chooses the loaned player", roster.receiverChoosesLoanPlayer) { on -> rules { it.copy(roster = it.roster.copy(receiverChoosesLoanPlayer = on)) } }
    ToggleRow("A player may change teams more than once", roster.allowMultipleSwitches) { on -> rules { it.copy(roster = it.roster.copy(allowMultipleSwitches = on)) } }
    ToggleRow("New player can bat immediately", roster.newPlayerMayBatImmediately) { on -> rules { it.copy(roster = it.roster.copy(newPlayerMayBatImmediately = on)) } }
    ToggleRow("New player can bowl immediately", roster.newPlayerMayBowlImmediately) { on -> rules { it.copy(roster = it.roster.copy(newPlayerMayBowlImmediately = on)) } }
    ToggleRow("Add players only at the end of an over", roster.additionsOnlyEndOfOver) { on -> rules { it.copy(roster = it.roster.copy(additionsOnlyEndOfOver = on)) } }
    ToggleRow("No-ball runs off the bat count for the batter", profile.extras.noBallCreditsBatter) { on ->
        rules { it.copy(extras = it.extras.copy(noBallCreditsBatter = on)) }
    }
}

@Composable
private fun ConfirmStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val rules = draft.rules
    var approvalA by remember { mutableStateOf(false) }
    var approvalB by remember { mutableStateOf(false) }
    val teamAName = store.data.teams.find { it.id == draft.teamAId }?.name ?: "Team A"
    val teamBName = store.data.teams.find { it.id == draft.teamBId }?.name ?: "Team B"
    Text("$teamAName vs $teamBName", style = MaterialTheme.typography.titleLarge)
    Text(
        "${draft.rules.overs.oversPerInnings ?: "No"} overs · ${draft.rules.squad.playersPerTeam} players · ${draft.rules.overs.inningsPerTeam} innings a side",
        color = Muted,
    )
    SectionLabel("Who bats first")
    TossStep(store, draft, edit)
    SectionLabel("Opening players")
    OpenersStep(store, draft, edit)
    Text("Check this list, then confirm. Those choices stay locked after the first ball.", color = Muted)
    SectionLabel("Additional scoring")
    val enabled = rules.additionalScoring.events.filter { it.enabled }
    if (enabled.isEmpty()) Text("No extra scoring values.", color = Muted)
    enabled.forEach { event ->
        Text("${event.name}: ${event.runs} ${if (event.awardMode == ScoringAwardMode.BONUS) "bonus" else "fixed"}")
        if (event.description.isNotBlank()) Text(event.description, color = Muted)
    }
    SectionLabel("Box Out")
    Text(if (!rules.boxOut.enabled) "Off" else rules.boxOut.description.ifBlank { "On. Describe the local rule before you start." })
    SectionLabel("Players")
    Text("Pre-match transfers: ${yesNo(rules.roster.preMatchTransfersAllowed)}")
    Text("In-match switching: ${yesNo(rules.roster.inMatchSwitchingAllowed)}")
    Text("Add players during the match: ${yesNo(rules.roster.allowAddDuringMatch)}")
    Text("Uneven team sizes: ${yesNo(rules.roster.allowUnevenTeamSizes)}")
    Text("Player loan: ${yesNo(rules.roster.allowLoanToOpponent)}")
    Text("Both teams approve changes: ${yesNo(rules.roster.requireBothTeamApproval)}")
    SectionLabel("Last-man standing")
    Text(
        if (!rules.lastMan.enabled) "Off" else "On when a team has at least ${rules.lastMan.minTeamSize} players and ${rules.lastMan.remainingBattersToActivate} batter${if (rules.lastMan.remainingBattersToActivate == 1) "" else "s"} remain.",
    )
    SectionLabel("No-ball")
    Text(if (rules.extras.noBallCreditsBatter) "Runs off the bat count for the batter. The no-ball penalty is an extra." else "Runs off the bat on a no-ball are extras, not batter runs.")
    Text("The live scorecard, over breakdown, and commentary stay available without leaving the match.")
    if (rules.roster.preMatchTransfersAllowed) {
        SectionLabel("Move a player before the start")
        Text("A transfer changes who plays for which side in this match. A substitution during the match is a separate action.", color = Muted)
        if (rules.roster.requireBothTeamApproval) {
            ToggleRow("${store.data.teams.find { it.id == draft.teamAId }?.name ?: "Team A"} approves", approvalA) { approvalA = it }
            ToggleRow("${store.data.teams.find { it.id == draft.teamBId }?.name ?: "Team B"} approves", approvalB) { approvalB = it }
        }
        draft.squadA.forEach { id ->
            SecondaryButton("Move ${store.playerName(id)} to the other squad", {
                moveSquadPlayer(store, draft, edit, id, toSquadB = true, approvalA, approvalB)
            })
        }
        draft.squadB.forEach { id ->
            SecondaryButton("Move ${store.playerName(id)} to the first squad", {
                moveSquadPlayer(store, draft, edit, id, toSquadB = false, approvalA, approvalB)
            })
        }
    }
    ToggleRow("These rules were checked with the organizer", rules.confirmedByOrganizer) { on ->
        edit { it.copy(rules = it.rules.copy(confirmedByOrganizer = on)) }
    }
}

private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

private fun moveSquadPlayer(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    playerId: String,
    toSquadB: Boolean,
    approvalA: Boolean,
    approvalB: Boolean,
) {
    if (draft.rules.roster.requireBothTeamApproval && (!approvalA || !approvalB)) {
        store.message = "Both teams have to approve this transfer."
        return
    }
    edit { current ->
        val next = if (toSquadB) {
            current.copy(squadA = current.squadA - playerId, squadB = (current.squadB + playerId).distinct())
        } else {
            current.copy(squadB = current.squadB - playerId, squadA = (current.squadA + playerId).distinct())
        }
        val batting = battingFirstId(next)
        val battingSquad = if (batting == next.teamBId) next.squadB else next.squadA
        val bowlingSquad = if (batting == next.teamBId) next.squadA else next.squadB
        next.copy(
            strikerId = next.strikerId.takeIf { it in battingSquad },
            nonStrikerId = next.nonStrikerId.takeIf { it in battingSquad && it != next.strikerId },
            bowlerId = next.bowlerId.takeIf { it in bowlingSquad },
        )
    }
}

@Composable
private fun TossStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    Text("Record the toss, or pick the batting side directly.", color = Muted)
    ToggleRow("No toss", draft.skipToss) { on -> edit { it.copy(skipToss = on) } }
    if (draft.skipToss) {
        SectionLabel("Who bats first?")
        teamA?.let { TeamPick(it, draft.manualBattingId == it.id, store) { edit { current -> current.copy(manualBattingId = it.id) } } }
        teamB?.let { TeamPick(it, draft.manualBattingId == it.id, store) { edit { current -> current.copy(manualBattingId = it.id) } } }
    } else {
        SectionLabel("Toss won by")
        teamA?.let { TeamPick(it, draft.tossWinnerId == it.id, store) { edit { current -> current.copy(tossWinnerId = it.id) } } }
        teamB?.let { TeamPick(it, draft.tossWinnerId == it.id, store) { edit { current -> current.copy(tossWinnerId = it.id) } } }
        SectionLabel("Decision")
        ChoiceRow(listOf("Bat" to (draft.tossChoice == TossChoice.BAT), "Bowl" to (draft.tossChoice == TossChoice.BOWL))) { index ->
            edit { it.copy(tossChoice = if (index == 0) TossChoice.BAT else TossChoice.BOWL) }
        }
    }
    battingFirstId(draft)?.let { batting ->
        Text("${store.data.teams.find { it.id == batting }?.name ?: "The batting side"} will bat first.", color = Lawn)
    }
}

@Composable
private fun OpenersStep(store: CricketStore, draft: MatchDraft, edit: ((MatchDraft) -> MatchDraft) -> Unit) {
    val battingId = battingFirstId(draft)
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    if (teamA == null || teamB == null || battingId == null) {
        Text("Go back and choose the teams and who bats first.")
        return
    }
    val batting = if (battingId == teamB.id) teamB else teamA
    val bowling = if (battingId == teamB.id) teamA else teamB
    val squadASelected = if (batting.id == teamA.id) draft.squadA else draft.squadB
    val squadBSelected = if (bowling.id == teamA.id) draft.squadA else draft.squadB
    val limit = draft.rules.squad.playersPerTeam
    Text("Playing squads, then the opening batters and ${draft.rules.labels.bowlingRole.lowercase()}.", color = Muted)
    SquadPicker("${batting.name} · $limit players", batting, squadASelected, limit, store) { selected ->
        edit { current -> if (batting.id == current.teamAId) current.copy(squadA = selected) else current.copy(squadB = selected) }
    }
    SquadPicker("${bowling.name} · $limit players", bowling, squadBSelected, limit, store) { selected ->
        edit { current -> if (bowling.id == current.teamAId) current.copy(squadA = selected) else current.copy(squadB = selected) }
    }
    if (squadASelected.isNotEmpty()) {
        SectionLabel("Striker")
        squadASelected.forEach { id ->
            PickRow(store.playerName(id), draft.strikerId == id) { edit { it.copy(strikerId = id) } }
        }
        if (draft.rules.squad.twoBatters) {
            SectionLabel("Non-striker")
            squadASelected.filter { it != draft.strikerId }.forEach { id ->
                PickRow(store.playerName(id), draft.nonStrikerId == id) { edit { it.copy(nonStrikerId = id) } }
            }
        }
    }
    if (squadBSelected.isNotEmpty()) {
        SectionLabel(draft.rules.labels.bowlingRole)
        squadBSelected.forEach { id ->
            PickRow(store.playerName(id), draft.bowlerId == id) { edit { it.copy(bowlerId = id) } }
        }
    }
    if (draft.rules.squad.captainRequired || draft.rules.squad.wicketkeeperRequired) {
        Notice("Captain and wicketkeeper default to the first player in each squad. Change them from the team page before the match if you need someone else.")
    }
    SectionLabel("Rules in this match")
    Text(draft.rules.name)
    Text("${draft.rules.squad.playersPerTeam} players · ${draft.rules.overs.oversPerInnings ?: "unlimited"} overs · ${draft.rules.overs.ballsPerOver} balls", color = Muted)
    draft.rules.organizerPrompts.take(3).forEach { prompt -> Notice(prompt) }
}

@Composable
private fun SquadPicker(title: String, team: Team, selected: List<String>, limit: Int, store: CricketStore, onChange: (List<String>) -> Unit) {
    SectionLabel("$title · ${selected.size} selected")
    if (team.playerIds.size < limit) {
        Notice("${team.name} only has ${team.playerIds.size} players. Add more, or lower the squad size.")
    }
    team.playerIds.forEach { id ->
        val on = id in selected
        PickRow(store.playerName(id), on) {
            onChange(
                when {
                    on -> selected - id
                    selected.size >= limit -> selected
                    else -> selected + id
                },
            )
        }
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●  $label" else "○  $label", color = if (selected) Lawn else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CreateTeamDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var players by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField("Team name", name) { name = it }
                NameField("Players, one name per line", players, singleLine = false) { players = it }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onCreate(name, players) }) { Text("Save team") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun com.example.cricketscoring.model.RulesProfile.withDismissal(type: DismissalType, enabled: Boolean) = copy(
    dismissals = dismissals.copy(
        enabled = if (enabled) (dismissals.enabled + type).distinct() else dismissals.enabled.filterNot { it == type },
    ),
)
