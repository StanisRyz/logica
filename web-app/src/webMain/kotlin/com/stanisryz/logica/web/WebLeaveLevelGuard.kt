package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.components.GameIcon
import com.stanisryz.logica.ui.components.GameIconImage
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

/** Costs the life of an unfinished attempt the player confirmed leaving; the default does nothing. */
internal val LocalWebAbandonAttempt = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Gameplay header with the Web counterpart of Android's `LeaveLevelGuard`: leaving a non-terminal
 * attempt that already has real progress asks for confirmation first, because unfinished
 * attempts are never persisted, and confirming costs a life. Everything else leaves immediately.
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
    val abandonAttempt = LocalWebAbandonAttempt.current
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
                        // Leaving an attempt with real progress costs a life, like losing it.
                        abandonAttempt()
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
    val rulesLabel = stringResource(WebRes.string.web_rules)
    // The title stays truly centred; the way back keeps its words while they fit beside it and
    // turns into an arrow (still named for screen readers) when a long label would run into it.
    SubcomposeLayout(Modifier.fillMaxWidth().height(GAME_HEADER_HEIGHT).padding(horizontal = 8.dp)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val wallet = subcompose("wallet") { WebGameplayWallet() }.map { it.measure(loose) }
        val walletWidth = wallet.maxOfOrNull { it.width } ?: 0
        val backIcon =
            subcompose("backIcon") {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel) }
            }.map { it.measure(loose) }
        // The help button's invisible touch margin may reach into the gap beside the wallet.
        val helpMargin = if (helpFor != null) (HELP_BUTTON_SIZE - HELP_ICON_SIZE).roundToPx() / 2 else 0
        val sideReserve = maxOf(walletWidth, backIcon.maxOfOrNull { it.width } ?: 0) - helpMargin
        val titleRow =
            subcompose("title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (helpFor != null) {
                        IconButton(onClick = { rulesOpen = true }, modifier = Modifier.size(HELP_BUTTON_SIZE)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = rulesLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(HELP_ICON_SIZE),
                            )
                        }
                    }
                }
            }.map { it.measure(loose.copy(maxWidth = (constraints.maxWidth - 2 * sideReserve).coerceAtLeast(0))) }
        val titleWidth = titleRow.maxOfOrNull { it.width } ?: 0
        val leftSpace = (constraints.maxWidth - titleWidth) / 2
        val backText =
            subcompose("backText") {
                TextButton(onClick = onBack) { Text(backLabel, maxLines = 1, softWrap = false) }
            }.map { it.measure(loose) }
        val back = if ((backText.maxOfOrNull { it.width } ?: 0) <= leftSpace) backText else backIcon
        val height = constraints.maxHeight
        layout(constraints.maxWidth, height) {
            back.forEach { it.placeRelative(0, (height - it.height) / 2) }
            titleRow.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, (height - it.height) / 2) }
            wallet.forEach { it.placeRelative(constraints.maxWidth - it.width, (height - it.height) / 2) }
        }
    }
}

internal val GAME_HEADER_HEIGHT = 52.dp
private val HELP_BUTTON_SIZE = 48.dp
private val HELP_ICON_SIZE = 20.dp

/** Lives and gems stay in sight during play, like Android's game bar; tapping opens the Store sheet. */
@Composable
internal fun WebGameplayWallet() {
    val wallet = LocalWebLives.current.state ?: return
    val openStore = LocalWebOpenStore.current
    Row(horizontalArrangement = Arrangement.spacedBy(WALLET_CHIP_GAP), verticalAlignment = Alignment.CenterVertically) {
        WalletChip(
            icon = GameIcon.lives(wallet.lives),
            value = "${wallet.lives}",
            description = stringResource(WebRes.string.web_wallet_lives, wallet.lives, EconomyPolicy.MAXIMUM_LIVES),
            tint = if (wallet.lives > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            onClick = openStore,
        )
        WalletChip(
            icon = GameIcon.GEM,
            value = "${wallet.gems}",
            description = stringResource(WebRes.string.web_wallet_gems, wallet.gems),
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = openStore,
        )
    }
}

@Composable
private fun WalletChip(
    icon: GameIcon,
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
            GameIconImage(icon, size = WALLET_ICON_SIZE)
            // At zero lives the number turns red too, beside the broken heart.
            Text(value, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/** Opens the Store as a sheet over the running game; provided by the Web host. */
internal val LocalWebOpenStore = staticCompositionLocalOf<() -> Unit> { {} }

private val WALLET_CHIP_GAP = 4.dp
private val WALLET_CHIP_PADDING = 10.dp
private val WALLET_CHIP_VERTICAL_PADDING = 6.dp
private val WALLET_ICON_SIZE = 18.dp
