#!/usr/bin/env python3
"""Reproducibly build Word V2 lexical data from an installed pymorphy3 dictionary.

The tool enumerates dictionary-backed entries through Dictionary.iter_known_words(), then uses a
pinned local wordfreq dataset only to rank answer suitability. It never generates arbitrary strings,
never enables morphology prediction as a corpus source, and never uses a network.
"""

from __future__ import annotations

import argparse
import csv
import importlib.metadata
import json
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Iterator

import pymorphy3
from wordfreq import zipf_frequency


PINNED_PYMORPHY3 = "2.0.6"
PINNED_DICTIONARY = "2.4.417150.4580142"
PINNED_WORDFREQ = "3.1.1"
SUPPORTED_LENGTHS = (4, 5, 6, 7)
TARGET_ANSWER_COUNT = 500
DIFFICULTY_BY_LENGTH = {4: "EASY", 5: "MEDIUM", 6: "HARD", 7: "EXPERT"}
RUSSIAN_LETTERS = frozenset("абвгдежзийклмнопрстуфхцчшщъыьэюя")
PROPER_GRAMMEMES = frozenset({"Name", "Surn", "Patr", "Geox", "Orgn", "Trad"})
PLURAL_ONLY_GRAMMEME = "Pltm"
ABBREVIATION_GRAMMEME = "Abbr"
ANSWER_EXCLUDED_GRAMMEMES = frozenset({"Arch", "Slng", "Vulg", "Erro", "Dist", "Ques", "Dmns", "Prnt"})
COMMENT = "#"


@dataclass
class LemmaEvidence:
    noun_tags: set[str] = field(default_factory=set)
    common_noun_tags: set[str] = field(default_factory=set)
    has_noun: bool = False
    has_proper_noun: bool = False
    has_abbreviation_noun: bool = False
    plural_only: bool = False

    def add(self, tag: object) -> None:
        if getattr(tag, "POS", None) != "NOUN":
            return
        self.has_noun = True
        grammemes = frozenset(tag.grammemes)
        rendered = str(tag)
        self.noun_tags.add(rendered)
        if grammemes & PROPER_GRAMMEMES:
            self.has_proper_noun = True
            return
        if ABBREVIATION_GRAMMEME in grammemes:
            self.has_abbreviation_noun = True
            return
        self.common_noun_tags.add(rendered)
        self.plural_only = self.plural_only or PLURAL_ONLY_GRAMMEME in grammemes


@dataclass(frozen=True)
class AcceptedLemma:
    word: str
    raw_lemmas: tuple[str, ...]
    tags: tuple[str, ...]
    plural_only: bool


@dataclass(frozen=True)
class KnownNounAnalysis:
    lemma: str
    tags: tuple[str, ...]
    plural_only: bool


def normalize_letters(raw: str) -> tuple[str | None, str | None]:
    """Lowercase and fold ё to е, rejecting anything outside Russian Cyrillic."""
    value = raw.lower().replace("ё", "е")
    if not value:
        return None, "INVALID_CHARACTERS"
    if any(letter not in RUSSIAN_LETTERS for letter in value):
        return None, "INVALID_CHARACTERS"
    return value, None


def _is_dictionary_parse(parse: object) -> bool:
    return any(method[0].__class__.__name__ == "DictionaryAnalyzer" for method in parse.methods_stack)


def analyze_known_form(analyzer: pymorphy3.MorphAnalyzer, surface: str) -> tuple[KnownNounAnalysis, ...]:
    """Small pinned-version adapter used by fixtures and investigations of individual forms."""
    normalized_surface = surface.lower()
    if normalize_letters(normalized_surface)[1] is not None:
        return ()
    if not analyzer.dictionary.word_is_known(normalized_surface):
        return ()
    grouped: dict[str, LemmaEvidence] = defaultdict(LemmaEvidence)
    for parse in analyzer.parse(normalized_surface):
        if _is_dictionary_parse(parse):
            grouped[parse.normal_form].add(parse.tag)
    accepted: list[KnownNounAnalysis] = []
    for raw_lemma, evidence in grouped.items():
        word, rejection = normalize_letters(raw_lemma)
        if rejection or word is None or len(word) not in SUPPORTED_LENGTHS or not evidence.common_noun_tags:
            continue
        accepted.append(
            KnownNounAnalysis(
                lemma=word,
                tags=tuple(sorted(evidence.common_noun_tags)),
                plural_only=evidence.plural_only,
            )
        )
    return tuple(sorted(accepted, key=lambda item: (item.lemma, item.tags)))


