package com.stanisryz.logica.sudoku

import com.stanisryz.logica.puzzle.core.catalog.BinaryCatalogLevelPack
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.BinarySudokuDataset
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetResult
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SharedSudokuDataParityTest {
    @Test
    fun canonicalDatasetKeepsTheFrozenCatalogSelectorIdentity() {
        val levelId = CatalogLevelId(PuzzleType.SUDOKU, Difficulty.EASY, CatalogLevelNumber(1))
        val levelPack =
            BinaryCatalogLevelPack(
                source = { packVersion, puzzleType, difficulty ->
                    File(puzzleDataDirectory, CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty))
                        .takeIf(File::isFile)
                        ?.inputStream()
                },
            )
        val definition =
            when (val result = levelPack.resolve(levelId)) {
                is CatalogLevelPackResult.Success -> result.value
                is CatalogLevelPackResult.Failure -> error(result.detail)
            }
        val dataset =
            BinarySudokuDataset { version, difficulty ->
                File(puzzleDataDirectory, "sudoku/v${version.value}/${difficulty.name.lowercase()}.sdk")
                    .takeIf(File::isFile)
                    ?.readBytes()
            }
        val puzzle =
            when (
                val result =
                    dataset.selectPuzzle(
                        SudokuDatasetVersion.V1,
                        SudokuDifficulty.EASY,
                        definition.seed.value,
                    )
            ) {
                is SudokuDatasetResult.Success -> result.value
                is SudokuDatasetResult.Failure -> error(result.detail)
            }

        assertEquals(PuzzleSeed(1L), definition.seed)
        assertEquals(EXPECTED_FINGERPRINT, puzzle.id.fingerprint)
    }

    private companion object {
        val puzzleDataDirectory: File =
            listOf(File("puzzle-data"), File("../puzzle-data"))
                .firstOrNull(File::isDirectory)
                ?: error("The canonical puzzle-data directory was not found.")

        const val EXPECTED_FINGERPRINT = "1a51ea67a4bf5bd74c5588e268636fe6f71f8f85d137be5be7db79f4fab35f01"
    }
}
