package com.stanisryz.logica.daily

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.game2048.Game2048GameContext
import com.stanisryz.logica.game2048.Game2048Launch
import com.stanisryz.logica.game2048.Game2048UiState
import com.stanisryz.logica.game2048.Game2048ViewModel
import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.game2048.Game2048Direction
import com.stanisryz.logica.puzzle.core.game2048.Game2048Engine
import com.stanisryz.logica.puzzle.core.game2048.Game2048GeneratorVersion
import com.stanisryz.logica.puzzle.core.game2048.Game2048PuzzleId
import com.stanisryz.logica.puzzle.core.game2048.Game2048State
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuCellStatus
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuGameState
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPosition
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzleId
import com.stanisryz.logica.result.FakeGameCompletionDao
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.toEntity
import com.stanisryz.logica.session.DailyGameSessionIdentity
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
import com.stanisryz.logica.sudoku.SudokuCatalogProvider
import com.stanisryz.logica.sudoku.SudokuGameContext
import com.stanisryz.logica.sudoku.SudokuGameLaunch
import com.stanisryz.logica.sudoku.SudokuGameUiState
import com.stanisryz.logica.sudoku.SudokuGameViewModel
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Daily Policy V5 as a lifecycle: Sudoku and 2048 now have Daily sessions of their own, and each of
 * them has to coexist with the Catalog save of the very same game. The aggregate half of the test
 * runs the real completion transaction over a V5 definition, so 0/5 → 5/5, the SOLVED-only rule, and
 * the Retry path are exercised where they actually live rather than in the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyFiveGameLifecycleTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val date: LocalDate = LocalDate.of(2026, 8, 11)
    private val definition = DailyChallengePolicyV5.definitionFor(date)
    private val dailyIdentity = DailyGameSessionIdentity(date, DailyChallengePolicyV5.VERSION.value)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sudoku daily and catalog attempts are saved and restored independently`() =
        runBlocking {
            val sessions = FakeSessions()
            val entry = definition.entryFor(PuzzleType.SUDOKU)
            assertEquals(Difficulty.MEDIUM, entry.difficulty)
            assertEquals(SudokuCatalogProvider.VERSION, entry.generatorVersion)

            val catalog =
                sudokuViewModel(SudokuGameLaunch.New(Difficulty.HARD, CATALOG_SELECTOR), sessions, "sudoku-catalog")
            catalog.placeCorrect(catalog.awaitReady())
            val catalogSave = requireNotNull(sessions.read(PuzzleType.SUDOKU, GameSessionScope.CATALOG))

            val daily =
                sudokuViewModel(
                    SudokuGameLaunch.New(
                        difficulty = entry.difficulty,
                        selectorSeed = entry.seed,
                        providerVersion = entry.generatorVersion,
                        context = SudokuGameContext.Daily(date, definition.policyVersion),
                    ),
                    sessions,
                    "sudoku-daily",
                )
            assertEquals(MEDIUM_PUZZLE.id, daily.awaitReady().puzzle.id)
            daily.placeIncorrect(daily.ready())
            val expectedDailyCells = daily.ready().game.cells

            val dailySave = requireNotNull(sessions.read(PuzzleType.SUDOKU, GameSessionScope.DAILY))
            assertEquals("sudoku-daily", dailySave.sessionId)
            assertEquals(dailyIdentity, dailySave.dailyIdentity)
            assertEquals(entry.seed, dailySave.puzzleSeed)
            assertEquals(Difficulty.MEDIUM, dailySave.difficulty)
            // Starting the Daily game left the Catalog slot exactly as it was.
            assertEquals(catalogSave, sessions.read(PuzzleType.SUDOKU, GameSessionScope.CATALOG))

            val restoredDaily =
                sudokuViewModel(
                    SudokuGameLaunch.Restore(
                        context = SudokuGameContext.Daily(date, definition.policyVersion),
                        expectedSelectorId = entry.puzzleId,
                    ),
                    sessions,
                    "sudoku-daily-restored",
                ).awaitReady()
            // The Daily identity selects the record; the stored fingerprint still pins the exact one.
            assertEquals(MEDIUM_PUZZLE.id, restoredDaily.puzzle.id)
            assertEquals(expectedDailyCells, restoredDaily.game.cells)
            assertEquals(1, restoredDaily.game.mistakesUsed)

            val restoredCatalog =
                sudokuViewModel(SudokuGameLaunch.Restore(), sessions, "sudoku-catalog-restored").awaitReady()
            assertEquals(HARD_PUZZLE.id, restoredCatalog.puzzle.id)
            assertEquals(0, restoredCatalog.game.mistakesUsed)
            assertNotEquals(restoredDaily.puzzle.id, restoredCatalog.puzzle.id)
        }

    @Test
    fun `daily 2048 restores its exact next spawn and never touches the catalog save`() =
        runBlocking {
            val sessions = FakeSessions()
            val entry = definition.entryFor(PuzzleType.GAME_2048)
            assertEquals(GeneratorVersion(Game2048GeneratorVersion.V2.value), entry.generatorVersion)

            val catalog = game2048ViewModel(Game2048Launch.New(Difficulty.EASY, CATALOG_SEED), sessions, "2048-catalog")
            val catalogEngine = Game2048Engine(Game2048PuzzleId(CATALOG_SEED, Difficulty.EASY, Game2048GeneratorVersion.V2))
            catalog.awaitReady()
            catalog.move(validDirection(catalogEngine, catalog.ready().game))
            val catalogSave = requireNotNull(sessions.read(PuzzleType.GAME_2048, GameSessionScope.CATALOG))

            val puzzleId =
                Game2048PuzzleId(entry.seed, entry.difficulty, Game2048GeneratorVersion(entry.generatorVersion.value))
            val engine = Game2048Engine(puzzleId)
            val daily =
                game2048ViewModel(
                    Game2048Launch.New(
                        difficulty = entry.difficulty,
                        seed = entry.seed,
                        generatorVersion = entry.generatorVersion,
                        context = Game2048GameContext.Daily(date, definition.policyVersion),
                    ),
                    sessions,
                    "2048-daily",
                )
            var uninterrupted = daily.awaitReady().game
            assertEquals(puzzleId, uninterrupted.puzzleId)
            assertEquals(MEDIUM_TARGET_SCORE, uninterrupted.puzzleId.rules.targetScore)
            repeat(8) {
                daily.move(validDirection(engine, uninterrupted))
                uninterrupted = daily.ready().game
            }

            val dailySave = requireNotNull(sessions.read(PuzzleType.GAME_2048, GameSessionScope.DAILY))
            assertEquals(dailyIdentity, dailySave.dailyIdentity)
            assertEquals(entry.generatorVersion, dailySave.generatorVersion)
            assertEquals(catalogSave, sessions.read(PuzzleType.GAME_2048, GameSessionScope.CATALOG))

            val restoredViewModel =
                game2048ViewModel(
                    Game2048Launch.Restore(
                        context = Game2048GameContext.Daily(date, definition.policyVersion),
                        expectedPuzzleId = puzzleId,
                    ),
                    sessions,
                    "2048-daily-restored",
                )
            val restored = restoredViewModel.awaitReady().game
            assertEquals(uninterrupted.board, restored.board)
            assertEquals(uninterrupted.score, restored.score)
            assertEquals(uninterrupted.nextSpawnIndex, restored.nextSpawnIndex)

            // The next identical valid move must spawn exactly what uninterrupted play would have.
            val nextDirection = validDirection(engine, uninterrupted)
            restoredViewModel.move(nextDirection)
            assertEquals(engine.move(uninterrupted, nextDirection), restoredViewModel.ready().game)
            assertEquals(catalogSave, sessions.read(PuzzleType.GAME_2048, GameSessionScope.CATALOG))
        }

    @Test
    fun `a failed entry keeps the v5 run open while five solved entries complete it at five of five`() =
        runBlocking {
            val dao = FakeGameCompletionDao(definition)
            val sudoku = definition.entryFor(PuzzleType.SUDOKU)
            val failed = dao.complete(sudoku.completion("daily-3", GameOutcome.FAILED).toEntity(1_000))

            // A failed Daily attempt is durable and costs a life, but completes nothing.
            assertEquals(GameOutcome.FAILED.name, failed.outcome)
            assertEquals(DailyChallengeStatus.IN_PROGRESS.name, dao.challenge(failed).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(EconomyRules.STARTING_LIVES - 1, dao.wallet(1_000).lives)
            assertEquals(0, dao.wallet(1_000).gems)

            // Retry: same Daily identity, new session, and only now does the entry complete.
            dao.startRetrySession(definition, sudoku, sessionId = "daily-3-retry", hintsUsed = 0)
            val solvedRetry = sudoku.completion("daily-3-retry", GameOutcome.SOLVED, hintsUsed = 0).toEntity(2_000)
            dao.complete(solvedRetry)
            dao.complete(solvedRetry)

            assertEquals(DailyChallengeStatus.COMPLETED.name, dao.challenge(solvedRetry).status)
            assertEquals(DailyRunStatus.IN_PROGRESS.name, dao.run.status)
            assertEquals(MEDIUM_GEM_REWARD, dao.wallet(2_000).gems)

            definition.entries
                .filterNot { it.puzzleType == PuzzleType.SUDOKU }
                .forEachIndexed { index, entry ->
                    val resultId = "daily-${definition.entries.indexOf(entry)}"
                    dao.complete(entry.completion(resultId, GameOutcome.SOLVED).toEntity(3_000L + index))
                    val isLast = index == definition.entries.size - 2
                    assertEquals(
                        if (isLast) DailyRunStatus.COMPLETED.name else DailyRunStatus.IN_PROGRESS.name,
                        dao.run.status,
                    )
                }

            // 5/5: five MEDIUM solves, five entries completed, one wallet event per durable result.
            assertEquals(5, definition.entries.size)
            assertEquals(6, dao.results.size)
            assertEquals(5 * MEDIUM_GEM_REWARD, dao.wallet(4_000).gems)
            assertEquals(EconomyRules.STARTING_LIVES - 1, dao.wallet(4_000).lives)
            definition.entries.forEach { entry ->
                assertEquals(
                    DailyChallengeStatus.COMPLETED.name,
                    requireNotNull(dao.findDailyChallenge(date.toString(), entry.puzzleType.name)).status,
                )
                assertNull(dao.findSession(entry.puzzleType.name, GameSessionScope.DAILY.name))
            }
        }

    private fun DailyPuzzleEntry.completion(
        resultId: String,
        outcome: GameOutcome,
        hintsUsed: Int = definition.entries.indexOfFirst { it.puzzleType == puzzleType } + 1,
    ): GameCompletion =
        GameCompletion(
            resultId = resultId,
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = seed,
            generatorVersion = generatorVersion,
            sessionScope = GameSessionScope.DAILY,
            hintsUsed = hintsUsed,
            outcome = outcome,
            attemptsUsed = if (puzzleType == PuzzleType.WORD) WORD_ATTEMPTS else null,
            challengeDate = definition.challengeDate,
            dailyPolicyVersion = definition.policyVersion,
        )

    private fun DailyChallengeDefinition.entryFor(puzzleType: PuzzleType): DailyPuzzleEntry = entries.single { it.puzzleType == puzzleType }

    private fun sudokuViewModel(
        launch: SudokuGameLaunch,
        sessions: FakeSessions,
        sessionId: String,
    ): SudokuGameViewModel =
        SudokuGameViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = RecordingCompletions(sessions),
            economyRepository = FullWallet,
            provider = SudokuCatalogProvider(FakeSudokuDataset),
            workDispatcher = dispatcher,
            sessionIdFactory = { sessionId },
        )

    private fun game2048ViewModel(
        launch: Game2048Launch,
        sessions: FakeSessions,
        sessionId: String,
    ): Game2048ViewModel =
        Game2048ViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = RecordingCompletions(sessions),
            economyRepository = FullWallet,
            sessionIdFactory = { sessionId },
        )

    private suspend fun SudokuGameViewModel.awaitReady(): SudokuGameUiState.Ready =
        uiState.first { it !is SudokuGameUiState.Loading } as SudokuGameUiState.Ready

    private fun SudokuGameViewModel.ready(): SudokuGameUiState.Ready = uiState.value as SudokuGameUiState.Ready

    private fun SudokuGameViewModel.placeCorrect(ready: SudokuGameUiState.Ready) {
        val position = ready.game.firstEmptyPosition()
        selectCell(position)
        inputDigit(ready.puzzle.solution[position.index].digitToInt())
    }

    private fun SudokuGameViewModel.placeIncorrect(ready: SudokuGameUiState.Ready) {
        val position = ready.game.firstEmptyPosition()
        val solution = ready.puzzle.solution[position.index].digitToInt()
        selectCell(position)
        inputDigit(if (solution == 9) 8 else solution + 1)
    }

    private fun SudokuGameState.firstEmptyPosition(): SudokuPosition =
        cells.indices
            .map(SudokuPosition::fromIndex)
            .first { cellAt(it).status == SudokuCellStatus.EMPTY }

    private suspend fun Game2048ViewModel.awaitReady(): Game2048UiState.Ready =
        uiState.first { it !is Game2048UiState.Loading } as Game2048UiState.Ready

    private fun Game2048ViewModel.ready(): Game2048UiState.Ready = uiState.value as Game2048UiState.Ready

    private fun validDirection(
        engine: Game2048Engine,
        game: Game2048State,
    ): Game2048Direction = Game2048Direction.entries.first { engine.move(game, it) != game }

    /** Two records with different difficulties, so a Daily and a Catalog attempt cannot be confused. */
    private object FakeSudokuDataset : SudokuDataset {
        override fun availableCount(
            version: SudokuDatasetVersion,
            difficulty: SudokuDifficulty,
        ): SudokuDatasetResult<Int> = SudokuDatasetResult.Success(1)

        override fun getPuzzle(id: SudokuPuzzleId): SudokuDatasetResult<SudokuPuzzle> =
            SudokuDatasetResult.Success(puzzleFor(id.difficulty))

        override fun selectPuzzle(
            version: SudokuDatasetVersion,
            difficulty: SudokuDifficulty,
            selector: Long,
        ): SudokuDatasetResult<SudokuPuzzle> {
            assertEquals(SudokuDatasetVersion.V1, version)
            return SudokuDatasetResult.Success(puzzleFor(difficulty))
        }

        private fun puzzleFor(difficulty: SudokuDifficulty): SudokuPuzzle =
            when (difficulty) {
                SudokuDifficulty.MEDIUM -> MEDIUM_PUZZLE
                SudokuDifficulty.HARD -> HARD_PUZZLE
                else -> error("The fake dataset only serves the two difficulties this test uses.")
            }
    }

    private class FakeSessions : GameSessionRepository {
        private val sessions = mutableMapOf<Pair<PuzzleType, GameSessionScope>, SavedGameSession>()

        fun read(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): SavedGameSession? = sessions[puzzleType to sessionScope]

        override suspend fun readActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): SavedGameSession? = read(puzzleType, sessionScope)

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
        override suspend fun complete(completion: GameCompletion): GameResult {
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
                attemptsUsed = completion.attemptsUsed,
                challengeDate = completion.challengeDate,
                dailyPolicyVersion = completion.dailyPolicyVersion,
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
        val CATALOG_SELECTOR = PuzzleSeed(0x5_0001)
        val CATALOG_SEED = PuzzleSeed(0x2048_0001)
        val MEDIUM_GEM_REWARD = EconomyRules.solvedGemReward(Difficulty.MEDIUM)
        const val MEDIUM_TARGET_SCORE = 30_000L
        const val WORD_ATTEMPTS = 3
        const val GIVENS = "050703060007000800000816000000030000005000100730040086906000204840572093000409000"
        const val SOLUTION = "158723469367954821294816375619238547485697132732145986976381254841572693523469718"

        val MEDIUM_PUZZLE =
            SudokuPuzzle(
                id =
                    SudokuPuzzleId(
                        SudokuDatasetVersion.V1,
                        SudokuDifficulty.MEDIUM,
                        "dfe20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d",
                    ),
                givens = GIVENS,
                solution = SOLUTION,
                upstreamRatingTenths = 25,
            )

        val HARD_PUZZLE =
            SudokuPuzzle(
                id =
                    SudokuPuzzleId(
                        SudokuDatasetVersion.V1,
                        SudokuDifficulty.HARD,
                        "a1b20863da651e55a9ac79a23e69134faa375a25f50ec4b8518b84199ede492d",
                    ),
                givens = GIVENS,
                solution = SOLUTION,
                upstreamRatingTenths = 40,
            )
    }
}
