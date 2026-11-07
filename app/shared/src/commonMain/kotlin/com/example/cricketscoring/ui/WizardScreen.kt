package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.model.AdditionalScoringEvent
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.RuleSource
import com.example.cricketscoring.model.RulesProfile
import com.example.cricketscoring.model.ScoringAwardMode
import com.example.cricketscoring.model.Team
import com.example.cricketscoring.model.TossChoice
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.rules.boxDefaults
import com.example.cricketscoring.rules.defaultRules
import com.example.cricketscoring.rules.openDefaults
import com.example.cricketscoring.rules.overarmLimited
import com.example.cricketscoring.rules.overarmMultiInnings
import com.example.cricketscoring.rules.styleTitle
import org.jetbrains.compose.ui.tooling.preview.Preview

private val wizardLabels = listOf("Teams", "Format", "Start")

@Composable
fun WizardScreen(store: CricketStore) {
    val navigator = LocalAppNavigator.current
    val draft = store.draft
    if (draft == null) {
        CommonColumn(scrollable = false, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This setup was closed.")
            SecondaryButton("Back", { navigator.back() })
        }
        return
    }
    val edit: ((MatchDraft) -> MatchDraft) -> Unit = { block ->
        store.draft = store.draft?.let(block)
    }
    val clearGuidance: () -> Unit = {
        if (draft.guidance != null || draft.focus != null) {
            edit { it.copy(guidance = null, focus = null) }
        }
    }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "New match",
            onBack = {
                if (draft.step > 1) edit {
                    it.copy(
                        step = it.step - 1,
                        guidance = null,
                        focus = null
                    )
                } else {
                    store.draft = null
                    navigator.back()
                }
            },
        )
        CommonColumn(
            modifier = Modifier.weight(1f),
            scrollable = true,
            verticalPadding = 8.dp,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepProgress(
                step = draft.step,
                total = 3,
                labels = wizardLabels,
                accent = draft.style.accent(),
                onStepClick = { target ->
                    edit {
                        it.copy(
                            step = target,
                            guidance = null,
                            focus = null
                        )
                    }
                },
            )
            draft.guidance?.let { guidance ->
                SetupGuidance(guidance) { clearGuidance() }
            }
            when (draft.step) {
                1 -> TeamsStep(
                    store,
                    draft,
                    edit,
                    onCreate = { creating = true },
                    onChanged = clearGuidance
                )

                2 -> FormatStep(store, draft, edit, onChanged = clearGuidance)
                else -> ConfirmStep(store, draft, edit, onChanged = clearGuidance)
            }
            Spacer(Modifier.height(8.dp))
        }
        ActionDock {
            if (draft.step < 3) {
                PrimaryButton(
                    when (draft.step) {
                        1 -> "Next · Format"
                        else -> "Next · Start"
                    },
                    { advance(store, draft, edit) },
                    Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton("Start match", {
                    store.commitDraft(start = true)
                        ?.let { navigator.afterCommit(it, started = true) }
                }, Modifier.fillMaxWidth())
                SecondaryButton("Save for later", {
                    store.commitDraft(start = false)
                        ?.let { navigator.afterCommit(it, started = false) }
                }, Modifier.fillMaxWidth())
            }
        }
    }
    if (creating) {
        CreateTeamSheet(
            onDismiss = { creating = false },
            onCreate = { name, players ->
                val team = store.createTeam(
                    name,
                    players,
                    teamPalette[store.data.teams.size % teamPalette.size]
                )
                if (team != null) {
                    edit { current ->
                        if (current.teamAId == null) {
                            current.copy(teamAId = team.id, guidance = null, focus = null)
                        } else {
                            current.copy(teamBId = team.id, guidance = null, focus = null)
                        }
                    }
                    creating = false
                }
            },
        )
    }
}

