package com.stanisryz.logica.puzzle.core.crowns

internal object CrownsBoardConstraints {
    fun requireValidSize(size: Int): Int {
        require(size > 0) { "Board size must be positive." }

        val cellCount = size.toLong() * size
        require(cellCount <= Int.MAX_VALUE) { "Board is too large." }
        return cellCount.toInt()
    }

    fun isInside(
        size: Int,
        position: CrownsPosition,
    ): Boolean = position.row < size && position.column < size

    fun requireInside(
        size: Int,
        position: CrownsPosition,
    ) {
        require(isInside(size, position)) {
            "Position $position is outside a $size x $size board."
        }
    }
}
