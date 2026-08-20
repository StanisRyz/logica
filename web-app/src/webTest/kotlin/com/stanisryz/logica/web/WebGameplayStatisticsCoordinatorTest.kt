package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebGameplayStatisticsCoordinatorTest {
    @Test
    fun terminalAttemptsRecordOnceAndFreshRetriesRemainIndependent() {
        val repository = repository("device-a-00000001")
        val session = FakeStatisticsSession(repository, WebPlayerContextToken(1L))
        val coordinator = WebGameplayStatisticsCoordinator(session)

        val solved = coordinator.startAttempt(PuzzleType.BALANCE, Difficulty.HARD)
        assertIs<WebStatisticsAttemptRecordResult.Recorded>(
            coordinator.recordTerminalResult(solved, WebStatisticsTerminalOutcome.SOLVED, hintsUsed = 2),
        )
        assertIs<WebStatisticsAttemptRecordResult.AlreadyRecorded>(
            coordinator.recordTerminalResult(solved, WebStatisticsTerminalOutcome.SOLVED, hintsUsed = 2),
        )

        val retry = coordinator.startAttempt(PuzzleType.BALANCE, Difficulty.HARD)
        assertIs<WebStatisticsAttemptRecordResult.Recorded>(
            coordinator.recordTerminalResult(retry, WebStatisticsTerminalOutcome.FAILED, hintsUsed = 1),
        )

        val wordSolved = coordinator.startAttempt(PuzzleType.WORD, Difficulty.MEDIUM)
        assertIs<WebStatisticsAttemptRecordResult.Recorded>(
            coordinator.recordTerminalResult(
                wordSolved,
                WebStatisticsTerminalOutcome.SOLVED,
                wordAttemptsUsed = 3,
            ),
        )
        val wordFailed = coordinator.startAttempt(PuzzleType.WORD, Difficulty.MEDIUM)
        assertIs<WebStatisticsAttemptRecordResult.Recorded>(
            coordinator.recordTerminalResult(wordFailed, WebStatisticsTerminalOutcome.FAILED),
        )

        val balance = repository.aggregate().totals(PuzzleType.BALANCE, Difficulty.HARD)
        assertEquals(WebStatisticsCounters(played = 2L, solved = 1L, failed = 1L, hints = 3L), balance)
        val word = repository.aggregate().totals(PuzzleType.WORD, Difficulty.MEDIUM)
        assertEquals(2L, word.played)
        assertEquals(1L, word.solved)
        assertEquals(1L, word.failed)
        assertEquals(mapOf(3 to 1L), word.wordSolvedAttempts)
        assertEquals(4, session.cloudSyncRequests)
    }

    @Test
    fun stalePlayerAttemptCannotWriteIntoReplacementBinding() {
        val repositoryA = repository("device-a-00000001")
        val session = FakeStatisticsSession(repositoryA, WebPlayerContextToken(1L))
        val coordinator = WebGameplayStatisticsCoordinator(session)
        val staleAttempt = coordinator.startAttempt(PuzzleType.SUDOKU, Difficulty.EASY)

        val repositoryB = repository("device-b-00000002")
        session.bind(repositoryB, WebPlayerContextToken(2L))

        assertIs<WebStatisticsAttemptRecordResult.ContextChanged>(
            coordinator.recordTerminalResult(staleAttempt, WebStatisticsTerminalOutcome.SOLVED, hintsUsed = 1),
        )
        assertEquals(0L, repositoryA.aggregate().totals().played)
        assertEquals(0L, repositoryB.aggregate().totals().played)
        assertEquals(0, session.cloudSyncRequests)
    }

    private fun repository(installationId: String): WebStatisticsRepository =
        WebStatisticsRepository(
            scope = WebCatalogProgressScope.STANDALONE,
            installationId = installationId,
            localStore = FakeStatisticsStore(),
        ).also(WebStatisticsRepository::loadLocal)

    private class FakeStatisticsStore : WebStatisticsStore {
        private var snapshot = WebStatisticsSnapshot.EMPTY

        override fun load(): WebStatisticsSnapshot = snapshot

        override fun save(snapshot: WebStatisticsSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeStatisticsSession(
        repository: WebStatisticsRepository,
        token: WebPlayerContextToken,
    ) : WebStatisticsSessionAccess {
        private val mutableBinding = MutableStateFlow<WebStatisticsBinding>(ready(repository, token))

        override val statisticsBinding: StateFlow<WebStatisticsBinding> = mutableBinding

        var cloudSyncRequests = 0
            private set

        fun bind(
            repository: WebStatisticsRepository,
            token: WebPlayerContextToken,
        ) {
            mutableBinding.value = ready(repository, token)
        }

        override fun requestStatisticsCloudSynchronization(binding: WebStatisticsBinding.Ready) {
            cloudSyncRequests += 1
        }

        private companion object {
            fun ready(
                repository: WebStatisticsRepository,
                token: WebPlayerContextToken,
            ): WebStatisticsBinding.Ready =
                WebStatisticsBinding.Ready(
                    token = token,
                    repository = repository,
                    identity = null,
                    syncStatus = WebStatisticsCloudSyncStatus.LOCAL_ONLY,
                )
        }
    }
}
