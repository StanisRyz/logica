package com.stanisryz.logica.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanisryz.logica.R
import com.stanisryz.logica.economy.EconomyRepository
import com.stanisryz.logica.economy.EconomyRules
import com.stanisryz.logica.economy.GameplayHints
import com.stanisryz.logica.economy.GemPack
import com.stanisryz.logica.economy.HintOffer
import com.stanisryz.logica.economy.PlayerEconomy
import com.stanisryz.logica.platform.StoreGateway
import com.stanisryz.logica.store.GemPackOffer
import com.stanisryz.logica.store.GemPackProductMapping
import com.stanisryz.logica.store.GemPurchaseOutcome
import com.stanisryz.logica.store.GemStoreState
import com.stanisryz.logica.store.GemStoreViewModel
import com.stanisryz.logica.store.GemStoreViewModelFactory
import com.stanisryz.logica.ui.components.GemPriceButton
import com.stanisryz.logica.ui.components.ScreenColumn
import com.stanisryz.logica.ui.components.StoreBalanceCard
import com.stanisryz.logica.ui.components.StoreItemRow
import com.stanisryz.logica.ui.components.StoreSectionTitle
import com.stanisryz.logica.ui.components.SupportingText
import com.stanisryz.logica.ui.theme.LogicaMotion
import com.stanisryz.logica.ui.theme.LogicaSpacing
import kotlinx.coroutines.launch

/**
 * The Store tab's state holder is created here rather than in the application shell, so a session
 * that never opens the Store never even builds one — and RuStore work cannot start because the
 * process, the root composition, or another tab came up. The ViewModel is scoped to the primary
 * destination like every other tab's, so switching tabs keeps the loaded prices.
 */
@Composable
internal fun StoreRoute(
    economy: PlayerEconomy,
    economyRepository: EconomyRepository,
    storeGateway: StoreGateway,
    storeProducts: GemPackProductMapping,
    modifier: Modifier = Modifier,
) {
    val factory =
        remember(economyRepository, storeGateway, storeProducts) {
            GemStoreViewModelFactory(economyRepository, storeGateway, storeProducts)
        }
    val storeViewModel: GemStoreViewModel = viewModel(factory = factory)
    val state by storeViewModel.state.collectAsStateWithLifecycle()
    // Hints are bought with gems already in the wallet, so no store provider is involved.
    val hints = remember(economyRepository) { GameplayHints(economyRepository) }
    val scope = rememberCoroutineScope()

    StoreScreen(
        economy = economy,
        state = state,
        onOpen = storeViewModel::open,
        onBuy = storeViewModel::buy,
        onDismissOutcome = storeViewModel::dismissOutcome,
        onBuyHints = { offer -> scope.launch { hints.buy(offer) } },
        modifier = modifier,
    )
}

/**
 * The same Store, opened over a running game. The game destination stays on the back stack
 * underneath, so its ViewModel and the unsaved attempt survive; Back or a swipe closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreSheet(
    economy: PlayerEconomy,
    economyRepository: EconomyRepository,
    storeGateway: StoreGateway,
    storeProducts: GemPackProductMapping,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StoreRoute(
            economy = economy,
            economyRepository = economyRepository,
            storeGateway = storeGateway,
            storeProducts = storeProducts,
        )
    }
}

/**
 * The one Gem Store in the product, now a primary tab rather than a dialog. It sells gem packs and
 * nothing else, and it is only ever reached because the player asked for it — from the tab bar, from
 * the gem balance, or from the lives dialog when a refill is out of reach.
 *
 * Every price on screen is RuStore's own formatted label; the application never states an amount of
 * money. The gem counts beside them come from the local [GemPack] table, so what a pack is worth is
 * this build's number rather than something read out of store metadata.
 */
