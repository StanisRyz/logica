package com.stanisryz.logica.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_done
import com.stanisryz.logica.shared.ui.generated.resources.tutorial_step
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * The one onboarding layout shared by every game and both hosts: step counter, title, instruction,
 * the interactive example, optional feedback, and the step navigation. It scrolls, so the example
 * never has to squeeze into a short host.
 */
@Composable
internal fun TutorialLayout(
    step: Int?,
    stepCount: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    feedback: String? = null,
    footer: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // The step navigation stays pinned below the scrolling example, so it is always reachable.
    val scroll = rememberScrollState()
    // Every new step starts at its title rather than wherever the previous one was scrolled.
    LaunchedEffect(step, title) { scroll.scrollTo(0) }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step != null) {
                Text(
                    text = stringResource(Res.string.tutorial_step, step, stepCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            // Feedback sits right under the instruction, where the eye already is, not below the board.
            if (feedback != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = feedback, modifier = Modifier.padding(LogicaSpacing.cardContent))
                }
            }
            content()
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = LogicaSpacing.item),
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = footer,
        )
    }
}

@Composable
internal fun TutorialCompleteDialog(
    title: String,
    body: String,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(Res.string.tutorial_done)) } },
    )
}

/** Keeps the example board and its tools on one screen together with the instruction. */
internal val TUTORIAL_BOARD_MAX_WIDTH = 320.dp
