package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.SudokuTutorial
import kotlinx.coroutines.launch

/** Android host for the shared Sudoku onboarding: it only records that the tutorial was completed. */
@Composable
internal fun SudokuTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    SudokuTutorial(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setSudokuTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}
