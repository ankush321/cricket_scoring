package com.example.cricketscoring.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.navigation.AppRoute
import com.example.cricketscoring.navigation.LocalAppNavigator
import com.example.cricketscoring.ui.CommonColumn
import com.example.cricketscoring.ui.ElevationMid
import com.example.cricketscoring.ui.Muted
import com.example.cricketscoring.ui.PitchTheme
import com.example.cricketscoring.ui.SurfaceCard

@Composable
fun MoreRoute() {
    val navigator = LocalAppNavigator.current
    val viewModel = remember { MoreViewModel() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MoreEffect.OpenTournaments -> navigator.navigate(AppRoute.Tournaments)
                MoreEffect.OpenStats -> navigator.navigate(AppRoute.Stats)
                MoreEffect.OpenRulesGuide -> navigator.navigate(AppRoute.RulesGuide)
                MoreEffect.OpenSettings -> navigator.navigate(AppRoute.Settings)
            }
        }
    }

    MoreScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
fun MoreScreen(
    uiState: MoreUiState,
    onAction: (MoreAction) -> Unit,
) {
    CommonColumn(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.items.forEach { item ->
            SurfaceCard(elevation = ElevationMid, onClick = { onAction(MoreAction.Open(item.id)) }) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(item.subtitle, color = Muted)
            }
        }
    }
}

@Preview
@Composable
private fun MoreScreenPreview() {
    PitchTheme {
        MoreScreen(
            uiState = MoreUiState(
                items = listOf(
                    MoreItem("tournaments", "Tournaments", "Group matches and share a rules copy."),
                    MoreItem("stats", "Statistics", "Runs, wickets, and results already scored."),
                ),
            ),
            onAction = {},
        )
    }
}
