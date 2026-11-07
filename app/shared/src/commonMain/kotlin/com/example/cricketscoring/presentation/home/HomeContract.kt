package com.example.cricketscoring.presentation.home

import com.example.cricketscoring.model.CricketStyle

data class HomeStyleCard(
    val style: CricketStyle,
    val title: String,
    val summary: String,
    val fixtureLabel: String,
)

data class HomeUiState(
    val styles: List<HomeStyleCard> = emptyList(),
)

sealed interface HomeAction {
    data class OpenStyle(val style: CricketStyle) : HomeAction
}

sealed interface HomeEffect {
    data class NavigateToStyleHub(val style: CricketStyle) : HomeEffect
}
