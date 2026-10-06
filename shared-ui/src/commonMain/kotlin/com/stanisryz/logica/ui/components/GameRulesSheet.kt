package com.stanisryz.logica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.rules_2048_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_2048_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_2048_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_balance_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_balance_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_balance_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_block_sudoku_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_block_sudoku_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_block_sudoku_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_crowns_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_crowns_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_crowns_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_done
import com.stanisryz.logica.shared.ui.generated.resources.rules_mistakes
import com.stanisryz.logica.shared.ui.generated.resources.rules_nonogram_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_nonogram_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_nonogram_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_sudoku_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_sudoku_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_sudoku_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_title
import com.stanisryz.logica.shared.ui.generated.resources.rules_word_1
import com.stanisryz.logica.shared.ui.generated.resources.rules_word_2
import com.stanisryz.logica.shared.ui.generated.resources.rules_word_3
import com.stanisryz.logica.shared.ui.generated.resources.rules_word_hint
import com.stanisryz.logica.shared.ui.generated.resources.rules_word_language
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A short reminder of a game's rules over the running board, opened from the game header's "?".
 * It changes nothing in the attempt; closing it returns straight to play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRulesSheet(
    puzzleType: PuzzleType,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LogicaSpacing.screenHorizontal).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Text(
                text = stringResource(Res.string.rules_title, stringResource(puzzleType.catalogTitleResource())),
                style = MaterialTheme.typography.titleLarge,
            )
            puzzleType.ruleResources().forEach { rule ->
                Row(horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(text = stringResource(rule), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = LogicaSpacing.text)) {
                Text(stringResource(Res.string.rules_done))
            }
        }
    }
}

private fun PuzzleType.ruleResources(): List<StringResource> =
    when (this) {
        PuzzleType.BALANCE ->
            listOf(
                Res.string.rules_balance_1,
                Res.string.rules_balance_2,
                Res.string.rules_balance_3,
                Res.string.rules_mistakes,
            )
        PuzzleType.CROWNS ->
            listOf(
                Res.string.rules_crowns_1,
                Res.string.rules_crowns_2,
                Res.string.rules_crowns_3,
                Res.string.rules_mistakes,
            )
        PuzzleType.SUDOKU ->
            listOf(Res.string.rules_sudoku_1, Res.string.rules_sudoku_2, Res.string.rules_sudoku_3, Res.string.rules_mistakes)
        PuzzleType.WORD ->
            listOf(
                Res.string.rules_word_language,
                Res.string.rules_word_1,
                Res.string.rules_word_2,
                Res.string.rules_word_3,
                Res.string.rules_word_hint,
            )
        PuzzleType.GAME_2048 -> listOf(Res.string.rules_2048_1, Res.string.rules_2048_2, Res.string.rules_2048_3)
        PuzzleType.NONOGRAM ->
            listOf(Res.string.rules_nonogram_1, Res.string.rules_nonogram_2, Res.string.rules_nonogram_3, Res.string.rules_mistakes)
        PuzzleType.BLOCK_SUDOKU -> listOf(Res.string.rules_block_sudoku_1, Res.string.rules_block_sudoku_2, Res.string.rules_block_sudoku_3)
        else -> emptyList()
    }
