package com.stanisryz.logica.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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

    fun setLastPlayed(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ) {
        viewModelScope.launch { repository.setLastPlayed(puzzleType, difficulty) }
    }

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

    /** Remembers that a game's tutorial was offered or opened, so it is never offered again. */
    fun markTutorialSeen(puzzleType: PuzzleType) {
        if (settings.value.tutorialCompleted(puzzleType)) return
        viewModelScope.launch {
            when (puzzleType) {
                PuzzleType.BALANCE -> repository.setBalanceTutorialCompleted(true)
                PuzzleType.CROWNS -> repository.setCrownsTutorialCompleted(true)
                PuzzleType.WORD -> repository.setWordTutorialCompleted(true)
                PuzzleType.SUDOKU -> repository.setSudokuTutorialCompleted(true)
                PuzzleType.GAME_2048 -> repository.setGame2048TutorialCompleted(true)
                PuzzleType.NONOGRAM -> repository.setNonogramTutorialCompleted(true)
                else -> Unit
            }
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
