# Sudoku Dataset V1 provenance

- Upstream: Sudoku Exchange Puzzle Bank
- Repository: <https://github.com/grantm/sudoku-exchange-puzzle-bank>
- Revision: [`d8c8ebaee0c08c412cfba96af1923dfa61c83317`](https://github.com/grantm/sudoku-exchange-puzzle-bank/tree/d8c8ebaee0c08c412cfba96af1923dfa61c83317)
- License/status: the upstream puzzle dataset is [dedicated to the public domain](https://github.com/grantm/sudoku-exchange-puzzle-bank/blob/d8c8ebaee0c08c412cfba96af1923dfa61c83317/LICENSE.txt).
- Import date: 2026-08-11
- Dataset/importer version: 1 / 1

Difficulty mapping is direct: `easy.txt` -> `EASY`, `medium.txt` -> `MEDIUM`,
`hard.txt` -> `HARD`, and `diabolical.txt` -> `EXPERT`. The upstream tenths rating is retained.

| Difficulty | Source | Valid unique | Rejected | Selected |
| --- | ---: | ---: | ---: | ---: |
| EASY | 100,000 | 100,000 | 0 | 10,000 |
| MEDIUM | 352,643 | 352,643 | 0 | 10,000 |
| HARD | 321,592 | 321,592 | 0 | 10,000 |
| EXPERT | 119,681 | 119,681 | 0 | 10,000 |
| **Total** | **893,916** | **893,916** | **0** | **40,000** |

The offline importer strictly parses and normalizes every source record, checks the upstream record
hash/rating bucket, rejects conflicting givens, counts solutions up to two, verifies the unique
solution, and deduplicates globally by SHA-256 of the canonical 81 digits. It selects the first
configured number of valid records in upstream file order, then writes each asset sorted by the full
fingerprint. See `import-report-v1.json` for rejection categories and generated asset hashes.

Regenerate from a clean checkout at the pinned revision:

```text
python tools/sudoku/import_dataset_v1.py --upstream-root <local-checkout> --workers 4
```

Dataset V1 is frozen. Any upstream revision, mapping, selection, or record-content change requires a
new dataset version.
