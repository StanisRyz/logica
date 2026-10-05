package com.stanisryz.logica.balance

import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.FrozenLevelRepository
import com.stanisryz.logica.economy.GatedHintWallet
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalanceSolver
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/** A hint is computed first and only then charged and shown, as one step that a board change cannot split. */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceHintChargeTest {
    private val work = StandardTestDispatcher()
    private val wallet = GatedHintWallet()
    private val levels = FrozenLevelRepository(PuzzleType.BALANCE)
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
    fun aBoardChangeDuringTheComputationChargesNothing() {
        val viewModel = loadedViewModel()
        viewModel.requestHint()
        // The hint is still being computed when the player places a value.
        viewModel.onCellTapped(firstEmptyCell(viewModel))
        val afterMove = viewModel.ready().game

        work.scheduler.advanceUntilIdle()

        assertEquals(0, wallet.consumeCalls)
        assertEquals(afterMove, viewModel.ready().game)
        assertFalse(viewModel.ready().isHintLoading)
    }

    @Test
    fun anUnchangedBoardIsChargedOnceAndShown() {
        val viewModel = loadedViewModel()
        val before = viewModel.ready().game
        wallet.gate.complete(Unit)

        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()

        assertEquals(1, wallet.consumeCalls)
        assertNotEquals(before, viewModel.ready().game)
        assertEquals(1, viewModel.ready().game.hintsUsed)
    }

    @Test
    fun boardInputWaitsWhileTheHintIsBeingCharged() {
        val viewModel = loadedViewModel()
        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()
        // Computed and now charging: the wallet has not answered yet.
        assertEquals(1, wallet.consumeCalls)
        val charging = viewModel.ready().game

        viewModel.onCellTapped(firstEmptyCell(viewModel))
        assertEquals(charging, viewModel.ready().game)

        wallet.gate.complete(Unit)
        work.scheduler.advanceUntilIdle()

        // The charged hint is the one shown, and it was charged exactly once.
        assertEquals(1, wallet.consumeCalls)
        assertEquals(1, viewModel.ready().game.hintsUsed)
        assertFalse(viewModel.ready().isHintLoading)
    }

    @Test
    fun aHintThatOpensTheLastCellRecordsTheSolvedLevel() {
        val viewModel = loadedViewModel()
        val ready = viewModel.ready()
        val solution = checkNotNull(BalanceSolver().solve(ready.puzzle))
        val size = ready.puzzle.size
        val empty =
            (0 until size * size)
                .map { BalancePosition(it / size, it % size) }
                .filter { ready.game.statusAt(it) == BalanceCellStatus.EMPTY }
        // Every empty cell but the last gets its correct value; the hint opens that last one.
        empty.dropLast(1).forEach { position ->
            viewModel.selectValue(solution.cellAt(position))
            viewModel.onCellTapped(position)
        }
        wallet.gate.complete(Unit)

        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()

        assertEquals(BalanceGameStatus.SOLVED, viewModel.ready().game.status)
        assertEquals(GameOutcome.SOLVED, completions.completions.single().outcome)
        assertEquals(CompletionPersistence.Saved, viewModel.ready().completionPersistence)
    }

    private fun loadedViewModel(): BalanceGameViewModel {
        val viewModel =
            BalanceGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels) { "attempt" },
                completionRepository = completions,
                economyRepository = wallet,
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun BalanceGameViewModel.ready(): BalanceGameUiState.Ready = uiState.value as BalanceGameUiState.Ready

    private fun firstEmptyCell(viewModel: BalanceGameViewModel): BalancePosition {
        val ready = viewModel.ready()
        val size = ready.puzzle.size
        return (0 until size * size)
            .map { BalancePosition(it / size, it % size) }
            .first { ready.game.statusAt(it) == BalanceCellStatus.EMPTY }
    }
}
