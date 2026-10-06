package com.stanisryz.logica.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stanisryz.logica.AppLanguage
import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.game2048.Game2048BestScore
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGeneratorV2
import com.stanisryz.logica.puzzle.core.nonogram.NonogramGenerators
import com.stanisryz.logica.runCatchingCancellable
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.ui.components.LevelMapSheet
import com.stanisryz.logica.ui.nonogram.DailyGalleryPicture
import com.stanisryz.logica.ui.nonogram.NonogramGallerySheet
import com.stanisryz.logica.ui.rating.GameRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Best stars per difficulty for one game's start screen, read from durable results. */
@Composable
internal fun difficultyStars(
    statisticsRepository: StatisticsRepository,
    puzzleType: PuzzleType,
): Map<Difficulty, Long> {
    val flow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val snapshot by flow.collectAsStateWithLifecycle(initialValue = null)
    return remember(snapshot, puzzleType) {
        snapshot
            ?.statistics
            ?.levelStars
            ?.filter { it.puzzleType == puzzleType }
            ?.groupBy { it.difficulty }
            ?.mapValues { (_, levels) -> levels.sumOf { it.stars.toLong() } }
            .orEmpty()
    }
}

/** A level game's rating: Catalog levels cleared per difficulty, straight from progression. */
@Composable
internal fun levelRating(
    catalogLevelRepository: CatalogLevelRepository,
    puzzleType: PuzzleType,
): GameRating {
    val flow = remember(catalogLevelRepository, puzzleType) { catalogLevelRepository.observeCurrentLevels(puzzleType) }
    val levels by flow.collectAsStateWithLifecycle(initialValue = emptyMap())
    return GameRating.Levels(levels.clearedLevels().mapValues { it.value.toLong() })
}

@Composable
internal fun bestScoreRating(bestScore: Game2048BestScore): GameRating {
    val best by bestScore.best.collectAsStateWithLifecycle(initialValue = 0L)
    return GameRating.BestScore(best)
}

/** Levels are cleared in order, so everything below the current level is solved. */
private fun Map<Difficulty, CatalogLevelNumber>.clearedLevels(): Map<Difficulty, Int> = mapValues { (_, level) -> level.value - 1 }

/**
 * The Nonogram gallery: every cleared level's picture, rebuilt from its frozen level on demand — the
 * levels below the V1 row from Level Pack V1, the later ones from V2 (pictures and symmetric boards).
 */
@Composable
internal fun NonogramGallery(
    catalogLevelRepository: CatalogLevelRepository,
    statisticsRepository: StatisticsRepository,
    onDismiss: () -> Unit,
    onReplay: (Difficulty, Int) -> Unit,
) {
    val levelsFlow = remember(catalogLevelRepository) { catalogLevelRepository.observeCurrentLevels(PuzzleType.NONOGRAM) }
    val levels by levelsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val statisticsFlow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val statistics by statisticsFlow.collectAsStateWithLifecycle(initialValue = null)
    val stars =
        remember(statistics) {
            statistics
                ?.statistics
                ?.levelStars
                ?.filter { it.puzzleType == PuzzleType.NONOGRAM }
                ?.associate { (it.difficulty to it.level) to it.stars }
                .orEmpty()
        }
    val dailyFlow = remember(statisticsRepository) { statisticsRepository.observeSolvedDailyPictures() }
    val dailySolved by dailyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyPictures =
        remember(dailySolved) {
            val pictures = NonogramGeneratorV2()
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(AppLanguage.locale)
            dailySolved.mapNotNull { (date, seed) ->
                runCatching {
                    DailyGalleryPicture(formatter.format(date), pictures.generate(PuzzleSeed(seed), Difficulty.MEDIUM))
                }.getOrNull()
            }
        }
    NonogramGallerySheet(
        clearedLevels = levels.clearedLevels(),
        loadPicture = { difficulty, level ->
            withContext(Dispatchers.Default) {
                runCatchingCancellable {
                    val levelId = catalogLevelRepository.levelId(PuzzleType.NONOGRAM, difficulty, CatalogLevelNumber(level))
                    val definition = catalogLevelRepository.resolve(levelId)
                    NonogramGenerators.generate(definition.seed, difficulty, definition.generatorVersion)
                }.getOrNull()
            }
        },
        starsOf = { difficulty, level -> stars[difficulty to level] ?: 0 },
        onDismiss = onDismiss,
        onReplay = onReplay,
        dailyPictures = dailyPictures,
    )
}

/** One game's level map over its progression and the best stars of each cleared level. */
@Composable
internal fun LevelMap(
    catalogLevelRepository: CatalogLevelRepository,
    statisticsRepository: StatisticsRepository,
    puzzleType: PuzzleType,
    onDismiss: () -> Unit,
    onPlay: (PuzzleType, Difficulty) -> Unit,
    onReplay: (PuzzleType, Difficulty, Int) -> Unit,
) {
    val levelsFlow = remember(catalogLevelRepository, puzzleType) { catalogLevelRepository.observeCurrentLevels(puzzleType) }
    val levels by levelsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val statisticsFlow = remember(statisticsRepository) { statisticsRepository.observe(LocalDate.now()) }
    val statistics by statisticsFlow.collectAsStateWithLifecycle(initialValue = null)
    val stars =
        remember(statistics, puzzleType) {
            statistics
                ?.statistics
                ?.levelStars
                ?.filter { it.puzzleType == puzzleType }
                ?.associate { (it.difficulty to it.level) to it.stars }
                .orEmpty()
        }
    LevelMapSheet(
        currentLevels = levels.mapValues { it.value.value },
        starsOf = { difficulty, level -> stars[difficulty to level] ?: 0 },
        onPlayCurrent = { difficulty ->
            onDismiss()
            onPlay(puzzleType, difficulty)
        },
        onReplay = { difficulty, level ->
            onDismiss()
            onReplay(puzzleType, difficulty, level)
        },
        onDismiss = onDismiss,
    )
}
