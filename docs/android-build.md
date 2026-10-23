# Shared Android build setup

Every Kairo app and this standalone library build use the same pinned setup:

- `gradle/android-versions.properties` owns Android plugin, Kotlin, Java, SDK, CI build tools, NDK and CMake versions. Gradle retains AGP's default build tools selection; CI installs the explicit tools used by release verification.
- `gradle/android-settings.gradle` owns plugin and dependency repositories and plugin version resolution.
- `gradle/android-module.gradle` supplies common Android and Kotlin defaults. `gradle/kairo-app.gradle` supplies every app module's identity, signing, flavors and assets (below).
- `gradle/wrapper/` and this repository's `gradlew` scripts own the Gradle distribution and its checksum. Product wrapper scripts delegate here with their product root as the project directory.
- `.github/actions/setup-android` reads the same version file and sets up CI. Product workflows use the local action under `shared/`, so their submodule pin selects the setup without fetching a floating branch. The action uses the basic Gradle cache, including in release and catalog workflows.
- The frontend builds the pinned librashader OpenGL runtime with the Rust version in `android-versions.properties` and target `aarch64-linux-android`. Local builders need that Rust toolchain and target installed; CI installs both in the shared setup action. Cargo uses `third_party/librashader/Cargo.lock`. The corresponding locked crate source archives are under `third_party/cargo-crates/`. Both products package the generated `liblibrashader_capi.so` through the frontend AAR and provide the NDK `libc++_shared.so` through their native C++ builds.

Product settings apply `shared/gradle/android-settings.gradle`; Android modules apply `shared/gradle/android-module.gradle` after their plugin declarations. The frontend resolves its convention relative to its own directory so it also builds standalone.

Before building a clean app checkout, run `git submodule update --init shared`. The shared workflows then run `tools/verify_checkout.sh`, which validates the pinned commit and a clean checkout.

Every Kairo app releases the same version, the one in `release-version`. To release: set it, pin that shared commit in every app, add each app's `docs/releases/v<version>.md`, then run `tools/tag-release.ps1`, which checks every app and tags them all. Each app's pre-push hook and release workflow run `tools/check-release-version.sh`, which refuses a tag that is not `v<release-version>` of the pinned shared.

Every app's module applies `gradle/kairo-app.gradle`: the identity, the shared manifest and launcher resources (`app/`), signing with the persistent Kairo key (debug and release, from `KAIRO_RELEASE_KEYSTORE` and `KAIRO_RELEASE_PASSWORD`), the `withImages`/`withoutImages` flavors, the catalog assets, the native library named after the product, the frontend and the UI tests. The app's build file adds only what is its own, such as CMake options or extra modules.

Every app builds and releases through three reusable workflows, which it calls from its own `android.yml`, `tag-builds.yml` and `play-release.yml` with `uses: MrJackSpade/Kairo/.github/workflows/<file>@<commit>` (the shared submodule's commit) and `secrets: inherit`. Before a build, both run `tools/check-app.sh`: the README's shared blocks, the catalog and artwork of an app on the shared catalog (`tools/catalog_config.py`), then the app's own checks and build preparation in its `tools/checks.sh`.

- `.github/workflows/app-build.yml` builds the debug APK for a pull request or a manual run and runs the app's `tests`; a manual run with `signed` signs it with the Kairo key under a given version, to update an installed release.
- `.github/workflows/app-release.yml` builds, for a tag `v<version>`, the release APK, the APK with bundled artwork and the Play bundle (reviewed artwork only); verifies their signer, version and alignment; audits them with `tools/audit_android_release.py` and the app's `audits`; publishes them as the tag's GitHub Release; and dispatches `play-release.yml` for a tag without a prerelease suffix. The app supplies `docs/releases/<tag>.md` and the secrets `KAIRO_RELEASE_KEYSTORE_B64` and `KAIRO_RELEASE_PASSWORD`.
- `.github/workflows/app-play-release.yml` publishes a release's Play bundle to production through `.github/actions/play-publish`. The app must exist in Play Console with the Play publishing service account invited, and the workload identity provider's condition must accept the app's repository.

To update the toolchain, change this repository's version file or wrapper, verify the standalone library and the apps, then update every app's pin. Do not copy versions back into the app builds or workflows.

## Native Debug optimization

Include `shared/cmake/KairoNativeDefaults.cmake` from the product's native build
and call `kairo_native_debug_defaults(target)` after declaring each native target.
The shared function adds `-O2` for Debug only. It does not add `NDEBUG`, remove
symbols, alter assertions, change release optimization, or set emulator-specific
compiler flags. Android/NDK Debug `-g` and `-fno-limit-debug-info` remain active.

KairoDos applies it to `kairodos`; its existing non-Debug `-O2` remains
product-owned. Kairo98 applies it to `kairo98` and `np21w_core`, preserving their
previous Debug `-O2`. Kairo98 LTO, aliasing and signed-char flags and DOSBox Staging
core compiler/PGO flags remain in the product integrations. The optional DOS
`presentationProfile` switch still explicitly adds `NDEBUG` and its profiling
marker; normal Debug presentation is already optimized without that switch.
