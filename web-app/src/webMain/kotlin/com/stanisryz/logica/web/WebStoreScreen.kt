package com.stanisryz.logica.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.StoreItem
import com.stanisryz.logica.platform.StoreRewardType
import com.stanisryz.logica.ui.components.GemPriceButton
import com.stanisryz.logica.ui.components.NoAdsRow
import com.stanisryz.logica.ui.components.STATE_ARTWORK_DIALOG_SIZE
import com.stanisryz.logica.ui.components.StarterPackCard
import com.stanisryz.logica.ui.components.StateArtwork
import com.stanisryz.logica.ui.components.StateArtworkImage
import com.stanisryz.logica.ui.components.StoreArtwork
import com.stanisryz.logica.ui.components.StoreBalanceCard
import com.stanisryz.logica.ui.components.StoreItemRow
import com.stanisryz.logica.ui.components.StoreSectionTitle
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.web.generated.resources.web_ad_cooldown
import com.stanisryz.logica.web.generated.resources.web_ad_dismissed
import com.stanisryz.logica.web.generated.resources.web_ad_in_progress
import com.stanisryz.logica.web.generated.resources.web_ad_offer
import com.stanisryz.logica.web.generated.resources.web_ad_unavailable
import com.stanisryz.logica.web.generated.resources.web_ad_watch
import com.stanisryz.logica.web.generated.resources.web_back_to_game
import com.stanisryz.logica.web.generated.resources.web_gems_plus
import com.stanisryz.logica.web.generated.resources.web_hints_plus
import com.stanisryz.logica.web.generated.resources.web_item_hint
import com.stanisryz.logica.web.generated.resources.web_item_hint_pack
import com.stanisryz.logica.web.generated.resources.web_item_life
import com.stanisryz.logica.web.generated.resources.web_life_ad_granted
import com.stanisryz.logica.web.generated.resources.web_lives_full
import com.stanisryz.logica.web.generated.resources.web_lives_plus
import com.stanisryz.logica.web.generated.resources.web_missing_gems
import com.stanisryz.logica.web.generated.resources.web_no_hints_body
import com.stanisryz.logica.web.generated.resources.web_no_hints_title
import com.stanisryz.logica.web.generated.resources.web_not_now
import com.stanisryz.logica.web.generated.resources.web_paid_cancelled
import com.stanisryz.logica.web.generated.resources.web_paid_error
import com.stanisryz.logica.web.generated.resources.web_paid_fulfilling
import com.stanisryz.logica.web.generated.resources.web_paid_pending
import com.stanisryz.logica.web.generated.resources.web_paid_purchasing
import com.stanisryz.logica.web.generated.resources.web_paid_saving
import com.stanisryz.logica.web.generated.resources.web_paid_success
import com.stanisryz.logica.web.generated.resources.web_paid_unavailable
import com.stanisryz.logica.web.generated.resources.web_purchase_done_hints
import com.stanisryz.logica.web.generated.resources.web_purchase_done_lives
import com.stanisryz.logica.web.generated.resources.web_purchase_failed
import com.stanisryz.logica.web.generated.resources.web_purchase_insufficient
import com.stanisryz.logica.web.generated.resources.web_store_gem_ad_granted
import com.stanisryz.logica.web.generated.resources.web_store_gem_ad_title
import com.stanisryz.logica.web.generated.resources.web_store_life_ad_title
import com.stanisryz.logica.web.generated.resources.web_store_next_life
import com.stanisryz.logica.web.generated.resources.web_store_section_for_gems
import com.stanisryz.logica.web.generated.resources.web_store_section_gems
import com.stanisryz.logica.web.generated.resources.web_to_store
import com.stanisryz.logica.web.generated.resources.web_wallet_unavailable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/**
 * The minimal internal Store screen: gems balance, the static catalog, purchase buttons, and
 * result feedback. Purchases use internal gems only and go through [WebStoreProcessor]; there is
 * no payment, ad, or external billing integration anywhere on this path.
 */
