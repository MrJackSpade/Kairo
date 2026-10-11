plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = projectDir.resolve("../gradle/android-module.gradle"))

android {
    namespace = "com.mrjackspade.kairo.frontend"

}

dependencies {
    testImplementation("junit:junit:4.13.2")
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
android.sourceSets.getByName("main").jniLibs.srcDir(slangJni)
tasks.named("preBuild") { dependsOn(packageSlangRuntime) }
