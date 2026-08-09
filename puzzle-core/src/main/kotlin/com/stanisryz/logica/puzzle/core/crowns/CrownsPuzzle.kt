package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleType

class CrownsPuzzle(
    override val id: PuzzleId,
    val size: Int,
    regionAssignments: Map<CrownsPosition, RegionId>,
) : PuzzleDefinition {
    val regionAssignments: Map<CrownsPosition, RegionId>

    init {
        require(id.type == PuzzleType.CROWNS) { "Crowns puzzle ID must have CROWNS type." }
        val expectedCellCount = CrownsBoardConstraints.requireValidSize(size)
        require(regionAssignments.size == expectedCellCount) {
            "Expected $expectedCellCount region assignments, but found ${regionAssignments.size}."
        }

        regionAssignments.keys.forEach { position ->
            CrownsBoardConstraints.requireInside(size, position)
        }

        val distinctRegions = regionAssignments.values.toSet()
        require(distinctRegions.size == size) {
            "Expected $size distinct regions, but found ${distinctRegions.size}."
        }
        distinctRegions.forEach { regionId ->
            val positions = regionAssignments.filterValues { it == regionId }.keys
            require(CrownsRegionConnectivity.isConnected(size, positions)) {
                "Region $regionId must be orthogonally connected."
            }
        }

        this.regionAssignments = regionAssignments.toMap()
    }

    fun regionAt(position: CrownsPosition): RegionId {
        CrownsBoardConstraints.requireInside(size, position)
        return checkNotNull(regionAssignments[position]) { "Position $position has no region assignment." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CrownsPuzzle &&
            id == other.id &&
            size == other.size &&
            regionAssignments == other.regionAssignments

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + size
        result = 31 * result + regionAssignments.hashCode()
        return result
    }

    override fun toString(): String = "CrownsPuzzle(id=$id, size=$size, regionAssignments=$regionAssignments)"
}
