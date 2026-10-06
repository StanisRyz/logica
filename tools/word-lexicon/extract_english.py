#!/usr/bin/env python3
"""Reproducibly build the Word V3 (English) lexicon.

Guesses: every 4-7 letter word of ENABLE (public domain), inflections included, minus a manual block
list. Answers: lower-case Open English WordNet 2023 lemmas (CC BY 4.0) that are ENABLE words, whose
noun senses are at least all their other senses, that are not a regular plural of another noun, not
a verb form (-s, -ed, -ing of a WordNet verb) with a single noun sense, not a name or place with at
most two common noun senses, and that wordfreq knows at Zipf >= 3.0; the 500 most frequent per length
after the manual block list. Both
sources are SHA-256 pinned files; wordfreq is a pinned pip package. No network is used.

    python tools/word-lexicon/extract_english.py --sources <dir with enable1.txt and english-wordnet-2023.xml.gz>
"""

from __future__ import annotations

import argparse
import gzip
import importlib.metadata
import re
import xml.etree.ElementTree as ElementTree
from collections import Counter, defaultdict
from pathlib import Path

from wordfreq import zipf_frequency

from lexicon_common import (
    MINIMUM_ANSWER_ZIPF,
    SUPPORTED_LENGTHS,
    TARGET_ANSWER_COUNT,
    TOPIC_REASON,
    read_manual_words,
    read_pairs,
    read_topics,
    require_sha256,
    samples,
    select_answers,
    write_report,
    write_runtime,
    write_tsv,
)

PINNED_WORDFREQ = "3.1.1"
ENABLE_FILE = "enable1.txt"
ENABLE_SHA256 = "3f16130220645692ed49c7134e24a18504c2ca55b3c012f7290e3e77c63b1a89"
OEWN_FILE = "english-wordnet-2023.xml.gz"
OEWN_SHA256 = "d516489cdeb72b1a332eefd9f63a9941aa2c8dcd5b5c7e4f41c5f48bea9d75cd"
WORD = re.compile(r"[a-z]{4,7}")
# The family filter reads a word's leading noun senses: its main meanings, not every rare one
# ("suit" goes for its lawsuit sense, "bar" stays despite a barroom sense far down its list).
TOPIC_SENSES = 1


def normalize(raw: str) -> str | None:
    value = "".join(chr(ord(c) + 32) if "A" <= c <= "Z" else c for c in raw.strip())
    return value if re.fullmatch(r"[a-z]+", value) else None


class WordNet:
    """What the filters read from WordNet: sense counts per part of speech for every written lemma form
    (case kept, so proper nouns are capitalized), each form's noun synsets in sense order, and every
    synset's hypernyms (instance hypernyms included)."""

    def __init__(self, path: Path) -> None:
        self.senses: dict[str, Counter[str]] = defaultdict(Counter)
        self.noun_synsets: dict[str, list[str]] = defaultdict(list)
        self.hypernyms: dict[str, list[str]] = {}
        with gzip.open(path) as stream:
            for _event, element in ElementTree.iterparse(stream, events=("end",)):
                if element.tag == "LexicalEntry":
                    lemma = element.find("Lemma")
                    form, pos = lemma.get("writtenForm"), lemma.get("partOfSpeech")
                    synsets = [sense.get("synset") for sense in element.findall("Sense")]
                    self.senses[form][pos] += len(synsets)
                    if pos == "n":
                        self.noun_synsets[form] += synsets
                    element.clear()
                elif element.tag == "Synset":
                    self.hypernyms[element.get("id")] = [
                        relation.get("target")
                        for relation in element.findall("SynsetRelation")
                        if relation.get("relType") in ("hypernym", "instance_hypernym")
                    ]
                    element.clear()

    def topic_trees(self, roots: dict[str, str]) -> dict[str, str]:
        """Every synset under a root (the root included), mapped to the root's topic."""
        unknown = sorted(set(roots) - set(self.hypernyms))
        if unknown:
            raise ValueError(f"Topic roots unknown to the pinned WordNet: {unknown}")
        children: dict[str, list[str]] = defaultdict(list)
        for synset, parents in self.hypernyms.items():
            for parent in parents:
                children[parent].append(synset)
        trees: dict[str, str] = {}
        for root in sorted(roots):
            pending = [root]
            while pending:
                synset = pending.pop()
                if synset not in trees:
                    trees[synset] = roots[root]
                    pending += children[synset]
        return trees

    def topic(self, word: str, trees: dict[str, str]) -> str | None:
        """The topic of the first of the word's leading noun senses that lies in a topic tree."""
        for synset in self.noun_synsets.get(word, [])[:TOPIC_SENSES]:
            if synset in trees:
                return trees[synset]
        return None


