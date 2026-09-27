package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.Game2048Tutorial
import kotlinx.coroutines.launch

/** Android host for the shared 2048 onboarding: it only records that the tutorial was completed. */
@Composable
internal fun Game2048TutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Game2048Tutorial(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setGame2048TutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}
