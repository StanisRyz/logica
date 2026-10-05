package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.GameResultDialog
import com.stanisryz.logica.ui.components.GameResultEconomy
import com.stanisryz.logica.ui.components.GameResultSaveState
import com.stanisryz.logica.ui.components.StateArtwork
import com.stanisryz.logica.ui.components.StateArtworkImage
import com.stanisryz.logica.ui.components.displayName
import com.stanisryz.logica.ui.components.starsForMistakes
import com.stanisryz.logica.web.generated.resources.web_back_to_difficulty
import com.stanisryz.logica.web.generated.resources.web_error_daily
import com.stanisryz.logica.web.generated.resources.web_error_level
import com.stanisryz.logica.web.generated.resources.web_error_progress
import com.stanisryz.logica.web.generated.resources.web_loading_level
import com.stanisryz.logica.web.generated.resources.web_loading_progress
import com.stanisryz.logica.web.generated.resources.web_retry
import com.stanisryz.logica.web.generated.resources.web_save_error_catalog
import com.stanisryz.logica.web.generated.resources.web_save_error_daily
import com.stanisryz.logica.web.generated.resources.web_save_error_generic
import com.stanisryz.logica.web.generated.resources.web_score_failed
import com.stanisryz.logica.web.generated.resources.web_score_final
import com.stanisryz.logica.web.generated.resources.web_to_difficulty
import com.stanisryz.logica.web.generated.resources.web_to_games
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

@Composable
internal fun WebCatalogLoadingContent(
    difficulty: Difficulty,
    levelNumber: Int?,
    onBack: () -> Unit,
    isDaily: Boolean = false,
) {
    CenteredColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            if (levelNumber == null) {
                stringResource(WebRes.string.web_loading_progress, difficulty.displayName())
            } else {
                stringResource(WebRes.string.web_loading_level, levelNumber, difficulty.displayName())
            },
        )
        Spacer(Modifier.height(12.dp))
        WebStateBackButton(
            label = stringResource(if (isDaily) WebRes.string.web_to_games else WebRes.string.web_back_to_difficulty),
            onClick = onBack,
        )
    }
}

/**
 * The way back from a loading or failed level. Framed and with an arrow so it is there from the
 * first frame even while its Web text resource is still loading.
 */
@Composable
private fun WebStateBackButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun WebCatalogLevelErrorContent(
    levelNumber: Int?,
    failure: WebLoadFailure,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isDaily: Boolean = false,
) {
    CenteredColumn {
        StateArtworkImage(StateArtwork.LOAD_FAILED)
        Spacer(Modifier.height(12.dp))
        Text(
            text =
                when {
                    isDaily -> stringResource(WebRes.string.web_error_daily)
                    levelNumber != null -> stringResource(WebRes.string.web_error_level, levelNumber)
                    else -> stringResource(WebRes.string.web_error_progress)
                },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(failure.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(WebRes.string.web_retry)) }
        Spacer(Modifier.height(8.dp))
        WebStateBackButton(
            label = stringResource(if (isDaily) WebRes.string.web_to_games else WebRes.string.web_to_difficulty),
            onClick = onBack,
        )
    }
}

