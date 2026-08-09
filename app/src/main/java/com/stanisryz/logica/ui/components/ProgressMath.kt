package com.stanisryz.logica.ui.components

/**
 * Pure fractions for the Daily progress bar and the Word attempt bars. Both are drawn from counts
 * that can legitimately be zero, so the zero-data case must never divide by zero.
 */
internal fun progressFraction(
    completed: Int,
    total: Int,
): Float {
    if (total <= 0) return 0f
    return (completed.coerceIn(0, total).toFloat() / total)
}

/** A bar's share of the widest bar in the same group; an all-zero group draws no bars at all. */
internal fun barFraction(
    value: Int,
    maximum: Int,
): Float {
    if (maximum <= 0 || value <= 0) return 0f
    return (value.toFloat() / maximum).coerceAtMost(1f)
}