private fun advance(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit
) {
    val issue = wizardIssue(store, draft, requireStartFields = false)
    if (issue != null && issue.step <= draft.step) {
        edit { it.copy(step = issue.step, guidance = issue.message, focus = issue.focus) }
        store.showToast(issue.message, AppToastType.Warning)
        return
    }
    edit { current ->
        val seeded =
            if (current.step == 2) seedSquads(store, current).withDefaultOpeners() else current
        seeded.copy(step = (current.step + 1).coerceAtMost(3), guidance = null, focus = null)
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
    return draft.copy(
        squadA = seed(draft.teamAId, draft.squadA),
        squadB = seed(draft.teamBId, draft.squadB)
    )
}

private fun MatchDraft.withDefaultOpeners(): MatchDraft {
    val batting = battingFirstId(this) ?: return this
    val battingSquad = if (batting == teamBId) squadB else squadA
    val bowlingSquad = if (batting == teamBId) squadA else squadB
    val striker = strikerId?.takeIf { it in battingSquad } ?: battingSquad.firstOrNull()
    val nonStriker = if (!rules.squad.twoBatters) {
        null
    } else {
        nonStrikerId?.takeIf { it in battingSquad && it != striker }
            ?: battingSquad.firstOrNull { it != striker }
    }
    val bowler = bowlerId?.takeIf { it in bowlingSquad } ?: bowlingSquad.firstOrNull()
    return copy(strikerId = striker, nonStrikerId = nonStriker, bowlerId = bowler)
}

@Composable
private fun TeamsStep(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    onCreate: () -> Unit,
    onChanged: () -> Unit,
) {
    val teams = store.data.teams.filterNot { it.archived }
    PageHeading(
        "Choose the two sides",
        "Only this step is required before format. Create a team if one is missing."
    )
    if (teams.isEmpty()) {
        EmptyHint(
            "No teams yet",
            "Add both sides with one player name on each line.",
            "Create a team",
            onCreate
        )
        return
    }
    FocusedBlock(
        focused = draft.focus == "teamA",
        title = "Team A",
        body = "Home or first side for this match.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            teams.forEach { team ->
                TeamPick(team, draft.teamAId == team.id, store, draft.style.accent()) {
                    onChanged()
                    edit {
                        val sameAsB = it.teamBId == team.id
                        it.copy(
                            teamAId = team.id,
                            squadA = emptyList(),
                            teamBId = if (sameAsB) null else it.teamBId,
                            squadB = if (sameAsB) emptyList() else it.squadB,
                            guidance = null,
                            focus = null,
                        )
                    }
                }
            }
        }
    }
    if (draft.teamAId != null) {
        val opponents = teams.filterNot { it.id == draft.teamAId }
        FocusedBlock(
            focused = draft.focus == "teamB",
            title = "Team B",
            body = "The opposing side.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (opponents.isEmpty()) {
                    Text("Create another team to set the opposition.", color = Muted)
                } else {
                    opponents.forEach { team ->
                        TeamPick(team, draft.teamBId == team.id, store, draft.style.accent()) {
                            onChanged()
                            edit {
                                it.copy(
                                    teamBId = team.id,
                                    squadB = emptyList(),
                                    guidance = null,
                                    focus = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SecondaryButton("Create a team", onCreate, Modifier.fillMaxWidth())
}

@Composable
private fun TeamPick(
    team: Team,
    selected: Boolean,
    store: CricketStore,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    SelectableTile(
        title = team.name,
        subtitle = team.playerIds.joinToString { store.playerName(it) }
            .ifBlank { "No players yet" },
        selected = selected,
        accent = accent,
        onClick = onClick,
        leading = { PlayerAvatar(team.name, accent, size = 40.dp) },
    )
}

@Composable
private fun FormatStep(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    onChanged: () -> Unit,
) {
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    PageHeading(
        "Set the match format",
        "Defaults are ready. Change only what this ground needs, then continue."
    )
    ContentBlock("Fixture details", "Optional title and ground.") {
        NameField(
            "Match title",
            draft.title.ifBlank { "${teamA?.name.orEmpty()} v ${teamB?.name.orEmpty()}" }) { value ->
            onChanged()
            edit { it.copy(title = value) }
        }
        Spacer(Modifier.height(8.dp))
        NameField("Ground", draft.venue) { value ->
            onChanged()
            edit { it.copy(venue = value) }
        }
    }
    FocusedBlock(
        focused = draft.focus in setOf("players", "balls", "overs", "innings"),
        title = "Squad and overs",
        body = "These numbers set lineup size and overs limit.",
    ) {
        if (draft.style == CricketStyle.OVERARM) {
            SectionLabel("Format preset")
            Text(
                "Presets fill a starting point. Change any number after you pick one.",
                color = Muted
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "T10" to overarmLimited(10, "T10"),
                    "T20" to overarmLimited(20, "T20"),
                    "50 overs" to overarmLimited(50, "50 overs"),
                    "Multi-innings" to overarmMultiInnings()
                ).forEach { (label, preset) ->
                    SecondaryButton(label, {
                        onChanged()
                        edit {
                            it.copy(
                                rules = preset.copy(id = it.rules.id),
                                squadA = emptyList(),
                                squadB = emptyList()
                            )
                        }
                    }, Modifier.fillMaxWidth())
                }
            }
        }
        IntField(
            modifier = Modifier.padding(vertical = 8.dp),
            label = "Players per team",
            value = draft.rules.squad.playersPerTeam
        ) { value ->
            value?.let { count ->
                onChanged()
                edit {
                    it.copy(
                        rules = it.rules.copy(squad = it.rules.squad.copy(playersPerTeam = count)),
                        squadA = emptyList(),
                        squadB = emptyList()
                    )
                }
            }
        }
        IntField(
            modifier = Modifier.padding(vertical = 8.dp),
            label = "Balls in an over",
            value = draft.rules.overs.ballsPerOver
        ) { value ->
            value?.let { count ->
                onChanged()
                edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(ballsPerOver = count))) }
            }
        }
        IntField(
            modifier = Modifier.padding(vertical = 8.dp),
            label = "Overs in an innings (empty means no limit)",
            value = draft.rules.overs.oversPerInnings
        ) { value ->
            onChanged()
            edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(oversPerInnings = value))) }
        }
        IntField(
            modifier = Modifier.padding(vertical = 8.dp),
            label = "Innings per team",
            value = draft.rules.overs.inningsPerTeam
        ) { value ->
            value?.let { count ->
                onChanged()
                edit {
                    it.copy(
                        rules = it.rules.copy(
                            overs = it.rules.overs.copy(inningsPerTeam = count),
                            endings = it.rules.endings.copy(
                                declarationsAllowed = count > 1,
                                followOnAllowed = count > 1
                            ),
                        ),
                    )
                }
            }
        }
        IntField(
            modifier = Modifier.padding(vertical = 8.dp),
            label = "Maximum overs per bowler (empty means no cap)",
            value = draft.rules.overs.maxOversPerBowler
        ) { value ->
            onChanged()
            edit { it.copy(rules = it.rules.copy(overs = it.rules.overs.copy(maxOversPerBowler = value))) }
        }
    }
    ContentBlock("Rules", "Recommended rules for ${styleTitle(draft.style)} are already applied.") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            SecondaryButton(text = "Recommended", onClick = {
                onChanged()
                val fresh = when (draft.style) {
                    CricketStyle.UNDERARM_BOX -> boxDefaults()
                    CricketStyle.UNDERARM_OPEN -> openDefaults()
                    CricketStyle.OVERARM -> defaultRules(draft.style)
                }
                edit { it.copy(rules = fresh.copy(id = it.rules.id)) }
            }, modifier = Modifier.weight(1f))

            SecondaryButton(
                text = "Save a copy", onClick = { store.saveProfile(draft.rules) },
                modifier = Modifier.weight(1f)
            )
        }
        Disclosure(
            title = "Advanced rules",
            summary = "Open only if this ground plays differently.",
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            RulesEditor(store, draft, edit)
        }
    }
}

