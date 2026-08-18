package com.stanisryz.logica.platform

internal enum class PlayerAuthorizationState {
    AUTHORIZED,
    ANONYMOUS,
    UNSUPPORTED,
}

/** Profile information owned by the application rather than any account SDK. */
internal data class PlayerIdentity(
    val playerId: String? = null,
    val displayName: String? = null,
    val avatarReference: String? = null,
    val authorizationState: PlayerAuthorizationState,
    val provider: String,
)

internal sealed interface PlayerAuthorizationResult {
    data class Available(
        val identity: PlayerIdentity,
    ) : PlayerAuthorizationResult

    data object Unsupported : PlayerAuthorizationResult

    data class Failed(
        val cause: Throwable,
    ) : PlayerAuthorizationResult
}

internal interface PlayerIdentityGateway {
    suspend fun identity(): PlayerIdentity

    suspend fun requestAuthorization(): PlayerAuthorizationResult
}
