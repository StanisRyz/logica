package com.stanisryz.logica.balance

import com.stanisryz.logica.puzzle.core.balance.BalanceCell
import com.stanisryz.logica.puzzle.core.balance.BalanceGameEngine
import com.stanisryz.logica.puzzle.core.balance.BalanceGameState
import com.stanisryz.logica.puzzle.core.balance.BalanceGameStatus
import com.stanisryz.logica.puzzle.core.balance.BalanceHint
import com.stanisryz.logica.puzzle.core.balance.BalanceHintProvider
import com.stanisryz.logica.puzzle.core.balance.BalanceMove
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
    const val SESSION_FORMAT_VERSION = 1

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
                append("hint=${game.currentHint?.toPayload() ?: "-"}")
            }
        val moveHistoryPayload =
            game.moveHistory.joinToString(separator = "\n") { move ->
                "${move.position.row},${move.position.column}," +
                    "${move.previousValue.payloadSymbol},${move.newValue.payloadSymbol}"
            }
        return EncodedBalanceSession(
            gameplayPayload = gameplayPayload,
            moveHistoryPayload = moveHistoryPayload,
            hintsUsed = game.hintsUsed,
            status = game.status.name,
        )
    }

    fun decode(
        puzzle: BalancePuzzle,
        sessionFormatVersion: Int,
        gameplayPayload: String,
        moveHistoryPayload: String,
        hintsUsed: Int,
        status: String,
    ): BalanceGameState {
        require(sessionFormatVersion == SESSION_FORMAT_VERSION) {
            "Unsupported Balance session format version: $sessionFormatVersion."
        }
        require(hintsUsed >= 0) { "Hints used must not be negative." }

        val gameplay = gameplayPayload.parseGameplayPayload()
        val size = gameplay.getValue("size").toIntOrNull() ?: error("Invalid saved board size.")
        require(size == puzzle.size) { "Saved board size does not match the regenerated puzzle." }
        val cells = gameplay.getValue("cells")
        require(cells.length == size * size) { "Saved board cell count is invalid." }
        val board =
            BalanceState.fromRows(
                cells.map(::balanceCellFromPayloadSymbol).chunked(size),
            )
        val moves = moveHistoryPayload.decodeMoves()
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
            BalanceGameEngine(puzzle).restore(
                board = board,
                moveHistory = moves,
                hintsUsed = hintsUsed,
                currentHint = currentHint,
            )
        val savedStatus =
            runCatching { BalanceGameStatus.valueOf(status) }
                .getOrElse { error("Invalid saved game status.") }
        require(game.status == savedStatus) { "Saved game status does not match the restored state." }
        return game
    }

    private fun String.parseGameplayPayload(): Map<String, String> {
        val values =
            lineSequence()
                .filter(String::isNotBlank)
                .associate { line ->
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank()) { "Invalid gameplay payload line." }
                    parts[0] to parts[1]
                }
        require(values.keys == setOf("size", "cells", "hint")) { "Invalid gameplay payload fields." }
        return values
    }

    private fun String.decodeMoves(): List<BalanceMove> {
        if (isBlank()) return emptyList()
        return lineSequence()
            .map { line ->
                val parts = line.split(',')
                require(parts.size == 4) { "Invalid move history entry." }
                val row = parts[0].toIntOrNull() ?: error("Invalid move row.")
                val column = parts[1].toIntOrNull() ?: error("Invalid move column.")
                BalanceMove(
                    position = BalancePosition(row, column),
                    previousValue = balanceCellFromPayloadSymbol(parts[2].singleOrNull()),
                    newValue = balanceCellFromPayloadSymbol(parts[3].singleOrNull()),
                )
            }.toList()
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
}
