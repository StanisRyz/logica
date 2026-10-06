#!/usr/bin/env python3
"""Reproducibly build the Word V4 (Turkish) lexicon.

Guesses: every 4-7 letter lemma of the Zemberek root lexicon (Apache-2.0) that is not a proper noun
or an abbreviation, plus every 4-7 letter word of wordfreq's Turkish list that Zemberek's morphology
analyzes through such a lemma (inflected forms like "evler"), minus a manual block list. Answers:
Zemberek lemmas that exist only as common nouns (no adjective, verb, or other entry of the same form),
were written without a circumflex, that wordfreq knows (Zipf >= 3.0), and that the analyzer never
reads as a verb form, a -sal/-sel adjective, a plural of another lemma, or another inflection of a
lemma at least 0.5 Zipf more frequent; the 500 most frequent per length after the manual block list. Both packages are pinned; no network is used.

    python tools/word-lexicon/extract_turkish.py
"""

from __future__ import annotations

import argparse
import importlib.metadata
import logging
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path

from wordfreq import top_n_list, zipf_frequency
from zemberek import TurkishMorphology
from zemberek.morphology.lexicon import RootLexicon

from lexicon_common import (
    MINIMUM_ANSWER_ZIPF,
    SUPPORTED_LENGTHS,
    TARGET_ANSWER_COUNT,
    read_manual_words,
    samples,
    select_answers,
    write_report,
    write_runtime,
    write_tsv,
)

PINNED_WORDFREQ = "3.1.1"
PINNED_ZEMBEREK = "0.2.3"
ALPHABET = frozenset("abcçdefgğhıijklmnoöprsştuüvyz")
CIRCUMFLEX = {"â": "a", "î": "i", "û": "u"}
LOWER = {"I": "ı", "İ": "i", "Ç": "ç", "Ğ": "ğ", "Ö": "ö", "Ş": "ş", "Ü": "ü", "Â": "â", "Î": "î", "Û": "û"}
EXCLUDED_SECONDARY = {"ProperNoun", "Abbreviation"}
INFLECTIONS = {"Acc", "Dat", "Loc", "Abl", "Gen", "Ins", "A3pl", "P1sg", "P2sg", "P3sg", "P1pl", "P2pl", "P3pl"}
# An inflected reading of another lemma rejects a word when that lemma is this much more frequent.
INFLECTED_READING_MARGIN = 0.5


def morphology_rejection(morphology: TurkishMorphology, word: str, zipf: float) -> str | None:
    """Why the analyzer says a lexicon noun is not a plain base-form noun, or None if it is one."""
    for analysis in morphology.analyze(word).analysis_results:
        item = analysis.item
        if item.primary_pos.name == "Verb":
            return "VERB_FORM"
        morphemes = [morpheme.id_ for morpheme in analysis.get_morphemes()]
        parts_of_speech = [morpheme.pos for morpheme in analysis.get_morphemes() if morpheme.pos is not None]
        if "Related" in morphemes and parts_of_speech[-1].name == "Adjective":
            return "ADJECTIVE_FORM"
        lemma = normalize(item.lemma)
        if lemma is None or lemma == word or len(parts_of_speech) > 1 or item.secondary_pos.name in EXCLUDED_SECONDARY:
            continue
        inflections = set(morphemes) & INFLECTIONS
        if "A3pl" in inflections or (inflections and zipf_frequency(lemma, "tr") >= zipf + INFLECTED_READING_MARGIN):
            return "INFLECTED_FORM"
    return None


def lower(raw: str) -> str:
    return "".join(LOWER.get(c, c.lower() if "A" <= c <= "Z" else c) for c in raw)


def normalize(raw: str) -> str | None:
    """The same rule as TurkishWordNormalizer: Turkish lower case, circumflex vowels folded, 29 letters only."""
    value = "".join(CIRCUMFLEX.get(c, c) for c in lower(raw.strip()))
    return value if value and all(c in ALPHABET for c in value) else None


