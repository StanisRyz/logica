package com.stanisryz.logica.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The application-level wallet holder. It owns the only regeneration timer in the product: instead
 * of a repeating background worker, it waits exactly until the next life is due and then persists
 * it, which re-emits the wallet for every other screen.
 */
internal class EconomyViewModel(
    private val repository: EconomyRepository,
    private val clock: EconomyClock = EconomyClock.SYSTEM,
    private val actionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val economy: StateFlow<PlayerEconomy> =
        repository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    private var refillJob: Job? = null

    init {
        viewModelScope.launch { runCatching { repository.refresh() } }
        viewModelScope.launch {
            economy.collectLatest { state ->
                val wait = state.millisUntilNextLife(clock.nowEpochMillis()) ?: return@collectLatest
                delay(wait + REGENERATION_SETTLE_MILLIS)
                runCatching { repository.refresh() }
            }
        }
    }

    /**
     * One intentional purchase gets one action ID. Repeated taps while it runs are ignored, and the
     * repository itself treats a repeat of the same action ID as a no-op.
     */
    fun refillLife() {
        if (refillJob?.isActive == true) return
        val actionId = actionIdFactory()
        refillJob = viewModelScope.launch { runCatching { repository.refillLifeWithGems(actionId) } }
    }

    private companion object {
        /** A small margin so the persisted refresh never lands a millisecond early. */
        const val REGENERATION_SETTLE_MILLIS = 250L
    }
}

internal class EconomyViewModelFactory(
    private val repository: EconomyRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EconomyViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return EconomyViewModel(repository) as T
    }
}
