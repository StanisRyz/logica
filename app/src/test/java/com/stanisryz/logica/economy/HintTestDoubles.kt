package com.stanisryz.logica.economy

import com.stanisryz.logica.catalog.CatalogLevelRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** A full wallet whose hint spending waits on [gate], so a test can act while a hint is being charged. */
internal class GatedHintWallet : EconomyRepository {
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

/** Level 1 of [puzzleType] on Medium, always resolved to [seed] with generator version 1. */
internal class FrozenLevelRepository(
    private val puzzleType: PuzzleType,
    private val seed: PuzzleSeed = PuzzleSeed(7L),
) : CatalogLevelRepository {
    val level = CatalogLevelId(puzzleType, Difficulty.MEDIUM, CatalogLevelNumber(1), CatalogLevelPackVersion.V1)

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
        CatalogLevelDefinition(levelId, seed, GeneratorVersion(1))
}

/** Records every completion and saves it at once. */
internal class RecordingCompletionRepository : GameCompletionRepository {
    val completions = mutableListOf<GameCompletion>()

    override suspend fun complete(completion: GameCompletion): GameResult {
        completions += completion
        return GameResult(
            resultId = completion.resultId,
            puzzleType = completion.puzzleType,
            difficulty = completion.difficulty,
            puzzleSeed = completion.puzzleSeed,
            generatorVersion = completion.generatorVersion,
            resultScope = completion.resultScope,
            hintsUsed = completion.hintsUsed,
            completedAt = Instant.EPOCH,
            outcome = completion.outcome,
            catalogLevel = completion.catalogLevel,
        )
    }
}
