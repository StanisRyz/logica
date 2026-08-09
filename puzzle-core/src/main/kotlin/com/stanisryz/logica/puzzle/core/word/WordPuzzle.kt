package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleType

data class WordPuzzle(
    override val id: PuzzleId,
    val answer: String,
) : PuzzleDefinition {
    val wordLength: Int get() = answer.length

    init {
        require(id.type == PuzzleType.WORD) { "Word puzzle ID must have WORD type." }
        require(WordRules.isSupportedLength(wordLength)) { "Unsupported Word length $wordLength." }
        when (id.generatorVersion.value) {
            1 -> require(wordLength == WordRules.V1_WORD_LENGTH) { "Word Generator V1 must use five-letter answers." }
            2 ->
                require(wordLength == WordRules.wordLengthForV2(id.difficulty)) {
                    "Word Generator V2 answer length does not match ${id.difficulty}."
                }
        }
        WordRules.requireNormalized(answer, wordLength)
    }
}
