package com.stanisryz.logica.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stanisryz.logica.platform.EconomyPolicy
import com.stanisryz.logica.puzzle.core.daily.DailyChallengePolicyResolver
import com.stanisryz.logica.puzzle.core.daily.DailyDate
import com.stanisryz.logica.puzzle.core.daily.toDailyEpochDay
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_start_error
import com.stanisryz.logica.ui.components.DailyRewardsCard
import com.stanisryz.logica.ui.components.dailyRewardsUiState
import com.stanisryz.logica.ui.daily.DailyHubResultRow
import com.stanisryz.logica.ui.daily.DailyHubSection
import com.stanisryz.logica.ui.daily.DailyHubUiState
import com.stanisryz.logica.ui.daily.DailyShareFormatter
import com.stanisryz.logica.ui.daily.DailyShareLanguage
import com.stanisryz.logica.ui.profile.AchievementRewards
import com.stanisryz.logica.ui.profile.DailyProfileMetrics
import com.stanisryz.logica.ui.profile.ProfileContent
import com.stanisryz.logica.ui.profile.ProfileEconomyMetrics
import com.stanisryz.logica.ui.profile.ProfilePage
import com.stanisryz.logica.ui.profile.ProfileStarSummary
import com.stanisryz.logica.ui.profile.ProfileStatistics
import com.stanisryz.logica.ui.profile.ProfileUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The day's login gift and quests for the bound Player. Quest counters and claims live beside
 * Catalog progress; Daily solves come from the durable Daily history. A claim is made durable
 * first and pays its gems only after that, so a repeated tap never pays twice.
 */
@Composable
internal fun WebDailyRewardsRoute(
    progressRepository: WebCatalogProgressRepository?,
    economyRepository: WebPlayerEconomyRepository?,
    playerSession: WebPlayerSessionController,
    currentDate: DailyDate,
) {
    val repository = progressRepository ?: return
    val economy = economyRepository ?: return
    val rewards by key(repository) { repository.rewards.collectAsState() }
    val dailyBinding by playerSession.dailyBinding.collectAsState()
    val dailySnapshot =
        (dailyBinding as? WebDailyBinding.Ready)?.let { ready ->
            key(ready.token) {
                ready.repository.snapshot
                    .collectAsState()
                    .value
            }
        }
    val today = currentDate.toDailyEpochDay()
    val dailySolved = dailySnapshot?.days?.get(currentDate)?.completedEntryCount ?: 0
    val state =
        dailyRewardsUiState(
            epochDay = today,
            activity = rewards.activity(today, dailySolved),
            claimedQuests = rewards.claimedQuests(today),
            // A clock moved back past the last claim shows the gift as claimed, which it is.
            lastGiftEpochDay = rewards.lastGiftDayOrNull?.let { minOf(it, today) },
            lastGiftStreakDay = rewards.giftStreakDay,
        )
    DailyRewardsCard(
        state = state,
        onClaimGift = { repository.claimLoginGift(today)?.let(economy::grantGems) },
        onClaimQuest = { index ->
            val quest = state.quests.firstOrNull { it.quest.index == index }
            if (quest != null && quest.complete && repository.claimQuest(today, index)) economy.grantGems(quest.quest.gems)
        },
    )
}

/**
 * Reactive Web Daily Hub presentation over the current Player-scoped binding. It performs no
 * cloud read and mutates nothing: the durable run is created only when gameplay actually starts.
 */
