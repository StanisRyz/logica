#!/usr/bin/env python3
"""Build the immutable Sudoku Dataset V1 from a local Puzzle Bank checkout.

The Android application never runs this tool. It consumes only the generated
fixed-width binary files under app/src/main/assets/sudoku/v1.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import multiprocessing
import os
import re
import struct
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path


IMPORTER_VERSION = 1
DATASET_VERSION = 1
UPSTREAM_REPOSITORY = "https://github.com/grantm/sudoku-exchange-puzzle-bank"
UPSTREAM_REVISION = "d8c8ebaee0c08c412cfba96af1923dfa61c83317"
DEFAULT_LIMITS = {
    "EASY": 10_000,
    "MEDIUM": 10_000,
    "HARD": 10_000,
    "EXPERT": 10_000,
}
CATEGORIES = (
    ("easy.txt", "EASY", 1, Decimal("0"), Decimal("1.5")),
    ("medium.txt", "MEDIUM", 2, Decimal("1.5"), Decimal("2.5")),
    ("hard.txt", "HARD", 3, Decimal("2.5"), Decimal("5.0")),
    ("diabolical.txt", "EXPERT", 4, Decimal("5.0"), None),
)
MAGIC = b"LOGSDK01"
HEADER = struct.Struct(">8sBBIH")
RECORD_SIZE = 32 + 2 + 41 + 41
SOURCE_RECORD_RE = re.compile(rb"([0-9a-f]{12}) ([0-9]{81})  ([0-9]\.[0-9])")
ALL_DIGITS_MASK = 0x3FE
_worker_minimum_rating = Decimal("0")
_worker_maximum_rating: Decimal | None = None


@dataclass
class BucketReport:
    source_records_read: int = 0
    malformed_rejected: int = 0
    invalid_givens_rejected: int = 0
    unsolved_rejected: int = 0
    multi_solution_rejected: int = 0
    duplicate_rejected: int = 0
    valid_records: int = 0
    records_selected: int = 0
    requested: int = 0
    shortfall: int = 0
    asset_bytes: int = 0
    asset_sha256: str = ""


@dataclass(frozen=True)
class SelectedRecord:
    fingerprint: bytes
    rating_tenths: int
    givens: str
    solution: str


def initialize_validation_worker(
    minimum_rating: str,
    maximum_rating: str | None,
) -> None:
    global _worker_minimum_rating, _worker_maximum_rating
    _worker_minimum_rating = Decimal(minimum_rating)
    _worker_maximum_rating = Decimal(maximum_rating) if maximum_rating is not None else None


def parse_limits(values: list[str]) -> dict[str, int]:
    limits = dict(DEFAULT_LIMITS)
    for value in values:
        try:
            difficulty, raw_limit = value.split("=", 1)
            difficulty = difficulty.upper()
            limit = int(raw_limit)
        except ValueError as error:
            raise argparse.ArgumentTypeError(
                f"Invalid limit {value!r}; expected DIFFICULTY=COUNT"
            ) from error
        if difficulty not in limits or limit < 0:
            raise argparse.ArgumentTypeError(f"Invalid limit {value!r}")
        limits[difficulty] = limit
    return limits


def verify_upstream_revision(upstream_root: Path, expected_revision: str) -> None:
    try:
        actual = subprocess.run(
            ["git", "-C", str(upstream_root), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise RuntimeError("Upstream root must be a Git checkout") from error
    if actual != expected_revision:
        raise RuntimeError(
            f"Upstream revision mismatch: expected {expected_revision}, found {actual}"
        )
    try:
        dirty = subprocess.run(
            ["git", "-C", str(upstream_root), "status", "--porcelain", "--untracked-files=no"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise RuntimeError("Unable to verify the upstream checkout state") from error
    if dirty:
        raise RuntimeError("Upstream checkout has modified tracked files")


def parse_source_record(
    raw_line: bytes,
    minimum_rating: Decimal,
    maximum_rating: Decimal | None,
) -> tuple[str, int] | None:
    line = raw_line.rstrip(b"\r\n")
    match = SOURCE_RECORD_RE.fullmatch(line)
    if match is None:
        return None
    source_hash, puzzle_bytes, rating_bytes = match.groups()
    if hashlib.sha1(puzzle_bytes).hexdigest()[:12].encode("ascii") != source_hash:
        return None
    try:
        rating = Decimal(rating_bytes.decode("ascii"))
    except (InvalidOperation, UnicodeDecodeError):
        return None
    if rating < minimum_rating or (maximum_rating is not None and rating >= maximum_rating):
        return None
    rating_tenths = int(rating * 10)
    if rating_tenths <= 0 or rating_tenths > 0xFFFF:
        return None
    return puzzle_bytes.decode("ascii"), rating_tenths


def solve_unique(givens: str) -> tuple[int, str | None] | None:
    """Return (solution count capped at two, first solution), or None for bad givens."""
    board = [ord(value) - 48 for value in givens]
    rows = [0] * 9
    columns = [0] * 9
    blocks = [0] * 9
    empty: list[int] = []

    for index, value in enumerate(board):
        if value == 0:
            empty.append(index)
            continue
        row, column = divmod(index, 9)
        block = (row // 3) * 3 + column // 3
        bit = 1 << value
        if rows[row] & bit or columns[column] & bit or blocks[block] & bit:
            return None
        rows[row] |= bit
        columns[column] |= bit
        blocks[block] |= bit

    solution_count = 0
    first_solution: str | None = None

    def search(depth: int) -> None:
        nonlocal solution_count, first_solution
        if solution_count >= 2:
            return
        if depth == len(empty):
            solution_count += 1
            if first_solution is None:
                first_solution = "".join(str(value) for value in board)
            return

        best_offset = -1
        best_candidates = 0
        best_count = 10
        for offset in range(depth, len(empty)):
            index = empty[offset]
            row, column = divmod(index, 9)
            block = (row // 3) * 3 + column // 3
            candidates = ALL_DIGITS_MASK & ~(rows[row] | columns[column] | blocks[block])
            candidate_count = candidates.bit_count()
            if candidate_count < best_count:
                best_offset = offset
                best_candidates = candidates
                best_count = candidate_count
                if candidate_count <= 1:
                    break
        if best_count == 0:
            return

        empty[depth], empty[best_offset] = empty[best_offset], empty[depth]
        index = empty[depth]
        row, column = divmod(index, 9)
        block = (row // 3) * 3 + column // 3
        candidates = best_candidates
        while candidates and solution_count < 2:
            bit = candidates & -candidates
            candidates -= bit
            value = bit.bit_length() - 1
            board[index] = value
            rows[row] |= bit
            columns[column] |= bit
            blocks[block] |= bit
            search(depth + 1)
            rows[row] ^= bit
            columns[column] ^= bit
            blocks[block] ^= bit
            board[index] = 0
        empty[depth], empty[best_offset] = empty[best_offset], empty[depth]

    search(0)
    return solution_count, first_solution


def verify_solution(givens: str, solution: str) -> bool:
    if len(solution) != 81 or any(value not in "123456789" for value in solution):
        return False
    if any(given != "0" and given != solved for given, solved in zip(givens, solution)):
        return False
    expected = set("123456789")
    for index in range(9):
        row = set(solution[index * 9 : index * 9 + 9])
        column = set(solution[index::9])
        block_row = (index // 3) * 3
        block_column = (index % 3) * 3
        block = {
            solution[(block_row + row_offset) * 9 + block_column + column_offset]
            for row_offset in range(3)
            for column_offset in range(3)
        }
        if row != expected or column != expected or block != expected:
            return False
    return True


def validate_source_line(raw_line: bytes) -> tuple[str, SelectedRecord | None]:
    parsed = parse_source_record(raw_line, _worker_minimum_rating, _worker_maximum_rating)
    if parsed is None:
        return "malformed", None
    givens, rating_tenths = parsed
    solved = solve_unique(givens)
    if solved is None:
        return "invalid_givens", None
    solution_count, solution = solved
    if solution_count == 0 or solution is None:
        return "unsolved", None
    if solution_count > 1:
        return "multiple", None
    if not verify_solution(givens, solution):
        return "unsolved", None
    return (
        "valid",
        SelectedRecord(
            hashlib.sha256(givens.encode("ascii")).digest(),
            rating_tenths,
            givens,
            solution,
        ),
    )


def pack_cells(cells: str) -> bytes:
    packed = bytearray(41)
    for index, value in enumerate(cells):
        digit = ord(value) - 48
        if index % 2 == 0:
            packed[index // 2] = digit << 4
        else:
            packed[index // 2] |= digit
    return bytes(packed)


def write_asset(
    output_path: Path,
    difficulty_code: int,
    records: list[SelectedRecord],
) -> tuple[int, str]:
    records.sort(key=lambda record: record.fingerprint)
    content = bytearray(HEADER.pack(MAGIC, DATASET_VERSION, difficulty_code, len(records), RECORD_SIZE))
    for record in records:
        content.extend(record.fingerprint)
        content.extend(struct.pack(">H", record.rating_tenths))
        content.extend(pack_cells(record.givens))
        content.extend(pack_cells(record.solution))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_suffix(output_path.suffix + ".tmp")
    temporary_path.write_bytes(content)
    temporary_path.replace(output_path)
    return len(content), hashlib.sha256(content).hexdigest()


def import_dataset(
    upstream_root: Path,
    output_root: Path,
    limits: dict[str, int],
    workers: int,
) -> dict[str, BucketReport]:
    seen_fingerprints: set[bytes] = set()
    report: dict[str, BucketReport] = {}

    for filename, difficulty, difficulty_code, minimum_rating, maximum_rating in CATEGORIES:
        bucket = BucketReport(requested=limits[difficulty])
        selected: list[SelectedRecord] = []
        source_path = upstream_root / filename
        if not source_path.is_file():
            raise RuntimeError(f"Missing upstream file: {source_path}")

        with (
            source_path.open("rb") as source,
            multiprocessing.Pool(
                processes=workers,
                initializer=initialize_validation_worker,
                initargs=(
                    str(minimum_rating),
                    str(maximum_rating) if maximum_rating is not None else None,
                ),
            ) as pool,
        ):
            for status, record in pool.imap(validate_source_line, source, chunksize=256):
                bucket.source_records_read += 1
                if status == "malformed":
                    bucket.malformed_rejected += 1
                    continue
                if status == "invalid_givens":
                    bucket.invalid_givens_rejected += 1
                    continue
                if status == "unsolved":
                    bucket.unsolved_rejected += 1
                    continue
                if status == "multiple":
                    bucket.multi_solution_rejected += 1
                    continue
                if status != "valid" or record is None:
                    raise RuntimeError(f"Unexpected validator status: {status}")
                if record.fingerprint in seen_fingerprints:
                    bucket.duplicate_rejected += 1
                    continue
                seen_fingerprints.add(record.fingerprint)
                bucket.valid_records += 1
                if len(selected) < bucket.requested:
                    selected.append(record)

        bucket.records_selected = len(selected)
        bucket.shortfall = max(0, bucket.requested - bucket.records_selected)
        asset_path = output_root / f"{difficulty.lower()}.sdk"
        bucket.asset_bytes, bucket.asset_sha256 = write_asset(
            asset_path, difficulty_code, selected
        )
        report[difficulty] = bucket
        print(
            f"{difficulty}: read={bucket.source_records_read} malformed={bucket.malformed_rejected} "
            f"invalid={bucket.invalid_givens_rejected} unsolved={bucket.unsolved_rejected} "
            f"multiple={bucket.multi_solution_rejected} duplicate={bucket.duplicate_rejected} "
            f"valid={bucket.valid_records} selected={bucket.records_selected} "
            f"shortfall={bucket.shortfall}"
        )
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-root", required=True, type=Path)
    parser.add_argument(
        "--output-root",
        type=Path,
        default=Path("app/src/main/assets/sudoku/v1"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("datasets/sudoku/import-report-v1.json"),
    )
    parser.add_argument(
        "--limit",
        action="append",
        default=[],
        metavar="DIFFICULTY=COUNT",
        help="Override a centralized V1 per-difficulty limit",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=min(4, os.cpu_count() or 1),
        help="Parallel validation workers (default: up to 4)",
    )
    args = parser.parse_args()

    try:
        if args.workers <= 0:
            raise argparse.ArgumentTypeError("--workers must be positive")
        limits = parse_limits(args.limit)
        verify_upstream_revision(args.upstream_root, UPSTREAM_REVISION)
        buckets = import_dataset(args.upstream_root, args.output_root, limits, args.workers)
    except (argparse.ArgumentTypeError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    report = {
        "datasetVersion": DATASET_VERSION,
        "importerVersion": IMPORTER_VERSION,
        "importDate": date.today().isoformat(),
        "upstreamRepository": UPSTREAM_REPOSITORY,
        "upstreamRevision": UPSTREAM_REVISION,
        "selection": "first N valid globally unique records in upstream file order; assets sorted by SHA-256 fingerprint",
        "limits": limits,
        "workers": args.workers,
        "buckets": {difficulty: asdict(bucket) for difficulty, bucket in buckets.items()},
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"report={args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
