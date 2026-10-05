"""Fails when a release APK calls Java 21 SequencedCollection methods on lists (absent before API 35).

Kotlin binds `removeFirst()`/`removeLast()` (and friends) on a MutableList to these Java 21 members
when a module compiles against android.jar 35+; R8 merely outlines such a call, so on Android 8-14 it
throws NoSuchMethodError at run time. ArrayDeque and LinkedList have had these methods for ages and
are not flagged. Usage: python3 check_dex_api.py <apk> [<dexdump>]
"""

import os
import re
import subprocess
import sys
import tempfile
import zipfile

apk = sys.argv[1]
dexdump = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.environ.get("ANDROID_HOME", "/opt/android-sdk"), "build-tools", "36.0.0", "dexdump"
)
FORBIDDEN = re.compile(
    r"invoke-\w+(?:/range)? \{[^}]*\}, "
    r"Ljava/util/(?:List|ArrayList|Collection|AbstractList|AbstractCollection|SequencedCollection|Vector|"
    r"Stack|CopyOnWriteArrayList|Collections\$\w+);"
    r"\.(?:removeFirst|removeLast|getFirst|getLast|addFirst|addLast|reversed):"
)
METHOD = re.compile(r"\|\[[0-9a-f]+\] (\S+)")

offenders = []
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as folder:
    dexes = [name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
    if not dexes:
        sys.exit(f"{apk} contains no dex files")
    for name in dexes:
        path = archive.extract(name, folder)
        method = "?"
        dump = subprocess.run([dexdump, "-d", path], check=True, capture_output=True).stdout.decode("utf-8", "replace")
        for line in dump.splitlines():
            found = METHOD.search(line)
            if found:
                method = found.group(1)
            elif FORBIDDEN.search(line):
                offenders.append(f"{name}: {method}: {line.split('|', 1)[-1].strip()}")

if offenders:
    print("Java 21 List methods called (missing on Android API < 35):")
    print("\n".join(offenders))
    print("Use removeAt(lastIndex)/removeAt(0)/first()/last()/asReversed() instead. See R8 mapping.txt for names.")
    sys.exit(1)
print(f"{apk}: no Java 21 List calls in {len(dexes)} dex file(s)")
