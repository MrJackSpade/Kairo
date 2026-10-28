package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

/** Shared RetroArch Slang preset import and selection for both products. */
class ShaderOptions(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val pages: () -> PageStack?,
    private val onSelected: (String?) -> Unit,
    private val reportError: (String) -> Unit
) {
    @Volatile private var importing = false
    private data class Builtin(val id: String, @StringRes val title: Int, val preset: String,
        val shader: String)

    companion object {
        const val IMPORT_REQUEST = 48129
        private const val SETTING = "slang_preset_path"
        private const val MAX_FILES = 10000
        private const val MAX_BYTES = 256L * 1024 * 1024
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private const val BUILTIN_VERSION = "e1d7563-v4"
        private val BUILTINS = listOf(
            Builtin("sharp-bilinear", R.string.kairo_shader_sharp_bilinear, "pixel-art-scaling/sharp-bilinear.slangp",
                "pixel-art-scaling/shaders/sharp-bilinear.slang"),
            Builtin("sharp-bilinear-scanlines", R.string.kairo_shader_sharp_bilinear_scanlines,
                "pixel-art-scaling/sharp-bilinear-scanlines.slangp",
                "pixel-art-scaling/shaders/sharp-bilinear-scanlines.slang"),
            Builtin("crt-easymode", R.string.kairo_shader_crt_easymode, "crt/crt-easymode.slangp",
                "crt/shaders/crt-easymode.slang")
        )
    }

    private val root get() = File(activity.filesDir, "slang-shaders")
    private val builtinRoot get() = File(activity.filesDir, "slang-builtins/$BUILTIN_VERSION")

    fun selectedPath(): String? {
        val value = preferences.getString(SETTING, null) ?: return null
        val builtin = BUILTINS.firstOrNull { value == "builtin:${it.id}" }
        if (value.startsWith("builtin:") && builtin == null) {
            preferences.edit().remove(SETTING).apply()
            return null
        }
        if (builtin != null) return try { materialize(builtin).absolutePath }
            catch (_: IOException) { null }
        return value.takeIf { File(it).isFile }
    }

    fun label(): String {
        val value = preferences.getString(SETTING, null)
        return BUILTINS.firstOrNull { value == "builtin:${it.id}" }?.let { str(it.title) }
            ?: selectedPath()?.let { File(it).nameWithoutExtension } ?: str(R.string.kairo_off)
    }

    private fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)

    /** Read on first use and dropped when an import ends; only the main thread touches it. */
    private var tree: ShaderPresetTree? = null

    private fun presets() = tree ?: ShaderPresetTree.read(root).also { tree = it }

    private fun presetLabel(file: File) = file.relativeTo(root).path.substringAfter(File.separatorChar)

    private fun options(files: List<File>) = files.map { ChoiceOption(it.absolutePath, it.nameWithoutExtension) }

    private fun folder(folder: ShaderPresetTree.Folder): ChoiceFolder = ChoiceFolder(folder.path, folder.label) {
        listOf(ChoiceGroup(null, options(folder.presets), folders = folder.folders.map(::folder)))
    }

    /** The Shader choice: BUILT-IN presets, then IMPORTED ones by folder. */
    fun choice() = ChoiceSpec(str(R.string.kairo_shader), { GameSource.ALL_GAMES }, {
        val imported = presets()
        listOfNotNull(
            ChoiceGroup(str(R.string.kairo_shader_group_built_in), listOf(ChoiceOption("off", UiText.res(R.string.kairo_off))) +
                BUILTINS.map { ChoiceOption("builtin:${it.id}", UiText.res(it.title)) }),
            imported.takeIf { !it.isEmpty }?.let {
                ChoiceGroup(str(R.string.kairo_shader_group_imported), options(it.presets), folders = it.folders.map(::folder))
            })
    }, {
        val selected = preferences.getString(SETTING, null)
        when {
            selected == null -> "off"
            BUILTINS.any { selected == "builtin:${it.id}" } -> selected
            selected in presets().paths -> selected
            else -> "off"
        }
    }, { id ->
        when {
            id == "off" -> select(null)
            id.startsWith("builtin:") -> BUILTINS.firstOrNull { "builtin:${it.id}" == id }?.let(::selectBuiltin)
            else -> select(id)
        }
    })

    /** The SHADER group: the choice and the import action that adds to it. */
    fun group() = RowGroup(str(R.string.kairo_shader_group), listOf(
        SettingsRow.choice("shader", str(R.string.kairo_shader), choice(), value = { label() }),
        SettingsRow.action("shader-import", str(R.string.kairo_shader_import),
            value = { if (importing) str(R.string.kairo_shader_importing) else "" }, busy = { importing },
            enabled = { !importing }, disabledReason = { str(R.string.kairo_firmware_wait) },
            run = ::importZip)),
        str(R.string.kairo_shader_import_foot))

    private fun selectBuiltin(builtin: Builtin) {
        try {
            val path = materialize(builtin).absolutePath
            preferences.edit().putString(SETTING, "builtin:${builtin.id}").apply()
            onSelected(path)
        } catch (error: IOException) {
            reportError(error.message ?: str(R.string.kairo_shader_builtin_failed))
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
        if (importing) return true
        importing = true
        pages()?.refresh()
        Thread {
            val result = try { importPack(uri) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                importing = false
                tree = null
                result.fold(onSuccess = { files ->
                    // One preset is selected at once; with several, the user picks from the list.
                    if (files.size == 1) {
                        select(files.single().absolutePath)
                        Ui.message(activity, str(R.string.kairo_shader_set, presetLabel(files.single())))
                    } else pages()?.let { stack ->
                        if (stack.top is GroupsPage) stack.push(ChoicePage(choice(), "shader"))
                    }
                }, onFailure = { error ->
                    reportError(error.message ?: str(R.string.kairo_shader_import_failed))
                })
                pages()?.refresh()
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
                            throw IOException(str(R.string.kairo_shader_zip_unsafe))
                        val relative = parts.filter { it.isNotEmpty() && it != "." }
                        if (relative.isEmpty()) continue
                        val target = File(destination, relative.joinToString(File.separator))
                        if (++count > MAX_FILES) throw IOException(str(R.string.kairo_shader_zip_too_many))
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
                                    throw IOException(str(R.string.kairo_shader_zip_too_large))
                                output.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException(str(R.string.kairo_shader_zip_unreadable))
            val presets = destination.walkTopDown().filter { it.isFile &&
                it.extension.equals("slangp", ignoreCase = true) }.toList()
            if (presets.isEmpty()) throw IOException(str(R.string.kairo_shader_zip_no_presets))
            return Result.success(presets)
        } catch (error: Exception) {
            destination.deleteRecursively()
            return Result.failure(error)
        }
    }
}
