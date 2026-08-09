package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.DailyGameSessionIdentity
import com.stanisryz.logica.session.GameSessionRepository
import com.stanisryz.logica.session.GameSessionScope
import com.stanisryz.logica.session.SavedGameSession
import com.stanisryz.logica.statistics.GameStatistics
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val date: LocalDate = LocalDate.of(2026, 8, 9)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun v2TodayDerivesPerEntryStateAndAggregateProgress() =
        runBlocking {
            val definition = DailyChallengePolicyV2.definitionFor(date)
            val balance = definition.entryFor(PuzzleType.BALANCE)
            val crowns = definition.entryFor(PuzzleType.CROWNS)
            val dailyRepository =
                FakeDailyChallengeRepository(
                    run = savedRun(DailyChallengePolicyV2.VERSION.value, DailyRunStatus.IN_PROGRESS),
                    entries =
                        mapOf(
                            PuzzleType.BALANCE to definition.savedChallenge(balance, DailyChallengeStatus.COMPLETED),
                            PuzzleType.CROWNS to definition.savedChallenge(crowns, DailyChallengeStatus.IN_PROGRESS),
                        ),
                )
            // Only Crowns has an active DAILY session; Balance is finished, so no session remains.
            val sessions =
                FakeGameSessionRepository(
                    mapOf(PuzzleType.CROWNS to definition.savedSession(crowns)),
                )

            val content = viewModel(dailyRepository, sessions).awaitContent()

            assertEquals(2, content.totalCount)
            assertEquals(1, content.completedCount)
            assertEquals(
                listOf(
                    TodayEntryUiState(PuzzleType.BALANCE, balance.difficulty, DailyEntryState.COMPLETED),
                    TodayEntryUiState(PuzzleType.CROWNS, crowns.difficulty, DailyEntryState.IN_PROGRESS),
                ),
                content.entries,
            )
        }

    @Test
    fun newRunUsesPolicyV2WhileAPersistedV1RunStaysASingleBalanceDaily() =
        runBlocking {
            val fresh = viewModel(FakeDailyChallengeRepository(), FakeGameSessionRepository()).awaitContent()

            assertEquals(DailyChallengePolicyV2.VERSION, fresh.definition.policyVersion)
            assertEquals(listOf(PuzzleType.BALANCE, PuzzleType.CROWNS), fresh.entries.map { it.puzzleType })
            assertEquals(0, fresh.completedCount)

            val v1Definition = DailyChallengePolicyV1.definitionFor(date)
            val v1Entry = v1Definition.entryFor(PuzzleType.BALANCE)
            val legacy =
                viewModel(
                    FakeDailyChallengeRepository(
                        run = savedRun(DailyChallengePolicyV1.VERSION.value, DailyRunStatus.IN_PROGRESS),
                        entries =
                            mapOf(
                                PuzzleType.BALANCE to
                                    v1Definition.savedChallenge(v1Entry, DailyChallengeStatus.IN_PROGRESS),
                            ),
                    ),
                    FakeGameSessionRepository(mapOf(PuzzleType.BALANCE to v1Definition.savedSession(v1Entry))),
                ).awaitContent()

            assertEquals(DailyChallengePolicyV1.VERSION, legacy.definition.policyVersion)
            assertEquals(
                listOf(TodayEntryUiState(PuzzleType.BALANCE, v1Entry.difficulty, DailyEntryState.IN_PROGRESS)),
                legacy.entries,
            )
            assertEquals(1, legacy.totalCount)
        }

    private fun viewModel(
        dailyChallengeRepository: DailyChallengeRepository,
        gameSessionRepository: GameSessionRepository,
    ): TodayViewModel =
        TodayViewModel(
            dailyChallengeRepository = dailyChallengeRepository,
            gameSessionRepository = gameSessionRepository,
            statisticsRepository = EmptyStatisticsRepository,
            dateProvider = { date },
            definitionProvider = DailyChallengePolicyResolver::definitionFor,
        )

    private suspend fun TodayViewModel.awaitContent(): TodayUiState.Content =
        uiState.first { it !is TodayUiState.Loading } as TodayUiState.Content

    private fun DailyChallengeDefinition.entryFor(puzzleType: PuzzleType): DailyPuzzleEntry = entries.single { it.puzzleType == puzzleType }

    private fun savedRun(
        policyVersion: Int,
        status: DailyRunStatus,
    ): SavedDailyRun =
        SavedDailyRun(
            challengeDate = date,
            policyVersion = DailyPolicyVersion(policyVersion),
            status = status,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            completedAt = if (status == DailyRunStatus.COMPLETED) Instant.EPOCH else null,
        )

    private fun DailyChallengeDefinition.savedChallenge(
        entry: DailyPuzzleEntry,
        status: DailyChallengeStatus,
    ): SavedDailyChallenge =
        SavedDailyChallenge(
            challengeDate = challengeDate,
            puzzleType = entry.puzzleType,
            policyVersion = policyVersion,
            difficulty = entry.difficulty,
            seed = entry.seed,
            generatorVersion = entry.generatorVersion,
            status = status,
        )

    private fun DailyChallengeDefinition.savedSession(entry: DailyPuzzleEntry): SavedGameSession =
        SavedGameSession(
            sessionId = "session-${entry.puzzleType}",
            puzzleType = entry.puzzleType,
            sessionScope = GameSessionScope.DAILY,
            difficulty = entry.difficulty,
            puzzleSeed = entry.seed,
            generatorVersion = entry.generatorVersion,
            dailyIdentity = DailyGameSessionIdentity(challengeDate, policyVersion.value),
            sessionFormatVersion = 1,
            gameplayPayload = "",
            moveHistoryPayload = "",
            hintsUsed = 0,
            status = "IN_PROGRESS",
        )

    private class FakeDailyChallengeRepository(
        private val run: SavedDailyRun? = null,
        private val entries: Map<PuzzleType, SavedDailyChallenge> = emptyMap(),
    ) : DailyChallengeRepository {
        override suspend fun read(
            challengeDate: LocalDate,
            puzzleType: PuzzleType,
        ): SavedDailyChallenge? = entries[puzzleType]?.takeIf { it.challengeDate == challengeDate }

        override suspend fun readRun(challengeDate: LocalDate): SavedDailyRun? = run?.takeIf { it.challengeDate == challengeDate }

        override suspend fun createRun(definition: DailyChallengeDefinition): SavedDailyRun = error("Unused.")
    }

    private class FakeGameSessionRepository(
        private val dailySessions: Map<PuzzleType, SavedGameSession> = emptyMap(),
    ) : GameSessionRepository {
        override suspend fun readActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): SavedGameSession? = dailySessions[puzzleType]?.takeIf { it.sessionScope == sessionScope }

        override fun replaceActiveSession(session: SavedGameSession) = error("Unused.")

        override fun updateActiveSession(session: SavedGameSession) = error("Unused.")

        override fun deleteActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
            sessionId: String,
        ) = error("Unused.")

        override fun observeHasActiveSession(
            puzzleType: PuzzleType,
            sessionScope: GameSessionScope,
        ): Flow<Boolean> = MutableStateFlow(dailySessions.containsKey(puzzleType))
    }

    private object EmptyStatisticsRepository : StatisticsRepository {
        override fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot> =
            flowOf(
                StatisticsSnapshot(
                    statistics = GameStatistics(0, 0, 0, 0, 0, emptyMap()),
                    dailyHintsUsedByDate = emptyMap(),
                ),
            )
    }
}
