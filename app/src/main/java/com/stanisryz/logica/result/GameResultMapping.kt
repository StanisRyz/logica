package com.stanisryz.logica.result

import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelId
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelNumber
import com.stanisryz.logica.puzzle.core.catalog.CatalogLevelPackVersion
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
        outcome = outcome.name,
        attemptsUsed = attemptsUsed,
        challengeDate = challengeDate?.toString(),
        dailyPolicyVersion = dailyPolicyVersion?.value,
        catalogLevelNumber = catalogLevel?.levelNumber?.value,
        catalogLevelPackVersion = catalogLevel?.packVersion?.value,
    )

internal fun GameResultEntity.toGameResultOrNull(): GameResult? =
    runCatching {
        val scope = GameSessionScope.valueOf(sessionScope)
        val type = PuzzleType.valueOf(puzzleType)
        val puzzleDifficulty = Difficulty.valueOf(difficulty)
        GameResult(
            resultId = resultId.also { require(it.isNotBlank()) },
            puzzleType = type,
            difficulty = puzzleDifficulty,
            puzzleSeed = PuzzleSeed(puzzleSeed),
            generatorVersion = GeneratorVersion(generatorVersion),
            sessionScope = scope,
            hintsUsed = hintsUsed.also { require(it >= 0) },
            completedAt = Instant.ofEpochMilli(completedAtEpochMillis),
            outcome = GameOutcome.valueOf(outcome),
            attemptsUsed = attemptsUsed,
            challengeDate = challengeDate?.let(LocalDate::parse),
            dailyPolicyVersion = dailyPolicyVersion?.let(::DailyPolicyVersion),
            // Level metadata is diagnostic: a historical or partially written pair is simply dropped
            // rather than invalidating an otherwise valid durable result.
            catalogLevel =
                catalogLevelNumber
                    ?.takeIf { scope == GameSessionScope.CATALOG }
                    ?.let { level ->
                        catalogLevelPackVersion?.let { packVersion ->
                            runCatching {
                                CatalogLevelId(
                                    puzzleType = type,
                                    difficulty = puzzleDifficulty,
                                    levelNumber = CatalogLevelNumber(level),
                                    packVersion = CatalogLevelPackVersion(packVersion),
                                )
                            }.getOrNull()
                        }
                    },
        ).also { result ->
            require(
                (scope == GameSessionScope.DAILY) ==
                    (result.challengeDate != null && result.dailyPolicyVersion != null),
            )
        }
    }.getOrNull()
