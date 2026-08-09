package com.stanisryz.logica.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

internal class CatalogViewModel(
    sessionRepository: GameSessionRepository,
) : ViewModel() {
    val hasActiveBalanceSession: StateFlow<Boolean> =
        sessionRepository.observeHasActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val hasActiveCrownsSession: StateFlow<Boolean> =
        sessionRepository.observeHasActiveSession(PuzzleType.CROWNS, GameSessionScope.CATALOG).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val hasActiveWordSession: StateFlow<Boolean> =
        sessionRepository.observeHasActiveSession(PuzzleType.WORD, GameSessionScope.CATALOG).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}

internal class CatalogViewModelFactory(
    private val sessionRepository: GameSessionRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CatalogViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return CatalogViewModel(sessionRepository) as T
    }
}
