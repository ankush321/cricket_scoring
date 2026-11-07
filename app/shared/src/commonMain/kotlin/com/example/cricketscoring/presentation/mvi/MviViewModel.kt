package com.example.cricketscoring.presentation.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lightweight MVI base:
 * - [uiState] is the single source of truth for previewable screens
 * - [onAction] reduces user intents
 * - [effects] carries one-shot navigation / toast side effects
 */
abstract class MviViewModel<State, Action, Effect>(
    initialState: State,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    protected val state: State get() = _uiState.value

    fun onAction(action: Action) {
        reduce(action)
    }

    protected abstract fun reduce(action: Action)

    protected fun setState(reducer: State.() -> State) {
        _uiState.update { it.reducer() }
    }

    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
