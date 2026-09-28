package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.NonogramTutorial
import kotlinx.coroutines.launch

/** Android host for the shared Nonogram onboarding: it only records that the tutorial was completed. */
@Composable
internal fun NonogramTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    NonogramTutorial(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setNonogramTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}
