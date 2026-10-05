package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The Web side of the one gem rule: an Expert level pays one gem when its best first reaches three
 * stars, read from the Player's stars snapshot before the solve records anything, and the result
 * card shows exactly what the wallet received.
 */
class WebExpertGemTest {
    private val progress = WebCatalogProgressRepository(WebCatalogProgressScope.STANDALONE, MemoryProgressStore()).also { it.loadLocal() }
    private val wallet =
        WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, MemoryEconomyStore()).also { it.loadLocal() }
    private val completion = WebCatalogCompletionController(RepositoryProgress(progress))

    @Test
    fun anExpertLevelPaysOnlyWhenItsBestFirstReachesThreeStars() {
        assertEquals(0, solve(level = 1, stars = 2))
        // A replay that raises the level to three stars pays the gem.
        assertEquals(1, solve(level = 1, stars = 3))
        // Already three: no second gem, and a lower replay pays nothing either.
        assertEquals(0, solve(level = 1, stars = 3))
        assertEquals(0, solve(level = 1, stars = 2))
        // A first solve with three stars pays at once.
        assertEquals(1, solve(level = 2, stars = 3))

        assertEquals(EconomyPolicy.STARTING_GEMS + 2, wallet.state.value.gems)
    }

    @Test
    fun aMediumLevelOrTheDailyNeverPays() {
        assertEquals(0, solve(level = 1, stars = 3, difficulty = Difficulty.MEDIUM))
        assertEquals(0, WebEconomyProcessor.dailyGemsFor(PuzzleType.SUDOKU, Difficulty.MEDIUM, stars = 3))
    }

    /** One solved attempt the way a controller runs it; returns what the result card shows. */
    private fun solve(
        level: Int,
        stars: Int,
        difficulty: Difficulty = Difficulty.EXPERT,
    ): Int {
        val attempt =
            WebCatalogAttempt(
                CatalogLevelId(PuzzleType.SUDOKU, difficulty, CatalogLevelNumber(level), CatalogLevelPackVersion.V1),
                WebPlayerContextToken(1L),
                replay =
                    level <
                        progress
                            .currentLevel(
                                WebCatalogProgressBucket(PuzzleType.SUDOKU, difficulty, CatalogLevelPackVersion.V1),
                            ).value,
            )
        completion.startAttempt(attempt)
        completion.saveSolved(attempt, stars)
        val before = wallet.state.value.gems
        wallet.applyTerminalResult(solved = true, gemsEarned = completion.gemsEarned)
        val shown = assertIs<WebCatalogCompletionState.Saved>(completion.state).gemsEarned
        assertEquals(wallet.state.value.gems - before, shown)
        return shown
    }

    /** The coordinator's two calls over a real repository, without a Player session around it. */
    private class RepositoryProgress(
        private val repository: WebCatalogProgressRepository,
    ) : WebCatalogProgressAccess {
        override val isReady: Boolean = true

        override suspend fun resolveCurrentLevel(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
            packVersion: CatalogLevelPackVersion,
        ): WebCatalogLevelResolution = error("Unused")

        override fun isCurrent(attempt: WebCatalogAttempt): Boolean = true

        override fun previousBestStars(attempt: WebCatalogAttempt): Int? = repository.previousBestStars(attempt.levelId)

        override fun advanceSolved(
            attempt: WebCatalogAttempt,
            stars: Int?,
        ): WebCatalogCompletionResult {
            repository.advanceSolved(attempt.levelId)
            stars?.let { repository.recordStars(attempt.levelId, it) }
            val bucket = WebCatalogProgressBucket(attempt.levelId.puzzleType, attempt.levelId.difficulty, attempt.levelId.packVersion)
            return WebCatalogCompletionResult.Saved(attempt.levelId.copy(levelNumber = repository.currentLevel(bucket)))
        }

        override fun retryContextBinding() = Unit
    }

    private class MemoryProgressStore : WebCatalogProgressStore {
        private var snapshot = WebCatalogProgressSnapshot.EMPTY

        override fun load(): WebCatalogProgressSnapshot = snapshot

        override fun save(snapshot: WebCatalogProgressSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class MemoryEconomyStore : WebEconomyStore {
        private var snapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }
}
