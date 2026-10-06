# Nonogram picture library provenance

Candidate pictures for a future Catalog Nonogram generator (V3). Developer-only data: nothing in the
game reads it yet, and the files in `library-v1/` are generated, never edited by hand — change the
filters in `tools/nonogram/build_picture_library.py` and rerun it.

## Source

| Source | Version and pin | Licence |
|---|---|---|
| Phosphor Icons, "fill" weight — `@phosphor-icons/web` (the `Phosphor-Fill.ttf` glyphs) | npm `2.1.2`, tarball SHA-256 `40e3096099ca818c047979cece6a3764944d5200c67b64092f7bcd8bcd1d2b08` | MIT |
| Phosphor Icons metadata — `@phosphor-icons/core` (names, categories, codepoints) | npm `2.1.1`, tarball SHA-256 `313332be6190b724da24107addd781799b48bf76b13963f24501112ffe1baadd` | MIT |

Copyright (c) 2020 Phosphor Icons (MIT licence). The tool checks both SHA-256 values.

## Reproduce

    mkdir -p build/nonogram-sources
    curl -L -o build/nonogram-sources/web-2.1.2.tgz https://registry.npmjs.org/@phosphor-icons/web/-/web-2.1.2.tgz
    curl -L -o build/nonogram-sources/core-2.1.1.tgz https://registry.npmjs.org/@phosphor-icons/core/-/core-2.1.1.tgz
    python3 tools/nonogram/build_picture_library.py --sources build/nonogram-sources --sheets <preview dir>

Requires Pillow (any 10+). The output is deterministic.

## Rules

Each icon is drawn at 512 px, cropped to its ink, centred in a square, averaged down to the grid, and
thresholded at half coverage. A picture is kept only when:

- its Phosphor categories are picture-worthy (nature, objects, games, health, maps and travel,
  commerce, weather, media, people, finances, office) and none of arrows, brands, editor, development,
  design;
- its name is not an interface glyph or a variant (`-slash`, `-simple`, `-plus`, `square`, `circle`,
  `battery`, `wifi`, …) and not on the manual list (religious and gender symbols, hazards, trademarks
  and video formats, media controls, abstract shapes);
- 25–72 % of its cells are filled and it is not a framed shape (three edges almost full);
- line logic alone solves it (a Python port of `NonogramLineSolver`; `NonogramPictureLibraryTest`
  checks every picture again with the Kotlin solver);
- no kept picture of the same size equals it, its mirror, or differs from either in at most 4 % of cells.

Sizes: Easy 10×10, Medium 12×12, Hard and Expert 15×15. Hard and Expert split the 15×15 pictures in
half by line-logic effort (sweeps, then line updates).

## Files

`library-v1/<difficulty>.txt`: `<key>\t<rows, '/' between rows, '#' filled>\t<sweeps>`, alphabetical by
key. `library-v1/summary.json`: counts and solver effort per difficulty.
