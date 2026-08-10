package com.stanisryz.logica.crowns

import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.session.DailyGameSessionIdentity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CrownsDailySessionTest {
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
    fun dailyCrownsPersistsItsOwnScopedSessionWithoutDisturbingTheCatalogOne() =
        runBlocking {
            val date = LocalDate.of(2026, 8, 9)
            val definition = DailyChallengePolicyV2.definitionFor(date)
            val entry = definition.entries.single { it.puzzleType == PuzzleType.CROWNS }
            val sessions = FakeGameSessionRepository()

            viewModel(CrownsGameLaunch.New(Difficulty.EASY, PuzzleSeed(4242)), sessions).awaitReady()
            viewModel(
                CrownsGameLaunch.New(
                    difficulty = entry.difficulty,
                    seed = entry.seed,
                    generatorVersion = entry.generatorVersion,
                    context = CrownsGameContext.Daily(date, definition.policyVersion),
                ),
                sessions,
            ).awaitReady()

            val catalog = requireNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.CATALOG))
            val daily = requireNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))

            assertEquals(PuzzleSeed(4242), catalog.puzzleSeed)
            assertEquals(null, catalog.dailyIdentity)
            assertEquals(GameSessionScope.DAILY, daily.sessionScope)
            assertEquals(PuzzleType.CROWNS, daily.puzzleType)
            assertEquals(entry.difficulty, daily.difficulty)
            assertEquals(entry.seed, daily.puzzleSeed)
            assertEquals(entry.generatorVersion, daily.generatorVersion)
            assertEquals(DailyGameSessionIdentity(date, definition.policyVersion.value), daily.dailyIdentity)

            // Scope-targeted restore must reach the Catalog save even though a Daily save also exists.
            val restored = viewModel(CrownsGameLaunch.Restore(), sessions).awaitReady()
            assertEquals(PuzzleSeed(4242), restored.puzzle.id.seed)
            assertNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))
        }

    @Test
    fun mismatchedDailyIdentityIsReportedWhileOnlyUnreadableGameplayIsDiscarded() =
        runBlocking {
            val date = LocalDate.of(2026, 8, 9)
            val definition = DailyChallengePolicyV2.definitionFor(date)
            val entry = definition.entries.single { it.puzzleType == PuzzleType.CROWNS }
            val dailyContext = CrownsGameContext.Daily(date, definition.policyVersion)
            val sessions = FakeGameSessionRepository()

            viewModel(CrownsGameLaunch.New(Difficulty.EASY, PuzzleSeed(4242)), sessions).awaitReady()
            viewModel(
                CrownsGameLaunch.New(entry.difficulty, entry.seed, entry.generatorVersion, dailyContext),
                sessions,
            ).awaitReady()

            // Another date's Daily definition must never consume today's save.
            val otherDate =
                viewModel(CrownsGameLaunch.Restore(CrownsGameContext.Daily(date.minusDays(1), definition.policyVersion)), sessions)
            assertEquals(CrownsGameError.INVALID_SAVED_SESSION, otherDate.awaitError().reason)
            assertNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))

            // A seed that does not belong to the Daily entry is an inconsistency, not damaged gameplay.
            val wrongPuzzle =
                viewModel(
                    CrownsGameLaunch.Restore(
                        dailyContext,
                        PuzzleId(PuzzleType.CROWNS, entry.difficulty, PuzzleSeed(7), entry.generatorVersion),
                    ),
                    sessions,
                )
            assertEquals(CrownsGameError.INVALID_SAVED_SESSION, wrongPuzzle.awaitError().reason)
            assertNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))

            val saved = requireNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))
            sessions.replaceActiveSession(saved.copy(gameplayPayload = "size=8"))
            val corrupted = viewModel(CrownsGameLaunch.Restore(dailyContext, entry.puzzleId), sessions)

            assertEquals(CrownsGameError.INVALID_SAVED_SESSION, corrupted.awaitError().reason)
            assertNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.DAILY))
            assertNotNull(sessions.readActiveSession(PuzzleType.CROWNS, GameSessionScope.CATALOG))
        }

    private fun viewModel(
        launch: CrownsGameLaunch,
        sessions: GameSessionRepository,
    ): CrownsGameViewModel =
        CrownsGameViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = UnusedCompletionRepository,
            economyRepository = FullWalletEconomyRepository,
            workDispatcher = dispatcher,
        )

    private suspend fun CrownsGameViewModel.awaitReady(): CrownsGameUiState.Ready =
        uiState.first { it !is CrownsGameUiState.Loading } as CrownsGameUiState.Ready

    private suspend fun CrownsGameViewModel.awaitError(): CrownsGameUiState.Error =
        uiState.first { it !is CrownsGameUiState.Loading } as CrownsGameUiState.Error

    private class FakeGameSessionRepository : GameSessionRepository {
        private val sessions = mutableMapOf<Pair<PuzzleType, GameSessionScope>, SavedGameSession>()

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
            if (sessions[puzzleType to sessionScope]?.sessionId == sessionId) sessions.remove(puzzleType to sessionScope)
        }

        override fun observeHasActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): Flow<Boolean> = MutableStateFlow(sessions.containsKey(puzzleType to sessionScope))
    }

    private object UnusedCompletionRepository : GameCompletionRepository {
        override suspend fun complete(completion: GameCompletion): GameResult = error("Unused.")
    }

    /** Session restore is what this test covers, so the wallet simply never blocks gameplay. */
    private object FullWalletEconomyRepository : EconomyRepository {
        override fun observe(): Flow<PlayerEconomy> = MutableStateFlow(PlayerEconomy())

        override suspend fun refresh(): PlayerEconomy = PlayerEconomy()

        override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = error("Unused.")
    }
}
