package com.example.cricketscoring.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscoring.navigation.AppTab
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(PitchBlack.copy(alpha = 0.2f), PitchCard),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SoftWhite.copy(alpha = 0.04f),
                            Lawn.copy(alpha = 0.35f),
                            ActionCoral.copy(alpha = 0.4f),
                            SoftWhite.copy(alpha = 0.04f),
                        ),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppScreenDefaults.Horizontal, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                AppBottomBarItem(
                    tab = tab,
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AppBottomBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(if (selected) Lawn else Muted)
    val bubble by animateColorAsState(
        if (selected) Lawn.copy(alpha = 0.16f) else PitchRaised.copy(alpha = 0.55f),
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.96f)
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(RadiusMd))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubble)
                .border(
                    width = 1.dp,
                    color = if (selected) Lawn.copy(alpha = 0.45f) else SoftWhite.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon(),
                contentDescription = tab.label,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = tab.label,
            color = accent,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Box(
            Modifier
                .size(width = if (selected) 14.dp else 0.dp, height = 3.dp)
                .clip(CircleShape)
                .background(if (selected) Lawn else SoftWhite.copy(alpha = 0f)),
        )
    }
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.Home -> Icons.Filled.Home
    AppTab.Fixtures -> Icons.Filled.SportsCricket
    AppTab.Teams -> Icons.Filled.Groups
    AppTab.Players -> Icons.Filled.Person
    AppTab.More -> Icons.Filled.MoreHoriz
}

@Preview
@Composable
private fun AppBottomBarPreview() {
    PitchTheme {
        AppBottomBar(
            selected = AppTab.Fixtures,
            onSelect = {},
        )
    }
}
