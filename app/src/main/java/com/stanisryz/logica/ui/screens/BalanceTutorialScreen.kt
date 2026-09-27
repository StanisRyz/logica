package com.stanisryz.logica.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.balance.BalanceTutorialViewModel
import com.stanisryz.logica.balance.BalanceTutorialViewModelFactory
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.tutorial.BalanceTutorialContent

/** Android host for the shared Balance onboarding; the ViewModel keeps it across configuration changes. */
@Composable
internal fun BalanceTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(settingsRepository) { BalanceTutorialViewModelFactory(settingsRepository) }
    val viewModel: BalanceTutorialViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BalanceTutorialContent(
        state = state,
        onCellTapped = viewModel::onCellTapped,
        onSelectValue = viewModel::selectValue,
        onTogglePencil = viewModel::togglePencilMode,
        onDone = onDone,
        modifier = modifier,
    )
}
