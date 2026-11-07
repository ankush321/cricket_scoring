package com.example.cricketscoring.presentation.more

data class MoreItem(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class MoreUiState(
    val items: List<MoreItem> = emptyList(),
)

sealed interface MoreAction {
    data class Open(val id: String) : MoreAction
}

sealed interface MoreEffect {
    data object OpenTournaments : MoreEffect
    data object OpenStats : MoreEffect
    data object OpenRulesGuide : MoreEffect
    data object OpenSettings : MoreEffect
}
