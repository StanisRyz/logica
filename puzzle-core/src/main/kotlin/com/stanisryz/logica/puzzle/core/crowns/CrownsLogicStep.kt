package com.stanisryz.logica.puzzle.core.crowns

enum class CrownsLogicTechnique {
    SINGLE_CANDIDATE_ROW,
    SINGLE_CANDIDATE_COLUMN,
    SINGLE_CANDIDATE_REGION,
    REGION_LOCKED_TO_ROW,
    REGION_LOCKED_TO_COLUMN,
}

enum class CrownsLogicAction {
    PLACE_CROWN,
    EXCLUDE_POSITIONS,
}

sealed class CrownsLogicStep protected constructor(
    val action: CrownsLogicAction,
    val technique: CrownsLogicTechnique,
    targetPositions: Iterable<CrownsPosition>,
    evidencePositions: Iterable<CrownsPosition>,
) {
    val targetPositions: Set<CrownsPosition> = targetPositions.toSet()
    val evidencePositions: Set<CrownsPosition> = evidencePositions.toSet()

    init {
        require(this.targetPositions.isNotEmpty()) { "A logic step must have at least one target." }
    }

    class PlaceCrown(
        val position: CrownsPosition,
        technique: CrownsLogicTechnique,
        evidencePositions: Iterable<CrownsPosition> = emptySet(),
    ) : CrownsLogicStep(
            action = CrownsLogicAction.PLACE_CROWN,
            technique = technique,
            targetPositions = listOf(position),
            evidencePositions = evidencePositions,
        )

    class ExcludePositions(
        positions: Iterable<CrownsPosition>,
        technique: CrownsLogicTechnique,
        evidencePositions: Iterable<CrownsPosition> = emptySet(),
    ) : CrownsLogicStep(
            action = CrownsLogicAction.EXCLUDE_POSITIONS,
            technique = technique,
            targetPositions = positions,
            evidencePositions = evidencePositions,
        )

    final override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsLogicStep &&
            action == other.action &&
            technique == other.technique &&
            targetPositions == other.targetPositions &&
            evidencePositions == other.evidencePositions

    final override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + technique.hashCode()
        result = 31 * result + targetPositions.hashCode()
        result = 31 * result + evidencePositions.hashCode()
        return result
    }

    final override fun toString(): String =
        "CrownsLogicStep(action=$action, technique=$technique, " +
            "targetPositions=$targetPositions, evidencePositions=$evidencePositions)"
}
