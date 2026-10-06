package com.stanisryz.logica.word

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
import com.stanisryz.logica.economy.RecordingCompletionRepository
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelDefinition
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.word.WordLanguage
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A Word hint costs exactly one hint from the stock; an empty stock opens nothing and offers to restock. */
@OptIn(ExperimentalCoroutinesApi::class)
class WordHintChargeTest {
    private val work = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(work.scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aHintCostsOneHintAndOpensTheFirstLetter() {
        val wallet = HintStock(3)
        val viewModel = loadedViewModel(wallet)

        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()

        val ready = viewModel.uiState.value as WordGameUiState.Ready
        assertEquals(1, wallet.consumeCalls)
        assertEquals(1, ready.game.hintsUsed)
        assertEquals(ready.puzzle.answer[0], ready.game.currentDraft[0])
        assertEquals(0, ready.game.attempts.size)
        assertFalse(ready.hintsExhausted)
    }

    @Test
    fun anEmptyStockOpensNothingAndOffersToRestock() {
        val wallet = HintStock(0)
        val viewModel = loadedViewModel(wallet)
        val before = (viewModel.uiState.value as WordGameUiState.Ready).game

        viewModel.requestHint()
        work.scheduler.advanceUntilIdle()

        val ready = viewModel.uiState.value as WordGameUiState.Ready
        assertEquals(before, ready.game)
        assertTrue(ready.hintsExhausted)
    }

    private fun loadedViewModel(wallet: EconomyRepository): WordGameViewModel {
        val levels = WordLevel()
        val viewModel =
            WordGameViewModel(
                launch = GameAttemptLaunch.Level(levels.level),
                attemptFactory = GameAttemptFactory(levels, wordLanguage = { WordLanguage.RUSSIAN }) { "attempt" },
                completionRepository = RecordingCompletionRepository(),
                economyRepository = wallet,
                workDispatcher = work,
            )
        work.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** Russian Word level 1 on Medium: Generator V2, like the frozen Russian bucket. */
    private class WordLevel : CatalogLevelRepository {
        val level = CatalogLevelId(PuzzleType.WORD, Difficulty.MEDIUM, CatalogLevelNumber(1))

        override fun observeCurrentLevel(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): Flow<CatalogLevelNumber> = MutableStateFlow(CatalogLevelNumber(1))

        override fun observeCurrentLevels(puzzleType: PuzzleType): Flow<Map<Difficulty, CatalogLevelNumber>> = MutableStateFlow(emptyMap())

        override suspend fun currentLevelId(
            puzzleType: PuzzleType,
            difficulty: Difficulty,
        ): CatalogLevelId = level

        override suspend fun resolve(levelId: CatalogLevelId): CatalogLevelDefinition =
            CatalogLevelDefinition(levelId, PuzzleSeed(7L), GeneratorVersion(2))
    }

    /** A wallet with [hints] in stock that spends them one by one. */
    private class HintStock(
        private var hints: Int,
    ) : EconomyRepository {
        var consumeCalls = 0
            private set

        override fun observe(): Flow<PlayerEconomy> = MutableStateFlow(PlayerEconomy(hints = hints))

        override suspend fun refresh(): PlayerEconomy = PlayerEconomy(hints = hints)

        override suspend fun consumeHint(actionId: String): EconomyHintUse {
            consumeCalls += 1
            if (hints == 0) return EconomyHintUse.NoHints(PlayerEconomy(hints = 0))
            hints -= 1
            return EconomyHintUse.Used(PlayerEconomy(hints = hints))
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
}
