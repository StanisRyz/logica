package com.stanisryz.logica.game2048

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048SessionCodecV1
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
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class Game2048CatalogIntegrationTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `catalog save restores exact future spawn stays isolated and honors zero lives`() =
        runBlocking {
            val sessions = FakeSessions(balanceSave())
            val economy = MutableEconomy()
            // A new Catalog game is V2: selected difficulty, fresh seed, current rules version.
            val puzzleId = Game2048PuzzleId(SEED, Difficulty.MEDIUM, Game2048GeneratorVersion.V2)
            val gameEngine = Game2048Engine(puzzleId)
            val original = viewModel(Game2048Launch.New(Difficulty.MEDIUM, SEED), sessions, economy)
            var uninterrupted = original.awaitReady().game
            assertEquals(puzzleId, uninterrupted.puzzleId)

            repeat(8) {
                val direction = validDirection(gameEngine, uninterrupted)
                original.move(direction)
                uninterrupted = original.ready().game
            }
            val saved = requireNotNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
            val restoredViewModel = viewModel(Game2048Launch.Restore(), sessions, economy)
            val restored = restoredViewModel.awaitReady().game

            assertEquals(uninterrupted.puzzleId, restored.puzzleId)
            assertEquals(uninterrupted.board, restored.board)
            assertEquals(uninterrupted.score, restored.score)
            assertEquals(uninterrupted.nextSpawnIndex, restored.nextSpawnIndex)
            val nextDirection = validDirection(gameEngine, uninterrupted)
            val expectedNext = gameEngine.move(uninterrupted, nextDirection)
            restoredViewModel.move(nextDirection)
            assertEquals(expectedNext, restoredViewModel.ready().game)
            assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))

            economy.wallet.value = PlayerEconomy(lives = 0, nextLifeAtEpochMillis = 1_000L)
            val blocked = restoredViewModel.ready().game
            val writesBeforeBlockedMove = sessions.updateCount
            restoredViewModel.move(validDirection(gameEngine, blocked))
            assertEquals(blocked, restoredViewModel.ready().game)
            assertEquals(writesBeforeBlockedMove, sessions.updateCount)

            val current = requireNotNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
            sessions.replaceActiveSession(current.copy(gameplayPayload = saved.gameplayPayload.replaceFirst("board=", "board=3,")))
            val corrupt = viewModel(Game2048Launch.Restore(), sessions, economy).awaitError()
            assertEquals(Game2048GameError.INVALID_SAVED_SESSION, corrupt.reason)
            assertNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
            assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))

            val blockedStart = viewModel(Game2048Launch.New(Difficulty.EASY, PuzzleSeed(99L)), sessions, economy)
            assertEquals(Game2048GameError.NO_LIVES, blockedStart.awaitError().reason)
            assertNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
        }

    /**
     * A persisted V1 attempt keeps the target-tile contract it was created with: it restores as V1,
     * it is already SOLVED at tile 256, and its Retry stays V1 on the same seed and difficulty.
     */
    @Test
    fun `terminal outcomes persist before same puzzle retry starts a new session`() =
        runBlocking {
            val puzzleId = Game2048PuzzleId(PuzzleSeed(37L), Difficulty.EASY, Game2048GeneratorVersion.V1)
            val gameEngine = Game2048Engine(puzzleId)
            val solved = gameEngine.restore(listOf(256) + List(15) { 0 }, score = 512L, nextSpawnIndex = 12L)
            val sessions = FakeSessions(saved("attempt-1", solved))
            val completions = RecordingCompletions(sessions)
            val viewModel =
                Game2048ViewModel(
                    launch = Game2048Launch.Restore(),
                    sessionRepository = sessions,
                    completionRepository = completions,
                    economyRepository = MutableEconomy(),
                    sessionIdFactory = { "attempt-2" },
                )

            val terminal = viewModel.awaitReady()
            assertEquals(Game2048GeneratorVersion.V1, terminal.game.puzzleId.generatorVersion)
            assertEquals(Game2048Status.SOLVED, terminal.game.status)
            assertEquals(CompletionPersistence.Saved, terminal.completionPersistence)
            assertEquals(GameOutcome.SOLVED, completions.results.single().outcome)
            assertEquals(PuzzleType.GAME_2048, completions.results.single().puzzleType)
            assertEquals(0, completions.results.single().hintsUsed)
            assertNull(completions.results.single().attemptsUsed)

            viewModel.retry()
            val retried = viewModel.ready().game
            assertEquals(puzzleId, retried.puzzleId)
            assertEquals(gameEngine.start(), retried)
            val active = requireNotNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
            assertEquals("attempt-2", active.sessionId)
            assertNotEquals("attempt-1", active.sessionId)

            val failed =
                gameEngine.restore(
                    listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2),
                    score = 128L,
                    nextSpawnIndex = 30L,
                )
            assertEquals(Game2048Status.FAILED, failed.status)
            val failedSessions = FakeSessions(saved("failed-attempt", failed))
            val failedCompletions = RecordingCompletions(failedSessions)
            val failedViewModel =
                Game2048ViewModel(
                    Game2048Launch.Restore(),
                    failedSessions,
                    failedCompletions,
                    MutableEconomy(),
                )
            assertEquals(CompletionPersistence.Saved, failedViewModel.awaitReady().completionPersistence)
            assertEquals(GameOutcome.FAILED, failedCompletions.results.single().outcome)
        }

    @Test
    fun `a v2 attempt only completes when it runs out of moves and retries as v2`() =
        runBlocking {
            val puzzleId = Game2048PuzzleId(PuzzleSeed(38L), Difficulty.EASY, Game2048GeneratorVersion.V2)
            val gameEngine = Game2048Engine(puzzleId)

            // Past the goal with moves left: informational only, so no result is produced at all.
            val past = gameEngine.restore(listOf(2, 4, 8, 16) + List(12) { 0 }, score = 20_000L, nextSpawnIndex = 40L)
            assertEquals(Game2048Status.IN_PROGRESS, past.status)
            assertTrue(past.goalReached)
            val liveCompletions = RecordingCompletions(FakeSessions(saved("v2-live", past)))
            val live =
                Game2048ViewModel(
                    Game2048Launch.Restore(),
                    FakeSessions(saved("v2-live", past)),
                    liveCompletions,
                    MutableEconomy(),
                )
            assertEquals(CompletionPersistence.NotRequired, live.awaitReady().completionPersistence)
            assertTrue(liveCompletions.results.isEmpty())

            val deadBoard = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
            val solved = gameEngine.restore(deadBoard, score = 12_000L, nextSpawnIndex = 60L)
            assertEquals(Game2048Status.SOLVED, solved.status)
            // The very same dead board one point short of the target is a defeat instead.
            assertEquals(Game2048Status.FAILED, gameEngine.restore(deadBoard, 11_999L, 60L).status)

            val sessions = FakeSessions(saved("v2-attempt-1", solved))
            val completions = RecordingCompletions(sessions)
            val viewModel =
                Game2048ViewModel(
                    launch = Game2048Launch.Restore(),
                    sessionRepository = sessions,
                    completionRepository = completions,
                    economyRepository = MutableEconomy(),
                    sessionIdFactory = { "v2-attempt-2" },
                )

            assertEquals(CompletionPersistence.Saved, viewModel.awaitReady().completionPersistence)
            val result = completions.results.single()
            assertEquals(GameOutcome.SOLVED, result.outcome)
            assertEquals(Difficulty.EASY, result.difficulty)
            assertEquals(V2_VERSION, result.generatorVersion)

            viewModel.retry()
            val retried = viewModel.ready().game
            assertEquals(puzzleId, retried.puzzleId)
            assertEquals(gameEngine.start(), retried)
            val active = requireNotNull(sessions.readActiveSession(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
            assertEquals("v2-attempt-2", active.sessionId)
            assertEquals(V2_VERSION, active.generatorVersion)
        }

    private fun viewModel(
        launch: Game2048Launch,
        sessions: FakeSessions,
        economy: EconomyRepository,
    ): Game2048ViewModel =
        Game2048ViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = RecordingCompletions(sessions),
            economyRepository = economy,
        )

    private suspend fun Game2048ViewModel.awaitReady(): Game2048UiState.Ready =
        uiState.first { it !is Game2048UiState.Loading } as Game2048UiState.Ready

    private suspend fun Game2048ViewModel.awaitError(): Game2048UiState.Error =
        uiState.first { it !is Game2048UiState.Loading } as Game2048UiState.Error

    private fun Game2048ViewModel.ready(): Game2048UiState.Ready = uiState.value as Game2048UiState.Ready

    private fun validDirection(
        engine: Game2048Engine,
        game: Game2048State,
    ): Game2048Direction = Game2048Direction.entries.first { engine.move(game, it) != game }

    private fun saved(
        sessionId: String,
        game: Game2048State,
    ): SavedGameSession {
        val encoded = Game2048SessionCodecV1.encode(game)
        return SavedGameSession(
            sessionId = sessionId,
            puzzleType = PuzzleType.GAME_2048,
            sessionScope = GameSessionScope.CATALOG,
            difficulty = game.puzzleId.difficulty,
            puzzleSeed = game.puzzleId.seed,
            generatorVersion = GeneratorVersion(game.puzzleId.generatorVersion.value),
            sessionFormatVersion = encoded.formatVersion,
            gameplayPayload = encoded.payload,
            moveHistoryPayload = "",
            hintsUsed = 0,
            status = game.status.name,
        )
    }

    private class FakeSessions(
        vararg initial: SavedGameSession,
    ) : GameSessionRepository {
        private val sessions = initial.associateBy { it.puzzleType to it.sessionScope }.toMutableMap()
        var updateCount: Int = 0
            private set

        override suspend fun readActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): SavedGameSession? = sessions[puzzleType to sessionScope]

        override fun replaceActiveSession(session: SavedGameSession) {
            sessions[session.puzzleType to session.sessionScope] = session
        }

        override fun updateActiveSession(session: SavedGameSession) {
            updateCount += 1
            replaceActiveSession(session)
        }

        override fun deleteActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
            sessionId: String,
        ) {
            val key = puzzleType to sessionScope
            if (sessions[key]?.sessionId == sessionId) sessions.remove(key)
        }

        override fun observeHasActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): Flow<Boolean> = MutableStateFlow(sessions.containsKey(puzzleType to sessionScope))
    }

    private class RecordingCompletions(
        private val sessions: FakeSessions,
    ) : GameCompletionRepository {
        val results = mutableListOf<GameCompletion>()

        override suspend fun complete(completion: GameCompletion): GameResult {
            if (results.none { it.resultId == completion.resultId }) results += completion
            sessions.deleteActiveSession(completion.puzzleType, completion.sessionScope, completion.resultId)
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
            )
        }
    }

    private class MutableEconomy : EconomyRepository {
        val wallet = MutableStateFlow(PlayerEconomy())

        override fun observe(): Flow<PlayerEconomy> = wallet

        override suspend fun refresh(): PlayerEconomy = wallet.value

        override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = error("Unused")

        override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = error("Unused")

        override suspend fun grantPurchasedGems(
            purchaseId: String,
            productId: String,
        ): EconomyGemPurchase = error("Unused")
    }

    private companion object {
        val SEED = PuzzleSeed(0x2048_0037)
        val V2_VERSION = GeneratorVersion(Game2048GeneratorVersion.V2.value)

        fun balanceSave() =
            SavedGameSession(
                sessionId = "balance",
                puzzleType = PuzzleType.BALANCE,
                sessionScope = GameSessionScope.CATALOG,
                difficulty = Difficulty.EASY,
                puzzleSeed = PuzzleSeed(1),
                generatorVersion = GeneratorVersion(1),
                sessionFormatVersion = 3,
                gameplayPayload = "untouched",
                moveHistoryPayload = "",
                hintsUsed = 0,
                status = "IN_PROGRESS",
            )
    }
}