@Composable
internal fun WebOrdinaryCatalogTerminalDialog(
    puzzleType: PuzzleType,
    visible: Boolean,
    levelNumber: Int,
    difficulty: Difficulty,
    solved: Boolean,
    completion: WebCatalogCompletionState,
    solvedDetail: String? = null,
    failedDetail: String? = null,
    mistakesUsed: Int? = null,
    hintsUsed: Int? = null,
    stars: Int? = null,
    replay: Boolean = false,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
) {
    if (!visible) return
    PauseGameKeysWhileShown()
    val saveError = completion as? WebCatalogCompletionState.SaveError
    // A failed level never saves progress, so only a solved one can wait on or fail its save.
    val saveState =
        when {
            !solved -> GameResultSaveState.SAVED
            saveError != null -> GameResultSaveState.ERROR
            completion is WebCatalogCompletionState.Saved -> GameResultSaveState.SAVED
            else -> GameResultSaveState.SAVING
        }
    GameResultDialog(
        solved = solved,
        levelNumber = levelNumber,
        isDaily = false,
        difficultyLabel = difficulty.displayName(),
        saveState = saveState,
        onNextLevel = onNextLevel,
        onRetry = onRetry,
        onRetrySave = onRetrySave,
        onExit = onBack,
        detail = if (solved) solvedDetail else failedDetail,
        saveErrorDetail = saveError?.let { stringResource(WebRes.string.web_save_error_catalog) },
        economy =
            when {
                // A solved replay only raises stars; it pays nothing.
                solved && replay -> GameResultEconomy()
                solved -> GameResultEconomy(gemsEarned = WebEconomyProcessor.gemRewardFor(puzzleType, difficulty))
                else -> GameResultEconomy(livesLost = 1)
            },
        mistakesUsed = mistakesUsed,
        hintsUsed = hintsUsed,
        exitToDifficulty = true,
        stars = if (solved) stars ?: mistakesUsed?.let(::starsForMistakes) else null,
    )
}

@Composable
internal fun WebDailyOrdinaryTerminalDialog(
    puzzleType: PuzzleType,
    visible: Boolean,
    difficulty: Difficulty,
    solved: Boolean,
    completion: WebDailyCompletionState,
    scoreDetail: String? = null,
    mistakesUsed: Int? = null,
    hintsUsed: Int? = null,
    stars: Int? = null,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onExit: () -> Unit,
) {
    if (!visible) return
    PauseGameKeysWhileShown()
    GameResultDialog(
        solved = solved,
        levelNumber = null,
        isDaily = true,
        difficultyLabel = difficulty.displayName(),
        saveState =
            when (completion) {
                is WebDailyCompletionState.SaveError -> GameResultSaveState.ERROR
                is WebDailyCompletionState.Saved -> GameResultSaveState.SAVED
                else -> GameResultSaveState.SAVING
            },
        onNextLevel = onExit,
        // A failed Daily entry stays open for a fresh real attempt of the same puzzle.
        onRetry = onRetry,
        onRetrySave = onRetrySave,
        onExit = onExit,
        detail = scoreDetail,
        economy =
            if (solved) {
                GameResultEconomy(gemsEarned = WebEconomyProcessor.gemRewardFor(puzzleType, difficulty))
            } else {
                GameResultEconomy(livesLost = 1)
            },
        saveErrorDetail =
            (completion as? WebDailyCompletionState.SaveError)?.let {
                stringResource(WebRes.string.web_save_error_daily)
            },
        mistakesUsed = mistakesUsed,
        hintsUsed = hintsUsed,
        stars = if (solved) stars ?: mistakesUsed?.let(::starsForMistakes) else null,
    )
}

@Composable
internal fun WebCatalogSaveErrorBanner(
    completion: WebCatalogCompletionState,
    onRetrySave: () -> Unit,
) {
    val error = completion as? WebCatalogCompletionState.SaveError ?: return
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(WebRes.string.web_save_error_generic),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetrySave) { Text(stringResource(WebRes.string.web_retry)) }
        }
    }
}

@Composable
internal fun Web2048CatalogTerminalDialog(
    visible: Boolean,
    levelNumber: Int,
    difficulty: Difficulty,
    goalReached: Boolean,
    score: String,
    completion: WebCatalogCompletionState,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
) {
    if (!visible) return
    WebOrdinaryCatalogTerminalDialog(
        puzzleType = PuzzleType.GAME_2048,
        visible = true,
        levelNumber = levelNumber,
        difficulty = difficulty,
        solved = goalReached,
        completion = completion,
        solvedDetail = stringResource(WebRes.string.web_score_final, score),
        failedDetail = stringResource(WebRes.string.web_score_failed, score),
        onNextLevel = onNextLevel,
        onRetry = onRetry,
        onRetrySave = onRetrySave,
        onBack = onBack,
    )
}