def generate(project_root: Path) -> dict[str, object]:
    for package, pinned in (("wordfreq", PINNED_WORDFREQ), ("zemberek-python", PINNED_ZEMBEREK)):
        if importlib.metadata.version(package) != pinned:
            raise RuntimeError(f"{package} {importlib.metadata.version(package)} does not match the pinned {pinned}.")
    logging.disable(logging.INFO)

    lexicon_root = project_root / "lexicon" / "word" / "v4"
    guess_blocklist = set(read_manual_words(lexicon_root / "guess_blocklist.txt", normalize))
    answer_blocklist = set(read_manual_words(lexicon_root / "answer_blocklist.txt", normalize))
    allowlist = read_manual_words(lexicon_root / "answer_allowlist.txt", normalize)

    parts_of_speech: dict[str, set[str]] = defaultdict(set)
    circumflexed: set[str] = set()
    for item in RootLexicon.get_default():
        word = normalize(item.lemma)
        if word is None or len(word) not in SUPPORTED_LENGTHS:
            continue
        if item.secondary_pos.name in EXCLUDED_SECONDARY or item.primary_pos.name == "Punctuation":
            continue
        parts_of_speech[word].add(item.primary_pos.name)
        if any(c in CIRCUMFLEX for c in lower(item.lemma)):
            circumflexed.add(word)

    morphology = TurkishMorphology.create_with_defaults()
    inflected: set[str] = set()
    for form in top_n_list("tr", 10**7):
        word = normalize(form)
        if word is None or len(word) not in SUPPORTED_LENGTHS or word in parts_of_speech:
            continue
        analyses = morphology.analyze(word).analysis_results
        if any(analysis.item.secondary_pos.name not in EXCLUDED_SECONDARY for analysis in analyses):
            inflected.add(word)
    guesses = sorted((set(parts_of_speech) | inflected) - guess_blocklist)
    guess_set = set(guesses)

    ranked: dict[int, list[tuple[str, float]]] = {length: [] for length in SUPPORTED_LENGTHS}
    rejected: list[tuple[str, str, float]] = []
    reasons: Counter[str] = Counter()
    for word in guesses:
        zipf = zipf_frequency(word, "tr")
        pos = parts_of_speech.get(word)
        if pos is None:
            reason = "NOT_A_LEMMA"
        elif "Noun" not in pos:
            reason = "NOT_A_NOUN"
        elif pos != {"Noun"}:
            reason = "ALSO_OTHER_PART_OF_SPEECH"
        elif word in circumflexed:
            reason = "CIRCUMFLEX"
        elif zipf < MINIMUM_ANSWER_ZIPF:
            reason = "RARE"
        elif (reason := morphology_rejection(morphology, word, zipf)) is not None:
            pass
        else:
            ranked[len(word)].append((word, zipf))
            continue
        reasons[reason] += 1
        if zipf >= MINIMUM_ANSWER_ZIPF:
            rejected.append((word, reason, zipf))
    for length in SUPPORTED_LENGTHS:
        ranked[length].sort(key=lambda item: (-item[1], item[0]))
    for word in sorted(answer_blocklist):
        if word in guess_set:
            rejected.append((word, "MANUAL_BLOCK", zipf_frequency(word, "tr")))
    rejected.sort(key=lambda item: (-item[2], item[0]))

    selected = select_answers(ranked, allowlist, answer_blocklist, guess_set)
    answers = sorted(selected)
    write_runtime(project_root / "puzzle-core" / "src" / "commonMain" / "resources" / "word" / "v4", "extract_turkish.py", "V4", guesses, answers)
    write_tsv(
        lexicon_root / "generated" / "answer_candidates.tsv",
        ["word", "length", "zipf", "source"],
        ({"word": word, "length": len(word), "zipf": f"{zipf_frequency(word, 'tr'):.2f}", "source": selected[word]} for word in answers),
    )
    write_tsv(
        lexicon_root / "generated" / "rejected_answers.tsv",
        ["word", "length", "zipf", "reason"],
        ({"word": word, "length": len(word), "zipf": f"{zipf:.2f}", "reason": reason} for word, reason, zipf in rejected),
    )

    report = {
        "language": "tr",
        "generator_version": 4,
        "sources": {
            "zemberek_python": {"version": PINNED_ZEMBEREK, "license": "Apache-2.0", "data": "Zemberek-NLP root lexicon and morphology"},
            "wordfreq": {"version": PINNED_WORDFREQ, "code_license": "Apache-2.0", "data_license": "CC BY-SA 4.0"},
        },
        "rules": {
            "target_answers_per_length": TARGET_ANSWER_COUNT,
            "minimum_answer_zipf": MINIMUM_ANSWER_ZIPF,
            "normalization": "Turkish lower case (I -> ı, İ -> i), â/î/û folded to a/i/u, 29 letters without q, w, x",
            "answer_filter": (
                "a Zemberek lemma that is only a common noun, written without a circumflex, never analyzed as a verb form, "
                "a -sal/-sel adjective, a plural of another lemma, or another inflection of a lemma 0.5 Zipf more frequent"
            ),
        },
        "counts": {
            str(length): {
                "guesses": sum(len(word) == length for word in guesses),
                "guess_lemmas": sum(len(word) == length for word in parts_of_speech),
                "guess_inflected_forms": sum(len(word) == length for word in inflected),
                "answers": sum(len(word) == length for word in answers),
                "answer_candidates": len(ranked[length]),
            }
            for length in SUPPORTED_LENGTHS
        },
        "manual": {"guess_blocklist": len(guess_blocklist), "answer_blocklist": len(answer_blocklist), "answer_allowlist": len(allowlist)},
        "rejection_reasons": dict(sorted(reasons.items())),
        "samples": samples(answers, rejected, frozenset({"NOT_A_NOUN", "NOT_A_LEMMA"})),
    }
    write_report(project_root / "datasets" / "word" / "tr" / "import-report.json", report)
    return report


def main() -> None:
    # Zemberek's analyzer iterates Python sets of strings, so a few forms (doubled consonants such as
    # "reddi") depend on the string hash order; one fixed seed makes every run write the same files.
    if os.environ.get("PYTHONHASHSEED") != "0":
        os.execve(sys.executable, [sys.executable, *sys.argv], {**os.environ, "PYTHONHASHSEED": "0"})
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[2])
    arguments = parser.parse_args()
    report = generate(arguments.project_root)
    for length, counts in report["counts"].items():
        print(f"{length} letters: {counts}")


if __name__ == "__main__":
    main()
