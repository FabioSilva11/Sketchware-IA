#!/usr/bin/env python3
"""Rebuilds the compile assets Sketchware uses to build projects on the device.

Outputs (in app/src/main/assets/libs/):
  libs.zip         one folder per library: classes.jar, res/, assets/, AndroidManifest.xml,
                   R.txt, proguard.txt
  dexs.zip         one <library>.dex per library, dexed with D8 for --min-api 21
  android.jar.zip  the framework jar generated projects compile against

It also regenerates the library table in BuiltInLibraries.java.

How it works:
  1. build.gradle (next to this script) declares the top-level libraries. Gradle resolves
     the transitive graph and the exportBuiltInLibraries task writes it as JSON.
  2. Every AAR/JAR is unpacked; libraries without classes or resources are dropped and
     their dependents inherit their dependencies.
  3. D8 dexes every library in one JVM (Dexer.java).
  4. Libraries that are not on Maven (CodeView, OTPView, ...) are carried over from the
     current zips.
  5. android.jar's resources.arsc is re-encoded without compact entries, because the
     aapt2 binaries shipped in assets/aapt cannot read the format used since API 35.

Usage: python tools/builtin-libs/build_builtin_libs.py [--skip-gradle]
Needs ANDROID_HOME (or local.properties) with build-tools 37.0.0 and platforms/android-37.0.
"""

import argparse
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
ROOT = TOOL_DIR.parent.parent
ASSETS_LIBS = ROOT / "app/src/main/assets/libs"
BUILT_IN_LIBRARIES_JAVA = ROOT / "app/src/main/java/mod/jbk/build/BuiltInLibraries.java"
BUILD_TOOLS_VERSION = "37.0.0"
PLATFORM = "android-37.0"
DEX_MIN_API = 21

# Constant names the app code already refers to, or that read better than the generic rule.
CONSTANT_NAMES = {
    "androidx.activity:activity": "ANDROIDX_ACTIVITY",
    "androidx.annotation:annotation-jvm": "ANDROIDX_ANNOTATION_JVM",
    "androidx.arch.core:core-common": "ANDROIDX_CORE_COMMON",
    "androidx.arch.core:core-runtime": "ANDROIDX_CORE_RUNTIME",
    "androidx.collection:collection-jvm": "ANDROIDX_COLLECTION_JVM",
    "androidx.graphics:graphics-shapes-android": "ANDROIDX_GRAPHICS_SHAPES_ANDROID",
    "com.airbnb.android:lottie": "LOTTIE",
    "com.github.bumptech.glide:annotations": "GLIDE_ANNOTATIONS",
    "com.github.bumptech.glide:disklrucache": "GLIDE_DISKLRUCACHE",
    "com.github.bumptech.glide:gifdecoder": "GLIDE_GIFDECODER",
    "com.github.bumptech.glide:glide": "GLIDE",
    "com.google.android.material:material": "MATERIAL",
    "com.google.android.play:core-common": "PLAY_CORE_COMMON",
    "com.google.android.ump:user-messaging-platform": "USER_MESSAGING_PLATFORM",
    "com.google.code.gson:gson": "GSON",
    "com.google.errorprone:error_prone_annotations": "ERROR_PRONE_ANNOTATIONS",
    "com.pierfrancescosoffritti.androidyoutubeplayer:core": "ANDROID_YOUTUBE_PLAYER",
    "com.squareup.okhttp3:okhttp-android": "OKHTTP_ANDROID",
    "com.squareup.okio:okio-jvm": "OKIO_JVM",
    "de.hdodenhof:circleimageview": "CIRCLEIMAGEVIEW",
    "org.jetbrains:annotations": "JETBRAINS_ANNOTATIONS",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm": "JETBRAINS_KOTLINX_COROUTINES_CORE_JVM",
    "org.jspecify:jspecify": "JSPECIFY",
}

# Folder names that would otherwise be ambiguous ("core-13.0.0", "core-common-2.0.3").
FOLDER_PREFIXES = {
    "com.pierfrancescosoffritti.androidyoutubeplayer:core": "android-youtube-player",
    "com.google.android.play:core-common": "play-core-common",
}

