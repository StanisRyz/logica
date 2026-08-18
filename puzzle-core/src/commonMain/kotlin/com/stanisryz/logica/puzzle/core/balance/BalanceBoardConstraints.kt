package com.stanisryz.logica.puzzle.core.balance

internal object BalanceBoardConstraints {
    fun requireValidSize(size: Int): Int {
        require(size > 0) { "Board size must be positive." }
        require(size % 2 == 0) { "Board size must be even." }

        val cellCount = size.toLong() * size
        require(cellCount <= Int.MAX_VALUE) { "Board is too large." }
        return cellCount.toInt()
    }

    fun requireInside(
        size: Int,
        position: BalancePosition,
    ) {
        require(position.row < size && position.column < size) {
            "Position $position is outside a $size x $size board."
        }
    }

    fun requireLineIndex(
        size: Int,
        index: Int,
    ) {
        require(index in 0 until size) { "Line index $index is outside the board." }
    }

    fun cellIndex(
        size: Int,
        position: BalancePosition,
    ): Int {
        requireInside(size, position)
        return position.row * size + position.column
    }
}
