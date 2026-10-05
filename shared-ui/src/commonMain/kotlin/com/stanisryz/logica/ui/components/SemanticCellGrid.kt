package com.stanisryz.logica.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * An invisible [size]×[size] grid laid over a board that draws itself on a canvas, so a screen
 * reader still finds each cell: its description and, where [cellAction] gives one, a click action. It
 * holds semantics only — no pointer input — so touches pass through to the board underneath.
 */
@Composable
internal fun SemanticCellGrid(
    size: Int,
    modifier: Modifier,
    cellDescription: @Composable (row: Int, column: Int) -> String,
    actionLabel: String? = null,
    cellAction: (row: Int, column: Int) -> (() -> Unit)? = { _, _ -> null },
) {
    Column(modifier) {
        repeat(size) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(size) { column ->
                    val description = cellDescription(row, column)
                    val action = cellAction(row, column)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().semantics {
                            contentDescription = description
                            if (action != null) {
                                role = Role.Button
                                onClick(label = actionLabel) {
                                    action()
                                    true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
