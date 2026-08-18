package com.stanisryz.logica.puzzle.core.crowns

class CrownsLogicEngine {
    fun nextStep(
        puzzle: CrownsPuzzle,
        state: CrownsState,
    ): CrownsLogicStep? = nextStep(puzzle, CrownsCandidateState.from(puzzle, state))

    internal fun nextStep(
        puzzle: CrownsPuzzle,
        state: CrownsCandidateState,
    ): CrownsLogicStep? {
        if (state.isContradictory(puzzle)) return null

        return findSingleCandidate(puzzle, state, CrownsConstraintGroupType.ROW)
            ?: findSingleCandidate(puzzle, state, CrownsConstraintGroupType.COLUMN)
            ?: findSingleCandidate(puzzle, state, CrownsConstraintGroupType.REGION)
            ?: findRegionLock(puzzle, state, CrownsConstraintGroupType.ROW)
            ?: findRegionLock(puzzle, state, CrownsConstraintGroupType.COLUMN)
    }

    private fun findSingleCandidate(
        puzzle: CrownsPuzzle,
        state: CrownsCandidateState,
        groupType: CrownsConstraintGroupType,
    ): CrownsLogicStep? {
        constraintGroups(puzzle)
            .filter { it.type == groupType && !it.isSatisfiedBy(state) }
            .forEach { group ->
                val candidates = group.candidatesIn(state)
                if (candidates.size == 1) {
                    return CrownsLogicStep.PlaceCrown(
                        position = candidates.single(),
                        technique = groupType.singleCandidateTechnique(),
                        evidencePositions = group.positions.filter { it in state.excluded },
                    )
                }
            }
        return null
    }

    private fun findRegionLock(
        puzzle: CrownsPuzzle,
        state: CrownsCandidateState,
        lineType: CrownsConstraintGroupType,
    ): CrownsLogicStep? {
        constraintGroups(puzzle)
            .filter { it.type == CrownsConstraintGroupType.REGION && !it.isSatisfiedBy(state) }
            .forEach { region ->
                val regionCandidates = region.candidatesIn(state)
                if (regionCandidates.size <= 1) return@forEach
                val lineIndexes =
                    regionCandidates
                        .map { position -> position.lineIndex(lineType) }
                        .distinct()
                if (lineIndexes.size != 1) return@forEach

                val lockedLineIndex = lineIndexes.single()
                val targets =
                    orderedPositions(puzzle).filter { position ->
                        position in state.allowed &&
                            position.lineIndex(lineType) == lockedLineIndex &&
                            position !in region.positions
                    }
                if (targets.isNotEmpty()) {
                    return CrownsLogicStep.ExcludePositions(
                        positions = targets,
                        technique = lineType.regionLockTechnique(),
                        evidencePositions = regionCandidates,
                    )
                }
            }
        return null
    }

    private fun CrownsConstraintGroupType.singleCandidateTechnique(): CrownsLogicTechnique =
        when (this) {
            CrownsConstraintGroupType.ROW -> CrownsLogicTechnique.SINGLE_CANDIDATE_ROW
            CrownsConstraintGroupType.COLUMN -> CrownsLogicTechnique.SINGLE_CANDIDATE_COLUMN
            CrownsConstraintGroupType.REGION -> CrownsLogicTechnique.SINGLE_CANDIDATE_REGION
        }

    private fun CrownsConstraintGroupType.regionLockTechnique(): CrownsLogicTechnique =
        when (this) {
            CrownsConstraintGroupType.ROW -> CrownsLogicTechnique.REGION_LOCKED_TO_ROW
            CrownsConstraintGroupType.COLUMN -> CrownsLogicTechnique.REGION_LOCKED_TO_COLUMN
            CrownsConstraintGroupType.REGION -> error("A region cannot be used as a line lock.")
        }

    private fun CrownsPosition.lineIndex(type: CrownsConstraintGroupType): Int =
        when (type) {
            CrownsConstraintGroupType.ROW -> row
            CrownsConstraintGroupType.COLUMN -> column
            CrownsConstraintGroupType.REGION -> error("A region is not a line.")
        }
}
