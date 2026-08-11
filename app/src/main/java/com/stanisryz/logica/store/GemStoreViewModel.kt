package com.stanisryz.logica.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.GemPack
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Gem Store can be. */
internal sealed interface GemStoreState {
    /** Prices are being loaded from RuStore. */
    data object Loading : GemStoreState

    /**
     * The packs are on screen. [purchasing] is the pack whose payment is already running, which is
     * what keeps a second tap from starting a second payment, and [outcome] is how the last attempt
     * ended.
     */
    data class Ready(
        val offers: List<GemPackOffer>,
        val purchasing: GemPack? = null,
        val outcome: GemPurchaseOutcome? = null,
    ) : GemStoreState

    /** RuStore is not usable here. The gem balance stays visible and a retry is offered. */
    data object Unavailable : GemStoreState
}

/**
 * The Gem Store's state holder.
 *
 * It never touches the wallet itself: a credited purchase reaches the UI through the same
 * [EconomyRepository] flow every other screen reads, so the balance is Room's number rather than a
 * count this class incremented. Reconciliation runs on construction and again whenever the store is
 * opened, so a purchase that was paid for but never credited is picked up without the player having
 * to ask for it — and without any restore button, which a consumable currency does not need.
 */
internal class GemStoreViewModel(
    private val purchases: GemPurchaseProcessor,
) : ViewModel() {
    private val _state = MutableStateFlow<GemStoreState>(GemStoreState.Loading)
    val state: StateFlow<GemStoreState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var purchaseJob: Job? = null

    init {
        // A quiet catch-up for anything paid for last session. Failure here is invisible and costs
        // the application nothing: the store is simply loaded again when it is opened.
        viewModelScope.launch { runCatching { purchases.reconcile() } }
    }

    /** Called when the store opens: reconcile first, then show the prices. */
    fun open() {
        if (loadJob?.isActive == true) return
        _state.value = GemStoreState.Loading
        loadJob =
            viewModelScope.launch {
                runCatching { purchases.reconcile() }
                _state.value =
                    runCatching { purchases.offers() }
                        .fold(
                            onSuccess = { offers ->
                                if (offers.isEmpty()) GemStoreState.Unavailable else GemStoreState.Ready(offers)
                            },
                            onFailure = { GemStoreState.Unavailable },
                        )
            }
    }

    /**
     * Starts one payment. Repeated taps while a payment is running are ignored here, and the ledger
     * would refuse a duplicate anyway, so neither layer depends on the other being careful.
     */
    fun buy(pack: GemPack) {
        val ready = _state.value as? GemStoreState.Ready ?: return
        if (ready.purchasing != null || purchaseJob?.isActive == true) return
        _state.value = ready.copy(purchasing = pack, outcome = null)
        purchaseJob =
            viewModelScope.launch {
                val outcome = runCatching { purchases.buy(pack) }.getOrDefault(GemPurchaseOutcome.Failed)
                _state.update { current ->
                    if (current is GemStoreState.Ready) current.copy(purchasing = null, outcome = outcome) else current
                }
            }
    }

    /** Clears the last result once the player has seen it. */
    fun dismissOutcome() {
        _state.update { current -> if (current is GemStoreState.Ready) current.copy(outcome = null) else current }
    }
}

internal class GemStoreViewModelFactory(
    private val economyRepository: EconomyRepository,
    private val gateway: RuStorePayGateway,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GemStoreViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return GemStoreViewModel(GemPurchaseProcessor(gateway, economyRepository)) as T
    }
}
