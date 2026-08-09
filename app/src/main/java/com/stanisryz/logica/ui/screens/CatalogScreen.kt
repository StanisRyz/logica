package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R

internal data class CatalogPuzzleCard(
    val titleResource: Int,
    val descriptionResource: Int,
    val hasActiveSession: Boolean,
    val onContinue: () -> Unit,
    val onNew: () -> Unit,
)

@Composable
internal fun CatalogScreen(
    puzzles: List<CatalogPuzzleCard>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.puzzles), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            puzzles.forEach { puzzle ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(puzzle.titleResource), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(puzzle.descriptionResource),
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (puzzle.hasActiveSession) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = puzzle.onContinue) { Text(stringResource(R.string.continue_game)) }
                                Button(onClick = puzzle.onNew) { Text(stringResource(R.string.new_game)) }
                            }
                        } else {
                            Button(onClick = puzzle.onNew) { Text(stringResource(R.string.play)) }
                        }
                    }
                }
            }
        }
    }
}
