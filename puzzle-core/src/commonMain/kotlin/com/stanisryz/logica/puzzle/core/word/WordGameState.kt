package com.stanisryz.logica.puzzle.core.word

import com.stanisryz.logica.puzzle.core.model.PuzzleId

/** Semantic letter outcome. Colors and other visual terminology belong to the presentation layer. */
enum class WordLetterFeedback(
    val strength: Int,
) {
    ABSENT(0),
    PRESENT(1),
    CORRECT(2),
}

data class WordLetterResult(
    val letter: Char,
    val feedback: WordLetterFeedback,
)

enum class WordGameStatus {
    IN_PROGRESS,
    SOLVED,
    FAILED,
}

/** A submitted, final guess. Submitted attempts are never undone. */
class WordAttempt internal constructor(
    val word: String,
    letters: Iterable<WordLetterResult>,
    language: WordLanguage = WordLanguage.RUSSIAN,
) {
    val letters: List<WordLetterResult> = letters.toList()

    init {
        require(WordRules.isSupportedLength(word.length)) { "Unsupported attempt word length." }
        WordRules.requireNormalized(word, word.length, language)
        require(this.letters.size == word.length) { "Feedback must cover every letter of the attempt." }
        require(this.letters.mapIndexed { index, result -> result.letter == word[index] }.all { it }) {
            "Feedback letters must match the attempted word."
        }
    }

    val isCorrect: Boolean = this.letters.all { it.feedback == WordLetterFeedback.CORRECT }

    override fun equals(other: Any?): Boolean = this === other || other is WordAttempt && word == other.word && letters == other.letters

    override fun hashCode(): Int = 31 * word.hashCode() + letters.hashCode()

    override fun toString(): String = "WordAttempt(word=$word, letters=$letters)"
}

/**
 * Strongest known feedback per normalized letter across every submitted attempt. A confirmed
 * [WordLetterFeedback.CORRECT] or [WordLetterFeedback.PRESENT] is never downgraded by later evidence.
 */
class WordLetterKnowledge internal constructor(
    byLetter: Map<Char, WordLetterFeedback>,
    private val language: WordLanguage = WordLanguage.RUSSIAN,
) {
    val byLetter: Map<Char, WordLetterFeedback> = byLetter.toMap()

    operator fun get(letter: Char): WordLetterFeedback? = this.byLetter[language.normalizer.normalizeLetter(letter)]

    override fun equals(other: Any?): Boolean = this === other || other is WordLetterKnowledge && byLetter == other.byLetter

    override fun hashCode(): Int = byLetter.hashCode()

    override fun toString(): String = "WordLetterKnowledge(byLetter=$byLetter)"

    companion object {
        fun from(
            attempts: Iterable<WordAttempt>,
            language: WordLanguage = WordLanguage.RUSSIAN,
        ): WordLetterKnowledge {
            val strongest = mutableMapOf<Char, WordLetterFeedback>()
            attempts.forEach { attempt ->
                attempt.letters.forEach { (letter, feedback) ->
                    val known = strongest[letter]
                    if (known == null || feedback.strength > known.strength) {
                        strongest[letter] = feedback
                    }
                }
            }
            return WordLetterKnowledge(strongest, language)
        }
    }
}

class WordGameState internal constructor(
    val puzzleId: PuzzleId,
    val wordLength: Int,
    val currentDraft: WordDraft,
    attempts: Iterable<WordAttempt>,
    val status: WordGameStatus,
    revealedLetters: Map<Int, Char> = emptyMap(),
) {
    val attempts: List<WordAttempt> = attempts.toList()

    /**
     * Answer letters opened by hints, by position. Each stays in its place in the draft until the
     * attempt ends: it cannot be cleared or replaced, and every new row starts with it.
     */
    val revealedLetters: Map<Int, Char> = revealedLetters.toMap()

    /** Hints shown in this attempt. */
    val hintsUsed: Int get() = revealedLetters.size

    /** The language this game plays in, from its generator version. */
    val language: WordLanguage get() = WordRuntimeResolver.language(puzzleId.generatorVersion)
    val letterKnowledge: WordLetterKnowledge = WordLetterKnowledge.from(this.attempts, language)

    init {
        require(WordRules.isSupportedLength(wordLength)) { "Unsupported Word length $wordLength." }
        require(currentDraft.wordLength == wordLength) { "Current draft does not match the puzzle word length." }
        require(this.attempts.size <= WordRules.MAXIMUM_ATTEMPTS) { "Too many submitted attempts." }
        require(this.attempts.all { it.word.length == wordLength }) {
            "Every submitted attempt must match the puzzle word length."
        }
        require(this.revealedLetters.all { (position, letter) -> currentDraft.positions.getOrNull(position) == letter }) {
            "A revealed letter must stand in its place in the draft."
        }
    }

    val remainingAttempts: Int = WordRules.MAXIMUM_ATTEMPTS - this.attempts.size
    val isFinished: Boolean = status != WordGameStatus.IN_PROGRESS

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is WordGameState &&
            puzzleId == other.puzzleId &&
            wordLength == other.wordLength &&
            currentDraft == other.currentDraft &&
            attempts == other.attempts &&
            status == other.status &&
            revealedLetters == other.revealedLetters

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + wordLength
        result = 31 * result + currentDraft.hashCode()
        result = 31 * result + attempts.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + revealedLetters.hashCode()
        return result
    }

    override fun toString(): String =
        "WordGameState(puzzleId=$puzzleId, wordLength=$wordLength, currentDraft=$currentDraft, attempts=$attempts, " +
            "status=$status, revealedLetters=$revealedLetters)"
}

/**
 * Whether leaving this unfinished attempt throws away something the player did — a submitted guess
 * or a typed letter — so leaving costs a life. Both hosts ask only this.
 */
val WordGameState.hasMeaningfulProgress: Boolean
    get() = !isFinished && (attempts.isNotEmpty() || currentDraft.positions.any { it != null })

/**
 * The position a hint would open: the leftmost one no submitted attempt has guessed exactly and no
 * hint has opened yet; null when the game is over, the attempt's hints are used up, or none is left.
 */
val WordGameState.nextHintPosition: Int?
    get() {
        if (isFinished || hintsUsed >= WordRules.maximumHints(wordLength)) return null
        return (0 until wordLength).firstOrNull { position ->
            position !in revealedLetters && attempts.none { it.letters[position].feedback == WordLetterFeedback.CORRECT }
        }
    }