def verb_stems(word: str) -> list[str]:
    """Candidate verb lemmas for a word that looks like an inflected verb form (-s, -ed, -ing)."""
    stems: list[str] = []
    if word.endswith("ing"):
        base = word[:-3]
        stems += [base, base + "e"] + ([base[:-1]] if len(base) > 1 and base[-1] == base[-2] else [])
    elif word.endswith("ed"):
        base = word[:-2]
        stems += [base, word[:-1]] + ([base[:-1]] if len(base) > 1 and base[-1] == base[-2] else [])
        if word.endswith("ied"):
            stems.append(word[:-3] + "y")
    elif word.endswith("s"):
        stems += [word[:-1]] + ([word[:-2]] if word.endswith("es") else [])
        if word.endswith("ies"):
            stems.append(word[:-3] + "y")
    return stems


def answer_rejection(word: str, senses: dict[str, Counter[str]], nouns: set[str], verbs: set[str]) -> str | None:
    counts = senses.get(word)
    if counts is None or counts["n"] == 0:
        return "PROPER_ONLY" if word.capitalize() in senses else "NOT_A_WORDNET_NOUN"
    if counts["n"] < sum(count for pos, count in counts.items() if pos != "n"):
        return "NOT_NOUN_DOMINANT"
    stems = verb_stems(word)
    if word.endswith("s") and any(stem in nouns for stem in stems):
        return "PLURAL_FORM"
    # A gerund, participle, or third-person form of a verb is a noun only in a sense or two.
    if counts["n"] < 2 and any(stem in verbs for stem in stems):
        return "VERB_FORM"
    # A capitalized entry (a name or a place) that owns the word unless its common noun has more senses.
    if counts["n"] <= 2 and word.capitalize() in senses:
        return "NAME_OR_PLACE"
    return None


