package com.stanisryz.logica.puzzle.core.crowns

object CrownsRegionConnectivity {
    fun isConnected(
        size: Int,
        positions: Set<CrownsPosition>,
    ): Boolean {
        if (size <= 0 || positions.isEmpty()) return false
        if (positions.any { !CrownsBoardConstraints.isInside(size, it) }) return false

        val start = positions.minWithOrNull(compareBy(CrownsPosition::row, CrownsPosition::column)) ?: return false
        val visited = mutableSetOf(start)
        val frontier = ArrayDeque<CrownsPosition>()
        frontier.add(start)
        while (frontier.isNotEmpty()) {
            val position = frontier.removeFirst()
            orthogonalNeighbors(size, position).forEach { neighbor ->
                if (neighbor in positions && visited.add(neighbor)) {
                    frontier.add(neighbor)
                }
            }
        }
        return visited.size == positions.size
    }

    internal fun orthogonalNeighbors(
        size: Int,
        position: CrownsPosition,
    ): List<CrownsPosition> =
        listOfNotNull(
            position.row.takeIf { it > 0 }?.let { CrownsPosition(it - 1, position.column) },
            position.column.takeIf { it > 0 }?.let { CrownsPosition(position.row, it - 1) },
            position.column.takeIf { it + 1 < size }?.let { CrownsPosition(position.row, it + 1) },
            position.row.takeIf { it + 1 < size }?.let { CrownsPosition(it + 1, position.column) },
        )
}