def iter_dictionary_entries(analyzer: pymorphy3.MorphAnalyzer) -> Iterator[tuple[str, object, str]]:
    """Adapter for the iteration API supported by the pinned pymorphy3 version."""
    iterator = getattr(analyzer.dictionary, "iter_known_words", None)
    if iterator is None:
        raise RuntimeError("Pinned pymorphy3 dictionary has no iter_known_words() API.")
    for surface, tag, normal_form, _paradigm_id, _form_index in iterator():
        yield surface, tag, normal_form


def read_manual_words(path: Path) -> list[str]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing manual lexicon file: {path}")
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith(COMMENT)
    ]


def prepare_manual_words(path: Path) -> list[str]:
    words: list[str] = []
    seen: set[str] = set()
    for raw in read_manual_words(path):
        word, rejection = normalize_letters(raw)
        if rejection or word is None:
            raise ValueError(f"{path}: invalid Russian word {raw!r}")
        if len(word) not in SUPPORTED_LENGTHS:
            raise ValueError(f"{path}: unsupported answer length for {raw!r}")
        if word in seen:
            raise ValueError(f"{path}: duplicate normalized word {word!r}")
        seen.add(word)
        words.append(word)
    return words


def installed_provenance(analyzer: pymorphy3.MorphAnalyzer) -> dict[str, object]:
    pymorphy_version = importlib.metadata.version("pymorphy3")
    dictionary_version = importlib.metadata.version("pymorphy3-dicts-ru")
    wordfreq_version = importlib.metadata.version("wordfreq")
    if (
        pymorphy_version != PINNED_PYMORPHY3
        or dictionary_version != PINNED_DICTIONARY
        or wordfreq_version != PINNED_WORDFREQ
    ):
        raise RuntimeError(
            "Installed lexicon-tool versions do not match requirements.lock: "
            f"pymorphy3={pymorphy_version}, pymorphy3-dicts-ru={dictionary_version}, wordfreq={wordfreq_version}"
        )
    useful_keys = (
        "language_code",
        "format_version",
        "compiled_at",
        "source",
        "source_version",
        "source_revision",
        "source_lexemes_count",
        "source_links_count",
        "words_dawg_length",
        "corpus_revision",
    )
    return {
        "pymorphy3_version": pymorphy_version,
        "pymorphy3_dicts_ru_version": dictionary_version,
        "wordfreq_version": wordfreq_version,
        "dictionary": {key: analyzer.dictionary.meta.get(key) for key in useful_keys},
        "lexical_data_license": "Creative Commons Attribution-ShareAlike 3.0 (CC BY-SA 3.0)",
        "package_code_licenses": "pymorphy3/pymorphy3-dicts package code: MIT; wordfreq code: Apache-2.0",
        "frequency_data_license": "wordfreq data: Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)",
        "transformations": [
            "dictionary-known entry enumeration",
            "NOUN-only normal-form extraction",
            "proper-name and abbreviation filtering",
            "lower-case normalization and Ё -> Е folding",
            "4/5/6/7-letter filtering",
            "wordfreq Russian Zipf-frequency ranking for answer candidates",
            "manual answer/guess allow/block curation",
        ],
    }


def is_frequency_answer_candidate(analyzer: pymorphy3.MorphAnalyzer, word: str) -> bool:
    """Require the dictionary's primary interpretation of the lemma itself to be a common noun."""
    dictionary_parses = [parse for parse in analyzer.parse(word) if _is_dictionary_parse(parse)]
    if not dictionary_parses:
        return False
    primary = dictionary_parses[0]
    normalized_lemma, rejection = normalize_letters(primary.normal_form)
    grammemes = frozenset(primary.tag.grammemes)
    return (
        rejection is None
        and normalized_lemma == word
        and primary.tag.POS == "NOUN"
        and not grammemes & PROPER_GRAMMEMES
        and ABBREVIATION_GRAMMEME not in grammemes
        and not grammemes & ANSWER_EXCLUDED_GRAMMEMES
    )


