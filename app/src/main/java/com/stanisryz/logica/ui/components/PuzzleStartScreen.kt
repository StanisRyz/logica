package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The shared structure of the three start screens: puzzle title, short explanation, difficulty
 * section, primary Start, and a secondary way into the tutorial. Puzzle-specific rule text and
 * difficulty details are supplied by the caller.
 */
@Composable
internal fun PuzzleStartScreen(
    puzzleType: PuzzleType,
    introResource: Int,
    tutorialOfferBodyResource: Int,
    tutorialCompleted: Boolean,
    hasActiveSession: Boolean,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    ruleResources: List<Int> = emptyList(),
    difficultyNoteResource: Int? = null,
    difficultyLabel: @Composable (Difficulty) -> String = { it.russianLabel() },
    difficultySupportingText: @Composable (Difficulty) -> String? = { null },
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }
    var showReplaceConfirmation by rememberSaveable { mutableStateOf(false) }
    val start = { if (hasActiveSession) showReplaceConfirmation = true else onStart(selectedDifficulty) }

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
                label = difficultyLabel,
                supportingText = difficultySupportingText,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = start, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start))
            }
            if (tutorialCompleted) {
                TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
            }
        }
    }

    if (showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmation = false },
            title = { Text(stringResource(R.string.start_new_game_title)) },
            text = { Text(stringResource(R.string.start_new_game_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirmation = false
                        onStart(selectedDifficulty)
                    },
                ) { Text(stringResource(R.string.start)) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
