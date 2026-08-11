package com.stanisryz.logica.puzzle.core.sudoku

data class EncodedSudokuSession(
    val formatVersion: Int,
    val payload: String,
)

/** Pure deterministic codec. Dataset givens, solution, and derived cell statuses are never stored. */
object SudokuSessionCodecV1 {
    const val FORMAT_VERSION = 1

    fun encode(state: SudokuGameState): EncodedSudokuSession {
        val playerValues =
            state.cells.joinToString(separator = "") { cell ->
                if (cell.status == SudokuCellStatus.GIVEN) "0" else cell.value.toString()
            }
        val candidates =
            state.cells.joinToString(separator = "") { cell ->
                cell.candidates.bits
                    .toString(16)
                    .padStart(CANDIDATE_HEX_WIDTH, '0')
            }
        return EncodedSudokuSession(
            formatVersion = FORMAT_VERSION,
            payload =
                buildString {
                    appendLine(
                        "puzzle=${state.puzzleId.datasetVersion.value}|" +
                            "${state.puzzleId.difficulty.name}|${state.puzzleId.fingerprint}",
                    )
                    appendLine("values=$playerValues")
                    appendLine("candidates=$candidates")
                    appendLine("mistakes=${state.mistakesUsed}")
                    append("hints=${state.hintsUsed}")
                },
        )
    }

    fun puzzleId(encoded: EncodedSudokuSession): SudokuPuzzleId {
        require(encoded.formatVersion == FORMAT_VERSION) {
            "Unsupported Sudoku session format version: ${encoded.formatVersion}."
        }
        return parsePayload(encoded.payload).puzzleId
    }

    fun decode(
        puzzle: SudokuPuzzle,
        encoded: EncodedSudokuSession,
        engine: SudokuGameEngine = SudokuGameEngine(puzzle),
    ): SudokuGameState {
        require(encoded.formatVersion == FORMAT_VERSION) {
            "Unsupported Sudoku session format version: ${encoded.formatVersion}."
        }
        val parsed = parsePayload(encoded.payload)
        require(parsed.puzzleId == puzzle.id) { "Saved Sudoku identity does not match the loaded puzzle." }
        return engine.restore(
            playerValues = parsed.playerValues,
            candidateMasks = parsed.candidateMasks,
            mistakesUsed = parsed.mistakesUsed,
            hintsUsed = parsed.hintsUsed,
        )
    }

    private fun parsePayload(payload: String): ParsedPayload {
        val lines = payload.lines()
        require(lines.size == PAYLOAD_LINE_COUNT) { "Sudoku session payload has invalid line count." }
        val identityParts = lines[0].requiredValue("puzzle").split('|')
        require(identityParts.size == 3) { "Saved Sudoku identity is malformed." }
        val versionValue = identityParts[0].toIntOrNull() ?: error("Saved Sudoku dataset version is invalid.")
        val version =
            SudokuDatasetVersion.entries.singleOrNull { it.value == versionValue }
                ?: error("Saved Sudoku dataset version is unsupported.")
        val difficulty =
            runCatching { SudokuDifficulty.valueOf(identityParts[1]) }
                .getOrElse { error("Saved Sudoku difficulty is invalid.") }
        val puzzleId = SudokuPuzzleId(version, difficulty, identityParts[2])

        val playerValueText = lines[1].requiredValue("values")
        require(playerValueText.length == SudokuGameState.CELL_COUNT && playerValueText.all { it in '0'..'9' }) {
            "Saved Sudoku values are malformed."
        }
        val candidateText = lines[2].requiredValue("candidates")
        require(candidateText.length == SudokuGameState.CELL_COUNT * CANDIDATE_HEX_WIDTH) {
            "Saved Sudoku candidates have invalid length."
        }
        val candidateMasks =
            candidateText.chunked(CANDIDATE_HEX_WIDTH).map { value ->
                require(value.all { it in '0'..'9' || it in 'a'..'f' }) {
                    "Saved Sudoku candidate mask is malformed."
                }
                SudokuCandidateMask(value.toInt(16))
            }
        val mistakesUsed =
            lines[3].requiredValue("mistakes").toIntOrNull()
                ?: error("Saved Sudoku mistake count is invalid.")
        val hintsUsed =
            lines[4].requiredValue("hints").toIntOrNull()
                ?: error("Saved Sudoku hint count is invalid.")
        return ParsedPayload(
            puzzleId,
            playerValueText.map(Char::digitToInt),
            candidateMasks,
            mistakesUsed,
            hintsUsed,
        )
    }

    private fun String.requiredValue(key: String): String {
        val prefix = "$key="
        require(startsWith(prefix)) { "Missing saved Sudoku field: $key." }
        return removePrefix(prefix)
    }

    private data class ParsedPayload(
        val puzzleId: SudokuPuzzleId,
        val playerValues: List<Int>,
        val candidateMasks: List<SudokuCandidateMask>,
        val mistakesUsed: Int,
        val hintsUsed: Int,
    )

    private const val CANDIDATE_HEX_WIDTH = 3
    private const val PAYLOAD_LINE_COUNT = 5
}
