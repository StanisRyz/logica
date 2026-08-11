package com.stanisryz.logica.navigation

import com.stanisryz.logica.R
import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.sudoku.SudokuGameLaunch
import com.stanisryz.logica.word.WordGameLaunch

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

    data object BalanceStart : AppDestination

    data object BalanceTutorial : AppDestination

    data object CrownsStart : AppDestination

    data object CrownsTutorial : AppDestination

    data object WordStart : AppDestination

    data object WordTutorial : AppDestination

    data object SudokuStart : AppDestination

    data object SudokuTutorial : AppDestination

    data class BalanceGame(
        val launch: BalanceGameLaunch,
    ) : AppDestination

    data class CrownsGame(
        val launch: CrownsGameLaunch,
    ) : AppDestination

    data class WordGame(
        val launch: WordGameLaunch,
    ) : AppDestination

    data class SudokuGame(
        val launch: SudokuGameLaunch,
    ) : AppDestination
}

/** The bottom navigation belongs to the primary tabs and to nothing else. */
internal fun AppDestination.showsBottomBar(): Boolean = this == AppDestination.Home

/** The Settings gear sits on all three primary tabs; secondary destinations show Back instead. */
internal fun AppDestination.showsSettingsAction(): Boolean = this == AppDestination.Home

/**
 * Where the wallet belongs: the shared shell of the primary tabs, and every screen a game can be
 * started, resumed, or played from. Settings and the tutorials deliberately stay free of it.
 */
internal fun AppDestination.showsWallet(): Boolean =
    when (this) {
        AppDestination.Home,
        AppDestination.BalanceStart,
        AppDestination.CrownsStart,
        AppDestination.WordStart,
        AppDestination.SudokuStart,
        is AppDestination.BalanceGame,
        is AppDestination.CrownsGame,
        is AppDestination.WordGame,
        is AppDestination.SudokuGame,
        -> true
        else -> false
    }

/**
 * Where a zero-life rewarded ad may be preloaded: only a surface a game can actually be started or
 * played from. Store and Profile carry the same wallet but never cause an ad load by themselves.
 */
internal fun AppDestination.allowsRewardedOffer(tab: PrimaryTab): Boolean =
    showsWallet() && (this != AppDestination.Home || tab == PrimaryTab.GAME)
