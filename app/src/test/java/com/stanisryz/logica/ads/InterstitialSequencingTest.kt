package com.stanisryz.logica.ads

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.result.GameCompletion
import com.stanisryz.logica.result.GameCompletionRepository
import com.stanisryz.logica.result.GameOutcome
import com.stanisryz.logica.result.GameResult
import com.stanisryz.logica.result.toEntity
import com.stanisryz.logica.result.toGameResultOrNull
import com.stanisryz.logica.session.GameSessionScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The ordering the product depends on: terminal gameplay, durable result plus economy transaction,
 * persistence success, and only then an optional ad. The Yandex SDK is not involved — what is
 * checked is which finished attempts create an opportunity at all, and what the coordinator does
 * with one.
 */
class InterstitialSequencingTest {
    @Test
    fun `a durable solved or failed attempt in either scope shows exactly one interstitial`() =
        runBlocking {
            val outcomes =
                listOf(
                    GameOutcome.SOLVED to GameSessionScope.CATALOG,
                    GameOutcome.FAILED to GameSessionScope.CATALOG,
                    GameOutcome.SOLVED to GameSessionScope.DAILY,
                    GameOutcome.FAILED to GameSessionScope.DAILY,
                )

            outcomes.forEachIndexed { index, (outcome, scope) ->
                val world = AdWorld()
                world.ad.ready = true

                val result = world.repository.complete(completion("result-$index", outcome, scope))

                assertEquals("result-$index", result.resultId)
                val opportunity = world.opportunities.pending.value
                assertNotNull("$outcome/$scope should be eligible", opportunity)
                assertTrue(world.attemptShow(requireNotNull(opportunity)))

                assertEquals(1, world.ad.shows)
                // The show, and only the show, started the five-minute interval.
                assertEquals(1, world.store.writes)
                assertFalse(world.cooldown.isEligible())
            }
        }

    @Test
    fun `a failed completion produces no opportunity and no advertising`() =
        runBlocking {
            val world = AdWorld(persistenceFails = true)
            world.ad.ready = true

            val failure = runCatching { world.repository.complete(completion("result-1")) }

            assertTrue(failure.isFailure)
            // Nothing durable, so nothing to advertise against: the persistence-retry UI runs alone.
            assertNull(world.opportunities.pending.value)
            assertEquals(0, world.ad.shows)
            assertEquals(0, world.store.writes)

            // The retry that finally succeeds is the first moment an opportunity may exist.
            world.persistenceFails = false
            world.repository.complete(completion("result-1"))

            val opportunity = requireNotNull(world.opportunities.pending.value)
            assertEquals("result-1", opportunity.resultId)
            assertTrue(world.attemptShow(opportunity))
            assertEquals(1, world.ad.shows)
        }

    @Test
    fun `an ad that is not ready is skipped rather than waited for`() =
        runBlocking {
            val world = AdWorld()
            world.ad.ready = false

            world.repository.complete(completion("result-1"))
            val opportunity = requireNotNull(world.opportunities.pending.value)

            // IDLE, LOADING, or UNAVAILABLE all look the same here: no show, no delay, no cooldown.
            assertFalse(world.attemptShow(opportunity))
            assertEquals(0, world.ad.shows)
            assertEquals(0, world.store.writes)
            assertTrue(world.cooldown.isEligible())

            // And the missed result is not retried against a later load either.
            world.ad.ready = true
            assertFalse(world.attemptShow(opportunity))
            assertEquals(0, world.ad.shows)
        }

    @Test
    fun `one terminal result causes at most one show however often it is reported`() =
        runBlocking {
            val world = AdWorld()
            world.ad.ready = true

            // The idempotent completion transaction runs again — a retried save, a recomposition,
            // a duplicated callback — and reports the very same result.
            world.repository.complete(completion("result-1"))
            val opportunity = requireNotNull(world.opportunities.pending.value)
            world.opportunities.consume(opportunity)
            world.repository.complete(completion("result-1"))
            world.repository.complete(completion("result-1"))

            assertNull("A repeated completion is not a second opportunity", world.opportunities.pending.value)

            // Even handed the same opportunity repeatedly, only the first attempt reaches the SDK.
            assertTrue(world.attemptShow(opportunity))
            world.ad.ready = true
            assertFalse(world.attemptShow(opportunity))
            assertFalse(world.attemptShow(InterstitialOpportunity("result-1")))

            assertEquals(1, world.ad.shows)
            assertEquals(1, world.store.writes)
        }

    @Test
    fun `an opportunity missed by a process kill is never replayed later`() =
        runBlocking {
            val world = AdWorld()
            world.ad.ready = true
            world.repository.complete(completion("result-1"))
            assertNotNull(world.opportunities.pending.value)

            // The process dies before the ad is shown. Opportunities are in-memory only, so the
            // next launch starts empty and never inspects the durable result history.
            val restarted = AdWorld(store = world.store)
            restarted.ad.ready = true

            assertNull(restarted.opportunities.pending.value)
            assertEquals(0, restarted.ad.shows)
        }

    private fun completion(
        resultId: String,
        outcome: GameOutcome = GameOutcome.SOLVED,
        scope: GameSessionScope = GameSessionScope.CATALOG,
    ): GameCompletion =
        GameCompletion(
            resultId = resultId,
            puzzleType = PuzzleType.BALANCE,
            difficulty = Difficulty.MEDIUM,
            puzzleSeed = PuzzleSeed(7),
            generatorVersion = GeneratorVersion(1),
            sessionScope = scope,
            hintsUsed = 0,
            outcome = outcome,
            challengeDate = if (scope == GameSessionScope.DAILY) LocalDate.of(2026, 8, 11) else null,
            dailyPolicyVersion = if (scope == GameSessionScope.DAILY) DailyPolicyVersion(5) else null,
        )
}

/** Everything above the SDK, assembled the way the application assembles it. */
private class AdWorld(
    val store: FakeInterstitialCooldownStore = FakeInterstitialCooldownStore(),
    var persistenceFails: Boolean = false,
) {
    val clock = FakeInterstitialClock()
    val cooldown = InterstitialCooldownPolicy(store, clock)
    val opportunities = InterstitialOpportunities()
    val ad = FakeInterstitialSlot()
    private val coordinator = InterstitialAdCoordinator(cooldown)
    val repository: GameCompletionRepository =
        InterstitialAwareGameCompletionRepository(
            delegate =
                object : GameCompletionRepository {
                    override suspend fun complete(completion: GameCompletion): GameResult {
                        check(!persistenceFails) { "The completion transaction failed." }
                        return requireNotNull(completion.toEntity(COMPLETED_AT).toGameResultOrNull())
                    }
                },
            opportunities = opportunities,
        )

    /** The controller's two effects, without Yandex: is an ad ready, and put it on screen. */
    suspend fun attemptShow(opportunity: InterstitialOpportunity): Boolean {
        val shown = coordinator.attemptShow(opportunity, isReady = { ad.ready }, show = { ad.show() })
        // The real controller records this from Yandex's `onAdShown`; the fake show stands in for it.
        if (shown) cooldown.recordShown()
        return shown
    }

    private companion object {
        const val COMPLETED_AT = 1_700_000_000_000L
    }
}

/** One loadable, showable-once interstitial. */
internal class FakeInterstitialSlot {
    var ready = false
    var shows = 0
        private set

    fun show(): Boolean {
        if (!ready) return false
        // A loaded ad is consumed exactly once: the slot is cleared before it goes on screen.
        ready = false
        shows++
        return true
    }
}
