package com.stanisryz.logica.platform

/**
 * Platform-neutral current-Player source. Implementations expose whatever identity the host
 * account system already provides (for Web/Yandex: the SDK's automatic Player) and never own
 * authentication, login UI, passwords, or custom accounts.
 */
interface PlayerProvider {
    /** The current Player identity, or null while no identity is available yet. */
    suspend fun currentPlayer(): PlayerIdentity?

    /** Whether an identity can currently be provided at all. */
    fun isIdentityAvailable(): Boolean
}
