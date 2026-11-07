package com.example.cricketscoring.presentation.fixtures

import com.example.cricketscoring.model.CricketStyle
import com.example.cricketscoring.presentation.mvi.MviViewModel
import com.example.cricketscoring.rules.styleTitle
import com.example.cricketscoring.ui.CricketStore

class FixturesViewModel(
    private val store: CricketStore,
) : MviViewModel<FixturesUiState, FixturesAction, FixturesEffect>(FixturesUiState()) {

    init {
        refresh()
    }

    fun refresh() {
        val matches = store.data.matches
        setState {
            copy(
                styles = CricketStyle.entries.map { style ->
                    val count = matches.count { it.style == style }
                    FixturesStyleCard(
                        style = style,
                        title = styleTitle(style),
                        fixtureLabel = if (count == 0) "No fixtures yet" else "$count fixtures",
                    )
                },
            )
        }
    }

    override fun reduce(action: FixturesAction) {
        when (action) {
            is FixturesAction.OpenStyle -> emitEffect(FixturesEffect.NavigateToStyleHub(action.style))
        }
    }
}
