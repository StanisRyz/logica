package com.stanisryz.logica.store

import com.stanisryz.logica.economy.GemPack

/** Provider configuration for the application's fixed gem packs. */
internal class GemPackProductMapping(
    platformProductIds: Map<GemPack, String>,
) {
    private val productIds = platformProductIds.toMap()
    private val packsByProductId = productIds.entries.associate { (pack, productId) -> productId to pack }

    init {
        require(productIds.keys == GemPack.CATALOG.toSet()) { "Every gem pack must have a platform product ID." }
        require(productIds.values.all(String::isNotBlank)) { "Platform product IDs must not be blank." }
        require(packsByProductId.size == productIds.size) { "Platform product IDs must be unique." }
    }

    fun productId(pack: GemPack): String = checkNotNull(productIds[pack])

    fun pack(productId: String): GemPack? = packsByProductId[productId]

    fun productIds(): List<String> = GemPack.CATALOG.map(::productId)
}