# Libraries that are not published to Maven; they are copied from the current zips.
# (constant, folder name, dependency constants, package name with resources/assets or None)
CARRIED_OVER = [
    ("CODEVIEW", "CodeView-0.4.0", [], "br.tiagohm.codeview"),
    ("HTTP_LEGACY_ANDROID", "http-legacy-android-28", [], None),
    ("OTPVIEW", "OTPView-0.1.0", ["ANDROIDX_APPCOMPAT", "ANDROIDX_CORE"], "affan.ahmad.otp"),
    ("PATTERN_LOCK_VIEW", "pattern-lock-view", ["ANDROIDX_CORE", "JETBRAINS_ANNOTATIONS"], "com.andrognito.patternlockview"),
    ("WAVE_SIDE_BAR", "wave-side-bar", [], "com.sayuti.lib"),
]

# Gradle resolves com.google.guava:listenablefuture to an empty "9999.0" version because full
# Guava also contains ListenableFuture. Sketchware adds libraries one by one though, so e.g.
# androidx.concurrent would end up without the interface: ship the real artifact instead and
# drop Guava's copy, which keeps exactly one definition of the class.
EMPTY_LISTENABLE_FUTURE = "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava"
CLASSES_TO_STRIP = {
    "com.google.guava:guava": ["com/google/common/util/concurrent/ListenableFuture.class"],
}

AAR_ENTRIES_TO_KEEP = ("classes.jar", "AndroidManifest.xml", "R.txt", "proguard.txt", "public.txt")
AAR_DIRECTORIES_TO_KEEP = ("res/", "assets/", "jni/")


def log(message):
    print(message, flush=True)


def android_home():
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not home:
        properties = ROOT / "local.properties"
        if properties.exists():
            for line in properties.read_text(encoding="utf-8").splitlines():
                if line.startswith("sdk.dir="):
                    home = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
    if not home or not Path(home).is_dir():
        sys.exit("Set ANDROID_HOME to an Android SDK installation")
    return Path(home)


def constant_name(group, module):
    key = f"{group}:{module}"
    if key in CONSTANT_NAMES:
        return CONSTANT_NAMES[key]
    if group.startswith("androidx.compose."):
        base = "ANDROIDX_COMPOSE_" + re.sub(r"-(android|jvm)$", "", module)
    elif group.startswith("androidx.privacysandbox."):
        base = "ANDROIDX_" + module
    elif group.startswith("androidx."):
        base = "ANDROIDX_" + re.sub(r"-(android|jvm)$", "", module)
    elif group == "com.google.firebase":
        base = "FIREBASE_" + module.removeprefix("firebase-")
    elif group == "com.google.android.gms":
        base = "PLAY_SERVICES_" + module.removeprefix("play-services-")
    elif group == "com.google.android.datatransport":
        base = "DATATRANSPORT_" + module
    elif group == "com.google.android.play":
        base = "PLAY_" + module
    elif group == "com.google.guava":
        base = "GUAVA" if module == "guava" else "GUAVA_" + module
    elif group in ("org.jetbrains.kotlin", "org.jetbrains.kotlinx"):
        base = "JETBRAINS_" + module
    else:
        base = module
    return re.sub(r"[^A-Za-z0-9]+", "_", base).upper()


def folder_name(group, module, version):
    return f"{FOLDER_PREFIXES.get(f'{group}:{module}', module)}-{version}"


def run_gradle():
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = dict(os.environ, ANDROID_HOME=str(android_home()))
    subprocess.run([str(gradlew), "-p", str(TOOL_DIR), "exportBuiltInLibraries",
                    "--no-daemon", "--console=plain", "-q"], cwd=ROOT, env=env, check=True)


def is_dexable_class(name):
    return name.endswith(".class") and not name.startswith("META-INF/") and not name.endswith("module-info.class")


