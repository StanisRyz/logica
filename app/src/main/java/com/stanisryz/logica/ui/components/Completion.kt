package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.ui.theme.LocalLogicaPalette
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared completion shell. Balance, Crowns, Word, and the completed Daily all use the same
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
 * Balance and Crowns share one solved dialog. Daily returns to Today; a Catalog game keeps its
 * New puzzle / Catalog navigation.
 */
@Composable
internal fun PuzzleSolvedDialog(
    completionPersistence: CompletionPersistence,
    hintsUsed: Int,
    isDaily: Boolean,
    onRetryCompletion: () -> Unit,
    onNewPuzzle: () -> Unit,
    onCatalog: () -> Unit,
    onToday: () -> Unit,
) {
    val isError = completionPersistence == CompletionPersistence.Error
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.TaskAlt,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else LocalLogicaPalette.current.success,
            )
        },
        title = {
            Text(stringResource(if (isError) R.string.completion_save_error_title else R.string.puzzle_solved))
        },
        text = {
            Text(
                when (completionPersistence) {
                    CompletionPersistence.NotRequired,
                    CompletionPersistence.Saving,
                    -> stringResource(R.string.saving_completion)
                    CompletionPersistence.Saved -> stringResource(R.string.hints_used, hintsUsed)
                    CompletionPersistence.Error -> stringResource(R.string.completion_save_error_body)
                },
            )
        },
        confirmButton = {
            when (completionPersistence) {
                CompletionPersistence.NotRequired,
                CompletionPersistence.Saving,
                -> TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.saving)) }
                CompletionPersistence.Error ->
                    TextButton(onClick = onRetryCompletion) { Text(stringResource(R.string.retry)) }
                CompletionPersistence.Saved ->
                    if (isDaily) {
                        TextButton(onClick = onToday) { Text(stringResource(R.string.to_today)) }
                    } else {
                        TextButton(onClick = onNewPuzzle) { Text(stringResource(R.string.new_puzzle)) }
                    }
            }
        },
        dismissButton = {
            if (!isDaily && completionPersistence == CompletionPersistence.Saved) {
                TextButton(onClick = onCatalog) { Text(stringResource(R.string.to_catalog)) }
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
