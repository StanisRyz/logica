package com.stanisryz.logica.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettings(),
        )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(themeMode)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSoundEnabled(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticsEnabled(enabled)
        }
    }

    fun setBalanceTutorialCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setBalanceTutorialCompleted(completed)
        }
    }

    fun setCrownsTutorialCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setCrownsTutorialCompleted(completed)
        }
    }

    fun setWordTutorialCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setWordTutorialCompleted(completed)
        }
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository) as T
    }
}
