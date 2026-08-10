package com.stanisryz.logica.economy

/**
 * A missing row is a brand-new player, and a stored row that breaks a wallet invariant is repaired
 * rather than allowed to crash the gameplay path: gems and lives are clamped, and a missing anchor
 * for a missing life restarts one interval from now.
 */
internal fun PlayerEconomyEntity?.toPlayerEconomy(nowEpochMillis: Long): PlayerEconomy {
    if (this == null) return PlayerEconomy()
    val safeLives = lives.coerceIn(0, EconomyRules.MAX_LIVES)
    return PlayerEconomy(
        gems = gems.coerceAtLeast(0),
        lives = safeLives,
        nextLifeAtEpochMillis =
            if (safeLives >= EconomyRules.MAX_LIVES) {
                null
            } else {
                nextLifeAtEpochMillis ?: (nowEpochMillis + EconomyRules.LIFE_REGENERATION_INTERVAL_MILLIS)
            },
    )
}

internal fun PlayerEconomy.toEntity(updatedAtEpochMillis: Long): PlayerEconomyEntity =
    PlayerEconomyEntity(
        gems = gems,
        lives = lives,
        nextLifeAtEpochMillis = nextLifeAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

internal fun EconomyEvent.toEntity(createdAtEpochMillis: Long): EconomyEventEntity =
    EconomyEventEntity(
        eventId = eventId,
        eventType = type.name,
        sourceId = sourceId,
        gemDelta = gemDelta,
        lifeDelta = lifeDelta,
        createdAtEpochMillis = createdAtEpochMillis,
    )
