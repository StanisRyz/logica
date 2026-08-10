package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceHint
import com.stanisryz.logica.puzzle.core.balance.BalanceHintProvider
import com.stanisryz.logica.puzzle.core.balance.BalancePosition
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.balance.BalanceState

internal data class EncodedBalanceSession(
    val gameplayPayload: String,
    val moveHistoryPayload: String,
    val hintsUsed: Int,
    val status: String,
)

internal object BalanceSessionCodec {
    /** V1 stored committed values plus an Undo history; V2 stores committed values plus pencil marks. */
    const val SESSION_FORMAT_VERSION = 2

    fun encode(game: BalanceGameState): EncodedBalanceSession {
        val cells =
            buildString(game.board.size * game.board.size) {
                for (row in 0 until game.board.size) {
                    for (column in 0 until game.board.size) {
                        append(game.board.cellAt(BalancePosition(row, column)).payloadSymbol)
                    }
                }
            }
        val gameplayPayload =
            buildString {
                appendLine("size=${game.board.size}")
                appendLine("cells=$cells")
                appendLine("pencil=${game.pencilMarks.toPayload()}")
                append("hint=${game.currentHint?.toPayload() ?: "-"}")
            }
        return EncodedBalanceSession(
            // Undo is gone, so the legacy history column stays empty for every new save.
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = "",
            hintsUsed = game.hintsUsed,
            status = game.status.name,
        )
    }

    /**
     * Restores committed values and pencil marks. Correct/wrong presentation is recomputed by the
     * engine from the regenerated puzzle, so a V1 save simply reopens with its values classified and
     * no pencil marks; its stored Undo history is dropped.
     */
    fun decode(
        puzzle: BalancePuzzle,
        sessionFormatVersion: Int,
        gameplayPayload: String,
        hintsUsed: Int,
        status: String,
        engine: BalanceGameEngine = BalanceGameEngine(puzzle),
    ): BalanceGameState {
        require(sessionFormatVersion in SUPPORTED_FORMAT_VERSIONS) {
            "Unsupported Balance session format version: $sessionFormatVersion."
        }
        require(hintsUsed >= 0) { "Hints used must not be negative." }

        val gameplay = gameplayPayload.parseGameplayPayload(sessionFormatVersion)
        val size = gameplay.getValue("size").toIntOrNull() ?: error("Invalid saved board size.")
        require(size == puzzle.size) { "Saved board size does not match the regenerated puzzle." }
        val cells = gameplay.getValue("cells")
        require(cells.length == size * size) { "Saved board cell count is invalid." }
        val board =
            BalanceState.fromRows(
                cells.map(::balanceCellFromPayloadSymbol).chunked(size),
            )
        val pencilMarks = gameplay["pencil"].orEmpty().decodePencilMarks()
        val savedHint = gameplay.getValue("hint")
        val currentHint =
            if (savedHint == "-") {
                null
            } else {
                BalanceHintProvider().hint(puzzle, board).also { hint ->
                    require(hint != null && hint.toPayload() == savedHint) {
                        "Saved hint is not compatible with the regenerated puzzle."
                    }
                }
            }
        val game =
            engine.restore(
                board = board,
                pencilMarks = pencilMarks,
                hintsUsed = hintsUsed,
                currentHint = currentHint,
            )
        val savedStatus =
            runCatching { BalanceGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(sessionFormatVersion: Int): Map<String, String> {
        val values =
            lineSequence()
                .filter(String::isNotBlank)
                .associate { line ->
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid gameplay payload line." }
                    parts[0] to parts[1]
                }
        val expectedFields =
            if (sessionFormatVersion == 1) setOf("size", "cells", "hint") else setOf("size", "cells", "pencil", "hint")
        require(values.keys == expectedFields) { "Invalid gameplay payload fields." }
        return values
    }

    private fun Map<BalancePosition, Set<BalanceCell>>.toPayload(): String =
        entries
            .sortedWith(compareBy({ it.key.row }, { it.key.column }))
            .joinToString(separator = "|") { (position, marks) ->
                val symbols = marks.sortedBy(BalanceCell::ordinal).map { it.payloadSymbol }.joinToString(separator = "")
                "${position.row}:${position.column}=$symbols"
            }.ifEmpty { "-" }

    private fun String.decodePencilMarks(): Map<BalancePosition, Set<BalanceCell>> {
        if (isEmpty() || this == "-") return emptyMap()
        val entries =
            split('|').map { entry ->
                val parts = entry.split('=')
                require(parts.size == 2) { "Invalid saved pencil entry." }
                val coordinates = parts[0].split(':')
                require(coordinates.size == 2) { "Invalid saved pencil position." }
                val position =
                    BalancePosition(
                        row = coordinates[0].toIntOrNull() ?: error("Invalid saved pencil row."),
                        column = coordinates[1].toIntOrNull() ?: error("Invalid saved pencil column."),
                    )
                val marks = parts[1].map(::balanceCellFromPayloadSymbol).toSet()
                require(marks.isNotEmpty() && marks.size == parts[1].length) { "Invalid saved pencil marks." }
                position to marks
            }
        require(entries.size == entries.map { it.first }.toSet().size) { "Saved pencil marks contain duplicates." }
        return entries.toMap()
    }

    private fun BalanceHint.toPayload(): String {
        val evidence =
            evidencePositions
                .sortedWith(compareBy(BalancePosition::row, BalancePosition::column))
                .joinToString(separator = "|") { "${it.row}:${it.column}" }
                .ifEmpty { "-" }
        return listOf(
            kind.name,
            position.row,
            position.column,
            suggestedValue.name,
            technique?.name ?: "-",
            evidence,
        ).joinToString(separator = ",")
    }

    private val BalanceCell.payloadSymbol: Char
        get() =
            when (this) {
                BalanceCell.EMPTY -> '.'
                BalanceCell.ZERO -> '0'
                BalanceCell.ONE -> '1'
            }

    private fun balanceCellFromPayloadSymbol(symbol: Char?): BalanceCell =
        when (symbol) {
            '.' -> BalanceCell.EMPTY
            '0' -> BalanceCell.ZERO
            '1' -> BalanceCell.ONE
            else -> error("Invalid saved cell value.")
        }

    private val SUPPORTED_FORMAT_VERSIONS = 1..SESSION_FORMAT_VERSION
}
