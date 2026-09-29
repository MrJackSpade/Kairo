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
