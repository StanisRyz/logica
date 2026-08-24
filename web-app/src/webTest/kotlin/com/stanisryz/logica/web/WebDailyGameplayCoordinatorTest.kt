package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebDailyGameplayCoordinatorTest {
    private val today = DailyDate(2026, 8, 24)

    private class FakeDailyStore : WebDailyStore {
        var snapshot: WebDailySnapshotV1 = WebDailySnapshotV1.EMPTY
        var saveCount = 0

        override fun load(): WebDailySnapshotV1 = snapshot

        override fun save(snapshot: WebDailySnapshotV1) {
            this.snapshot = snapshot
            saveCount += 1
        }
    }

    private class FakeSessionAccess : WebDailySessionAccess {
        override val dailyBinding = MutableStateFlow<WebDailyBinding>(WebDailyBinding.Loading)
        var cloudSyncRequests = 0

        override fun requestDailyCloudSynchronization(binding: WebDailyBinding.Ready) {
            cloudSyncRequests += 1
        }
    }

    private class RecordingStatistics : WebGameplayStatistics {
        private var nextAttemptIdentity = 0L
        private var started = 0
        val outcomes = mutableListOf<WebStatisticsTerminalOutcome>()

        override fun startAttempt(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): WebStatisticsAttempt {
            started += 1
            return DisabledWebGameplayStatistics.startAttempt(puzzleType, difficulty)
        }

        override fun recordTerminalResult(
            attempt: WebStatisticsAttempt,
            outcome: WebStatisticsTerminalOutcome,
            hintsUsed: Int,
            wordAttemptsUsed: Int?,
        ): WebStatisticsAttemptRecordResult {
            attempt.markRecorded()
            outcomes += outcome
            return WebStatisticsAttemptRecordResult.Recorded
        }

        fun startedCount(): Int = started
    }

    private fun readyRepository(store: FakeDailyStore): WebDailyRepository =
        WebDailyRepository(WebCatalogProgressScope.STANDALONE, store) { today }.also { it.loadLocal() }

    private fun readySession(
        repository: WebDailyRepository,
        tokenValue: Long = 7L,
    ): FakeSessionAccess =
        FakeSessionAccess().also {
            it.dailyBinding.value =
                WebDailyBinding.Ready(
                    token = WebPlayerContextToken(tokenValue),
                    repository = repository,
                    identity = null,
                    syncStatus = WebDailyCloudSyncStatus.LOCAL_ONLY,
                )
        }

    @Test
    fun dailyLifecycleEnsuresRunThenFailureRetrySolvedCompletionCountsTwoAttempts() {
        val store = FakeDailyStore()
        val repository = readyRepository(store)
        val session = readySession(repository)
        val coordinator = WebDailyGameplayCoordinator(session) { today }
        val statistics = RecordingStatistics()

        // AVAILABLE -> an actual start creates the durable run before gameplay may begin...
        val first = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.BALANCE))
        assertEquals(1, store.saveCount)
        assertTrue(repository.stateFor(today).isDurable)
        assertEquals(WebDailyEntryState.AVAILABLE, repository.stateFor(today).entries[PuzzleType.BALANCE])
        assertEquals(1, session.cloudSyncRequests)

        // ...and each fresh engine start receives its own transient statistics identity.
        var statsAttempt = statistics.startAttempt(PuzzleType.BALANCE, Difficulty.MEDIUM)

        // FAILED -> RETRY, never a completion. A terminal attempt records Statistics and Daily
        // through the two separate seams exactly as the shared controllers do.
        assertEquals(
            WebStatisticsAttemptRecordResult.Recorded,
            statistics.recordTerminalResult(statsAttempt, WebStatisticsTerminalOutcome.FAILED),
        )
        assertIs<WebDailyRecordResult.Recorded>(
            coordinator.recordTerminalResult(first.attempt, WebStatisticsTerminalOutcome.FAILED),
        )
        assertEquals(WebDailyEntryState.RETRY, repository.stateFor(today).entries[PuzzleType.BALANCE])
        assertFalse(repository.stateFor(today).fullyCompleted)
        assertFalse(repository.stateFor(today).qualifiedForStreak)

        // Retry of the same entry: same deterministic definition, durable run reused.
        val second = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.BALANCE))
        assertEquals(first.attempt.definition, second.attempt.definition)
        statsAttempt = statistics.startAttempt(PuzzleType.BALANCE, Difficulty.MEDIUM)

        // SOLVED retry -> COMPLETED and V5 streak qualification, still not a full completion.
        assertEquals(
            WebStatisticsAttemptRecordResult.Recorded,
            statistics.recordTerminalResult(statsAttempt, WebStatisticsTerminalOutcome.SOLVED),
        )
        assertIs<WebDailyRecordResult.Recorded>(
            coordinator.recordTerminalResult(second.attempt, WebStatisticsTerminalOutcome.SOLVED),
        )
        assertEquals(WebDailyEntryState.COMPLETED, repository.stateFor(today).entries[PuzzleType.BALANCE])
        assertTrue(repository.stateFor(today).qualifiedForStreak)
        assertFalse(repository.stateFor(today).fullyCompleted)
        assertEquals(2, statistics.startedCount())
        assertEquals(
            listOf(WebStatisticsTerminalOutcome.FAILED, WebStatisticsTerminalOutcome.SOLVED),
            statistics.outcomes,
        )
    }

    @Test
    fun stalePlayerContextIsRejectedAndNeverWritesIntoNewPlayerScope() {
        val storeA = FakeDailyStore()
        val repositoryA = readyRepository(storeA)
        val storeB = FakeDailyStore()
        val repositoryB = readyRepository(storeB)
        val session = FakeSessionAccess().also {
            it.dailyBinding.value =
                WebDailyBinding.Ready(
                    token = WebPlayerContextToken(7L),
                    repository = repositoryA,
                    identity = null,
                    syncStatus = WebDailyCloudSyncStatus.LOCAL_ONLY,
                )
        }
        val coordinator = WebDailyGameplayCoordinator(session) { today }

        val stale = assertIs<WebDailyStartResult.Started>(coordinator.start(PuzzleType.WORD))
        assertTrue(repositoryA.stateFor(today).isDurable)

        // The account context changes: Player B becomes current with their own isolated scope.
        session.dailyBinding.value =
            WebDailyBinding.Ready(
                token = WebPlayerContextToken(8L),
                repository = repositoryB,
                identity = null,
                syncStatus = WebDailyCloudSyncStatus.LOCAL_ONLY,
            )

        assertEquals(
            WebDailyRecordResult.StaleContext,
            coordinator.recordTerminalResult(stale.attempt, WebStatisticsTerminalOutcome.SOLVED, wordAttemptsUsed = 3),
        )
        assertFalse(repositoryB.stateFor(today).isDurable)
        assertEquals(0, storeB.saveCount)
        assertEquals(WebDailyEntryState.AVAILABLE, repositoryB.stateFor(today).entries[PuzzleType.WORD])
    }
}
