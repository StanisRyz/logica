package com.stanisryz.logica.ui.tutorial

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_offer_body
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_offer_open
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_offer_skip
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_offer_title
import com.stanisryz.logica.ui.components.catalogTitleResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time offer shown when a player first picks a difficulty in a game: open the tutorial, or
 * play right away. Hosts decide when it is due and remember that it was shown; dismissing it
 * starts nothing and keeps the offer for the next tap.
 */
@Composable
fun FirstPlayTutorialDialog(
    puzzleType: PuzzleType,
    onOpenTutorial: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.tutorial_offer_title, stringResource(puzzleType.catalogTitleResource()))) },
        text = { Text(stringResource(Res.string.tutorial_offer_body)) },
        confirmButton = { Button(onClick = onOpenTutorial) { Text(stringResource(Res.string.tutorial_offer_open)) } },
        dismissButton = { TextButton(onClick = onPlay) { Text(stringResource(Res.string.tutorial_offer_skip)) } },
    )
}
