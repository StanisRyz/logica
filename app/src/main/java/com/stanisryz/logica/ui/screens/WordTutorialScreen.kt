package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.AppLanguage
import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.WordTutorial
import kotlinx.coroutines.launch

/** Android host for the shared Word onboarding: it only records that the tutorial was completed. */
@Composable
internal fun WordTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    WordTutorial(
        language = WordLanguage.forInterfaceTag(AppLanguage.tag),
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setWordTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}
