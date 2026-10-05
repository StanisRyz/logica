# Fonts

`source/rubik_*.ttf` are the static 400/500/600/700 weights of
[Rubik](https://fonts.google.com/specimen/Rubik) (Google Fonts, v31, version 2.300), unchanged,
under the SIL Open Font License 1.1 in `Rubik-OFL.txt` (which permits subsetting).

The app ships subsets of them in `shared-ui/src/commonMain/composeResources/font/`, made by
`subset_rubik.py` (fontTools): every character of every `strings.xml` in all modules and languages,
the non-ASCII characters of the Kotlin sources, and Basic Latin, Latin-1, Latin Extended-A
(Turkish), Cyrillic, and common punctuation, with layout features and hinting kept. Rerun the script
after adding text in a new script or symbol instead of editing the outputs:

    pip install fonttools
    python3 tools/fonts/subset_rubik.py

`FontSubsetCoverageTest` in `:app` fails when a string uses a character the full font has but a
subset lacks.
