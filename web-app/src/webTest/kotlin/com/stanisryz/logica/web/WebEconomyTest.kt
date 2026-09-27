package com.stanisryz.logica.web

import com.stanisryz.logica.platform.EconomyConsumptionType
import com.stanisryz.logica.platform.EconomyEvent
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.EconomyRewardType
import com.stanisryz.logica.platform.EconomyState
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 45.9 regressions: Player-scoped economy storage never leaks across Players, and the
 * reward pipeline grants difficulty gem rewards / consumes failure lives exactly as specified.
 */
class WebEconomyTest {
    /** Deterministic in-memory store standing in for the browser-local Player-scoped storage. */
    private class FakeEconomyStore : WebEconomyStore {
        var snapshot: WebEconomySnapshot = WebEconomySnapshot.DEFAULT

        override fun load(): WebEconomySnapshot = snapshot

        override fun save(snapshot: WebEconomySnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeEconomySession : WebEconomySessionAccess {
        override val economyBinding =
            MutableStateFlow<WebEconomyBinding>(WebEconomyBinding.Loading)
    }

    private fun repository(store: FakeEconomyStore): WebPlayerEconomyRepository =
        WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, store).also { it.loadLocal() }

    @Test
    fun playerScopedWalletsStayIsolatedAndPersistWithinTheirOwnScope() {
        val storeA = FakeEconomyStore()
        val storeB = FakeEconomyStore()
        val playerA = repository(storeA)
        val playerB = repository(storeB)

        // Player A earns and spends; Player B must stay at the untouched starting wallet.
        assertTrue(playerA.addGems(100))
        assertTrue(playerA.spendGems(40))
        assertEquals(60, playerA.state.value.gems)
        assertEquals(EconomyPolicy.STARTING_GEMS, playerB.state.value.gems)

        // Reloading the same scope restores exactly that Player's durable wallet...
        val reloadedA = repository(storeA)
        assertEquals(60, reloadedA.state.value.gems)
        // ...and reloading the other scope still shows no leakage from Player A.
        val reloadedB = repository(storeB)
        assertEquals(EconomyPolicy.STARTING_GEMS, reloadedB.state.value.gems)

        // The gameplay seam writes only into whichever Player context is currently bound.
        val session = FakeEconomySession()
        val coordinator = WebGameplayEconomyCoordinator(session)
        session.economyBinding.value =
            WebEconomyBinding.Ready(WebPlayerContextToken(1L), playerB, null)
        coordinator.recordCatalogTerminalResult(PuzzleType.BALANCE, Difficulty.MEDIUM, solved = true)
        assertEquals(2, playerB.state.value.gems)
        assertEquals(60, playerA.state.value.gems)
    }

    @Test
    fun rewardPipelineGrantsDifficultyGemsAndConsumesFailureLivesWithoutGoingNegative() {
        val repository = repository(FakeEconomyStore())

        // Solved Medium Catalog puzzle -> +2 gems through the processor.
        val solvedEvents = repository.applyCatalogTerminalResult(PuzzleType.BALANCE, Difficulty.MEDIUM, solved = true)
        assertEquals(2, repository.state.value.gems)
        assertEquals(EconomyPolicy.STARTING_LIVES, repository.state.value.lives)
        assertEquals(EconomyEvent.GameCompleted, solvedEvents[0])
        val granted = assertIs<EconomyEvent.RewardGranted>(solvedEvents[1])
        assertEquals(EconomyRewardType.GEMS, granted.type)
        assertEquals(2, granted.amount)

        // Failed Catalog puzzle -> -1 life, floored at zero.
        val failedEvents = repository.applyCatalogTerminalResult(PuzzleType.WORD, Difficulty.EXPERT, solved = false)
        assertEquals(4, repository.state.value.lives)
        assertEquals(EconomyEvent.GameFailed, failedEvents[0])
        val consumed = assertIs<EconomyEvent.ResourceConsumed>(failedEvents[1])
        assertEquals(EconomyConsumptionType.LIFE, consumed.type)
        assertEquals(1, consumed.amount)

        repeat(EconomyPolicy.MAXIMUM_LIVES) {
            repository.consumeLife()
        }
        assertEquals(0, repository.state.value.lives)
        assertFalse(repository.consumeLife())

        // Spending beyond the balance is rejected; an exact balance spends cleanly.
        assertTrue(repository.spendGems(2))
        assertEquals(0, repository.state.value.gems)
        assertFalse(repository.spendGems(1))
    }

    @Test
    fun livesRegenerateEveryIntervalWithoutRestartingARunningCountdown() {
        var now = START
        val repository =
            WebPlayerEconomyRepository(WebCatalogProgressScope.STANDALONE, FakeEconomyStore(), currentTimeMs = { now })
                .also { it.loadLocal() }

        // The first loss starts the countdown; a second loss later keeps it running.
        repository.applyCatalogTerminalResult(PuzzleType.SUDOKU, Difficulty.EASY, solved = false)
        assertEquals(START + INTERVAL, repository.state.value.nextLifeRestoreAtEpochMs)
        now = START + 5 * MINUTE
        repository.applyCatalogTerminalResult(PuzzleType.SUDOKU, Difficulty.EASY, solved = false)
        assertEquals(3, repository.state.value.lives)
        assertEquals(START + INTERVAL, repository.state.value.nextLifeRestoreAtEpochMs)

        // One second short of the interval nothing has come back yet.
        now = START + INTERVAL - 1_000
        repository.refresh()
        assertEquals(3, repository.state.value.lives)

        // Reaching the interval restores one life and keeps counting toward the next one.
        now = START + INTERVAL
        repository.refresh()
        assertEquals(4, repository.state.value.lives)
        assertEquals(START + 2 * INTERVAL, repository.state.value.nextLifeRestoreAtEpochMs)

        // A long gap stops at the cap and clears the countdown.
        now = START + 10 * INTERVAL
        repository.refresh()
        assertEquals(EconomyPolicy.MAXIMUM_LIVES, repository.state.value.lives)
        assertNull(repository.state.value.nextLifeRestoreAtEpochMs)
    }

    @Test
    fun regenerationRepairsOldSavesAndIgnoresABackwardsClock() {
        // Saves written before regeneration existed hold a missing life without a countdown.
        val legacy = WebEconomyProcessor.regenerated(EconomyState(gems = 0, lives = 2, nextLifeRestoreAtEpochMs = null), START)
        assertEquals(START + INTERVAL, legacy.nextLifeRestoreAtEpochMs)
        assertEquals(2, legacy.lives)

        val running = EconomyState(gems = 0, lives = 4, nextLifeRestoreAtEpochMs = START + INTERVAL)
        assertEquals(running, WebEconomyProcessor.regenerated(running, START + MINUTE))
        val movedBackADay = START - 24 * 60 * MINUTE
        assertEquals(movedBackADay + INTERVAL, WebEconomyProcessor.regenerated(running, movedBackADay).nextLifeRestoreAtEpochMs)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val MINUTE = 60_000L
        const val INTERVAL = EconomyPolicy.LIFE_RESTORE_INTERVAL_MS
    }
}
