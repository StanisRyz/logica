#!/usr/bin/env python3
"""Build the candidate Nonogram picture library from an open filled icon font (developer-only).

Source: Phosphor Icons 2.1 "fill" weight (MIT) — the `@phosphor-icons/web` font for the glyphs and
`@phosphor-icons/core` for names and categories, both SHA-256 pinned npm tarballs (see
datasets/nonogram/PROVENANCE.md). Each icon is drawn large, cropped to its ink, centred in a square,
averaged down to the grid, and thresholded at half coverage. A picture is kept only when:

- it is a picture-worthy icon (category allowlist, interface and variant names left out);
- it is not on the manual left-out list (sensitive symbols, trademarks, media controls);
- 25-72% of its cells are filled, and it is not a framed shape (three edges almost full);
- line logic alone solves it (the same algorithm as NonogramLineSolver), so it has one answer;
- no kept picture of the same size is equal to it or to its mirror, or differs in only a few cells.

Nothing here runs on a device or touches the game. Usage:

    python tools/nonogram/build_picture_library.py --sources <dir with the two .tgz files>
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import tarfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WEB_TARBALL = "web-2.1.2.tgz"
WEB_SHA256 = "40e3096099ca818c047979cece6a3764944d5200c67b64092f7bcd8bcd1d2b08"
CORE_TARBALL = "core-2.1.1.tgz"
CORE_SHA256 = "313332be6190b724da24107addd781799b48bf76b13963f24501112ffe1baadd"

# Grid size per difficulty. Hard and Expert share 15x15 and are split by line-logic effort.
SIZES = {"easy": 10, "medium": 12, "hard": 15, "expert": 15}
THRESHOLD = 0.5
MIN_FILL, MAX_FILL = 0.25, 0.72
NEAR_DUPLICATE_SHARE = 0.04

# Picture-worthy Phosphor categories; an icon also in a left-out category is skipped.
KEPT_CATEGORIES = {"NATURE", "OBJECTS", "GAMES", "HEALTH", "MAP", "COMMERCE", "WEATHER", "MEDIA", "PEOPLE", "FINANCE", "OFFICE"}
LEFT_OUT_CATEGORIES = {"ARROWS", "BRAND", "EDITOR", "DEVELOPMENT", "DESIGN"}
# Interface glyphs, overlays, and variants that read as noise once rasterized.
LEFT_OUT_NAMES = re.compile(
    r"slash|simple|-plus|-minus|-check|-x\b|-dashed|-dotted|square|circle|text|number|file|folder|chart|battery|wifi|"
    r"cell-|signal|user|identification|list|bracket|toggle|radio|checks?\b|caret|arrow|align|percent|currency|sort|"
    r"funnel|faders|sliders|code|terminal|cursor|seal|warning|info|question|prohibit|placeholder|selection|resize|"
    r"frame|crop|logo|grid|dots|columns|rows|split|corners|bounding|subtract|unite|intersect|exclude|layout|sidebar|"
    r"browser|app-window|article|textbox|table|kanban|tray|notification|chat|envelope-open|phone-(?:call|disconnect|incoming|outgoing|list|pause|plus|slash|transfer|x)|"
    r"smiley-(?!$)|-fill|-half|-light|-dark|-up\b|-down\b|-left\b|-right\b|-in\b|-out\b|-off\b|-on\b|-open|-closed|-simple|-line|-bold|-high|-low|-none|-alt"
)


# Icons that pass the automatic filters but do not belong in a family puzzle or do not read as a
# picture: religious and gender symbols, hazards and weapons, trademarks and video formats, media
# controls, and abstract interface shapes.
MANUAL_LEFT_OUT = {
    "church", "mosque", "synagogue", "star-of-david", "asclepius",
    "gender-female", "gender-male", "gender-intersex", "gender-neuter", "gender-nonbinary", "gender-transgender",
    "biohazard", "fallout-shelter", "nuclear-plant", "skull", "bomb", "virus",
    "lego", "finn-the-human", "trademark", "trademark-registered", "copyleft", "high-definition", "standard-definition",
    "closed-captioning", "subtitles", "virtual-reality", "gif",
    "pause", "play", "play-pause", "skip-back", "skip-forward", "fast-forward", "rewind", "eject", "record",
    "slideshow", "ranking", "signature", "scan-smiley", "cube-focus", "image", "image-broken", "images", "panorama",
    "device-mobile", "device-mobile-camera", "device-mobile-speaker", "devices", "broadcast", "equalizer",
    "wave-sawtooth", "wave-sine", "waveform", "pulse", "heartbeat", "presentation", "invoice", "certificate",
    "shield-chevron", "shield-star", "lock-laminated", "steps", "monitor-play", "video", "video-conference",
    "cigarette", "copyright", "lego-smiley", "airplay", "screencast", "crosshair", "contactless-payment",
}


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_member(tarball: Path, expected_sha: str, member: str) -> bytes:
    if sha256_of(tarball) != expected_sha:
        raise RuntimeError(f"{tarball} does not match its pinned SHA-256.")
    with tarfile.open(tarball) as archive:
        return archive.extractfile(member).read()


def icon_entries(core_js: str) -> list[tuple[str, set[str], int]]:
    """Name, Phosphor categories, and codepoint of every icon, in the package's order."""
    entries = []
    for block in re.split(r"\n  \{\n", core_js)[1:]:
        name = re.search(r'name: "([^"]+)"', block)
        categories = re.search(r"categories: \[([^\]]*)\]", block)
        codepoint = re.search(r"codepoint: (\d+)", block)
        if name and categories and codepoint:
            kinds = {item.strip().removeprefix("e.") for item in categories.group(1).split(",") if item.strip()}
            entries.append((name.group(1), kinds, int(codepoint.group(1))))
    return entries


