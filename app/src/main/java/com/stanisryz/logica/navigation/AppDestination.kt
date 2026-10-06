package com.stanisryz.logica.navigation

import com.stanisryz.logica.R
import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.ui.profile.ProfilePage

/**
 * The three primary sections of the application. Everything the player can reach is either one of
 * these tabs or a secondary destination opened from one of them.
 */
internal enum class PrimaryTab(
    val titleResource: Int,
) {
    /** Daily challenges plus the regular puzzle catalog. */
    GAME(R.string.tab_game),

    /** The RuStore gem store. */
    STORE(R.string.tab_store),

    /** The local gameplay profile: statistics only, no account. */
    PROFILE(R.string.tab_profile),

    ;

    companion object {
        /** The application always opens on the Game hub. */
        val START: PrimaryTab = GAME
    }
}

internal sealed interface AppDestination {
    /**
     * The primary shell. Which of the three tabs it shows is shell state rather than a back-stack
     * entry, so switching tabs keeps each tab's ViewModels and its saved Compose state instead of
     * rebuilding them.
     */
    data object Home : AppDestination

    data object Settings : AppDestination

    /** The third-party notices, opened from Settings. */
    data object Licenses : AppDestination

    /** Every achievement on its own row, opened from the Profile. */
    data object Achievements : AppDestination

    /** One Profile page (the Daily calendar or the games), opened from the Profile. */
    data class ProfileSection(
        val page: ProfilePage,
    ) : AppDestination

    /** The Daily archive's past days, opened from the Game hub. */
    data object DailyArchive : AppDestination

    /** One archive day, opened from the archive list or a past day of the Profile calendar. */
    data class DailyArchiveDay(
        val epochDay: Long,
    ) : AppDestination

    data object BalanceStart : AppDestination

    data object BalanceTutorial : AppDestination

    data object CrownsStart : AppDestination

    data object CrownsTutorial : AppDestination

    data object WordStart : AppDestination

    data object WordTutorial : AppDestination

    data object SudokuStart : AppDestination

    data object SudokuTutorial : AppDestination

    data object Game2048Start : AppDestination

    data object Game2048Tutorial : AppDestination

    data object NonogramStart : AppDestination

    data object NonogramTutorial : AppDestination

    data object BlockSudokuStart : AppDestination

    data object BlockSudokuTutorial : AppDestination

    data class BalanceGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class CrownsGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class WordGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class SudokuGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class Game2048Game(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class NonogramGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination

    data class BlockSudokuGame(
        val launch: GameAttemptLaunch,
    ) : AppDestination
}

/** The bottom navigation belongs to the primary tabs and to nothing else. */
internal fun AppDestination.showsBottomBar(): Boolean = this == AppDestination.Home

/** The Settings gear is available everywhere except Settings itself and the Licences opened from it. */
internal fun AppDestination.showsSettingsAction(): Boolean = this != AppDestination.Settings && this != AppDestination.Licenses

/**
 * Where the wallet belongs: the shared shell of the primary tabs, and every screen a game can be
 * started, resumed, or played from. Settings and the tutorials deliberately stay free of it.
 */
internal fun AppDestination.showsWallet(): Boolean =
    when (this) {
        AppDestination.Home,
        AppDestination.DailyArchive,
        is AppDestination.DailyArchiveDay,
        AppDestination.BalanceStart,
        AppDestination.CrownsStart,
        AppDestination.WordStart,
        AppDestination.SudokuStart,
        AppDestination.Game2048Start,
        AppDestination.NonogramStart,
        AppDestination.BlockSudokuStart,
        is AppDestination.BalanceGame,
        is AppDestination.CrownsGame,
        is AppDestination.WordGame,
        is AppDestination.SudokuGame,
        is AppDestination.Game2048Game,
        is AppDestination.NonogramGame,
        is AppDestination.BlockSudokuGame,
        -> true
        else -> false
    }

/**
 * Where a zero-life rewarded ad may be preloaded: only a surface a game can actually be started or
 * played from. Store and Profile carry the same wallet but never cause an ad load by themselves.
 */
internal fun AppDestination.allowsRewardedOffer(tab: PrimaryTab): Boolean =
    showsWallet() && (this != AppDestination.Home || tab == PrimaryTab.GAME)

/**
 * An actual production gameplay destination, and nothing else: the interstitial preloads only while
 * one of these is on screen. The Game hub, the Store, Profile, Settings, the start screens, and
 * every tutorial deliberately answer `false`.
 */
internal fun AppDestination.isGameplay(): Boolean =
    when (this) {
        is AppDestination.BalanceGame,
        is AppDestination.CrownsGame,
        is AppDestination.WordGame,
        is AppDestination.SudokuGame,
        is AppDestination.Game2048Game,
        is AppDestination.NonogramGame,
        is AppDestination.BlockSudokuGame,
        -> true
        else -> false
    }

/** The game a gameplay destination plays, for its rules sheet; null anywhere else. */
internal fun AppDestination.gameplayPuzzleType(): PuzzleType? =
    when (this) {
        is AppDestination.BalanceGame -> PuzzleType.BALANCE
        is AppDestination.CrownsGame -> PuzzleType.CROWNS
        is AppDestination.WordGame -> PuzzleType.WORD
        is AppDestination.SudokuGame -> PuzzleType.SUDOKU
        is AppDestination.Game2048Game -> PuzzleType.GAME_2048
        is AppDestination.NonogramGame -> PuzzleType.NONOGRAM
        is AppDestination.BlockSudokuGame -> PuzzleType.BLOCK_SUDOKU
        else -> null
    }

/**
 * Unfinished attempts are never saved, so opening the Store from a running game must not leave it:
 * there the Store opens as a sheet over the board, and everywhere else it is the Store tab.
 */
internal fun AppDestination.opensStoreAsSheet(): Boolean = isGameplay()
