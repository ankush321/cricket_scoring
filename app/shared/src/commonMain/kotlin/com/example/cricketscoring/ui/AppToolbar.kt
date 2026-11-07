package com.example.cricketscoring.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

data class AppToolbarUiState(
    val title: String,
    val subtitle: String? = null,
    val showBack: Boolean = false,
    val actionLabel: String? = null,
)

sealed interface AppToolbarAction {
    data object Back : AppToolbarAction
    data object PrimaryAction : AppToolbarAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolbar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PitchBlack,
            scrolledContainerColor = PitchCard,
            titleContentColor = Ink,
            navigationIconContentColor = Ink,
            actionIconContentColor = Lawn,
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = actions,
    )
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
        subtitle = uiState.subtitle,
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
            uiState = AppToolbarUiState(
                title = "Fixtures",
                subtitle = "Live, upcoming, and completed",
            ),
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
