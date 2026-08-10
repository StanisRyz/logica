package com.stanisryz.logica.economy

/**
 * The one time source the economy is allowed to read. Repositories and ViewModels take it instead
 * of calling [System.currentTimeMillis] directly, which keeps the 15-minute regeneration math
 * deterministic in tests.
 */
internal fun interface EconomyClock {
    fun nowEpochMillis(): Long

    companion object {
        val SYSTEM = EconomyClock { System.currentTimeMillis() }
    }
}
