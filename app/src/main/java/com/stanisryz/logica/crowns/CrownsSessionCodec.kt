package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsHint
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintProvider
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.CrownsState

internal data class EncodedCrownsSession(
    val gameplayPayload: String,
    val moveHistoryPayload: String,
    val hintsUsed: Int,
    val status: String,
)

internal object CrownsSessionCodec {
    /** V1 stored committed values plus an Undo history; V2 stores committed values plus pencil marks. */
    const val SESSION_FORMAT_VERSION = 2

    fun encode(
        puzzle: CrownsPuzzle,
        game: CrownsGameState,
    ): EncodedCrownsSession {
        require(game.puzzleId == puzzle.id) { "Crowns game belongs to a different puzzle." }
        val gameplayPayload =
            buildString {
                appendLine("size=${puzzle.size}")
                appendLine("crowns=${game.board.crowns.toPositionPayload()}")
                appendLine("marks=${game.userMarks.toPositionPayload()}")
                appendLine("pencilCrowns=${game.pencilCrowns.toPositionPayload()}")
                appendLine("pencilMarks=${game.pencilMarks.toPositionPayload()}")
                append("hint=${game.currentHint?.toPayload() ?: "-"}")
            }
        return EncodedCrownsSession(
            // Undo is gone, so the legacy history column stays empty for every new save.
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = "",
            hintsUsed = game.hintsUsed,
            status = game.status.name,
        )
    }

    /**
     * Restores committed crowns/marks and pencil marks. Correct/wrong presentation is recomputed by
     * the engine from the regenerated puzzle, so a V1 save simply reopens with its values classified
     * and no pencil marks; its stored Undo history is dropped.
     */
    fun decode(
        puzzle: CrownsPuzzle,
        sessionFormatVersion: Int,
        gameplayPayload: String,
        hintsUsed: Int,
        status: String,
        engine: CrownsGameEngine = CrownsGameEngine(puzzle),
    ): CrownsGameState {
        require(sessionFormatVersion in SUPPORTED_FORMAT_VERSIONS) {
            "Unsupported Crowns session format version: $sessionFormatVersion."
        }
        require(hintsUsed >= 0) { "Hints used must not be negative." }

        val gameplay = gameplayPayload.parseGameplayPayload(sessionFormatVersion)
        val size = gameplay.getValue("size").toIntOrNull() ?: error("Invalid saved board size.")
        require(size == puzzle.size) { "Saved board size does not match the regenerated puzzle." }
        val crowns = gameplay.getValue("crowns").decodePositions()
        val marks = gameplay.getValue("marks").decodePositions()
        val pencilCrowns = gameplay["pencilCrowns"].orEmpty().decodePositions()
        val pencilMarks = gameplay["pencilMarks"].orEmpty().decodePositions()
        val board = CrownsState(crowns)
        val savedHintPayload = gameplay.getValue("hint")
        val currentHint =
            if (savedHintPayload == "-") {
                null
            } else {
                CrownsHintProvider().hint(puzzle, board, marks).also { hint ->
                    require(hint != null && hint.toPayload() == savedHintPayload) {
                        "Saved hint is not compatible with the regenerated puzzle."
                    }
                }
            }
        val game =
            engine.restore(
                board = board,
                userMarks = marks,
                pencilCrowns = pencilCrowns,
                pencilMarks = pencilMarks,
                hintsUsed = hintsUsed,
                currentHint = currentHint,
            )
        val savedStatus =
            runCatching { CrownsGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(sessionFormatVersion: Int): Map<String, String> {
        val entries =
            lineSequence()
                .filter(String::isNotBlank)
                .map { line ->
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid gameplay payload line." }
                    parts[0] to parts[1]
                }.toList()
        val values = entries.toMap()
        require(entries.size == values.size) { "Gameplay payload contains duplicate fields." }
        val expectedFields =
            if (sessionFormatVersion == 1) {
                setOf("size", "crowns", "marks", "hint")
            } else {
                setOf("size", "crowns", "marks", "pencilCrowns", "pencilMarks", "hint")
            }
        require(values.keys == expectedFields) { "Invalid gameplay payload fields." }
        return values
    }

    private fun Set<CrownsPosition>.toPositionPayload(): String =
        sortedWith(POSITION_ORDER)
            .joinToString(separator = "|") { "${it.row}:${it.column}" }
            .ifEmpty { "-" }

    private fun String.decodePositions(): Set<CrownsPosition> {
        if (isEmpty() || this == "-") return emptySet()
        val positions =
            split('|').map { entry ->
                val parts = entry.split(':')
                require(parts.size == 2) { "Invalid saved position." }
                CrownsPosition(
                    row = parts[0].toIntOrNull() ?: error("Invalid saved row."),
                    column = parts[1].toIntOrNull() ?: error("Invalid saved column."),
                )
            }
        require(positions.size == positions.toSet().size) { "Saved positions contain duplicates." }
        return positions.toSet()
    }

    private fun CrownsHint.toPayload(): String =
        listOf(
            kind.name,
            action.name,
            technique?.name ?: "-",
            targetPositions.toPositionPayload(),
            evidencePositions.toPositionPayload(),
            conflictPositions.toPositionPayload(),
        ).joinToString(separator = ",")

    private val POSITION_ORDER = compareBy(CrownsPosition::row, CrownsPosition::column)

    private val SUPPORTED_FORMAT_VERSIONS = 1..SESSION_FORMAT_VERSION
}