@Composable
internal fun WebStoreScreen(
    playerSession: WebPlayerSessionController,
    storeProcessor: WebStoreProcessor,
    paymentsCoordinator: WebPaymentsCoordinator,
    rewardedAds: WebRewardedAds,
) {
    val economyBinding by playerSession.economyBinding.collectAsState()
    val storeBinding by playerSession.storeBinding.collectAsState()
    var feedback by remember { mutableStateOf<WebStoreFeedback?>(null) }

    // Real-money catalog loads once per visit; standalone/unsupported hides the section.
    LaunchedEffect(paymentsCoordinator) { paymentsCoordinator.refreshCatalog() }
    val paidCatalog by paymentsCoordinator.catalogState.collectAsState()
    val purchaseState by paymentsCoordinator.purchaseState.collectAsState()
    val purchasingProduct by paymentsCoordinator.purchasingProduct.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = LogicaSpacing.screenHorizontal,
                    vertical = LogicaSpacing.screenVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(LogicaSpacing.item),
    ) {
        val hints =
            (storeBinding as? WebStoreBinding.Ready)
                ?.repository
                ?.snapshot
                ?.collectAsState()
                ?.value
                ?.quantityOf(STORE_INVENTORY_HINTS)
        when (val economy = economyBinding) {
            is WebEconomyBinding.Ready -> {
                val state =
                    economy.repository.state
                        .collectAsState()
                        .value
                val now = rememberNowMs(ticking = state.nextLifeRestoreAtEpochMs != null)
                StoreBalanceCard(
                    gems = state.gems.toLong(),
                    lives = state.lives,
                    maximumLives = EconomyPolicy.MAXIMUM_LIVES,
                    hints = hints,
                    footnote =
                        state.nextLifeRestoreAtEpochMs?.let {
                            stringResource(WebRes.string.web_store_next_life, formatLifeCountdown(it - now))
                        },
                )
            }
            else -> Text(stringResource(WebRes.string.web_wallet_unavailable), style = MaterialTheme.typography.bodyMedium)
        }

        RewardedAdRow(
            controller = rewardedAds.gems,
            title = stringResource(WebRes.string.web_store_gem_ad_title),
            grantedText = stringResource(WebRes.string.web_store_gem_ad_granted),
            enabled = economyBinding is WebEconomyBinding.Ready,
            artwork = StoreArtwork.AD_GEM,
        )
        // The life placement exists only while a life is actually missing.
        val walletLives =
            (economyBinding as? WebEconomyBinding.Ready)
                ?.repository
                ?.state
                ?.collectAsState()
                ?.value
                ?.lives
        if (walletLives != null && walletLives < EconomyPolicy.MAXIMUM_LIVES) {
            RewardedAdRow(
                controller = rewardedAds.life,
                title = stringResource(WebRes.string.web_store_life_ad_title),
                grantedText = stringResource(WebRes.string.web_life_ad_granted),
                enabled = true,
                artwork = StoreArtwork.AD_LIFE,
            )
        }

        // Real-money gem top-up (Yandex Payments): price/currency come from the Yandex catalog.
        if (paidCatalog is WebPaidCatalogState.Ready) {
            val entries = (paidCatalog as WebPaidCatalogState.Ready).entries
            val ledger = playerSession.paymentsRepository?.let { key(it) { it.snapshot.collectAsState().value } }
            // The starter pack is offered until the Player has bought it once.
            entries.firstOrNull { it.product == WebPaidProduct.STARTER_PACK }?.let { entry ->
                if (ledger != null && !ledger.owns(WebPaidProduct.STARTER_PACK)) {
                    StarterPackCard(
                        enabled = !purchaseState.isBusy,
                        onBuy = { paymentsCoordinator.purchase(entry.product) },
                        message = if (purchasingProduct == entry.product) paidPurchaseMessage(purchaseState) else null,
                    ) { PaidPriceLabel(entry.details) }
                }
            }
            StoreSectionTitle(stringResource(WebRes.string.web_store_section_gems))
            entries.filter { it.product in WebPaidProduct.GEM_PACKS }.forEach { entry ->
                PaidGemTopUpCard(entry, purchaseState, rowReportsState = purchasingProduct == entry.product, paymentsCoordinator)
            }
            entries.firstOrNull { it.product == WebPaidProduct.NO_ADS }?.let { entry ->
                NoAdsRow(
                    owned = ledger?.owns(WebPaidProduct.NO_ADS) == true,
                    enabled = ledger != null && !purchaseState.isBusy,
                    onBuy = { paymentsCoordinator.purchase(entry.product) },
                    message = if (purchasingProduct == entry.product) paidPurchaseMessage(purchaseState) else null,
                ) { PaidPriceLabel(entry.details) }
            }
        }

        StoreSectionTitle(stringResource(WebRes.string.web_store_section_for_gems))
        WebStoreCatalog.ITEMS.forEach { item -> StoreCatalogRow(item, economyBinding, storeProcessor, { feedback = it }) }

        feedback?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message.text(),
                    modifier = Modifier.padding(LogicaSpacing.cardContent),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(LogicaSpacing.section))
    }
}

