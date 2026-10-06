package com.stanisryz.logica.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stanisryz.logica.puzzle.core.model.Difficulty
import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.how_to_play
import com.stanisryz.logica.ui.components.DifficultySelector
import com.stanisryz.logica.ui.components.LevelMapSheet
import com.stanisryz.logica.ui.components.catalogTitleResource
import com.stanisryz.logica.ui.rating.DifficultyScreenActions
import com.stanisryz.logica.ui.rating.GameRatingSheet
import com.stanisryz.logica.ui.theme.LogicaSpacing
import com.stanisryz.logica.ui.tutorial.FirstPlayTutorialDialog
import com.stanisryz.logica.web.generated.resources.web_to_games
import org.jetbrains.compose.resources.stringResource
import com.stanisryz.logica.web.generated.resources.Res as WebRes

@Composable
internal fun DifficultyContent(
    puzzleType: PuzzleType,
    onBack: () -> Unit,
    onStart: (Difficulty) -> Unit,
    gallery: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onReplay: ((Difficulty, Int) -> Unit)? = null,
) {
    val ratingUi = LocalWebRating.current
    var levelsOpen by remember { mutableStateOf(false) }
    var ratingOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    if (ratingOpen) {
        GameRatingSheet(
            puzzleType = puzzleType,
            rating = ratingUi.rating(puzzleType),
            onDismiss = { ratingOpen = false },
            leaderboard = ratingUi.leaderboard?.let { table -> { table(puzzleType) } },
        )
    }
    if (galleryOpen) gallery?.invoke { galleryOpen = false }
    val stars = LocalWebCatalogStars.current
    val livesGuard = LocalWebLives.current
    if (levelsOpen && onReplay != null) {
        LevelMapSheet(
            currentLevels = ratingUi.progress.clearedLevels(puzzleType).mapValues { it.value + 1 },
            starsOf = { difficulty, level ->
                stars.starsOf(ratingUi.progress.bucketForLevel(puzzleType, difficulty, level), level)
            },
            onPlayCurrent = { difficulty ->
                levelsOpen = false
                livesGuard.guard {
                    WebLastPlayed.record(puzzleType, difficulty)
                    onStart(difficulty)
                }
            },
            onReplay = { difficulty, level ->
                levelsOpen = false
                livesGuard.guard { onReplay(difficulty, level) }
            },
            onDismiss = { levelsOpen = false },
        )
    }
    val showTutorial = LocalOpenTutorial.current
    val openTutorial = {
        WebTutorialOffers.markOffered(puzzleType)
        showTutorial(puzzleType)
    }
    val lives = LocalWebLives.current
    // The first difficulty tap in a game offers its tutorial once; either answer settles it.
    var offeredDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    offeredDifficulty?.let { difficulty ->
        FirstPlayTutorialDialog(
            puzzleType = puzzleType,
            onOpenTutorial = {
                offeredDifficulty = null
                openTutorial()
            },
            onPlay = {
                offeredDifficulty = null
                WebTutorialOffers.markOffered(puzzleType)
                lives.guard {
                    WebLastPlayed.record(puzzleType, difficulty)
                    onStart(difficulty)
                }
            },
            onDismiss = { offeredDifficulty = null },
        )
    }
    Column(Modifier.fillMaxSize()) {
        // The same bar as gameplay: the way back, the game in the middle, and the wallet.
        WebTopBar(
            backLabel = stringResource(WebRes.string.web_to_games),
            onBack = onBack,
            title = stringResource(puzzleType.catalogTitleResource()),
        )
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = LogicaSpacing.screenHorizontal,
                        vertical = LogicaSpacing.screenVertical,
                    ),
        ) {
            // The wallet chip in the header already shows the lives; the line only adds the countdown.
            val livesState = lives.state?.takeIf { it.nextLifeRestoreAtEpochMs != null }
            val livesHeight = if (livesState != null) LIVES_STATUS_HEIGHT + LogicaSpacing.section else 0.dp
            // The wide host shows the four difficulties as a 2x2 grid of taller cards.
            val columns = if (LocalWebWideLayout.current) 2 else 1
            val rows = 4 / columns
            val cardHeight =
                (
                    (
                        maxHeight - TUTORIAL_ACTION_HEIGHT - livesHeight -
                            LogicaSpacing.section - LogicaSpacing.item * (rows - 1)
                    ) / rows
                ).coerceIn(MIN_DIFFICULTY_CARD_HEIGHT, if (columns > 1) MAX_WIDE_DIFFICULTY_CARD_HEIGHT else MAX_DIFFICULTY_CARD_HEIGHT)
            Column(verticalArrangement = Arrangement.spacedBy(LogicaSpacing.section)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(TUTORIAL_ACTION_HEIGHT),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DifficultyScreenActions(
                        howToPlayLabel = stringResource(Res.string.how_to_play),
                        onHowToPlay = openTutorial,
                        onRating = { ratingOpen = true },
                        onGallery = gallery?.let { { galleryOpen = true } },
                        // The gallery is the Nonogram's way back into its cleared levels.
                        onLevels = onReplay?.takeIf { gallery == null }?.let { { levelsOpen = true } },
                    )
                }
                if (livesState != null) {
                    WebLivesStatus(livesState, Modifier.height(LIVES_STATUS_HEIGHT))
                }
                DifficultySelector(
                    onStart = { difficulty ->
                        if (WebTutorialOffers.isPending(puzzleType)) {
                            offeredDifficulty = difficulty
                        } else {
                            lives.guard {
                                WebLastPlayed.record(puzzleType, difficulty)
                                onStart(difficulty)
                            }
                        }
                    },
                    enabled = true,
                    cardHeight = cardHeight,
                    columns = columns,
                    stars = LocalWebCatalogStars.current.starsByDifficulty(puzzleType),
                )
            }
        }
    }
}

private val LIVES_STATUS_HEIGHT = 24.dp

private val TUTORIAL_ACTION_HEIGHT = 40.dp

private val MIN_DIFFICULTY_CARD_HEIGHT = 96.dp

private val MAX_DIFFICULTY_CARD_HEIGHT = 152.dp

private val MAX_WIDE_DIFFICULTY_CARD_HEIGHT = 260.dp
