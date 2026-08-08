package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.ValidationResult

internal object BalanceRules {
    private val candidateOrder = listOf(BalanceCell.ZERO, BalanceCell.ONE)

    fun validate(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): ValidationResult {
        val analysis = analyze(puzzle, state)
        analysis.violations.firstOrNull()?.let { violation ->
            return ValidationResult.Invalid(violation.validationReason())
        }
        return if (analysis.isComplete) ValidationResult.ValidComplete else ValidationResult.ValidPartial
    }

    fun analyze(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): BalanceRuleAnalysis {
        if (state.size != puzzle.size) {
            return BalanceRuleAnalysis(
                isComplete = false,
                violations = listOf(BalanceViolation(BalanceViolationType.BOARD_SIZE_MISMATCH, emptyList())),
            )
        }

        val violations = mutableListOf<BalanceViolation>()
        puzzle.fixedClues.forEach { (position, value) ->
            if (state.cellAt(position) != value) {
                violations += BalanceViolation(BalanceViolationType.FIXED_CLUE_CONFLICT, listOf(position))
            }
        }

        val rows = List(puzzle.size, state::row)
        val columns = List(puzzle.size, state::column)

        collectLineViolations(rows, LineOrientation.ROW, violations)
        collectLineViolations(columns, LineOrientation.COLUMN, violations)
        collectDuplicateLineViolations(rows, LineOrientation.ROW, violations)
        collectDuplicateLineViolations(columns, LineOrientation.COLUMN, violations)

        return BalanceRuleAnalysis(
            isComplete = state.isComplete(),
            violations = violations.toList(),
        )
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

    private fun collectLineViolations(
        lines: List<List<BalanceCell>>,
        orientation: LineOrientation,
        violations: MutableList<BalanceViolation>,
    ) {
        lines.forEachIndexed { index, line ->
            val halfSize = line.size / 2
            val zeroCount = line.count { it == BalanceCell.ZERO }
            val oneCount = line.count { it == BalanceCell.ONE }

            if (zeroCount > halfSize || oneCount > halfSize) {
                violations +=
                    BalanceViolation(
                        type = orientation.unbalancedType,
                        affectedPositions = line.indices.map { orientation.position(index, it) },
                    )
            }

            for (start in 0..(line.size - 3)) {
                val value = line[start]
                if (value != BalanceCell.EMPTY && value == line[start + 1] && value == line[start + 2]) {
                    violations +=
                        BalanceViolation(
                            type = orientation.tripleType,
                            affectedPositions = (start..start + 2).map { orientation.position(index, it) },
                        )
                }
            }
        }
    }

    private fun collectDuplicateLineViolations(
        lines: List<List<BalanceCell>>,
        orientation: LineOrientation,
        violations: MutableList<BalanceViolation>,
    ) {
        for (firstIndex in lines.indices) {
            if (BalanceCell.EMPTY in lines[firstIndex]) continue
            for (secondIndex in firstIndex + 1 until lines.size) {
                if (lines[firstIndex] == lines[secondIndex]) {
                    violations +=
                        BalanceViolation(
                            type = orientation.duplicateType,
                            affectedPositions =
                                lines[firstIndex].indices.map { cellIndex -> orientation.position(firstIndex, cellIndex) } +
                                    lines[secondIndex].indices.map { cellIndex ->
                                        orientation.position(secondIndex, cellIndex)
                                    },
                        )
                }
            }
        }
    }

    private fun BalanceViolation.validationReason(): String =
        when (type) {
            BalanceViolationType.BOARD_SIZE_MISMATCH -> "State board size does not match the puzzle."
            BalanceViolationType.FIXED_CLUE_CONFLICT ->
                "Fixed clue at ${affectedPositions.first()} was changed."
            BalanceViolationType.UNBALANCED_ROW ->
                "Row ${affectedPositions.first().row + 1} is unbalanced."
            BalanceViolationType.UNBALANCED_COLUMN ->
                "Column ${affectedPositions.first().column + 1} is unbalanced."
            BalanceViolationType.THREE_EQUAL_HORIZONTAL ->
                "Row ${affectedPositions.first().row + 1} contains three equal values in a row."
            BalanceViolationType.THREE_EQUAL_VERTICAL ->
                "Column ${affectedPositions.first().column + 1} contains three equal values in a row."
            BalanceViolationType.DUPLICATE_ROWS -> "Completed rows must be unique."
            BalanceViolationType.DUPLICATE_COLUMNS -> "Completed columns must be unique."
        }

    private enum class LineOrientation(
        val unbalancedType: BalanceViolationType,
        val tripleType: BalanceViolationType,
        val duplicateType: BalanceViolationType,
    ) {
        ROW(
            BalanceViolationType.UNBALANCED_ROW,
            BalanceViolationType.THREE_EQUAL_HORIZONTAL,
            BalanceViolationType.DUPLICATE_ROWS,
        ),
        COLUMN(
            BalanceViolationType.UNBALANCED_COLUMN,
            BalanceViolationType.THREE_EQUAL_VERTICAL,
            BalanceViolationType.DUPLICATE_COLUMNS,
        ),
        ;

        fun position(
            lineIndex: Int,
            cellIndex: Int,
        ): BalancePosition =
            when (this) {
                ROW -> BalancePosition(row = lineIndex, column = cellIndex)
                COLUMN -> BalancePosition(row = cellIndex, column = lineIndex)
            }
    }

    private fun BalanceCell.opposite(): BalanceCell =
        when (this) {
            BalanceCell.ZERO -> BalanceCell.ONE
            BalanceCell.ONE -> BalanceCell.ZERO
            BalanceCell.EMPTY -> error("EMPTY has no opposite value.")
        }
}