@Composable
private fun RulesEditor(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit
) {
    val rules = draft.rules
    fun rules(block: (RulesProfile) -> RulesProfile) {
        edit { it.copy(rules = block(it.rules).copy(source = RuleSource.CUSTOM)) }
    }

    val saved =
        store.data.ruleProfiles.filter { it.style == draft.style } + store.data.tournaments.filter { it.style == draft.style }
            .map { it.rules.copy(name = it.name) }
    saved.forEach { profile ->
        SecondaryButton(profile.name, {
            edit {
                it.copy(
                    rules = profile.copy(id = it.rules.id),
                    tournamentId = store.data.tournaments.find { tournament -> tournament.rules.id == profile.id }?.id
                )
            }
        }, Modifier.fillMaxWidth())
    }
    SectionLabel("Match format")
    ToggleRow(
        "Two batters at the crease",
        rules.squad.twoBatters
    ) { on -> rules { it.copy(squad = it.squad.copy(twoBatters = on)) } }
    ToggleRow("Running between the wickets", rules.boundaries.runningBetweenWicketsEnabled) { on ->
        rules { it.copy(boundaries = it.boundaries.copy(runningBetweenWicketsEnabled = on)) }
    }
    SectionLabel("Scoring rules")
    ToggleRow("Wides", rules.extras.widesEnabled) { on ->
        rules {
            it.copy(
                extras = it.extras.copy(
                    widesEnabled = on
                )
            )
        }
    }
    ToggleRow(
        "No-balls",
        rules.extras.noBallsEnabled
    ) { on -> rules { it.copy(extras = it.extras.copy(noBallsEnabled = on)) } }
    ToggleRow("Byes", rules.extras.byesEnabled) { on ->
        rules {
            it.copy(
                extras = it.extras.copy(
                    byesEnabled = on
                )
            )
        }
    }
    ToggleRow(
        "Leg-byes",
        rules.extras.legByesEnabled
    ) { on -> rules { it.copy(extras = it.extras.copy(legByesEnabled = on)) } }
    ToggleRow("Free hit after a no-ball", rules.extras.freeHitEnabled) { on ->
        rules {
            it.copy(
                extras = it.extras.copy(freeHitEnabled = on)
            )
        }
    }
    ToggleRow(
        "Fours",
        rules.boundaries.foursEnabled
    ) { on -> rules { it.copy(boundaries = it.boundaries.copy(foursEnabled = on)) } }
    ToggleRow(
        "Sixes",
        rules.boundaries.sixesEnabled
    ) { on -> rules { it.copy(boundaries = it.boundaries.copy(sixesEnabled = on)) } }
    IntField(
        "Runs for a four",
        rules.boundaries.fourRuns
    ) { value -> value?.let { count -> rules { it.copy(boundaries = it.boundaries.copy(fourRuns = count)) } } }
    IntField(
        "Runs for a six",
        rules.boundaries.sixRuns
    ) { value -> value?.let { count -> rules { it.copy(boundaries = it.boundaries.copy(sixRuns = count)) } } }
    ToggleRow("Super Over if the scores are level", rules.tieBreak.superOverEnabled) { on ->
        rules {
            it.copy(
                tieBreak = it.tieBreak.copy(
                    superOverEnabled = on,
                    superOverLegalBalls = it.overs.ballsPerOver
                )
            )
        }
    }
    if (draft.style == CricketStyle.UNDERARM_BOX) {
        SectionLabel("Box surfaces")
        Text(
            "Leave a box blank to hide that button. Confirm the numbers with the ground.",
            color = Muted
        )
        IntField(
            "Side wall",
            rules.boundaries.sideWallRuns
        ) { value -> rules { it.copy(boundaries = it.boundaries.copy(sideWallRuns = value)) } }
        IntField(
            "Back wall",
            rules.boundaries.backWallRuns
        ) { value -> rules { it.copy(boundaries = it.boundaries.copy(backWallRuns = value)) } }
        IntField(
            "Ceiling",
            rules.boundaries.ceilingRuns
        ) { value -> rules { it.copy(boundaries = it.boundaries.copy(ceilingRuns = value)) } }
        IntField(
            "Net",
            rules.boundaries.netRuns
        ) { value -> rules { it.copy(boundaries = it.boundaries.copy(netRuns = value)) } }
        ToggleRow(
            "Roof contact is a dead ball",
            rules.boundaries.roofIsDeadBall
        ) { on -> rules { it.copy(boundaries = it.boundaries.copy(roofIsDeadBall = on)) } }
        ToggleRow(
            "Catch off a wall or net",
            rules.dismissals.catchOffWallAllowed
        ) { on -> rules { it.copy(dismissals = it.dismissals.copy(catchOffWallAllowed = on)) } }
    }
    SectionLabel("Dismissal rules")
    if (draft.style != CricketStyle.OVERARM) {
        Text("These stay off until this match turns them on.", color = Muted)
        ToggleRow("One-tip-one-hand", rules.local.oneTipOneHandEnabled) { on ->
            rules {
                it.copy(
                    local = it.local.copy(oneTipOneHandEnabled = on)
                )
            }
        }
        ToggleRow(
            "Direct hit",
            rules.local.directHitDismissalEnabled
        ) { on -> rules { it.copy(local = it.local.copy(directHitDismissalEnabled = on)) } }
        ToggleRow(
            "Single bounce",
            rules.local.singleBounceDismissalEnabled
        ) { on -> rules { it.copy(local = it.local.copy(singleBounceDismissalEnabled = on)) } }
    }
    if (draft.style == CricketStyle.OVERARM) {
        ToggleRow(
            "LBW",
            DismissalType.LBW in rules.dismissals.enabled
        ) { on -> rules { it.withDismissal(DismissalType.LBW, on) } }
        ToggleRow(
            "Stumped",
            DismissalType.STUMPED in rules.dismissals.enabled
        ) { on -> rules { it.withDismissal(DismissalType.STUMPED, on) } }
        if (rules.overs.inningsPerTeam > 1) {
            ToggleRow("Declarations", rules.endings.declarationsAllowed) { on ->
                rules {
                    it.copy(
                        endings = it.endings.copy(declarationsAllowed = on)
                    )
                }
            }
            ToggleRow(
                "Follow-on",
                rules.endings.followOnAllowed
            ) { on -> rules { it.copy(endings = it.endings.copy(followOnAllowed = on)) } }
            IntField("Follow-on deficit", rules.endings.followOnDeficit) { value ->
                rules {
                    it.copy(
                        endings = it.endings.copy(followOnDeficit = value)
                    )
                }
            }
        }
    }
    IntField(
        "Maximum runs on one ball (empty means none)",
        rules.extras.maxRunsPerDelivery
    ) { value ->
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

@Composable
private fun ProvisionsEditor(draft: MatchDraft, rules: ((RulesProfile) -> RulesProfile) -> Unit) {
    val profile = draft.rules
    SectionLabel("Additional scoring rules")
    Text("These values stay locked after the first ball.", color = Muted)
    profile.additionalScoring.events.forEach { event ->
        ToggleRow("${event.name} · ${event.runs}", event.enabled) { on ->
            rules {
                it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item ->
                    if (item.id == event.id) item.copy(
                        enabled = on
                    ) else item
                }))
            }
        }
        if (event.enabled) {
            NameField(event.name, event.name) { value ->
                rules {
                    it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item ->
                        if (item.id == event.id) item.copy(
                            name = value.ifBlank { item.name }) else item
                    }))
                }
            }
            IntField("Runs for ${event.name}", event.runs) { value ->
                value?.let { runs ->
                    rules {
                        it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item ->
                            if (item.id == event.id) item.copy(
                                runs = runs
                            ) else item
                        }))
                    }
                }
            }
            ChoiceRow(
                listOf(
                    "Fixed total" to (event.awardMode == ScoringAwardMode.FIXED),
                    "Bonus on top" to (event.awardMode == ScoringAwardMode.BONUS)
                )
            ) { index ->
                val mode = if (index == 0) ScoringAwardMode.FIXED else ScoringAwardMode.BONUS
                rules {
                    it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item ->
                        if (item.id == event.id) item.copy(
                            awardMode = mode
                        ) else item
                    }))
                }
            }
            NameField("How it is awarded", event.description) { value ->
                rules {
                    it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.map { item ->
                        if (item.id == event.id) item.copy(
                            description = value
                        ) else item
                    }))
                }
            }
            SecondaryButton("Remove ${event.name}", {
                rules { it.copy(additionalScoring = it.additionalScoring.copy(events = it.additionalScoring.events.filter { item -> item.id != event.id })) }
            })
        }
    }
    SecondaryButton("Add scoring event", {
        rules {
            it.copy(
                additionalScoring = it.additionalScoring.copy(
                    events = it.additionalScoring.events + AdditionalScoringEvent(
                        freshId("score"),
                        "Custom scoring event",
                        10,
                        enabled = true,
                        description = "Local tournament rule"
                    )
                )
            )
        }
    })
    SectionLabel("Box Out")
    ToggleRow("Box Out", profile.boxOut.enabled) { on ->
        rules {
            it.copy(
                boxOut = it.boxOut.copy(
                    enabled = on
                )
            )
        }
    }
    if (profile.boxOut.enabled) {
        NameField("What counts as Box Out", profile.boxOut.description) { value ->
            rules {
                it.copy(
                    boxOut = it.boxOut.copy(description = value)
                )
            }
        }
        ToggleRow(
            "Credit the bowler",
            profile.boxOut.bowlerCredited
        ) { on -> rules { it.copy(boxOut = it.boxOut.copy(bowlerCredited = on)) } }
    }
    SectionLabel("Last-man standing")
    ToggleRow(
        "Allow last-man standing",
        profile.lastMan.enabled
    ) { on -> rules { it.copy(lastMan = it.lastMan.copy(enabled = on)) } }
    Text(
        "The team-size minimum and the number of batters left are separate. A team of four does not start the rule after the first wicket.",
        color = Muted
    )
    if (profile.lastMan.enabled) {
        IntField(
            "Minimum team size",
            profile.lastMan.minTeamSize
        ) { value -> value?.let { count -> rules { it.copy(lastMan = it.lastMan.copy(minTeamSize = count)) } } }
        IntField(
            "Batters remaining when it starts",
            profile.lastMan.remainingBattersToActivate
        ) { value ->
            value?.let { count ->
                rules {
                    it.copy(
                        lastMan = it.lastMan.copy(
                            remainingBattersToActivate = count
                        )
                    )
                }
            }
        }
        ToggleRow("Last batter can score", profile.lastMan.allowScoring) { on ->
            rules {
                it.copy(
                    lastMan = it.lastMan.copy(allowScoring = on)
                )
            }
        }
        ToggleRow("Last batter can run", profile.lastMan.allowRunning) { on ->
            rules {
                it.copy(
                    lastMan = it.lastMan.copy(allowRunning = on)
                )
            }
        }
        ToggleRow(
            "Continue after another dismissal",
            profile.lastMan.continueAfterAnotherDismissal
        ) { on -> rules { it.copy(lastMan = it.lastMan.copy(continueAfterAnotherDismissal = on)) } }
        ToggleRow(
            "A dismissal can end the innings",
            profile.lastMan.endOnDismissal
        ) { on -> rules { it.copy(lastMan = it.lastMan.copy(endOnDismissal = on)) } }
    }
    SectionLabel("Player management")
    val roster = profile.roster
    ToggleRow(
        "Allow player changes before the match",
        roster.preMatchTransfersAllowed
    ) { on -> rules { it.copy(roster = it.roster.copy(preMatchTransfersAllowed = on)) } }
    ToggleRow(
        "Allow player changes during the match",
        roster.inMatchSwitchingAllowed
    ) { on -> rules { it.copy(roster = it.roster.copy(inMatchSwitchingAllowed = on)) } }
    ToggleRow(
        "Both teams must approve changes",
        roster.requireBothTeamApproval
    ) { on -> rules { it.copy(roster = it.roster.copy(requireBothTeamApproval = on)) } }
    ToggleRow("Replace an injured player", roster.allowInjuredReplacement) { on ->
        rules {
            it.copy(
                roster = it.roster.copy(allowInjuredReplacement = on)
            )
        }
    }
    ToggleRow(
        "Transferred player can represent the new team",
        roster.transferredPlayerMayRepresentReceiver
    ) { on -> rules { it.copy(roster = it.roster.copy(transferredPlayerMayRepresentReceiver = on)) } }
    ToggleRow("Add players during the match", roster.allowAddDuringMatch) { on ->
        rules {
            it.copy(
                roster = it.roster.copy(allowAddDuringMatch = on)
            )
        }
    }
    SectionLabel("Team balancing")
    ToggleRow(
        "Allow uneven teams",
        roster.allowUnevenTeamSizes
    ) { on -> rules { it.copy(roster = it.roster.copy(allowUnevenTeamSizes = on)) } }
    ToggleRow(
        "Allow player loans",
        roster.allowLoanToOpponent
    ) { on -> rules { it.copy(roster = it.roster.copy(allowLoanToOpponent = on)) } }
    ToggleRow(
        "Receiving team chooses the loaned player",
        roster.receiverChoosesLoanPlayer
    ) { on -> rules { it.copy(roster = it.roster.copy(receiverChoosesLoanPlayer = on)) } }
    ToggleRow(
        "A player may change teams more than once",
        roster.allowMultipleSwitches
    ) { on -> rules { it.copy(roster = it.roster.copy(allowMultipleSwitches = on)) } }
    ToggleRow(
        "New player can bat immediately",
        roster.newPlayerMayBatImmediately
    ) { on -> rules { it.copy(roster = it.roster.copy(newPlayerMayBatImmediately = on)) } }
    ToggleRow(
        "New player can bowl immediately",
        roster.newPlayerMayBowlImmediately
    ) { on -> rules { it.copy(roster = it.roster.copy(newPlayerMayBowlImmediately = on)) } }
    ToggleRow(
        "Add players only at the end of an over",
        roster.additionsOnlyEndOfOver
    ) { on -> rules { it.copy(roster = it.roster.copy(additionsOnlyEndOfOver = on)) } }
    ToggleRow(
        "No-ball runs off the bat count for the batter",
        profile.extras.noBallCreditsBatter
    ) { on ->
        rules { it.copy(extras = it.extras.copy(noBallCreditsBatter = on)) }
    }
}

