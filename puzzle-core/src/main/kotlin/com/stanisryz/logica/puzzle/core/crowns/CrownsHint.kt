package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleHint

enum class CrownsHintKind {
    LOGICAL_DEDUCTION,
    INCORRECT_CROWN,
    INCORRECT_MARK,
}

enum class CrownsHintAction {
    PLACE_CROWN,
    MARK_POSITIONS,
    CLEAR_CROWN,
    CLEAR_MARK,
}

class CrownsHint internal constructor(
    val kind: CrownsHintKind,
    val action: CrownsHintAction,
    targetPositions: Iterable<CrownsPosition>,
    evidencePositions: Iterable<CrownsPosition> = emptySet(),
    conflictPositions: Iterable<CrownsPosition> = emptySet(),
    val technique: CrownsLogicTechnique? = null,
) : PuzzleHint {
    val targetPositions: Set<CrownsPosition> = targetPositions.toSet()
    val evidencePositions: Set<CrownsPosition> = evidencePositions.toSet()
    val conflictPositions: Set<CrownsPosition> = conflictPositions.toSet()

    init {
        require(this.targetPositions.isNotEmpty()) { "A hint must have at least one target position." }
        require((kind == CrownsHintKind.LOGICAL_DEDUCTION) == (technique != null)) {
            "Only logical deduction hints have a technique."
        }
        require(action != CrownsHintAction.PLACE_CROWN || this.targetPositions.size == 1) {
            "A crown-placement hint must target exactly one position."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsHint &&
            kind == other.kind &&
            action == other.action &&
            targetPositions == other.targetPositions &&
            evidencePositions == other.evidencePositions &&
            conflictPositions == other.conflictPositions &&
            technique == other.technique

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + targetPositions.hashCode()
        result = 31 * result + evidencePositions.hashCode()
        result = 31 * result + conflictPositions.hashCode()
        result = 31 * result + (technique?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "CrownsHint(kind=$kind, action=$action, targetPositions=$targetPositions, " +
            "evidencePositions=$evidencePositions, conflictPositions=$conflictPositions, technique=$technique)"
}
