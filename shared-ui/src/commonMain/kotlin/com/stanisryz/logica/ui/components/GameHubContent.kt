package com.stanisryz.logica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.game_2048
import com.stanisryz.logica.shared.ui.generated.resources.game_balance
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_play_label
import com.stanisryz.logica.shared.ui.generated.resources.game_catalog_section_title
import com.stanisryz.logica.shared.ui.generated.resources.game_crowns
import com.stanisryz.logica.shared.ui.generated.resources.game_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_title_2048
import com.stanisryz.logica.shared.ui.generated.resources.game_title_balance
import com.stanisryz.logica.shared.ui.generated.resources.game_title_crowns
import com.stanisryz.logica.shared.ui.generated.resources.game_title_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_title_word
import com.stanisryz.logica.shared.ui.generated.resources.game_word
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The canonical five-game catalog order shared by Android and Web hosts. */
val GAME_CATALOG_PUZZLE_TYPES: List<PuzzleType> =
    listOf(
        PuzzleType.BALANCE,
        PuzzleType.CROWNS,
        PuzzleType.WORD,
        PuzzleType.SUDOKU,
        PuzzleType.GAME_2048,
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
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding =
            PaddingValues(
                horizontal = LogicaSpacing.screenHorizontal,
                vertical = LogicaSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        headerContent?.let { content -> item(key = "host-header") { content() } }
        statusContent?.let { content -> item(key = "host-status") { content() } }
        item(key = "games-title") {
            Text(
                text = stringResource(Res.string.game_catalog_section_title),
                modifier = Modifier.padding(top = LogicaSpacing.text),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(puzzleTypes, key = { it }) { puzzleType ->
            GameCatalogCard(
                puzzleType = puzzleType,
                enabled = catalogEnabled,
                onClick = { onGameSelected(puzzleType) },
            )
        }
    }
}

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
    Card(
        modifier = modifier.fillMaxWidth().height(GAME_CATALOG_CARD_HEIGHT),
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
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = stringResource(Res.string.game_catalog_play_label, title),
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(puzzleType.catalogArtworkResource()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to CATALOG_LABEL_SCRIM,
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(0.5f).padding(start = GAME_CATALOG_LABEL_PADDING),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * CATALOG_CARD_TITLE_SCALE,
                    ),
                color = GAME_CATALOG_LABEL_COLOR.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            )
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
        else -> error("$this has no Catalog artwork.")
    }

fun PuzzleType.catalogTitleResource(): StringResource =
    when (this) {
        PuzzleType.BALANCE -> Res.string.game_title_balance
        PuzzleType.CROWNS -> Res.string.game_title_crowns
        PuzzleType.WORD -> Res.string.game_title_word
        PuzzleType.SUDOKU -> Res.string.game_title_sudoku
        PuzzleType.GAME_2048 -> Res.string.game_title_2048
        else -> error("$this has no Catalog title.")
    }

private val GAME_CATALOG_CARD_HEIGHT = 124.dp
private val GAME_CATALOG_LABEL_PADDING = 24.dp
private val GAME_CATALOG_LABEL_COLOR = Color(0xFF1B2A35)
private val CATALOG_LABEL_SCRIM = Color(0xFFF4F8FB).copy(alpha = 0.15f)
private const val CATALOG_CARD_TITLE_SCALE = 1.40625f
private const val DISABLED_ALPHA = 0.38f
