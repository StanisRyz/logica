package com.stanisryz.logica.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.continue_level
import com.stanisryz.logica.shared.ui.generated.resources.continue_title
import com.stanisryz.logica.shared.ui.generated.resources.game_2048
import com.stanisryz.logica.shared.ui.generated.resources.game_balance
import com.stanisryz.logica.shared.ui.generated.resources.game_block_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_action
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_play_label
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_section_title
import com.stanisryz.logica.shared.ui.generated.resources.game_crowns
import com.stanisryz.logica.shared.ui.generated.resources.game_nonogram
import com.stanisryz.logica.shared.ui.generated.resources.game_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_title_2048
import com.stanisryz.logica.shared.ui.generated.resources.game_title_balance
import com.stanisryz.logica.shared.ui.generated.resources.game_title_block_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_title_crowns
import com.stanisryz.logica.shared.ui.generated.resources.game_title_nonogram
import com.stanisryz.logica.shared.ui.generated.resources.game_title_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_title_word
import com.stanisryz.logica.shared.ui.generated.resources.game_word
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/** The canonical six-game catalog order shared by Android and Web hosts. */
val GAME_CATALOG_PUZZLE_TYPES: List<PuzzleType> =
    listOf(
        PuzzleType.BALANCE,
        PuzzleType.CROWNS,
        PuzzleType.WORD,
        PuzzleType.SUDOKU,
        PuzzleType.GAME_2048,
        PuzzleType.NONOGRAM,
        PuzzleType.BLOCK_SUDOKU,
    )

