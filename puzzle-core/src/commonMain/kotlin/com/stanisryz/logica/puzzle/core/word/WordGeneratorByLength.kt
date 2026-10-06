package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleGenerator
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.puzzle.core.random.PuzzleRandomV1

/**
 * V2's selection — one draw of the project random stream over a length-defined answer pool — for the
 * language versions: V3 (English) and V4 (Turkish), each over its own lexicon.
 */
class WordGeneratorByLength(
    override val version: GeneratorVersion,
    private val possibleAnswers: WordPossibleAnswers,
) : PuzzleGenerator<WordPuzzle> {
    override val type = PuzzleType.WORD

    init {
        require(version.value == 3 || version.value == 4) { "Word generator version ${version.value} is not a language version." }
    }

    override fun generate(
        seed: PuzzleSeed,
        difficulty: Difficulty,
    ): WordPuzzle {
        val pool = possibleAnswers.answers(difficulty)
        check(pool.isNotEmpty()) { "The ${difficulty.name} Word V${version.value} answer pool is empty." }
        val answer = pool[PuzzleRandomV1(seed).nextInt(pool.size)]
        require(answer.length == WordRules.wordLengthForV2(difficulty)) {
            "The ${difficulty.name} Word V${version.value} pool contains a wrong-length answer."
        }
        return WordPuzzle(PuzzleId(type, difficulty, seed, version), answer)
    }

    companion object {
        fun v3(possibleAnswers: WordPossibleAnswers = WordLexiconV3.possibleAnswers) =
            WordGeneratorByLength(GeneratorVersion(3), possibleAnswers)

        fun v4(possibleAnswers: WordPossibleAnswers = WordLexiconV4.possibleAnswers) =
            WordGeneratorByLength(GeneratorVersion(4), possibleAnswers)
    }
}
