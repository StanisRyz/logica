package com.stanisryz.logica.store

import com.stanisryz.logica.economy.GemPack

/**
 * Provider configuration for the application's paid products: the fixed gem packs, and optionally
 * the one-time starter pack and the permanent «no ads» product.
 */
internal class GemPackProductMapping(
    platformProductIds: Map<GemPack, String>,
    private val starterPackProductId: String? = null,
    val noAdsProductId: String? = null,
) {
    private val productIds = platformProductIds.toMap() + listOfNotNull(starterPackProductId?.let { GemPack.STARTER_PACK to it })
    private val packsByProductId = productIds.entries.associate { (pack, productId) -> productId to pack }

    init {
        require(platformProductIds.keys == GemPack.CATALOG.toSet()) { "Every gem pack must have a platform product ID." }
        require(productIds.values.all(String::isNotBlank)) { "Platform product IDs must not be blank." }
        require(packsByProductId.size == productIds.size) { "Platform product IDs must be unique." }
        require(noAdsProductId == null || noAdsProductId !in packsByProductId) { "«No ads» needs its own product ID." }
    }

    fun productId(pack: GemPack): String = checkNotNull(productIds[pack])

    fun pack(productId: String): GemPack? = packsByProductId[productId]

    fun isNoAds(productId: String): Boolean = productId == noAdsProductId

    val hasStarterPack: Boolean
        get() = starterPackProductId != null

    /** Every product whose price the store shows. */
    fun productIds(): List<String> = GemPack.CATALOG.map(::productId) + listOfNotNull(starterPackProductId, noAdsProductId)
}
