# Word lexicon provenance

## Frozen V1

`answers_source.txt` and `guesses_source.txt` are the original project-authored, manually reviewed
five-letter sources. `puzzle-core/src/commonMain/resources/word/v1/` remains frozen and is generated only by
`:puzzle-core:wordLexiconPrepare`; no Stage 27.1 change was made to either V1 resource.

Frozen resource SHA-256 values:

- `allowed_guesses.txt`: `60ED00AC0E1E3F5522FABBC98E7A7C15F080D6BF89B9BFF75038B355E0FB5E47`
- `answers.txt`: `9B32435FBCE8B4407116A7A67C7ADFD06952CFFFDE6E6BC022785DBCF2A74CAA`

## V2 morphology data

`tools/word-lexicon/extract_pymorphy3.py` enumerates locally installed, dictionary-known entries and
does not download data or use prediction to invent corpus forms. It ranks only the answer candidates
with a separate, locally installed Russian frequency dataset. The pinned inputs are:

- `pymorphy3==2.0.6`
- `pymorphy3-dicts-ru==2.4.417150.4580142`
- `wordfreq==3.1.1`
- dictionary format `2.4`, OpenCorpora source `0.92`, source revision `417150`, corpus revision
  `4580142`, compiled `2022-01-08T22:09:24.565962`

The pymorphy package code is MIT-licensed, and `wordfreq` code is Apache-2.0. The OpenCorpora-derived
dictionary data is licensed under CC BY-SA 3.0. The `wordfreq` data combines sources including Google
Books Ngrams, Leeds Internet Corpus, Wikipedia, ParaCrawl, OPUS OpenSubtitles, SUBTLEX, and other
documented corpora, and is redistributable with attribution under CC BY-SA 4.0. These data licenses
and ShareAlike terms apply separately from the repository's source-code license.

The deterministic transformations are noun-only normal-form extraction, proper-name and abbreviation
filtering, plural-only preservation from morphology tags, lower-casing, `Ё -> Е`, Russian-Cyrillic and
4/5/6/7-letter filtering, normalization-collision detection, primary noun-interpretation validation,
stable Russian Zipf-frequency ranking, and final project allow/block overrides. Pymorphy parse scores
are used only to reject candidates whose primary interpretation is not a noun, never as frequency.
Runtime Android code contains only generated text resources and has no Python dependency.

`lexicon/word/v2/generated/metadata.json` records exact versions, dictionary metadata, counts, and
filtering totals. `answer_candidates.tsv` is the practical answer review artifact, and
`rejection_diagnostics.tsv` records rejected playable-length dictionary lemmas. V2 now contains 500
EASY, 505 MEDIUM, 500 HARD, and 500 EXPERT answers: 505 retain their V1 review status, 74 are manual
V2 additions, and 1,426 are morphology-filtered frequency selections. The 1,500 non-V1 candidates
remain explicit in the review artifact until final linguistic review declares the V2 ordering frozen.
