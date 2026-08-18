package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleState

class CrownsState(
    crowns: Iterable<CrownsPosition> = emptySet(),
) : PuzzleState {
    val crowns: Set<CrownsPosition> = crowns.toSet()

    override fun equals(other: Any?): Boolean = this === other || other is CrownsState && crowns == other.crowns

    override fun hashCode(): Int = crowns.hashCode()

    override fun toString(): String = "CrownsState(crowns=$crowns)"
}
