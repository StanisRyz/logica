package com.stanisryz.logica.ui.sudoku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.hint
import com.stanisryz.logica.shared.ui.generated.resources.hints_left
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_digits_left
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_erase
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_pencil_off_short
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_pencil_on_short
import com.stanisryz.logica.shared.ui.generated.resources.sudoku_undo
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_erase
import com.stanisryz.logica.shared.ui.generated.resources.tool_caption_undo
import com.stanisryz.logica.shared.ui.generated.resources.tool_off
import com.stanisryz.logica.shared.ui.generated.resources.tool_on
import com.stanisryz.logica.shared.ui.generated.resources.tool_pencil
import com.stanisryz.logica.ui.components.LocalRoomyGameplayControls
import com.stanisryz.logica.ui.components.PuzzleTool
import com.stanisryz.logica.ui.components.PuzzleToolBar
import org.jetbrains.compose.resources.stringResource

/**
 * The digit row under the board: nine large plain digits in one line (or a 3x3 block beside a
 * landscape board). A digit whose nine places are all confirmed disappears, as there is nothing
 * left to place; its slot stays so the other digits never shift under the finger.
 */
@Composable
fun SudokuNumberPad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    remaining: ((Int) -> Int)? = null,
    isPencilMode: Boolean = false,
    columns: Int = DIGIT_COUNT,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        (1..DIGIT_COUNT).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { digit ->
                    val left = remaining?.invoke(digit)
                    SudokuDigitKey(
                        digit = digit,
                        left = left,
                        visible = left != 0,
                        enabled = enabled && left != 0,
                        isPencilMode = isPencilMode,
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SudokuDigitKey(
    digit: Int,
    left: Int?,
    visible: Boolean,
    enabled: Boolean,
    isPencilMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val leftDescription = left?.let { stringResource(Res.string.sudoku_digits_left, it) }
    val roomy = LocalRoomyGameplayControls.current
    BoxWithConstraints(
        modifier =
            modifier
                .height(if (roomy) ROOMY_DIGIT_KEY_HEIGHT else SUDOKU_DIGIT_KEY_HEIGHT)
                .clip(MaterialTheme.shapes.small)
                .alpha(if (visible) 1f else 0f)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = digit.toString()
                    leftDescription?.let { stateDescription = it }
                },
        contentAlignment = Alignment.Center,
    ) {
        val fontSize =
            with(LocalDensity.current) {
                (maxWidth * DIGIT_TEXT_RATIO).coerceIn(DIGIT_MIN_TEXT, if (roomy) ROOMY_DIGIT_MAX_TEXT else DIGIT_MAX_TEXT).toSp()
            }
        Text(
            text = digit.toString(),
            fontSize = fontSize,
            lineHeight = fontSize,
            // Pencil digits look lighter, so the mode is visible on the keys themselves.
            color =
                when {
                    !enabled -> colors.onSurfaceVariant.copy(alpha = DISABLED_DIGIT_ALPHA)
                    isPencilMode -> colors.primary.copy(alpha = PENCIL_DIGIT_ALPHA)
                    else -> colors.primary
                },
            fontWeight = if (isPencilMode) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

/**
 * Flat Undo / Erase / Pencil / Hint actions: an icon over a short label, no button
 * containers. Pencil shows its state next to the icon, and Hint carries the remaining stock.
 */
@Composable
fun SudokuToolBar(
    isPencilMode: Boolean,
    onToggle: () -> Unit,
    onErase: () -> Unit,
    eraseEnabled: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    hintEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    wrapTools: Boolean = false,
    hintCount: Int? = null,
) {
    val pencilState = stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off)
    val tools: List<@Composable (Modifier) -> Unit> =
        listOf(
            { m ->
                FlatTool(
                    stringResource(Res.string.sudoku_undo),
                    stringResource(Res.string.tool_caption_undo),
                    enabled && canUndo,
                    onUndo,
                    m,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                }
            },
            { m ->
                FlatTool(
                    stringResource(Res.string.sudoku_erase),
                    stringResource(Res.string.tool_caption_erase),
                    enabled && eraseEnabled,
                    onErase,
                    m,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
                }
            },
            { m ->
                FlatTool(
                    label = stringResource(Res.string.tool_pencil),
                    caption = stringResource(Res.string.tool_pencil),
                    enabled = enabled,
                    onClick = onToggle,
                    modifier = m,
                    selected = isPencilMode,
                    stateDescription = pencilState,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Text(
                            text =
                                stringResource(
                                    if (isPencilMode) Res.string.sudoku_pencil_on_short else Res.string.sudoku_pencil_off_short,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            { m ->
                FlatTool(
                    label = stringResource(Res.string.hint),
                    caption = stringResource(Res.string.hint),
                    enabled = enabled && hintEnabled,
                    onClick = onHint,
                    modifier = m,
                    stateDescription = hintCount?.let { stringResource(Res.string.hints_left, it) },
                ) {
                    BadgedBox(
                        badge = {
                            hintCount?.let { count ->
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text(if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString())
                                }
                            }
                        },
                    ) { Icon(Icons.Rounded.Lightbulb, contentDescription = null) }
                }
            },
        )
    if (wrapTools) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(COMPACT_TOOL_ROW_SPACING)) {
            tools.chunked(WRAPPED_TOOLS_PER_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { tool -> tool(Modifier.weight(1f)) }
                    repeat(WRAPPED_TOOLS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Row(modifier = modifier.fillMaxWidth()) {
            tools.forEach { tool -> tool(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun FlatTool(
    label: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    stateDescription: String? = null,
    symbol: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val roomy = LocalRoomyGameplayControls.current
    val tint =
        when {
            !enabled -> colors.onSurfaceVariant.copy(alpha = DISABLED_DIGIT_ALPHA)
            selected -> colors.primary
            else -> colors.onSurfaceVariant
        }
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription?.let { this.stateDescription = it }
                }.padding(vertical = if (roomy) ROOMY_FLAT_TOOL_VERTICAL_PADDING else FLAT_TOOL_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            Box(Modifier.height(FLAT_TOOL_ICON_HEIGHT), contentAlignment = Alignment.Center) { symbol() }
            Text(
                text = caption,
                modifier = Modifier.clearAndSetSemantics {},
                style = if (roomy) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** Tutorial mode exposes the same production Pencil control and semantics. */
@Composable
fun SudokuPencilToggle(
    isPencilMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PuzzleToolBar(
        tools = listOf(pencilTool(isPencilMode, onToggle)),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun pencilTool(
    isPencilMode: Boolean,
    onToggle: () -> Unit,
): PuzzleTool =
    PuzzleTool(
        label = stringResource(Res.string.tool_pencil),
        stateDescription = stringResource(if (isPencilMode) Res.string.tool_on else Res.string.tool_off),
        selected = isPencilMode,
        onClick = onToggle,
        symbol = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        caption = stringResource(Res.string.tool_pencil),
    )

private const val DIGIT_COUNT = 9
private const val WRAPPED_TOOLS_PER_ROW = 2
private const val MAX_BADGE_COUNT = 99
private const val DIGIT_TEXT_RATIO = 0.8f
private const val PENCIL_DIGIT_ALPHA = 0.6f
private const val DISABLED_DIGIT_ALPHA = 0.38f
private val DIGIT_MIN_TEXT = 20.dp
private val DIGIT_MAX_TEXT = 40.dp
internal val SUDOKU_DIGIT_KEY_HEIGHT = 56.dp
private val COMPACT_TOOL_ROW_SPACING = 2.dp
private val FLAT_TOOL_VERTICAL_PADDING = 4.dp
private val FLAT_TOOL_ICON_HEIGHT = 28.dp
private val ROOMY_DIGIT_KEY_HEIGHT = 68.dp
private val ROOMY_DIGIT_MAX_TEXT = 46.dp
private val ROOMY_FLAT_TOOL_VERTICAL_PADDING = 10.dp
