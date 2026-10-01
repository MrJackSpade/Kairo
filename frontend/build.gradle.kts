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
