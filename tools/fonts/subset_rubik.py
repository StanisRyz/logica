#!/usr/bin/env python3
"""Subsets the Rubik fonts to the characters the game can show.

The full Google Fonts files stay in tools/fonts/source/; this writes the subsets the app ships to
shared-ui/src/commonMain/composeResources/font/. Rerun it after adding text instead of editing the
outputs. The character set is every character of every strings.xml (all modules, all languages),
every non-ASCII character in the Kotlin sources, and base ranges: Basic Latin, Latin-1, Latin
Extended-A (Turkish), Cyrillic, digits, and common punctuation. Layout features and hinting stay.

Requires fontTools (pip install fonttools). Run from the repository root:
    python3 tools/fonts/subset_rubik.py
"""
import glob
import os
import sys
import xml.etree.ElementTree as ElementTree

from fontTools import subset

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SOURCE = os.path.join(ROOT, "tools", "fonts", "source")
OUTPUT = os.path.join(ROOT, "shared-ui", "src", "commonMain", "composeResources", "font")
WEIGHTS = ["regular", "medium", "semibold", "bold"]

BASE_RANGES = [
    (0x0020, 0x007E),  # Basic Latin
    (0x00A0, 0x00FF),  # Latin-1 Supplement
    (0x0100, 0x017F),  # Latin Extended-A: ğ ı İ ş and the rest
    (0x0400, 0x045F),  # Cyrillic, ё included
    (0x2010, 0x2027),  # dashes, quotes, bullet, ellipsis
    (0x2030, 0x203A),  # per mille, primes, angle quotes
    (0x20BD, 0x20BD),  # rouble sign
    (0x2116, 0x2116),  # numero sign
    (0x2212, 0x2212),  # minus sign
]


def string_resource_files():
    patterns = [
        "shared-ui/src/*/composeResources/values*/strings.xml",
        "web-app/src/*/composeResources/values*/strings.xml",
        "app/src/main/res/values*/strings.xml",
    ]
    return sorted(path for pattern in patterns for path in glob.glob(os.path.join(ROOT, pattern)))


def characters_in_strings():
    characters = set()
    for path in string_resource_files():
        for element in ElementTree.parse(path).getroot().iter():
            for text in (element.text, element.tail):
                if text:
                    characters.update(text.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"'))
    return characters


def characters_in_kotlin():
    characters = set()
    for module in ["shared-ui", "web-app", "app"]:
        for path in glob.glob(os.path.join(ROOT, module, "src", "*Main", "**", "*.kt"), recursive=True):
            with open(path, encoding="utf-8") as source:
                characters.update(c for c in source.read() if ord(c) > 0x7E)
    return characters


def wanted_codepoints():
    codepoints = {code for low, high in BASE_RANGES for code in range(low, high + 1)}
    codepoints.update(ord(c) for c in characters_in_strings() | characters_in_kotlin() if c.isprintable() and not c.isspace())
    return sorted(codepoints)


def main():
    codepoints = wanted_codepoints()
    for weight in WEIGHTS:
        source = os.path.join(SOURCE, f"rubik_{weight}.ttf")
        output = os.path.join(OUTPUT, f"rubik_{weight}.ttf")
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.notdef_outline = True
        options.hinting = True
        font = subset.load_font(source, options)
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=codepoints)
        subsetter.subset(font)
        subset.save_font(font, output, options)
        print(f"{os.path.relpath(output, ROOT)}: {os.path.getsize(source)} -> {os.path.getsize(output)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
