package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.R
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.word.WordBoard
import kotlinx.coroutines.launch

@Composable
internal fun WordTutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    WordTutorialScreen(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setWordTutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}

@Composable
private fun WordTutorialScreen(
    onDone: () -> Unit,
    modifier: Modifier,
) {
    // A real core game state, so the worked example can never drift from the actual feedback rules.
    val example = remember { exampleGame() }

    ScreenColumn(modifier = modifier, verticalSpacing = LogicaSpacing.item) {
        ScreenTitle(stringResource(R.string.word_tutorial_title))
        Text(stringResource(R.string.word_rules_intro), style = MaterialTheme.typography.bodyLarge)
        listOf(
            R.string.word_rule_length,
            R.string.word_rule_attempts,
            R.string.word_rule_correct,
            R.string.word_rule_present,
            R.string.word_rule_absent,
            R.string.word_rule_repeats,
        ).forEach { rule -> Text(stringResource(rule), style = MaterialTheme.typography.bodyMedium) }

        LogicaCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.word_tutorial_example_title), style = MaterialTheme.typography.titleMedium)
                WordBoard(example)
                Text(stringResource(R.string.word_tutorial_example_body), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.done))
        }
    }
}

/**
 * Answer `полка` with the guess `лампа`: one `а` lands in the right place while the other is absent,
 * which is exactly the repeated-letter rule the tutorial explains.
 */
private fun exampleGame(): WordGameState {
    val puzzle =
        WordPuzzle(
            id = PuzzleId(PuzzleType.WORD, Difficulty.EASY, PuzzleSeed(1), GeneratorVersion(1)),
            answer = EXAMPLE_ANSWER,
        )
    val engine = WordGameEngine(puzzle, WordLexiconV1.allowedGuesses)
    val started = EXAMPLE_GUESS.fold(engine.start(), engine::appendLetter)
    return (engine.submit(started) as? WordSubmitResult.Accepted)?.state ?: started
}

private const val EXAMPLE_ANSWER = "полка"
private const val EXAMPLE_GUESS = "лампа"
