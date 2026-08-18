package com.stanisryz.logica.puzzle.core.web

import com.stanisryz.logica.puzzle.core.catalog.ByteArrayCatalogLevelPackInput
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackFormat
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackInput
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackSource
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetSource
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDatasetVersion
import com.stanisryz.logica.puzzle.core.sudoku.SudokuDifficulty
import com.stanisryz.logica.puzzle.core.word.WordLexiconV1
import com.stanisryz.logica.puzzle.core.word.WordLexiconV2

/**
 * Synchronous browser boundary for the bundled data types the core currently consumes.
 * A future host loads the files itself and installs them here before accessing the shared runtime.
 */
object WebPuzzleData : CatalogLevelPackSource, SudokuDatasetSource {
    private val wordResources = mutableMapOf<String, String>()
    private val catalogLevelPacks = mutableMapOf<String, ByteArray>()
    private val sudokuDatasets = mutableMapOf<SudokuDatasetKey, ByteArray>()

    fun installWordLexiconResource(
        resourcePath: String,
        text: String,
    ) {
        require(resourcePath.isSupportedWordResource()) {
            "Unsupported bundled Word resource: $resourcePath"
        }
        require(text.isNotEmpty()) { "Bundled Word resource $resourcePath is empty." }
        wordResources[resourcePath] = text
    }

    fun installCatalogLevelPack(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
        bytes: ByteArray,
    ) {
        require(bytes.isNotEmpty()) {
            "Catalog Level Pack ${CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)} is empty."
        }
        catalogLevelPacks[CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)] = bytes.copyOf()
    }

    fun installSudokuDataset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
        bytes: ByteArray,
    ) {
        require(bytes.isNotEmpty()) { "Sudoku dataset $version/$difficulty is empty." }
        sudokuDatasets[SudokuDatasetKey(version, difficulty)] = bytes.copyOf()
    }

    override fun open(
        packVersion: CatalogLevelPackVersion,
        puzzleType: PuzzleType,
        difficulty: Difficulty,
    ): CatalogLevelPackInput {
        val path = CatalogLevelPackFormat.assetPath(packVersion, puzzleType, difficulty)
        val bytes =
            checkNotNull(catalogLevelPacks[path]) {
                "Web Catalog Level Pack $path was not preloaded."
            }
        return ByteArrayCatalogLevelPackInput(bytes)
    }

    override fun readAsset(
        version: SudokuDatasetVersion,
        difficulty: SudokuDifficulty,
    ): ByteArray =
        checkNotNull(sudokuDatasets[SudokuDatasetKey(version, difficulty)]) {
            "Web Sudoku dataset $version/$difficulty was not preloaded."
        }.copyOf()

    internal fun readWordResource(resourcePath: String): String =
        checkNotNull(wordResources[resourcePath]) {
            "Web Word lexicon resource $resourcePath was not preloaded."
        }

    private fun String.isSupportedWordResource(): Boolean =
        this == WordLexiconV1.ALLOWED_GUESSES_RESOURCE ||
            this == WordLexiconV1.ANSWERS_RESOURCE ||
            this == WordLexiconV2.ALLOWED_GUESSES_RESOURCE ||
            this == WordLexiconV2.ANSWERS_RESOURCE

    private data class SudokuDatasetKey(
        val version: SudokuDatasetVersion,
        val difficulty: SudokuDifficulty,
    )
}
