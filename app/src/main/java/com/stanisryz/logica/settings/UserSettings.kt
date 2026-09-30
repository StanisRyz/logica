package com.stanisryz.logica.settings

import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val balanceTutorialCompleted: Boolean = false,
    val crownsTutorialCompleted: Boolean = false,
    val wordTutorialCompleted: Boolean = false,
    val sudokuTutorialCompleted: Boolean = false,
    val game2048TutorialCompleted: Boolean = false,
    val nonogramTutorialCompleted: Boolean = false,
    val blockSudokuTutorialCompleted: Boolean = false,
    /** The last Catalog game and difficulty the player started, for the hub's Continue card. */
    val lastPlayedPuzzle: PuzzleType? = null,
    val lastPlayedDifficulty: Difficulty? = null,
    /** Achievement ids already announced; null until the first look records the reached ones. */
    val seenAchievements: Set<String>? = null,
)

/** Whether this game's tutorial was already completed, opened, or declined once. */
fun UserSettings.tutorialCompleted(puzzleType: PuzzleType): Boolean =
    when (puzzleType) {
        PuzzleType.BALANCE -> balanceTutorialCompleted
        PuzzleType.CROWNS -> crownsTutorialCompleted
        PuzzleType.WORD -> wordTutorialCompleted
        PuzzleType.SUDOKU -> sudokuTutorialCompleted
        PuzzleType.GAME_2048 -> game2048TutorialCompleted
        PuzzleType.NONOGRAM -> nonogramTutorialCompleted
        PuzzleType.BLOCK_SUDOKU -> blockSudokuTutorialCompleted
        // Games without a shipped tutorial have nothing to offer.
        else -> true
    }
