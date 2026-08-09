package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/**
 * Version 1 selects one answer from the requested difficulty's frozen pool using project-owned
 * deterministic randomness only. The pool contents and ordering are part of V1 compatibility.
 */
class WordGeneratorV1(
    private val possibleAnswers: WordPossibleAnswers = WordLexiconV1.possibleAnswers,
) : PuzzleGenerator<WordPuzzle> {
    override val type = PuzzleType.WORD
    override val version = GeneratorVersion(1)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): WordPuzzle {
        val pool = possibleAnswers.answers(difficulty)
        check(pool.isNotEmpty()) { "The ${difficulty.name} Word answer pool is empty." }
        val answer = pool[PuzzleRandomV1(seed).nextInt(pool.size)]
        return WordPuzzle(PuzzleId(type, difficulty, seed, version), answer)
    }
}
