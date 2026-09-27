package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_complete_body
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_complete_title
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_four_body
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_four_title
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_one_body
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_one_title
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_three_body
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_three_title
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_two_body
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_stage_two_title
import com.stanisryz.logica.shared.ui.generated.resources.balance_tutorial_try_again
import com.stanisryz.logica.ui.balance.BalanceBoard
import com.stanisryz.logica.ui.balance.BalanceToolBar
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Stateless Balance onboarding shared by Android and Web; the host owns the controller. */
@Composable
fun BalanceTutorialContent(
    state: BalanceTutorialUiState,
    onCellTapped: (BalancePosition) -> Unit,
    onSelectValue: (BalanceCell) -> Unit,
    onTogglePencil: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialLayout(
        step = state.stage.ordinal + 1,
        stepCount = BalanceTutorialStage.entries.size,
        title = stringResource(state.stage.titleResource()),
        body = stringResource(state.stage.bodyResource()),
        feedback = state.feedback?.let { stringResource(Res.string.balance_tutorial_try_again) },
        modifier = modifier,
    ) {
        BalanceBoard(
            puzzle = state.puzzle,
            game = state.game,
            onCellTapped = onCellTapped,
            enabledPositions = state.interactivePositions,
            modifier = Modifier.widthIn(max = TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth(),
            // Guided steps highlight the one cell they talk about; the free puzzle highlights nothing.
            guidedPositions =
                if (BalanceTutorialScenarios.expectedMove(state.stage) != null) state.interactivePositions else emptySet(),
        )
        BalanceToolBar(state.selectedValue, state.isPencilMode, onSelectValue, onTogglePencil)
    }
    if (state.completed) {
        TutorialCompleteDialog(
            title = stringResource(Res.string.balance_tutorial_complete_title),
            body = stringResource(Res.string.balance_tutorial_complete_body),
            onDone = onDone,
        )
    }
}

/** Self-contained Balance onboarding for hosts without a ViewModel layer (Web). */
@Composable
fun BalanceTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = remember { BalanceTutorialController() }
    var state by remember { mutableStateOf(controller.state) }
    BalanceTutorialContent(
        state = state,
        onCellTapped = { position ->
            controller.onCellTapped(position)
            state = controller.state
        },
        onSelectValue = { value ->
            controller.selectValue(value)
            state = controller.state
        },
        onTogglePencil = {
            controller.togglePencilMode()
            state = controller.state
        },
        onDone = onDone,
        modifier = modifier,
    )
}

private fun BalanceTutorialStage.titleResource(): StringResource =
    when (this) {
        BalanceTutorialStage.PREVENT_THREE -> Res.string.balance_tutorial_stage_one_title
        BalanceTutorialStage.COMPLETE_QUOTA -> Res.string.balance_tutorial_stage_two_title
        BalanceTutorialStage.PRESERVE_UNIQUENESS -> Res.string.balance_tutorial_stage_three_title
        BalanceTutorialStage.INDEPENDENT_PUZZLE -> Res.string.balance_tutorial_stage_four_title
    }

private fun BalanceTutorialStage.bodyResource(): StringResource =
    when (this) {
        BalanceTutorialStage.PREVENT_THREE -> Res.string.balance_tutorial_stage_one_body
        BalanceTutorialStage.COMPLETE_QUOTA -> Res.string.balance_tutorial_stage_two_body
        BalanceTutorialStage.PRESERVE_UNIQUENESS -> Res.string.balance_tutorial_stage_three_body
        BalanceTutorialStage.INDEPENDENT_PUZZLE -> Res.string.balance_tutorial_stage_four_body
    }
