package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty

/**
 * The version-aware 2048 gameplay contract, so no ViewModel or Compose code has to branch on a
 * generator version. Board size, compaction, merging, scoring, and deterministic spawning are shared
 * by every version; only the win condition and the terminal evaluation differ.
 *
 * V1 wins the instant the target tile appears. V2 plays on to the last legal move and judges the
 * final score against a difficulty target.
 */
sealed interface Game2048Ruleset {
    val version: Game2048GeneratorVersion

    val difficulty: Difficulty

    /** The tile that wins a V1 attempt; `null` for score-based versions. */
    val targetTile: Int?

    /** The score a V2 attempt must hold when it runs out of moves; `null` for tile-based versions. */
    val targetScore: Long?

    /** Informational only. Reaching the V2 goal never finishes the game by itself. */
    fun isGoalReached(
        board: List<Int>,
        score: Long,
    ): Boolean

    /** Whether a moved board already wins before that move's spawn. True only for V1. */
    fun solvesBeforeSpawn(
        board: List<Int>,
        score: Long,
    ): Boolean

    fun status(
        board: List<Int>,
        score: Long,
    ): Game2048Status

    companion object {
        fun of(
            version: Game2048GeneratorVersion,
            difficulty: Difficulty,
        ): Game2048Ruleset =
            when (version) {
                Game2048GeneratorVersion.V1 -> Game2048RulesetV1(difficulty)
                Game2048GeneratorVersion.V2 -> Game2048RulesetV2(difficulty)
                else -> error("Unsupported 2048 rules version: ${version.value}.")
            }

        fun isSupported(version: Game2048GeneratorVersion): Boolean =
            version == Game2048GeneratorVersion.V1 || version == Game2048GeneratorVersion.V2
    }
}

/** Frozen Stage 36 rules: the target tile ends the attempt in victory the moment it is merged. */
data class Game2048RulesetV1(
    override val difficulty: Difficulty,
) : Game2048Ruleset {
    override val version: Game2048GeneratorVersion get() = Game2048GeneratorVersion.V1

    override val targetTile: Int =
        when (difficulty) {
            Difficulty.EASY -> 256
            Difficulty.MEDIUM -> 512
            Difficulty.HARD -> 1024
            Difficulty.EXPERT -> 2048
        }

    override val targetScore: Long? get() = null

    override fun isGoalReached(
        board: List<Int>,
        score: Long,
    ): Boolean = board.any { it >= targetTile }

    override fun solvesBeforeSpawn(
        board: List<Int>,
        score: Long,
    ): Boolean = isGoalReached(board, score)

    override fun status(
        board: List<Int>,
        score: Long,
    ): Game2048Status = Game2048Rules.status(board, targetTile)
}

/** Score rules: the goal can be crossed mid-game, and only the final score decides the outcome. */
data class Game2048RulesetV2(
    override val difficulty: Difficulty,
) : Game2048Ruleset {
    override val version: Game2048GeneratorVersion get() = Game2048GeneratorVersion.V2

    override val targetTile: Int? get() = null

    override val targetScore: Long =
        when (difficulty) {
            Difficulty.EASY -> 12_000L
            Difficulty.MEDIUM -> 30_000L
            Difficulty.HARD -> 100_000L
            Difficulty.EXPERT -> 250_000L
        }

    override fun isGoalReached(
        board: List<Int>,
        score: Long,
    ): Boolean = score >= targetScore

    override fun solvesBeforeSpawn(
        board: List<Int>,
        score: Long,
    ): Boolean = false

    override fun status(
        board: List<Int>,
        score: Long,
    ): Game2048Status = Game2048Rules.scoreStatus(board, score, targetScore)
}
