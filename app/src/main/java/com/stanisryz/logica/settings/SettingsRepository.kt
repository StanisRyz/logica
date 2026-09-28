package com.stanisryz.logica.settings

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setRegionPatterns(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setBalanceTutorialCompleted(completed: Boolean)

    suspend fun setCrownsTutorialCompleted(completed: Boolean)

    suspend fun setWordTutorialCompleted(completed: Boolean)

    suspend fun setSudokuTutorialCompleted(completed: Boolean)

    suspend fun setGame2048TutorialCompleted(completed: Boolean)

    suspend fun setLastPlayed(
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    )
}
