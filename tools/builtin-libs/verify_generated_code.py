#!/usr/bin/env python3
"""Checks that code Sketchware generates still compiles against the bundled libraries.

Compiles, with the same ECJ version and flags the app uses on the device:
  * the helper classes Lx.java generates (RequestNetworkController/OkHttp,
    GoogleMapController/Maps, BluetoothConnect, FileUtil, ...), extracted from its source;
  * the debug classes in app/src/main/assets/debug;
  * smoke/GeneratedCodeSmoke.java, which mirrors what the generators emit per component.
The result is then dexed with D8, like the on-device D8 path does.

Usage: python tools/builtin-libs/verify_generated_code.py [--java-version 1.8]
Run build_builtin_libs.py first (it resolves the ECJ jar through Gradle).
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from build_builtin_libs import ASSETS_LIBS, BUILD_TOOLS_VERSION, PLATFORM, ROOT, TOOL_DIR, android_home

PACKAGE = "com.example.smoke"
LX = ROOT / "app/src/main/java/a/a/a/Lx.java"
# Lx methods that return a whole generated class built by plain string concatenation.
HELPER_GENERATORS = ["b", "c", "e", "f", "g", "h"]
JAVA_STRING = re.compile(r'"((?:\\.|[^"\\])*)"|\bpackageName\b')


def unescape(literal):
    return re.sub(r"\\u([0-9a-fA-F]{4})|\\(.)",
                  lambda m: chr(int(m.group(1), 16)) if m.group(1) else
                  {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f", "0": "\0"}.get(m.group(2), m.group(2)),
                  literal)


def generated_helper(source, method):
    start = source.index(f"public static String {method}(String packageName) {{")
    body = source[source.index("return", start):source.index(";\n    }", start)]
    return "".join(PACKAGE if m.group(0) == "packageName" else unescape(m.group(1))
                   for m in JAVA_STRING.finditer(body))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-version", default="1.8")
    arguments = parser.parse_args()

    graph = json.loads((TOOL_DIR / "build/builtin-libs.json").read_text(encoding="utf-8"))
    ecj = graph["onDeviceJavaCompiler"][0]
    sdk = android_home()

    work = Path(tempfile.mkdtemp(prefix="verify-code-"))
    with zipfile.ZipFile(ASSETS_LIBS / "android.jar.zip") as archive:
        archive.extractall(work)
    with zipfile.ZipFile(ASSETS_LIBS / "libs.zip") as archive:
        archive.extractall(work / "libs")

    sources = work / "src" / PACKAGE.replace(".", "/")
    sources.mkdir(parents=True)
    lx = LX.read_text(encoding="utf-8").replace("\r\n", "\n")
    for method in HELPER_GENERATORS:
        code = generated_helper(lx, method)
        name = re.search(r"public (?:final )?class (\w+)", code).group(1)
        (sources / f"{name}.java").write_text(code, encoding="utf-8")
    for debug_class in (ROOT / "app/src/main/assets/debug").glob("*.java"):
        code = debug_class.read_text(encoding="utf-8").replace("<?package_name?>", PACKAGE)
        code = code.replace("<?class_name_package?>", PACKAGE).replace("<?class_name?>", "SketchApplication")
        (sources / debug_class.name).write_text(code, encoding="utf-8")
    smoke = (TOOL_DIR / "smoke/GeneratedCodeSmoke.java").read_text(encoding="utf-8")
    (sources / "GeneratedCodeSmoke.java").write_text(smoke, encoding="utf-8")
    # Stand-ins for the remaining generated classes the snippets above use.
    (sources / "R.java").write_text(f"package {PACKAGE};\npublic final class R {{}}\n", encoding="utf-8")
    (sources / "SketchwareUtil.java").write_text(
        f"package {PACKAGE};\npublic class SketchwareUtil {{\n"
        "public static void showMessage(android.content.Context c, String s) {}\n}\n", encoding="utf-8")

    jars = sorted(str(p) for p in (work / "libs").glob("*/classes.jar"))
    classpath = os.pathsep.join([str(ASSETS_LIBS / "core-lambda-stubs.jar")] + jars)
    classes = work / "classes"
    print(f"Compiling {len(list(sources.glob('*.java')))} files with ECJ ({Path(ecj).name}, -{arguments.java_version})")
    # On the device ECJ has no JDK, so java.* comes from android.jar. On the PC that needs
    # -bootclasspath, which ECJ only accepts below Java 9; the API surface checked is the same.
    result = subprocess.run(["java", "-Xmx768m", "-jar", ecj, f"-{arguments.java_version}", "-nowarn", "-proc:none",
                             "-bootclasspath", str(work / "android.jar"),
                             "-d", str(classes), "-cp", classpath, str(work / "src")],
                            capture_output=True, text=True)
    output = (result.stdout + result.stderr).strip()
    if result.returncode != 0 or "ERROR in" in output:
        print(output)
        print("COMPILE FAILED")
        sys.exit(1)
    print("COMPILE OK")

    d8 = sdk / "build-tools" / BUILD_TOOLS_VERSION / "lib/d8.jar"
    class_files = [str(p) for p in classes.rglob("*.class")]
    dex = work / "dex"
    dex.mkdir()
    command = ["java", "-Xmx768m", "-cp", str(d8), "com.android.tools.r8.D8", "--release", "--min-api", "23",
               "--lib", str(sdk / "platforms" / PLATFORM / "android.jar"), "--output", str(dex)]
    for jar in jars:
        command += ["--classpath", jar]
    result = subprocess.run(command + class_files, capture_output=True, text=True)
    if result.returncode != 0:
        print((result.stdout + result.stderr).strip())
        print("DEX FAILED")
        sys.exit(1)
    print(f"DEX OK ({', '.join(p.name for p in dex.glob('*.dex'))}); work directory: {work}")


if __name__ == "__main__":
    main()
