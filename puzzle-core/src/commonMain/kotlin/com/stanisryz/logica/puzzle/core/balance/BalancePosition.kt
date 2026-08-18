package com.stanisryz.logica.puzzle.core.balance

data class BalancePosition(
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "Row must not be negative." }
        require(column >= 0) { "Column must not be negative." }
    }
}
