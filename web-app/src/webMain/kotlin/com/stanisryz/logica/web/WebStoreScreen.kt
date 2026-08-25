package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.platform.PurchaseResult
import com.stanisryz.logica.platform.PurchaseStatus
import com.stanisryz.logica.platform.StoreItem
import com.stanisryz.logica.platform.StoreRewardType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.profile_gems
import com.stanisryz.logica.shared.ui.generated.resources.profile_lives
import com.stanisryz.logica.ui.theme.LogicaSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * The minimal internal Store screen: gems balance, the static catalog, purchase buttons, and
 * result feedback. Purchases use internal gems only and go through [WebStoreProcessor]; there is
 * no payment, ad, or external billing integration anywhere on this path.
 */
@Composable
internal fun WebStoreScreen(
    playerSession: WebPlayerSessionController,
    storeProcessor: WebStoreProcessor,
    rewardedHintsController: WebStoreRewardedHintsController,
) {
    val economyBinding by playerSession.economyBinding.collectAsState()
    val storeBinding by playerSession.storeBinding.collectAsState()
    var feedback by remember { mutableStateOf<String?>(null) }

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
        Text(
            text = "Магазин",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        when (val economy = economyBinding) {
            is WebEconomyBinding.Ready -> {
                val state =
                    economy.repository.state
                        .collectAsState()
                        .value
                WalletCard(stringResource(Res.string.profile_gems), state.gems.toString())
                WalletCard(
                    stringResource(Res.string.profile_lives),
                    "${state.lives} / ${EconomyPolicy.MAXIMUM_LIVES}",
                )
            }
            else -> Text("Кошелёк недоступен", style = MaterialTheme.typography.bodyMedium)
        }
        if (storeBinding is WebStoreBinding.Ready) {
            val snapshot =
                (storeBinding as WebStoreBinding.Ready)
                    .repository.snapshot
                    .collectAsState()
                    .value
            Text(
                text = "Подсказки Судоку: ${snapshot.quantityOf(STORE_INVENTORY_HINTS)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RewardedHintsCard(rewardedHintsController, storeBinding)

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
private fun RewardedHintsCard(
    controller: WebStoreRewardedHintsController,
    storeBinding: WebStoreBinding,
) {
    val state by controller.state.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(LogicaSpacing.cardContent), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
            Text(text = "Бесплатные подсказки", style = MaterialTheme.typography.titleMedium)
            // The exchange is always disclosed explicitly: watching an advertisement is required.
            Text(
                text = "Смотреть рекламу → +3 подсказки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = controller::requestReward,
                enabled = controller.isRequestAllowed && storeBinding is WebStoreBinding.Ready,
            ) {
                Text(if (state == WebRewardedHintState.Showing) "Реклама показывается…" else "Смотреть рекламу")
            }
            when (state) {
                WebRewardedHintState.RewardGranted ->
                    Text(
                        "Реклама просмотрена: +3 подсказки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                WebRewardedHintState.Dismissed ->
                    Text(
                        "Награда не получена: реклама закрыта раньше времени.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                WebRewardedHintState.Unavailable, WebRewardedHintState.Error ->
                    Text(
                        "Реклама сейчас недоступна. Попробуйте позже.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                WebRewardedHintState.Cooldown ->
                    Text(
                        "Подождите немного перед следующей рекламой.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                else -> Unit
            }
        }
    }
}

@Composable
private fun StoreCatalogRow(
    item: StoreItem,
    economyBinding: WebEconomyBinding,
    storeProcessor: WebStoreProcessor,
    onFeedback: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(LogicaSpacing.cardContent).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicaSpacing.text)) {
                Text(text = item.webTitle(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.webDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${item.priceGems} кристаллов",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(onClick = { onFeedback(purchaseFeedback(storeProcessor, item, economyBinding)) }) {
                Text("Купить")
            }
        }
    }
}

@Composable
private fun WalletCard(
    title: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Row(
            modifier = Modifier.padding(LogicaSpacing.cardContent).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun purchaseFeedback(
    storeProcessor: WebStoreProcessor,
    item: StoreItem,
    economyBinding: WebEconomyBinding,
): String =
    when (val result = storeProcessor.purchase(item, (economyBinding as? WebEconomyBinding.Ready)?.identity?.playerId)) {
        is PurchaseResult.Success ->
            "Покупка выполнена: +${result.grantedAmount} ${item.reward.type.webGrantWord()}"
        is PurchaseResult.Failure ->
            when (result.status) {
                PurchaseStatus.INSUFFICIENT_GEMS ->
                    "Недостаточно кристаллов: нужно ${result.requiredGems}, есть ${result.availableGems}."
                else -> "Покупка не выполнена. Попробуйте ещё раз."
            }
    }

private fun StoreItem.webTitle(): String =
    when (id) {
        WebStoreCatalog.ITEM_HINT_PACK -> "Набор подсказок"
        WebStoreCatalog.ITEM_LIFE_RESTORE -> "Восстановление жизни"
        else -> id
    }

private fun StoreItem.webDescription(): String =
    when (id) {
        WebStoreCatalog.ITEM_HINT_PACK -> "+${reward.amount} подсказки для Судоку"
        WebStoreCatalog.ITEM_LIFE_RESTORE -> "+${reward.amount} жизнь"
        else -> ""
    }

private fun StoreRewardType.webGrantWord(): String =
    when (this) {
        StoreRewardType.HINTS -> "подсказки"
        StoreRewardType.LIFE_RESTORE -> "жизнь"
    }
