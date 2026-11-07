package com.example.cricketscoring.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.ui.CommonColumn
import com.example.cricketscoring.ui.CricketStore
import com.example.cricketscoring.ui.ElevationHigh
import com.example.cricketscoring.ui.Muted
import com.example.cricketscoring.ui.PitchTheme
import com.example.cricketscoring.ui.PrimaryButton
import com.example.cricketscoring.ui.StyleMark
import com.example.cricketscoring.ui.SurfaceCard
import com.example.cricketscoring.ui.accent

@Composable
fun HomeRoute(
    store: CricketStore,
    onOpenStyle: (CricketStyle) -> Unit,
) {
    val viewModel = remember(store) { HomeViewModel(store) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(store.data.matches) {
        viewModel.refresh()
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToStyleHub -> onOpenStyle(effect.style)
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    CommonColumn(
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.styles.forEach { card ->
            SurfaceCard(
                elevation = ElevationHigh,
                accent = card.style.accent(),
                onClick = { onAction(HomeAction.OpenStyle(card.style)) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StyleMark(card.style)
                    Column(Modifier.weight(1f)) {
                        Text(card.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(card.summary, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(card.style.accent().copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                card.fixtureLabel,
                                color = card.style.accent(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    "View fixtures",
                    { onAction(HomeAction.OpenStyle(card.style)) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    PitchTheme {
        HomeScreen(
            uiState = HomeSampleData.uiState,
            onAction = {},
        )
    }
}