def rank_frequency_candidates(
    analyzer: pymorphy3.MorphAnalyzer,
    words: Iterable[str],
) -> dict[int, list[tuple[str, float]]]:
    ranked: dict[int, list[tuple[str, float]]] = {length: [] for length in SUPPORTED_LENGTHS}
    for word in words:
        if is_frequency_answer_candidate(analyzer, word):
            ranked[len(word)].append((word, zipf_frequency(word, "ru")))
    for length in SUPPORTED_LENGTHS:
        ranked[length].sort(key=lambda item: (-item[1], item[0]))
    return ranked


def collect_lemmas(analyzer: pymorphy3.MorphAnalyzer) -> tuple[dict[str, AcceptedLemma], dict[str, str], dict[str, int]]:
    evidence_by_raw_lemma: dict[str, LemmaEvidence] = defaultdict(LemmaEvidence)
    entry_count = 0
    for _surface, tag, normal_form in iter_dictionary_entries(analyzer):
        entry_count += 1
        evidence_by_raw_lemma[normal_form].add(tag)

    accepted_by_normalized: dict[str, list[tuple[str, LemmaEvidence]]] = defaultdict(list)
    rejected: dict[str, str] = {}
    rejection_counts: Counter[str] = Counter()
    rejection_sample_counts: Counter[str] = Counter()
    for raw_lemma in sorted(evidence_by_raw_lemma):
        evidence = evidence_by_raw_lemma[raw_lemma]
        normalized, invalid_reason = normalize_letters(raw_lemma)
        if invalid_reason or normalized is None:
            reason = "INVALID_CHARACTERS"
        elif len(normalized) not in SUPPORTED_LENGTHS:
            reason = "UNSUPPORTED_LENGTH"
        elif not evidence.has_noun:
            reason = "NOT_NOUN"
        elif not evidence.common_noun_tags and evidence.has_proper_noun:
            reason = "PROPER_NAME"
        elif not evidence.common_noun_tags and evidence.has_abbreviation_noun:
            reason = "ABBREVIATION"
        elif not evidence.common_noun_tags:
            reason = "OTHER_NOUN_FILTER"
        else:
            accepted_by_normalized[normalized].append((raw_lemma, evidence))
            continue
        rejection_counts[reason] += 1
        # Keep complete diagnostics for potentially playable lengths and compact samples otherwise.
        if (normalized is not None and len(normalized) in SUPPORTED_LENGTHS) or rejection_sample_counts[reason] < 25:
            rejected[raw_lemma] = reason
            rejection_sample_counts[reason] += 1

    accepted: dict[str, AcceptedLemma] = {}
    collision_count = 0
    for normalized in sorted(accepted_by_normalized):
        variants = accepted_by_normalized[normalized]
        raw_lemmas = tuple(sorted({raw for raw, _evidence in variants}))
        if len({raw.lower() for raw in raw_lemmas}) > 1:
            collision_count += 1
            rejected[" | ".join(raw_lemmas)] = "NORMALIZATION_COLLISION"
        accepted[normalized] = AcceptedLemma(
            word=normalized,
            raw_lemmas=raw_lemmas,
            tags=tuple(sorted({tag for _raw, evidence in variants for tag in evidence.common_noun_tags})),
            plural_only=any(evidence.plural_only for _raw, evidence in variants),
        )

    stats = {
        "dictionary_entries": entry_count,
        "dictionary_lemmas": len(evidence_by_raw_lemma),
        "accepted_noun_lemmas_before_manual_block": len(accepted),
        "plural_only_accepted": sum(lemma.plural_only for lemma in accepted.values()),
        "normalization_collisions": collision_count,
        **{f"rejected_{reason.lower()}": count for reason, count in sorted(rejection_counts.items())},
    }
    return accepted, rejected, stats


