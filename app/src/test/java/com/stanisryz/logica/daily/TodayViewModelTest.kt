package com.stanisryz.logica.daily

import com.stanisryz.logica.puzzle.core.daily.DailyChallengeDefinition
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV1
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV2
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV3
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV4
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyV5
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.daily.DailyPuzzleEntry
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.statistics.GameStatistics
import com.stanisryz.logica.statistics.StatisticsRepository
import com.stanisryz.logica.statistics.StatisticsSnapshot
import com.stanisryz.logica.statistics.WordStatistics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
            // Attempts are transient, so an unfinished Crowns entry is simply open again.
            val content = viewModel(dailyRepository).awaitContent()

            assertEquals(2, content.totalCount)
            assertEquals(1, content.completedCount)
            assertEquals(
                listOf(
                    TodayEntryUiState(PuzzleType.BALANCE, balance.difficulty, DailyEntryState.COMPLETED),
                    TodayEntryUiState(PuzzleType.CROWNS, crowns.difficulty, DailyEntryState.AVAILABLE),
                ),
                content.entries,
            )
        }

    @Test
    fun newRunsUseV5WhilePersistedOlderRunsKeepTheirOriginalEntries() =
        runBlocking {
            val fresh = viewModel(FakeDailyChallengeRepository()).awaitContent()

            assertEquals(DailyChallengePolicyV5.VERSION, fresh.definition.policyVersion)
            assertEquals(
                listOf(
                    PuzzleType.BALANCE,
                    PuzzleType.CROWNS,
                    PuzzleType.WORD,
                    PuzzleType.SUDOKU,
                    PuzzleType.GAME_2048,
                ),
                fresh.entries.map { it.puzzleType },
            )
            assertEquals(0, fresh.completedCount)
            assertEquals(5, fresh.totalCount)
            assertEquals(
                GeneratorVersion(2),
                fresh.definition.entries
                    .single { it.puzzleType == PuzzleType.WORD }
                    .generatorVersion,
            )

            // A run created before V5 keeps its own policy: it is never extended to five entries.
            val v4Definition = DailyChallengePolicyV4.definitionFor(date)
            val persistedV4 =
                viewModel(
                    FakeDailyChallengeRepository(
                        run = savedRun(DailyChallengePolicyV4.VERSION.value, DailyRunStatus.IN_PROGRESS),
                        entries =
                            v4Definition.entries.associate { entry ->
                                entry.puzzleType to v4Definition.savedChallenge(entry, DailyChallengeStatus.IN_PROGRESS)
                            },
                    ),
                ).awaitContent()

            assertEquals(DailyChallengePolicyV4.VERSION, persistedV4.definition.policyVersion)
            assertEquals(3, persistedV4.totalCount)
            assertEquals(
                listOf(PuzzleType.BALANCE, PuzzleType.CROWNS, PuzzleType.WORD),
                persistedV4.entries.map { it.puzzleType },
            )
            // The historical streak rule travels with the policy version, not with the build.
            assertFalse(persistedV4.streak.anySolvedQualifies)
            assertTrue(fresh.streak.anySolvedQualifies)

            val v1Definition = DailyChallengePolicyV1.definitionFor(date)
            val v1Entry = v1Definition.entryFor(PuzzleType.BALANCE)
            val legacyV1 =
                viewModel(
                    FakeDailyChallengeRepository(
                        run = savedRun(DailyChallengePolicyV1.VERSION.value, DailyRunStatus.IN_PROGRESS),
                        entries =
                            mapOf(
                                PuzzleType.BALANCE to
                                    v1Definition.savedChallenge(v1Entry, DailyChallengeStatus.IN_PROGRESS),
                            ),
                    ),
                ).awaitContent()

            assertEquals(DailyChallengePolicyV1.VERSION, legacyV1.definition.policyVersion)
            assertEquals(
                listOf(TodayEntryUiState(PuzzleType.BALANCE, v1Entry.difficulty, DailyEntryState.AVAILABLE)),
                legacyV1.entries,
            )
            assertEquals(1, legacyV1.totalCount)

            val v2Definition = DailyChallengePolicyV2.definitionFor(date)
            val legacyV2 =
                viewModel(
                    FakeDailyChallengeRepository(
                        run = savedRun(DailyChallengePolicyV2.VERSION.value, DailyRunStatus.IN_PROGRESS),
                        entries =
                            v2Definition.entries.associate { entry ->
                                entry.puzzleType to
                                    v2Definition.savedChallenge(entry, DailyChallengeStatus.IN_PROGRESS)
                            },
                    ),
                ).awaitContent()

            assertEquals(DailyChallengePolicyV2.VERSION, legacyV2.definition.policyVersion)
            assertEquals(2, legacyV2.totalCount)
            assertEquals(listOf(PuzzleType.BALANCE, PuzzleType.CROWNS), legacyV2.entries.map { it.puzzleType })
        }

    @Test
    fun aFailedWordEntryStillCompletesTheV3RunAtThreeOfThree() =
        runBlocking {
            val definition = DailyChallengePolicyV3.definitionFor(date)
            // A Word game that ended FAILED persists a terminal result, so its Daily entry is COMPLETED
            // exactly like the two solved puzzles: the run must not be stuck at 2/3.
            val content =
                viewModel(
                    FakeDailyChallengeRepository(
                        run = savedRun(DailyChallengePolicyV3.VERSION.value, DailyRunStatus.COMPLETED),
                        entries =
                            definition.entries.associate { entry ->
                                entry.puzzleType to definition.savedChallenge(entry, DailyChallengeStatus.COMPLETED)
                            },
                    ),
                ).awaitContent()

            assertEquals(DailyRunStatus.COMPLETED, content.runStatus)
            assertEquals(3, content.totalCount)
            assertEquals(3, content.completedCount)
            assertEquals(
                List(3) { DailyEntryState.COMPLETED },
                content.entries.map { it.state },
            )
        }

    @Test
    fun startKeepsContentAndEmitsOneLaunchWhileRunCreationIsPending() =
        runBlocking {
            val createdRun = CompletableDeferred<SavedDailyRun>()
            var createCalls = 0
            val dailyRepository =
                FakeDailyChallengeRepository(
                    onCreateRun = {
                        createCalls += 1
                        createdRun.await()
                    },
                )
            val todayViewModel = viewModel(dailyRepository)
            val content = todayViewModel.awaitContent()
            val launch =
                async(start = CoroutineStart.UNDISPATCHED) {
                    todayViewModel.launches.first()
                }

            todayViewModel.start(PuzzleType.BALANCE)
            todayViewModel.start(PuzzleType.BALANCE)

            assertEquals(1, createCalls)
            assertEquals(content, todayViewModel.uiState.value)

            createdRun.complete(savedRun(DailyChallengePolicyV5.VERSION.value, DailyRunStatus.IN_PROGRESS))

            assertEquals(PuzzleType.BALANCE, launch.await().launch.puzzleType)
        }

    // Construction loads nothing: the hub route is the one owner of the initial refresh, so the tests
    // ask for it the same way the route does.
    private fun viewModel(dailyChallengeRepository: DailyChallengeRepository): TodayViewModel =
        TodayViewModel(
            dailyChallengeRepository = dailyChallengeRepository,
            statisticsRepository = EmptyStatisticsRepository,
            dailyResultRepository = EmptyDailyResultRepository,
            dateProvider = { date },
            definitionProvider = DailyChallengePolicyResolver::definitionFor,
        ).also { it.refresh() }

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

    private class FakeDailyChallengeRepository(
        private val run: SavedDailyRun? = null,
        private val entries: Map<PuzzleType, SavedDailyChallenge> = emptyMap(),
        private val onCreateRun: suspend (DailyChallengeDefinition) -> SavedDailyRun = { error("Unused.") },
    ) : DailyChallengeRepository {
        override suspend fun read(
            challengeDate: LocalDate,
            puzzleType: PuzzleType,
        ): SavedDailyChallenge? = entries[puzzleType]?.takeIf { it.challengeDate == challengeDate }

        override suspend fun readRun(challengeDate: LocalDate): SavedDailyRun? = run?.takeIf { it.challengeDate == challengeDate }

        override suspend fun createRun(definition: DailyChallengeDefinition): SavedDailyRun = onCreateRun(definition)
    }

    private object EmptyStatisticsRepository : StatisticsRepository {
        override fun observe(currentDate: LocalDate): Flow<StatisticsSnapshot> =
            flowOf(
                StatisticsSnapshot(
                    statistics = GameStatistics(0, 0, 0, 0, 0, emptyMap(), WordStatistics(0, 0, 0, emptyMap())),
                    dailyHintsUsedByDate = emptyMap(),
                ),
            )
    }

    private object EmptyDailyResultRepository : DailyResultRepository {
        override suspend fun readResults(
            challengeDate: LocalDate,
            policyVersion: DailyPolicyVersion,
        ): List<GameResult> = emptyList()
    }
}
