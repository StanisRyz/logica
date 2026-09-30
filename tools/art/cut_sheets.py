"""Cuts the achievement icon sheets in art/achievements into the shared drawables.

Each sheet is a grid of icons on a plain white background. Rows and then the icons inside each row
are found from the empty white bands between them; the background reachable from a cell's border
becomes transparent (with a soft, un-whitened edge), and every icon is fitted into the same square
with the same margin. Rerun after replacing a sheet: python3 tools/achievements/cut_achievement_icons.py
Needs Pillow, NumPy, and SciPy.
"""

from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
# (sheet under art/, drawable name prefix, names row by row; None skips a picture nothing uses yet)
SHEETS = [
    ("achievements/sheet1.png", "achievement", [
        ["first_solve", "all_games", "solver_50"],
        ["solver_250", "solver_1000", "balance"],
        ["crowns", "sudoku", "word"],
        ["game_2048", "nonogram", "block_sudoku"],
    ]),
    ("achievements/sheet2.png", "achievement", [
        ["word_first_try", "expert_1", "expert_25", "stars_100", "stars_500"],
        ["perfect_25", "daily_1", "daily_30", "streak_7", "streak_30"],
    ]),
    ("store/sheet.png", "store", [
        ["gems_50", "gems_150", "gems_500", "starter_pack"],
        ["no_ads", "hint_single", "hint_pack", "life"],
        ["ad_gem", "ad_life", None],
    ]),
]
OUTPUT = ROOT / "shared-ui/src/commonMain/composeResources/drawable"
SIZE = 192          # 48 dp at xxxhdpi, the largest the game shows
NOISE = 16          # on a transparent sheet, alpha below this is generator noise, not drawing
MARGIN = 0.06       # empty border around every icon, as a share of SIZE
INK = 14            # a channel this far from white counts as drawing, not background
EDGE = 60           # up to this far from white an edge pixel is partly transparent
HOLE = 800          # an enclosed flat-white area at least this large (sheet pixels) is background too


def bands(ink: np.ndarray, count: int) -> list[tuple[int, int]]:
    """The [start, end) runs of an ink profile, merged down to the expected number of objects."""
    runs, start = [], None
    for i, value in enumerate(ink > 2):
        if value and start is None:
            start = i
        elif not value and start is not None:
            runs.append((start, i))
            start = None
    if start is not None:
        runs.append((start, len(ink)))
    runs = [r for r in runs if r[1] - r[0] > 6]  # stray specks
    while len(runs) > count:  # join the pair with the smallest gap
        gaps = [runs[i + 1][0] - runs[i][1] for i in range(len(runs) - 1)]
        i = int(np.argmin(gaps))
        runs[i:i + 2] = [(runs[i][0], runs[i + 1][1])]
    if len(runs) != count:
        raise SystemExit(f"expected {count} objects, found {len(runs)}")
    return runs


def cut(cell: np.ndarray) -> Image.Image:
    distance = np.abs(cell.astype(int) - 255).max(axis=2)
    near_white = distance < INK
    labels, _ = ndimage.label(near_white)
    border = set(np.unique(np.concatenate([labels[0], labels[-1], labels[:, 0], labels[:, -1]]))) - {0}
    background = np.isin(labels, list(border))
    # Background also shows through gaps the drawing closes on every side (inside a trophy's handles,
    # between chains or blocks). Those are large and flat white; highlights on the drawing are small
    # or tinted, so they stay.
    for label in set(range(1, labels.max() + 1)) - border:
        area = labels == label
        if area.sum() >= HOLE and distance[area].mean() < 3.5 and (distance[area] < 5).mean() >= 0.7:
            background |= area
    # Pixels next to the background fade out by how white they are, so no white fringe remains.
    rim = ndimage.binary_dilation(background, iterations=2) & ~background
    alpha = np.where(background, 0.0, 1.0)
    alpha[rim] = np.clip(distance[rim] / EDGE, 0.0, 1.0)
    rgb = cell.astype(float)
    safe = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.where(alpha[..., None] > 0, (rgb - 255 * (1 - alpha[..., None])) / safe, 0)
    rgba = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)
    image = Image.fromarray(rgba, "RGBA")
    return image.crop(image.getbbox())


def trim(cell: np.ndarray) -> Image.Image:
    """A transparent sheet's cell without its alpha noise, cropped to the drawing."""
    rgba = cell.copy()
    rgba[rgba[..., 3] < NOISE] = 0
    image = Image.fromarray(rgba, "RGBA")
    return image.crop(image.getbbox())


def fit(icon: Image.Image) -> Image.Image:
    inner = round(SIZE * (1 - 2 * MARGIN))
    scale = inner / max(icon.size)
    icon = icon.resize((max(1, round(icon.width * scale)), max(1, round(icon.height * scale))), Image.LANCZOS)
    square = Image.new("RGBA", (SIZE, SIZE))
    square.alpha_composite(icon, ((SIZE - icon.width) // 2, (SIZE - icon.height) // 2))
    return square


def main() -> None:
    for sheet, prefix, grid in SHEETS:
        image = Image.open(ROOT / "art" / sheet)
        transparent = image.mode == "RGBA" and (np.asarray(image)[..., 3] == 0).mean() > 0.2
        pixels = np.asarray(image.convert("RGBA" if transparent else "RGB"))
        if transparent:
            drawn = pixels[..., 3] >= NOISE
        else:
            drawn = np.abs(pixels.astype(int) - 255).max(axis=2) >= INK
        for (top, bottom), names in zip(bands(drawn.sum(axis=1), len(grid)), grid):
            for (left, right), name in zip(bands(drawn[top:bottom].sum(axis=0), len(names)), names):
                if name is None:
                    continue
                pad = 4
                cell = pixels[max(0, top - pad):bottom + pad, max(0, left - pad):right + pad]
                picture = trim(cell) if transparent else cut(cell)
                fit(picture).save(OUTPUT / f"{prefix}_{name}.webp", "WEBP", quality=90, method=6)
                print(f"{prefix}_{name}.webp")


if __name__ == "__main__":
    main()
