package com.example.cricketscoring.presentation.fixtures

import com.example.cricketscoring.model.CricketStyle

data class FixturesStyleCard(
    val style: CricketStyle,
    val title: String,
    val fixtureLabel: String,
)

data class FixturesUiState(
    val styles: List<FixturesStyleCard> = emptyList(),
)

sealed interface FixturesAction {
    data class OpenStyle(val style: CricketStyle) : FixturesAction
}

sealed interface FixturesEffect {
    data class NavigateToStyleHub(val style: CricketStyle) : FixturesEffect
}