@Composable
private fun ConfirmStep(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    onChanged: () -> Unit,
) {
    val rules = draft.rules
    var approvalA by remember { mutableStateOf(false) }
    var approvalB by remember { mutableStateOf(false) }
    val teamAName = store.data.teams.find { it.id == draft.teamAId }?.name ?: "Team A"
    val teamBName = store.data.teams.find { it.id == draft.teamBId }?.name ?: "Team B"
    PageHeading(
        "Ready to start",
        "Toss, lineup, and organizer check. Missing items jump you back here with a tip."
    )
    MatchupBanner(
        teamA = teamAName,
        teamB = teamBName,
        detail = "${draft.rules.overs.oversPerInnings ?: "No"} overs · ${draft.rules.squad.playersPerTeam} players · ${draft.rules.overs.inningsPerTeam} innings a side",
        accent = draft.style.accent(),
    )
    FocusedBlock(
        focused = draft.focus == "toss",
        title = "Who bats first",
        body = "Record the toss, or pick the batting side directly.",
    ) {
        TossStep(store, draft, edit, onChanged)
    }
    FocusedBlock(
        focused = draft.focus in setOf("squadA", "squadB", "striker", "nonStriker", "bowler"),
        title = "Lineup and openers",
        body = "Squads are pre-filled. Confirm or change the openers.",
    ) {
        OpenersStep(store, draft, edit, onChanged)
    }
    FocusedBlock(
        focused = draft.focus == "confirm",
        title = "Organizer check",
        body = "Required before the first ball.",
    ) {
        ToggleRow("These rules were checked with the organizer", rules.confirmedByOrganizer) { on ->
            onChanged()
            edit {
                it.copy(
                    rules = it.rules.copy(confirmedByOrganizer = on),
                    guidance = null,
                    focus = null
                )
            }
        }
    }
    Disclosure("Rules locked after start", "Review extras, transfers, and local provisions.") {
        LockedRulesReview(
            store = store,
            draft = draft,
            edit = edit,
            approvalA = approvalA,
            approvalB = approvalB,
            onApprovalA = { approvalA = it },
            onApprovalB = { approvalB = it },
        )
    }
}

