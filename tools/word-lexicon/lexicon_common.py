"""Shared helpers for the offline Word V3 (English) and V4 (Turkish) lexicon extraction.

Developer-only: nothing here runs on a device. Inputs are pinned (pip versions or SHA-256 checked
files), outputs are deterministic, and generated files are never edited by hand.
"""

from __future__ import annotations

import csv
import hashlib
import json
import random
from pathlib import Path
from typing import Callable, Iterable

SUPPORTED_LENGTHS = (4, 5, 6, 7)
TARGET_ANSWER_COUNT = 500
MINIMUM_ANSWER_ZIPF = 3.0
DIFFICULTY_BY_LENGTH = {4: "EASY", 5: "MEDIUM", 6: "HARD", 7: "EXPERT"}
SAMPLE_SEED = 20261006
COMMENT = "#"
# The family filter's topics (owner decision, stage 10.1a); a word in one of them is never an answer.
TOPICS = frozenset(
    {"alcohol", "tobacco", "drugs", "gambling", "weapons", "death", "violence", "crime", "disease", "religion", "politics", "profanity", "sexual"}
)
TOPIC_REASON = "TOPIC"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def require_sha256(path: Path, expected: str) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Missing pinned source {path}; see datasets/word/*/PROVENANCE.md for the download command.")
    actual = sha256_of(path)
    if actual != expected:
        raise RuntimeError(f"{path} has SHA-256 {actual}, expected the pinned {expected}.")


def read_manual_words(path: Path, normalize: Callable[[str], str | None]) -> list[str]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing manual lexicon file: {path}")
    words: list[str] = []
    seen: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.split(COMMENT, 1)[0].strip()
        if not raw:
            continue
        word = normalize(raw)
        if word is None or len(word) not in SUPPORTED_LENGTHS:
            raise ValueError(f"{path}: {raw!r} is not a normalized 4-7 letter word")
        if word in seen:
            raise ValueError(f"{path}: duplicate word {word!r}")
        seen.add(word)
        words.append(word)
    return words


def read_pairs(path: Path) -> list[tuple[str, str]]:
    """Two whitespace-separated fields per line (comments and blank lines skipped), in file order."""
    if not path.is_file():
        raise FileNotFoundError(f"Missing manual lexicon file: {path}")
    pairs: list[tuple[str, str]] = []
    seen: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.split(COMMENT, 1)[0].split()
        if not raw:
            continue
        if len(raw) != 2:
            raise ValueError(f"{path}: expected two fields in {line!r}")
        if raw[0] in seen:
            raise ValueError(f"{path}: duplicate entry {raw[0]!r}")
        seen.add(raw[0])
        pairs.append((raw[0], raw[1]))
    return pairs


def read_topics(path: Path) -> dict[str, str]:
    """A family-filter list: `<entry> <topic>` per line, every topic one of [TOPICS]."""
    topics = dict(read_pairs(path))
    unknown = sorted(set(topics.values()) - TOPICS)
    if unknown:
        raise ValueError(f"{path}: unknown topics {unknown}")
    return topics


def select_answers(
    ranked: dict[int, list[tuple[str, float]]],
    allowlist: Iterable[str],
    blocklist: set[str],
    guesses: set[str],
) -> dict[str, str]:
    """Allowlisted words first, then the most frequent candidates, until each length has its target."""
    selected: dict[str, str] = {}
    for word in allowlist:
        if word not in guesses:
            raise ValueError(f"Allowlisted answer {word!r} is not an allowed guess.")
        if word in blocklist:
            raise ValueError(f"Answer {word!r} is both allowlisted and blocklisted.")
        selected[word] = "MANUAL_ALLOW"
    for length in SUPPORTED_LENGTHS:
        count = sum(len(word) == length for word in selected)
        for word, _zipf in ranked[length]:
            if count >= TARGET_ANSWER_COUNT:
                break
            if word in blocklist or word in selected:
                continue
            selected[word] = "WORDFREQ_RANKED"
            count += 1
        if count < TARGET_ANSWER_COUNT:
            raise ValueError(f"Only {count} suitable {length}-letter answers; expected {TARGET_ANSWER_COUNT}.")
    return selected


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


def write_runtime(runtime_root: Path, tool: str, version: str, guesses: list[str], answers: list[str]) -> None:
    write_lines(runtime_root / "allowed_guesses.txt", f"Generated by tools/word-lexicon/{tool}; do not edit by hand.", guesses)
    write_lines(
        runtime_root / "answers.txt",
        f"Generated Word {version} answers by tools/word-lexicon/{tool}; format: <word>\\t<difficulty>; ordering is compatibility data.",
        [f"{word}\t{DIFFICULTY_BY_LENGTH[len(word)]}" for word in answers],
    )


def samples(
    answers: list[str],
    rejected: list[tuple[str, str, float]],
    obvious_reasons: frozenset[str],
) -> dict[str, dict[str, object]]:
    """Per length: 40 deterministic answer samples, the 20 most frequent words the answer filter dropped
    for a reason worth reviewing (not the [obvious_reasons], which are mostly function words), and the 20
    most frequent words the family filter dropped."""
    result: dict[str, dict[str, object]] = {}
    for length in SUPPORTED_LENGTHS:
        pool = sorted(word for word in answers if len(word) == length)
        dropped = [
            entry
            for entry in rejected
            if len(entry[0]) == length and entry[1] not in obvious_reasons and not entry[1].startswith(TOPIC_REASON)
        ][:20]
        topic = [entry for entry in rejected if len(entry[0]) == length and entry[1].startswith(TOPIC_REASON)][:20]
        result[str(length)] = {
            "answers": sorted(random.Random(SAMPLE_SEED + length).sample(pool, 40)),
            "dropped": [{"word": word, "reason": reason, "zipf": round(zipf, 2)} for word, reason, zipf in dropped],
            "topic_dropped": [{"word": word, "reason": reason, "zipf": round(zipf, 2)} for word, reason, zipf in topic],
        }
    return result


def write_report(path: Path, report: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