def picture_worthy(name: str, categories: set[str]) -> bool:
    return (
        bool(categories & KEPT_CATEGORIES)
        and not categories & LEFT_OUT_CATEGORIES
        and not LEFT_OUT_NAMES.search(name)
        and name not in MANUAL_LEFT_OUT
    )


def rasterize(font: ImageFont.FreeTypeFont, codepoint: int, size: int) -> list[bool] | None:
    canvas = Image.new("L", (768, 768), 0)
    ImageDraw.Draw(canvas).text((128, 128), chr(codepoint), font=font, fill=255)
    box = canvas.getbbox()
    if box is None:
        return None
    ink = canvas.crop(box)
    side = max(ink.size)
    square = Image.new("L", (side, side), 0)
    square.paste(ink, ((side - ink.size[0]) // 2, (side - ink.size[1]) // 2))
    small = square.resize((size, size), Image.BOX)
    return [small.getpixel((column, row)) >= THRESHOLD * 255 for row in range(size) for column in range(size)]


def clue(line: list[bool]) -> list[int]:
    runs, run = [], 0
    for cell in line:
        if cell:
            run += 1
        elif run:
            runs.append(run)
            run = 0
    if run:
        runs.append(run)
    return runs


UNKNOWN, FILLED, EMPTY = 0, 1, 2


def solve_line(runs: list[int], line: list[int]) -> list[int] | None:
    """Port of NonogramLineSolver.solveLine: every cell all placements of the runs agree on."""
    length, count = len(line), len(runs)

    def can_place(start: int, run: int) -> bool:
        end = start + runs[run]
        if end > length or any(line[cell] == EMPTY for cell in range(start, end)):
            return False
        return end == length or line[end] != FILLED

    def after(start: int, run: int) -> int:
        return max(min(start + runs[run] + 1, length), min(start + runs[run], length))

    feasible = [[False] * (count + 1) for _ in range(length + 2)]
    for i in range(length, -1, -1):
        for k in range(count, -1, -1):
            if k == count:
                feasible[i][k] = all(line[cell] != FILLED for cell in range(i, length))
            else:
                feasible[i][k] = (i < length and line[i] != FILLED and feasible[i + 1][k]) or (
                    can_place(i, k) and feasible[after(i, k)][k + 1]
                )
    if not feasible[0][0]:
        return None
    can_fill, can_empty, seen, stack = [False] * length, [False] * length, set(), [(0, 0)]
    while stack:
        i, k = stack.pop()
        if (i, k) in seen:
            continue
        seen.add((i, k))
        if k == count:
            for cell in range(i, length):
                can_empty[cell] = True
            continue
        if i < length and line[i] != FILLED and feasible[i + 1][k]:
            can_empty[i] = True
            stack.append((i + 1, k))
        if can_place(i, k) and feasible[after(i, k)][k + 1]:
            for cell in range(i, i + runs[k]):
                can_fill[cell] = True
            if i + runs[k] < length:
                can_empty[i + runs[k]] = True
            stack.append((after(i, k), k + 1))
    return [FILLED if can_fill[c] and not can_empty[c] else EMPTY if can_empty[c] and not can_fill[c] else UNKNOWN for c in range(length)]


def solve(size: int, picture: list[bool]) -> dict[str, int] | None:
    """Line-logic solve; returns its effort (full row-and-column sweeps, line updates) or None if it stalls."""
    rows = [clue(picture[r * size:(r + 1) * size]) for r in range(size)]
    columns = [clue([picture[r * size + c] for r in range(size)]) for c in range(size)]
    board = [UNKNOWN] * (size * size)
    sweeps = updates = 0
    changed = True
    while changed:
        changed = False
        sweeps += 1
        for index in range(size):
            for is_row in (True, False):
                cells = [index * size + c for c in range(size)] if is_row else [r * size + index for r in range(size)]
                line = [board[cell] for cell in cells]
                if UNKNOWN not in line:
                    continue
                solved = solve_line(rows[index] if is_row else columns[index], line)
                if solved is None:
                    return None
                if solved != line:
                    updates += 1
                    changed = True
                    for cell, value in zip(cells, solved):
                        board[cell] = value
    if UNKNOWN in board:
        return None
    return {"sweeps": sweeps, "line_updates": updates}


def mirrored(picture: list[bool], size: int) -> list[bool]:
    return [picture[r * size + (size - 1 - c)] for r in range(size) for c in range(size)]


def is_framed(picture: list[bool], size: int) -> bool:
    edges = [
        picture[:size],
        picture[-size:],
        [picture[r * size] for r in range(size)],
        [picture[r * size + size - 1] for r in range(size)],
    ]
    return sum(sum(edge) >= 0.8 * size for edge in edges) >= 3


def build(sources: Path) -> dict[str, list[dict[str, object]]]:
    font_bytes = read_member(sources / WEB_TARBALL, WEB_SHA256, "package/src/fill/Phosphor-Fill.ttf")
    core_js = read_member(sources / CORE_TARBALL, CORE_SHA256, "package/dist/index.mjs").decode("utf-8")
    font = ImageFont.truetype(io.BytesIO(font_bytes), 512)
    candidates = [(name, codepoint) for name, categories, codepoint in icon_entries(core_js) if picture_worthy(name, categories)]

    by_size: dict[int, list[dict[str, object]]] = {}
    for size in sorted(set(SIZES.values())):
        kept: list[dict[str, object]] = []
        limit = max(2, round(NEAR_DUPLICATE_SHARE * size * size))
        for name, codepoint in sorted(candidates):
            picture = rasterize(font, codepoint, size)
            if picture is None:
                continue
            fill = sum(picture) / len(picture)
            if not MIN_FILL <= fill <= MAX_FILL or is_framed(picture, size):
                continue
            mirror = mirrored(picture, size)
            if any(
                sum(a != b for a, b in zip(picture, other["cells"])) <= limit or sum(a != b for a, b in zip(mirror, other["cells"])) <= limit
                for other in kept
            ):
                continue
            effort = solve(size, picture)
            if effort is None:
                continue
            kept.append({"key": name, "cells": picture, "fill": round(fill, 3), **effort})
        by_size[size] = kept

    library: dict[str, list[dict[str, object]]] = {}
    for size in set(SIZES.values()):
        difficulties = [difficulty for difficulty, value in SIZES.items() if value == size]
        if len(difficulties) == 1:
            library[difficulties[0]] = by_size[size]
            continue
        # A shared size is split by line-logic effort: the less effortful half goes to the easier difficulty.
        ranked = sorted(by_size[size], key=lambda p: (p["sweeps"], p["line_updates"], p["key"]))
        share = len(ranked) // len(difficulties)
        for index, difficulty in enumerate(difficulties):
            part = ranked[index * share:] if index == len(difficulties) - 1 else ranked[index * share:(index + 1) * share]
            library[difficulty] = sorted(part, key=lambda p: p["key"])
    return {difficulty: library[difficulty] for difficulty in SIZES}


def write_library(root: Path, library: dict[str, list[dict[str, object]]]) -> None:
    out = root / "datasets" / "nonogram" / "library-v1"
    out.mkdir(parents=True, exist_ok=True)
    summary = {}
    for difficulty, pictures in library.items():
        size = SIZES[difficulty]
        lines = [
            f"# Nonogram picture library V1 candidates, {difficulty}: {size}x{size}. Generated by tools/nonogram/build_picture_library.py; do not edit.",
            "# <key>\\t<rows top to bottom, '/' between rows, '#' filled>\\t<line-logic sweeps>",
        ]
        for picture in pictures:
            cells = picture["cells"]
            rows = "/".join("".join("#" if cells[r * size + c] else "." for c in range(size)) for r in range(size))
            lines.append(f"{picture['key']}\t{rows}\t{picture['sweeps']}")
        (out / f"{difficulty}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        sweeps = sorted(int(p["sweeps"]) for p in pictures)
        summary[difficulty] = {
            "size": size,
            "pictures": len(pictures),
            "sweeps_min": sweeps[0],
            "sweeps_median": sweeps[len(sweeps) // 2],
            "sweeps_max": sweeps[-1],
            "line_updates_median": sorted(int(p["line_updates"]) for p in pictures)[len(pictures) // 2],
        }
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_sheets(directory: Path, library: dict[str, list[dict[str, object]]]) -> None:
    """One PNG per difficulty: every picture with its number and key, for the owner's review."""
    directory.mkdir(parents=True, exist_ok=True)
    for difficulty, pictures in library.items():
        size = SIZES[difficulty]
        cell = max(4, 90 // size)
        columns = 12
        width, height = size * cell + 14, size * cell + 26
        rows = (len(pictures) + columns - 1) // columns
        sheet = Image.new("L", (columns * width, rows * height + 24), 255)
        draw = ImageDraw.Draw(sheet)
        draw.text((6, 6), f"{difficulty}: {size}x{size}, {len(pictures)} pictures", fill=0)
        for number, picture in enumerate(pictures, start=1):
            x = ((number - 1) % columns) * width + 7
            y = ((number - 1) // columns) * height + 28
            cells = picture["cells"]
            for r in range(size):
                for c in range(size):
                    if cells[r * size + c]:
                        draw.rectangle([x + c * cell, y + r * cell, x + (c + 1) * cell - 1, y + (r + 1) * cell - 1], fill=0)
            draw.rectangle([x - 1, y - 1, x + size * cell, y + size * cell], outline=180)
            draw.text((x, y + size * cell + 3), f"{number} {picture['key']}"[:20], fill=0)
        sheet.save(directory / f"{difficulty}-{size}x{size}.png", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--sheets", type=Path, help="Write the review sheets here (PNG per difficulty).")
    arguments = parser.parse_args()
    library = build(arguments.sources)
    write_library(arguments.project_root, library)
    if arguments.sheets:
        write_sheets(arguments.sheets, library)
    for difficulty, pictures in library.items():
        print(f"{difficulty} {SIZES[difficulty]}x{SIZES[difficulty]}: {len(pictures)} pictures")


if __name__ == "__main__":
    main()
