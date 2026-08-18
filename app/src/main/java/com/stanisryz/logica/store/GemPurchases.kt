package com.stanisryz.logica.store

import com.stanisryz.logica.economy.EconomyGemPurchase
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.platform.PlatformPurchase
import com.stanisryz.logica.platform.PlatformPurchaseResult
import com.stanisryz.logica.platform.StoreGateway

/**
 * Turns a confirmed platform purchase into gems, in the one order that is safe to crash in.
 *
 * The ledger row goes first and platform finalization second. If the process dies between them,
 * the store still considers the purchase undelivered and hands it back from
 * [StoreGateway.unprocessedPurchases]; the ledger insert is then a no-op, no second pack of
 * gems is credited, and only the finalization is retried. The reverse order could lose a paid
 * purchase entirely, which is the one outcome that costs the player real money.
 */
internal class GemPurchaseProcessor(
    private val gateway: StoreGateway,
    private val products: GemPackProductMapping,
    private val economy: EconomyRepository,
) {
    /**
     * Runs one payment end to end. Gems are credited for exactly one purchase state — the confirmed
     * one — so a cancelled, still-settling, or failed payment moves the wallet by nothing at all.
     */
    suspend fun buy(pack: GemPack): GemPurchaseOutcome =
        when (val result = gateway.purchase(products.productId(pack))) {
            is PlatformPurchaseResult.Confirmed ->
                when (val credited = credit(result.purchase)) {
                    is EconomyGemPurchase.Granted -> GemPurchaseOutcome.Granted(credited.pack)
                    // Already credited means the gems are in the wallet; from the player's side the
                    // pack simply arrived, so they are told the same thing either way.
                    is EconomyGemPurchase.AlreadyGranted -> GemPurchaseOutcome.Granted(pack)
                    is EconomyGemPurchase.UnsupportedProduct -> GemPurchaseOutcome.Failed
                }
            // A payment that is still settling is finished by reconciliation, not by waiting here.
            PlatformPurchaseResult.Pending -> GemPurchaseOutcome.Processing
            PlatformPurchaseResult.Cancelled -> GemPurchaseOutcome.Cancelled
            is PlatformPurchaseResult.Failed -> GemPurchaseOutcome.Failed
        }

    /**
     * Credits one confirmed purchase and then closes it with the platform. A finalization failure is
     * deliberately swallowed: the gems are already durable, and the retry comes from the next
     * reconciliation rather than from a loop here.
     */
    suspend fun credit(purchase: PlatformPurchase): EconomyGemPurchase {
        val pack = products.pack(purchase.productId)
        // An unknown platform product is deliberately converted to an unsupported application key;
        // the economy then returns its existing safe result without writing a ledger event.
        val productKey = pack?.key ?: "unsupported-platform-product:${purchase.productId}"
        val granted = economy.grantPurchasedGems(purchase.transactionId, productKey)
        // An unsupported product is never finalized: this build cannot honour it, and leaving it
        // open keeps it creditable by a later build that knows the product instead of quietly
        // acknowledging a purchase nothing was delivered for.
        if (granted !is EconomyGemPurchase.UnsupportedProduct) {
            runCatching { gateway.finalize(purchase.purchaseId) }
        }
        return granted
    }

    /**
     * Credits everything the platform still considers undelivered. This makes the immediate
     * purchase callback optional rather than load-bearing: a purchase confirmed while the app was
     * dead, backgrounded, or mid-crash is picked up the next time the store is opened.
     */
    suspend fun reconcile(): List<EconomyGemPurchase> = gateway.unprocessedPurchases().map { credit(it) }

    /** The platform prices for the three known packs; a missing pack is simply not offered. */
    suspend fun offers(): List<GemPackOffer> {
        val priceLabels = gateway.products(products.productIds()).associate { it.productId to it.priceLabel }
        return GemPack.CATALOG.mapNotNull { pack ->
            priceLabels[products.productId(pack)]?.let { GemPackOffer(pack, it) }
        }
    }
}

/** One row of the Gem Store: this build's gem amount next to the platform's price. */
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