/** One scrollable game catalog with optional host-owned content before the cards. */
@Composable
fun GameHubContent(
    puzzleTypes: List<PuzzleType>,
    catalogEnabled: Boolean,
    onGameSelected: (PuzzleType) -> Unit,
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    statusContent: (@Composable () -> Unit)? = null,
    continueContent: (@Composable () -> Unit)? = null,
    rewardsContent: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Wide windows (a desktop, a tablet) lay the game cards out in a grid instead of one tall list.
        val columns =
            when {
                maxWidth >= THREE_COLUMN_WIDTH -> 3
                maxWidth >= TWO_COLUMN_WIDTH -> 2
                else -> 1
            }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding =
                PaddingValues(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            continueContent?.let { content -> item(key = "host-continue") { content() } }
            headerContent?.let { content -> item(key = "host-header") { content() } }
            statusContent?.let { content -> item(key = "host-status") { content() } }
            rewardsContent?.let { content -> item(key = "host-rewards") { content() } }
            item(key = "games-title") {
                Text(
                    text = stringResource(Res.string.game_catalog_section_title),
                    modifier = Modifier.padding(top = LogicaSpacing.text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(puzzleTypes.chunked(columns), key = { row -> row.first() }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
                    row.forEach { puzzleType ->
                        GameCatalogCard(
                            puzzleType = puzzleType,
                            enabled = catalogEnabled,
                            onClick = { onGameSelected(puzzleType) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val TWO_COLUMN_WIDTH = 640.dp
private val THREE_COLUMN_WIDTH = 1000.dp

/**
 * One tap back into the last Catalog game the player started: its artwork, the game, and the
 * difficulty with the level the player is on, opening that level directly.
 */
@Composable
fun ContinueGameCard(
    puzzleType: PuzzleType,
    difficultyLabel: String,
    levelNumber: Int?,
    enabled: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(puzzleType.catalogTitleResource())
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow, contentColor = colors.onSurface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onContinue)
                    .padding(CONTINUE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
        ) {
            Image(
                bitmap = imageResource(puzzleType.catalogArtworkResource()),
                contentDescription = null,
                filterQuality = ArtworkFilterQuality,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(CONTINUE_ARTWORK_SIZE).clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.continue_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        levelNumber?.let { stringResource(Res.string.continue_level, difficultyLabel, it) }
                            ?: difficultyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(CONTINUE_PLAY_SIZE)
                        .clip(CircleShape)
                        .background(if (enabled) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (enabled) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

private val CONTINUE_PADDING = 12.dp
private val CONTINUE_ARTWORK_SIZE = 56.dp
private val CONTINUE_PLAY_SIZE = 44.dp

/** A full-width Catalog artwork card shared by Android and Web. */
@Composable
fun GameCatalogCard(
    puzzleType: PuzzleType,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(puzzleType.catalogTitleResource())
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardScale by
        animateFloatAsState(
            targetValue = if (pressed && enabled) CATALOG_CARD_PRESSED_SCALE else 1f,
            animationSpec =
                spring(
                    dampingRatio = CATALOG_CARD_SPRING_DAMPING,
                    stiffness = CATALOG_CARD_SPRING_STIFFNESS,
                ),
            label = "catalog-card-scale",
        )
    Card(
        modifier = modifier.fillMaxWidth().height(GAME_CATALOG_CARD_HEIGHT).scale(cardScale),
        colors =
            CardDefaults.cardColors(
                containerColor = if (enabled) colors.surfaceContainerLow else colors.surfaceContainerHighest,
                contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = stringResource(Res.string.game_catalog_play_label, title),
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                bitmap = imageResource(puzzleType.catalogArtworkResource()),
                contentDescription = null,
                filterQuality = ArtworkFilterQuality,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.fillMaxWidth(0.68f).padding(start = GAME_CATALOG_LABEL_PADDING),
                verticalArrangement = Arrangement.spacedBy(CATALOG_ACTION_GAP),
                horizontalAlignment = Alignment.Start,
            ) {
                // The artwork is always light, so the label keeps fixed ink colours in both themes
                // and a soft glow instead of a scrim over the picture.
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            shadow = Shadow(color = CATALOG_TITLE_GLOW, blurRadius = CATALOG_TITLE_GLOW_RADIUS),
                        ),
                    color = CATALOG_TITLE_INK.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                )
                Surface(
                    color = Color.White.copy(alpha = if (enabled) CATALOG_ACTION_ALPHA else DISABLED_ACTION_ALPHA),
                    contentColor = if (enabled) CATALOG_ACTION_INK else CATALOG_TITLE_INK.copy(alpha = DISABLED_ALPHA),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = CATALOG_ACTION_HORIZONTAL_PADDING,
                                vertical = CATALOG_ACTION_VERTICAL_PADDING,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CATALOG_ACTION_ICON_GAP),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(CATALOG_ACTION_ICON_SIZE),
                        )
                        Text(
                            text = stringResource(Res.string.game_catalog_action),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

fun PuzzleType.catalogArtworkResource(): DrawableResource =
    when (this) {
        PuzzleType.BALANCE -> Res.drawable.game_balance
        PuzzleType.CROWNS -> Res.drawable.game_crowns
        PuzzleType.WORD -> Res.drawable.game_word
        PuzzleType.SUDOKU -> Res.drawable.game_sudoku
        PuzzleType.GAME_2048 -> Res.drawable.game_2048
        PuzzleType.NONOGRAM -> Res.drawable.game_nonogram
        PuzzleType.BLOCK_SUDOKU -> Res.drawable.game_block_sudoku
        else -> error("$this has no Catalog artwork.")
    }

fun PuzzleType.catalogTitleResource(): StringResource =
    when (this) {
        PuzzleType.BALANCE -> Res.string.game_title_balance
        PuzzleType.CROWNS -> Res.string.game_title_crowns
        PuzzleType.WORD -> Res.string.game_title_word
        PuzzleType.SUDOKU -> Res.string.game_title_sudoku
        PuzzleType.GAME_2048 -> Res.string.game_title_2048
        PuzzleType.NONOGRAM -> Res.string.game_title_nonogram
        PuzzleType.BLOCK_SUDOKU -> Res.string.game_title_block_sudoku
        else -> error("$this has no Catalog title.")
    }

private val GAME_CATALOG_CARD_HEIGHT = 148.dp
private val GAME_CATALOG_LABEL_PADDING = 24.dp
private val CATALOG_ACTION_HORIZONTAL_PADDING = 12.dp
private val CATALOG_ACTION_VERTICAL_PADDING = 6.dp
private val CATALOG_ACTION_ICON_GAP = 2.dp
private val CATALOG_ACTION_ICON_SIZE = 18.dp
private val CATALOG_ACTION_GAP = 8.dp
private const val CATALOG_CARD_PRESSED_SCALE = 0.985f
private const val CATALOG_CARD_SPRING_DAMPING = 0.72f
private const val CATALOG_CARD_SPRING_STIFFNESS = 700f
private val CATALOG_TITLE_INK = Color(0xFF231F1A)
private val CATALOG_ACTION_INK = Color(0xFF2F5D4E)
private val CATALOG_TITLE_GLOW = Color(0xCCFFFBF4)
private const val CATALOG_TITLE_GLOW_RADIUS = 18f
private const val CATALOG_ACTION_ALPHA = 0.86f
private const val DISABLED_ACTION_ALPHA = 0.58f
private const val DISABLED_ALPHA = 0.38f
