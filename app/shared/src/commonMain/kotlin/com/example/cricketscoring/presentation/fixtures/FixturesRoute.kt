package com.example.cricketscoring.presentation.fixtures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.ui.CommonColumn
import com.example.cricketscoring.ui.CricketStore
import com.example.cricketscoring.ui.ElevationMid
import com.example.cricketscoring.ui.Muted
import com.example.cricketscoring.ui.PitchTheme
import com.example.cricketscoring.ui.StyleMark
import com.example.cricketscoring.ui.SurfaceCard
import com.example.cricketscoring.ui.accent

@Composable
fun FixturesRoute(
    store: CricketStore,
    onOpenStyle: (CricketStyle) -> Unit,
) {
    val viewModel = remember(store) { FixturesViewModel(store) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(store.data.matches) {
        viewModel.refresh()
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FixturesEffect.NavigateToStyleHub -> onOpenStyle(effect.style)
            }
        }
    }

    FixturesScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun FixturesScreen(
    uiState: FixturesUiState,
    onAction: (FixturesAction) -> Unit,
) {
    CommonColumn(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        uiState.styles.forEach { card ->
            SurfaceCard(
                elevation = ElevationMid,
                accent = card.style.accent(),
                onClick = { onAction(FixturesAction.OpenStyle(card.style)) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyleMark(card.style)
                    Column(Modifier.weight(1f)) {
                        Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(card.fixtureLabel, color = Muted)
                    }
                    Text("Open", color = card.style.accent(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview
@Composable
private fun FixturesScreenPreview() {
    PitchTheme {
        FixturesScreen(
            uiState = FixturesSampleData.uiState,
            onAction = {},
        )
    }
}
