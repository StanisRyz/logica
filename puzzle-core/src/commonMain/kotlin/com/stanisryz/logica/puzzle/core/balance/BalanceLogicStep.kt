package com.stanisryz.logica.puzzle.core.balance

enum class BalanceLogicTechnique {
    PREVENT_THREE,
    COMPLETE_QUOTA,
    PRESERVE_UNIQUENESS,
}

data class BalanceLogicStep(
    val position: BalancePosition,
    val value: BalanceCell,
    val technique: BalanceLogicTechnique,
) {
    init {
        require(value != BalanceCell.EMPTY) { "A logic step must place a value." }
    }

    fun applyTo(state: BalanceState): BalanceState {
        require(state.cellAt(position) == BalanceCell.EMPTY) { "A logic step must target an empty cell." }
        return state.withCell(position, value)
    }
}
