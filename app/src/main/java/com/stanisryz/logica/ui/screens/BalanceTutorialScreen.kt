package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.balance.BalanceTutorialFeedback
import com.stanisryz.logica.balance.BalanceTutorialStage
import com.stanisryz.logica.balance.BalanceTutorialUiState
import com.stanisryz.logica.balance.BalanceTutorialViewModel
import com.stanisryz.logica.balance.BalanceTutorialViewModelFactory
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.balance.BalanceBoard
import com.stanisryz.logica.ui.balance.BalanceToolBar
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.theme.LogicaSpacing

@Composable
internal fun BalanceTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = androidx.compose.runtime.remember(settingsRepository) { BalanceTutorialViewModelFactory(settingsRepository) }
    val viewModel: BalanceTutorialViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BalanceTutorialScreen(
        state,
        viewModel::onCellTapped,
        viewModel::selectValue,
        viewModel::togglePencilMode,
        onDone,
        modifier,
    )
}

@Composable
private fun BalanceTutorialScreen(
    state: BalanceTutorialUiState,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.balance_tutorial_progress, state.stage.ordinal + 1),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(stageTitle(state.stage), style = MaterialTheme.typography.headlineSmall)
        Text(stageInstruction(state.stage), style = MaterialTheme.typography.bodyLarge)
        BalanceBoard(
            puzzle = state.puzzle,
            game = state.game,
            onCellTapped = onCellTapped,
            enabledPositions = state.interactivePositions,
            modifier = Modifier.fillMaxWidth(),
        )
        BalanceToolBar(state.selectedValue, state.isPencilMode, onSelectValue, onTogglePencil)
        if (state.feedback == BalanceTutorialFeedback.TRY_AGAIN) {
            LogicaCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Text(stringResource(R.string.balance_tutorial_try_again))
            }
        }
    }

    if (state.completed) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.balance_tutorial_complete_title)) },
            text = { Text(stringResource(R.string.balance_tutorial_complete_body)) },
            confirmButton = {
                Button(onClick = onDone) { Text(stringResource(R.string.done)) }
            },
        )
    }
}

@Composable
private fun stageTitle(stage: BalanceTutorialStage): String =
    stringResource(
        when (stage) {
            BalanceTutorialStage.PREVENT_THREE -> R.string.balance_tutorial_stage_one_title
            BalanceTutorialStage.COMPLETE_QUOTA -> R.string.balance_tutorial_stage_two_title
            BalanceTutorialStage.PRESERVE_UNIQUENESS -> R.string.balance_tutorial_stage_three_title
            BalanceTutorialStage.INDEPENDENT_PUZZLE -> R.string.balance_tutorial_stage_four_title
        },
    )

@Composable
private fun stageInstruction(stage: BalanceTutorialStage): String =
    stringResource(
        when (stage) {
            BalanceTutorialStage.PREVENT_THREE -> R.string.balance_tutorial_stage_one_body
            BalanceTutorialStage.COMPLETE_QUOTA -> R.string.balance_tutorial_stage_two_body
            BalanceTutorialStage.PRESERVE_UNIQUENESS -> R.string.balance_tutorial_stage_three_body
            BalanceTutorialStage.INDEPENDENT_PUZZLE -> R.string.balance_tutorial_stage_four_body
        },
    )
