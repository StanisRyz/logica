package com.stanisryz.logica.crowns

import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.FrozenLevelRepository
import com.stanisryz.logica.economy.GatedHintWallet
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsSolver
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.CompletionPersistence
import com.stanisryz.logica.result.GameOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A hint that places the last crown finishes the attempt like a move does: the solved result is recorded. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrownsHintFinishTest {
    private val work = StandardTestDispatcher()
    private val wallet = GatedHintWallet()
    private val levels = FrozenLevelRepository(PuzzleType.CROWNS)
    private val completions = RecordingCompletionRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(work.scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aHintThatPlacesTheLastCrownRecordsTheSolvedLevel() {
        val viewModel =
            CrownsGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels) { "attempt" },
                completionRepository = completions,
                economyRepository = wallet,
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        val ready = viewModel.uiState.value as CrownsGameUiState.Ready
        val crowns = checkNotNull(CrownsSolver().solve(ready.puzzle)).crowns.toList()
        // Every crown but one is placed (the crown tool is selected by default); the hint places the last.
        crowns.dropLast(1).forEach(viewModel::onCellTapped)
        wallet.gate.complete(Unit)

        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()

        val finished = viewModel.uiState.value as CrownsGameUiState.Ready
        assertEquals(CrownsGameStatus.SOLVED, finished.game.status)
        assertEquals(1, finished.game.hintsUsed)
        assertEquals(GameOutcome.SOLVED, completions.completions.single().outcome)
        assertEquals(CompletionPersistence.Saved, finished.completionPersistence)
    }
}