def generate(project_root: Path, sources: Path) -> dict[str, object]:
    if importlib.metadata.version("wordfreq") != PINNED_WORDFREQ:
        raise RuntimeError(f"wordfreq {importlib.metadata.version('wordfreq')} does not match the pinned {PINNED_WORDFREQ}.")
    enable_path, oewn_path = sources / ENABLE_FILE, sources / OEWN_FILE
    require_sha256(enable_path, ENABLE_SHA256)
    require_sha256(oewn_path, OEWN_SHA256)

    lexicon_root = project_root / "lexicon" / "word" / "v3"
    guess_blocklist = set(read_manual_words(lexicon_root / "guess_blocklist.txt", normalize))
    answer_blocklist = set(read_manual_words(lexicon_root / "answer_blocklist.txt", normalize))
    allowlist = read_manual_words(lexicon_root / "answer_allowlist.txt", normalize)
    topic_roots = read_topics(lexicon_root / "topic_roots.txt")
    topic_words = read_topics(lexicon_root / "topic_words.txt")
    british = dict(read_pairs(lexicon_root / "british_spellings.txt"))

    enable = [line.strip() for line in enable_path.read_text(encoding="ascii").splitlines()]
    guesses = sorted({word for word in enable if WORD.fullmatch(word)} - guess_blocklist)
    guess_set = set(guesses)
    enable_set = set(enable)
    for british_form, american_form in british.items():
        if british_form not in guess_set or american_form not in enable_set:
            raise ValueError(f"british_spellings.txt: {british_form} -> {american_form} is not a guess and an ENABLE word.")

    unknown_topic_words = sorted(set(topic_words) - guess_set)
    if unknown_topic_words:
        raise ValueError(f"topic_words.txt lists words that are not guesses: {unknown_topic_words}")

    wordnet = WordNet(oewn_path)
    senses = wordnet.senses
    trees = wordnet.topic_trees(topic_roots)

    def topic_of(word: str) -> str | None:
        return topic_words.get(word) or wordnet.topic(word, trees)
    nouns = {form for form, counts in senses.items() if counts["n"] > 0 and re.fullmatch(r"[a-z]+", form)}
    verbs = {form for form, counts in senses.items() if counts["v"] > 0 and re.fullmatch(r"[a-z]+", form)}

    ranked: dict[int, list[tuple[str, float]]] = {length: [] for length in SUPPORTED_LENGTHS}
    rejected: list[tuple[str, str, float]] = []
    reasons: Counter[str] = Counter()
    for word in guesses:
        zipf = zipf_frequency(word, "en")
        reason = answer_rejection(word, senses, nouns, verbs)
        if reason is None and word in british:
            reason = "BRITISH_SPELLING"
        if reason is None and (topic := topic_of(word)) is not None:
            reason = f"{TOPIC_REASON}:{topic}"
        if reason is None and zipf < MINIMUM_ANSWER_ZIPF:
            reason = "RARE"
        if reason is None:
            ranked[len(word)].append((word, zipf))
        else:
            reasons[reason] += 1
            if zipf >= MINIMUM_ANSWER_ZIPF:
                rejected.append((word, reason, zipf))
    for length in SUPPORTED_LENGTHS:
        ranked[length].sort(key=lambda item: (-item[1], item[0]))
    for word in sorted(answer_blocklist):
        if word in guess_set:
            zipf = zipf_frequency(word, "en")
            rejected.append((word, "MANUAL_BLOCK", zipf))
    rejected.sort(key=lambda item: (-item[2], item[0]))

    for word in allowlist:
        if word in british or topic_of(word) is not None:
            raise ValueError(f"Allowlisted answer {word!r} is a British spelling or in a family-filter topic.")
    selected = select_answers(ranked, allowlist, answer_blocklist, guess_set)
    answers = sorted(selected)
    write_runtime(project_root / "puzzle-core" / "src" / "commonMain" / "resources" / "word" / "v3", "extract_english.py", "V3", guesses, answers)
    write_tsv(
        lexicon_root / "generated" / "answer_candidates.tsv",
        ["word", "length", "zipf", "noun_senses", "other_senses", "source"],
        (
            {
                "word": word,
                "length": len(word),
                "zipf": f"{zipf_frequency(word, 'en'):.2f}",
                "noun_senses": senses[word]["n"],
                "other_senses": sum(count for pos, count in senses[word].items() if pos != "n"),
                "source": selected[word],
            }
            for word in answers
        ),
    )
    write_tsv(
        lexicon_root / "generated" / "rejected_answers.tsv",
        ["word", "length", "zipf", "reason"],
        ({"word": word, "length": len(word), "zipf": f"{zipf:.2f}", "reason": reason} for word, reason, zipf in rejected),
    )

    report = {
        "language": "en",
        "generator_version": 3,
        "sources": {
            "enable": {"file": ENABLE_FILE, "sha256": ENABLE_SHA256, "license": "Public domain (ENABLE2K)"},
            "open_english_wordnet": {"file": OEWN_FILE, "sha256": OEWN_SHA256, "edition": "2023", "license": "CC BY 4.0"},
            "wordfreq": {"version": PINNED_WORDFREQ, "code_license": "Apache-2.0", "data_license": "CC BY-SA 4.0"},
        },
        "rules": {
            "target_answers_per_length": TARGET_ANSWER_COUNT,
            "minimum_answer_zipf": MINIMUM_ANSWER_ZIPF,
            "answer_filter": (
                "lower-case WordNet lemma, noun senses >= other senses, not a regular plural of another noun, "
                "not a one-sense verb form, not a name or place with at most two common noun senses"
            ),
            "american_spelling": "a British form from british_spellings.txt is never an answer",
            "family_filter": (
                "the first noun sense does not lie under a topic_roots.txt synset and the word is not in topic_words.txt "
                "(alcohol, tobacco, drugs, gambling, weapons, death, violence, crime, disease, religion, politics, profanity, sexual)"
            ),
        },
        "counts": {
            str(length): {
                "guesses": sum(len(word) == length for word in guesses),
                "answers": sum(len(word) == length for word in answers),
                "answer_candidates": len(ranked[length]),
            }
            for length in SUPPORTED_LENGTHS
        },
        "manual": {
            "guess_blocklist": len(guess_blocklist),
            "answer_blocklist": len(answer_blocklist),
            "answer_allowlist": len(allowlist),
            "topic_roots": len(topic_roots),
            "topic_words": len(topic_words),
            "topic_synsets": len(trees),
            "british_spellings": len(british),
        },
        "rejection_reasons": dict(sorted(reasons.items())),
        "samples": samples(answers, rejected, frozenset({"NOT_A_WORDNET_NOUN"})),
    }
    write_report(project_root / "datasets" / "word" / "en" / "import-report.json", report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--sources", type=Path, required=True)
    arguments = parser.parse_args()
    report = generate(arguments.project_root, arguments.sources)
    for length, counts in report["counts"].items():
        print(f"{length} letters: {counts}")


if __name__ == "__main__":
    main()
