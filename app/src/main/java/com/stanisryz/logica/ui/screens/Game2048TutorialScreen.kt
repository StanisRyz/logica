package com.stanisryz.logica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stanisryz.logica.R
import com.stanisryz.logica.settings.SettingsRepository
import com.stanisryz.logica.ui.components.LogicaCard
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.ScreenTitle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.launch

/** Static onboarding with no engine repositories, sessions, results, or economy dependencies. */
@Composable
internal fun Game2048TutorialRoute(
    settingsRepository: SettingsRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Game2048TutorialScreen(
        onDone = {
            lifecycleOwner.lifecycleScope.launch { settingsRepository.setGame2048TutorialCompleted(true) }
            onDone()
        },
        modifier = modifier,
    )
}

@Composable
private fun Game2048TutorialScreen(
    onDone: () -> Unit,
    modifier: Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    ScreenColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle(stringResource(R.string.game_2048_tutorial_title))
        Text(stringResource(R.string.game_2048_tutorial_progress, step + 1), style = MaterialTheme.typography.labelLarge)
        LogicaCard {
            Text(stringResource(STEP_TITLES[step]), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(STEP_BODIES[step]), style = MaterialTheme.typography.bodyMedium)
            if (step == MERGE_STEP) {
                Text(
                    stringResource(R.string.game_2048_tutorial_merge_example),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action, Alignment.End),
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text(stringResource(R.string.back)) }
            }
            Button(onClick = { if (step == LAST_STEP) onDone() else step += 1 }) {
                Text(stringResource(if (step == LAST_STEP) R.string.done else R.string.continue_game))
            }
        }
    }
}

private val STEP_TITLES =
    listOf(
        R.string.game_2048_tutorial_swipe_title,
        R.string.game_2048_tutorial_merge_title,
        R.string.game_2048_tutorial_goal_title,
    )
private val STEP_BODIES =
    listOf(
        R.string.game_2048_tutorial_swipe_body,
        R.string.game_2048_tutorial_merge_body,
        R.string.game_2048_tutorial_goal_body,
    )
private const val MERGE_STEP = 1
private const val LAST_STEP = 2
