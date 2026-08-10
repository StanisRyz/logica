package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.BodyText
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.PuzzleTitle
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.components.StatusChip
import com.stanisryz.logica.ui.components.ZeroLivesCard
import com.stanisryz.logica.ui.components.titleResource
import com.stanisryz.logica.ui.theme.LogicaSpacing

internal data class CatalogPuzzleCard(
    val puzzleType: PuzzleType,
    val descriptionResource: Int,
    val hasActiveSession: Boolean,
    val onContinue: () -> Unit,
    val onNew: () -> Unit,
)

@Composable
internal fun CatalogScreen(
    puzzles: List<CatalogPuzzleCard>,
    economy: PlayerEconomy,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        ScreenTitle(stringResource(R.string.puzzles))
        ZeroLivesCard(economy, onRestoreLife)
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
            puzzles.forEach { puzzle -> CatalogCard(puzzle, economy.isGameplayAllowed) }
        }
    }
}

@Composable
private fun CatalogCard(
    puzzle: CatalogPuzzleCard,
    gameplayAllowed: Boolean,
) {
    LogicaCard {
        PuzzleTitle(stringResource(puzzle.puzzleType.titleResource()), puzzleType = puzzle.puzzleType)
        BodyText(stringResource(puzzle.descriptionResource))
        if (puzzle.hasActiveSession) {
            // Continue is the primary action; New game stays secondary and routes through the
            // start screen, which already confirms before replacing saved progress. Continue keeps
            // opening the saved puzzle at zero lives; its gameplay actions are what stay disabled.
            StatusChip(
                icon = Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.catalog_active_session),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
            ) {
                Button(onClick = puzzle.onContinue, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.continue_game))
                }
                OutlinedButton(
                    onClick = puzzle.onNew,
                    enabled = gameplayAllowed,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.new_game))
                }
            }
        } else {
            Button(onClick = puzzle.onNew, enabled = gameplayAllowed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.play))
            }
        }
    }
}
