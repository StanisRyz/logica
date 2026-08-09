package com.stanisryz.logica.puzzle.core.crowns.quality

import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.crowns.CrownsDifficultyEvaluator
import com.stanisryz.logica.puzzle.core.crowns.CrownsGeneratorV1
import com.stanisryz.logica.puzzle.core.crowns.CrownsPosition
import com.stanisryz.logica.puzzle.core.crowns.CrownsPuzzle
import com.stanisryz.logica.puzzle.core.crowns.CrownsRegionConnectivity
import com.stanisryz.logica.puzzle.core.crowns.CrownsSolveAnalysis
import com.stanisryz.logica.puzzle.core.crowns.CrownsSolver
import com.stanisryz.logica.puzzle.core.crowns.CrownsValidator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import java.util.Locale
import kotlin.math.ceil
import kotlin.system.exitProcess

object CrownsQualityRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val seedCount = args.singleOrNull()?.toIntOrNull()
        require(seedCount != null && seedCount > 0) { "Expected one positive seed-count argument." }

        println("CrownsGeneratorV1 quality check: $seedCount sequential seeds per difficulty (starting at 1)")
        val reports = Difficulty.entries.map { difficulty -> runDifficulty(difficulty, seedCount) }
        reports.forEach(::printReport)

        val failureCount = reports.sumOf { it.failures.size }
        if (failureCount > 0) {
            System.err.println("Crowns quality check FAILED with $failureCount hard correctness failure(s).")
            exitProcess(1)
        }
        println("Crowns quality check PASSED: no hard correctness failures.")
    }

    private fun runDifficulty(
        difficulty: Difficulty,
        seedCount: Int,
    ): DifficultyReport {
        val solver = CrownsSolver()
        val evaluator = CrownsDifficultyEvaluator(solver)
        val generator = CrownsGeneratorV1(solver, evaluator)
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
                        report = report,
                    )
                }
            }
        }
        return report
    }

    private fun verifyPuzzle(
        puzzle: CrownsPuzzle,
        seed: PuzzleSeed,
        difficulty: Difficulty,
        verifyReproducibility: Boolean,
        generator: CrownsGeneratorV1,
        solver: CrownsSolver,
        evaluator: CrownsDifficultyEvaluator,
        report: DifficultyReport,
    ) {
        val expectedId = PuzzleId(PuzzleType.CROWNS, difficulty, seed, GeneratorVersion(1))
        if (puzzle.id != expectedId) {
            report.fail(seed, FailureKind.IDENTITY, "expected $expectedId, found ${puzzle.id}")
        }
        verifyStructure(puzzle)?.let { reason -> report.fail(seed, FailureKind.INVALID, reason) }

        val solveResult = solver.solveWithAnalysis(puzzle)
        val solution = solveResult.solution
        if (solution == null) {
            report.fail(seed, FailureKind.NO_SOLUTION, "solver returned no solution")
        } else if (CrownsValidator().validate(puzzle, solution.asState()) != ValidationResult.ValidComplete) {
            report.fail(seed, FailureKind.INVALID, "solver result is not a valid completed state")
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
                if (generator.generate(seed, difficulty) != puzzle) {
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

        recordMetrics(puzzle, analysis, report)
    }

    private fun verifyStructure(puzzle: CrownsPuzzle): String? {
        val expectedPositions =
            buildSet {
                repeat(puzzle.size) { row ->
                    repeat(puzzle.size) { column -> add(CrownsPosition(row, column)) }
                }
            }
        if (puzzle.regionAssignments.keys != expectedPositions) return "region assignments do not cover the board"

        val regions = puzzle.regionAssignments.values.toSet()
        if (regions.size != puzzle.size) return "expected ${puzzle.size} regions, found ${regions.size}"
        regions.forEach { regionId ->
            val positions = puzzle.regionAssignments.filterValues { it == regionId }.keys
            if (!CrownsRegionConnectivity.isConnected(puzzle.size, positions)) {
                return "region $regionId is disconnected"
            }
        }
        return null
    }

    private fun recordMetrics(
        puzzle: CrownsPuzzle,
        analysis: CrownsSolveAnalysis,
        report: DifficultyReport,
    ) {
        val regionSizes =
            puzzle.regionAssignments.values
                .groupingBy { it }
                .eachCount()
                .values
        report.regionSizes += regionSizes
        report.singleCellRegions += regionSizes.count { it == 1 }
        report.logicalSteps += analysis.logicalSteps
        report.candidateEliminations += analysis.candidateEliminations
        report.singleCandidateRow += analysis.techniqueCounts.singleCandidateRow
        report.singleCandidateColumn += analysis.techniqueCounts.singleCandidateColumn
        report.singleCandidateRegion += analysis.techniqueCounts.singleCandidateRegion
        report.regionLockedToRow += analysis.techniqueCounts.regionLockedToRow
        report.regionLockedToColumn += analysis.techniqueCounts.regionLockedToColumn
    }

    private fun generateTimed(
        generator: CrownsGeneratorV1,
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): TimedGeneration {
        val startedAt = System.nanoTime()
        return try {
            TimedGeneration.Success(generator.generate(seed, difficulty), elapsedMilliseconds(startedAt))
        } catch (exception: Exception) {
            TimedGeneration.Failure(exception.conciseMessage(), elapsedMilliseconds(startedAt))
        }
    }

    private fun printReport(report: DifficultyReport) {
        println()
        println(report.difficulty.name)
        println("  seeds checked: ${report.seedsChecked}")
        println("  successful generations: ${report.successfulGenerations}")
        FailureKind.entries.forEach { kind ->
            println("  ${kind.label}: ${report.failureSeedCount(kind)}")
        }
        println("  generation time ms: ${report.generationDurationsMs.summary()}")
        println("  region sizes: ${report.regionSizes.distributionSummary()}")
        println("  single-cell regions total: ${report.singleCellRegions}")
        println("  logical steps: ${report.logicalSteps.distributionSummary()}")
        println("  candidate eliminations: ${report.candidateEliminations.distributionSummary()}")
        println(
            "  techniques total: row-single=${report.singleCandidateRow}, " +
                "column-single=${report.singleCandidateColumn}, region-single=${report.singleCandidateRegion}, " +
                "region-row-lock=${report.regionLockedToRow}, region-column-lock=${report.regionLockedToColumn}",
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
        val puzzle: CrownsPuzzle,
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
    val regionSizes = mutableListOf<Int>()
    var singleCellRegions: Int = 0
    val logicalSteps = mutableListOf<Int>()
    val candidateEliminations = mutableListOf<Int>()
    var singleCandidateRow: Int = 0
    var singleCandidateColumn: Int = 0
    var singleCandidateRegion: Int = 0
    var regionLockedToRow: Int = 0
    var regionLockedToColumn: Int = 0
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
