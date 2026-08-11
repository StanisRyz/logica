package com.stanisryz.logica.navigation

import com.stanisryz.logica.balance.BalanceGameLaunch
import com.stanisryz.logica.crowns.CrownsGameLaunch
import com.stanisryz.logica.game2048.Game2048Launch
import com.stanisryz.logica.sudoku.SudokuGameLaunch
import com.stanisryz.logica.word.WordGameLaunch
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
            AppDestination.BalanceGame(BalanceGameLaunch.Restore()),
            AppDestination.CrownsGame(CrownsGameLaunch.Restore()),
            AppDestination.WordGame(WordGameLaunch.Restore()),
            AppDestination.SudokuGame(SudokuGameLaunch.Restore()),
            AppDestination.Game2048Game(Game2048Launch.Restore()),
        )

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
        assertTrue(AppDestination.WordGame(WordGameLaunch.Restore()).allowsRewardedOffer(PrimaryTab.GAME))
        assertTrue(AppDestination.Game2048Game(Game2048Launch.Restore()).allowsRewardedOffer(PrimaryTab.GAME))
        assertFalse(AppDestination.Settings.allowsRewardedOffer(PrimaryTab.GAME))
        assertFalse(AppDestination.CrownsTutorial.allowsRewardedOffer(PrimaryTab.GAME))
    }
}
