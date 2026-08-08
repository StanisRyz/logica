package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.ValidationResult

internal object BalanceRules {
    private val candidateOrder = listOf(BalanceCell.ZERO, BalanceCell.ONE)

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

    fun validCandidates(
        puzzle: BalancePuzzle,
        state: BalanceState,
        position: BalancePosition,
    ): List<BalanceCell> {
        if (state.size != puzzle.size || state.cellAt(position) != BalanceCell.EMPTY) {
            return emptyList()
        }

        return candidateOrder.filter { candidate ->
            validate(puzzle, state.withCell(position, candidate)) !is ValidationResult.Invalid
        }
    }

    fun requiredByTriple(
        line: List<BalanceCell>,
        emptyIndex: Int,
    ): BalanceCell? {
        if (line[emptyIndex] != BalanceCell.EMPTY) return null

        val windowStarts =
            (emptyIndex - 2..emptyIndex)
                .filter { start -> start >= 0 && start + 2 < line.size }

        return windowStarts.firstNotNullOfOrNull { start ->
            val otherValues =
                (start..start + 2)
                    .filter { it != emptyIndex }
                    .map(line::get)
            otherValues
                .takeIf { values -> values[0] != BalanceCell.EMPTY && values[0] == values[1] }
                ?.first()
                ?.opposite()
        }
    }

    fun requiredByQuota(line: List<BalanceCell>): BalanceCell? {
        val halfSize = line.size / 2
        return when {
            line.count { it == BalanceCell.ZERO } == halfSize -> BalanceCell.ONE
            line.count { it == BalanceCell.ONE } == halfSize -> BalanceCell.ZERO
            else -> null
        }
    }

    fun requiredByUniqueness(
        line: List<BalanceCell>,
        emptyIndex: Int,
        peerLines: List<List<BalanceCell>>,
    ): BalanceCell? {
        val emptyIndexes = line.indices.filter { line[it] == BalanceCell.EMPTY }
        if (emptyIndexes.size != 2 || emptyIndex !in emptyIndexes) return null

        val requiredValues =
            peerLines
                .asSequence()
                .filter { BalanceCell.EMPTY !in it }
                .filter { completed ->
                    line.indices.all { index ->
                        line[index] == BalanceCell.EMPTY || line[index] == completed[index]
                    }
                }.map { completed -> completed[emptyIndex].opposite() }
                .distinct()
                .toList()

        return requiredValues.singleOrNull()
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

    private fun BalanceCell.opposite(): BalanceCell =
        when (this) {
            BalanceCell.ZERO -> BalanceCell.ONE
            BalanceCell.ONE -> BalanceCell.ZERO
            BalanceCell.EMPTY -> error("EMPTY has no opposite value.")
        }
}
