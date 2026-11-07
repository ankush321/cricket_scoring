package com.example.cricketscoring.presentation.more

import com.example.cricketscoring.presentation.mvi.MviViewModel

class MoreViewModel : MviViewModel<MoreUiState, MoreAction, MoreEffect>(
    MoreUiState(
        items = listOf(
            MoreItem("tournaments", "Tournaments", "Group matches and share a rules copy."),
            MoreItem("stats", "Statistics", "Runs, wickets, and results already scored."),
            MoreItem("rules", "Rules guide", "Recommended starting points for each style."),
            MoreItem("settings", "Settings", "This device only. Matches save as you score."),
        ),
    ),
) {
    override fun reduce(action: MoreAction) {
        when (action) {
            is MoreAction.Open -> when (action.id) {
                "tournaments" -> emitEffect(MoreEffect.OpenTournaments)
                "stats" -> emitEffect(MoreEffect.OpenStats)
                "rules" -> emitEffect(MoreEffect.OpenRulesGuide)
                "settings" -> emitEffect(MoreEffect.OpenSettings)
            }
        }
    }
}