def write_lines(path: Path, header: str, lines: Iterable[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    body = "\n".join(lines)
    path.write_text(f"# {header}\n{body}\n", encoding="utf-8", newline="\n")


def write_tsv(path: Path, fieldnames: list[str], rows: Iterable[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def generate(project_root: Path) -> dict[str, object]:
    analyzer = pymorphy3.MorphAnalyzer(lang="ru")
    provenance = installed_provenance(analyzer)
    source_root = project_root / "lexicon" / "word"
    v2_root = source_root / "v2"
    runtime_root = project_root / "puzzle-core" / "src" / "main" / "resources" / "word" / "v2"
    report_root = v2_root / "generated"

    accepted, rejected, stats = collect_lemmas(analyzer)
    guess_blocklist = set(prepare_manual_words(v2_root / "guess_blocklist.txt"))
    answer_blocklist = set(prepare_manual_words(v2_root / "answer_blocklist.txt"))
    # The historical free-form source contains a few candidates that V1 preparation intentionally
    # filtered by length. Only actual five-letter V1-compatible candidates carry forward.
    v1_source_words = [word for word in prepare_manual_words(source_root / "answers_source.txt") if len(word) == 5]
    # Carry forward only morphology-compatible V1 candidates. For example, a historical accepted
    # plural may normalize to a different singular dictionary lemma and must not be emitted in V2.
    v1_trusted = [word for word in v1_source_words if word in accepted]
    v2_manual = prepare_manual_words(v2_root / "answer_allowlist.txt")

    for blocked in sorted(guess_blocklist):
        if blocked in accepted:
            accepted.pop(blocked)
            rejected[blocked] = "MANUAL_BLOCK"
    allowed_words = sorted(accepted)

    requested_answers: dict[str, str] = {}
    for word in v1_trusted:
        requested_answers[word] = "TRUSTED_V1"
    for word in v2_manual:
        if word in requested_answers:
            raise ValueError(f"V2 answer allowlist repeats trusted V1 answer {word!r}.")
        requested_answers[word] = "MANUAL_ALLOW"

    missing_from_dictionary = sorted(word for word in requested_answers if word not in accepted)
    if missing_from_dictionary:
        sample = ", ".join(missing_from_dictionary[:20])
        raise ValueError(f"Answer candidates are not accepted dictionary noun lemmas: {sample}")

    requested_answers = {word: source for word, source in requested_answers.items() if word not in answer_blocklist}
    ranked_candidates = rank_frequency_candidates(analyzer, allowed_words)
    frequency_by_word = {
        word: frequency
        for candidates in ranked_candidates.values()
        for word, frequency in candidates
    }
    rank_by_word = {
        word: rank
        for candidates in ranked_candidates.values()
        for rank, (word, _frequency) in enumerate(candidates, start=1)
    }
    for length in SUPPORTED_LENGTHS:
        selected_count = sum(len(word) == length for word in requested_answers)
        if selected_count >= TARGET_ANSWER_COUNT:
            continue
        for word, _frequency in ranked_candidates[length]:
            if word in answer_blocklist or word in requested_answers:
                continue
            requested_answers[word] = "WORDFREQ_RANKED"
            selected_count += 1
            if selected_count == TARGET_ANSWER_COUNT:
                break
        if selected_count < TARGET_ANSWER_COUNT:
            raise ValueError(f"Only {selected_count} suitable {length}-letter answers; expected {TARGET_ANSWER_COUNT}.")

    answers = sorted(requested_answers)
    answer_lines = [f"{word}\t{DIFFICULTY_BY_LENGTH[len(word)]}" for word in answers]
    write_lines(
        runtime_root / "allowed_guesses.txt",
        "Generated by tools/word-lexicon/extract_pymorphy3.py; do not edit by hand.",
        allowed_words,
    )
    write_lines(
        runtime_root / "answers.txt",
        "Generated Word V2 answers; format: <word>\\t<difficulty>; ordering is compatibility data.",
        answer_lines,
    )

    review_rows = []
    for word in answers:
        lemma = accepted[word]
        source = requested_answers[word]
        review_rows.append(
            {
                "lemma": word,
                "length": len(word),
                "difficulty": DIFFICULTY_BY_LENGTH[len(word)],
                "morphology_tags": " | ".join(lemma.tags),
                "plural_only": str(lemma.plural_only).lower(),
                "zipf_frequency": f"{frequency_by_word.get(word, zipf_frequency(word, 'ru')):.2f}",
                "frequency_rank_for_length": rank_by_word.get(word, ""),
                "source": source,
                "status": "TRUSTED" if source == "TRUSTED_V1" else "IN_POOL_REVIEW_REQUIRED",
                "manual_decision": "" if source == "WORDFREQ_RANKED" else "ALLOW",
            }
        )
    write_tsv(
        report_root / "answer_candidates.tsv",
        [
            "lemma",
            "length",
            "difficulty",
            "morphology_tags",
            "plural_only",
            "zipf_frequency",
            "frequency_rank_for_length",
            "source",
            "status",
            "manual_decision",
        ],
        review_rows,
    )

    rejection_rows = [
        {"candidate": candidate, "reason": reason}
        for candidate, reason in sorted(rejected.items(), key=lambda item: (item[1], item[0]))
        if candidate not in accepted
    ]
    write_tsv(report_root / "rejection_diagnostics.tsv", ["candidate", "reason"], rejection_rows)

    counts_by_length = {str(length): sum(len(word) == length for word in allowed_words) for length in SUPPORTED_LENGTHS}
    answer_counts = {
        difficulty: sum(DIFFICULTY_BY_LENGTH[len(word)] == difficulty for word in answers)
        for difficulty in DIFFICULTY_BY_LENGTH.values()
    }
    minimum_answer_frequency = {
        difficulty: min(zipf_frequency(word, "ru") for word in answers if DIFFICULTY_BY_LENGTH[len(word)] == difficulty)
        for difficulty in DIFFICULTY_BY_LENGTH.values()
    }
    frequency_selection_floor = {
        difficulty: min(
            frequency_by_word[word]
            for word, source in requested_answers.items()
            if source == "WORDFREQ_RANKED" and DIFFICULTY_BY_LENGTH[len(word)] == difficulty
        )
        for difficulty in DIFFICULTY_BY_LENGTH.values()
        if any(
            source == "WORDFREQ_RANKED" and DIFFICULTY_BY_LENGTH[len(word)] == difficulty
            for word, source in requested_answers.items()
        )
    }
    summary: dict[str, object] = {
        **provenance,
        "counts": {
            **stats,
            "manual_guess_blocks_applied": sum(word in guess_blocklist for word in rejected),
            "allowed_guesses_by_length": counts_by_length,
            "possible_answers_by_difficulty": answer_counts,
            "answer_candidates_requiring_review": sum(source != "TRUSTED_V1" for source in requested_answers.values()),
            "frequency_ranked_answers": sum(source == "WORDFREQ_RANKED" for source in requested_answers.values()),
            "minimum_answer_zipf_frequency_by_difficulty": minimum_answer_frequency,
            "wordfreq_selection_floor_by_difficulty": frequency_selection_floor,
            "v1_trusted_candidates_not_compatible_with_v2_morphology": len(v1_source_words) - len(v1_trusted),
            "rejection_diagnostic_rows": len(rejection_rows),
        },
    }
    report_root.mkdir(parents=True, exist_ok=True)
    (report_root / "metadata.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Repository root (defaults to the script's repository).",
    )
    args = parser.parse_args()
    summary = generate(args.project_root.resolve())
    counts = summary["counts"]
    print(
        f"pymorphy3 {summary['pymorphy3_version']}; "
        f"pymorphy3-dicts-ru {summary['pymorphy3_dicts_ru_version']}; "
        f"wordfreq {summary['wordfreq_version']}"
    )
    print("AllowedGuessesV2 by length: " + ", ".join(f"{key}={value}" for key, value in counts["allowed_guesses_by_length"].items()))
    print("PossibleAnswersV2: " + ", ".join(f"{key}={value}" for key, value in counts["possible_answers_by_difficulty"].items()))
    print(
        "Filtering: "
        f"proper={counts.get('rejected_proper_name', 0)}, "
        f"abbreviation={counts.get('rejected_abbreviation', 0)}, "
        f"plural-only kept={counts['plural_only_accepted']}, "
        f"collisions={counts['normalization_collisions']}"
    )
    print(f"Answer candidates still requiring review: {counts['answer_candidates_requiring_review']}")


if __name__ == "__main__":
    main()
