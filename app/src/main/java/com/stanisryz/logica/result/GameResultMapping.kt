package com.stanisryz.logica.result

import com.stanisryz.logica.puzzle.core.daily.DailyPolicyVersion
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.GeneratorVersion
import com.stanisryz.logica.puzzle.core.model.PuzzleSeed
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.session.GameSessionScope
import java.time.Instant
import java.time.LocalDate

internal fun GameCompletion.toEntity(completedAtEpochMillis: Long): GameResultEntity =
    GameResultEntity(
        resultId = resultId,
        puzzleType = puzzleType.name,
        difficulty = difficulty.name,
        puzzleSeed = puzzleSeed.value,
        generatorVersion = generatorVersion.value,
        sessionScope = sessionScope.name,
        hintsUsed = hintsUsed,
        completedAtEpochMillis = completedAtEpochMillis,
        challengeDate = challengeDate?.toString(),
        dailyPolicyVersion = dailyPolicyVersion?.value,
    )

internal fun GameResultEntity.toGameResultOrNull(): GameResult? =
    runCatching {
        val scope = GameSessionScope.valueOf(sessionScope)
        GameResult(
            resultId = resultId.also { require(it.isNotBlank()) },
            puzzleType = PuzzleType.valueOf(puzzleType),
            difficulty = Difficulty.valueOf(difficulty),
            puzzleSeed = PuzzleSeed(puzzleSeed),
            generatorVersion = GeneratorVersion(generatorVersion),
            sessionScope = scope,
            hintsUsed = hintsUsed.also { require(it >= 0) },
            completedAt = Instant.ofEpochMilli(completedAtEpochMillis),
            challengeDate = challengeDate?.let(LocalDate::parse),
            dailyPolicyVersion = dailyPolicyVersion?.let(::DailyPolicyVersion),
        ).also { result ->
            require(
                (scope == GameSessionScope.DAILY) ==
                    (result.challengeDate != null && result.dailyPolicyVersion != null),
            )
        }
    }.getOrNull()
