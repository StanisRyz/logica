package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.daily.DailyArchive
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_archive
import com.stanisryz.logica.ui.daily.DailyArchiveDay
import com.stanisryz.logica.ui.daily.DailyArchiveDayContent
import com.stanisryz.logica.ui.daily.DailyArchiveList
import com.stanisryz.logica.ui.daily.DailyHubEntry
import com.stanisryz.logica.ui.daily.DailyHubEntryState
import com.stanisryz.logica.ui.daily.DailyRewardedAdState
import com.stanisryz.logica.web.generated.resources.web_back
import com.stanisryz.logica.web.generated.resources.web_to_games
import com.stanisryz.logica.web.generated.resources.web_to_profile
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

/** One past Daily day of the bound Player, as the archive shows it. */
internal data class WebArchiveDay(
    val date: DailyDate,
    val entries: List<DailyHubEntry>,
    /** Opened for gems or an ad, or started on its own date (a durable record exists). */
    val unlocked: Boolean,
) {
    val epochDay: Long get() = date.toDailyEpochDay()
    val completedCount: Int get() = entries.count { it.state == DailyHubEntryState.COMPLETED }
}

/**
 * The archive's days for the bound Player: the last [EconomyPolicy.DAILY_ARCHIVE_DAYS] days before
 * [today], newest first. A day keeps its persisted policy, else gets the newest one; it is open when
 * the rewards record lists it or when the player started it on its own date.
 */
internal fun webArchiveDays(
    snapshot: WebDailySnapshotV1,
    today: DailyDate,
    unlockedDays: Set<Long>,
): List<WebArchiveDay> =
    DailyArchive.dates(today, EconomyPolicy.DAILY_ARCHIVE_DAYS).map { date ->
        val record = snapshot.days[date]
        val definition = DailyArchive.definitionFor(date, record?.policyVersion)
        WebArchiveDay(
            date = date,
            entries =
                definition.entries.map { entry ->
                    val facts = record?.facts(entry.puzzleType) ?: WebDailyEntryFacts()
                    DailyHubEntry(
                        puzzleType = entry.puzzleType,
                        difficulty = entry.difficulty,
                        state =
                            when {
                                facts.solved -> DailyHubEntryState.COMPLETED
                                facts.failedSeen -> DailyHubEntryState.RETRY
                                else -> DailyHubEntryState.AVAILABLE
                            },
                    )
                },
            unlocked = record != null || date.toDailyEpochDay() in unlockedDays,
        )
    }

/**
 * The Daily archive page: the list of days, or one day with its entries. Opening a day is made
 * durable in the Player's rewards record first and paid only then — gems after the write, or a
 * rewarded ad that opens it only for the Player who started the ad. Starting an entry goes through
 * the host's ordinary Daily start with the day's date.
 */
@Composable
internal fun WebDailyArchiveRoute(
    playerSession: WebPlayerSessionController,
    today: DailyDate,
    selectedDay: Long?,
    onSelectDay: (Long?) -> Unit,
    origin: WebPageOrigin,
    onLeave: (WebPageOrigin) -> Unit,
    ad: WebRewardedPlacementController?,
    onStart: (PuzzleType, DailyDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val back = webArchiveBack(origin, selectedDay)
        WebTopBar(
            backLabel =
                stringResource(
                    when (back) {
                        WebArchiveBack.DAY_LIST -> WebRes.string.web_back
                        WebArchiveBack.GAME_HUB -> WebRes.string.web_to_games
                        WebArchiveBack.PROFILE_CALENDAR -> WebRes.string.web_to_profile
                    },
                ),
            onBack = { if (back == WebArchiveBack.DAY_LIST) onSelectDay(null) else onLeave(origin) },
            title = stringResource(Res.string.daily_archive),
        )
        WideReadableColumn(WIDE_ARCHIVE_MAX_WIDTH) {
            val binding = playerSession.dailyBinding.collectAsState().value as? WebDailyBinding.Ready ?: return@WideReadableColumn
            key(binding.token) {
                val snapshot =
                    binding.repository.snapshot
                        .collectAsState()
                        .value
                val progress =
                    (playerSession.progressBinding.collectAsState().value as? WebCatalogProgressBinding.Ready)
                        ?.takeIf { it.token == binding.token }
                        ?.repository
                val economy =
                    (playerSession.economyBinding.collectAsState().value as? WebEconomyBinding.Ready)
                        ?.takeIf { it.token == binding.token }
                        ?.repository
                val unlockedDays =
                    progress?.let {
                        key(it) {
                            it.rewards
                                .collectAsState()
                                .value.unlockedArchiveDays
                        }
                    } ?: emptySet()
                val days = webArchiveDays(snapshot, today, unlockedDays)
                val day = selectedDay?.let { selected -> days.firstOrNull { it.epochDay == selected } }
                if (day == null) {
                    DailyArchiveList(
                        days =
                            days.map {
                                DailyArchiveDay(
                                    it.epochDay,
                                    formatWebDailyDateLabel(it.date),
                                    it.completedCount,
                                    it.entries.size,
                                    it.unlocked,
                                )
                            },
                        onOpenDay = onSelectDay,
                    )
                } else {
                    val gems =
                        economy?.let {
                            key(it) {
                                it.state
                                    .collectAsState()
                                    .value.gems
                            }
                        } ?: 0
                    val adState = ad?.let { key(it) { it.state.collectAsState().value } }
                    DailyArchiveDayContent(
                        dateLabel = formatWebDailyDateLabel(day.date),
                        entries = day.entries,
                        unlocked = day.unlocked,
                        unlockPrice = if (day.unlocked) 0 else EconomyPolicy.DAILY_ARCHIVE_UNLOCK_GEMS,
                        gems = gems,
                        adState =
                            when (adState) {
                                null, WebRewardedAdState.Unavailable -> DailyRewardedAdState.UNAVAILABLE
                                WebRewardedAdState.Showing -> DailyRewardedAdState.LOADING
                                else -> DailyRewardedAdState.READY
                            },
                        gameplayAllowed = true,
                        onUnlockWithGems = {
                            if (progress != null &&
                                economy != null &&
                                economy.state.value.gems >= EconomyPolicy.DAILY_ARCHIVE_UNLOCK_GEMS
                            ) {
                                if (progress.claimDailyArchive(day.epochDay)) economy.spendGems(EconomyPolicy.DAILY_ARCHIVE_UNLOCK_GEMS)
                            }
                        },
                        onWatchAd = { if (progress != null) ad?.requestReward { progress.claimDailyArchive(day.epochDay) } },
                        onStart = { puzzleType -> onStart(puzzleType, day.date) },
                    )
                }
            }
        }
    }
}

/** The epoch day of [dayOfMonth] in [today]'s month, for a tap on the Profile calendar. */
internal fun webEpochDayInMonth(
    today: DailyDate,
    dayOfMonth: Int,
): Long = today.toDailyEpochDay() - today.getDayOfMonth() + dayOfMonth

private val WIDE_ARCHIVE_MAX_WIDTH = 720.dp
