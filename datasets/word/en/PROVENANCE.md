# Word V3 (English) lexicon provenance

Developer-only offline data for Word Generator V3. Nothing here runs on a device, and the generated
files are never edited by hand: change the manual lists in `lexicon/word/v3/` and rerun the tool.

## Sources

| Source | Version and pin | Licence | Used for |
|---|---|---|---|
| ENABLE word list (`enable1.txt`) | SHA-256 `3f16130220645692ed49c7134e24a18504c2ca55b3c012f7290e3e77c63b1a89` | Public domain | Allowed guesses: every 4–7 letter word, inflections included |
| Open English WordNet, 2023 edition (`english-wordnet-2023.xml.gz`) | SHA-256 `d516489cdeb72b1a332eefd9f63a9941aa2c8dcd5b5c7e4f41c5f48bea9d75cd` | CC BY 4.0 | Answers: nouns, sense counts per part of speech, capitalised names and places |
| `wordfreq` | `3.1.1` (pip, `tools/word-lexicon/requirements.lock`) | Code Apache-2.0, data CC BY-SA 4.0 | Ranking answers by English Zipf frequency |

ENABLE and WordNet are SHA-256 checked by the tool, which refuses other files.

## Reproduce

    python -m pip install -r tools/word-lexicon/requirements.lock
    mkdir -p build/word-sources
    curl -L -o build/word-sources/enable1.txt https://raw.githubusercontent.com/dolph/dictionary/master/enable1.txt
    curl -L -o build/word-sources/english-wordnet-2023.xml.gz https://github.com/globalwordnet/english-wordnet/releases/download/2023-edition/english-wordnet-2023.xml.gz
    cd tools/word-lexicon && python extract_english.py --sources ../../build/word-sources

The tool writes `puzzle-core/src/commonMain/resources/word/v3/`, the review tables in
`lexicon/word/v3/generated/`, and `datasets/word/en/import-report.json` (counts, rules, rejection
reasons, and the samples shown to the owner).

## Rules

- **Guesses:** all 4–7 letter ENABLE words (`a–z` only) minus `guess_blocklist.txt`.
- **Answers:** lower-case WordNet lemmas that are ENABLE words and whose noun senses are at least all
  their other senses. A regular plural of another noun is out, and so is a verb form (-s, -ed, -ing of
  a WordNet verb) with a single noun sense. A word that is also a capitalised WordNet entry (a name or
  place) is out unless its common noun has more than two senses. The word must reach Zipf 3.0 in
  `wordfreq`. From what is left, `answer_allowlist.txt` comes first, then the most frequent words,
  500 per length after `answer_blocklist.txt`.
- **American spelling** (owner decision, stage 10.1a): a British form listed in
  `british_spellings.txt` (`colour → color`, `centre → center`, `defence → defense`, …) is never an
  answer; both forms stay guesses, and the American one becomes an answer through the ordinary rules.
- **Family filter** (owner decision, stage 10.1a): alcohol, tobacco and drugs, gambling, weapons,
  death, violence, crime with prisons and courts, disease, religion, politics, profanity and sex stay
  out of the answers (still guesses). `topic_roots.txt` names WordNet synsets per topic, and a word is
  out when its first noun sense lies anywhere under one of them (hyponyms and instance hyponyms
  followed). Only the first sense counts, so `ball`, `round`, or `steel` keep their everyday meaning;
  the topical words whose topical sense comes later (`poker`, `trial`, `shot`) are listed in
  `topic_words.txt`.
- **Answer order** in `answers.txt` is alphabetical per difficulty. Once a level pack uses it, it is
  generator compatibility data.

## Result

| Length (difficulty) | Guesses | Answer candidates | Answers |
|---|---|---|---|
| 4 (Easy) | 3 903 | 809 | 500 |
| 5 (Medium) | 8 636 | 975 | 500 |
| 6 (Hard) | 15 232 | 1 093 | 500 |
| 7 (Expert) | 23 109 | 1 146 | 500 |

Generated resource SHA-256: `allowed_guesses.txt` `d47823dacb709a68c4212b89fbbc0dce52033c5db775251e18a9ddef397f4094`, `answers.txt` `05e711954ca3067cfcaf5f9d64847b26fbfcee4b27b3acc9e8fe161a4c0bfa22`.

## Licences

The data licences (CC BY 4.0 for WordNet, CC BY-SA 4.0 for the wordfreq frequencies) apply to the
generated lists separately from the repository's source-code licence; attribution: Open English
WordNet (https://en-word.net), wordfreq by Robyn Speer.
