package com.stanisryz.logica.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleRating
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.rating.GameRating

/** Levels are cleared in order, so every level below the current one is solved. */
internal fun WebCatalogProgressSnapshot.clearedLevels(puzzleType: PuzzleType): Map<Difficulty, Int> =
    Difficulty.entries.associateWith { difficulty ->
        currentLevel(WebCatalogProgressBucket(puzzleType, difficulty, CatalogLevelPackVersion.V1)).value - 1
    }

/** One game's rating from the bound Player's durable progress and best 2048 score. */
internal fun gameRating(
    puzzleType: PuzzleType,
    progress: WebCatalogProgressSnapshot,
    best2048: Long,
): GameRating =
    if (PuzzleRating.isScoreRated(puzzleType)) {
        GameRating.BestScore(best2048)
    } else {
        GameRating.Levels(progress.clearedLevels(puzzleType).mapValues { it.value.toLong() })
    }

/** What the difficulty screen needs for its rating sheet: the numbers and, on Yandex, the table. */
internal class WebRatingUi(
    val progress: WebCatalogProgressSnapshot,
    val rating: (PuzzleType) -> GameRating,
    val leaderboard: (@Composable (PuzzleType) -> Unit)?,
)

internal val LocalWebRating =
    staticCompositionLocalOf {
        WebRatingUi(
            WebCatalogProgressSnapshot.EMPTY,
            rating = { GameRating.Levels(emptyMap()) },
            leaderboard = null,
        )
    }
