# Nonogram picture library provenance

Candidate pictures for a future Catalog Nonogram generator (V3). Developer-only data: nothing in the
game reads it yet, and the files in `library-v1/` are generated, never edited by hand — change the
filters in `tools/nonogram/build_picture_library.py` and rerun it.

## Source

| Source | Version and pin | Licence |
|---|---|---|
| Phosphor Icons, "fill" weight — `@phosphor-icons/web` (the `Phosphor-Fill.ttf` glyphs) | npm `2.1.2`, tarball SHA-256 `40e3096099ca818c047979cece6a3764944d5200c67b64092f7bcd8bcd1d2b08` | MIT |
| Phosphor Icons metadata — `@phosphor-icons/core` (names, categories, codepoints) | npm `2.1.1`, tarball SHA-256 `313332be6190b724da24107addd781799b48bf76b13963f24501112ffe1baadd` | MIT |
| Material Symbols Outlined — `@material-symbols/font-400` (`material-symbols-outlined.woff2`, variable in FILL) | npm `0.47.6`, tarball SHA-256 `0637d16dc9d642abc8d6ed35cc1cc77fb683ae4ebafae06bbeef506c599e961d` | Apache-2.0 |

Copyright (c) 2020 Phosphor Icons (MIT licence). Material Symbols copyright Google LLC, Apache License
2.0 (the package ships the licence text). The tool checks all three SHA-256 values. Material icons
carry the key prefix `ms:`.

## Reproduce

    mkdir -p build/nonogram-sources
    curl -L -o build/nonogram-sources/web-2.1.2.tgz https://registry.npmjs.org/@phosphor-icons/web/-/web-2.1.2.tgz
    curl -L -o build/nonogram-sources/core-2.1.1.tgz https://registry.npmjs.org/@phosphor-icons/core/-/core-2.1.1.tgz
    curl -L -o build/nonogram-sources/font-400-0.47.6.tgz https://registry.npmjs.org/@material-symbols/font-400/-/font-400-0.47.6.tgz
    python3 -m pip install pillow fonttools==4.60.1 brotli==1.1.0
    python3 tools/nonogram/build_picture_library.py --sources build/nonogram-sources --sheets <preview dir>

Requires Pillow (any 10+), and fontTools with brotli to decode the WOFF2 font and instance it at
FILL=1. The output is deterministic.

## Rules

Each icon is drawn at 512 px, cropped to its ink, centred in a square, averaged down to the grid, and
thresholded at half coverage. A picture is kept only when:

- its Phosphor categories are picture-worthy (nature, objects, games, health, maps and travel,
  commerce, weather, media, people, finances, office) and none of arrows, brands, editor, development,
  design;
- its name is not an interface glyph or a variant (`-slash`, `-simple`, `-plus`, `square`, `circle`,
  `battery`, `wifi`, …) and not on the manual list (religious and gender symbols, hazards, trademarks
  and video formats, media controls, abstract shapes; since stage 10.2a also alcohol — `beer-bottle`,
  `beer-stein`, `brandy`, `champagne`, `cheers`, `martini`, `wine`, `pint-glass` — the tobacco `pipe`
  and the `poker-chip`);
- a Material Symbols icon is used only when its name is in the hand-picked `MATERIAL_PICTURES` list
  (objects, animals, food, transport, sports, places; ~210 names), and only after every Phosphor
  picture of that size, so it fills gaps rather than replacing anything;
- at 10×10 it is not a blob (stage 10.2a): it does not fall apart into more than one speck of at most
  two cells or into more than three parts, is not a plain mass (at least 80 % of its bounding box
  filled, edge length ≤ 1.35 × 4√area, no hole), and its edge is not ragged (edge length ≥ 2.6 ×
  4√area). Then a by-eye list (`MANUAL_UNREADABLE_10`) removes what still does not read;
- 25–72 % of its cells are filled and it is not a framed shape (three edges almost full);
- line logic alone solves it (a Python port of `NonogramLineSolver`; `NonogramPictureLibraryTest`
  checks every picture again with the Kotlin solver);
- no kept picture of the same size equals it, its mirror, or differs from either in at most 4 % of cells.

Sizes: Easy 10×10, Medium 12×12, Hard and Expert 15×15 (owner decision). Hard and Expert split the 15×15 pictures in
half by line-logic effort (sweeps, then line updates).

## Result

Pictures per difficulty: easy 160, medium 375, hard 194, expert 194. At 10×10 the blob rule left out 203 candidates
(PARTS 22, PLAIN_MASS 9, RAGGED 20, SPECKS 152) and the by-eye list 144 more; `summary.json` lists them.

## Files

`library-v1/<difficulty>.txt`: `<key>\t<rows, '/' between rows, '#' filled>\t<sweeps>`, alphabetical by
key. `library-v1/summary.json`: counts and solver effort per difficulty.
