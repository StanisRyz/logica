# Fonts

`source/rubik_*.ttf` are the static 400/500/600/700 weights of
[Rubik](https://fonts.google.com/specimen/Rubik) (Google Fonts, v31, version 2.300), unchanged,
under the SIL Open Font License 1.1 in `Rubik-OFL.txt` (which permits subsetting).

The app ships subsets of them in `shared-ui/src/commonMain/composeResources/font/`, made by
`subset_rubik.py` (fontTools): every character of every `strings.xml` in all modules and languages,
the non-ASCII characters of the Kotlin sources, every Latin and Cyrillic character the source font
has (U+0000–024F, U+1E00–1EFF, U+0400–052F: player names in the Yandex leaderboards come from outside
and may use Kazakh, Uzbek, or other letters), and common punctuation, with layout features and hinting
kept. Only scripts no interface text or player name of the audience uses (now Hebrew) are left out. Rerun the script
after adding text in a new script or symbol instead of editing the outputs:

    pip install fonttools
    python3 tools/fonts/subset_rubik.py

`FontSubsetCoverageTest` in `:app` fails when a string uses a character the full font has but a
subset lacks, or when a subset lacks any Latin or Cyrillic character of the full font.
