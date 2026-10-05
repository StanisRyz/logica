package com.stanisryz.logica.web

import com.stanisryz.logica.puzzle.core.model.PuzzleType

/**
 * The terminal step every game shares: the Statistics result first, then the Catalog or Daily save,
 * then the wallet. Daily never advances Catalog progression, and only a Catalog solve saves progress.
 */
internal class WebTerminalRecorder(
    private val puzzleType: PuzzleType,
    private val statistics: WebGameplayStatistics,
    private val economy: WebGameplayEconomy,
    private val completion: WebCatalogCompletionController,
    private val dailyCompletion: WebDailyCompletionController,
) {
    fun record(
        source: WebGameplaySource,
        statisticsAttempt: WebStatisticsAttempt?,
        solved: Boolean,
        stars: Int? = null,
        hintsUsed: Int = 0,
        wordAttemptsUsed: Int? = null,
    ) {
        val outcome = if (solved) WebStatisticsTerminalOutcome.SOLVED else WebStatisticsTerminalOutcome.FAILED
        statisticsAttempt?.let { statistics.recordTerminalResult(it, outcome, hintsUsed, wordAttemptsUsed) }
        when (source) {
            is WebGameplaySource.CatalogLevel -> {
                if (solved) completion.saveSolved(source.attempt, stars)
                // A solve pays its earned gems (a replay only by raising an Expert level to three stars),
                // a failure — replay or not — one life.
                economy.recordTerminalResult(solved = solved, gemsEarned = if (solved) completion.gemsEarned else 0)
            }
            is WebGameplaySource.DailyChallenge -> {
                dailyCompletion.saveTerminal(source.attempt, outcome, wordAttemptsUsed)
                // A Daily result feeds the wallet like a Catalog one: a failure costs a life.
                economy.recordTerminalResult(
                    solved = solved,
                    gemsEarned = if (solved) WebEconomyProcessor.dailyGemsFor(puzzleType, source.difficulty, stars) else 0,
                )
            }
        }
    }
}
