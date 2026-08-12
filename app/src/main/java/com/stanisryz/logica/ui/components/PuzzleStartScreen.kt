package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared structure of the Catalog start screens: puzzle title, short explanation, difficulty
 * section, primary Start, and a secondary way into the tutorial. Each difficulty carries the level it
 * currently stands on, because progression is per game and per difficulty. Puzzle-specific rule text
 * and difficulty details are supplied by the caller.
 *
 * There is no saved-progress branch any more: Start always opens that difficulty's current level.
 */
@Composable
internal fun PuzzleStartScreen(
    puzzleType: PuzzleType,
    introResource: Int,
    tutorialOfferBodyResource: Int,
    tutorialCompleted: Boolean,
    levels: Map<Difficulty, Int>,
    economy: PlayerEconomy,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    onRestoreLife: () -> Unit,
    modifier: Modifier = Modifier,
    ruleResources: List<Int> = emptyList(),
    difficultyNoteResource: Int? = null,
    difficultyLabel: @Composable (Difficulty) -> String = { it.russianLabel() },
    difficultySupportingText: @Composable (Difficulty) -> String? = { null },
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }

    ScreenColumn(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            PuzzleTitle(stringResource(puzzleType.titleResource()), puzzleType = puzzleType)
            BodyText(stringResource(introResource))
            ruleResources.forEach { rule -> BodyText(stringResource(rule)) }
        }

        if (!tutorialCompleted) {
            LogicaCard(verticalSpacing = LogicaSpacing.item) {
                SupportingText(stringResource(tutorialOfferBodyResource))
                FilledTonalButton(onClick = onOpenTutorial, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.take_tutorial))
                }
            }
        }

        ScreenSection(
            title = stringResource(R.string.difficulty),
            supportingText = difficultyNoteResource?.let { stringResource(it) },
        ) {
            DifficultySelector(
                selected = selectedDifficulty,
                onSelected = { selectedDifficulty = it },
                label = { difficulty ->
                    val base = difficultyLabel(difficulty)
                    levels[difficulty]
                        ?.let { level -> stringResource(R.string.catalog_difficulty_level, base, level) }
                        ?: base
                },
                supportingText = difficultySupportingText,
            )
        }

        ZeroLivesCard(economy, onRestoreLife)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { onStart(selectedDifficulty) },
                enabled = economy.isGameplayAllowed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.start))
            }
            if (tutorialCompleted) {
                TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
            }
        }
    }
}
