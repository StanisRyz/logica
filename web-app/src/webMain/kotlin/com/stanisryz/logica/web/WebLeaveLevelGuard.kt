package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.word.WordGameState
import com.stanisryz.logica.ui.components.GameRulesSheet
import com.stanisryz.logica.ui.components.LocalGameSounds
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.web.generated.resources.web_leave_body
import com.stanisryz.logica.web.generated.resources.web_leave_confirm
import com.stanisryz.logica.web.generated.resources.web_leave_stay
import com.stanisryz.logica.web.generated.resources.web_leave_title
import com.stanisryz.logica.web.generated.resources.web_rules
import com.stanisryz.logica.web.generated.resources.web_to_difficulty
import com.stanisryz.logica.web.generated.resources.web_to_games
import com.stanisryz.logica.web.generated.resources.web_wallet_gems
import com.stanisryz.logica.web.generated.resources.web_wallet_lives
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/**
 * Runs a player-tapped transition out of gameplay (leaving a level, Retry, To difficulty, To
 * games) behind a possible interstitial; the transition runs exactly once whether or not an ad
 * appears. The default runs it at once.
 */
internal val LocalWebTransitionAd = staticCompositionLocalOf<(() -> Unit) -> Unit> { { transition -> transition() } }

/**
 * Gameplay header with the Web counterpart of Android's `LeaveLevelGuard`: leaving a non-terminal
 * attempt that already has real progress asks for confirmation first, because unfinished
 * attempts are never persisted. Everything else leaves immediately.
 */
@Composable
internal fun WebGameplayHeader(
    puzzleType: PuzzleType,
    isDaily: Boolean,
    hasMeaningfulProgress: Boolean,
    onExit: () -> Unit,
) {
    var confirmingExit by remember { mutableStateOf(false) }
    val transitionAd = LocalWebTransitionAd.current
    WebTopBar(
        backLabel = stringResource(if (isDaily) WebRes.string.web_to_games else WebRes.string.web_to_difficulty),
        onBack = { if (hasMeaningfulProgress) confirmingExit = true else transitionAd(onExit) },
        title = stringResource(puzzleType.catalogTitleResource()),
        helpFor = puzzleType,
    )
    if (confirmingExit && hasMeaningfulProgress) {
        PauseGameKeysWhileShown()
        AlertDialog(
            onDismissRequest = { confirmingExit = false },
            title = { Text(stringResource(WebRes.string.web_leave_title)) },
            text = { Text(stringResource(WebRes.string.web_leave_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingExit = false
                        transitionAd(onExit)
                    },
                ) { Text(stringResource(WebRes.string.web_leave_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmingExit = false }) { Text(stringResource(WebRes.string.web_leave_stay)) } },
        )
    }
}

/**
 * The Web game/difficulty bar: the way back on the left, the game title truly centred whatever
 * the sides measure, and the wallet (which opens the Store sheet) on the right.
 */
@Composable
internal fun WebTopBar(
    backLabel: String,
    onBack: () -> Unit,
    title: String,
    helpFor: PuzzleType? = null,
) {
    var rulesOpen by remember { mutableStateOf(false) }
    if (rulesOpen && helpFor != null) {
        PauseGameKeysWhileShown()
        GameRulesSheet(helpFor, onDismiss = { rulesOpen = false })
    }
    // Reaching a game screen means the player has tapped: a good moment to load the sounds.
    val sounds = LocalGameSounds.current
    LaunchedEffect(sounds) { (sounds as? WebGameSoundPlayer)?.preload() }
    Box(
        modifier = Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text(backLabel) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (helpFor != null) {
                IconButton(onClick = { rulesOpen = true }, modifier = Modifier.size(HELP_BUTTON_SIZE)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(WebRes.string.web_rules),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(HELP_ICON_SIZE),
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) { WebGameplayWallet() }
    }
}

// The progress rules mirror the Android gameplay ViewModels' `hasMeaningfulProgress`.

internal val BalanceGameState.hasMeaningfulProgress: Boolean
    get() =
        !status.isTerminal &&
            (
                cellStatuses.values.any { it != BalanceCellStatus.FIXED } ||
                    pencilMarks.isNotEmpty() ||
                    mistakesUsed > 0 ||
                    hintsUsed > 0
            )

internal val CrownsGameState.hasMeaningfulProgress: Boolean
    get() =
        !status.isTerminal &&
            (
                cellStatuses.isNotEmpty() ||
                    pencilCrowns.isNotEmpty() ||
                    pencilMarks.isNotEmpty() ||
                    userMarks.isNotEmpty() ||
                    mistakesUsed > 0 ||
                    hintsUsed > 0
            )

internal val WordGameState.hasMeaningfulProgress: Boolean
    get() = !isFinished && (attempts.isNotEmpty() || currentDraft.positions.any { it != null })

internal val SudokuGameState.hasMeaningfulProgress: Boolean
    get() =
        !status.isTerminal &&
            (
                cells.any { cell ->
                    cell.status == SudokuCellStatus.CORRECT ||
                        cell.status == SudokuCellStatus.INCORRECT ||
                        !cell.candidates.isEmpty
                } ||
                    mistakesUsed > 0 ||
                    hintsUsed > 0
            )

/** A cleared Catalog level has nothing left to lose once its completion is durably saved. */
internal fun Game2048State.hasMeaningfulProgress(
    levelCleared: Boolean,
    completionSaved: Boolean,
): Boolean =
    !status.isTerminal &&
        if (levelCleared) !completionSaved else nextSpawnIndex > GAME_2048_INITIAL_SPAWN_COUNT

private const val GAME_2048_INITIAL_SPAWN_COUNT = 2L

internal val GAME_HEADER_HEIGHT = 52.dp
private val HELP_BUTTON_SIZE = 36.dp
private val HELP_ICON_SIZE = 20.dp

/** Lives and gems stay in sight during play, like Android's game bar; tapping opens the Store sheet. */
@Composable
internal fun WebGameplayWallet() {
    val wallet = LocalWebLives.current.state ?: return
    val openStore = LocalWebOpenStore.current
    Row(horizontalArrangement = Arrangement.spacedBy(WALLET_CHIP_GAP), verticalAlignment = Alignment.CenterVertically) {
        WalletChip(
            icon = if (wallet.lives > 0) Icons.Rounded.Favorite else Icons.Rounded.HeartBroken,
            value = "${wallet.lives}",
            description = stringResource(WebRes.string.web_wallet_lives, wallet.lives, EconomyPolicy.MAXIMUM_LIVES),
            tint = if (wallet.lives > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            onClick = openStore,
        )
        WalletChip(
            icon = Icons.Rounded.Diamond,
            value = "${wallet.gems}",
            description = stringResource(WebRes.string.web_wallet_gems, wallet.gems),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = openStore,
        )
    }
}

@Composable
private fun WalletChip(
    icon: ImageVector,
    value: String,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WALLET_CHIP_PADDING, vertical = WALLET_CHIP_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(WALLET_CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(WALLET_ICON_SIZE))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Opens the Store as a sheet over the running game; provided by the Web host. */
internal val LocalWebOpenStore = staticCompositionLocalOf<() -> Unit> { {} }

private val WALLET_CHIP_GAP = 4.dp
private val WALLET_CHIP_PADDING = 10.dp
private val WALLET_CHIP_VERTICAL_PADDING = 6.dp
private val WALLET_ICON_SIZE = 16.dp
