package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/** Deterministically selects from V2's length-defined answer pool. */
class WordGeneratorV2(
    private val possibleAnswers: WordPossibleAnswers = WordLexiconV2.possibleAnswers,
) : PuzzleGenerator<WordPuzzle> {
    override val type = PuzzleType.WORD
    override val version = GeneratorVersion(2)

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): WordPuzzle {
        val pool = possibleAnswers.answers(difficulty)
        check(pool.isNotEmpty()) { "The ${difficulty.name} Word V2 answer pool is empty." }
        val answer = pool[PuzzleRandomV1(seed).nextInt(pool.size)]
        require(answer.length == WordRules.wordLengthForV2(difficulty)) {
            "The ${difficulty.name} Word V2 pool contains a wrong-length answer."
        }
        return WordPuzzle(PuzzleId(type, difficulty, seed, version), answer)
    }
}
