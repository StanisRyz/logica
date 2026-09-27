package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_complete_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_complete_title
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_feedback_diagonal
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_feedback_marks
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_feedback_mini_puzzle
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_feedback_region
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_feedback_row_column
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_five_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_five_title
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_four_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_four_title
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_one_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_one_title
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_three_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_three_title
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_two_body
import com.stanisryz.logica.shared.ui.generated.resources.crowns_tutorial_stage_two_title
import com.stanisryz.logica.ui.crowns.CrownsBoard
import com.stanisryz.logica.ui.crowns.CrownsToolBar
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stateless Crowns onboarding shared by Android and Web; the host owns the controller and may wrap
 * [onCellTapped] with platform feedback such as haptics.
 */
@Composable
fun CrownsTutorialContent(
    state: CrownsTutorialUiState,
    onCellTapped: (CrownsPosition) -> Unit,
    onSelectValue: (CrownsPlayerCell) -> Unit,
    onTogglePencil: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialLayout(
        step = state.stage.ordinal + 1,
        stepCount = CrownsTutorialStage.entries.size,
        title = stringResource(state.stage.titleResource()),
        body = stringResource(state.stage.bodyResource()),
        feedback = state.feedback?.let { stringResource(it.textResource()) },
        modifier = modifier,
    ) {
        CrownsBoard(
            puzzle = state.puzzle,
            game = state.game,
            onCellTapped = onCellTapped,
            guidedPositions = state.focusedPositions,
            modifier = Modifier.widthIn(max = TUTORIAL_BOARD_MAX_WIDTH).fillMaxWidth(),
        )
        CrownsToolBar(state.selectedValue, state.isPencilMode, onSelectValue, onTogglePencil)
    }
    if (state.completed) {
        TutorialCompleteDialog(
            title = stringResource(Res.string.crowns_tutorial_complete_title),
            body = stringResource(Res.string.crowns_tutorial_complete_body),
            onDone = onDone,
        )
    }
}

/** Self-contained Crowns onboarding for hosts without a ViewModel layer (Web). */
@Composable
fun CrownsTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = remember { CrownsTutorialController() }
    var state by remember { mutableStateOf(controller.state) }
    CrownsTutorialContent(
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

private fun CrownsTutorialStage.titleResource(): StringResource =
    when (this) {
        CrownsTutorialStage.ROW_AND_COLUMN -> Res.string.crowns_tutorial_stage_one_title
        CrownsTutorialStage.REGION -> Res.string.crowns_tutorial_stage_two_title
        CrownsTutorialStage.DIAGONAL -> Res.string.crowns_tutorial_stage_three_title
        CrownsTutorialStage.MARKS_AND_CONTROLS -> Res.string.crowns_tutorial_stage_four_title
        CrownsTutorialStage.MINI_PUZZLE -> Res.string.crowns_tutorial_stage_five_title
    }

private fun CrownsTutorialStage.bodyResource(): StringResource =
    when (this) {
        CrownsTutorialStage.ROW_AND_COLUMN -> Res.string.crowns_tutorial_stage_one_body
        CrownsTutorialStage.REGION -> Res.string.crowns_tutorial_stage_two_body
        CrownsTutorialStage.DIAGONAL -> Res.string.crowns_tutorial_stage_three_body
        CrownsTutorialStage.MARKS_AND_CONTROLS -> Res.string.crowns_tutorial_stage_four_body
        CrownsTutorialStage.MINI_PUZZLE -> Res.string.crowns_tutorial_stage_five_body
    }

private fun CrownsTutorialFeedback.textResource(): StringResource =
    when (this) {
        CrownsTutorialFeedback.ROW_AND_COLUMN -> Res.string.crowns_tutorial_feedback_row_column
        CrownsTutorialFeedback.REGION -> Res.string.crowns_tutorial_feedback_region
        CrownsTutorialFeedback.DIAGONAL -> Res.string.crowns_tutorial_feedback_diagonal
        CrownsTutorialFeedback.MARKS_AND_CONTROLS -> Res.string.crowns_tutorial_feedback_marks
        CrownsTutorialFeedback.MINI_PUZZLE -> Res.string.crowns_tutorial_feedback_mini_puzzle
    }
