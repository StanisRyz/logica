package com.stanisryz.logica.balance

import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.FrozenLevelRepository
import com.stanisryz.logica.economy.GatedHintWallet
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalanceSolver
import com.stanisryz.logica.puzzle.core.model.PuzzleType
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

/** A Balance drag places the selected piece along its path and stops at its first mistake. */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceDragTest {
    private val work = StandardTestDispatcher()
    private val levels = FrozenLevelRepository(PuzzleType.BALANCE)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(work.scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aStrokeStopsAtItsFirstMistakeAndCostsExactlyOne() {
        val viewModel = loadedViewModel()
        val ready = viewModel.uiState.value as BalanceGameUiState.Ready
        val solution = checkNotNull(BalanceSolver().solve(ready.puzzle))
        val size = ready.puzzle.size
        val empty =
            (0 until size * size)
                .map { BalancePosition(it / size, it % size) }
                .filter { ready.game.statusAt(it) == BalanceCellStatus.EMPTY }
        val value = solution.cellAt(empty.first())
        val wrong = empty.filter { solution.cellAt(it) != value }.take(2)
        viewModel.selectValue(value)

        viewModel.onDragStart(empty.first())
        wrong.forEach(viewModel::onDragCell)
        viewModel.onDragEnd()

        val game = (viewModel.uiState.value as BalanceGameUiState.Ready).game
        assertEquals(BalanceCellStatus.CORRECT, game.statusAt(empty.first()))
        assertEquals(BalanceCellStatus.INCORRECT, game.statusAt(wrong[0]))
        assertEquals(BalanceCell.EMPTY, game.board.cellAt(wrong[1]))
        assertEquals(1, game.mistakesUsed)
    }

    private fun loadedViewModel(): BalanceGameViewModel {
        val viewModel =
            BalanceGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels) { "attempt" },
                completionRepository = RecordingCompletionRepository(),
                economyRepository = GatedHintWallet(),
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        return viewModel
    }
}
