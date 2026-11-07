package com.example.cricketscoring.presentation.fixtures

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.rules.styleTitle

object FixturesSampleData {
    val uiState = FixturesUiState(
        styles = CricketStyle.entries.map { style ->
            FixturesStyleCard(
                style = style,
                title = styleTitle(style),
                fixtureLabel = when (style) {
                    CricketStyle.UNDERARM_BOX -> "2 fixtures"
                    else -> "No fixtures yet"
                },
            )
        },
    )
}
