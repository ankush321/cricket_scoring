package com.example.cricketscoring.presentation.home

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.presentation.mvi.MviViewModel
import com.example.cricketscoring.rules.styleSummary
import com.example.cricketscoring.rules.styleTitle
import com.example.cricketscoring.ui.CricketStore

class HomeViewModel(
    private val store: CricketStore,
) : MviViewModel<HomeUiState, HomeAction, HomeEffect>(HomeUiState()) {

    init {
        refresh()
    }

    fun refresh() {
        val matches = store.data.matches
        setState {
            copy(
                styles = CricketStyle.entries.map { style ->
                    val count = matches.count { it.style == style }
                    HomeStyleCard(
                        style = style,
                        title = styleTitle(style),
                        summary = styleSummary(style),
                        fixtureLabel = if (count == 0) "No fixtures yet" else "$count fixtures",
                    )
                },
            )
        }
    }

    override fun reduce(action: HomeAction) {
        when (action) {
            is HomeAction.OpenStyle -> emitEffect(HomeEffect.NavigateToStyleHub(action.style))
        }
    }
}
