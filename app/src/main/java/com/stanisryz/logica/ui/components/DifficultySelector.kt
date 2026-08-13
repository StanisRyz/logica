package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The one direct-launch difficulty selector for every Catalog game. Each complete button opens the
 * current level for its difficulty; there is intentionally no selected state or separate Start action.
 */
@Composable
internal fun DifficultySelector(
    levels: Map<Difficulty, Int>,
    onStart: (Difficulty) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        Difficulty.entries.forEach { difficulty ->
            DifficultyOption(
                label = difficulty.russianLabel(),
                level = levels[difficulty],
                enabled = enabled,
                onStart = { onStart(difficulty) },
            )
        }
    }
}

@Composable
private fun DifficultyOption(
    label: String,
    level: Int?,
    enabled: Boolean,
    onStart: () -> Unit,
) {
    Button(
        onClick = onStart,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = LogicaSpacing.text),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            level?.let { Text(stringResource(R.string.catalog_level, it), style = MaterialTheme.typography.labelLarge) }
        }
    }
}
