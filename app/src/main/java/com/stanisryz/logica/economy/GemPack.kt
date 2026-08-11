package com.stanisryz.logica.economy

/**
 * The complete list of things real money can buy, and the only place a purchased gem amount is
 * decided.
 *
 * [productId] and [gems] together are a compatibility contract: the IDs are what the RuStore Console
 * catalogue is configured with, and the reward is read from this table rather than derived from
 * anything the store sends back. Nothing parses digits out of `gems_250`, and no title, description,
 * or remote quantity can change what a pack is worth. A purchase whose product ID is not in this
 * table is worth nothing at all.
 */
internal enum class GemPack(
    val productId: String,
    val gems: Int,
) {
    GEMS_50("gems_50", 50),
    GEMS_250("gems_250", 250),
    GEMS_600("gems_600", 600),
    ;

    companion object {
        /** The order the Gem Store shows them in: smallest pack first. */
        val CATALOG: List<GemPack> = entries

        val PRODUCT_IDS: List<String> = entries.map { it.productId }

        /** The whitelist lookup. `null` means the store sold something this build cannot honour. */
        fun forProductId(productId: String): GemPack? = entries.firstOrNull { it.productId == productId }
    }
}
