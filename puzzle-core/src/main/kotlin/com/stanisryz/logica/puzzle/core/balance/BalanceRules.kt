package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.ValidationResult

internal object BalanceRules {
    fun validate(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): ValidationResult {
        if (state.size != puzzle.size) {
            return ValidationResult.Invalid("State board size does not match the puzzle.")
        }

        puzzle.fixedClues.forEach { (position, value) ->
            if (state.cellAt(position) != value) {
                return ValidationResult.Invalid("Fixed clue at $position was changed.")
            }
        }

        val rows = List(puzzle.size, state::row)
        val columns = List(puzzle.size, state::column)

        validateLines(rows, "Row")?.let { return it }
        validateLines(columns, "Column")?.let { return it }
        findDuplicateCompleteLine(rows, "rows")?.let { return it }
        findDuplicateCompleteLine(columns, "columns")?.let { return it }

        return if (state.isComplete()) {
            ValidationResult.ValidComplete
        } else {
            ValidationResult.ValidPartial
        }
    }

    private fun validateLines(
        lines: List<List<BalanceCell>>,
        label: String,
    ): ValidationResult.Invalid? {
        lines.forEachIndexed { index, line ->
            val halfSize = line.size / 2
            val zeroCount = line.count { it == BalanceCell.ZERO }
            val oneCount = line.count { it == BalanceCell.ONE }

            if (zeroCount > halfSize || oneCount > halfSize) {
                return ValidationResult.Invalid("$label ${index + 1} is unbalanced.")
            }
            if (hasThreeConsecutiveEqualValues(line)) {
                return ValidationResult.Invalid("$label ${index + 1} contains three equal values in a row.")
            }
            if (BalanceCell.EMPTY !in line && (zeroCount != halfSize || oneCount != halfSize)) {
                return ValidationResult.Invalid("$label ${index + 1} is not evenly balanced.")
            }
        }
        return null
    }

    private fun hasThreeConsecutiveEqualValues(line: List<BalanceCell>): Boolean {
        for (index in 0..line.size - 3) {
            val value = line[index]
            if (value != BalanceCell.EMPTY && value == line[index + 1] && value == line[index + 2]) {
                return true
            }
        }
        return false
    }

    private fun findDuplicateCompleteLine(
        lines: List<List<BalanceCell>>,
        label: String,
    ): ValidationResult.Invalid? {
        val completedLines = mutableSetOf<List<BalanceCell>>()
        lines.filter { BalanceCell.EMPTY !in it }.forEach { line ->
            if (!completedLines.add(line)) {
                return ValidationResult.Invalid("Completed $label must be unique.")
            }
        }
        return null
    }
}
