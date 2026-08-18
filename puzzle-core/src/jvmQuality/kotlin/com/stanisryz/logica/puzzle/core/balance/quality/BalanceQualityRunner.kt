package com.stanisryz.logica.puzzle.core.balance.quality

import com.stanisryz.logica.puzzle.core.balance.BalanceDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.balance.BalanceGeneratorV1
import com.stanisryz.logica.puzzle.core.balance.BalancePuzzle
import com.stanisryz.logica.puzzle.core.balance.BalanceSolver
import com.stanisryz.logica.puzzle.core.balance.BalanceState
import com.stanisryz.logica.puzzle.core.balance.BalanceValidator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.util.Locale
import kotlin.math.ceil
import kotlin.system.exitProcess

object BalanceQualityRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.singleOrNull()?.toIntOrNull()
        require(seedCount != null && seedCount > 0) { "Expected one positive seed-count argument." }

        println("BalanceGeneratorV1 quality check: $seedCount sequential seeds per difficulty (starting at 1)")
        val reports = Difficulty.entries.map { difficulty -> runDifficulty(difficulty, seedCount) }
        reports.forEach(::printReport)

        val hardFailureCount = reports.sumOf { it.failures.size }
        if (hardFailureCount > 0) {
            System.err.println("Balance quality check FAILED with $hardFailureCount hard correctness failure(s).")
            exitProcess(1)
        }
        println("Balance quality check PASSED: no hard correctness failures.")
    }

    private fun runDifficulty(
        difficulty: Difficulty,
        seedCount: Int,
    ): DifficultyReport {
        val solver = BalanceSolver()
        val evaluator = BalanceDifficultyEvaluator(solver)
        val generator = BalanceGeneratorV1(solver, evaluator)
        val validator = BalanceValidator()
        val report = DifficultyReport(difficulty, seedCount)
        val reproducibilityIndexes = setOf(0, seedCount / 2, seedCount - 1)

        repeat(seedCount) { seedIndex ->
            val seed = PuzzleSeed(FIRST_SEED + seedIndex)
            when (val generation = generateTimed(generator, seed, difficulty)) {
                is TimedGeneration.Failure -> {
                    report.generationDurationsMs += generation.durationMs
                    report.fail(seed, FailureKind.GENERATION, generation.reason)
                }
                is TimedGeneration.Success -> {
                    report.generationDurationsMs += generation.durationMs
                    report.successfulGenerations++
                    verifyPuzzle(
                        puzzle = generation.puzzle,
                        seed = seed,
                        difficulty = difficulty,
                        verifyReproducibility = seedIndex in reproducibilityIndexes,
                        generator = generator,
                        solver = solver,
                        evaluator = evaluator,
                        validator = validator,
                        report = report,
                    )
                }
            }
        }
        return report
    }

    private fun verifyPuzzle(
        puzzle: BalancePuzzle,
        seed: PuzzleSeed,
        difficulty: Difficulty,
        verifyReproducibility: Boolean,
        generator: BalanceGeneratorV1,
        solver: BalanceSolver,
        evaluator: BalanceDifficultyEvaluator,
        validator: BalanceValidator,
        report: DifficultyReport,
    ) {
        val expectedId =
            PuzzleId(
                type = PuzzleType.BALANCE,
                difficulty = difficulty,
                seed = seed,
                generatorVersion = GeneratorVersion(1),
            )
        if (puzzle.id != expectedId) {
            report.fail(seed, FailureKind.IDENTITY, "expected $expectedId, found ${puzzle.id}")
        }

        val initialValidation = validator.validate(puzzle, BalanceState.fromPuzzle(puzzle))
        if (initialValidation is ValidationResult.Invalid) {
            report.fail(seed, FailureKind.INVALID, initialValidation.reason ?: "initial state is invalid")
        }

        val solveResult = solver.solveWithAnalysis(puzzle)
        val solution = solveResult.solution
        if (solution == null) {
            report.fail(seed, FailureKind.NO_SOLUTION, "solver returned no solution")
        } else {
            val solutionValidation = validator.validate(puzzle, solution.asState())
            if (solutionValidation != ValidationResult.ValidComplete) {
                report.fail(seed, FailureKind.INVALID, "solver result is not a valid completed state")
            }
        }

        val solutionCount = solver.countSolutions(puzzle, limit = 2)
        if (solutionCount != 1) {
            report.fail(seed, FailureKind.NON_UNIQUE, "countSolutions(limit=2) returned $solutionCount")
        }

        val analysis = solveResult.analysis
        if (analysis.branchDecisions != 0 || analysis.maximumSearchDepth != 0) {
            report.fail(
                seed,
                FailureKind.REQUIRES_GUESSING,
                "branches=${analysis.branchDecisions}, maximumDepth=${analysis.maximumSearchDepth}",
            )
        }

        val evaluatedDifficulty = evaluator.evaluate(puzzle)
        if (evaluatedDifficulty != difficulty) {
            report.fail(seed, FailureKind.WRONG_DIFFICULTY, "evaluated as $evaluatedDifficulty")
        }

        if (verifyReproducibility) {
            try {
                val repeated = generator.generate(seed, difficulty)
                if (repeated != puzzle) {
                    report.fail(seed, FailureKind.NON_DETERMINISTIC, "repeated generation was not structurally equal")
                }
            } catch (exception: Exception) {
                report.fail(
                    seed,
                    FailureKind.NON_DETERMINISTIC,
                    "repeated generation failed: ${exception.conciseMessage()}",
                )
            }
        }

        val cellCount = puzzle.size * puzzle.size
        report.fixedClueCounts += puzzle.fixedClues.size
        report.removedClueCounts += cellCount - puzzle.fixedClues.size
        report.logicalStepCounts += analysis.logicalSteps
        report.preventThreeSteps += analysis.techniqueCounts.preventThree
        report.completeQuotaSteps += analysis.techniqueCounts.completeQuota
        report.preserveUniquenessSteps += analysis.techniqueCounts.preserveUniqueness
    }

    private fun generateTimed(
        generator: BalanceGeneratorV1,
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): TimedGeneration {
        val startedAt = System.nanoTime()
        return try {
            val puzzle = generator.generate(seed, difficulty)
            TimedGeneration.Success(puzzle, elapsedMilliseconds(startedAt))
        } catch (exception: Exception) {
            TimedGeneration.Failure(exception.conciseMessage(), elapsedMilliseconds(startedAt))
        }
    }

    private fun printReport(report: DifficultyReport) {
        println()
        println(report.difficulty.name)
        println("  seeds checked: ${report.seedsChecked}")
        println("  successful generations: ${report.successfulGenerations}")
        println("  hard correctness failures:")
        FailureKind.entries.forEach { kind ->
            println("    ${kind.label}: ${report.failureSeedCount(kind)}")
        }
        println("  generation time ms (all attempts): ${report.generationDurationsMs.summary()}")
        println("  fixed clues: ${report.fixedClueCounts.distributionSummary()}")
        println("  removed clues: ${report.removedClueCounts.distributionSummary()}")
        println("  logical steps: ${report.logicalStepCounts.distributionSummary()}")
        println(
            "  techniques total: prevent-three=${report.preventThreeSteps}, " +
                "quota=${report.completeQuotaSteps}, uniqueness=${report.preserveUniquenessSteps}",
        )
        if (report.failures.isEmpty()) {
            println("  failing seeds: none")
        } else {
            println("  exact failing seeds:")
            report.failures.forEach { failure ->
                println("    seed=${failure.seed.value} [${failure.kind.label}]: ${failure.reason}")
            }
        }
    }

    private fun elapsedMilliseconds(startedAt: Long): Double = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private fun Exception.conciseMessage(): String = "${javaClass.simpleName}: ${message.orEmpty().replace('\n', ' ').trim()}".trimEnd()

    private fun List<Double>.summary(): String {
        if (isEmpty()) return "n/a"
        val sorted = sorted()
        return "avg=${average().format()}, p50=${sorted.percentile(0.50).format()}, " +
            "p95=${sorted.percentile(0.95).format()}, max=${max().format()}"
    }

    private fun List<Int>.distributionSummary(): String {
        if (isEmpty()) return "n/a"
        return "avg=${average().format()}, min=${min()}, max=${max()}"
    }

    private fun List<Double>.percentile(fraction: Double): Double {
        val rank = ceil(size * fraction).toInt().coerceIn(1, size)
        return this[rank - 1]
    }

    private fun Double.format(): String = String.format(Locale.ROOT, "%.2f", this)

    private const val FIRST_SEED = 1L
    private const val NANOS_PER_MILLISECOND = 1_000_000.0
}