@Composable
private fun PaidGemTopUpCard(
    entry: WebPaidCatalogEntry,
    state: WebPaidPurchaseState,
    rowReportsState: Boolean,
    coordinator: WebPaymentsCoordinator,
) {
    // One payment runs at a time: every pack waits for it, and only the one being bought says why.
    val message = if (rowReportsState) paidPurchaseMessage(state) else null
    StoreItemRow(
        artwork = StoreArtwork.forGemPack(entry.product.gemReward),
        title = pluralStringResource(WebRes.plurals.web_gems_plus, entry.product.gemReward, entry.product.gemReward),
        // Without a message or a catalog description the title stands alone, centred on the price.
        subtitle = message ?: entry.details.description?.takeIf { it.isNotBlank() },
        subtitleColor =
            if (rowReportsState &&
                state == WebPaidPurchaseState.Success
            ) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    ) {
        // Portal-supplied price + currency only; never a locally manufactured amount.
        Button(onClick = { coordinator.purchase(entry.product) }, enabled = !state.isBusy) {
            PaidPriceLabel(entry.details)
        }
    }
}

private val WebPaidPurchaseState.isBusy: Boolean
    get() =
        this == WebPaidPurchaseState.Purchasing ||
            this == WebPaidPurchaseState.Fulfilling ||
            this == WebPaidPurchaseState.Saving

/**
 * Price exactly as the Yandex catalog supplies it: the amount with the portal currency icon, or,
 * while the icon is unavailable, the catalog's own price text, which already names the currency.
 */
@Composable
private fun PaidPriceLabel(details: PaymentProductSnapshot) {
    val icon = rememberCurrencyIcon(details.priceCurrencyImageUrl)
    val amount = details.priceValue
    if (icon != null && amount != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(amount)
            Image(bitmap = icon, contentDescription = details.priceCurrencyCode, modifier = Modifier.size(18.dp))
        }
    } else {
        Text(paidPriceText(details))
    }
}

internal fun paidPriceText(details: PaymentProductSnapshot): String =
    details.price?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(details.priceValue, details.priceCurrencyCode).joinToString(" ").ifEmpty { "—" }

@Composable
private fun paidPurchaseMessage(state: WebPaidPurchaseState): String? =
    when (state) {
        WebPaidPurchaseState.Purchasing -> stringResource(WebRes.string.web_paid_purchasing)
        WebPaidPurchaseState.Fulfilling -> stringResource(WebRes.string.web_paid_fulfilling)
        WebPaidPurchaseState.Saving -> stringResource(WebRes.string.web_paid_saving)
        WebPaidPurchaseState.Success -> stringResource(WebRes.string.web_paid_success)
        WebPaidPurchaseState.Cancelled -> stringResource(WebRes.string.web_paid_cancelled)
        WebPaidPurchaseState.Unavailable -> stringResource(WebRes.string.web_paid_unavailable)
        WebPaidPurchaseState.CloudPending -> stringResource(WebRes.string.web_paid_pending)
        WebPaidPurchaseState.Error -> stringResource(WebRes.string.web_paid_error)
        WebPaidPurchaseState.Idle -> null
    }

/** One rewarded placement row; the exchange is always disclosed: watching an advertisement is required. */
@Composable
internal fun RewardedAdRow(
    controller: WebRewardedPlacementController,
    title: String,
    grantedText: String,
    enabled: Boolean,
    artwork: StoreArtwork,
) {
    val state by controller.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val (subtitle, subtitleColor) = rewardedAdSubtitle(state, grantedText)
    StoreItemRow(
        artwork = artwork,
        title = title,
        subtitle = subtitle,
        subtitleColor = subtitleColor ?: colors.onSurfaceVariant,
        highlighted = true,
    ) {
        Button(
            onClick = controller::requestReward,
            enabled = controller.isRequestAllowed && enabled,
        ) {
            Text(stringResource(if (state == WebRewardedAdState.Showing) WebRes.string.web_ad_in_progress else WebRes.string.web_ad_watch))
        }
    }
}

@Composable
internal fun rewardedAdSubtitle(
    state: WebRewardedAdState,
    grantedText: String,
): Pair<String, Color?> {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        WebRewardedAdState.RewardGranted -> grantedText to colors.primary
        WebRewardedAdState.Dismissed -> stringResource(WebRes.string.web_ad_dismissed) to null
        WebRewardedAdState.Unavailable, WebRewardedAdState.Error -> stringResource(WebRes.string.web_ad_unavailable) to colors.error
        WebRewardedAdState.Cooldown -> stringResource(WebRes.string.web_ad_cooldown) to null
        else -> stringResource(WebRes.string.web_ad_offer) to null
    }
}

