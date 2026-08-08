package com.stanisryz.logica.puzzle.core.balance

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandom
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

class BalanceGeneratorV1(
    private val solver: BalanceSolver = BalanceSolver(),
    private val difficultyEvaluator: BalanceDifficultyEvaluator = BalanceDifficultyEvaluator(solver),
) : PuzzleGenerator<BalancePuzzle> {
    override val type = PuzzleType.BALANCE
    override val version = GeneratorVersion(1)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): BalancePuzzle {
        val profile = BalanceGenerationProfiles.forDifficulty(difficulty)
        val id = PuzzleId(type, difficulty, seed, version)
        val random = PuzzleRandomV1(seed)
        var completedBoards = 0
        var bestRemovedClueCount = 0

        repeat(profile.maximumAttempts) {
            val emptyPuzzle = BalancePuzzle(id, profile.boardSize, emptyList())
            val completedState =
                generateCompletedState(emptyPuzzle, random, profile.maximumFullBoardSearchNodes)
                    ?: return@repeat
            completedBoards++
            val removal = removeClues(id, completedState, profile, random)
            bestRemovedClueCount = maxOf(bestRemovedClueCount, removal.removedClueCount)
            removal.puzzle?.let { return it }
        }

        error(
            "Unable to generate a ${difficulty.name} Balance puzzle for seed ${seed.value} " +
                "within ${profile.maximumAttempts} attempts " +
                "(completed boards: $completedBoards, best clue removals: $bestRemovedClueCount).",
        )
    }

    private fun generateCompletedState(
        puzzle: BalancePuzzle,
        random: PuzzleRandom,
        maximumNodes: Int,
    ): BalanceState? =
        fillBoard(
            puzzle = puzzle,
            state = BalanceState.empty(puzzle.size),
            random = random,
            budget = SearchBudget(maximumNodes),
        )

    private fun fillBoard(
        puzzle: BalancePuzzle,
        state: BalanceState,
        random: PuzzleRandom,
        budget: SearchBudget,
    ): BalanceState? {
        if (!budget.claimNode()) return null
        when (BalanceRules.validate(puzzle, state)) {
            is ValidationResult.Invalid -> return null
            ValidationResult.ValidComplete -> return state
            ValidationResult.ValidPartial -> Unit
        }

        val choice = selectCell(puzzle, state) ?: return null
        val candidates = choice.candidates.toMutableList()
        random.shuffle(candidates)
        for (candidate in candidates) {
            fillBoard(
                puzzle = puzzle,
                state = state.withCell(choice.position, candidate),
                random = random,
                budget = budget,
            )?.let { return it }
        }
        return null
    }

    private fun selectCell(
        puzzle: BalancePuzzle,
        state: BalanceState,
    ): CellChoice? {
        var bestChoice: CellChoice? = null
        for (row in 0 until state.size) {
            for (column in 0 until state.size) {
                val position = BalancePosition(row, column)
                if (state.cellAt(position) != BalanceCell.EMPTY) continue
                val choice = CellChoice(position, BalanceRules.validCandidates(puzzle, state, position))
                if (choice.candidates.isEmpty()) return choice
                if (bestChoice == null || choice.candidates.size < bestChoice.candidates.size) {
                    bestChoice = choice
                }
                if (choice.candidates.size == 1) return choice
            }
        }
        return bestChoice
    }

    private fun removeClues(
        id: PuzzleId,
        completedState: BalanceState,
        profile: BalanceGenerationProfile,
        random: PuzzleRandom,
    ): RemovalAttempt {
        val clues = completedState.toClueMap()
        val removalOrder = clues.keys.toMutableList()
        random.shuffle(removalOrder)
        var removedClues = 0

        for (position in removalOrder) {
            if (removedClues >= profile.maximumRemovedClues) break
            val removedValue = requireNotNull(clues.remove(position))
            val candidate = clues.toPuzzle(id, completedState.size)

            if (solver.countSolutions(candidate, limit = 2) != 1) {
                clues[position] = removedValue
                continue
            }

            val solveResult = solver.solveWithAnalysis(candidate)
            if (
                solveResult.solution == null ||
                solveResult.analysis.branchDecisions != 0 ||
                solveResult.analysis.maximumSearchDepth != 0
            ) {
                clues[position] = removedValue
                continue
            }

            val evaluatedDifficulty = difficultyEvaluator.evaluate(candidate, solveResult)
            if (evaluatedDifficulty.ordinal > id.difficulty.ordinal) {
                clues[position] = removedValue
                continue
            }

            removedClues++
            if (removedClues >= profile.minimumRemovedClues && evaluatedDifficulty == id.difficulty) {
                return RemovalAttempt(
                    puzzle = candidate,
                    removedClueCount = removedClues,
                )
            }
        }
        return RemovalAttempt(
            puzzle = null,
            removedClueCount = removedClues,
        )
    }

    private fun BalanceState.toClueMap(): MutableMap<BalancePosition, BalanceCell> {
        val state = this
        return buildMap {
            for (row in 0 until state.size) {
                for (column in 0 until state.size) {
                    val position = BalancePosition(row, column)
                    put(position, state.cellAt(position))
                }
            }
        }.toMutableMap()
    }

    private fun Map<BalancePosition, BalanceCell>.toPuzzle(
        id: PuzzleId,
        size: Int,
    ): BalancePuzzle =
        BalancePuzzle(
            id = id,
            size = size,
            fixedClues = entries.map { (position, value) -> BalanceClue(position, value) },
        )

    private data class CellChoice(
        val position: BalancePosition,
        val candidates: List<BalanceCell>,
    )

    private data class RemovalAttempt(
        val puzzle: BalancePuzzle?,
        val removedClueCount: Int,
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