private sealed interface TimedGeneration {
    val durationMs: Double

    data class Success(
        val puzzle: BalancePuzzle,
        override val durationMs: Double,
    ) : TimedGeneration

    data class Failure(
        val reason: String,
        override val durationMs: Double,
    ) : TimedGeneration
}

private enum class FailureKind(
    val label: String,
) {
    GENERATION("generation failures"),
    IDENTITY("wrong identities"),
    INVALID("invalid puzzles"),
    NO_SOLUTION("missing solutions"),
    NON_UNIQUE("non-unique puzzles"),
    REQUIRES_GUESSING("requiring guessing/branching"),
    WRONG_DIFFICULTY("wrong difficulty classifications"),
    NON_DETERMINISTIC("non-deterministic outputs"),
}

private data class QualityFailure(
    val seed: PuzzleSeed,
    val kind: FailureKind,
    val reason: String,
)

private class DifficultyReport(
    val difficulty: Difficulty,
    val seedsChecked: Int,
) {
    var successfulGenerations: Int = 0
    val generationDurationsMs = mutableListOf<Double>()
    val fixedClueCounts = mutableListOf<Int>()
    val removedClueCounts = mutableListOf<Int>()
    val logicalStepCounts = mutableListOf<Int>()
    var preventThreeSteps: Int = 0
    var completeQuotaSteps: Int = 0
    var preserveUniquenessSteps: Int = 0
    val failures = mutableListOf<QualityFailure>()

    fun fail(
        seed: PuzzleSeed,
        kind: FailureKind,
        reason: String,
    ) {
        failures += QualityFailure(seed, kind, reason)
    }

    fun failureSeedCount(kind: FailureKind): Int =
        failures
            .asSequence()
            .filter { it.kind == kind }
            .map { it.seed }
            .distinct()
            .count()
}
