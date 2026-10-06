# Word V4 (Turkish) lexicon provenance

Developer-only offline data for Word Generator V4. Nothing here runs on a device, and the generated
files are never edited by hand: change the manual lists in `lexicon/word/v4/` and rerun the tool.

## Sources

| Source | Version and pin | Licence | Used for |
|---|---|---|---|
| `zemberek-python` (port of Zemberek-NLP, with its root lexicon and morphology) | `0.2.3` (pip, `tools/word-lexicon/requirements.lock`) | Apache-2.0 | Lemmas with part of speech and proper-noun/abbreviation flags; analysis of inflected forms |
| `wordfreq` | `3.1.1` (pip) | Code Apache-2.0, data CC BY-SA 4.0 | Inflected guess forms (the Turkish word list) and ranking answers by Zipf frequency |

## Reproduce

    python -m pip install -r tools/word-lexicon/requirements.lock
    cd tools/word-lexicon && python extract_turkish.py

The tool writes `puzzle-core/src/commonMain/resources/word/v4/`, the review tables in
`lexicon/word/v4/generated/`, and `datasets/word/tr/import-report.json`.

## Rules

- **Normalisation** (the same as `TurkishWordNormalizer`): Turkish lower case by an explicit table
  (`I → ı`, `İ → i`), the circumflex vowels `â î û` folded to `a i u`, and the 29-letter
  alphabet only (no q, w, x).
- **Guesses:** every 4–7 letter Zemberek lemma that is not a proper noun or an abbreviation (all parts
  of speech, verbs as their -mak/-mek infinitive). Added to them: every 4–7 letter word of wordfreq's
  Turkish list that Zemberek analyses through such a lemma (inflected forms such as `evler`). Minus
  `guess_blocklist.txt`.
- **Answers:** lemmas that exist only as common nouns, with no adjective, verb or other entry of the
  same form, written without a circumflex, and present in wordfreq's Turkish list (Zipf ≥ 3.0, its
  floor). Also out is any word the analyser reads as:
  - a verb form;
  - a `-sal/-sel` adjective;
  - a plural of another lemma;
  - another inflection of a lemma at least 0.5 Zipf more frequent (`adamı` = `adam` + accusative).

  `answer_allowlist.txt` comes first, then the most frequent words, 500 per length after
  `answer_blocklist.txt`.
- **The allowlist** holds 86 common nouns the strict rules drop because Zemberek also lists them as
  adjectives or reads them as verb forms (`insan`, `kadın`, `çocuk`, `oyun`, `düşünce`, …).
- **Answer order** is alphabetical per difficulty (Python code-point order).
- **Determinism.** Zemberek's analyser iterates Python sets of strings, so the tool re-runs itself
  with `PYTHONHASHSEED=0`; without it a few doubled-consonant guesses (`reddi`, `tıbbı`) come and go.

## Result

| Length (difficulty) | Guesses (lemmas + inflected) | Answer candidates | Answers |
|---|---|---|---|
| 4 (Easy) | 2 653 (2 015 + 638) | 697 | 500 |
| 5 (Medium) | 7 018 (5 043 + 1 975) | 1 279 | 500 |
| 6 (Hard) | 9 017 (4 825 + 4 192) | 1 000 | 500 |
| 7 (Expert) | 11 096 (4 524 + 6 572) | 738 | 500 |

Generated resource SHA-256: `allowed_guesses.txt` `368d2209dbdf6fe4bc40f3feddfb0d64e5ee5836fe25a8596d3b8c9c784dcea7`, `answers.txt` `3687ae88095ea7c8de265db901767ba25e1b9b1f465d6c1f62ab6debf1415fc2`.

## Licences

Zemberek is Apache-2.0; the wordfreq frequencies are CC BY-SA 4.0 and apply to the generated lists
separately from the repository's source-code licence.
