package com.example.cricketscoring.presentation.home

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle

object HomeSampleData {
    val uiState = HomeUiState(
        styles = CricketStyle.entries.map { style ->
            HomeStyleCard(
                style = style,
                title = styleTitle(style),
                summary = styleSummary(style),
                fixtureLabel = when (style) {
                    CricketStyle.UNDERARM_BOX -> "1 fixtures"
                    else -> "No fixtures yet"
                },
            )
        },
    )
}
