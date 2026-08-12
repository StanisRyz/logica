package com.stanisryz.logica.navigation

import com.stanisryz.logica.catalog.GameAttemptLaunch
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The primary navigation contract: three tabs, Game first, Settings reachable from each of them. */
class AppShellNavigationTest {
    private val secondaryDestinations =
        listOf(
            AppDestination.Settings,
            AppDestination.BalanceStart,
            AppDestination.BalanceTutorial,
            AppDestination.CrownsStart,
            AppDestination.CrownsTutorial,
            AppDestination.WordStart,
            AppDestination.WordTutorial,
            AppDestination.SudokuStart,
            AppDestination.SudokuTutorial,
            AppDestination.Game2048Start,
            AppDestination.Game2048Tutorial,
            AppDestination.BalanceGame(levelLaunch(PuzzleType.BALANCE)),
            AppDestination.CrownsGame(levelLaunch(PuzzleType.CROWNS)),
            AppDestination.WordGame(levelLaunch(PuzzleType.WORD)),
            AppDestination.SudokuGame(levelLaunch(PuzzleType.SUDOKU)),
            AppDestination.Game2048Game(levelLaunch(PuzzleType.GAME_2048)),
        )

    private fun levelLaunch(puzzleType: PuzzleType) =
        GameAttemptLaunch.Level(CatalogLevelId(puzzleType, Difficulty.EASY, CatalogLevelNumber(1)))

    @Test
    fun `primary navigation is exactly game, store, and profile, and starts on game`() {
        assertEquals(listOf(PrimaryTab.GAME, PrimaryTab.STORE, PrimaryTab.PROFILE), PrimaryTab.entries)
        assertEquals(PrimaryTab.GAME, PrimaryTab.START)
        val titles = PrimaryTab.entries.map { it.titleResource }
        assertEquals(PrimaryTab.entries.size, titles.toSet().size)

        // The three tabs share one primary destination, so the gear, the bottom bar, and the wallet
        // are on all of them and on none of the secondary destinations.
        assertTrue(AppDestination.Home.showsSettingsAction())
        assertTrue(AppDestination.Home.showsBottomBar())
        assertTrue(AppDestination.Home.showsWallet())
        secondaryDestinations.forEach { destination ->
            assertFalse("$destination", destination.showsSettingsAction())
            assertFalse("$destination", destination.showsBottomBar())
        }
    }

    @Test
    fun `only game and gameplay surfaces may preload a rewarded ad`() {
        assertTrue(AppDestination.Home.allowsRewardedOffer(PrimaryTab.GAME))
        assertFalse(AppDestination.Home.allowsRewardedOffer(PrimaryTab.STORE))
        assertFalse(AppDestination.Home.allowsRewardedOffer(PrimaryTab.PROFILE))
        assertTrue(AppDestination.BalanceStart.allowsRewardedOffer(PrimaryTab.GAME))
        assertTrue(AppDestination.WordGame(levelLaunch(PuzzleType.WORD)).allowsRewardedOffer(PrimaryTab.GAME))
        assertTrue(AppDestination.Game2048Game(levelLaunch(PuzzleType.GAME_2048)).allowsRewardedOffer(PrimaryTab.GAME))
        assertFalse(AppDestination.Settings.allowsRewardedOffer(PrimaryTab.GAME))
        assertFalse(AppDestination.CrownsTutorial.allowsRewardedOffer(PrimaryTab.GAME))
    }
}
