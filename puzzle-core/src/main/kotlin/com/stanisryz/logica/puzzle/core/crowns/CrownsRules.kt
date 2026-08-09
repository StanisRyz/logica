package com.stanisryz.logica.puzzle.core.crowns

import kotlin.math.abs

object CrownsRules {
    fun analyze(
        puzzle: CrownsPuzzle,
        state: CrownsState,
    ): CrownsRuleAnalysis {
        val orderedCrowns = state.crowns.sortedWith(compareBy(CrownsPosition::row, CrownsPosition::column))
        val outsideCrowns = orderedCrowns.filterNot { CrownsBoardConstraints.isInside(puzzle.size, it) }
        val boardCrowns = orderedCrowns.filter { CrownsBoardConstraints.isInside(puzzle.size, it) }
        val violations = mutableListOf<CrownsViolation>()

        if (outsideCrowns.isNotEmpty()) {
            violations += CrownsViolation(CrownsViolationType.POSITION_OUTSIDE_BOARD, outsideCrowns)
        }

        collectGroupConflicts(
            positions = boardCrowns,
            keySelector = CrownsPosition::row,
            type = CrownsViolationType.ROW_CONFLICT,
            violations = violations,
        )
        collectGroupConflicts(
            positions = boardCrowns,
            keySelector = CrownsPosition::column,
            type = CrownsViolationType.COLUMN_CONFLICT,
            violations = violations,
        )
        collectGroupConflicts(
            positions = boardCrowns,
            keySelector = puzzle::regionAt,
            type = CrownsViolationType.REGION_CONFLICT,
            violations = violations,
        )
        collectDiagonalConflicts(boardCrowns, violations)

        return CrownsRuleAnalysis(
            isComplete = outsideCrowns.isEmpty() && boardCrowns.size == puzzle.size,
            violations = violations.toList(),
        )
    }

    private fun <K> collectGroupConflicts(
        positions: List<CrownsPosition>,
        keySelector: (CrownsPosition) -> K,
        type: CrownsViolationType,
        violations: MutableList<CrownsViolation>,
    ) {
        positions
            .groupBy(keySelector)
            .values
            .filter { it.size > 1 }
            .forEach { conflictingPositions ->
                violations += CrownsViolation(type, conflictingPositions)
            }
    }

    private fun collectDiagonalConflicts(
        positions: List<CrownsPosition>,
        violations: MutableList<CrownsViolation>,
    ) {
        for (firstIndex in positions.indices) {
            for (secondIndex in firstIndex + 1 until positions.size) {
                val first = positions[firstIndex]
                val second = positions[secondIndex]
                if (abs(first.row - second.row) == 1 && abs(first.column - second.column) == 1) {
                    violations +=
                        CrownsViolation(
                            CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT,
                            listOf(first, second),
                        )
                }
            }
        }
    }
}
