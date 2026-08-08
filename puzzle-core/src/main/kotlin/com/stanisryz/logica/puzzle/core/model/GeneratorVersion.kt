package com.stanisryz.logica.puzzle.core.model

@JvmInline
value class GeneratorVersion(
    val value: Int,
) {
    init {
        require(value > 0) { "Generator version must be positive." }
    }
}
