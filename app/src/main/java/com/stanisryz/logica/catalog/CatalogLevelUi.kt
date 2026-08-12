package com.stanisryz.logica.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
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

/** Opens one public Catalog level of one game. The frozen pack decides what that level contains. */
internal fun CatalogLevelRepository.levelLaunch(
    puzzleType: PuzzleType,
    difficulty: Difficulty,
    levelNumber: Int,
): GameAttemptLaunch.Level =
    GameAttemptLaunch.Level(
        CatalogLevelId(
            puzzleType = puzzleType,
            difficulty = difficulty,
            levelNumber = CatalogLevelNumber(levelNumber),
            packVersion = packVersion,
        ),
    )

/**
 * The level after this one. Progression has already advanced by the time this is used, so the next
 * displayed level is simply this one plus one — level 10 001 after 10 000, reusing content slot 1.
 */
internal fun GameAttemptLaunch.nextLevelLaunch(): GameAttemptLaunch.Level? =
    (this as? GameAttemptLaunch.Level)?.let { level ->
        GameAttemptLaunch.Level(level.levelId.copy(levelNumber = level.levelId.levelNumber.next))
    }
