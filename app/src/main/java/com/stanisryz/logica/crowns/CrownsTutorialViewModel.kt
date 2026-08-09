package com.stanisryz.logica.crowns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class CrownsTutorialViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val controller = CrownsTutorialController()
    private val mutableUiState = MutableStateFlow(controller.state)
    val uiState: StateFlow<CrownsTutorialUiState> = mutableUiState.asStateFlow()

    fun onCellTapped(position: CrownsPosition) {
        controller.onCellTapped(position)
        mutableUiState.value = controller.state
        if (controller.state.completed) {
            viewModelScope.launch { settingsRepository.setCrownsTutorialCompleted(true) }
        }
    }
}

internal class CrownsTutorialViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CrownsTutorialViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return CrownsTutorialViewModel(settingsRepository) as T
    }
}
