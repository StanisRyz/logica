package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleValidator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult

class CrownsValidator : PuzzleValidator<CrownsPuzzle, CrownsState> {
    override fun validate(
        puzzle: CrownsPuzzle,
        state: CrownsState,
    ): ValidationResult {
        val analysis = CrownsRules.analyze(puzzle, state)
        analysis.violations.firstOrNull()?.let { violation ->
            return ValidationResult.Invalid(violation.validationReason())
        }
        return if (analysis.isComplete) ValidationResult.ValidComplete else ValidationResult.ValidPartial
    }

    private fun CrownsViolation.validationReason(): String =
        when (type) {
            CrownsViolationType.POSITION_OUTSIDE_BOARD -> "A crown is outside the puzzle board."
            CrownsViolationType.ROW_CONFLICT -> "A row contains more than one crown."
            CrownsViolationType.COLUMN_CONFLICT -> "A column contains more than one crown."
            CrownsViolationType.REGION_CONFLICT -> "A region contains more than one crown."
            CrownsViolationType.DIAGONAL_ADJACENCY_CONFLICT -> "Two crowns touch diagonally."
        }
}
