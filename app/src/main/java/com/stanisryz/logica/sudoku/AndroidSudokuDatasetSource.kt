package com.stanisryz.logica.sudoku

import android.content.res.AssetManager
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetSource
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import java.io.IOException

class AndroidSudokuDatasetSource(
    private val assets: AssetManager,
) : SudokuDatasetSource {
    override fun readAsset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): ByteArray? =
        try {
            assets
                .open(
                    "sudoku/v${version.value}/${difficulty.name.lowercase()}.sdk",
                    AssetManager.ACCESS_BUFFER,
                ).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
}
