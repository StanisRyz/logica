package com.stanisryz.logica.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PaymentProductSnapshot
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.StoreItem
import com.stanisryz.logica.platform.StoreRewardType
import com.stanisryz.logica.ui.components.GemPriceButton
import com.stanisryz.logica.ui.components.StoreBalanceCard
import com.stanisryz.logica.ui.components.StoreItemRow
import com.stanisryz.logica.ui.components.StoreSectionTitle
import com.stanisryz.logica.ui.theme.LogicaSpacing

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
    var feedback by remember { mutableStateOf<String?>(null) }

    // Real-money catalog loads once per visit; standalone/unsupported hides the section.
    LaunchedEffect(paymentsCoordinator) { paymentsCoordinator.refreshCatalog() }
    val paidCatalog by paymentsCoordinator.catalogState.collectAsState()
    val purchaseState by paymentsCoordinator.purchaseState.collectAsState()

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
                    footnote = state.nextLifeRestoreAtEpochMs?.let { "Новая жизнь через ${formatLifeCountdown(it - now)}" },
                )
            }
            else -> Text("Кошелёк недоступен", style = MaterialTheme.typography.bodyMedium)
        }

        RewardedAdRow(
            controller = rewardedAds.hints,
            title = "+3 подсказки",
            grantedText = "Реклама просмотрена: +3 подсказки.",
            enabled = storeBinding is WebStoreBinding.Ready,
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
                title = "+1 жизнь",
                grantedText = "Реклама просмотрена: +1 жизнь.",
                enabled = true,
                icon = Icons.Filled.Favorite,
            )
        }

        // Real-money gem top-up (Yandex Payments): price/currency come from the Yandex catalog.
        if (paidCatalog is WebPaidCatalogState.Ready) {
            val entries = (paidCatalog as WebPaidCatalogState.Ready).entries
            StoreSectionTitle("Кристаллы")
            entries.forEach { entry -> PaidGemTopUpCard(entry, purchaseState, paymentsCoordinator) }
        }

        StoreSectionTitle("За кристаллы")
        WebStoreCatalog.ITEMS.forEach { item -> StoreCatalogRow(item, economyBinding, storeProcessor, { feedback = it }) }

        feedback?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
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
    coordinator: WebPaymentsCoordinator,
) {
    val message = paidPurchaseMessage(state)
    StoreItemRow(
        icon = Icons.Filled.Diamond,
        title = "+${entry.product.gemReward} ${gemsWord(entry.product.gemReward)}",
        subtitle = message ?: entry.details.description ?: "Пополнение кристаллов",
        subtitleColor =
            if (state == WebPaidPurchaseState.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        // Portal-supplied price + currency only; never a locally manufactured amount.
        Button(onClick = coordinator::purchaseGemsSmall, enabled = !state.isBusy) {
            Text(paidPriceLabel(entry.details))
        }
    }
}

private val WebPaidPurchaseState.isBusy: Boolean
    get() =
        this == WebPaidPurchaseState.Purchasing ||
            this == WebPaidPurchaseState.Fulfilling ||
            this == WebPaidPurchaseState.Saving

/** Price exactly as the Yandex catalog supplies it; currency icon rendering stays host-side. */
private fun paidPriceLabel(details: PaymentProductSnapshot): String =
    buildString {
        append(details.price ?: details.priceValue ?: "")
        details.priceCurrencyCode?.let { code ->
            if (isNotEmpty()) append(' ')
            append(code)
        }
    }.ifEmpty { "—" }

@Composable
private fun paidPurchaseMessage(state: WebPaidPurchaseState): String? =
    when (state) {
        WebPaidPurchaseState.Purchasing -> "Открывается оплата…"
        WebPaidPurchaseState.Fulfilling -> "Начисляем кристаллы…"
        WebPaidPurchaseState.Saving -> "Сохраняем покупку…"
        WebPaidPurchaseState.Success -> "Покупка завершена: +100 кристаллов."
        WebPaidPurchaseState.Cancelled -> "Оплата отменена."
        WebPaidPurchaseState.Unavailable -> "Оплата сейчас недоступна."
        WebPaidPurchaseState.CloudPending -> "Покупка сохранена и будет завершена автоматически."
        WebPaidPurchaseState.Error -> "Не удалось завершить покупку. Попробуйте ещё раз."
        WebPaidPurchaseState.Idle -> null
    }

/** One rewarded placement row; the exchange is always disclosed: watching an advertisement is required. */
@Composable
internal fun RewardedAdRow(
    controller: WebRewardedPlacementController,
    title: String,
    grantedText: String,
    enabled: Boolean,
    icon: ImageVector = Icons.Filled.PlayCircle,
) {
    val state by controller.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val (subtitle, subtitleColor) = rewardedAdSubtitle(state, grantedText)
    StoreItemRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        subtitleColor = subtitleColor ?: colors.onSurfaceVariant,
        highlighted = true,
    ) {
        Button(
            onClick = controller::requestReward,
            enabled = controller.isRequestAllowed && enabled,
        ) {
            Text(if (state == WebRewardedAdState.Showing) "Идёт…" else "Смотреть")
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
        WebRewardedAdState.Dismissed -> "Награда не получена: реклама закрыта раньше времени." to null
        WebRewardedAdState.Unavailable, WebRewardedAdState.Error -> "Реклама сейчас недоступна. Попробуйте позже." to colors.error
        WebRewardedAdState.Cooldown -> "Подождите немного перед следующей рекламой." to null
        else -> "За просмотр короткой рекламы" to null
    }
}

@Composable
private fun StoreCatalogRow(
    item: StoreItem,
    economyBinding: WebEconomyBinding,
    storeProcessor: WebStoreProcessor,
    onFeedback: (String) -> Unit,
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
        icon = if (item.reward.type == StoreRewardType.LIFE_RESTORE) Icons.Filled.Favorite else Icons.Filled.Lightbulb,
        title = item.webTitle(),
        subtitle =
            when {
                livesFull -> "Жизни уже полные"
                missingGems > 0 -> "Не хватает $missingGems ${russianPlural(missingGems, "кристалла", "кристаллов", "кристаллов")}"
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

private fun purchaseFeedback(
    storeProcessor: WebStoreProcessor,
    item: StoreItem,
    economyBinding: WebEconomyBinding,
): String =
    when (val result = storeProcessor.purchase(item, (economyBinding as? WebEconomyBinding.Ready)?.identity?.playerId)) {
        is PurchaseResult.Success ->
            "Покупка выполнена: +${result.grantedAmount} ${item.reward.type.webGrantWord(result.grantedAmount)}"
        is PurchaseResult.Failure ->
            when (result.status) {
                PurchaseStatus.INSUFFICIENT_GEMS ->
                    "Недостаточно кристаллов: нужно ${result.requiredGems}, есть ${result.availableGems}."
                else -> "Покупка не выполнена. Попробуйте ещё раз."
            }
    }

private fun StoreItem.webTitle(): String =
    when (id) {
        WebStoreCatalog.ITEM_HINT_SINGLE -> "Подсказка"
        WebStoreCatalog.ITEM_HINT_PACK -> "Набор подсказок"
        WebStoreCatalog.ITEM_LIFE_RESTORE -> "Восстановление жизни"
        else -> id
    }

private fun StoreItem.webDescription(): String =
    when (id) {
        WebStoreCatalog.ITEM_HINT_SINGLE, WebStoreCatalog.ITEM_HINT_PACK ->
            "+${reward.amount} ${russianPlural(reward.amount, "подсказка", "подсказки", "подсказок")}"
        WebStoreCatalog.ITEM_LIFE_RESTORE -> "+${reward.amount} ${russianPlural(reward.amount, "жизнь", "жизни", "жизней")}"
        else -> ""
    }

/** Russian noun form for [count]: one / few / many ("1 подсказка", "3 подсказки", "5 подсказок"). */
private fun gemsWord(count: Int): String = russianPlural(count, "кристалл", "кристалла", "кристаллов")

internal fun russianPlural(
    count: Int,
    one: String,
    few: String,
    many: String,
): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}

private fun StoreRewardType.webGrantWord(amount: Int): String =
    when (this) {
        StoreRewardType.HINTS -> russianPlural(amount, "подсказка", "подсказки", "подсказок")
        StoreRewardType.LIFE_RESTORE -> russianPlural(amount, "жизнь", "жизни", "жизней")
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
        title = { Text("Подсказки закончились") },
        text = {
            Text(
                "Подсказки можно купить в магазине за кристаллы или получить бесплатно за просмотр рекламы. " +
                    "Магазин откроется поверх игры — партия останется на месте.",
            )
        },
        confirmButton = { TextButton(onClick = onOpenStore) { Text("В магазин") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Не сейчас") } },
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
                        Text("Вернуться к игре")
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}

private const val STORE_SHEET_SCRIM_ALPHA = 0.32f
private const val STORE_SHEET_HEIGHT_FRACTION = 0.92f
