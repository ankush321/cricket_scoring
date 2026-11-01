package com.example.cricketscoring.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.model.DismissalType
import com.example.cricketscoring.model.RuleDisposition
import com.example.cricketscoring.rules.styleTitle

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StepProgress(step: Int, total: Int, labels: List<String>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { index ->
                val active = index + 1 <= step
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) accent else PitchRaised),
                )
            }
        }
        Text(
            "Step $step of $total · ${labels.getOrElse(step - 1) { "" }}",
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ContentBlock(title: String, body: String? = null, content: @Composable () -> Unit) {
    SurfaceCard(elevation = ElevationMid) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = Muted)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    elevation: Dp = ElevationMid,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = if (accent != null) PitchGlass else PitchCard),
        shape = RoundedCornerShape(RadiusLg),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (accent != null) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)) else null,
        content = {
            Column(
                Modifier
                    .then(
                        if (accent != null) {
                            Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)))
                        } else {
                            Modifier
                        },
                    )
                    .padding(18.dp),
                content = { content() },
            )
        },
    )
}

@Composable
fun SelectableTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val background by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else PitchRaised)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(background)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (selected) accent else Ink)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(if (selected) "Selected" else "Tap", color = if (selected) accent else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun MatchupBanner(teamA: String, teamB: String, detail: String, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), ActionCoralDeep.copy(alpha = 0.9f))))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MATCHUP", color = SoftWhite.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(teamA, color = SoftWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SoftWhite.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("VS", color = SoftWhite, fontWeight = FontWeight.Black) }
                Text(teamB, color = SoftWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Text(detail, color = SoftWhite.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun ActionDock(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg))
            .background(PitchCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = RadiusXl, topEnd = RadiusXl))
                .background(PitchCard),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PitchRaised),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PageHeading(title, subtitle ?: "")
                content()
            }
        }
    }
}

@Composable
fun CreateTeamSheet(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var playerDraft by remember { mutableStateOf("") }
    var players by remember { mutableStateOf(listOf<String>()) }
    BottomSheet(
        onDismiss = onDismiss,
        title = "New team",
        subtitle = "Name the side, then add players one by one.",
    ) {
        ContentBlock("Team details", "This name appears on fixtures and scorecards.") {
            NameField("Team name", name) { name = it }
        }
        ContentBlock("Players", "Add each player separately. You need at least one.") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Squad list", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PlayerCountBadge(players.size)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f)) {
                    NameField("Player name", playerDraft) { playerDraft = it }
                }
                SecondaryButton("Add", {
                    val next = playerDraft.trim()
                    if (next.isNotEmpty() && players.none { it.equals(next, ignoreCase = true) }) {
                        players = players + next
                        playerDraft = ""
                    }
                })
            }
            Spacer(Modifier.size(20.dp))
            if (players.isEmpty()) {
                Text("No players yet. Add the first name above.", color = Muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    players.forEachIndexed { index, player ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RadiusMd))
                                .background(PitchRaised)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlayerAvatar(player, ActionCoral, size = 34.dp)
                            Column(Modifier.weight(1f)) {
                                Text(player, fontWeight = FontWeight.SemiBold)
                                Text("Player ${index + 1}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Remove",
                                color = WicketRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { players = players.filterIndexed { i, _ -> i != index } },
                            )
                        }
                    }
                }
            }
        }
        PrimaryButton(
            "Save team",
            { onCreate(name, players.joinToString("\n")) },
            Modifier.fillMaxWidth(),
            enabled = name.trim().isNotEmpty() && players.isNotEmpty(),
        )
        SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
fun PlayerCountBadge(count: Int) {
    val ready = count > 0
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (ready) Lawn.copy(alpha = 0.18f) else PitchRaised)
            .border(1.dp, if (ready) Lawn.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            if (count == 0) "0 players" else if (count == 1) "1 player" else "$count players",
            color = if (ready) Lawn else Muted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun NavGlyph(label: String, selected: Boolean) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Lawn.copy(alpha = 0.2f) else PitchRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Lawn else Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun Disclosure(title: String, summary: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(if (open) "Hide $title" else title, { open = !open }, Modifier.fillMaxWidth())
        if (!open) Text(summary, color = Muted)
        if (open) content()
    }
}

