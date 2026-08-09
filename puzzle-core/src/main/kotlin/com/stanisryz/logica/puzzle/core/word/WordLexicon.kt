package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.Difficulty

/** Words the player may submit. Always a superset of [WordPossibleAnswers]. */
interface WordAllowedGuesses {
    val size: Int

    operator fun contains(normalizedWord: String): Boolean

    /** Stable ascending order; intended for verification tooling rather than gameplay. */
    fun all(): List<String>
}

/** Curated words that may actually be generated as puzzle answers. */
interface WordPossibleAnswers {
    val size: Int

    /**
     * Stable answer pool for [difficulty]. Its ordering is part of generator compatibility:
     * changing it changes existing `(difficulty, seed, generatorVersion)` answers.
     */
    fun answers(difficulty: Difficulty): List<String>

    fun difficultyOf(normalizedWord: String): Difficulty?

    fun all(): List<String>
}
