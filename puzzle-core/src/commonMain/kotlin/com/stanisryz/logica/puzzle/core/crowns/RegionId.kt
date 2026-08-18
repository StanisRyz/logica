package com.stanisryz.logica.puzzle.core.crowns

import kotlin.jvm.JvmInline

@JvmInline
value class RegionId(
    val value: Int,
) {
    init {
        require(value >= 0) { "Region ID must not be negative." }
    }
}
