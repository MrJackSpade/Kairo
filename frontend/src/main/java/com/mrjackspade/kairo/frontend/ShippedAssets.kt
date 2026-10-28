package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Data an app ships in its assets (an emulator's data folder, a free firmware, a font), installed
 * into app storage where the emulator reads it.
 *
 * An asset only changes when the app does, so [install] writes it once per installed app version,
 * recorded in a marker beside the target, instead of reading the asset on every launch. It installs
 * through a temporary copy renamed into place, so a target is either the old copy or the new one.
 */
object ShippedAssets {
    /**
     * Installs the asset at [path] (a file, or a folder and everything in it) as [target], unless this
     * app version already did; [progress] hears the shared "Preparing the emulator" first when it does.
     */
    fun install(context: Context, path: String, target: File, progress: (String) -> Unit = {}) {
        val marker = File(target.parentFile, ".${target.name}.installed")
        val version = appVersion(context)
        if (target.exists() && marker.takeIf { it.isFile }?.readText() == version) return
        progress(context.getString(R.string.kairo_preparing_emulator))
        val parent = target.parentFile!!
        check(parent.mkdirs() || parent.isDirectory) { "Could not create $parent" }
        val copy = File(parent, "${target.name}.new")
        copy.deleteRecursively()
        copy(context, path, copy)
        if (copy.isDirectory) {
            // A folder can't replace another in one rename: the old one goes first.
            target.deleteRecursively()
            check(copy.renameTo(target)) { "Could not install $path in $target" }
        } else {
            Files.move(copy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        marker.writeText(version)
    }

    private fun copy(context: Context, path: String, dest: File) {
        val children = context.assets.list(path) ?: error("Missing asset $path")
        if (children.isEmpty()) {
            context.assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
        } else {
            check(dest.mkdirs() || dest.isDirectory) { "Could not create $dest" }
            for (child in children) copy(context, "$path/$child", File(dest, child))
        }
    }

    /** The installed app's version and when it was installed, which a reinstall of the same version changes too. */
    private fun appVersion(context: Context): String {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return "$code/${info.lastUpdateTime}"
    }
}
