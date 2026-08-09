package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty

@Composable
internal fun WordStartScreen(
    hasActiveSession: Boolean,
    tutorialCompleted: Boolean,
    onOpenTutorial: () -> Unit,
    onStart: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }
    var showReplaceConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.word), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.word_rules_intro), style = MaterialTheme.typography.bodyLarge)
        listOf(
            R.string.word_rule_length,
            R.string.word_rule_attempts,
            R.string.word_rule_correct,
            R.string.word_rule_present,
            R.string.word_rule_absent,
            R.string.word_rule_repeats,
        ).forEach { rule -> Text(stringResource(rule), style = MaterialTheme.typography.bodyMedium) }

        if (!tutorialCompleted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.word_tutorial_offer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.word_tutorial_offer_body))
                    Button(onClick = onOpenTutorial, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.take_tutorial))
                    }
                    TextButton(
                        onClick = {
                            if (hasActiveSession) showReplaceConfirmation = true else onStart(selectedDifficulty)
                        },
                    ) { Text(stringResource(R.string.play_now)) }
                }
            }
        } else {
            TextButton(onClick = onOpenTutorial) { Text(stringResource(R.string.how_to_play)) }
        }

        Text(
            stringResource(R.string.difficulty),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(stringResource(R.string.word_difficulty_note), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                val isSelected = selectedDifficulty == difficulty
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { selected = isSelected }
                            .clickable(role = Role.RadioButton) { selectedDifficulty = difficulty },
                ) {
                    Text(
                        stringResource(difficulty.wordDifficultyResource()),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Button(
            onClick = {
                if (hasActiveSession) showReplaceConfirmation = true else onStart(selectedDifficulty)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.start)) }
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
            dismissButton = { TextButton(onClick = { showReplaceConfirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Difficulty.wordDifficultyResource(): Int =
    when (this) {
        Difficulty.EASY -> R.string.word_difficulty_easy
        Difficulty.MEDIUM -> R.string.word_difficulty_medium
        Difficulty.HARD -> R.string.word_difficulty_hard
        Difficulty.EXPERT -> R.string.word_difficulty_expert
    }
