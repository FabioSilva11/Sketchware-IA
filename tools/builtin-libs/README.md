# Built-in libraries

Generates the compile assets in `app/src/main/assets/libs/` that Sketchware uses to build
projects on the device, and the library table in `mod/jbk/build/BuiltInLibraries.java`:

| File | Contents |
|---|---|
| `libs.zip` | One folder per library: `classes.jar`, `res/`, `assets/`, `AndroidManifest.xml`, `proguard.txt` |
| `dexs.zip` | One `<library>.dex` per library, dexed with D8 for `--min-api 21` |
| `android.jar.zip` | `platforms/android-37.0/android.jar`, its `resources.arsc` re-encoded without compact entries |

## Updating

1. Change the versions in `build.gradle`. Only list what Sketchware adds to projects, or a
   transitive dependency you want newer than what the others request.
2. Rebuild the assets (needs `ANDROID_HOME` with build-tools 37.0.0 and platforms/android-37.0):

   ```
   python tools/builtin-libs/build_builtin_libs.py
   ```

3. Check them:

   ```
   python tools/builtin-libs/verify_resources.py
   python tools/builtin-libs/verify_classes.py
   python tools/builtin-libs/verify_generated_code.py
   ```

   `verify_resources.py` compiles and links every library's resources with build-tools 30.0.3's
   aapt2, which is older than the one in `app/src/main/jniLibs`, and checks that every `R` field
   the libraries' bytecode reads gets generated. `verify_classes.py` checks that every class's
   superclass and interfaces are in the library's own dependency closure, since a project only
   gets the libraries `BuiltInLibraries.java` lists. `verify_generated_code.py` compiles the helper
   classes `Lx.java` generates plus `smoke/GeneratedCodeSmoke.java` (the code the generators emit
   per component) with the ECJ version the app runs, then dexes them.

## Why the extra steps

- **aapt2 on the device** can't read the compact resource entries Android 15+ framework
  resources use, so the script re-encodes `android.jar`'s resource table. It also inlines
  Material's `<macro>` resources, which older aapt2 versions (like the one used for checking)
  don't know.
- **Library manifests** are merged into the app's at build time
  (`mod/jbk/build/compiler/manifest/LibraryManifestMerger.java`), the same way Gradle does, so
  components such as Firebase's registrars always match the bundled versions.
- **Guava's ListenableFuture**: next to full Guava, Gradle resolves `listenablefuture` to an
  empty artifact. Projects that only use AndroidX would then miss the interface, so the real
  `listenablefuture:1.0` is bundled and Guava's copy of the class removed.
- **Libraries that aren't on Maven** (CodeView, OTPView, PatternLockView, WaveSideBar, Apache HTTP
  legacy) are copied over from the previous zips; see `CARRIED_OVER` in the script.
