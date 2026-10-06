package com.stanisryz.logica.puzzle.core.word

/** An immutable, independently editable letter position for the current unsubmitted attempt. */
class WordDraft private constructor(
    positions: Iterable<Char?>,
    /** The language whose normalized letters the positions hold. */
    val language: WordLanguage,
) {
    val positions: List<Char?> = positions.toList()
    val wordLength: Int = this.positions.size

    init {
        require(WordRules.isSupportedLength(wordLength)) { "Unsupported Word draft length $wordLength." }
        require(
            this.positions.filterNotNull().all { letter ->
                language.normalizer.isSupportedLetter(letter) &&
                    language.normalizer.normalizeLetter(letter) == letter
            },
        ) { "Word draft positions must contain normalized ${language.name.lowercase()} letters or be empty." }
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
        require(language.normalizer.isSupportedLetter(letter)) {
            "Letter '$letter' is not a supported ${language.name.lowercase()} letter."
        }
        val normalized = language.normalizer.normalizeLetter(letter)
        return WordDraft(positions.mapIndexed { position, current -> if (position == index) normalized else current }, language)
    }

    internal fun withoutLetter(index: Int): WordDraft {
        require(index in positions.indices) { "Word draft position $index is out of bounds." }
        if (positions[index] == null) return this
        return WordDraft(positions.mapIndexed { position, current -> if (position == index) null else current }, language)
    }

    override fun equals(other: Any?): Boolean = this === other || other is WordDraft && positions == other.positions

    override fun hashCode(): Int = positions.hashCode()

    override fun toString(): String = "WordDraft(positions=$positions)"

    companion object {
        fun empty(
            wordLength: Int,
            language: WordLanguage = WordLanguage.RUSSIAN,
        ): WordDraft = WordDraft(List(wordLength) { null }, language)

        fun fromPrefix(
            prefix: String,
            wordLength: Int,
            language: WordLanguage = WordLanguage.RUSSIAN,
        ): WordDraft {
            require(prefix.length <= wordLength) { "Word draft prefix is longer than the puzzle word." }
            return fromPositions(prefix.toList() + List(wordLength - prefix.length) { null }, language)
        }

        fun fromPositions(
            positions: Iterable<Char?>,
            language: WordLanguage = WordLanguage.RUSSIAN,
        ): WordDraft = WordDraft(positions, language)
    }
}
