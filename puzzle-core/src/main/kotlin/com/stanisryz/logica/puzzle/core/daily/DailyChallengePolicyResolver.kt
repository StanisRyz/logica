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
            else -> error("Unsupported Daily policy version ${policyVersion.value}.")
        }
}
