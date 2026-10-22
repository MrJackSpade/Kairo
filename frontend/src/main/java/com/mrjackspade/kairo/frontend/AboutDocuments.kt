package com.mrjackspade.kairo.frontend

import android.app.Activity

/** The shared About page and offline text documents. The host supplies its own content. */
object AboutDocuments {
    /**
     * The notices every app carries through the frontend, after the app's own: its Java libraries
     * and font, the NDK's LLVM runtime (generated from the NDK at build time), the shader runtime
     * when the app has it, and AndroidX.
     */
    val FRONTEND_NOTICES = listOfNotNull("FRONTEND_NOTICES.txt", "NDK_NOTICES.txt",
        "SLANG_RUNTIME_NOTICES.txt".takeIf { BuildConfig.SHADERS }, "ANDROIDX_NOTICES.txt")

    fun page(activity: Activity, pages: PageStack, shortcuts: List<Pair<String, String>>,
             shortcutNote: String?, credits: String, privacyAsset: String, noticesAsset: String,
             diagnostics: (() -> Page)? = null): GroupsPage {
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        return GroupsPage("About", content = {
            listOf(
                RowGroup("APP", listOfNotNull(
                    SettingsRow.info("version", "Version") { version },
                    diagnostics?.let { open -> SettingsRow.nav("diagnostics", "Diagnostics") { pages.push(open()) } })),
                RowGroup("SHORTCUTS", shortcuts.mapIndexed { index, (label, value) ->
                    SettingsRow.info("shortcut$index", label) { value }
                }, shortcutNote),
                RowGroup("LEGAL", listOf(
                    SettingsRow.nav("licenses", "Third-party licenses") {
                        pages.push(document(activity, "Third-party licenses",
                            listOf(noticesAsset) + FRONTEND_NOTICES, Ui.LABEL))
                    },
                    SettingsRow.nav("privacy", "Privacy policy") {
                        pages.push(document(activity, "Privacy policy", listOf(privacyAsset), Ui.SECONDARY))
                    }), credits))
        })
    }

    fun document(activity: Activity, title: String, assets: List<String>, textSize: Float): DocumentPage {
        val content = try {
            assets.joinToString("\n\n") { asset ->
                activity.assets.open(asset).bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            "This document could not be opened."
        }
        return DocumentPage(title, content, textSize)
    }
}
