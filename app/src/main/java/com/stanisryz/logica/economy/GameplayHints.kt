package com.stanisryz.logica.economy

import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * Gameplay access to the consumable hint stock, shared by every hint-capable game ViewModel. Each
 * request and each purchase gets its own action ID, so a repeated call can never spend or buy twice.
 */
internal class GameplayHints(
    private val repository: EconomyRepository,
    private val actionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    /** Spends one hint for a hint that is about to be shown; false leaves the board unchanged. */
    suspend fun spend(): Boolean =
        try {
            when (repository.consumeHint(actionIdFactory())) {
                is EconomyHintUse.Used, is EconomyHintUse.AlreadyUsed -> true
                is EconomyHintUse.NoHints -> false
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }

    /** Buys [offer] for gems; true only when the hints actually reached the stock. */
    suspend fun buy(offer: HintOffer): Boolean =
        try {
            repository.buyHintsWithGems(actionIdFactory(), offer) is EconomyHintPurchase.Applied
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
}