@Composable
private fun StoreCatalogRow(
    item: StoreItem,
    economyBinding: WebEconomyBinding,
    storeProcessor: WebStoreProcessor,
    onFeedback: (WebStoreFeedback) -> Unit,
) {
    val wallet =
        (economyBinding as? WebEconomyBinding.Ready)
            ?.repository
            ?.state
            ?.collectAsState()
            ?.value
    val livesFull = item.reward.type == StoreRewardType.LIFE_RESTORE && wallet?.let { it.lives >= EconomyPolicy.MAXIMUM_LIVES } == true
    // A purchase the balance cannot cover is shown as such instead of failing after the tap.
    val missingGems = wallet?.let { (item.priceGems - it.gems).coerceAtLeast(0) } ?: 0
    StoreItemRow(
        artwork = if (item.reward.type == StoreRewardType.LIFE_RESTORE) StoreArtwork.LIFE else StoreArtwork.forHints(item.reward.amount),
        title = item.webTitle(),
        subtitle =
            when {
                livesFull -> stringResource(WebRes.string.web_lives_full)
                missingGems > 0 -> pluralStringResource(WebRes.plurals.web_missing_gems, missingGems, missingGems)
                else -> item.webDescription()
            },
        subtitleColor =
            if (missingGems > 0 && !livesFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        // The price is the button: one tap spends exactly what it says.
        GemPriceButton(
            price = item.priceGems,
            enabled = !livesFull && missingGems == 0,
            onClick = { onFeedback(purchaseFeedback(storeProcessor, item, economyBinding)) },
        )
    }
}

/** The outcome of one internal purchase, turned into text in the current language when shown. */
internal data class WebStoreFeedback(
    val item: StoreItem,
    val result: PurchaseResult,
)

private fun purchaseFeedback(
    storeProcessor: WebStoreProcessor,
    item: StoreItem,
    economyBinding: WebEconomyBinding,
): WebStoreFeedback =
    WebStoreFeedback(item, storeProcessor.purchase(item, (economyBinding as? WebEconomyBinding.Ready)?.identity?.playerId))

@Composable
private fun WebStoreFeedback.text(): String =
    when (val outcome = result) {
        is PurchaseResult.Success ->
            when (item.reward.type) {
                StoreRewardType.HINTS ->
                    pluralStringResource(WebRes.plurals.web_purchase_done_hints, outcome.grantedAmount, outcome.grantedAmount)
                StoreRewardType.LIFE_RESTORE ->
                    pluralStringResource(WebRes.plurals.web_purchase_done_lives, outcome.grantedAmount, outcome.grantedAmount)
                StoreRewardType.GEMS ->
                    pluralStringResource(WebRes.plurals.web_gems_plus, outcome.grantedAmount, outcome.grantedAmount)
            }
        is PurchaseResult.Failure ->
            when (outcome.status) {
                PurchaseStatus.INSUFFICIENT_GEMS ->
                    stringResource(WebRes.string.web_purchase_insufficient, outcome.requiredGems, outcome.availableGems)
                else -> stringResource(WebRes.string.web_purchase_failed)
            }
    }

@Composable
private fun StoreItem.webTitle(): String =
    when (id) {
        WebStoreCatalog.ITEM_HINT_SINGLE -> stringResource(WebRes.string.web_item_hint)
        WebStoreCatalog.ITEM_HINT_PACK -> stringResource(WebRes.string.web_item_hint_pack)
        WebStoreCatalog.ITEM_LIFE_RESTORE -> stringResource(WebRes.string.web_item_life)
        else -> id
    }

@Composable
private fun StoreItem.webDescription(): String =
    when (reward.type) {
        StoreRewardType.HINTS -> pluralStringResource(WebRes.plurals.web_hints_plus, reward.amount, reward.amount)
        StoreRewardType.LIFE_RESTORE -> pluralStringResource(WebRes.plurals.web_lives_plus, reward.amount, reward.amount)
        StoreRewardType.GEMS -> pluralStringResource(WebRes.plurals.web_gems_plus, reward.amount, reward.amount)
    }

/** Shown when a hint is requested with an empty hint inventory. */
@Composable
internal fun WebHintsExhaustedDialog(
    onOpenStore: () -> Unit,
    onDismiss: () -> Unit,
) {
    PauseGameKeysWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StateArtworkImage(StateArtwork.NO_HINTS, size = STATE_ARTWORK_DIALOG_SIZE) },
        title = { Text(stringResource(WebRes.string.web_no_hints_title), textAlign = TextAlign.Center) },
        text = {
            Text(stringResource(WebRes.string.web_no_hints_body))
        },
        confirmButton = { TextButton(onClick = onOpenStore) { Text(stringResource(WebRes.string.web_to_store)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(WebRes.string.web_not_now)) } },
    )
}

/**
 * The Store opened from a running game: a sheet over the board inside the portrait host, so the
 * attempt underneath is kept exactly as it was. Tapping the dimmed board or «Вернуться к игре»
 * closes it.
 */
@Composable
internal fun WebStoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = STORE_SHEET_SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(STORE_SHEET_HEIGHT_FRACTION),
                shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                Column {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(stringResource(WebRes.string.web_back_to_game))
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}

private const val STORE_SHEET_SCRIM_ALPHA = 0.32f
private const val STORE_SHEET_HEIGHT_FRACTION = 0.92f
