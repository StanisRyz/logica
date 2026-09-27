package com.stanisryz.logica.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** Constraint-driven scene for the square-board games. The puzzle stays the largest element. */
@Composable
internal fun SquareGameLayout(
    modifier: Modifier = Modifier,
    metadataContent: @Composable ColumnScope.(wideLayout: Boolean) -> Unit,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    boardContent: @Composable BoxScope.() -> Unit,
    toolContent: @Composable ColumnScope.() -> Unit,
    showContextStatus: Boolean = false,
    contextStatusContent: @Composable BoxScope.(compact: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val wideLayout = isWideGameplayLayout(maxWidth, maxHeight)
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else NORMAL_VERTICAL_PADDING
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else LogicaSpacing.item
        val contextMaxHeight = if (compact) COMPACT_CONTEXT_MAX_HEIGHT else NORMAL_CONTEXT_MAX_HEIGHT
        val panelWidth = minOf(WIDE_PANEL_MAX_WIDTH, maxWidth * WIDE_PANEL_WIDTH_FRACTION)

        if (wideLayout) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = LogicaSpacing.screenHorizontal, vertical = verticalPadding)
                        .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(sectionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                    content = boardContent,
                )
                Column(
                    modifier = Modifier.width(panelWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    metadataContent(true)
                    hostStatusContent()
                    Spacer(Modifier.weight(1f))
                    toolContent()
                    ContextStatusRegion(showContextStatus, compact, contextMaxHeight, contextStatusContent)
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = LogicaSpacing.screenHorizontal,
                            vertical = verticalPadding,
                        ).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                metadataContent(false)
                hostStatusContent()
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                    content = boardContent,
                )
                toolContent()
                ContextStatusRegion(showContextStatus, compact, contextMaxHeight, contextStatusContent)
            }
        }
    }
}

@Composable
private fun ContextStatusRegion(
    visible: Boolean,
    compact: Boolean,
    maxHeight: Dp,
    content: @Composable BoxScope.(compact: Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(CONTEXT_REVEAL_MILLIS)) + expandVertically(tween(CONTEXT_REVEAL_MILLIS)),
        exit = fadeOut(tween(CONTEXT_REVEAL_MILLIS)) + shrinkVertically(tween(CONTEXT_REVEAL_MILLIS)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            contentAlignment = Alignment.TopCenter,
            content = { content(compact) },
        )
    }
}

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 6.dp
private val NORMAL_VERTICAL_PADDING = 10.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val COMPACT_CONTEXT_MAX_HEIGHT = 76.dp
private val NORMAL_CONTEXT_MAX_HEIGHT = 96.dp
private val WIDE_PANEL_MAX_WIDTH = 232.dp
private const val WIDE_PANEL_WIDTH_FRACTION = 0.42f
private const val CONTEXT_REVEAL_MILLIS = 180
