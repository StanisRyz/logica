package com.stanisryz.logica.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.theme.LogicaSpacing

/**
 * The standard vertically scrolling screen body: one horizontal inset, one section rhythm, and
 * growth downwards so large system font sizes never clip content.
 */
@Composable
internal fun ScreenColumn(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = LogicaSpacing.section,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** A section of a screen: an optional title plus its items at the shared item rhythm. */
@Composable
internal fun ScreenSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        if (title != null || supportingText != null) {
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                title?.let { SectionTitle(it) }
                supportingText?.let { SupportingText(it) }
            }
        }
        content()
    }
}

/** Level 1: the name of the screen. */
@Composable
internal fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Level 2: a group of related content inside a screen. */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Level 3: the name of one puzzle, on a card or at the top of its own screen. */
@Composable
internal fun PuzzleTitle(
    text: String,
    modifier: Modifier = Modifier,
    puzzleType: PuzzleType? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (puzzleType != null) {
            Spacer(
                Modifier
                    .size(ACCENT_DOT_SIZE)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(puzzleType.accentColor()),
            )
            Spacer(Modifier.size(LogicaSpacing.action))
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

/** Level 4: a number the user is meant to read at a glance. */
@Composable
internal fun MetricValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/** Level 5: status and explanatory text that must never compete with the content above it. */
@Composable
internal fun SupportingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = textAlign,
    )
}

/** Ordinary body copy. */
@Composable
internal fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
}

/** The one card shape, elevation, and padding used across the product. */
@Composable
internal fun LogicaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = defaultContentColorFor(containerColor),
    border: BorderStroke? = null,
    verticalSpacing: Dp = LogicaSpacing.cardContent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(LogicaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

/** `contentColorFor` returns `Unspecified` for the palette colors Material 3 has no role for. */
@Composable
private fun defaultContentColorFor(containerColor: Color): Color =
    contentColorFor(containerColor).takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface

private val ACCENT_DOT_SIZE = 10.dp
