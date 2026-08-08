package com.stanisryz.logica.puzzle.core.testing

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.contract.PuzzleSolution
import com.stanisryz.logica.puzzle.core.contract.PuzzleSolver
import com.stanisryz.logica.puzzle.core.contract.PuzzleValidator
import com.stanisryz.logica.puzzle.core.contract.ValidationResult
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

object PuzzleGeneratorContract {
    fun <D : PuzzleDefinition, S : PuzzleSolution> verify(
        generator: PuzzleGenerator<D>,
        solver: PuzzleSolver<D, S>,
        validator: PuzzleValidator<D, S>,
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ) {
        val first = generator.generate(seed, difficulty)
        val second = generator.generate(seed, difficulty)

        assertEquals("Generator must be deterministic.", first, second)
        assertEquals(generator.type, first.id.type)
        assertEquals(generator.version, first.id.generatorVersion)
        assertEquals(seed, first.id.seed)
        assertEquals(difficulty, first.id.difficulty)

        val solution = solver.solve(first)
        assertNotNull("Generated puzzle must have a solution.", solution)
        assertEquals(ValidationResult.Valid, validator.validate(first, requireNotNull(solution)))
        assertEquals("Generated puzzle must have exactly one solution.", 1, solver.countSolutions(first))
    }
}
