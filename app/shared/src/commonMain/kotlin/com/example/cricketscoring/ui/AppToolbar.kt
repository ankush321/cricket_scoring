package com.example.cricketscoring.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class AppToolbarUiState(
    val title: String,
    val showBack: Boolean = false,
    val actionLabel: String? = null,
)

sealed interface AppToolbarAction {
    data object Back : AppToolbarAction
    data object PrimaryAction : AppToolbarAction
}

@Composable
fun AppToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(PitchCard.copy(alpha = 0.92f), PitchBlack),
                ),
            )
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppScreenDefaults.Horizontal, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PitchRaised)
                        .border(1.dp, SoftWhite.copy(alpha = 0.08f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Ink,
                    )
                }
                Spacer(Modifier.width(10.dp))
            } else {
                Box(
                    Modifier
                        .padding(start = 4.dp, end = 12.dp)
                        .width(4.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ActionBrush),
                )
            }

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            ActionCoral.copy(alpha = 0.55f),
                            Lawn.copy(alpha = 0.35f),
                            SoftWhite.copy(alpha = 0.05f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
fun AppToolbar(
    uiState: AppToolbarUiState,
    onAction: (AppToolbarAction) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {
        if (uiState.actionLabel != null) {
            TextButton(onClick = { onAction(AppToolbarAction.PrimaryAction) }) {
                Text(uiState.actionLabel, color = Lawn, fontWeight = FontWeight.SemiBold)
            }
        }
    },
) {
    AppToolbar(
        title = uiState.title,
        modifier = modifier,
        onBack = if (uiState.showBack) {
            { onAction(AppToolbarAction.Back) }
        } else {
            null
        },
        actions = actions,
    )
}

@Preview
@Composable
private fun AppToolbarRootPreview() {
    PitchTheme {
        AppToolbar(
            uiState = AppToolbarUiState(title = "Fixtures"),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun AppToolbarDetailPreview() {
    PitchTheme {
        AppToolbar(
            uiState = AppToolbarUiState(
                title = "Box cricket",
                showBack = true,
                actionLabel = "Share",
            ),
            onAction = {},
        )
    }
}
