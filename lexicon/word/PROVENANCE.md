# Word lexicon provenance

The repository contains no licensed Russian source corpus, so no external dictionary was imported,
scraped, or generated.

`answers_source.txt` and `guesses_source.txt` are a hand-written lexicon authored for this project:
common modern Russian words reviewed word by word against the curation rules stated at the top of
each file. They are original project content and carry the repository's own license.

`puzzle-core/src/main/resources/word/v1/` is generated from these sources by
`:puzzle-core:wordLexiconPrepare` and must never be edited by hand.

**Status: frozen (Stage 27).** `WordLexiconV1` is the compatibility baseline for `WordGeneratorV1`.
Its answer contents and ordering fix the `(difficulty, seed, generatorVersion = 1)` mapping; changing
them requires a new generator version, not an edit here. Regenerating after any source change and
re-running `:puzzle-core:wordQualityCheck` is mandatory.
