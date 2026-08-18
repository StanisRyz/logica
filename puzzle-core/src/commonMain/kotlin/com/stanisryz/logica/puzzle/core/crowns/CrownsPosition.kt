package com.stanisryz.logica.puzzle.core.crowns

data class CrownsPosition(
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "Row must not be negative." }
        require(column >= 0) { "Column must not be negative." }
    }
}
