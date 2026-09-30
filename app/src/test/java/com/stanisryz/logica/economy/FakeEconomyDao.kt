package com.stanisryz.logica.economy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory economy tables, so the real DAO transaction logic runs without a device. */
internal class FakeEconomyDao(
    startingEconomy: PlayerEconomy,
) : EconomyDao {
    val events = mutableMapOf<String, EconomyEventEntity>()
    private var economy: PlayerEconomyEntity? = startingEconomy.toEntity(0)

    override fun observe(): Flow<PlayerEconomyEntity?> = flowOf(economy)

    override suspend fun find(): PlayerEconomyEntity? = economy

    override suspend fun upsert(economy: PlayerEconomyEntity) {
        this.economy = economy
    }

    override suspend fun insertEvent(event: EconomyEventEntity): Long = if (events.putIfAbsent(event.eventId, event) == null) 1 else -1

    override suspend fun hasEvent(eventId: String): Boolean = events.containsKey(eventId)

    override fun observeEventIds(prefix: String): Flow<List<String>> = flowOf(events.keys.filter { it.startsWith(prefix) })

    override fun observeHasEventType(type: String): Flow<Boolean> = flowOf(events.values.any { it.eventType == type })

    override fun observeLastLoginGift(): Flow<EconomyEventEntity?> = flowOf(lastLoginGift())

    override suspend fun findLastLoginGift(): EconomyEventEntity? = lastLoginGift()

    private fun lastLoginGift(): EconomyEventEntity? =
        events.values.filter { it.eventType == EconomyEventType.LOGIN_GIFT.name }.maxByOrNull { it.createdAtEpochMillis }

    fun wallet(nowEpochMillis: Long): PlayerEconomy = economy.toPlayerEconomy(nowEpochMillis)
}
