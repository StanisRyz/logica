package com.stanisryz.logica.game2048

import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.game2048.Game2048Status
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.ui.screens.Game2048BackBehavior
import com.stanisryz.logica.ui.screens.game2048BackBehavior
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The 2048 level contract: crossing the score target clears the Catalog level immediately — result,
 * gems, and progression, exactly once — while the board keeps running as freeplay, and running out
 * of moves afterwards is not a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Game2048LevelClearTest {
    private val levelId =
        CatalogLevelId(PuzzleType.GAME_2048, Difficulty.EASY, CatalogLevelNumber(3), CatalogLevelPackVersion.V1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun delayedTargetSaveSurvivesFreeplayAndTheLaterGameOverNeverFails() =
        runBlocking {
            val seed = seedThatClearsThenDies()
            val saveGate = CompletableDeferred<Unit>()
            val completions = RecordingCompletions(saveGate)
            val viewModel =
                Game2048ViewModel(
                    launch = GameAttemptLaunch.Level(levelId),
                    attemptFactory = GameAttemptFactory(FrozenLevel(seed)) { ATTEMPT_ID },
                    completionRepository = completions,
                    economyRepository = FullWallet,
                )
            var ready = viewModel.uiState.first { it !is Game2048UiState.Loading } as Game2048UiState.Ready
            assertFalse(ready.levelCleared)
            assertTrue(completions.recorded.isEmpty())
            assertEquals(Game2048BackBehavior.NORMAL, game2048BackBehavior(GameAttemptLaunch.Level(levelId), ready))

            // Play up to and past the clear: the level completes the moment the target is crossed.
            while (!ready.levelCleared) {
                assertEquals(Game2048Status.IN_PROGRESS, ready.game.status)
                viewModel.play(nextDirection(ready.game))
                ready = viewModel.ready()
            }

            val clear = completions.recorded.single()
            assertEquals(GameOutcome.SOLVED, clear.outcome)
            assertEquals(levelId, clear.catalogLevel)
            // Cleared, and still running while the durable transaction is deliberately delayed.
            assertEquals(Game2048Status.IN_PROGRESS, ready.game.status)
            assertEquals(CompletionPersistence.Saving, ready.completionPersistence)
            assertTrue(ready.hasMeaningfulProgress)
            assertEquals(
                Game2048BackBehavior.BLOCKED_SAVING,
                game2048BackBehavior(GameAttemptLaunch.Level(levelId), ready),
            )
            assertEquals(
                Game2048BackBehavior.NORMAL,
                game2048BackBehavior(
                    GameAttemptLaunch.Level(levelId),
                    ready.copy(completionPersistence = CompletionPersistence.Error),
                ),
            )

            val gameAtClear = ready.game
            viewModel.play(nextDirection(ready.game))
            ready = viewModel.ready()
            assertTrue(gameAtClear != ready.game)
            assertEquals(CompletionPersistence.Saving, ready.completionPersistence)

            saveGate.complete(Unit)
            ready =
                viewModel.uiState.first { state ->
                    state is Game2048UiState.Ready && state.completionPersistence == CompletionPersistence.Saved
                } as Game2048UiState.Ready
            assertFalse(ready.hasMeaningfulProgress)
            assertEquals(
                Game2048BackBehavior.DEFERRED_TERMINAL_ACTION,
                game2048BackBehavior(GameAttemptLaunch.Level(levelId), ready),
            )

            val scoreAtClear = ready.game.score
            while (!ready.game.status.isTerminal) {
                viewModel.play(nextDirection(ready.game))
                ready = viewModel.ready()
            }

            // Playing on paid nothing extra, and the eventual game over is not a failure.
            assertEquals(1, completions.recorded.size)
            assertEquals(1, completions.calls)
            assertTrue(ready.game.score >= scoreAtClear)
            assertTrue(ready.levelCleared)
            assertTrue(completions.recorded.none { it.outcome == GameOutcome.FAILED })
        }

    /**
     * A seed whose deterministic corner-strategy playthrough crosses the EASY target and then runs
     * out of moves, so the test exercises both halves of the contract without depending on the
     * shipped asset content.
     */
    private fun seedThatClearsThenDies(): PuzzleSeed {
        val target = requireNotNull(Game2048RulesetTarget)
        var best = 0L
        (1L..500L).forEach { candidate ->
            val engine = Game2048Engine(Game2048PuzzleId(PuzzleSeed(candidate), Difficulty.EASY, Game2048GeneratorVersion.V2))
            var state = engine.start()
            while (!state.status.isTerminal) state = engine.move(state, nextDirection(state))
            best = maxOf(best, state.score)
            if (state.score >= target) return PuzzleSeed(candidate)
        }
        error("No 2048 seed reached the EASY target $target; best was $best.")
    }

    /**
     * A deterministic player good enough to finish an EASY level: spawns are deterministic, so a
     * short exhaustive lookahead is exact. Both the seed search and the driven ViewModel follow this
     * same line, so the test always plays the game it measured.
     */
    private fun nextDirection(state: Game2048State): Game2048Direction {
        val engine = Game2048Engine(state.puzzleId)
        return PREFERRED_DIRECTIONS
            .filter { direction -> engine.move(state, direction) != state }
            .maxBy { direction -> lookahead(engine, engine.move(state, direction), LOOKAHEAD_DEPTH) }
    }

    private fun lookahead(
        engine: Game2048Engine,
        state: Game2048State,
        depth: Int,
    ): Long {
        val value = state.score + EMPTY_CELL_WEIGHT * state.board.count { it == 0 }
        if (depth == 0 || state.status.isTerminal) return value
        return PREFERRED_DIRECTIONS
            .mapNotNull { direction -> engine.move(state, direction).takeIf { it != state } }
            .maxOfOrNull { moved -> lookahead(engine, moved, depth - 1) }
            ?: value
    }

    private fun Game2048ViewModel.play(direction: Game2048Direction) {
        move(direction)
        ready().motionEvent?.revision?.let(::finishMotion)
    }

    private fun Game2048ViewModel.ready(): Game2048UiState.Ready = uiState.value as Game2048UiState.Ready

    private class FrozenLevel(
        private val seed: PuzzleSeed,
    ) : CatalogLevelRepository {
        override val packVersion = CatalogLevelPackVersion.V1

        override fun observeCurrentLevel(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): Flow<CatalogLevelNumber> = MutableStateFlow(CatalogLevelNumber(1))

        override fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>> = MutableStateFlow(emptyMap())

        override suspend fun currentLevelId(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): CatalogLevelId = CatalogLevelId(puzzleType, difficulty, CatalogLevelNumber(1))

        override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition =
            CatalogLevelDefinition(levelId, seed, GeneratorVersion(Game2048GeneratorVersion.V2.value))
    }

    private class RecordingCompletions(
        private val saveGate: CompletableDeferred<Unit>,
    ) : GameCompletionRepository {
        val recorded = mutableListOf<GameCompletion>()
        var calls = 0
            private set

        override suspend fun complete(completion: GameCompletion): GameResult {
            calls += 1
            if (recorded.none { it.resultId == completion.resultId }) recorded += completion
            saveGate.await()
            return GameResult(
                resultId = completion.resultId,
                puzzleType = completion.puzzleType,
                difficulty = completion.difficulty,
                puzzleSeed = completion.puzzleSeed,
                generatorVersion = completion.generatorVersion,
                sessionScope = completion.sessionScope,
                hintsUsed = completion.hintsUsed,
                completedAt = Instant.EPOCH,
                outcome = completion.outcome,
                catalogLevel = completion.catalogLevel,
            )
        }
    }

    private object FullWallet : EconomyRepository {
        override fun observe(): Flow<PlayerEconomy> = MutableStateFlow(PlayerEconomy())

        override suspend fun refresh(): PlayerEconomy = PlayerEconomy()

        override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = error("Unused")

        override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = error("Unused")

        override suspend fun grantPurchasedGems(
            purchaseId: String,
            productId: String,
        ): EconomyGemPurchase = error("Unused")
    }

    private companion object {
        const val ATTEMPT_ID = "attempt"

        /** Keeping cells free is worth roughly one small merge; it stops the greedy player choking. */
        const val EMPTY_CELL_WEIGHT = 12L
        const val LOOKAHEAD_DEPTH = 4

        val PREFERRED_DIRECTIONS =
            listOf(
                Game2048Direction.DOWN,
                Game2048Direction.LEFT,
                Game2048Direction.RIGHT,
                Game2048Direction.UP,
            )

        /** The shipped EASY V2 score target; the level clears the instant it is reached. */
        val Game2048RulesetTarget: Long? =
            Game2048PuzzleId(PuzzleSeed(1L), Difficulty.EASY, Game2048GeneratorVersion.V2).rules.targetScore
    }
}
