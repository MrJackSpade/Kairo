package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

/** Shared RetroArch Slang preset import and selection for both products. */
class ShaderOptions(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val showDialog: (AlertDialog.Builder) -> AlertDialog,
    private val onSelected: (String?) -> Unit,
    private val reportError: (String) -> Unit
) {
    private data class Builtin(val id: String, val title: String, val preset: String,
        val shader: String)

    companion object {
        const val IMPORT_REQUEST = 48129
        private const val SETTING = "slang_preset_path"
        private const val MAX_FILES = 10000
        private const val MAX_BYTES = 256L * 1024 * 1024
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private const val BUILTIN_VERSION = "e1d7563-v2"
        private val BUILTINS = listOf(
            Builtin("sharp-bilinear", "Sharp bilinear", "pixel-art-scaling/sharp-bilinear.slangp",
                "pixel-art-scaling/shaders/sharp-bilinear.slang"),
            Builtin("sharp-bilinear-scanlines", "Sharp bilinear + scanlines",
                "pixel-art-scaling/sharp-bilinear-scanlines.slangp",
                "pixel-art-scaling/shaders/sharp-bilinear-scanlines.slang"),
            Builtin("crt-easymode", "CRT EasyMode", "crt/crt-easymode.slangp",
                "crt/shaders/crt-easymode.slang")
        )
    }

    private val root get() = File(activity.filesDir, "slang-shaders")
    private val builtinRoot get() = File(activity.filesDir, "slang-builtins/$BUILTIN_VERSION")

    fun selectedPath(): String? {
        val value = preferences.getString(SETTING, null) ?: return null
        val builtin = BUILTINS.firstOrNull { value == "builtin:${it.id}" }
        if (builtin != null) return try { materialize(builtin).absolutePath }
            catch (_: IOException) { null }
        return value.takeIf { File(it).isFile }
    }

    fun label(): String {
        val value = preferences.getString(SETTING, null)
        return BUILTINS.firstOrNull { value == "builtin:${it.id}" }?.title
            ?: selectedPath()?.let { File(it).nameWithoutExtension } ?: "Off"
    }

    fun entry() = SettingsEntry("RetroArch shader", ::label, ::show)

    fun show() {
        val presets = root.walkTopDown().filter { it.isFile &&
            it.extension.equals("slangp", ignoreCase = true) }.sortedBy {
            it.relativeTo(root).path.lowercase()
        }.toList()
        val labels = listOf("Off") + BUILTINS.map { it.title } + presets.map {
            it.relativeTo(root).path.substringAfter(File.separatorChar)
        } +
            "Import shader ZIP…"
        val selected = preferences.getString(SETTING, null)
        val builtinIndex = BUILTINS.indexOfFirst { selected == "builtin:${it.id}" }
        val importedIndex = presets.indexOfFirst { it.absolutePath == selected }
        val selectedIndex = when {
            builtinIndex >= 0 -> builtinIndex + 1
            importedIndex >= 0 -> importedIndex + BUILTINS.size + 1
            else -> 0
        }
        showDialog(AlertDialog.Builder(activity).setTitle("RetroArch Slang shader")
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, choice ->
                dialog.dismiss()
                when {
                    choice == 0 -> select(null)
                    choice <= BUILTINS.size -> selectBuiltin(BUILTINS[choice - 1])
                    choice <= BUILTINS.size + presets.size ->
                        select(presets[choice - BUILTINS.size - 1].absolutePath)
                    else -> importZip()
                }
            }.setNegativeButton("Cancel", null))
    }

    private fun selectBuiltin(builtin: Builtin) {
        try {
            val path = materialize(builtin).absolutePath
            preferences.edit().putString(SETTING, "builtin:${builtin.id}").apply()
            onSelected(path)
        } catch (error: IOException) {
            reportError(error.message ?: "Could not prepare built-in shader")
        }
    }

    private fun materialize(builtin: Builtin): File {
        for (relative in listOf(builtin.preset, builtin.shader)) {
            val target = File(builtinRoot, relative)
            if (target.isFile) continue
            if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs())
                throw IOException("Could not create built-in shader directory")
            val temporary = File(target.path + ".tmp")
            try {
                activity.assets.open("retroarch/$relative").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temporary.renameTo(target)) throw IOException("Could not prepare built-in shader")
            } finally {
                temporary.delete()
            }
        }
        return File(builtinRoot, builtin.preset)
    }

    private fun select(path: String?) {
        preferences.edit().putString(SETTING, path).apply()
        onSelected(path)
    }

    @Suppress("DEPRECATION")
    private fun importZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        activity.startActivityForResult(intent, IMPORT_REQUEST)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != IMPORT_REQUEST) return false
        val uri = data?.data?.takeIf { resultCode == Activity.RESULT_OK } ?: return true
        val progress = showDialog(AlertDialog.Builder(activity).setTitle("Importing shaders")
            .setMessage("Copying the selected shader pack…"))
        Thread {
            val result = try { importPack(uri) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                progress.dismiss()
                result.fold(onSuccess = { files ->
                    if (files.size == 1) select(files.single().absolutePath) else show()
                }, onFailure = { error ->
                    reportError(error.message ?: "Could not import shader pack")
                })
            }
        }.start()
        return true
    }

    private fun importPack(uri: Uri): Result<List<File>> {
        val destination = File(root, UUID.randomUUID().toString())
        if (!destination.mkdirs()) throw IOException("Could not create shader directory")
        try {
            var count = 0
            var total = 0L
            activity.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val parts = entry.name.replace('\\', '/').split('/')
                        if (entry.name.startsWith('/') || parts.any { it == ".." || ':' in it ||
                                it.any { char -> char.code < 32 || char.code == 127 } })
                            throw IOException("Shader ZIP contains an unsafe path")
                        val relative = parts.filter { it.isNotEmpty() && it != "." }
                        if (relative.isEmpty()) continue
                        val target = File(destination, relative.joinToString(File.separator))
                        if (++count > MAX_FILES) throw IOException("Shader ZIP contains too many files")
                        if (entry.isDirectory) {
                            if (!target.mkdirs() && !target.isDirectory)
                                throw IOException("Could not create shader directory")
                            continue
                        }
                        if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs())
                            throw IOException("Could not create shader directory")
                        var fileBytes = 0L
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                fileBytes += read
                                total += read
                                if (fileBytes > MAX_FILE_BYTES || total > MAX_BYTES)
                                    throw IOException("Shader ZIP is too large")
                                output.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException("Could not open shader ZIP")
            val presets = destination.walkTopDown().filter { it.isFile &&
                it.extension.equals("slangp", ignoreCase = true) }.toList()
            if (presets.isEmpty()) throw IOException("Shader ZIP contains no .slangp presets")
            return Result.success(presets)
        } catch (error: Exception) {
            destination.deleteRecursively()
            return Result.failure(error)
        }
    }
}
