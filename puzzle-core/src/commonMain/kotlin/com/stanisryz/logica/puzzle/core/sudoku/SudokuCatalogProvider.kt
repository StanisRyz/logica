package com.stanisryz.logica.puzzle.core.sudoku

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed

/**
 * Catalog identity adapter for the immutable Sudoku Dataset V1. The platform Catalog identity
 * supplies the difficulty, frozen selector seed, and provider version; the selected record keeps
 * its authoritative dataset version and fingerprint in [SudokuPuzzle.id].
 */
class SudokuCatalogProvider(
    private val dataset: SudokuDataset,
) {
    val version: GeneratorVersion = VERSION

    fun select(
        difficulty: Difficulty,
        selectorSeed: PuzzleSeed,
        providerVersion: GeneratorVersion = version,
    ): SudokuDatasetResult<SudokuPuzzle> {
        require(providerVersion == version) {
            "Unsupported Sudoku Catalog provider version: ${providerVersion.value}."
        }
        return dataset.selectPuzzle(
            version = SudokuDatasetVersion.V1,
            difficulty = difficulty.toSudokuDifficulty(),
            selector = selectorSeed.value,
        )
    }

    companion object {
        val VERSION = GeneratorVersion(1)
    }
}

fun Difficulty.toSudokuDifficulty(): SudokuDifficulty =
    when (this) {
        Difficulty.EASY -> SudokuDifficulty.EASY
        Difficulty.MEDIUM -> SudokuDifficulty.MEDIUM
        Difficulty.HARD -> SudokuDifficulty.HARD
        Difficulty.EXPERT -> SudokuDifficulty.EXPERT
    }

fun SudokuDifficulty.toPlatformDifficulty(): Difficulty =
    when (this) {
        SudokuDifficulty.EASY -> Difficulty.EASY
        SudokuDifficulty.MEDIUM -> Difficulty.MEDIUM
        SudokuDifficulty.HARD -> Difficulty.HARD
        SudokuDifficulty.EXPERT -> Difficulty.EXPERT
    }