@Composable
fun PitchCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SurfaceCard(modifier = modifier, elevation = ElevationMid, onClick = onClick, content = content)
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(WicketRed.copy(alpha = 0.18f))
            .border(1.dp, WicketRed.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(WicketRed))
        Text("LIVE", color = WicketRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp)
    }
}

@Composable
fun PlayerAvatar(name: String, accent: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f))))
            .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = accent, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
    }
}

@Composable
fun BallChip(
    symbol: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val wicket = symbol.equals("W", ignoreCase = true) || symbol.contains("W", ignoreCase = true) && symbol.length <= 3
    val boundary = symbol == "4" || symbol == "6"
    val background = when {
        wicket -> WicketRed
        boundary -> Gold
        else -> PitchRaised
    }
    val content = when {
        wicket || boundary -> Color(0xFF14110A)
        else -> Ink
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol.take(3), color = if (wicket) Color.White else content, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun PanelTabs(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PitchRaised.copy(alpha = 0.55f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            val background by animateColorAsState(if (selected) Lawn else Color.Transparent)
            val foreground by animateColorAsState(if (selected) Color(0xFF052117) else Ink)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ScorePadKey(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PitchRaised,
    contentColor: Color = Ink,
) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = contentColor, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(subtitle, color = contentColor.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun StyleMark(style: CricketStyle, modifier: Modifier = Modifier) {
    val letter = when (style) {
        CricketStyle.UNDERARM_BOX -> "BX"
        CricketStyle.UNDERARM_OPEN -> "OP"
        CricketStyle.OVERARM -> "OV"
    }
    Column(
        modifier = modifier.size(52.dp).clip(CircleShape).background(style.accent().copy(alpha = 0.18f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(letter, color = style.accent(), fontWeight = FontWeight.Black)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .then(
                if (enabled) Modifier.background(ActionBrush)
                else Modifier.background(PitchRaised),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) SoftWhite else Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(RadiusMd),
        border = androidx.compose.foundation.BorderStroke(1.dp, PitchRaised),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun ChoiceRow(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, selected) ->
            val modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            if (selected) {
                Button(onClick = { onSelect(index) }, modifier = modifier, contentPadding = ButtonDefaults.ContentPadding) {
                    Text(label, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = modifier) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) WicketRed else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Notice(text: String) {
    Text(text, color = Gold, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(bottom = 4.dp, top = 8.dp))
}

@Composable
fun IntField(label: String, value: Int?, onValue: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() }.take(3)
            text = cleaned
            onValue(cleaned.toIntOrNull())
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
fun NameField(label: String, value: String, singleLine: Boolean = true, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
    )
}

fun PlayingRole.displayName(): String = when (this) {
    PlayingRole.BATTER -> "Batter"
    PlayingRole.BOWLER -> "Bowler"
    PlayingRole.ALL_ROUNDER -> "All-rounder"
    PlayingRole.WICKET_KEEPER -> "Wicket-keeper"
}

fun DismissalType.displayName(): String = when (this) {
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

fun RuleDisposition.displayName(): String = when (this) {
    RuleDisposition.RECOMMENDED_DEFAULT -> "Recommended"
    RuleDisposition.CONFIGURABLE -> "Configurable"
    RuleDisposition.CONFIRM_WITH_ORGANIZER -> "Confirm with organizer"
}

fun CricketStyle.screenTitle(): String = styleTitle(this)

@Composable
fun EmptyHint(title: String, body: String, action: String, onAction: () -> Unit) {
    PitchCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(action, onAction)
    }
}

@Composable
fun InlineActions(vararg actions: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        actions.forEach { (label, action) ->
            TextButton(onClick = action) { Text(label) }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun AccentRail(color: Color, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(4.dp).heightIn(min = 72.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}
