package com.stanisryz.logica.puzzle.core.crowns

internal class CrownsCandidateState private constructor(
    confirmed: Iterable<CrownsPosition>,
    allowed: Iterable<CrownsPosition>,
    excluded: Iterable<CrownsPosition>,
) {
    val confirmed: Set<CrownsPosition> = confirmed.toSet()
    val allowed: Set<CrownsPosition> = allowed.toSet()
    val excluded: Set<CrownsPosition> = excluded.toSet()

    fun asState(): CrownsState = CrownsState(confirmed)

    fun isContradictory(puzzle: CrownsPuzzle): Boolean =
        CrownsRules.analyze(puzzle, asState()).violations.isNotEmpty() ||
            constraintGroups(puzzle).any { group ->
                !group.isSatisfiedBy(this) && group.candidatesIn(this).isEmpty()
            }

    fun placeCrown(
        puzzle: CrownsPuzzle,
        position: CrownsPosition,
    ): CrownsCandidateTransition {
        require(position in allowed) { "A crown must be placed at an allowed position." }
        val eliminated =
            orderedPositions(puzzle).filter { candidate ->
                candidate in allowed && candidate != position && CrownsRules.positionsConflict(puzzle, position, candidate)
            }
        return CrownsCandidateTransition(
            state =
                CrownsCandidateState(
                    confirmed = confirmed + position,
                    allowed = allowed - position - eliminated.toSet(),
                    excluded = excluded + eliminated,
                ),
            eliminatedPositions = eliminated.toSet(),
        )
    }

    fun exclude(positions: Iterable<CrownsPosition>): CrownsCandidateTransition {
        val eliminated = positions.toSet().intersect(allowed)
        require(eliminated.isNotEmpty()) { "An exclusion step must remove at least one allowed position." }
        return CrownsCandidateTransition(
            state =
                CrownsCandidateState(
                    confirmed = confirmed,
                    allowed = allowed - eliminated,
                    excluded = excluded + eliminated,
                ),
            eliminatedPositions = eliminated,
        )
    }

    fun apply(
        puzzle: CrownsPuzzle,
        step: CrownsLogicStep,
    ): CrownsCandidateTransition =
        when (step) {
            is CrownsLogicStep.PlaceCrown -> placeCrown(puzzle, step.position)
            is CrownsLogicStep.ExcludePositions -> exclude(step.targetPositions)
        }

    companion object {
        fun from(
            puzzle: CrownsPuzzle,
            state: CrownsState = CrownsState(),
        ): CrownsCandidateState {
            val orderedPositions = orderedPositions(puzzle)
            val confirmed = state.crowns
            val boardConfirmed = confirmed.filter { CrownsBoardConstraints.isInside(puzzle.size, it) }
            val allowed =
                orderedPositions.filter { candidate ->
                    candidate !in confirmed &&
                        boardConfirmed.none { crown -> CrownsRules.positionsConflict(puzzle, crown, candidate) }
                }
            return CrownsCandidateState(
                confirmed = confirmed,
                allowed = allowed,
                excluded = orderedPositions.filter { it !in confirmed && it !in allowed },
            )
        }
    }
}

internal data class CrownsCandidateTransition(
    val state: CrownsCandidateState,
    val eliminatedPositions: Set<CrownsPosition>,
)

internal enum class CrownsConstraintGroupType {
    ROW,
    COLUMN,
    REGION,
}

internal data class CrownsConstraintGroup(
    val type: CrownsConstraintGroupType,
    val identifier: Int,
    val positions: List<CrownsPosition>,
) {
    fun isSatisfiedBy(state: CrownsCandidateState): Boolean = positions.any { it in state.confirmed }

    fun candidatesIn(state: CrownsCandidateState): List<CrownsPosition> = positions.filter { it in state.allowed }
}

internal fun constraintGroups(puzzle: CrownsPuzzle): List<CrownsConstraintGroup> =
    buildList(puzzle.size * 3) {
        repeat(puzzle.size) { row ->
            add(
                CrownsConstraintGroup(
                    type = CrownsConstraintGroupType.ROW,
                    identifier = row,
                    positions = orderedPositions(puzzle).filter { it.row == row },
                ),
            )
        }
        repeat(puzzle.size) { column ->
            add(
                CrownsConstraintGroup(
                    type = CrownsConstraintGroupType.COLUMN,
                    identifier = column,
                    positions = orderedPositions(puzzle).filter { it.column == column },
                ),
            )
        }
        puzzle.regionAssignments.values
            .distinct()
            .sortedBy(RegionId::value)
            .forEach { regionId ->
                add(
                    CrownsConstraintGroup(
                        type = CrownsConstraintGroupType.REGION,
                        identifier = regionId.value,
                        positions = orderedPositions(puzzle).filter { puzzle.regionAt(it) == regionId },
                    ),
                )
            }
    }

internal fun orderedPositions(puzzle: CrownsPuzzle): List<CrownsPosition> =
    buildList(puzzle.size * puzzle.size) {
        repeat(puzzle.size) { row ->
            repeat(puzzle.size) { column ->
                add(CrownsPosition(row, column))
            }
        }
    }
