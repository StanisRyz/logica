package com.stanisryz.logica.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class BalanceTutorialViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val controller = BalanceTutorialController()
    private val mutableUiState = MutableStateFlow(controller.state)
    val uiState: StateFlow<BalanceTutorialUiState> = mutableUiState.asStateFlow()

    fun onCellTapped(position: BalancePosition) {
        controller.onCellTapped(position)
        mutableUiState.value = controller.state
        if (controller.state.completed) {
            viewModelScope.launch { settingsRepository.setBalanceTutorialCompleted(true) }
        }
    }
}

internal class BalanceTutorialViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BalanceTutorialViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return BalanceTutorialViewModel(settingsRepository) as T
    }
}
