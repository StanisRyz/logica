package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.crowns.CrownsTutorialViewModel
import com.stanisryz.logica.crowns.CrownsTutorialViewModelFactory
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.CrownsTutorialContent

/** Android host for the shared Crowns onboarding; haptics stay a platform concern. */
@Composable
internal fun CrownsTutorialRoute(
    settingsRepository: SettingsRepository,
    hapticsEnabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(settingsRepository) { CrownsTutorialViewModelFactory(settingsRepository) }
    val viewModel: CrownsTutorialViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(state.feedback) {
        if (hapticsEnabled && state.feedback != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    LaunchedEffect(state.completed) {
        if (hapticsEnabled && state.completed) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    CrownsTutorialContent(
        state = state,
        onCellTapped = { position ->
            if (hapticsEnabled) {
                view.performHapticFeedback(
                    if (state.isPencilMode) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP,
                )
            }
            viewModel.onCellTapped(position)
        },
        onSelectValue = viewModel::selectValue,
        onTogglePencil = viewModel::togglePencilMode,
        onDone = onDone,
        modifier = modifier,
    )
}
