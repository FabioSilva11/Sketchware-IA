#!/usr/bin/env python3
"""Checks that every built-in library's classes can be loaded with just its dependencies.

Sketchware adds a library together with the dependencies BuiltInLibraries.java lists for
it, transitively. A class whose superclass or interfaces live outside that closure (and
outside android.jar) fails to load on the device with NoClassDefFoundError, so this reports
each such class. References inside method bodies are not checked: libraries often have
optional integrations that only run when the other library is present.

Usage: python tools/builtin-libs/verify_classes.py
"""

import io
import re
import struct
import sys
import zipfile

from build_builtin_libs import ASSETS_LIBS, BUILT_IN_LIBRARIES_JAVA

# Supertypes that are fine to miss, because the classes needing them only load when another
# library is in use anyway (the same holds for Gradle-built apps).
OPTIONAL_SUPERTYPES = {
    # Room's LiveData and Paging integrations
    "androidx/lifecycle/LiveData",
    "androidx/paging/PositionalDataSource",
    # Transition's support for androidx.fragment, which loads it itself
    "androidx/fragment/app/FragmentTransitionImpl",
    # kotlinx.coroutines' JVM debug agent
    "java/lang/instrument/ClassFileTransformer",
    # firebase-appcheck and firebase-datatransport only come in through Firebase libraries,
    # which all depend on firebase-common (and so firebase-components)
    "com/google/firebase/components/ComponentRegistrar",
}


def class_header(data):
    """(name, superclass, [interfaces]) of a class file."""
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
            offset += 5
        elif tag in (5, 6):
            offset += 9
            index += 1
        else:
            raise ValueError(f"unknown constant pool tag {tag}")
        index += 1
    _, this, superclass, interface_count = struct.unpack_from(">HHHH", data, offset)
    interfaces = [pool[pool[struct.unpack_from(">H", data, offset + 8 + 2 * i)[0]]] for i in range(interface_count)]
    return pool[pool[this]], (pool[pool[superclass]] if superclass else None), interfaces


def jar_classes(data):
    headers = {}
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        for name in jar.namelist():
            if name.endswith(".class") and not name.startswith("META-INF/") and not name.endswith("module-info.class"):
                this, superclass, interfaces = class_header(jar.read(name))
                headers[this] = [superclass] + interfaces
    return headers


def main():
    source = BUILT_IN_LIBRARIES_JAVA.read_text(encoding="utf-8")
    names = dict(re.findall(r'public static String (\w+) = "([^"]+)";', source))
    dependencies = {constant: re.findall(r"\w+", deps or "")
                    for constant, deps in re.findall(r"new BuiltInLibrary\((\w+)(?:, List\.of\(([^)]*)\))?", source)}

    with zipfile.ZipFile(ASSETS_LIBS / "android.jar.zip") as archive:
        platform = set(jar_classes(archive.read("android.jar")))
    with zipfile.ZipFile(ASSETS_LIBS / "libs.zip") as libs:
        classes = {constant: jar_classes(libs.read(f"{name}/classes.jar"))
                   for constant, name in names.items() if f"{name}/classes.jar" in libs.namelist()}

    def closure(constant):
        seen, stack = set(), [constant]
        while stack:
            current = stack.pop()
            if current not in seen:
                seen.add(current)
                stack.extend(dependencies.get(current, []))
        return seen

    failures = 0
    for constant in sorted(classes):
        available = set(platform)
        for library in closure(constant):
            available.update(classes.get(library, {}))
        missing = {}
        for name, supertypes in classes[constant].items():
            for supertype in supertypes:
                if supertype and supertype not in available and supertype not in OPTIONAL_SUPERTYPES:
                    missing.setdefault(supertype, []).append(name)
        if missing:
            failures += 1
            print(f"{names[constant]}: {len(missing)} missing supertypes")
            for supertype, users in sorted(missing.items())[:5]:
                print(f"    {supertype}  (needed by {users[0]}{' and others' if len(users) > 1 else ''})")
    print(f"{len(classes) - failures}/{len(classes)} libraries load with only their declared dependencies")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
