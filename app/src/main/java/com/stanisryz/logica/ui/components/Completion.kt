package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared completion shell. Balance, Crowns, Word, Sudoku, and the completed Daily use the same
 * icon-plus-title header and only differ in the content and actions they place below it.
 */
@Composable
internal fun CompletionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalLogicaPalette.current.successContainer,
    contentColor: Color = LocalLogicaPalette.current.onSuccessContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    LogicaCard(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(COMPLETION_ICON_SIZE))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        content()
    }
}

/**
 * Balance, Crowns, and Sudoku share one terminal dialog for both endings. A failed attempt leads
 * with Retry on the same puzzle; a solved Daily returns to the Game hub, while each Catalog game
 * chooses new-puzzle or same-puzzle replay as its primary action. The board stays visible behind it.
 *
 * The wallet effect is reported only once the result is durably stored, and a retry is offered only
 * while a life is available.
 */
@Composable
internal fun PuzzleTerminalDialog(
    isSolved: Boolean,
    completionPersistence: CompletionPersistence,
    hintsUsed: Int,
    maxMistakes: Int,
    lives: Int,
    difficulty: Difficulty,
    isRetryAllowed: Boolean,
    isDaily: Boolean,
    onRetryCompletion: () -> Unit,
    onRetryPuzzle: () -> Unit,
    onNewPuzzle: () -> Unit,
    onGameHub: () -> Unit,
    preferSamePuzzleRetry: Boolean = false,
) {
    val isError = completionPersistence == CompletionPersistence.Error
    val isSaved = completionPersistence == CompletionPersistence.Saved
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = if (isError || !isSolved) Icons.Filled.ErrorOutline else Icons.Filled.TaskAlt,
                contentDescription = null,
                tint =
                    if (isError || !isSolved) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalLogicaPalette.current.success
                    },
            )
        },
        title = {
            Text(
                stringResource(
                    when {
                        isError -> R.string.completion_save_error_title
                        isSolved -> R.string.puzzle_solved
                        else -> R.string.puzzle_failed
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(
                    when {
                        completionPersistence == CompletionPersistence.Error ->
                            stringResource(R.string.completion_save_error_body)
                        !isSaved -> stringResource(R.string.saving_completion)
                        isSolved -> stringResource(R.string.hints_used, hintsUsed)
                        else -> stringResource(R.string.puzzle_failed_body, maxMistakes)
                    },
                )
                if (isSaved) {
                    EconomyResultFeedback(isSolved = isSolved, lives = lives, difficulty = difficulty)
                }
            }
        },
        confirmButton = {
            when {
                completionPersistence == CompletionPersistence.Error ->
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                !isSaved -> TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                !isSolved || preferSamePuzzleRetry ->
                    TextButton(onClick = onRetryPuzzle, enabled = isRetryAllowed) {
                        Text(stringResource(R.string.retry_puzzle))
                    }
                isDaily -> TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
                else -> TextButton(onClick = onNewPuzzle) { Text(stringResource(R.string.new_puzzle)) }
            }
        },
        dismissButton = {
            // One way out of a finished attempt, because Daily and the catalog share one hub.
            if (isSaved && !(isDaily && isSolved)) {
                TextButton(onClick = onGameHub) { Text(stringResource(R.string.to_games)) }
            }
        },
    )
}

/** The completion actions row, so every puzzle keeps the same primary/secondary hierarchy. */
@Composable
internal fun CompletionActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A short label/value line used inside completion and statistics cards. */
@Composable
internal fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private val COMPLETION_ICON_SIZE = 24.dp
