package com.stanisryz.logica.puzzle.core.balance

enum class BalanceViolationType {
    BOARD_SIZE_MISMATCH,
    UNBALANCED_ROW,
    UNBALANCED_COLUMN,
    THREE_EQUAL_HORIZONTAL,
    THREE_EQUAL_VERTICAL,
    DUPLICATE_ROWS,
    DUPLICATE_COLUMNS,
    FIXED_CLUE_CONFLICT,
}

class BalanceViolation internal constructor(
    val type: BalanceViolationType,
    affectedPositions: Iterable<BalancePosition>,
) {
    val affectedPositions: Set<BalancePosition> = affectedPositions.toSet()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BalanceViolation &&
            type == other.type &&
            affectedPositions == other.affectedPositions

    override fun hashCode(): Int = 31 * type.hashCode() + affectedPositions.hashCode()

    override fun toString(): String = "BalanceViolation(type=$type, affectedPositions=$affectedPositions)"
}

class BalanceDiagnostics {
    fun violations(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): List<BalanceViolation> = BalanceRules.analyze(puzzle, state).violations
}

internal data class BalanceRuleAnalysis(
    val isComplete: Boolean,
    val violations: List<BalanceViolation>,
)
