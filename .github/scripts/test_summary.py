"""Prints every test target's counts and the full failure of any failing test (JUnit XML reports)."""

import glob
import os
import xml.etree.ElementTree as ElementTree

totals = {}
failures = []
for path in sorted(glob.glob("**/build/test-results/**/*.xml", recursive=True)):
    target = os.path.dirname(path)
    suite = ElementTree.parse(path).getroot()
    count = totals.setdefault(target, [0, 0, 0])
    count[0] += int(suite.get("tests", 0))
    count[1] += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    count[2] += int(suite.get("skipped", 0))
    for case in suite.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            failures.append((f"{case.get('classname')}.{case.get('name')}", problem.text or problem.get("message", "")))

for target, (tests, failed, skipped) in totals.items():
    print(f"{target}: tests={tests} failed={failed} skipped={skipped}")
for name, text in failures:
    print(f"\n=== FAILED {name}\n{text.strip()[:6000]}")
