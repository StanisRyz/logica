#!/usr/bin/env python3
"""Build the candidate Nonogram picture library from open filled icon fonts (developer-only).

Sources (see datasets/nonogram/PROVENANCE.md), all SHA-256 pinned npm tarballs:
- Phosphor Icons 2.1 "fill" weight (MIT) — the `@phosphor-icons/web` font for the glyphs and
  `@phosphor-icons/core` for names and categories;
- Material Symbols Outlined (Apache-2.0) — the `@material-symbols/font-400` variable font, instanced
  at FILL=1 with fontTools; only the hand-picked picture names in MATERIAL_PICTURES are used, after
  every Phosphor picture of the same size.

Each icon is drawn large, cropped to its ink, centred in a square, averaged down to the grid, and
thresholded at half coverage. A picture is kept only when:

- it is a picture-worthy icon (Phosphor: category allowlist, interface and variant names left out;
  Material: listed in MATERIAL_PICTURES);
- it is not on the manual left-out list (sensitive symbols, alcohol, trademarks, media controls);
- at 10x10 it is not a blob: fragmented into specks, a plain mass, or a noisy edge (see `blob_reason`),
  and not on the by-eye list of 10x10 pictures that do not read;
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
MATERIAL_TARBALL = "font-400-0.47.6.tgz"
MATERIAL_SHA256 = "0637d16dc9d642abc8d6ed35cc1cc77fb683ae4ebafae06bbeef506c599e961d"
MATERIAL_FONT = "package/material-symbols-outlined.woff2"
MATERIAL_PREFIX = "ms:"

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
    # Alcohol, tobacco, and gambling (owner decision, stage 10.2a).
    "beer-bottle", "beer-stein", "brandy", "champagne", "cheers", "martini", "wine", "pint-glass", "pipe", "poker-chip",
}

# Material Symbols names that are pictures (objects, animals, food, transport, sports, places), picked
# by hand from the font's list; no alcohol, tobacco, weapons, religion, or interface glyphs.
MATERIAL_PICTURES = (
    "agriculture", "airport_shuttle", "anchor", "apartment", "backpack", "bakery_dining", "balcony", "bathtub",
    "beach_access", "bed", "bedtime", "blender", "boat_bus", "brick", "brush", "build", "bungalow", "cabin",
    "cable_car", "cake", "candle", "carpenter", "chair", "chair_umbrella", "chalet", "chef_hat", "chess_bishop",
    "chess_king", "chess_knight", "chess_pawn", "chess_queen", "chess_rook", "cloud", "coffee", "construction",
    "cookie", "cooking", "crib", "crown", "cruelty_free", "cyclone", "deck", "dentistry", "desk", "diamond", "dining",
    "dishwasher", "door_front", "doorbell", "downhill_skiing", "dresser", "drone", "eco", "egg", "electric_bike",
    "electric_car", "electric_moped", "electric_rickshaw", "electric_scooter", "emoji_food_beverage", "emoji_nature",
    "extension", "eyeglasses", "factory", "faucet", "favorite", "fence", "festival", "fire_extinguisher",
    "fire_hydrant", "fire_truck", "flatware", "flight", "flight_takeoff", "forest", "fort", "front_hand",
    "front_loader", "funicular", "garage", "garden_cart", "gate", "glass_cup", "globe", "golf_course", "gondola_lift",
    "grass", "hanami_dango", "hardware", "helicopter", "hiking", "hive", "holiday_village", "home",
    "home_repair_service", "hot_tub", "hourglass", "house", "houseboat", "ice_skating", "icecream", "iron", "jewelry",
    "kayaking", "kettle", "king_bed", "kitesurfing", "lightbulb", "luggage", "lunch_dining", "microwave", "monorail",
    "moon_stars", "mop", "moped", "motorcycle", "museum", "nature", "nutrition", "oil_barrel", "onsen",
    "outdoor_grill", "oven", "owl", "package", "palette", "paragliding", "park", "pedal_bike", "pergola",
    "pest_control_rodent", "pets", "piano", "plumbing", "pool", "propane_tank", "radio", "raven", "restaurant",
    "rice_bowl", "robot", "rocket", "rocket_launch", "roller_skating", "rowing", "sailing", "satellite", "scooter",
    "scuba_diving", "shoe_cleats", "skateboarding", "skillet", "sledding", "smart_toy", "snail", "snowboarding",
    "snowflake", "snowshoeing", "soap", "soup_kitchen", "spa", "sports_baseball", "sports_basketball",
    "sports_cricket", "sports_esports", "sports_football", "sports_golf", "sports_gymnastics", "sports_handball",
    "sports_motorsports", "sports_rugby", "sports_soccer", "sports_tennis", "sports_volleyball", "sprinkler",
    "stadium", "stairs", "star", "stethoscope", "store", "storefront", "stroller", "sunny", "surfing",
    "takeout_dining", "thunderstorm", "toast", "tornado", "toys", "train", "tram", "trolley", "trophy", "tsunami",
    "tv", "two_wheeler", "umbrella", "vacuum", "villa", "volcano", "warehouse", "watch", "water_bottle", "water_drop",
    "waves", "waving_hand", "weekend", "wheat", "window", "yakitori", "yard",
)

# 10x10 pictures that pass the blob rule but still do not read as their subject, decided by eye on
# the review sheet (owner decision, stage 10.2a: when in doubt, leave it out).
MANUAL_UNREADABLE_10 = {
    "air-traffic-control", "archive", "bank", "baseball-helmet", "basket", "beach-ball", "belt", "bicycle",
    "book", "book-bookmark", "bookmarks", "books", "brain", "briefcase-metal", "broom", "building-apartment",
    "buildings", "bulldozer", "camera-rotate", "cardholder", "cash-register", "chalkboard-teacher", "charging-station", "city",
    "coffee-bean", "coins", "compass", "court-basketball", "crown-cross", "desk", "detective", "dresser",
    "engine", "exam", "face-mask", "factory", "fan", "film-reel", "fire-extinguisher", "fire-truck",
    "flag-banner", "flag-banner-fold", "flower-lotus", "flying-saucer", "football", "footprints", "four-k", "gas-can",
    "grains", "hair-dryer", "hand-deposit", "hand-eye", "hand-grabbing", "hand-soap", "hand-withdraw", "headlights",
    "hoodie", "hospital", "joystick", "lectern", "log", "megaphone", "metronome", "money",
    "money-wavy", "newspaper", "newspaper-clipping", "nut", "park", "plug-charging", "police-car", "projector-screen",
    "receipt", "road-horizon", "rug", "seat", "seatbelt", "shipping-container", "shirt-folded", "sneaker-move",
    "speaker-hifi", "stool", "suitcase-rolling", "sun-horizon", "thermometer-cold", "toolbox", "tractor", "train-regional",
    "tram", "treasure-chest", "truck-trailer", "vinyl-record", "wallet", "warehouse", "wave-triangle", "wind",
    "ms:airport_shuttle", "ms:bakery_dining", "ms:bungalow", "ms:cable_car", "ms:chalet", "ms:chef_hat",
    "ms:cookie", "ms:crib", "ms:desk", "ms:doorbell", "ms:dresser", "ms:drone",
    "ms:factory", "ms:fire_extinguisher", "ms:fire_hydrant", "ms:fire_truck", "ms:forest", "ms:fort", "ms:front_hand",
    "ms:front_loader", "ms:gate", "ms:glass_cup", "ms:hardware", "ms:home_repair_service", "ms:ice_skating",
    "ms:kitesurfing", "ms:lunch_dining", "ms:monorail", "ms:mop", "ms:nutrition", "ms:palette", "ms:pergola",
    "ms:plumbing", "ms:propane_tank", "ms:radio", "ms:raven", "ms:rice_bowl", "ms:shoe_cleats",
    "ms:smart_toy", "ms:spa", "ms:sports_baseball", "ms:sports_esports", "ms:sports_motorsports", "ms:store", "ms:tram",
    "ms:villa", "ms:warehouse", "ms:weekend",
}

# The blob rule (10x10 only): a picture is out when it falls apart into specks or many parts, is a
# plain mass with no inner detail, or has an edge so ragged that no shape is left.
BLOB_SIZE = 10
MAX_SPECKS, SPECK_CELLS, MAX_PARTS = 1, 2, 3
PLAIN_MASS_BOX_FILL, PLAIN_MASS_COMPACTNESS = 0.8, 1.35
MAX_COMPACTNESS = 2.6


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


def filled_parts(picture: list[bool], size: int) -> list[int]:
    """Sizes of the orthogonally connected groups of filled cells."""
    seen: set[int] = set()
    parts = []
    for start in range(size * size):
        if not picture[start] or start in seen:
            continue
        seen.add(start)
        stack, count = [start], 0
        while stack:
            cell = stack.pop()
            count += 1
            row, column = divmod(cell, size)
            for r, c in ((row - 1, column), (row + 1, column), (row, column - 1), (row, column + 1)):
                if 0 <= r < size and 0 <= c < size and picture[r * size + c] and r * size + c not in seen:
                    seen.add(r * size + c)
                    stack.append(r * size + c)
        parts.append(count)
    return parts


def has_hole(picture: list[bool], size: int) -> bool:
    """Whether some empty cell is enclosed, that is, not connected to the border through empty cells."""
    outside = {cell for cell in range(size * size) if not picture[cell] and (cell < size or cell >= size * (size - 1) or cell % size in (0, size - 1))}
    stack = list(outside)
    while stack:
        row, column = divmod(stack.pop(), size)
        for r, c in ((row - 1, column), (row + 1, column), (row, column - 1), (row, column + 1)):
            cell = r * size + c
            if 0 <= r < size and 0 <= c < size and not picture[cell] and cell not in outside:
                outside.add(cell)
                stack.append(cell)
    return sum(not cell for cell in picture) > len(outside)


def blob_reason(picture: list[bool], size: int) -> str | None:
    """Why a small picture is a blob rather than a readable silhouette, or None.

    - SPECKS / PARTS: it falls apart into more than one speck of at most two cells, or into more than
      three groups — the eye no longer joins them into one subject;
    - PLAIN_MASS: it fills at least 80 % of its bounding box with a short, smooth edge and no hole —
      a block or a disc without any detail;
    - RAGGED: its edge is so long for its area (edge cells / 4 sqrt(area) at least 2.6, the noisiest
      tenth of the 10.2 set) that no outline is left.
    """
    parts = filled_parts(picture, size)
    if sum(part <= SPECK_CELLS for part in parts) > MAX_SPECKS:
        return "SPECKS"
    if len(parts) > MAX_PARTS:
        return "PARTS"
    area = sum(picture)
    edge = 0
    for cell in range(size * size):
        if picture[cell]:
            row, column = divmod(cell, size)
            for r, c in ((row - 1, column), (row + 1, column), (row, column - 1), (row, column + 1)):
                edge += not (0 <= r < size and 0 <= c < size and picture[r * size + c])
    compactness = edge / (4 * area**0.5)
    rows = [r for r in range(size) if any(picture[r * size:(r + 1) * size])]
    columns = [c for c in range(size) if any(picture[r * size + c] for r in range(size))]
    box_fill = area / ((rows[-1] - rows[0] + 1) * (columns[-1] - columns[0] + 1))
    if box_fill >= PLAIN_MASS_BOX_FILL and compactness <= PLAIN_MASS_COMPACTNESS and not has_hole(picture, size):
        return "PLAIN_MASS"
    if compactness >= MAX_COMPACTNESS:
        return "RAGGED"
    return None


def material_font(sources: Path) -> tuple[ImageFont.FreeTypeFont, dict[str, int]]:
    """The Material Symbols font instanced at FILL=1, and the codepoint of every listed picture name."""
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer

    variable = TTFont(io.BytesIO(read_member(sources / MATERIAL_TARBALL, MATERIAL_SHA256, MATERIAL_FONT)))
    codepoints: dict[str, int] = {}
    for codepoint, glyph in sorted(variable.getBestCmap().items()):
        codepoints.setdefault(glyph, codepoint)
    missing = [name for name in MATERIAL_PICTURES if name not in codepoints]
    if missing:
        raise RuntimeError(f"Material Symbols has no glyph for {missing}")
    filled = instancer.instantiateVariableFont(variable, {"FILL": 1.0})
    filled.flavor = None
    buffer = io.BytesIO()
    filled.save(buffer)
    return ImageFont.truetype(io.BytesIO(buffer.getvalue()), 512), {name: codepoints[name] for name in MATERIAL_PICTURES}


def build(sources: Path) -> tuple[dict[str, list[dict[str, object]]], list[dict[str, object]]]:
    font_bytes = read_member(sources / WEB_TARBALL, WEB_SHA256, "package/src/fill/Phosphor-Fill.ttf")
    core_js = read_member(sources / CORE_TARBALL, CORE_SHA256, "package/dist/index.mjs").decode("utf-8")
    font = ImageFont.truetype(io.BytesIO(font_bytes), 512)
    phosphor = [(name, font, codepoint) for name, categories, codepoint in sorted(icon_entries(core_js)) if picture_worthy(name, categories)]
    material, material_codepoints = material_font(sources)
    # Phosphor first, so a Material icon only fills a gap; within each source, alphabetical.
    candidates = phosphor + [(MATERIAL_PREFIX + name, material, material_codepoints[name]) for name in sorted(MATERIAL_PICTURES)]

    blobs: list[dict[str, object]] = []
    by_size: dict[int, list[dict[str, object]]] = {}
    for size in sorted(set(SIZES.values())):
        kept: list[dict[str, object]] = []
        limit = max(2, round(NEAR_DUPLICATE_SHARE * size * size))
        for name, source_font, codepoint in candidates:
            picture = rasterize(source_font, codepoint, size)
            if picture is None:
                continue
            fill = sum(picture) / len(picture)
            if not MIN_FILL <= fill <= MAX_FILL or is_framed(picture, size):
                continue
            if size == BLOB_SIZE:
                reason = "BY_EYE" if name in MANUAL_UNREADABLE_10 else blob_reason(picture, size)
                if reason is not None:
                    blobs.append({"key": name, "cells": picture, "reason": reason})
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
    return {difficulty: library[difficulty] for difficulty in SIZES}, blobs


def write_library(root: Path, library: dict[str, list[dict[str, object]]], blobs: list[dict[str, object]]) -> None:
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
    summary["left_out_blobs_10x10"] = {
        reason: sorted(str(blob["key"]) for blob in blobs if blob["reason"] == reason) for reason in sorted({str(blob["reason"]) for blob in blobs})
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8", newline="\n")


def draw_sheet(path: Path, title: str, size: int, pictures: list[dict[str, object]], label) -> None:
    """A review sheet: every picture in a grid with a short label under it."""
    cell = max(4, 90 // size)
    columns = 12
    width, height = size * cell + 14, size * cell + 26
    rows = max(1, (len(pictures) + columns - 1) // columns)
    sheet = Image.new("L", (columns * width, rows * height + 24), 255)
    draw = ImageDraw.Draw(sheet)
    draw.text((6, 6), title, fill=0)
    for number, picture in enumerate(pictures, start=1):
        x = ((number - 1) % columns) * width + 7
        y = ((number - 1) // columns) * height + 28
        cells = picture["cells"]
        for r in range(size):
            for c in range(size):
                if cells[r * size + c]:
                    draw.rectangle([x + c * cell, y + r * cell, x + (c + 1) * cell - 1, y + (r + 1) * cell - 1], fill=0)
        draw.rectangle([x - 1, y - 1, x + size * cell, y + size * cell], outline=180)
        draw.text((x, y + size * cell + 3), label(number, picture)[:20], fill=0)
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path, optimize=True)


def write_sheets(directory: Path, library: dict[str, list[dict[str, object]]], blobs: list[dict[str, object]]) -> None:
    """One PNG per difficulty with every picture, its number, and key, plus the 10x10 blobs left out."""
    for difficulty, pictures in library.items():
        size = SIZES[difficulty]
        draw_sheet(
            directory / f"{difficulty}-{size}x{size}.png",
            f"{difficulty}: {size}x{size}, {len(pictures)} pictures (ms: = Material Symbols)",
            size,
            pictures,
            lambda number, picture: f"{number} {picture['key']}",
        )
    draw_sheet(
        directory / f"left-out-blobs-{BLOB_SIZE}x{BLOB_SIZE}.png",
        f"{BLOB_SIZE}x{BLOB_SIZE} left out as blobs: {len(blobs)} (reason, key)",
        BLOB_SIZE,
        blobs,
        lambda number, picture: f"{str(picture['reason'])[:5]} {picture['key']}",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--sheets", type=Path, help="Write the review sheets here (PNG per difficulty).")
    arguments = parser.parse_args()
    library, blobs = build(arguments.sources)
    write_library(arguments.project_root, library, blobs)
    if arguments.sheets:
        write_sheets(arguments.sheets, library, blobs)
    for difficulty, pictures in library.items():
        print(f"{difficulty} {SIZES[difficulty]}x{SIZES[difficulty]}: {len(pictures)} pictures")


if __name__ == "__main__":
    main()
