package com.stanisryz.logica.puzzle.core.contract

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType

interface PuzzleDefinition {
    val id: PuzzleId
}

interface PuzzleState

interface PuzzleSolution : PuzzleState

interface PuzzleHint

interface PuzzleGenerator<out D : PuzzleDefinition> {
    val type: PuzzleType
    val version: GeneratorVersion

    fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): D
}

interface PuzzleSolver<in D : PuzzleDefinition, out S : PuzzleSolution> {
    fun solve(puzzle: D): S?

    /** Returns at most [limit] and may stop searching as soon as the limit is reached. */
    fun countSolutions(
        puzzle: D,
        limit: Int = 2,
    ): Int
}

interface PuzzleValidator<in D : PuzzleDefinition, in S : PuzzleState> {
    fun validate(
        puzzle: D,
        state: S,
    ): ValidationResult
}

interface PuzzleHintProvider<in D : PuzzleDefinition, in S : PuzzleState, out H : PuzzleHint> {
    fun hint(
        puzzle: D,
        state: S,
    ): H?
}

interface PuzzleDifficultyEvaluator<in D : PuzzleDefinition> {
    fun evaluate(puzzle: D): Difficulty
}

sealed interface ValidationResult {
    data object ValidPartial : ValidationResult

    data object ValidComplete : ValidationResult

    data class Invalid(
        val reason: String? = null,
    ) : ValidationResult
}