def jar_has_classes(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        return any(is_dexable_class(name) for name in jar.namelist())


def merge_jars(jars):
    """Merges classes.jar with an AAR's libs/*.jar into one jar."""
    if len(jars) == 1:
        return jars[0]
    seen = set()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as merged:
        for data in jars:
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                for info in jar.infolist():
                    if info.is_dir() or info.filename in seen:
                        continue
                    seen.add(info.filename)
                    merged.writestr(info, jar.read(info))
    return output.getvalue()


def proguard_rules_from_jar(data):
    """R8 rules embedded in a plain JAR, preferring META-INF/com.android.tools/r8."""
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names = jar.namelist()
        r8 = sorted(n for n in names if n.startswith("META-INF/com.android.tools/r8") and n.endswith(".pro"))
        proguard = sorted(n for n in names if n.startswith("META-INF/proguard/") and n.endswith(".pro"))
        chosen = r8 or proguard
        return "\n".join(jar.read(n).decode("utf-8", "replace") for n in chosen)


def references_own_r_class(classes_jar, package_name):
    needle = (package_name.replace(".", "/") + "/R$").encode()
    with zipfile.ZipFile(io.BytesIO(classes_jar)) as jar:
        return any(name.endswith(".class") and needle in jar.read(name) for name in jar.namelist())


MACRO_DEFINITION = re.compile(r'[ \t]*<macro\s+name="([^"]+)"\s*>(.*?)</macro>[ \t]*\r?\n?', re.S)
MACRO_REFERENCE = re.compile(r"@macro/([A-Za-z0-9_.]+)")


def expand_macros(res):
    """Inlines <macro> resources (aapt2 7.0+), so they also compile with older aapt2 versions.

    Macros are plain compile-time text substitution and may only live in values/, so
    replacing every @macro/name reference with its value is equivalent.
    """
    xml_files = sorted(res.rglob("*.xml"))
    macros = {}
    for path in xml_files:
        if path.parent.name == "values":
            macros.update((name, value.strip())
                          for name, value in MACRO_DEFINITION.findall(path.read_bytes().decode("utf-8")))
    if not macros:
        return 0

    def resolve(match, depth=0):
        if depth > 16:
            sys.exit(f"Recursive macro @macro/{match.group(1)} in {res}")
        return MACRO_REFERENCE.sub(lambda inner: resolve(inner, depth + 1), macros[match.group(1)])

    for path in xml_files:
        text = path.read_bytes().decode("utf-8")
        expanded = MACRO_REFERENCE.sub(resolve, MACRO_DEFINITION.sub("", text))
        if expanded != text:
            path.write_bytes(expanded.encode("utf-8"))
    return len(macros)


def has_meaningful_resources(res):
    """False for the placeholder res/values/values.xml that empty AndroidX -ktx AARs ship."""
    for path in res.rglob("*"):
        if not path.is_file():
            continue
        if not (path.parent.name.startswith("values") and path.suffix == ".xml"):
            return True
        content = re.sub(r"<!--.*?-->|<\?xml[^>]*\?>", "", path.read_bytes().decode("utf-8"), flags=re.S)
        if re.search(r"<(?!/?resources\b)[A-Za-z]", content):
            return True
    return False


def manifest_package(manifest):
    match = re.search(r'\bpackage\s*=\s*"([^"]+)"', manifest)
    return match.group(1) if match else None


def strip_classes(data, names):
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as jar, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as stripped:
        for info in jar.infolist():
            if info.filename not in names:
                stripped.writestr(info, jar.read(info))
    return output.getvalue()


def unpack(component, destination):
    """Unpacks one Maven artifact into destination. Returns metadata about it."""
    artifact = Path(component["artifact"]["path"])
    destination.mkdir(parents=True)
    info = {"has_classes": False, "has_res": False, "has_assets": False, "package": None, "jni": False}

    if component["artifact"]["type"] == "jar":
        data = artifact.read_bytes()
        strip = CLASSES_TO_STRIP.get(f"{component['group']}:{component['module']}")
        if strip:
            data = strip_classes(data, strip)
        (destination / "classes.jar").write_bytes(data)
        info["has_classes"] = jar_has_classes(data)
        rules = proguard_rules_from_jar(data)
        if rules.strip():
            (destination / "proguard.txt").write_text(rules, encoding="utf-8")
        return info

    with zipfile.ZipFile(artifact) as aar:
        jars = []
        for entry in aar.infolist():
            name = entry.filename
            if entry.is_dir():
                continue
            if name == "classes.jar":
                jars.insert(0, aar.read(entry))
            elif name.startswith("libs/") and name.endswith(".jar"):
                jars.append(aar.read(entry))
            elif name in AAR_ENTRIES_TO_KEEP or name.startswith(AAR_DIRECTORIES_TO_KEEP):
                target = destination / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(aar.read(entry))
                if name.startswith("res/"):
                    info["has_res"] = True
                elif name.startswith("assets/"):
                    info["has_assets"] = True
                elif name.startswith("jni/"):
                    info["jni"] = True

        classes = merge_jars(jars) if jars else None
        if classes:
            (destination / "classes.jar").write_bytes(classes)
            info["has_classes"] = jar_has_classes(classes)

    if info["has_res"] and not has_meaningful_resources(destination / "res"):
        shutil.rmtree(destination / "res")
        info["has_res"] = False

    if info["has_res"]:
        expanded = expand_macros(destination / "res")
        if expanded:
            log(f"{destination.name}: inlined {expanded} <macro> resources")

    manifest = destination / "AndroidManifest.xml"
    if manifest.exists():
        info["package"] = manifest_package(manifest.read_text(encoding="utf-8"))
    if info["package"] and not info["has_res"] and classes and references_own_r_class(classes, info["package"]):
        # The code reads its own R class (generated from the app's merged resources), so aapt2
        # has to emit it even though the library ships no resources of its own.
        values = destination / "res/values"
        values.mkdir(parents=True)
        (values / "values.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources/>\n', encoding="utf-8")
        info["has_res"] = True
    return info


def collapse(components, keep):
    """Removes components not in keep, connecting their dependents to their dependencies."""
    by_id = {c["id"]: c for c in components}
    cache = {}

    def effective(component_id, stack=()):
        if component_id in cache:
            return cache[component_id]
        result = []
        for dependency in by_id[component_id]["dependencies"]:
            if dependency in stack:
                continue
            if dependency in keep:
                result.append(dependency)
            else:
                result.extend(effective(dependency, stack + (component_id,)))
        cache[component_id] = list(dict.fromkeys(result))
        return cache[component_id]

    return {component_id: [d for d in effective(component_id) if d != component_id] for component_id in keep}


def dex_libraries(libraries, work, sdk):
    listing = work / "dex-input.tsv"
    listing.write_text("".join(f"{name}\t{path}\n" for name, path in libraries), encoding="utf-8")
    output = work / "dex"
    d8 = sdk / "build-tools" / BUILD_TOOLS_VERSION / "lib/d8.jar"
    android_jar = sdk / "platforms" / PLATFORM / "android.jar"
    # Small, C1-only JVM: this has to run on machines with little free memory.
    subprocess.run(["java", "-Xmx768m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                    "-XX:ReservedCodeCacheSize=64m", "-cp", str(d8), str(TOOL_DIR / "Dexer.java"),
                    str(listing), str(android_jar), str(output), str(DEX_MIN_API)], check=True)
    dexes = {}
    for name, _ in libraries:
        produced = sorted((output / name).glob("*.dex"))
        if len(produced) != 1:
            sys.exit(f"{name}: expected one DEX file, D8 produced {[p.name for p in produced]}")
        dexes[name] = produced[0]
    return dexes


def build_android_jar(sdk, work):
    """android.jar with a resources.arsc the old on-device aapt2 can load."""
    source = sdk / "platforms" / PLATFORM / "android.jar"
    aapt2 = sdk / "build-tools" / BUILD_TOOLS_VERSION / ("aapt2.exe" if os.name == "nt" else "aapt2")
    converted = work / "framework-res.apk"
    subprocess.run([str(aapt2), "convert", "--output-format", "binary", "-o", str(converted), str(source)], check=True)
    with zipfile.ZipFile(converted) as apk:
        arsc = apk.read("resources.arsc")

    jar = io.BytesIO()
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as patched:
        for entry in original.infolist():
            data = arsc if entry.filename == "resources.arsc" else original.read(entry)
            out = zipfile.ZipInfo(entry.filename, date_time=entry.date_time)
            out.compress_type = zipfile.ZIP_DEFLATED
            out.external_attr = entry.external_attr
            patched.writestr(out, data)

    target = ASSETS_LIBS / "android.jar.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        info = zipfile.ZipInfo("android.jar", date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, jar.getvalue())
    log(f"Wrote {target} from {PLATFORM}")


def write_zip(target, root):
    """Zips root's children with directory entries and stable ordering."""
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(root.rglob("*")):
            relative = path.relative_to(root).as_posix()
            if path.is_dir():
                info = zipfile.ZipInfo(relative + "/", date_time=(2026, 1, 1, 0, 0, 0))
                info.external_attr = 0o40755 << 16
                archive.writestr(info, b"")
            else:
                info = zipfile.ZipInfo(relative, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes())


def java_table(entries):
    lines = ["    // region generated by tools/builtin-libs/build_builtin_libs.py — do not edit by hand",
             "    // None final so that field values won't be optimized into code, and to allow easy changing of library names due to that",
             ""]
    for entry in entries:
        lines.append(f'    public static String {entry["constant"]} = "{entry["name"]}";')
    lines += ["", "    public static final BuiltInLibrary[] KNOWN_BUILT_IN_LIBRARIES = {"]
    for entry in entries:
        dependencies = ", ".join(entry["dependencies"])
        arguments = [entry["constant"]]
        if entry["package"]:
            arguments += [f"List.of({dependencies})", f'"{entry["package"]}"']
        elif entry["dependencies"]:
            arguments.append(f"List.of({dependencies})")
        lines.append(f"            new BuiltInLibrary({', '.join(arguments)}),")
    lines += ["    };", "    // endregion"]
    return "\n".join(lines)


def update_java(entries):
    source = BUILT_IN_LIBRARIES_JAVA.read_text(encoding="utf-8")
    pattern = re.compile(r"    // region generated by tools/builtin-libs/build_builtin_libs\.py.*?    // endregion", re.S)
    if not pattern.search(source):
        sys.exit(f"Generated region not found in {BUILT_IN_LIBRARIES_JAVA}")
    BUILT_IN_LIBRARIES_JAVA.write_text(pattern.sub(lambda _: java_table(entries), source), encoding="utf-8")
    log(f"Updated {BUILT_IN_LIBRARIES_JAVA.relative_to(ROOT)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--skip-gradle", action="store_true", help="reuse build/builtin-libs.json")
    arguments = parser.parse_args()

    sdk = android_home()
    if not arguments.skip_gradle:
        run_gradle()
    graph = json.loads((TOOL_DIR / "build/builtin-libs.json").read_text(encoding="utf-8"))
    components = graph["components"]

    work = Path(tempfile.mkdtemp(prefix="builtin-libs-"))
    libs_root = work / "libs"
    dexs_root = work / "dexs"
    dexs_root.mkdir(parents=True)
    try:
        metadata = {}
        for component in components:
            if component["id"] == EMPTY_LISTENABLE_FUTURE:
                component = dict(component, version="1.0", artifact={"path": graph["listenableFuture"], "type": "jar"})
            if not component["artifact"]:
                continue
            name = folder_name(component["group"], component["module"], component["version"])
            info = unpack(component, libs_root / name)
            if info["jni"]:
                log(f"warning: {name} ships native libraries, which built-in libraries don't support")
            if not (info["has_classes"] or info["has_res"] or info["has_assets"]):
                manifest = libs_root / name / "AndroidManifest.xml"
                if manifest.exists() and re.search(r"<(application\b[^>]*>\s*<|uses-permission|queries|permission\b)",
                                                   manifest.read_text(encoding="utf-8")):
                    sys.exit(f"{name} only contributes manifest entries; the build doesn't support such libraries yet")
                shutil.rmtree(libs_root / name)
                continue
            info["name"] = name
            info["constant"] = constant_name(component["group"], component["module"])
            metadata[component["id"]] = info

        names = [m["name"] for m in metadata.values()]
        constants = [m["constant"] for m in metadata.values()] + [c[0] for c in CARRIED_OVER]
        for values, kind in ((names, "folder"), (constants, "constant")):
            duplicates = {v for v in values if values.count(v) > 1}
            if duplicates:
                sys.exit(f"Duplicate {kind} names: {sorted(duplicates)}")

        dependencies = collapse(components, set(metadata))
        dexes = dex_libraries([(m["name"], libs_root / m["name"] / "classes.jar")
                               for m in metadata.values() if m["has_classes"]], work, sdk)
        for name, dex in dexes.items():
            shutil.copyfile(dex, dexs_root / f"{name}.dex")

        entries = []
        for component_id, info in metadata.items():
            entries.append({
                "constant": info["constant"],
                "name": info["name"],
                "dependencies": sorted(metadata[d]["constant"] for d in dependencies[component_id]),
                "package": info["package"] if info["has_res"] else None,
            })

        known_constants = {e["constant"] for e in entries} | {c[0] for c in CARRIED_OVER}
        unknown = sorted({d for c in CARRIED_OVER for d in c[2]} - known_constants)
        if unknown:
            sys.exit(f"CARRIED_OVER libraries depend on libraries that no longer exist: {unknown}")

        with zipfile.ZipFile(ASSETS_LIBS / "libs.zip") as old_libs, zipfile.ZipFile(ASSETS_LIBS / "dexs.zip") as old_dexs:
            for constant, name, deps, package in CARRIED_OVER:
                for entry in old_libs.infolist():
                    if entry.filename.startswith(name + "/") and not entry.is_dir():
                        target = libs_root / entry.filename
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(old_libs.read(entry))
                (dexs_root / f"{name}.dex").write_bytes(old_dexs.read(f"{name}.dex"))
                entries.append({"constant": constant, "name": name, "dependencies": sorted(deps), "package": package})

        entries.sort(key=lambda e: e["constant"])
        write_zip(ASSETS_LIBS / "libs.zip", libs_root)
        write_zip(ASSETS_LIBS / "dexs.zip", dexs_root)
        log(f"Wrote {len(entries)} libraries to libs.zip and dexs.zip")
        update_java(entries)
        build_android_jar(sdk, work)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
