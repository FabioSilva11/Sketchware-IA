#!/usr/bin/env python3
"""Checks that every built-in library's resources compile and link with an old aapt2.

The aapt2 binaries in app/src/main/assets/aapt predate compact resource entries. On the PC,
build-tools 30.0.3's aapt2 (even older: it doesn't know <macro> either) stands in for them.
This mirrors the on-device build: `aapt2 compile --dir res` per library, then one
`aapt2 link` against assets/libs/android.jar.zip with every library package passed to
--extra-packages. Finally it checks that every R field the libraries' bytecode reads is
declared in the generated R classes.

Usage: python tools/builtin-libs/verify_resources.py [--aapt2 path/to/aapt2]
"""

import argparse
import os
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from build_builtin_libs import ASSETS_LIBS, BUILT_IN_LIBRARIES_JAVA, android_home


def library_packages():
    """name -> package for libraries that BuiltInLibraries.java marks as having resources."""
    source = BUILT_IN_LIBRARIES_JAVA.read_text(encoding="utf-8")
    constants = dict(re.findall(r'public static String (\w+) = "([^"]+)";', source))
    return {constants[c]: p for c, p in re.findall(r'new BuiltInLibrary\((\w+), List\.of\([^)]*\), "([^"]+)"\)', source)}


def r_field_references(classes_jar):
    """{("pkg/R$type", "field")} for every R field the jar's bytecode reads."""
    references = set()
    with zipfile.ZipFile(classes_jar) as jar:
        for name in jar.namelist():
            if not name.endswith(".class") or name.startswith("META-INF/"):
                continue
            data = jar.read(name)
            count = struct.unpack_from(">H", data, 8)[0]
            pool, offset, index = {}, 10, 1
            while index < count:
                tag = data[offset]
                if tag == 1:
                    length = struct.unpack_from(">H", data, offset + 1)[0]
                    pool[index] = data[offset + 3:offset + 3 + length].decode("utf-8", "replace")
                    offset += 3 + length
                elif tag in (7, 8, 16, 19, 20):
                    pool[index] = struct.unpack_from(">H", data, offset + 1)[0]
                    offset += 3
                elif tag == 15:
                    offset += 4
                elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                    pool[index] = (tag,) + struct.unpack_from(">HH", data, offset + 1)
                    offset += 5
                elif tag in (5, 6):
                    offset += 9
                    index += 1
                else:
                    raise ValueError(f"{classes_jar}!{name}: unknown constant pool tag {tag}")
                index += 1
            for entry in pool.values():
                if isinstance(entry, tuple) and entry[0] == 9:  # CONSTANT_Fieldref
                    owner = pool[pool[entry[1]]]
                    if owner.rsplit("/", 1)[-1].startswith("R$"):
                        references.add((owner, pool[pool[entry[2]][1]]))
    return references


def generated_r_fields(gen):
    """{("pkg/R$type", "field")} declared by the R.java files aapt2 generated."""
    fields = set()
    for r_java in gen.rglob("R.java"):
        text = r_java.read_text(encoding="utf-8")
        package = re.search(r"^package ([\w.]+);", text, re.M).group(1).replace(".", "/")
        current = None
        for line in text.splitlines():
            nested = re.match(r"\s*public static final class (\w+)", line)
            if nested:
                current = nested.group(1)
            field = re.match(r"\s*public static (?:final )?int(?:\[\])? (\w+)", line)
            if field and current:
                fields.add((f"{package}/R${current}", field.group(1)))
    return fields


def run(command):
    result = subprocess.run(command, capture_output=True, text=True)
    output = (result.stdout + result.stderr).strip()
    return result.returncode == 0 and "error" not in output.lower(), output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aapt2", default=str(android_home() / "build-tools/30.0.3" / ("aapt2.exe" if os.name == "nt" else "aapt2")))
    aapt2 = parser.parse_args().aapt2

    work = Path(tempfile.mkdtemp(prefix="verify-res-"))
    with zipfile.ZipFile(ASSETS_LIBS / "android.jar.zip") as archive:
        archive.extractall(work)
    with zipfile.ZipFile(ASSETS_LIBS / "libs.zip") as archive:
        archive.extractall(work / "libs")

    packages = library_packages()
    compiled = []
    failed = False
    for name, package in sorted(packages.items()):
        res = work / "libs" / name / "res"
        if not res.is_dir():
            print(f"MISSING res/ for {name}")
            failed = True
            continue
        output = work / "compiled" / f"{name}.zip"
        output.parent.mkdir(exist_ok=True)
        ok, log = run([aapt2, "compile", "--dir", str(res), "-o", str(output)])
        if not ok:
            failed = True
            print(f"COMPILE FAILED {name}\n{log}\n")
        else:
            compiled.append(output)

    manifest = work / "AndroidManifest.xml"
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.verify">'
                        '<application android:theme="@style/Theme.Material3.DayNight.NoActionBar"/></manifest>', encoding="utf-8")
    gen = work / "gen"
    gen.mkdir()
    command = [aapt2, "link", "--allow-reserved-package-id", "--auto-add-overlay", "--no-version-vectors",
               "--no-version-transitions", "--min-sdk-version", "23", "--target-sdk-version", "36",
               "-I", str(work / "android.jar"), "--manifest", str(manifest), "--java", str(gen),
               "--extra-packages", ":".join(sorted(set(packages.values()))), "-o", str(work / "out.apk")]
    for archive in compiled:
        command += ["-R", str(archive)]
    ok, log = run(command)
    print(("LINK OK" if ok else "LINK FAILED") + (f"\n{log}" if log else ""))
    failed |= not ok

    if ok:
        declared = generated_r_fields(gen)
        missing = {}
        for classes_jar in sorted((work / "libs").glob("*/classes.jar")):
            for owner, field in r_field_references(classes_jar):
                if (owner, field) not in declared:
                    missing.setdefault(classes_jar.parent.name, set()).add(f"{owner.replace('/', '.')}.{field}")
        for library, fields in sorted(missing.items()):
            failed = True
            listed = sorted(fields)
            print(f"MISSING R FIELDS used by {library} ({len(listed)}): {listed[:6]}{' ...' if len(listed) > 6 else ''}")
        if not missing:
            print("Every R field referenced by library bytecode is generated")

    print(f"{len(compiled)}/{len(packages)} libraries compiled; work directory: {work}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
