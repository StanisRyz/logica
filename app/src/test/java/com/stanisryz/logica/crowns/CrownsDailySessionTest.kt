package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.model.Difficulty
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

    private fun viewModel(
        launch: CrownsGameLaunch,
        sessions: GameSessionRepository,
    ): CrownsGameViewModel =
        CrownsGameViewModel(
            launch = launch,
            sessionRepository = sessions,
            completionRepository = UnusedCompletionRepository,
            workDispatcher = dispatcher,
        )

    private suspend fun CrownsGameViewModel.awaitReady(): CrownsGameUiState.Ready =
        uiState.first { it !is CrownsGameUiState.Loading } as CrownsGameUiState.Ready

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
}
