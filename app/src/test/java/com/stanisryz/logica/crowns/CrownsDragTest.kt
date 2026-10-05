package com.stanisryz.logica.crowns

import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.FrozenLevelRepository
import com.stanisryz.logica.economy.GatedHintWallet
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
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

/** A drag with the X tool draws or wipes X notes along its path through the ordinary tap path. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrownsDragTest {
    private val work = StandardTestDispatcher()
    private val levels = FrozenLevelRepository(PuzzleType.CROWNS)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(work.scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aStrokePutsAnXOnEveryCellItPasses() {
        val viewModel = loadedViewModel()
        viewModel.selectValue(CrownsPlayerCell.MARKED)

        stroke(viewModel, row(0, 1, 2))

        row(0, 1, 2).forEach { assertEquals(CrownsPlayerCell.MARKED, viewModel.ready().game.cellAt(it)) }
        assertEquals(0, viewModel.ready().game.mistakesUsed)
    }

    @Test
    fun aStrokeStartingOnAnXWipesTheXs() {
        val viewModel = loadedViewModel()
        viewModel.selectValue(CrownsPlayerCell.MARKED)
        stroke(viewModel, row(0, 1, 2))

        stroke(viewModel, row(0, 1))

        assertEquals(CrownsPlayerCell.EMPTY, viewModel.ready().game.cellAt(CrownsPosition(0, 0)))
        assertEquals(CrownsPlayerCell.EMPTY, viewModel.ready().game.cellAt(CrownsPosition(0, 1)))
        assertEquals(CrownsPlayerCell.MARKED, viewModel.ready().game.cellAt(CrownsPosition(0, 2)))
    }

    @Test
    fun aStrokeWithTheCrownToolIsATapOnItsFirstCell() {
        val viewModel = loadedViewModel()

        stroke(viewModel, row(0, 1, 2))

        assertEquals(CrownsPlayerCell.CROWN, viewModel.ready().game.cellAt(CrownsPosition(0, 0)))
        assertEquals(CrownsPlayerCell.EMPTY, viewModel.ready().game.cellAt(CrownsPosition(0, 1)))
        assertEquals(CrownsPlayerCell.EMPTY, viewModel.ready().game.cellAt(CrownsPosition(0, 2)))
    }

    private fun stroke(
        viewModel: CrownsGameViewModel,
        path: List<CrownsPosition>,
    ) {
        viewModel.onDragStart(path.first())
        path.drop(1).forEach(viewModel::onDragCell)
        viewModel.onDragEnd()
    }

    private fun row(vararg columns: Int): List<CrownsPosition> = columns.map { CrownsPosition(0, it) }

    private fun loadedViewModel(): CrownsGameViewModel {
        val viewModel =
            CrownsGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels) { "attempt" },
                completionRepository = RecordingCompletionRepository(),
                economyRepository = GatedHintWallet(),
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun CrownsGameViewModel.ready(): CrownsGameUiState.Ready = uiState.value as CrownsGameUiState.Ready
}
