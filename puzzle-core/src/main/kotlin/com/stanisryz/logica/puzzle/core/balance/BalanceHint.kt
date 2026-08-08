package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleHint

enum class BalanceHintKind {
    LOGICAL_DEDUCTION,
    INCORRECT_VALUE,
}

class BalanceHint internal constructor(
    val kind: BalanceHintKind,
    val position: BalancePosition,
    val suggestedValue: BalanceCell,
    val technique: BalanceLogicTechnique?,
    evidencePositions: Iterable<BalancePosition> = emptyList(),
) : PuzzleHint {
    val evidencePositions: Set<BalancePosition> = evidencePositions.toSet()

    init {
        require(suggestedValue != BalanceCell.EMPTY) { "A hint must suggest a value." }
        require((kind == BalanceHintKind.LOGICAL_DEDUCTION) == (technique != null)) {
            "Only logical deduction hints have a technique."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BalanceHint &&
            kind == other.kind &&
            position == other.position &&
            suggestedValue == other.suggestedValue &&
            technique == other.technique &&
            evidencePositions == other.evidencePositions

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + suggestedValue.hashCode()
        result = 31 * result + (technique?.hashCode() ?: 0)
        result = 31 * result + evidencePositions.hashCode()
        return result
    }

    override fun toString(): String =
        "BalanceHint(kind=$kind, position=$position, suggestedValue=$suggestedValue, " +
            "technique=$technique, evidencePositions=$evidencePositions)"
}
