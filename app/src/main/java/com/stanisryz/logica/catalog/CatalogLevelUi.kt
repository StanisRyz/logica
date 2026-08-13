package com.stanisryz.logica.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * The current level of every difficulty of one game, as the start screen shows it. Progression is
 * persisted per game, per difficulty, and per level-pack version, so each row moves on its own.
 */
@Composable
internal fun rememberCatalogLevels(
    repository: CatalogLevelRepository,
    puzzleType: PuzzleType,
): Map<Difficulty, Int> {
    val levelFlow = remember(repository, puzzleType) { repository.observeCurrentLevels(puzzleType) }
    val levels by levelFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    return levels.mapValues { (_, level) -> level.value }
}
