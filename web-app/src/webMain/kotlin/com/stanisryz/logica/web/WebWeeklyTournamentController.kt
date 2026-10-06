package com.stanisryz.logica.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Last week's prize waiting for the Player bound by [token]. */
internal data class WebWeeklyPrize(
    val week: Int,
    val place: Int,
    val gems: Int,
    val token: WebPlayerContextToken,
)

/**
 * The parts of the weekly tournament that read Yandex: last week's final place (for its prize) and
 * this week's own place. Both use `getPlayerEntry`, at most once a minute per kind; the table itself
 * is read through [WebLeaderboardController] only while the tournament page shows it.
 *
 * A prize is offered only for the week just ended, a score of that week with stars, a place within
 * the prize places, and a week not claimed yet. Claiming checks that the Player who saw the offer is
 * still the bound one, then makes the claim durable in the rewards record before paying.
 */
internal class WebWeeklyTournamentController(
    private val bridge: WebLeaderboardBridge,
    private val scope: CoroutineScope,
    private val now: () -> Long = webClock::now,
) {
    private val mutablePrize = MutableStateFlow<WebWeeklyPrize?>(null)
    val prize: StateFlow<WebWeeklyPrize?> = mutablePrize.asStateFlow()

    private val mutableOwnPlace = MutableStateFlow<Int?>(null)

    /** This week's own place in the table, once read; null before that or without an entry. */
    val ownPlace: StateFlow<Int?> = mutableOwnPlace.asStateFlow()

    private var prizeCheckedAt: Pair<WebPlayerContextToken, Long>? = null
    private var placeReadAt: Pair<WebPlayerContextToken, Long>? = null

    /**
     * Reads last week's final place for the Player bound by [token] and offers its prize. Runs when
     * a Player binds and when the tournament opens, at most once a minute for the same Player.
     */
    fun checkPrize(
        token: WebPlayerContextToken,
        claimedWeeks: Set<Int>,
    ) {
        if (!bridge.isLeaderboardsSupported()) return
        val time = now()
        val last = prizeCheckedAt
        if (last != null && last.first == token && time - last.second < MIN_READ_SPACING_MS) return
        prizeCheckedAt = token to time
        if (mutablePrize.value?.token != token) mutablePrize.value = null
        val lastWeek = WebWeeklyTournament.week(time) - 1
        if (lastWeek in claimedWeeks) return
        scope.launch {
            val entry = bridge.leaderboardPlayerEntry(WebWeeklyTournament.board(lastWeek)) ?: return@launch
            val gems = WebWeeklyTournament.prize(lastWeek, entry.rank, entry.score)
            // A Player switch while the place was read offers nothing to the new Player.
            if (gems > 0 && prizeCheckedAt?.first == token) mutablePrize.value = WebWeeklyPrize(lastWeek, entry.rank, gems, token)
        }
    }

    /** Reads this week's own place for the Player bound by [token], at most once a minute. */
    fun refreshOwnPlace(token: WebPlayerContextToken) {
        if (!bridge.isLeaderboardsSupported()) return
        val time = now()
        val last = placeReadAt
        if (last != null && last.first == token && time - last.second < MIN_READ_SPACING_MS) return
        if (last?.first != token) mutableOwnPlace.value = null
        placeReadAt = token to time
        val week = WebWeeklyTournament.week(time)
        scope.launch {
            val entry = bridge.leaderboardPlayerEntry(WebWeeklyTournament.board(week))
            if (placeReadAt?.first != token) return@launch
            mutableOwnPlace.value = entry?.takeIf { WebWeeklyTournament.starsIn(it.score, week) != null }?.rank
        }
    }

    /**
     * Pays the offered prize to the Player bound by [currentToken]: nothing when another Player is
     * bound now, and nothing when the week was already claimed here or on another device. The claim
     * is made durable first ([claimDurably]); only then are the gems granted ([grantGems]).
     */
    fun claim(
        currentToken: WebPlayerContextToken?,
        claimDurably: (week: Int) -> Boolean,
        grantGems: (Int) -> Unit,
    ): Boolean {
        val offer = mutablePrize.value ?: return false
        mutablePrize.value = null
        if (offer.token != currentToken) return false
        if (!claimDurably(offer.week)) return false
        grantGems(offer.gems)
        return true
    }

    /** Hides the offer for now; it comes back with the next check while the week is unclaimed. */
    fun dismiss() {
        mutablePrize.value = null
    }

    private companion object {
        /** `getPlayerEntry` allows 60 calls in 5 minutes; one a minute per kind stays far below. */
        const val MIN_READ_SPACING_MS = 60_000L
    }
}
