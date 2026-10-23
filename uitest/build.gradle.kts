import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = projectDir.resolve("../gradle/android-module.gradle"))

// The UI test framework and the tests every app shares (docs/ui-tests.md). An app's androidTest
// depends on it; the app itself never does.
android {
    namespace = "com.mrjackspade.kairo.uitest"
}

val versions = Properties().apply {
    projectDir.resolve("../gradle/android-versions.properties").inputStream().use { load(it) }
}

dependencies {
    // The app under test brings the frontend; the tests compile against it.
    compileOnly(project(":frontend"))
    api("androidx.test:runner:${versions["androidx_test_runner"]}")
    api("androidx.test:rules:${versions["androidx_test_rules"]}")
    api("androidx.test.ext:junit:${versions["androidx_test_junit"]}")
    api("androidx.test.services:storage:${versions["androidx_test_services"]}")
    api("junit:junit:4.13.2")
}
