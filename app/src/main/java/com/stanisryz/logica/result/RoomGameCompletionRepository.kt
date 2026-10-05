package com.stanisryz.logica.result

import com.stanisryz.logica.economy.EconomyEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The durable end of an attempt. Terminal results, their economy effect, their Daily lifecycle, and
 * Catalog level progression all happen inside one Room transaction, so this is the whole write path
 * — there is no active-attempt record to keep in step with it.
 *
 * Completions are serialised so two games finishing at almost the same moment still each see an
 * up-to-date wallet.
 */
internal class RoomGameCompletionRepository(
    private val dao: GameCompletionDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : GameCompletionRepository {
    private val writeLock = Mutex()

    override suspend fun complete(completion: GameCompletion): GameResult =
        writeLock.withLock {
            val result =
                dao
                    .complete(completion.toEntity(currentTimeMillis()))
                    .toGameResultOrNull()
                    ?: error("The stored completion result is invalid.")
            // What the result card shows is what the ledger row for this result actually moved.
            val gems = dao.findEconomyEventGems(EconomyEvent.resultEventId(result.resultId)) ?: 0
            result.copy(gemsEarned = gems.coerceAtLeast(0))
        }
}
