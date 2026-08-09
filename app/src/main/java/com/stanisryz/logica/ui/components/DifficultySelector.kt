package com.stanisryz.logica.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The one difficulty selector for Balance, Crowns, and Word. The four core difficulties never
 * change; only the supporting line differs, which is how Word shows its 4/5/6/7 letter lengths.
 */
@Composable
internal fun DifficultySelector(
    selected: Difficulty,
    onSelected: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (Difficulty) -> String = { it.russianLabel() },
    supportingText: @Composable (Difficulty) -> String? = { null },
) {
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        Difficulty.entries.forEach { difficulty ->
            DifficultyOption(
                label = label(difficulty),
                supportingText = supportingText(difficulty),
                isSelected = difficulty == selected,
                onSelected = { onSelected(difficulty) },
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    label: String,
    supportingText: String?,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelected),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
                contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
            ),
        // Non-color cue: the selected option keeps a filled check and an outline.
        border = if (isSelected) BorderStroke(1.dp, colors.secondary) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LogicaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                modifier = Modifier.size(SELECTION_ICON_SIZE),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private val SELECTION_ICON_SIZE = 22.dp
