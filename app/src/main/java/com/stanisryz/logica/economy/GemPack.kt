package com.stanisryz.logica.economy

import com.stanisryz.logica.platform.EconomyPolicy

/**
 * The complete list of things real money can buy, and the only place a purchased gem amount is
 * decided.
 *
 * [key] and [gems] are application-owned business data. A platform mapping selects the RuStore or
 * future Yandex product ID for each pack; the reward is never derived from store metadata.
 */
internal enum class GemPack(
    val key: String,
    val gems: Int,
    val hints: Int = 0,
    val refillsLives: Boolean = false,
) {
    GEMS_50("gems_50", EconomyPolicy.GEM_PACK_SMALL),
    GEMS_150("gems_150", EconomyPolicy.GEM_PACK_MEDIUM),
    GEMS_500("gems_500", EconomyPolicy.GEM_PACK_LARGE),

    /** The one-time starter pack: gems, hints, and every missing life; offered until bought once. */
    STARTER_PACK("starter_pack", EconomyPolicy.STARTER_PACK_GEMS, hints = EconomyPolicy.STARTER_PACK_HINTS, refillsLives = true),
    ;

    companion object {
        /** The ordinary gem packs the Gem Store lists: smallest pack first. */
        val CATALOG: List<GemPack> = listOf(GEMS_50, GEMS_150, GEMS_500)

        /** The application whitelist lookup used inside the durable economy transaction. */
        fun forKey(key: String): GemPack? = entries.firstOrNull { it.key == key }
    }
}
