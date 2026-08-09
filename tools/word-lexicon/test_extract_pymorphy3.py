from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

import pymorphy3


MODULE_PATH = Path(__file__).with_name("extract_pymorphy3.py")
SPEC = importlib.util.spec_from_file_location("word_lexicon_extract", MODULE_PATH)
assert SPEC and SPEC.loader
EXTRACTOR = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = EXTRACTOR
SPEC.loader.exec_module(EXTRACTOR)


class PymorphyAdapterFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.analyzer = pymorphy3.MorphAnalyzer(lang="ru")

    def test_dictionary_noun_normalization_filters_and_supported_lengths(self) -> None:
        analyze = lambda word: EXTRACTOR.analyze_known_form(self.analyzer, word)

        self.assertEqual("школа", analyze("школа")[0].lemma)
        self.assertEqual("школа", analyze("школы")[0].lemma)
        self.assertEqual((), analyze("бежать"))
        self.assertEqual((), analyze("москва"))
        self.assertEqual((), analyze(" школа"))
        trousers = analyze("брюки")
        self.assertEqual("брюки", trousers[0].lemma)
        self.assertTrue(trousers[0].plural_only)
        for surface, expected_length in (("рука", 4), ("школа", 5), ("работа", 6), ("телефон", 7)):
            self.assertEqual(expected_length, len(analyze(surface)[0].lemma))


if __name__ == "__main__":
    unittest.main()