@Composable
internal fun StoreScreen(
    economy: PlayerEconomy,
    state: GemStoreState,
    onOpen: () -> Unit,
    onBuy: (GemPack) -> Unit,
    onDismissOutcome: () -> Unit,
    modifier: Modifier = Modifier,
    onBuyHints: (HintOffer) -> Unit = {},
) {
    // Opening the store is what reconciles anything paid for but not yet credited, and what loads
    // the prices. Leaving the tab clears the last purchase message rather than keeping it forever.
    LaunchedEffect(Unit) { onOpen() }
    DisposableEffect(Unit) { onDispose(onDismissOutcome) }

    ScreenColumn(modifier, verticalSpacing = LogicaSpacing.item) {
        // The balance stays visible in every state, including a store that cannot load.
        StoreBalanceCard(
            gems = economy.gems.toLong(),
            lives = economy.lives,
            maximumLives = EconomyRules.MAX_LIVES,
            hints = economy.hints,
        )
        StoreSectionTitle(stringResource(R.string.gem_store_section))
        AnimatedContent(
            targetState = state,
            contentKey = { it.presentationKey() },
            transitionSpec = {
                fadeIn(tween(LogicaMotion.SCREEN_MILLIS)) togetherWith
                    fadeOut(tween(LogicaMotion.SHORT_MILLIS))
            },
            label = "storeState",
        ) { currentState ->
            Box(
                Modifier.semantics {
                    if (currentState.presentationKey() != state.presentationKey()) hideFromAccessibility()
                },
            ) {
                when (currentState) {
                    GemStoreState.Loading -> GemStoreLoading()
                    GemStoreState.Unavailable -> GemStoreUnavailable(onOpen)
                    is GemStoreState.Ready -> GemStoreOffers(currentState, onBuy)
                }
            }
        }
        // Hints are a consumable item bought with gems; this works even when paid packs cannot load.
        StoreSectionTitle(stringResource(R.string.hints_store_for_gems))
        HintOffer.entries.forEach { offer ->
            val missing = (offer.gemCost - economy.gems).coerceAtLeast(0)
            StoreItemRow(
                icon = Icons.Filled.Lightbulb,
                title = pluralStringResource(R.plurals.hints_store_offer_title, offer.hints, offer.hints),
                subtitle = if (missing > 0) pluralStringResource(R.plurals.hints_store_missing, missing, missing) else null,
                subtitleColor = MaterialTheme.colorScheme.error,
            ) {
                GemPriceButton(price = offer.gemCost, enabled = missing == 0, onClick = { onBuyHints(offer) })
            }
        }
    }
}

private fun GemStoreState.presentationKey(): String =
    when (this) {
        GemStoreState.Loading -> "loading"
        GemStoreState.Unavailable -> "unavailable"
        is GemStoreState.Ready -> "ready"
    }

@Composable
private fun GemStoreLoading() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LogicaSpacing.action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE))
        SupportingText(stringResource(R.string.gem_store_loading))
    }
}

@Composable
private fun GemStoreUnavailable(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
        SupportingText(stringResource(R.string.gem_store_unavailable))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.gem_store_retry)) }
    }
}

@Composable
private fun GemStoreOffers(
    state: GemStoreState.Ready,
    onBuy: (GemPack) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item)) {
        state.offers.forEach { offer ->
            GemPackRow(
                offer = offer,
                // One payment at a time: every Buy is disabled while any of them is running.
                enabled = state.purchasing == null,
                isPurchasing = state.purchasing == offer.pack,
                onBuy = { onBuy(offer.pack) },
            )
        }
        AnimatedVisibility(
            visible = state.outcome != null,
            enter = fadeIn(tween(LogicaMotion.SHORT_MILLIS)),
            exit = fadeOut(tween(LogicaMotion.SHORT_MILLIS)),
        ) {
            state.outcome?.let { GemPurchaseMessage(it) }
        }
    }
}

@Composable
private fun GemPackRow(
    offer: GemPackOffer,
    enabled: Boolean,
    isPurchasing: Boolean,
    onBuy: () -> Unit,
) {
    StoreItemRow(
        icon = Icons.Filled.Diamond,
        title = stringResource(R.string.gem_store_pack_gems, offer.pack.gems),
        subtitle = null,
    ) {
        Button(onClick = onBuy, enabled = enabled) {
            // The price label is RuStore's, formatted and localized by the store itself.
            Text(if (isPurchasing) stringResource(R.string.gem_store_purchasing) else offer.priceLabel)
        }
    }
}

@Composable
private fun GemPurchaseMessage(outcome: GemPurchaseOutcome) {
    SupportingText(
        when (outcome) {
            is GemPurchaseOutcome.Granted -> stringResource(R.string.gem_store_granted, outcome.pack.gems)
            GemPurchaseOutcome.Processing -> stringResource(R.string.gem_store_processing)
            GemPurchaseOutcome.Cancelled -> stringResource(R.string.gem_store_cancelled)
            GemPurchaseOutcome.Failed -> stringResource(R.string.gem_store_failed)
        },
    )
}

private val ICON_SIZE = 20.dp
private val INDICATOR_SIZE = 18.dp
