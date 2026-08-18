package com.stanisryz.logica.puzzle.core.registry

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleType

class PuzzleGeneratorRegistry(
    generators: Iterable<PuzzleGenerator<*>> = emptyList(),
) {
    private val generatorsByKey = mutableMapOf<Key, PuzzleGenerator<*>>()

    init {
        generators.forEach(::register)
    }

    fun register(generator: PuzzleGenerator<*>) {
        val key = Key(generator.type, generator.version)
        require(key !in generatorsByKey) {
            "Generator already registered for ${generator.type} version ${generator.version.value}."
        }
        generatorsByKey[key] = generator
    }

    fun find(
        type: PuzzleType,
        version: GeneratorVersion,
    ): PuzzleGenerator<*>? = generatorsByKey[Key(type, version)]

    private data class Key(
        val type: PuzzleType,
        val version: GeneratorVersion,
    )
}
