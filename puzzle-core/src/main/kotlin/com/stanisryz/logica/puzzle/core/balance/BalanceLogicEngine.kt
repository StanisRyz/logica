package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.ValidationResult

class BalanceLogicEngine {
    fun nextStep(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): BalanceLogicStep? {
        if (BalanceRules.validate(puzzle, state) !is ValidationResult.ValidPartial) return null

        val lines = lines(state)
        return findStep(puzzle, state, lines, BalanceLogicTechnique.PREVENT_THREE) { line, index, _ ->
            BalanceRules.requiredByTriple(line, index)
        } ?: findStep(puzzle, state, lines, BalanceLogicTechnique.COMPLETE_QUOTA) { line, _, _ ->
            BalanceRules.requiredByQuota(line)
        } ?: findStep(puzzle, state, lines, BalanceLogicTechnique.PRESERVE_UNIQUENESS) { line, index, peers ->
            BalanceRules.requiredByUniqueness(line, index, peers)
        }
    }

    private fun findStep(
        puzzle: BalancePuzzle,
        state: BalanceState,
        lines: List<Line>,
        technique: BalanceLogicTechnique,
        requiredValue: (List<BalanceCell>, Int, List<List<BalanceCell>>) -> BalanceCell?,
    ): BalanceLogicStep? {
        for (line in lines) {
            val cells = line.cells(state)
            val peers = lines.filter { it.orientation == line.orientation }.map { it.cells(state) }
            for (cellIndex in cells.indices) {
                if (cells[cellIndex] != BalanceCell.EMPTY) continue
                val value = requiredValue(cells, cellIndex, peers) ?: continue
                val position = line.position(cellIndex)
                if (value in BalanceRules.validCandidates(puzzle, state, position)) {
                    return BalanceLogicStep(position, value, technique)
                }
            }
        }
        return null
    }

    private fun lines(state: BalanceState): List<Line> =
        buildList(state.size * 2) {
            repeat(state.size) { add(Line(LineOrientation.ROW, it)) }
            repeat(state.size) { add(Line(LineOrientation.COLUMN, it)) }
        }

    private enum class LineOrientation {
        ROW,
        COLUMN,
    }

    private data class Line(
        val orientation: LineOrientation,
        val index: Int,
    ) {
        fun cells(state: BalanceState): List<BalanceCell> =
            when (orientation) {
                LineOrientation.ROW -> state.row(index)
                LineOrientation.COLUMN -> state.column(index)
            }

        fun position(cellIndex: Int): BalancePosition =
            when (orientation) {
                LineOrientation.ROW -> BalancePosition(row = index, column = cellIndex)
                LineOrientation.COLUMN -> BalancePosition(row = cellIndex, column = index)
            }
    }
}
