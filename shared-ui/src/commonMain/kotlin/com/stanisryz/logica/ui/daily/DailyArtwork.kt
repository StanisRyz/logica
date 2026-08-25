package com.stanisryz.logica.ui.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.game_2048
import com.stanisryz.logica.shared.ui.generated.resources.game_balance
import com.stanisryz.logica.shared.ui.generated.resources.game_crowns
import com.stanisryz.logica.shared.ui.generated.resources.game_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.game_word
import org.jetbrains.compose.resources.DrawableResource

/** Compact Daily-card artwork route, independent from the Catalog card presentation API. */
internal fun PuzzleType.dailyArtworkResource(): DrawableResource =
    when (this) {
        PuzzleType.BALANCE -> Res.drawable.game_balance
        PuzzleType.CROWNS -> Res.drawable.game_crowns
        PuzzleType.WORD -> Res.drawable.game_word
        PuzzleType.SUDOKU -> Res.drawable.game_sudoku
        PuzzleType.GAME_2048 -> Res.drawable.game_2048
        else -> error("$this has no Daily artwork.")
    }
