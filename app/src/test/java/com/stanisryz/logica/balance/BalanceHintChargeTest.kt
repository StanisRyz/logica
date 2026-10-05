package com.stanisryz.logica.balance

import com.stanisryz.logica.catalog.CatalogLevelRepository
import com.stanisryz.logica.catalog.GameAttemptFactory
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyHintPurchase
import com.stanisryz.logica.economy.EconomyHintUse
import com.stanisryz.logica.economy.EconomyRefill
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRewardedLife
import com.stanisryz.logica.economy.HintOffer
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.puzzle.core.balance.BalanceCellStatus
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val wallet = CountingWallet()

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

    private fun loadedViewModel(): BalanceGameViewModel {
        val viewModel =
            BalanceGameViewModel(
                launch = GameAttemptLaunch.Level(LEVEL),
                attemptFactory = GameAttemptFactory(FrozenLevel) { "attempt" },
                completionRepository = UnusedCompletions,
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

    private class CountingWallet : EconomyRepository {
        val gate = CompletableDeferred<Unit>()
        var consumeCalls = 0
            private set

        override fun observe(): Flow<PlayerEconomy> = MutableStateFlow(PlayerEconomy())

        override suspend fun refresh(): PlayerEconomy = PlayerEconomy()

        override suspend fun consumeHint(actionId: String): EconomyHintUse {
            consumeCalls += 1
            gate.await()
            return EconomyHintUse.Used(PlayerEconomy(hints = PlayerEconomy().hints - 1))
        }

        override suspend fun refillLifeWithGems(actionId: String): EconomyRefill = error("Unused")

        override suspend fun buyHintsWithGems(
            actionId: String,
            offer: HintOffer,
        ): EconomyHintPurchase = error("Unused")

        override suspend fun spendLifeForAbandonedAttempt(actionId: String) = Unit

        override suspend fun grantRewardedGem(actionId: String): Boolean = error("Unused")

        override suspend fun grantRewardedLife(actionId: String): EconomyRewardedLife = error("Unused")

        override suspend fun grantPurchasedGems(
            purchaseId: String,
            productId: String,
        ): EconomyGemPurchase = error("Unused")
    }

    private object FrozenLevel : CatalogLevelRepository {
        override val packVersion = CatalogLevelPackVersion.V1

        override fun observeCurrentLevel(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): Flow<CatalogLevelNumber> = MutableStateFlow(CatalogLevelNumber(1))

        override fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>> = MutableStateFlow(emptyMap())

        override suspend fun currentLevelId(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): CatalogLevelId = LEVEL

        override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition =
            CatalogLevelDefinition(levelId, PuzzleSeed(7L), GeneratorVersion(1))
    }

    private object UnusedCompletions : GameCompletionRepository {
        override suspend fun complete(completion: GameCompletion): GameResult = error("Unused")
    }

    private companion object {
        val LEVEL = CatalogLevelId(PuzzleType.BALANCE, Difficulty.MEDIUM, CatalogLevelNumber(1), CatalogLevelPackVersion.V1)
    }
}
