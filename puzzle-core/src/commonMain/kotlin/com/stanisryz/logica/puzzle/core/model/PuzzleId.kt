package com.stanisryz.logica.puzzle.core.model

data class PuzzleId(
    val type: PuzzleType,
    val difficulty: Difficulty,
    val seed: PuzzleSeed,
    val generatorVersion: GeneratorVersion,
)
