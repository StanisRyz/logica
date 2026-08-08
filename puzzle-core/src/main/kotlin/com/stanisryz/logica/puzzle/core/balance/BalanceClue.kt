package com.stanisryz.logica.puzzle.core.balance

data class BalanceClue(
    val position: BalancePosition,
    val value: BalanceCell,
) {
    init {
        require(value != BalanceCell.EMPTY) { "A fixed clue must contain ZERO or ONE." }
    }
}
