package com.stanisryz.logica.catalog

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResultScope
import java.time.LocalDate
import java.util.UUID

/**
 * A request to open one gameplay attempt. There is no "restore" any more: a Catalog launch names the
 * public level to play and a Daily launch names that day's deterministic puzzle, and both always
 * start from their own initial state.
 */
internal sealed interface GameAttemptLaunch {
    val puzzleType: PuzzleType

    data class Level(
        val levelId: CatalogLevelId,
    ) : GameAttemptLaunch {
        override val puzzleType: PuzzleType get() = levelId.puzzleType
    }

    data class Daily(
        override val puzzleType: PuzzleType,
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
        val difficulty: Difficulty,
        val seed: PuzzleSeed,
        val generatorVersion: GeneratorVersion,
    ) : GameAttemptLaunch
}

/** The displayed level of a Catalog launch, or null for Daily. Presentation reads this directly. */
internal fun GameAttemptLaunch.levelNumberOrNull(): Int? = (this as? GameAttemptLaunch.Level)?.levelId?.levelNumber?.value

/** Where a resolved attempt belongs. The context alone decides result scope and level identity. */
internal sealed interface GameAttemptContext {
    val resultScope: GameResultScope

    data class Catalog(
        val levelId: CatalogLevelId,
    ) : GameAttemptContext {
        override val resultScope: GameResultScope get() = GameResultScope.CATALOG
    }

    data class Daily(
        val challengeDate: LocalDate,
        val policyVersion: DailyPolicyVersion,
    ) : GameAttemptContext {
        override val resultScope: GameResultScope get() = GameResultScope.DAILY
    }
}

/**
 * One transient attempt at one puzzle. Its board lives only in the gameplay ViewModel; the only
 * durable things it can produce are its terminal result, the economy effect of that result, and —
 * for a solved Catalog level — the progression step to the next level.
 *
 * [resultId] is the completion identity: it is derived from the displayed Catalog level plus this
 * attempt, so a repeated completion callback is an idempotent no-op while a genuine retry after a
 * failure is a new attempt. Level 10 001 is a different completion from level 1 even though the two
 * share a content slot.
 */
internal data class GameAttempt(
    val attemptId: String,
    val puzzleType: PuzzleType,
    val difficulty: Difficulty,
    val seed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
    val context: GameAttemptContext,
) {
    init {
        require(attemptId.isNotBlank()) { "Attempt ID must not be blank." }
        val catalog = context as? GameAttemptContext.Catalog
        require(catalog == null || catalog.levelId.puzzleType == puzzleType) {
            "The Catalog level belongs to another game."
        }
        require(catalog == null || catalog.levelId.difficulty == difficulty) {
            "The Catalog level belongs to another difficulty."
        }
    }

    val levelId: CatalogLevelId? get() = (context as? GameAttemptContext.Catalog)?.levelId

    val levelNumber: CatalogLevelNumber? get() = levelId?.levelNumber

    val isCatalog: Boolean get() = context is GameAttemptContext.Catalog

    val resultId: String
        get() =
            when (context) {
                is GameAttemptContext.Catalog ->
                    "catalog:${context.levelId.packVersion.value}:${puzzleType.name}:" +
                        "${difficulty.name}:${context.levelId.levelNumber.value}:$attemptId"
                is GameAttemptContext.Daily -> attemptId
            }

    /** A fresh attempt at exactly the same puzzle, with its own completion identity. */
    fun restarted(attemptId: String = UUID.randomUUID().toString()): GameAttempt = copy(attemptId = attemptId)

    fun completion(
        outcome: GameOutcome,
        hintsUsed: Int = 0,
        attemptsUsed: Int? = null,
    ): GameCompletion {
        val daily = context as? GameAttemptContext.Daily
        return GameCompletion(
            resultId = resultId,
            puzzleType = puzzleType,
            difficulty = difficulty,
            puzzleSeed = seed,
            generatorVersion = generatorVersion,
            resultScope = context.resultScope,
            hintsUsed = hintsUsed,
            outcome = outcome,
            attemptsUsed = attemptsUsed,
            challengeDate = daily?.challengeDate,
            dailyPolicyVersion = daily?.policyVersion,
            catalogLevel = levelId,
        )
    }
}

/**
 * Turns a launch into a fresh in-memory attempt. A Catalog launch resolves its frozen level
 * definition here — never a generated-on-the-spot seed — so the same level always produces the same
 * puzzle for every player.
 */
internal class GameAttemptFactory(
    private val levelRepository: CatalogLevelRepository,
    private val attemptIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(
        launch: GameAttemptLaunch,
        puzzleType: PuzzleType,
    ): GameAttempt {
        require(launch.puzzleType == puzzleType) { "${launch.puzzleType} launch cannot open $puzzleType." }
        return when (launch) {
            is GameAttemptLaunch.Level -> {
                val definition = levelRepository.resolve(launch.levelId)
                GameAttempt(
                    attemptId = attemptIdFactory(),
                    puzzleType = puzzleType,
                    difficulty = launch.levelId.difficulty,
                    seed = definition.seed,
                    generatorVersion = definition.generatorVersion,
                    context = GameAttemptContext.Catalog(launch.levelId),
                )
            }
            is GameAttemptLaunch.Daily ->
                GameAttempt(
                    attemptId = attemptIdFactory(),
                    puzzleType = puzzleType,
                    difficulty = launch.difficulty,
                    seed = launch.seed,
                    generatorVersion = launch.generatorVersion,
                    context = GameAttemptContext.Daily(launch.challengeDate, launch.policyVersion),
                )
        }
    }

    fun nextAttemptId(): String = attemptIdFactory()
}
