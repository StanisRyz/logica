package com.stanisryz.logica.puzzle.core.game2048

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed

data class EncodedGame2048Session(
    val formatVersion: Int,
    val payload: String,
)

/**
 * Stores only identity — including the rules version — board, score, and spawn progression; status
 * is derived from the persisted version when the session is restored. That payload is everything
 * both V1 and V2 need, so the serialized format stays at version 1 across the rules change.
 */
object Game2048SessionCodecV1 {
    const val FORMAT_VERSION = 1

    fun encode(state: Game2048State): EncodedGame2048Session {
        require(Game2048Ruleset.isSupported(state.puzzleId.generatorVersion)) {
            "Unsupported 2048 generator version: ${state.puzzleId.generatorVersion.value}."
        }
        require(state.nextSpawnIndex >= INITIAL_SPAWN_COUNT) {
            "Saved 2048 spawn index predates the two initial spawns."
        }
        return EncodedGame2048Session(
            formatVersion = FORMAT_VERSION,
            payload =
                buildString {
                    appendLine(
                        "puzzle=${state.puzzleId.seed.value}|${state.puzzleId.difficulty.name}|" +
                            state.puzzleId.generatorVersion.value,
                    )
                    appendLine("board=${state.board.joinToString(",")}")
                    appendLine("score=${state.score}")
                    append("spawn=${state.nextSpawnIndex}")
                },
        )
    }

    fun puzzleId(encoded: EncodedGame2048Session): Game2048PuzzleId = parse(encoded).puzzleId

    fun decode(encoded: EncodedGame2048Session): Game2048State {
        val parsed = parse(encoded)
        return Game2048Engine(parsed.puzzleId).restore(
            board = parsed.board,
            score = parsed.score,
            nextSpawnIndex = parsed.nextSpawnIndex,
        )
    }

    private fun parse(encoded: EncodedGame2048Session): ParsedSession {
        require(encoded.formatVersion == FORMAT_VERSION) {
            "Unsupported 2048 session format version: ${encoded.formatVersion}."
        }
        val lines = encoded.payload.lines()
        require(lines.size == PAYLOAD_LINE_COUNT) { "2048 session payload has invalid line count." }
        val identity = lines[0].requiredValue("puzzle").split('|')
        require(identity.size == 3) { "Saved 2048 identity is malformed." }
        val seed = identity[0].toLongOrNull() ?: error("Saved 2048 seed is invalid.")
        val difficulty =
            runCatching { Difficulty.valueOf(identity[1]) }
                .getOrElse { error("Saved 2048 difficulty is invalid.") }
        val generatorVersion = identity[2].toIntOrNull() ?: error("Saved 2048 generator version is invalid.")
        require(generatorVersion > 0 && Game2048Ruleset.isSupported(Game2048GeneratorVersion(generatorVersion))) {
            "Saved 2048 generator version is unsupported."
        }
        val boardParts = lines[1].requiredValue("board").split(',')
        require(boardParts.size == Game2048State.CELL_COUNT) { "Saved 2048 board has invalid size." }
        val board =
            boardParts.map { part ->
                val value = part.toIntOrNull() ?: error("Saved 2048 tile is invalid.")
                require(Game2048Rules.isValidCellValue(value)) { "Saved 2048 tile is invalid." }
                value
            }
        val score = lines[2].requiredValue("score").toLongOrNull() ?: error("Saved 2048 score is invalid.")
        require(score >= 0L) { "Saved 2048 score is negative." }
        val spawn = lines[3].requiredValue("spawn").toLongOrNull() ?: error("Saved 2048 spawn index is invalid.")
        require(spawn >= INITIAL_SPAWN_COUNT) { "Saved 2048 spawn index is invalid." }
        return ParsedSession(
            puzzleId =
                Game2048PuzzleId(
                    seed = PuzzleSeed(seed),
                    difficulty = difficulty,
                    generatorVersion = Game2048GeneratorVersion(generatorVersion),
                ),
            board = board,
            score = score,
            nextSpawnIndex = spawn,
        )
    }

    private fun String.requiredValue(key: String): String {
        val prefix = "$key="
        require(startsWith(prefix)) { "Missing saved 2048 field: $key." }
        return removePrefix(prefix)
    }

    private data class ParsedSession(
        val puzzleId: Game2048PuzzleId,
        val board: List<Int>,
        val score: Long,
        val nextSpawnIndex: Long,
    )

    private const val INITIAL_SPAWN_COUNT = 2L
    private const val PAYLOAD_LINE_COUNT = 4
}
