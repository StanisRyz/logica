# Word lexicon provenance

The repository contains no licensed Russian source corpus, so no external dictionary was imported,
scraped, or generated.

`answers_source.txt` and `guesses_source.txt` are a hand-written starter lexicon authored for this
project: common modern Russian words reviewed word by word against the curation rules stated at the
top of each file. They are original project content and carry the repository's own license.

`puzzle-core/src/main/resources/word/v1/` is generated from these sources by
`:puzzle-core:wordLexiconPrepare` and must never be edited by hand.

**Status: not frozen.** The starter lexicon is sized for implementation, tests, and the quality gate.
A larger V1 freeze requires either a properly licensed Russian corpus with attribution added here, or
further manual curation; until then `WordLexiconV1` is explicitly provisional.
