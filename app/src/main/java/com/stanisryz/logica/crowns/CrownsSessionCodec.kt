package com.stanisryz.logica.crowns

import com.stanisryz.logica.puzzle.core.crowns.CrownsGameEngine
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameState
import com.stanisryz.logica.puzzle.core.crowns.CrownsGameStatus
import com.stanisryz.logica.puzzle.core.crowns.CrownsHint
import com.stanisryz.logica.puzzle.core.crowns.CrownsHintProvider
import com.stanisryz.logica.puzzle.core.crowns.CrownsMove
import com.stanisryz.logica.puzzle.core.crowns.CrownsPlayerCell
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
    const val SESSION_FORMAT_VERSION = 1

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
                append("hint=${game.currentHint?.toPayload() ?: "-"}")
            }
        val moveHistoryPayload =
            game.moveHistory.joinToString(separator = "\n") { move ->
                "${move.position.row},${move.position.column},${move.previousCell.name},${move.newCell.name}"
            }
        return EncodedCrownsSession(
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = moveHistoryPayload,
            hintsUsed = game.hintsUsed,
            status = game.status.name,
        )
    }

    fun decode(
        puzzle: CrownsPuzzle,
        sessionFormatVersion: Int,
        gameplayPayload: String,
        moveHistoryPayload: String,
        hintsUsed: Int,
        status: String,
    ): CrownsGameState {
        require(sessionFormatVersion == SESSION_FORMAT_VERSION) {
            "Unsupported Crowns session format version: $sessionFormatVersion."
        }
        require(hintsUsed >= 0) { "Hints used must not be negative." }

        val gameplay = gameplayPayload.parseGameplayPayload()
        val size = gameplay.getValue("size").toIntOrNull() ?: error("Invalid saved board size.")
        require(size == puzzle.size) { "Saved board size does not match the regenerated puzzle." }
        val crowns = gameplay.getValue("crowns").decodePositions()
        val marks = gameplay.getValue("marks").decodePositions()
        val board = CrownsState(crowns)
        val moves = moveHistoryPayload.decodeMoves()
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
            CrownsGameEngine(puzzle).restore(
                board = board,
                userMarks = marks,
                moveHistory = moves,
                hintsUsed = hintsUsed,
                currentHint = currentHint,
            )
        val savedStatus =
            runCatching { CrownsGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(): Map<String, String> {
        val entries =
            lineSequence()
                .filter(String::isNotBlank)
                .map { line ->
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid gameplay payload line." }
                    parts[0] to parts[1]
                }.toList()
        val values =
            entries.toMap()
        require(entries.size == values.size) { "Gameplay payload contains duplicate fields." }
        require(values.keys == setOf("size", "crowns", "marks", "hint")) { "Invalid gameplay payload fields." }
        return values
    }

    private fun Set<CrownsPosition>.toPositionPayload(): String =
        sortedWith(POSITION_ORDER)
            .joinToString(separator = "|") { "${it.row}:${it.column}" }
            .ifEmpty { "-" }

    private fun String.decodePositions(): Set<CrownsPosition> {
        if (this == "-") return emptySet()
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

    private fun String.decodeMoves(): List<CrownsMove> {
        if (isBlank()) return emptyList()
        return lineSequence()
            .map { line ->
                val parts = line.split(',')
                require(parts.size == 4) { "Invalid move history entry." }
                CrownsMove(
                    position =
                        CrownsPosition(
                            row = parts[0].toIntOrNull() ?: error("Invalid move row."),
                            column = parts[1].toIntOrNull() ?: error("Invalid move column."),
                        ),
                    previousCell = parts[2].toPlayerCell(),
                    newCell = parts[3].toPlayerCell(),
                )
            }.toList()
    }

    private fun String.toPlayerCell(): CrownsPlayerCell =
        runCatching { CrownsPlayerCell.valueOf(this) }
            .getOrElse { error("Invalid saved player cell.") }

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
}
