package com.stanisryz.logica.sudoku

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.sudoku.SudokuPuzzle

/**
 * Catalog identity adapter for the immutable Sudoku Dataset V1. The generic platform identity
 * stores the selected difficulty, selector seed, and this provider version; the resulting puzzle
 * keeps its authoritative dataset version and SHA-256 fingerprint in [SudokuPuzzle.id].
 */
internal class SudokuCatalogProvider(
    private val dataset: SudokuDataset,
) {
    val version: GeneratorVersion = VERSION

    fun select(
        difficulty: Difficulty,
        selectorSeed: PuzzleSeed,
        providerVersion: GeneratorVersion = version,
    ): SudokuDatasetResult<SudokuPuzzle> {
        require(providerVersion == version) { "Unsupported Sudoku Catalog provider version: ${providerVersion.value}." }
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

internal fun Difficulty.toSudokuDifficulty(): SudokuDifficulty =
    when (this) {
        Difficulty.EASY -> SudokuDifficulty.EASY
        Difficulty.MEDIUM -> SudokuDifficulty.MEDIUM
        Difficulty.HARD -> SudokuDifficulty.HARD
        Difficulty.EXPERT -> SudokuDifficulty.EXPERT
    }

internal fun SudokuDifficulty.toPlatformDifficulty(): Difficulty =
    when (this) {
        SudokuDifficulty.EASY -> Difficulty.EASY
        SudokuDifficulty.MEDIUM -> Difficulty.MEDIUM
        SudokuDifficulty.HARD -> Difficulty.HARD
        SudokuDifficulty.EXPERT -> Difficulty.EXPERT
    }
