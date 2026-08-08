package com.stanisryz.logica.puzzle.core.registry

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PuzzleGeneratorRegistryTest {
    @Test
    fun lookupRequiresExactVersionAndDuplicatesAreRejected() {
        val versionOne = TestGenerator(GeneratorVersion(1))
        val versionTwo = TestGenerator(GeneratorVersion(2))
        val registry = PuzzleGeneratorRegistry(listOf(versionOne, versionTwo))

        assertSame(versionOne, registry.find(PuzzleType.BALANCE, GeneratorVersion(1)))
        assertSame(versionTwo, registry.find(PuzzleType.BALANCE, GeneratorVersion(2)))
        assertNull(registry.find(PuzzleType.BALANCE, GeneratorVersion(3)))
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(TestGenerator(GeneratorVersion(1)))
        }
    }

    private data class TestDefinition(
        override val id: PuzzleId,
    ) : PuzzleDefinition

    private class TestGenerator(
        override val version: GeneratorVersion,
    ) : PuzzleGenerator<TestDefinition> {
        override val type = PuzzleType.BALANCE

        override fun generate(
            seed: PuzzleSeed,
            difficulty: Difficulty,
        ) = TestDefinition(
            PuzzleId(type, difficulty, seed, version),
        )
    }
}
