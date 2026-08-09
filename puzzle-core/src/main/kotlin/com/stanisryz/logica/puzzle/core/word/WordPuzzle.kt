package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.contract.PuzzleDefinition
import com.stanisryz.logica.puzzle.core.model.PuzzleId
import com.stanisryz.logica.puzzle.core.model.PuzzleType

data class WordPuzzle(
    override val id: PuzzleId,
    val answer: String,
) : PuzzleDefinition {
    init {
        require(id.type == PuzzleType.WORD) { "Word puzzle ID must have WORD type." }
        WordRules.requireNormalized(answer)
    }
}
