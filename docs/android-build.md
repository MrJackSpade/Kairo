# Shared Android build setup

Both products and this standalone library build use the same pinned setup:

- `gradle/android-versions.properties` owns Android plugin, Kotlin, Java, SDK, CI build tools, NDK and CMake versions. Gradle retains AGP's default build tools selection; CI installs the explicit tools used by release verification.
- `gradle/android-settings.gradle` owns plugin and dependency repositories and plugin version resolution.
- `gradle/android-module.gradle` supplies common Android and Kotlin defaults. Application IDs, namespaces, native paths/options, ABIs, flavors, signing and release rules stay in the product modules.
- `gradle/wrapper/` and this repository's `gradlew` scripts own the Gradle distribution and its checksum. Product wrapper scripts delegate here with their product root as the project directory.
- `.github/actions/setup-android` reads the same version file and sets up CI. Product workflows use the local action under `shared/`, so their submodule pin selects the setup without fetching a floating branch. The action uses the basic Gradle cache, including in release and catalog workflows.

Product settings apply `shared/gradle/android-settings.gradle`; Android modules apply `shared/gradle/android-module.gradle` after their plugin declarations. The frontend resolves its convention relative to its own directory so it also builds standalone.

Before building a clean product checkout, run `bash tools/checkout_shared.sh` (or `git submodule update --init shared` locally). Each product retains only the bootstrap needed to fetch the pinned shared checkout, then delegates commit and cleanliness validation to `tools/verify_checkout.sh` here. Missing shared files produce an explicit initialization error from the product wrapper.

To update the toolchain, change this repository's version file or wrapper, verify the standalone library and both products, then update both product pins. Do not copy versions back into the app builds or workflows. Tag triggers, signing secrets, native build commands and publication remain product-owned.

## Native Debug optimization

Include `shared/cmake/KairoNativeDefaults.cmake` from the product's native build
and call `kairo_native_debug_defaults(target)` after declaring each native target.
The shared function adds `-O2` for Debug only. It does not add `NDEBUG`, remove
symbols, alter assertions, change release optimization, or set emulator-specific
compiler flags. Android/NDK Debug `-g` and `-fno-limit-debug-info` remain active.

KairoDos applies it to `kairodos_host`; its existing non-Debug `-O2` remains
product-owned. Kairo98 applies it to `kairo98` and `np21w_core`, preserving their
previous Debug `-O2`. Kairo98 LTO, aliasing and signed-char flags and DOSBox Staging
core compiler/PGO flags remain in the product integrations. The optional DOS
`presentationProfile` switch still explicitly adds `NDEBUG` and its profiling
marker; normal Debug presentation is already optimized without that switch.
