package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleValidator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult

class BalanceValidator : PuzzleValidator<BalancePuzzle, BalanceState> {
    override fun validate(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): ValidationResult = BalanceRules.validate(puzzle, state)
}
