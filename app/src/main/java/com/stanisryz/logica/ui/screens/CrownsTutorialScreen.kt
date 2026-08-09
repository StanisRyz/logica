package com.stanisryz.logica.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.crowns.CrownsTutorialFeedback
import com.stanisryz.logica.crowns.CrownsTutorialStage
import com.stanisryz.logica.crowns.CrownsTutorialUiState
import com.stanisryz.logica.crowns.CrownsTutorialViewModel
import com.stanisryz.logica.crowns.CrownsTutorialViewModelFactory
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.crowns.CrownsBoard
import com.stanisryz.logica.ui.theme.LogicaSpacing

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
    CrownsTutorialScreen(state, viewModel::onCellTapped, hapticsEnabled, onDone, modifier)
}

@Composable
private fun CrownsTutorialScreen(
    state: CrownsTutorialUiState,
    onCellTapped: (CrownsPosition) -> Unit,
    hapticsEnabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    LaunchedEffect(state.feedback) {
        if (hapticsEnabled && state.feedback != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    LaunchedEffect(state.completed) {
        if (hapticsEnabled && state.completed) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    ScreenColumn(
        modifier = modifier,
        verticalSpacing = LogicaSpacing.item,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.crowns_tutorial_progress, state.stage.ordinal + 1),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(crownsTutorialStageTitle(state.stage), style = MaterialTheme.typography.headlineSmall)
        Text(crownsTutorialStageInstruction(state), style = MaterialTheme.typography.bodyLarge)
        CrownsBoard(
            puzzle = state.puzzle,
            game = state.game,
            onCellTapped = { position ->
                if (hapticsEnabled) {
                    view.performHapticFeedback(
                        when (state.game.cellAt(position)) {
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.EMPTY -> HapticFeedbackConstants.KEYBOARD_TAP
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.MARKED -> HapticFeedbackConstants.CLOCK_TICK
                            com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell.CROWN -> HapticFeedbackConstants.KEYBOARD_TAP
                        },
                    )
                }
                onCellTapped(position)
            },
            guidedPositions = state.focusedPositions,
            modifier = Modifier.fillMaxWidth(),
        )
        state.feedback?.let { feedback ->
            LogicaCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Text(crownsTutorialFeedbackText(feedback))
            }
        }
    }

    if (state.completed) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.crowns_tutorial_complete_title)) },
            text = { Text(stringResource(R.string.crowns_tutorial_complete_body)) },
            confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun crownsTutorialStageTitle(stage: CrownsTutorialStage): String =
    stringResource(
        when (stage) {
            CrownsTutorialStage.ROW_AND_COLUMN -> R.string.crowns_tutorial_stage_one_title
            CrownsTutorialStage.REGION -> R.string.crowns_tutorial_stage_two_title
            CrownsTutorialStage.DIAGONAL -> R.string.crowns_tutorial_stage_three_title
            CrownsTutorialStage.MARKS_AND_CONTROLS -> R.string.crowns_tutorial_stage_four_title
            CrownsTutorialStage.MINI_PUZZLE -> R.string.crowns_tutorial_stage_five_title
        },
    )

@Composable
private fun crownsTutorialStageInstruction(state: CrownsTutorialUiState): String =
    stringResource(
        when (state.stage) {
            CrownsTutorialStage.ROW_AND_COLUMN -> R.string.crowns_tutorial_stage_one_body
            CrownsTutorialStage.REGION -> R.string.crowns_tutorial_stage_two_body
            CrownsTutorialStage.DIAGONAL -> R.string.crowns_tutorial_stage_three_body
            CrownsTutorialStage.MARKS_AND_CONTROLS ->
                when (state.markCycleStep) {
                    0 -> R.string.crowns_tutorial_stage_four_body_mark
                    1 -> R.string.crowns_tutorial_stage_four_body_crown
                    else -> R.string.crowns_tutorial_stage_four_body_clear
                }
            CrownsTutorialStage.MINI_PUZZLE -> R.string.crowns_tutorial_stage_five_body
        },
    )

@Composable
private fun crownsTutorialFeedbackText(feedback: CrownsTutorialFeedback): String =
    stringResource(
        when (feedback) {
            CrownsTutorialFeedback.ROW_AND_COLUMN -> R.string.crowns_tutorial_feedback_row_column
            CrownsTutorialFeedback.REGION -> R.string.crowns_tutorial_feedback_region
            CrownsTutorialFeedback.DIAGONAL -> R.string.crowns_tutorial_feedback_diagonal
            CrownsTutorialFeedback.MARKS_AND_CONTROLS -> R.string.crowns_tutorial_feedback_marks
            CrownsTutorialFeedback.MINI_PUZZLE -> R.string.crowns_tutorial_feedback_mini_puzzle
        },
    )
