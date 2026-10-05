package com.stanisryz.logica.economy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Gameplay access to the consumable hint stock, shared by every hint-capable game ViewModel. Each
 * request and each purchase gets its own action ID, so a repeated call can never spend or buy twice.
 */
internal class GameplayHints(
    private val repository: EconomyRepository,
    private val actionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    /** True while a computed hint is being charged and shown; board input waits until it is over. */
    var isCharging: Boolean = false
        private set

    /**
     * Charges one hint for an already computed hint and shows it, as one step. Nothing is charged when
     * [stillCurrent] says the board changed while the hint was computed. Once charging starts nothing can
     * cancel it half-way — neither a board change nor leaving the screen — and the ViewModel ignores
     * board input while [isCharging], so a charged hint is always shown and a shown hint always charged.
     */
    suspend fun chargeAndShow(
        stillCurrent: () -> Boolean,
        show: (paid: Boolean) -> Unit,
    ) {
        if (!stillCurrent()) return
        isCharging = true
        try {
            withContext(NonCancellable) { show(spend()) }
        } finally {
            isCharging = false
        }
    }

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
