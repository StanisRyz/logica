package com.stanisryz.logica.puzzle.core.crowns

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandom
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1
import kotlin.math.abs

class CrownsGeneratorV1(
    private val solver: CrownsSolver = CrownsSolver(),
    private val difficultyEvaluator: CrownsDifficultyEvaluator = CrownsDifficultyEvaluator(solver),
) : PuzzleGenerator<CrownsPuzzle> {
    override val type = PuzzleType.CROWNS
    override val version = GeneratorVersion(1)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): CrownsPuzzle {
        val profile = CrownsGenerationProfiles.forDifficulty(difficulty)
        val id = PuzzleId(type, difficulty, seed, version)
        val random = PuzzleRandomV1(seed)
        val diagnostics = GenerationDiagnostics()

        repeat(profile.maximumSolutionAttempts) solutionAttempt@{
            val intendedCrowns =
                generateCompletedPlacement(
                    size = profile.boardSize,
                    random = random,
                    maximumNodes = profile.maximumSolutionSearchNodes,
                ) ?: return@solutionAttempt
            diagnostics.completedPlacements++

            repeat(profile.maximumRegionAttemptsPerSolution) regionAttempt@{
                val assignments = growRegions(profile, intendedCrowns, random) ?: return@regionAttempt
                diagnostics.regionLayouts++
                val puzzle = CrownsPuzzle(id, profile.boardSize, assignments)
                if (CrownsValidator().validate(puzzle, CrownsState(intendedCrowns)) != ValidationResult.ValidComplete) {
                    return@regionAttempt
                }
                if (solver.countSolutions(puzzle, limit = 2) != 1) return@regionAttempt
                diagnostics.uniqueLayouts++

                val solveResult = solver.solveWithAnalysis(puzzle)
                if (
                    solveResult.solution == null ||
                    solveResult.analysis.branchDecisions != 0 ||
                    solveResult.analysis.maximumSearchDepth != 0
                ) {
                    return@regionAttempt
                }
                diagnostics.logicOnlyLayouts++
                if (difficultyEvaluator.evaluate(puzzle, solveResult) != difficulty) return@regionAttempt
                return puzzle
            }
        }

        error(
            "Unable to generate a ${difficulty.name} Crowns puzzle for seed ${seed.value} within " +
                "${profile.maximumSolutionAttempts} solution attempts and " +
                "${profile.maximumRegionAttemptsPerSolution} region attempts per solution " +
                "(completed placements: ${diagnostics.completedPlacements}, " +
                "region layouts: ${diagnostics.regionLayouts}, unique: ${diagnostics.uniqueLayouts}, " +
                "logic-only: ${diagnostics.logicOnlyLayouts}).",
        )
    }

    private fun generateCompletedPlacement(
        size: Int,
        random: PuzzleRandom,
        maximumNodes: Int,
    ): List<CrownsPosition>? =
        placeCrownInRow(
            size = size,
            row = 0,
            columns = mutableSetOf(),
            crowns = mutableListOf(),
            random = random,
            budget = SearchBudget(maximumNodes),
        )

    private fun placeCrownInRow(
        size: Int,
        row: Int,
        columns: MutableSet<Int>,
        crowns: MutableList<CrownsPosition>,
        random: PuzzleRandom,
        budget: SearchBudget,
    ): List<CrownsPosition>? {
        if (!budget.claimNode()) return null
        if (row == size) return crowns.toList()

        val previousColumn = crowns.lastOrNull()?.column
        val candidates =
            (0 until size)
                .filter { column -> column !in columns && previousColumn?.let { abs(it - column) != 1 } != false }
                .toMutableList()
        random.shuffle(candidates)
        candidates.forEach { column ->
            columns += column
            crowns += CrownsPosition(row, column)
            placeCrownInRow(size, row + 1, columns, crowns, random, budget)?.let { return it }
            crowns.removeLast()
            columns -= column
        }
        return null
    }

    private fun growRegions(
        profile: CrownsGenerationProfile,
        intendedCrowns: List<CrownsPosition>,
        random: PuzzleRandom,
    ): Map<CrownsPosition, RegionId>? {
        val assignments =
            intendedCrowns
                .mapIndexed { region, position -> position to RegionId(region) }
                .toMap()
                .toMutableMap()
        val regions = intendedCrowns.indices.toMutableList()
        random.shuffle(regions)
        val frozenRegions = regions.take(profile.frozenRegionCount).map(::RegionId).toSet()
        val remainingCellCount = profile.boardSize * profile.boardSize - intendedCrowns.size

        repeat(remainingCellCount) {
            val frontier =
                orderedBoardPositions(profile.boardSize).mapNotNull { position ->
                    if (position in assignments) return@mapNotNull null
                    val adjacentRegions =
                        CrownsRegionConnectivity
                            .orthogonalNeighbors(profile.boardSize, position)
                            .mapNotNull(assignments::get)
                            .filterNot { it in frozenRegions }
                            .distinct()
                    position.takeIf { adjacentRegions.isNotEmpty() }?.let { it to adjacentRegions }
                }
            if (frontier.isEmpty()) return null

            val (position, adjacentRegions) = frontier[random.nextInt(frontier.size)]
            assignments[position] = adjacentRegions[random.nextInt(adjacentRegions.size)]
        }
        return assignments.toMap()
    }

    private fun orderedBoardPositions(size: Int): List<CrownsPosition> =
        buildList(size * size) {
            repeat(size) { row ->
                repeat(size) { column ->
                    add(CrownsPosition(row, column))
                }
            }
        }

    private data class GenerationDiagnostics(
        var completedPlacements: Int = 0,
        var regionLayouts: Int = 0,
        var uniqueLayouts: Int = 0,
        var logicOnlyLayouts: Int = 0,
    )

    private class SearchBudget(
        private val maximumNodes: Int,
    ) {
        private var claimedNodes = 0

        fun claimNode(): Boolean {
            if (claimedNodes >= maximumNodes) return false
            claimedNodes++
            return true
        }
    }
}
