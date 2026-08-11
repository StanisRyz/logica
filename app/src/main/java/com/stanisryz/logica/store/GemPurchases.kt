package com.stanisryz.logica.store

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.GemPack

/**
 * Turns a confirmed RuStore purchase into gems, in the one order that is safe to crash in.
 *
 * The ledger row goes first and the RuStore finalization second. If the process dies between them,
 * RuStore still considers the purchase undelivered and hands it back from
 * [RuStorePayGateway.unfinalizedPurchases]; the ledger insert is then a no-op, no second pack of
 * gems is credited, and only the finalization is retried. The reverse order could lose a paid
 * purchase entirely, which is the one outcome that costs the player real money.
 */
internal class GemPurchaseProcessor(
    private val gateway: RuStorePayGateway,
    private val economy: EconomyRepository,
) {
    /**
     * Runs one payment end to end. Gems are credited for exactly one purchase state — the confirmed
     * one — so a cancelled, still-settling, or failed payment moves the wallet by nothing at all.
     */
    suspend fun buy(pack: GemPack): GemPurchaseOutcome =
        when (val result = gateway.purchase(pack.productId)) {
            is StorePurchaseResult.Confirmed ->
                when (val credited = credit(result.purchase)) {
                    is EconomyGemPurchase.Granted -> GemPurchaseOutcome.Granted(credited.pack)
                    // Already credited means the gems are in the wallet; from the player's side the
                    // pack simply arrived, so they are told the same thing either way.
                    is EconomyGemPurchase.AlreadyGranted -> GemPurchaseOutcome.Granted(pack)
                    is EconomyGemPurchase.UnsupportedProduct -> GemPurchaseOutcome.Failed
                }
            // A payment that is still settling is finished by reconciliation, not by waiting here.
            StorePurchaseResult.Pending -> GemPurchaseOutcome.Processing
            StorePurchaseResult.Cancelled -> GemPurchaseOutcome.Cancelled
            is StorePurchaseResult.Failed -> GemPurchaseOutcome.Failed
        }

    /**
     * Credits one confirmed purchase and then closes it with RuStore. A finalization that fails is
     * deliberately swallowed: the gems are already durable, and the retry comes from the next
     * reconciliation rather than from a loop here.
     */
    suspend fun credit(purchase: StorePurchase): EconomyGemPurchase {
        val granted = economy.grantPurchasedGems(purchase.purchaseId, purchase.productId)
        // An unsupported product is never finalized: this build cannot honour it, and leaving it
        // open keeps it creditable by a later build that knows the product instead of quietly
        // acknowledging a purchase nothing was delivered for.
        if (granted !is EconomyGemPurchase.UnsupportedProduct) {
            runCatching { gateway.finalize(purchase.purchaseId) }
        }
        return granted
    }

    /**
     * Credits everything RuStore still considers undelivered. This is what makes the immediate
     * purchase callback optional rather than load-bearing: a purchase confirmed while the app was
     * dead, backgrounded, or mid-crash is picked up the next time the store is opened.
     */
    suspend fun reconcile(): List<EconomyGemPurchase> = gateway.unfinalizedPurchases().map { credit(it) }

    /** The prices RuStore has for the three known packs; a missing pack is simply not offered. */
    suspend fun offers(): List<GemPackOffer> {
        val priceLabels = gateway.products(GemPack.PRODUCT_IDS).associate { it.productId to it.priceLabel }
        return GemPack.CATALOG.mapNotNull { pack ->
            priceLabels[pack.productId]?.let { GemPackOffer(pack, it) }
        }
    }
}

/** One row of the Gem Store: this build's gem amount next to RuStore's price. */
internal data class GemPackOffer(
    val pack: GemPack,
    val priceLabel: String,
)

/** How one payment ended, in the terms the player is told about. */
internal sealed interface GemPurchaseOutcome {
    data class Granted(
        val pack: GemPack,
    ) : GemPurchaseOutcome

    /** Paid but not settled yet; the gems arrive once reconciliation sees it confirmed. */
    data object Processing : GemPurchaseOutcome

    data object Cancelled : GemPurchaseOutcome

    data object Failed : GemPurchaseOutcome
}
