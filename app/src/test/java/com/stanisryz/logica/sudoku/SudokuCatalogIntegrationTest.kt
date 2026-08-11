package com.stanisryz.logica.sudoku

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuCatalogIntegrationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val puzzle = hardPuzzle()
    private val provider = SudokuCatalogProvider(SinglePuzzleDataset(puzzle))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun hardSelectorAutosavesAndRestoresTheExactFingerprintWithoutTouchingOtherSlots() =
        runBlocking {
            val sessions = FakeSessions(balanceSave())
            val original = viewModel(SudokuGameLaunch.New(Difficulty.HARD, SELECTOR), sessions).awaitReady()
            assertEquals(puzzle.id, original.puzzle.id)

            val gameViewModel = viewModel(SudokuGameLaunch.Restore(), sessions)
            var ready = gameViewModel.awaitReady()
            gameViewModel.requestHint()
            ready = gameViewModel.ready()

            val candidatePosition = ready.game.firstEmptyPosition()
            gameViewModel.selectCell(candidatePosition)
            gameViewModel.togglePencilMode()
            val candidate =
                (1..9).first { digit ->
                    val before = gameViewModel.ready().game
                    gameViewModel.inputDigit(digit)
                    gameViewModel.ready().game != before
                }
            assertTrue(
                gameViewModel
                    .ready()
                    .game
                    .cellAt(candidatePosition)
                    .candidates
                    .contains(candidate),
            )

            gameViewModel.togglePencilMode()
            val wrongPosition = gameViewModel.ready().game.firstEmptyPosition(excluding = candidatePosition)
            val wrongDigit = puzzle.solution[wrongPosition.index].digitToInt().let { if (it == 9) 8 else it + 1 }
            gameViewModel.selectCell(wrongPosition)
            gameViewModel.inputDigit(wrongDigit)
            val beforeRestore = gameViewModel.ready().game

            val restored = viewModel(SudokuGameLaunch.Restore(), sessions).awaitReady()
            assertEquals(puzzle.id, restored.puzzle.id)
            assertEquals(beforeRestore.cells, restored.game.cells)
            assertEquals(1, restored.game.mistakesUsed)
            assertEquals(1, restored.game.hintsUsed)
            assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))

            // A parseable but different embedded fingerprint is an inconsistency and is retained.
            val saved = requireNotNull(sessions.readActiveSession(PuzzleType.SUDOKU, GameSessionScope.CATALOG))
            val otherFingerprint = puzzle.id.fingerprint.dropLast(1) + if (puzzle.id.fingerprint.last() == '0') '1' else '0'
            sessions.replaceActiveSession(
                saved.copy(
                    gameplayPayload =
                        saved.gameplayPayload.replace(
                            puzzle.id.fingerprint,
                            otherFingerprint,
                        ),
                ),
            )
            val inconsistent = viewModel(SudokuGameLaunch.Restore(), sessions).awaitError()
            assertEquals(SudokuGameError.INVALID_SAVED_SESSION, inconsistent.reason)
            assertNotNull(sessions.readActiveSession(PuzzleType.SUDOKU, GameSessionScope.CATALOG))
            assertNotNull(sessions.readActiveSession(PuzzleType.BALANCE, GameSessionScope.CATALOG))
        }

    @Test
    fun terminalFailureMustBeDurableBeforeSamePuzzleRetryUsesANewSessionId() =
        runBlocking {
            val sessions = FakeSessions()
            val completions = RecordingCompletions(sessions)
            val ids = ArrayDeque(listOf("sudoku-attempt-1", "sudoku-attempt-2"))
            val gameViewModel =
                SudokuGameViewModel(
                    launch = SudokuGameLaunch.New(Difficulty.HARD, SELECTOR),
                    sessionRepository = sessions,
                    completionRepository = completions,
                    economyRepository = FullWallet,
                    provider = provider,
                    workDispatcher = dispatcher,
                    sessionIdFactory = { ids.removeFirst() },
                )
            val started = gameViewModel.awaitReady()
            val position = started.game.firstEmptyPosition()
            gameViewModel.selectCell(position)
            val solution = puzzle.solution[position.index].digitToInt()
            (1..9).filter { it != solution }.take(3).forEach(gameViewModel::inputDigit)

            val terminal = gameViewModel.ready()
            assertEquals(SudokuGameStatus.FAILED, terminal.game.status)
            assertEquals(CompletionPersistence.Saved, terminal.completionPersistence)
            assertEquals(GameOutcome.FAILED, completions.results.single().outcome)
            assertEquals("sudoku-attempt-1", completions.results.single().resultId)

            gameViewModel.retry()
            val retried = gameViewModel.ready()
            assertEquals(puzzle.id, retried.puzzle.id)
            assertEquals(SudokuGameStatus.IN_PROGRESS, retried.game.status)
            assertEquals(0, retried.game.mistakesUsed)
            assertEquals(0, retried.game.hintsUsed)
            val active = requireNotNull(sessions.readActiveSession(PuzzleType.SUDOKU, GameSessionScope.CATALOG))
            assertEquals("sudoku-attempt-2", active.sessionId)
            assertNotEquals(completions.results.single().resultId, active.sessionId)
        }

    private fun viewModel(
        launch: SudokuGameLaunch,
        sessions: FakeSessions,
    ): SudokuGameViewModel =
        SudokuGameViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = RecordingCompletions(sessions),
            economyRepository = FullWallet,
            provider = provider,
            workDispatcher = dispatcher,
        )

    private suspend fun SudokuGameViewModel.awaitReady(): SudokuGameUiState.Ready =
        uiState.first { it !is SudokuGameUiState.Loading } as SudokuGameUiState.Ready

    private suspend fun SudokuGameViewModel.awaitError(): SudokuGameUiState.Error =
        uiState.first { it !is SudokuGameUiState.Loading } as SudokuGameUiState.Error

    private fun SudokuGameViewModel.ready(): SudokuGameUiState.Ready = uiState.value as SudokuGameUiState.Ready

    private fun com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState.firstEmptyPosition(
        excluding: SudokuPosition? = null,
    ): SudokuPosition =
        cells.indices
            .map(SudokuPosition::fromIndex)
            .first { it != excluding && cellAt(it).status == SudokuCellStatus.EMPTY }

    private class SinglePuzzleDataset(
        private val puzzle: SudokuPuzzle,
    ) : SudokuDataset {
        override fun availableCount(
            version: SudokuDatasetVersion,
            difficulty: SudokuDifficulty,
        ): SudokuDatasetResult<Int> = SudokuDatasetResult.Success(1)

        override fun getPuzzle(id: SudokuPuzzleId): SudokuDatasetResult<SudokuPuzzle> = SudokuDatasetResult.Success(puzzle)

        override fun selectPuzzle(
            version: SudokuDatasetVersion,
            difficulty: SudokuDifficulty,
            selector: Long,
        ): SudokuDatasetResult<SudokuPuzzle> {
            assertEquals(SudokuDatasetVersion.V1, version)
            assertEquals(SudokuDifficulty.HARD, difficulty)
            assertEquals(SELECTOR.value, selector)
            return SudokuDatasetResult.Success(puzzle)
        }
    }

    private class FakeSessions(
        vararg initial: SavedGameSession,
    ) : GameSessionRepository {
        private val sessions = initial.associateBy { it.puzzleType to it.sessionScope }.toMutableMap()

        override suspend fun readActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): SavedGameSession? = sessions[puzzleType to sessionScope]

        override fun replaceActiveSession(session: SavedGameSession) {
            sessions[session.puzzleType to session.sessionScope] = session
        }

        override fun updateActiveSession(session: SavedGameSession) = replaceActiveSession(session)

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
        val SELECTOR = PuzzleSeed(0x35_0001)

        fun hardPuzzle() =
            SudokuPuzzle(
                id =
                    SudokuPuzzleId(
                        SudokuDatasetVersion.V1,
                        SudokuDifficulty.HARD,
                        "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d",
                    ),
                givens = "050703060007000800000816000000030000005000100730040086906000204840572093000409000",
                solution = "158723469367954821294816375619238547485697132732145986976381254841572693523469718",
                upstreamRatingTenths = 40,
            )

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
