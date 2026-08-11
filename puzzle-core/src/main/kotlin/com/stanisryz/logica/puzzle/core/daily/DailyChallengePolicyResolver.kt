package com.stanisryz.logica.puzzle.core.daily

import java.time.LocalDate

object DailyChallengePolicyResolver {
    fun definitionFor(
        date: LocalDate,
        policyVersion: DailyPolicyVersion,
    ): DailyChallengeDefinition =
        when (policyVersion) {
            DailyChallengePolicyV1.VERSION -> DailyChallengePolicyV1.definitionFor(date)
            DailyChallengePolicyV2.VERSION -> DailyChallengePolicyV2.definitionFor(date)
            DailyChallengePolicyV3.VERSION -> DailyChallengePolicyV3.definitionFor(date)
            DailyChallengePolicyV4.VERSION -> DailyChallengePolicyV4.definitionFor(date)
            DailyChallengePolicyV5.VERSION -> DailyChallengePolicyV5.definitionFor(date)
            else -> error("Unsupported Daily policy version ${policyVersion.value}.")
        }

    /**
     * Whether one solved entry already qualifies its date for the streak. True from V5 on; V1–V4
     * keep the historical full-completion rule, so a partially solved historical day never becomes
     * streak-qualified retroactively. Full Daily completion stays a separate concept in both cases.
     */
    fun qualifiesStreakOnAnySolvedEntry(policyVersion: DailyPolicyVersion): Boolean =
        policyVersion.value >= DailyChallengePolicyV5.VERSION.value
}
