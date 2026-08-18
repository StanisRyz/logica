package com.stanisryz.logica.economy

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
) {
    GEMS_50("gems_50", 50),
    GEMS_250("gems_250", 250),
    GEMS_600("gems_600", 600),
    ;

    companion object {
        /** The order the Gem Store shows them in: smallest pack first. */
        val CATALOG: List<GemPack> = entries

        /** The application whitelist lookup used inside the durable economy transaction. */
        fun forKey(key: String): GemPack? = entries.firstOrNull { it.key == key }
    }
}
