package com.stanisryz.logica.puzzle.core.crowns

enum class CrownsViolationType {
    POSITION_OUTSIDE_BOARD,
    ROW_CONFLICT,
    COLUMN_CONFLICT,
    REGION_CONFLICT,
    DIAGONAL_ADJACENCY_CONFLICT,
}

class CrownsViolation internal constructor(
    val type: CrownsViolationType,
    affectedPositions: Iterable<CrownsPosition>,
) {
    val affectedPositions: Set<CrownsPosition> = affectedPositions.toSet()

    init {
        require(this.affectedPositions.isNotEmpty()) { "A violation must affect at least one position." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsViolation &&
            type == other.type &&
            affectedPositions == other.affectedPositions

    override fun hashCode(): Int = 31 * type.hashCode() + affectedPositions.hashCode()

    override fun toString(): String = "CrownsViolation(type=$type, affectedPositions=$affectedPositions)"
}

data class CrownsRuleAnalysis internal constructor(
    val isComplete: Boolean,
    val violations: List<CrownsViolation>,
)
