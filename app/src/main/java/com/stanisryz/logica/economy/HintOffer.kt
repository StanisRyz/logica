package com.stanisryz.logica.economy

/** The gem-priced ways to restock hints; amounts and prices live in [EconomyRules]. */
internal enum class HintOffer(
    val hints: Int,
    val gemCost: Int,
) {
    SINGLE(hints = 1, gemCost = EconomyRules.HINT_SINGLE_GEM_COST),
    PACK(hints = EconomyRules.HINT_PACK_SIZE, gemCost = EconomyRules.HINT_PACK_GEM_COST),
}