@Composable
internal fun WebDailyHubRoute(
    playerSession: WebPlayerSessionController,
    coordinator: WebDailyGameplayCoordinator,
    currentDate: DailyDate,
    onStartDaily: (PuzzleType) -> Unit,
) {
    val binding by playerSession.dailyBinding.collectAsState()
    when (val current = binding) {
        WebDailyBinding.Loading ->
            DailyHubSection(
                uiState = DailyHubUiState.Loading,
                gameplayAllowed = true,
                onStart = {},
            )
        is WebDailyBinding.Unavailable ->
            DailyHubSection(
                uiState = DailyHubUiState.Error(),
                gameplayAllowed = true,
                onStart = {},
                onRetryLoad = playerSession::retryCurrentContext,
            )
        is WebDailyBinding.Ready ->
            key(current.token) {
                val snapshot by current.repository.snapshot.collectAsState()
                val hubState =
                    if (coordinator.lastStartWasRejected) {
                        DailyHubUiState.Error(stringResource(Res.string.daily_start_error))
                    } else {
                        buildWebDailyHubUiState(snapshot, currentDate)
                    }
                // The Web share action exists only for a fully completed, still-current Daily day;
                // it is user-initiated from the shared completion card's optional callback.
                val uiStateWithShare =
                    if (hubState is DailyHubUiState.Content) {
                        val sharePayload =
                            webDailySharePayloadOrNull(
                                snapshot.days[currentDate],
                                currentDate,
                                hubState.streak.current,
                            )
                        val completionWithShare =
                            hubState.completion?.let { completion ->
                                val record = snapshot.days.getValue(currentDate)
                                if (sharePayload == null) {
                                    completion
                                } else {
                                    val definition =
                                        DailyChallengePolicyResolver.definitionFor(record.date, record.policyVersion)
                                    completion.copy(
                                        resultRows =
                                            definition.entries.map { entry ->
                                                DailyHubResultRow(
                                                    puzzleType = entry.puzzleType,
                                                    solved = true,
                                                    wordAttemptsUsed =
                                                        record.wordSolvedAttemptsUsed.takeIf { entry.puzzleType == PuzzleType.WORD },
                                                )
                                            },
                                        onShare = {
                                            WebDailyTextSharer.share(
                                                DailyShareFormatter.format(
                                                    sharePayload,
                                                    DailyShareLanguage.fromTag(currentWebAppLanguage.tag),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        if (completionWithShare != null) hubState.copy(completion = completionWithShare) else hubState
                    } else {
                        hubState
                    }
                DailyHubSection(
                    uiState = uiStateWithShare,
                    gameplayAllowed = true,
                    onStart = onStartDaily,
                    onRetryLoad = coordinator::clearStartRejection,
                )
            }
    }
}

@Composable
internal fun WebProfileRoute(
    playerSession: WebPlayerSessionController,
    leaderboard: WebLeaderboardController,
    binding: WebStatisticsBinding,
    currentDate: DailyDate,
    onRetry: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
    achievementRewards: AchievementRewards?,
) {
    when (binding) {
        WebStatisticsBinding.Loading ->
            ProfileContent(
                uiState = ProfileUiState.Loading,
                onRetry = onRetry,
            )
        is WebStatisticsBinding.Unavailable ->
            ProfileContent(
                uiState = ProfileUiState.Error,
                onRetry = onRetry,
            )
        is WebStatisticsBinding.Ready ->
            key(binding.token) {
                ProfileContent(
                    uiState = webProfileStatistics(playerSession, binding, currentDate).toUiState(),
                    onRetry = onRetry,
                    onOpenGames = onOpenGames,
                    onOpenPage = onOpenPage,
                    hasRatingPage = leaderboard.isSupported,
                    onOpenAchievements = onOpenAchievements,
                    achievementRewards = achievementRewards,
                )
            }
    }
}

/**
 * Achievement rewards for the bound Player: the paid ids live in the Player-scoped rewards record
 * beside Catalog progress, and a claim is made durable there before its gems are paid.
 */
@Composable
internal fun webAchievementRewards(
    progressRepository: WebCatalogProgressRepository?,
    economyRepository: WebPlayerEconomyRepository?,
): AchievementRewards? {
    val repository = progressRepository ?: return null
    val economy = economyRepository ?: return null
    val rewards by key(repository) { repository.rewards.collectAsState() }
    return remember(repository, economy, rewards.claimedAchievements) {
        AchievementRewards(rewards.claimedAchievements) { achievement ->
            if (repository.claimAchievement(achievement.id)) economy.grantGems(achievement.gems)
        }
    }
}

/** Everything the Profile shows for the bound Player, derived locally without any cloud read. */
@Composable
internal fun webProfileStatistics(
    playerSession: WebPlayerSessionController,
    binding: WebStatisticsBinding.Ready,
    currentDate: DailyDate,
): ProfileStatistics {
    val snapshot by binding.repository.snapshot.collectAsState()
    // Real Daily metrics come from the currently bound Player's Daily repository and update
    // locally after gameplay; opening Profile never triggers a cloud read.
    val dailyMetrics =
        webDailyProfileMetricsOrNull(
            dailyBinding = playerSession.dailyBinding.collectAsState().value,
            statisticsToken = binding.token,
            currentDate = currentDate,
        )
    val economyMetrics =
        webEconomyMetricsOrNull(
            economyBinding = playerSession.economyBinding.collectAsState().value,
            statisticsToken = binding.token,
        )
    return WebStatisticsAggregator
        .aggregate(snapshot)
        .toProfileStatistics()
        .copy(
            dailyMetrics = dailyMetrics,
            economy = economyMetrics,
            stars = ProfileStarSummary.from(LocalWebCatalogStars.current.levelRecords()),
        )
}

/** Daily metrics from the Daily repository bound to exactly this Player context, else absent. */
@Composable
private fun webDailyProfileMetricsOrNull(
    dailyBinding: WebDailyBinding,
    statisticsToken: WebPlayerContextToken,
    currentDate: DailyDate,
): DailyProfileMetrics? =
    when {
        dailyBinding is WebDailyBinding.Ready && dailyBinding.token == statisticsToken -> {
            val snapshot by dailyBinding.repository.snapshot.collectAsState()
            snapshot.dailyProfileMetrics(currentDate)
        }
        else -> null
    }

/** Wallet display from the economy repository bound to exactly this Player context, else absent. */
@Composable
private fun webEconomyMetricsOrNull(
    economyBinding: WebEconomyBinding,
    statisticsToken: WebPlayerContextToken,
): ProfileEconomyMetrics? =
    when {
        economyBinding is WebEconomyBinding.Ready && economyBinding.token == statisticsToken -> {
            val state by economyBinding.repository.state.collectAsState()
            state.let {
                ProfileEconomyMetrics(
                    gems = it.gems.toLong(),
                    lives = it.lives.toLong(),
                    maximumLives = EconomyPolicy.MAXIMUM_LIVES.toLong(),
                )
            }
        }
        else -> null
    }
