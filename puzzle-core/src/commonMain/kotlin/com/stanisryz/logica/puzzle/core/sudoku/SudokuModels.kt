package com.stanisryz.logica.puzzle.core.sudoku

enum class SudokuDifficulty(
    internal val assetCode: Int,
) {
    EASY(1),
    MEDIUM(2),
    HARD(3),
    EXPERT(4),
}

enum class SudokuDatasetVersion(
    val value: Int,
) {
    V1(1),
}

data class SudokuPuzzleId(
    val datasetVersion: SudokuDatasetVersion,
    val difficulty: SudokuDifficulty,
    val fingerprint: String,
) {
    init {
        require(fingerprint.length == SHA_256_HEX_LENGTH && fingerprint.all { it in HEX_CHARACTERS }) {
            "Sudoku fingerprint must be 64 lower-case hexadecimal characters."
        }
    }

    private companion object {
        const val SHA_256_HEX_LENGTH = 64
        const val HEX_CHARACTERS = "0123456789abcdef"
    }
}

data class SudokuPuzzle(
    val id: SudokuPuzzleId,
    val givens: String,
    val solution: String,
    val upstreamRatingTenths: Int,
) {
    init {
        require(givens.length == CELL_COUNT && givens.all { it in '0'..'9' }) {
            "Sudoku givens must contain exactly 81 digits using 0 for an empty cell."
        }
        require(solution.length == CELL_COUNT && solution.all { it in '1'..'9' }) {
            "Sudoku solution must contain exactly 81 digits from 1 through 9."
        }
        require(upstreamRatingTenths > 0) { "Upstream Sudoku rating must be positive." }
    }

    val upstreamRating: Double
        get() = upstreamRatingTenths / 10.0

    private companion object {
        const val CELL_COUNT = 81
    }
}
