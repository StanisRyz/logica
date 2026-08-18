package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.ui.theme.LogicaSpacing

/** Small one-screen scaffold for the shared square-board games. */
@Composable
internal fun SquareGameLayout(
    modifier: Modifier = Modifier,
    metadataContent: @Composable ColumnScope.() -> Unit,
    hostStatusContent: @Composable ColumnScope.() -> Unit = {},
    boardContent: @Composable BoxScope.() -> Unit,
    toolContent: @Composable ColumnScope.() -> Unit,
    contextStatusContent: @Composable BoxScope.(compact: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
        val verticalPadding = if (compact) COMPACT_VERTICAL_PADDING else LogicaSpacing.screenVertical
        val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else LogicaSpacing.item
        val contextHeight = if (compact) COMPACT_CONTEXT_HEIGHT else NORMAL_CONTEXT_HEIGHT

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = verticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            metadataContent()
            hostStatusContent()
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
                content = boardContent,
            )
            toolContent()
            Box(
                modifier = Modifier.fillMaxWidth().height(contextHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                contextStatusContent(compact)
            }
        }
    }
}

private val COMPACT_HEIGHT_THRESHOLD = 700.dp
private val COMPACT_VERTICAL_PADDING = 8.dp
private val COMPACT_SECTION_SPACING = 6.dp
private val COMPACT_CONTEXT_HEIGHT = 76.dp
private val NORMAL_CONTEXT_HEIGHT = 112.dp
