package com.stanisryz.logica.ui.daily

import com.stanisryz.logica.puzzle.core.model.PuzzleType
import com.stanisryz.logica.shared.ui.generated.resources.Res
import com.stanisryz.logica.shared.ui.generated.resources.daily_2048
import com.stanisryz.logica.shared.ui.generated.resources.daily_balance
import com.stanisryz.logica.shared.ui.generated.resources.daily_crowns
import com.stanisryz.logica.shared.ui.generated.resources.daily_sudoku
import com.stanisryz.logica.shared.ui.generated.resources.daily_word
import org.jetbrains.compose.resources.DrawableResource

/** Compact Daily-card artwork route, independent from the Catalog resources and presentation API. */
internal fun PuzzleType.dailyArtworkResource(): DrawableResource =
    when (this) {
        PuzzleType.BALANCE -> Res.drawable.daily_balance
        PuzzleType.CROWNS -> Res.drawable.daily_crowns
        PuzzleType.WORD -> Res.drawable.daily_word
        PuzzleType.SUDOKU -> Res.drawable.daily_sudoku
        PuzzleType.GAME_2048 -> Res.drawable.daily_2048
        else -> error("$this has no Daily artwork.")
    }
