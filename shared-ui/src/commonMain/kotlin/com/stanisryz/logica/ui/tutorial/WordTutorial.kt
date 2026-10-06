package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordAllowedGuesses
import com.stanisryz.logica.puzzle.core.word.WordCatalogContent
import com.stanisryz.logica.puzzle.core.word.WordGameEngine
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.puzzle.core.word.WordLanguage
import com.stanisryz.logica.puzzle.core.word.WordPuzzle
import com.stanisryz.logica.puzzle.core.word.WordSubmitResult
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_absent
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_attempts
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_correct
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_length
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_present
import com.stanisryz.logica.shared.ui.generated.resources.word_rule_repeats
import com.stanisryz.logica.shared.ui.generated.resources.word_rules_intro
import com.stanisryz.logica.shared.ui.generated.resources.word_tutorial_example_body
import com.stanisryz.logica.shared.ui.generated.resources.word_tutorial_example_title
import com.stanisryz.logica.shared.ui.generated.resources.word_tutorial_rules_title
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.word.WordBoard
import org.jetbrains.compose.resources.stringResource

/** Word rules plus one worked attempt computed by the real engine, so it never drifts from the rules. */
@Composable
fun WordTutorial(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    language: WordLanguage = WordLanguage.RUSSIAN,
) {
    val example = remember(language) { exampleGame(language) }
    TutorialLayout(
        step = null,
        stepCount = 1,
        title = stringResource(Res.string.word_tutorial_rules_title),
        body = stringResource(Res.string.word_rules_intro),
        modifier = modifier,
        footer = { Button(onClick = onDone) { Text(stringResource(Res.string.tutorial_done)) } },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text),
        ) {
            listOf(
                Res.string.word_rule_length,
                Res.string.word_rule_attempts,
                Res.string.word_rule_correct,
                Res.string.word_rule_present,
                Res.string.word_rule_absent,
                Res.string.word_rule_repeats,
            ).forEach { rule -> Text(stringResource(rule), style = MaterialTheme.typography.bodyMedium) }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(LogicaSpacing.cardContent),
                verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.word_tutorial_example_title), style = MaterialTheme.typography.titleMedium)
                WordBoard(example, visibleRows = example.attempts.size)
                Text(stringResource(Res.string.word_tutorial_example_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * One worked try per language, each showing the repeated-letter rule the same way: two letters in the
 * word but out of place, one absent, the last letter in place, and an earlier copy of it fading —
 * `полка`/`лампа`, `scope`/`peace`, `sergi`/`giysi`. The interface language's example text describes
 * the same pair. The example accepts only its own guess, so it needs no bundled lexicon (which Web
 * loads lazily).
 */
private fun exampleGame(language: WordLanguage): WordGameState {
    val (answer, guess) =
        when (language) {
            WordLanguage.RUSSIAN -> "полка" to "лампа"
            WordLanguage.ENGLISH -> "scope" to "peace"
            WordLanguage.TURKISH -> "sergi" to "giysi"
        }
    val version = if (language == WordLanguage.RUSSIAN) GeneratorVersion(1) else WordCatalogContent.generatorVersion(language)
    val puzzle =
        WordPuzzle(
            id = PuzzleId(PuzzleType.WORD, Difficulty.EASY, PuzzleSeed(1), version),
            answer = answer,
        )
    val engine = WordGameEngine(puzzle, ExampleGuesses(guess))
    val started =
        guess.foldIndexed(engine.start()) { index, state, letter ->
            engine.setLetter(state, index, letter)
        }
    return (engine.submit(started) as? WordSubmitResult.Accepted)?.state ?: started
}

private class ExampleGuesses(
    private val guess: String,
) : WordAllowedGuesses {
    override val size: Int = 1

    override fun contains(normalizedWord: String): Boolean = normalizedWord == guess

    override fun all(): List<String> = listOf(guess)
}
