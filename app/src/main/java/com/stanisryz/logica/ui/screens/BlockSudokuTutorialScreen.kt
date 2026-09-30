package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.BlockSudokuTutorial
import kotlinx.coroutines.launch

/** Android host for the shared Block Sudoku onboarding: it only records that the tutorial was completed. */
@Composable
internal fun BlockSudokuTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    BlockSudokuTutorial(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setBlockSudokuTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}
