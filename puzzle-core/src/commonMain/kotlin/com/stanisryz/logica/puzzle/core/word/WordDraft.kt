package com.stanisryz.logica.puzzle.core.word

/** An immutable, independently editable letter position for the current unsubmitted attempt. */
class WordDraft private constructor(
    positions: Iterable<Char?>,
) {
    val positions: List<Char?> = positions.toList()
    val wordLength: Int = this.positions.size

    init {
        require(WordRules.isSupportedLength(wordLength)) { "Unsupported Word draft length $wordLength." }
        require(
            this.positions.filterNotNull().all { letter ->
                RussianWordNormalizer.isSupportedLetter(letter) &&
                    RussianWordNormalizer.normalizeLetter(letter) == letter
            },
        ) { "Word draft positions must contain normalized Russian letters or be empty." }
    }

    operator fun get(index: Int): Char? = positions[index]

    val isComplete: Boolean = positions.all { it != null }

    fun firstEmptyIndex(): Int? = positions.indexOfFirst { it == null }.takeIf { it >= 0 }

    fun completedWordOrNull(): String? = if (isComplete) positions.joinToString(separator = "") else null

    internal fun withLetter(
        index: Int,
        letter: Char,
    ): WordDraft {
        require(index in positions.indices) { "Word draft position $index is out of bounds." }
        require(RussianWordNormalizer.isSupportedLetter(letter)) {
            "Letter '$letter' is not a supported Russian letter."
        }
        val normalized = RussianWordNormalizer.normalizeLetter(letter)
        return WordDraft(positions.mapIndexed { position, current -> if (position == index) normalized else current })
    }

    internal fun withoutLetter(index: Int): WordDraft {
        require(index in positions.indices) { "Word draft position $index is out of bounds." }
        if (positions[index] == null) return this
        return WordDraft(positions.mapIndexed { position, current -> if (position == index) null else current })
    }

    override fun equals(other: Any?): Boolean = this === other || other is WordDraft && positions == other.positions

    override fun hashCode(): Int = positions.hashCode()

    override fun toString(): String = "WordDraft(positions=$positions)"

    companion object {
        fun empty(wordLength: Int): WordDraft = WordDraft(List(wordLength) { null })

        fun fromPrefix(
            prefix: String,
            wordLength: Int,
        ): WordDraft {
            require(prefix.length <= wordLength) { "Word draft prefix is longer than the puzzle word." }
            return fromPositions(prefix.toList() + List(wordLength - prefix.length) { null })
        }

        fun fromPositions(positions: Iterable<Char?>): WordDraft = WordDraft(positions)
    }
}
