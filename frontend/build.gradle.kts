plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = projectDir.resolve("../gradle/android-module.gradle"))

// An app whose emulator cannot be combined with the GPL-3.0 shader runtime sets kairoShaders=false
// in its gradle.properties: the frontend then carries no shader runtime or presets and offers no
// shaders (BuildConfig.SHADERS), and the app's native library is built without the presenter.
val kairoShaders = providers.gradleProperty("kairoShaders").orNull != "false"

android {
    namespace = "com.mrjackspade.kairo.frontend"
    buildFeatures { buildConfig = true }
    defaultConfig { buildConfigField("boolean", "SHADERS", kairoShaders.toString()) }
}

dependencies {
    // Installs baseline-prof.txt on sideloaded APKs, which receive no cloud profile.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // @StringRes and @PluralsRes on UiText, so lint checks the resource each one names.
    implementation("androidx.annotation:annotation:1.8.1")
    // 7z archives in DiskImageLibrary; xz supplies their LZMA and LZMA2 decoders.
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub on the JVM; tests parse JSON with the real library.
    testImplementation("org.json:json:20240303")
}

// Build the pinned Slang runtime from source, once for either product. The
// frontend AAR carries the same arm64 library into both APK variants and AABs.
val slangSource = projectDir.resolve("../third_party/librashader")
val slangTarget = layout.buildDirectory.dir("librashader-target")
val slangJni = layout.buildDirectory.dir("generated/slang-jni")
val buildSlangRuntime = tasks.register<Exec>("buildSlangRuntime") {
    val sdk = android.sdkDirectory
    val host = if (System.getProperty("os.name").startsWith("Windows"))
        "windows-x86_64" else "linux-x86_64"
    val ndkBin = sdk.resolve("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt/$host/bin")
    val suffix = if (host.startsWith("windows")) ".cmd" else ""
    val clang = ndkBin.resolve("aarch64-linux-android26-clang$suffix").absolutePath
    val clangxx = ndkBin.resolve("aarch64-linux-android26-clang++$suffix").absolutePath
    workingDir(slangSource)
    environment("CARGO_TARGET_DIR", slangTarget.get().asFile.absolutePath)
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", clang)
    environment("CC_aarch64_linux_android", clang)
    environment("CXX_aarch64_linux_android", clangxx)
    environment("AR_aarch64_linux_android", ndkBin.resolve(
        if (host.startsWith("windows")) "llvm-ar.exe" else "llvm-ar").absolutePath)
    commandLine("cargo", "build", "--release", "--locked", "-p", "librashader-capi",
        "--target", "aarch64-linux-android", "--no-default-features", "--features", "runtime-opengl")
    inputs.files(fileTree(slangSource) { exclude("target/**", "vendor/**") })
    outputs.file(slangTarget.map { it.file("aarch64-linux-android/release/liblibrashader_capi.so") })
}
val packageSlangRuntime = tasks.register<Copy>("packageSlangRuntime") {
    dependsOn(buildSlangRuntime)
    from(slangTarget.map { it.file("aarch64-linux-android/release/liblibrashader_capi.so") })
    into(slangJni.map { it.dir("arm64-v8a") })
}
if (kairoShaders) {
    android.sourceSets.getByName("main").jniLibs.srcDir(slangJni)
    android.sourceSets.getByName("main").assets.srcDir(
        projectDir.resolve("../third_party/slang-shaders/assets"))
    // The runtime's notices, SLANG_RUNTIME_NOTICES.txt.
    android.sourceSets.getByName("main").assets.srcDir(projectDir.resolve("src/shaders/assets"))
    tasks.named("preBuild") { dependsOn(packageSlangRuntime) }
}

// The LLVM notice of the NDK that builds every app's native code and statically links its C++
// runtime, from that NDK itself (About -> Licenses shows it as NDK_NOTICES.txt).
val ndkNotices = layout.buildDirectory.dir("generated/ndk-notices")
val writeNdkNotices = tasks.register("writeNdkNotices") {
    val prebuilt = android.sdkDirectory.resolve("ndk/${android.ndkVersion}/toolchains/llvm/prebuilt")
    val hosts = prebuilt.listFiles { file -> file.isDirectory }.orEmpty()
    check(hosts.size == 1) { "Expected one NDK host toolchain in $prebuilt, found ${hosts.map { it.name }}" }
    val notice = hosts.single().resolve("NOTICE")
    val version = android.ndkVersion
    inputs.file(notice)
    outputs.dir(ndkNotices)
    doLast {
        val rule = "=".repeat(72)
        ndkNotices.get().file("NDK_NOTICES.txt").asFile.apply { parentFile.mkdirs() }.writeText(
            "$rule\nAndroid NDK $version LLVM toolchain and statically linked C++ runtime\n" +
                "Source: Android NDK $version, toolchains/llvm/prebuilt NOTICE\n$rule\n" + notice.readText())
    }
}
android.sourceSets.getByName("main").assets.srcDir(ndkNotices)
tasks.named("preBuild") { dependsOn(writeNdkNotices) }