@Composable
private fun LockedRulesReview(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    approvalA: Boolean,
    approvalB: Boolean,
    onApprovalA: (Boolean) -> Unit,
    onApprovalB: (Boolean) -> Unit,
) {
    val rules = draft.rules
    val enabledEvents = rules.additionalScoring.events.filter { it.enabled }
    val teamAName = store.data.teams.find { it.id == draft.teamAId }?.name ?: "Team A"
    val teamBName = store.data.teams.find { it.id == draft.teamBId }?.name ?: "Team B"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewSection(
            title = "Additional scoring",
            subtitle = "Extra values awarded beyond normal batting runs.",
        ) {
            if (enabledEvents.isEmpty()) {
                RuleFactRow(label = "Extra scoring values", value = "None", positive = false)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    enabledEvents.forEach { event ->
                        RuleFactRow(
                            label = event.name,
                            value = "${event.runs} ${if (event.awardMode == ScoringAwardMode.BONUS) "bonus" else "fixed"}",
                            detail = event.description.ifBlank { null },
                        )
                    }
                }
            }
        }

        ReviewSection(title = "Box Out") {
            RuleFactRow(
                label = "Status",
                value = if (rules.boxOut.enabled) "On" else "Off",
                positive = rules.boxOut.enabled,
                detail = when {
                    !rules.boxOut.enabled -> "Not used for this match."
                    rules.boxOut.description.isNotBlank() -> rules.boxOut.description
                    else -> "On. Describe the local rule before you start."
                },
            )
        }

        ReviewSection(
            title = "Players",
            subtitle = "How squads can change for this fixture.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleFactRow("Pre-match transfers", yesNo(rules.roster.preMatchTransfersAllowed), positive = rules.roster.preMatchTransfersAllowed)
                RuleFactRow("In-match switching", yesNo(rules.roster.inMatchSwitchingAllowed), positive = rules.roster.inMatchSwitchingAllowed)
                RuleFactRow("Add players during the match", yesNo(rules.roster.allowAddDuringMatch), positive = rules.roster.allowAddDuringMatch)
                RuleFactRow("Uneven team sizes", yesNo(rules.roster.allowUnevenTeamSizes), positive = rules.roster.allowUnevenTeamSizes)
                RuleFactRow("Player loan", yesNo(rules.roster.allowLoanToOpponent), positive = rules.roster.allowLoanToOpponent)
                RuleFactRow("Both teams approve changes", yesNo(rules.roster.requireBothTeamApproval), positive = rules.roster.requireBothTeamApproval)
            }
        }

        ReviewSection(title = "Last-man standing") {
            RuleFactRow(
                label = "Status",
                value = if (rules.lastMan.enabled) "On" else "Off",
                positive = rules.lastMan.enabled,
                detail = if (!rules.lastMan.enabled) {
                    "Not used for this match."
                } else {
                    "Activates when a team has at least ${rules.lastMan.minTeamSize} players and ${rules.lastMan.remainingBattersToActivate} batter${if (rules.lastMan.remainingBattersToActivate == 1) "" else "s"} remain."
                },
            )
        }

        ReviewSection(title = "No-ball") {
            Text(
                if (rules.extras.noBallCreditsBatter) {
                    "Runs off the bat count for the batter. The no-ball penalty is an extra."
                } else {
                    "Runs off the bat on a no-ball are extras, not batter runs."
                },
                color = Ink,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
            )
        }

        if (rules.roster.preMatchTransfersAllowed) {
            ReviewSection(
                title = "Move a player before the start",
                subtitle = "A transfer changes who plays for which side in this match.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rules.roster.requireBothTeamApproval) {
                        ToggleRow("$teamAName approves", approvalA, onApprovalA)
                        ToggleRow("$teamBName approves", approvalB, onApprovalB)
                    }
                    draft.squadA.forEach { id ->
                        SecondaryButton(
                            "Move ${store.playerName(id)} to $teamBName",
                            { moveSquadPlayer(store, draft, edit, id, toSquadB = true, approvalA, approvalB) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    draft.squadB.forEach { id ->
                        SecondaryButton(
                            "Move ${store.playerName(id)} to $teamAName",
                            { moveSquadPlayer(store, draft, edit, id, toSquadB = false, approvalA, approvalB) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
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
        store.showToast("Both teams have to approve this transfer.", AppToastType.Warning)
        return
    }
    edit { current ->
        val next = if (toSquadB) {
            current.copy(
                squadA = current.squadA - playerId,
                squadB = (current.squadB + playerId).distinct()
            )
        } else {
            current.copy(
                squadB = current.squadB - playerId,
                squadA = (current.squadA + playerId).distinct()
            )
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
private fun TossStep(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    onChanged: () -> Unit,
) {
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    val accent = draft.style.accent()
    ToggleRow("No toss", draft.skipToss) { on ->
        onChanged()
        edit { it.copy(skipToss = on).withDefaultOpeners() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.skipToss) {
            SectionLabel("Who bats first?")
            teamA?.let {
                TeamPick(it, draft.manualBattingId == it.id, store, accent) {
                    onChanged()
                    edit { current -> current.copy(manualBattingId = it.id).withDefaultOpeners() }
                }
            }
            teamB?.let {
                TeamPick(it, draft.manualBattingId == it.id, store, accent) {
                    onChanged()
                    edit { current -> current.copy(manualBattingId = it.id).withDefaultOpeners() }
                }
            }
        } else {
            SectionLabel("Toss won by")
            teamA?.let {
                TeamPick(it, draft.tossWinnerId == it.id, store, accent) {
                    onChanged()
                    edit { current -> current.copy(tossWinnerId = it.id).withDefaultOpeners() }
                }
            }
            teamB?.let {
                TeamPick(it, draft.tossWinnerId == it.id, store, accent) {
                    onChanged()
                    edit { current -> current.copy(tossWinnerId = it.id).withDefaultOpeners() }
                }
            }
            SectionLabel("Decision")
            ChoiceRow(
                listOf(
                    "Bat" to (draft.tossChoice == TossChoice.BAT),
                    "Bowl" to (draft.tossChoice == TossChoice.BOWL)
                )
            ) { index ->
                onChanged()
                edit {
                    it.copy(tossChoice = if (index == 0) TossChoice.BAT else TossChoice.BOWL)
                        .withDefaultOpeners()
                }
            }
        }
    }
    battingFirstId(draft)?.let { batting ->
        Text(
            "${store.data.teams.find { it.id == batting }?.name ?: "The batting side"} will bat first.",
            color = Lawn,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OpenersStep(
    store: CricketStore,
    draft: MatchDraft,
    edit: ((MatchDraft) -> MatchDraft) -> Unit,
    onChanged: () -> Unit,
) {
    val battingId = battingFirstId(draft)
    val teamA = store.data.teams.find { it.id == draft.teamAId }
    val teamB = store.data.teams.find { it.id == draft.teamBId }
    if (teamA == null || teamB == null || battingId == null) {
        Text("Choose who bats first above, then the openers appear here.", color = Muted)
        return
    }
    val batting = if (battingId == teamB.id) teamB else teamA
    val bowling = if (battingId == teamB.id) teamA else teamB
    val squadASelected = if (batting.id == teamA.id) draft.squadA else draft.squadB
    val squadBSelected = if (bowling.id == teamA.id) draft.squadA else draft.squadB
    val limit = draft.rules.squad.playersPerTeam
    val accent = draft.style.accent()
    SquadPicker(
        "${batting.name} batting · $limit",
        batting,
        squadASelected,
        limit,
        store,
        accent,
        focused = draft.focus == "squadA"
    ) { selected ->
        onChanged()
        edit { current ->
            val next =
                if (batting.id == current.teamAId) current.copy(squadA = selected) else current.copy(
                    squadB = selected
                )
            next.withDefaultOpeners()
        }
    }
    Spacer(Modifier.height(8.dp))
    SquadPicker(
        "${bowling.name} bowling · $limit",
        bowling,
        squadBSelected,
        limit,
        store,
        accent,
        focused = draft.focus == "squadB"
    ) { selected ->
        onChanged()
        edit { current ->
            val next =
                if (bowling.id == current.teamAId) current.copy(squadA = selected) else current.copy(
                    squadB = selected
                )
            next.withDefaultOpeners()
        }
    }
    if (squadASelected.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(if (draft.focus == "striker") "Striker · needed next" else "Striker")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            squadASelected.forEach { id ->
                LineupPick(
                    store.playerName(id),
                    "Faces the first ball",
                    draft.strikerId == id,
                    accent
                ) {
                    onChanged()
                    edit { it.copy(strikerId = id, guidance = null, focus = null) }
                }
            }
        }
        if (draft.rules.squad.twoBatters) {
            SectionLabel(if (draft.focus == "nonStriker") "Non-striker · needed next" else "Non-striker")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                squadASelected.filter { it != draft.strikerId }.forEach { id ->
                    LineupPick(
                        store.playerName(id),
                        "Other end",
                        draft.nonStrikerId == id,
                        accent
                    ) {
                        onChanged()
                        edit { it.copy(nonStrikerId = id, guidance = null, focus = null) }
                    }
                }
            }
        }
    }
    if (squadBSelected.isNotEmpty()) {
        SectionLabel(if (draft.focus == "bowler") "${draft.rules.labels.bowlingRole} · needed next" else draft.rules.labels.bowlingRole)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            squadBSelected.forEach { id ->
                LineupPick(
                    store.playerName(id),
                    "Opens the bowling",
                    draft.bowlerId == id,
                    accent
                ) {
                    onChanged()
                    edit { it.copy(bowlerId = id, guidance = null, focus = null) }
                }
            }
        }
    }
}

@Composable
private fun SquadPicker(
    title: String,
    team: Team,
    selected: List<String>,
    limit: Int,
    store: CricketStore,
    accent: androidx.compose.ui.graphics.Color,
    focused: Boolean = false,
    onChange: (List<String>) -> Unit,
) {
    SectionLabel(if (focused) "$title · ${selected.size} selected · needed next" else "$title · ${selected.size} selected")
    if (team.playerIds.size < limit) {
        Notice("${team.name} only has ${team.playerIds.size} players. Add more, or lower the squad size.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        team.playerIds.forEach { id ->
            val on = id in selected
            LineupPick(
                store.playerName(id),
                if (on) "In the playing XI" else "Tap to include",
                on,
                accent
            ) {
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
}

@Composable
private fun LineupPick(
    label: String,
    subtitle: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    SelectableTile(
        title = label,
        subtitle = subtitle,
        selected = selected,
        accent = accent,
        onClick = onClick,
        leading = { PlayerAvatar(label, accent, size = 36.dp) },
    )
}


private fun com.example.cricketscoring.model.RulesProfile.withDismissal(
    type: DismissalType,
    enabled: Boolean
) = copy(
    dismissals = dismissals.copy(
        enabled = if (enabled) (dismissals.enabled + type).distinct() else dismissals.enabled.filterNot { it == type },
    ),
)

@Preview
@Composable
private fun WizardScreenPreview() {
    PreviewShell(
        configure = { store ->
            store.openWizard(CricketStyle.UNDERARM_BOX)
            store.draft = store.draft?.copy(
                teamAId = PreviewIds.TeamA,
                teamBId = PreviewIds.TeamB,
                squadA = listOf(PreviewIds.PlayerA1, PreviewIds.PlayerA2, PreviewIds.PlayerA3),
                squadB = listOf(PreviewIds.PlayerB1, PreviewIds.PlayerB2, PreviewIds.PlayerB3),
            )
        },
    ) { WizardScreen(it) }
}
