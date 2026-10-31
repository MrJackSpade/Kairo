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
             shortcutNote: String?, credits: String, privacyAsset: String, noticesAssets: List<String>,
             diagnostics: (() -> Page)? = null): GroupsPage {
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        return GroupsPage(activity.getString(R.string.kairo_about), content = {
            listOf(
                RowGroup(activity.getString(R.string.kairo_about_app_group), listOfNotNull(
                    SettingsRow.info("version", activity.getString(R.string.kairo_about_version)) { version },
                    diagnostics?.let { open -> SettingsRow.nav("diagnostics", activity.getString(R.string.kairo_diagnostics)) { pages.push(open()) } })),
                RowGroup(activity.getString(R.string.kairo_about_shortcuts_group), shortcuts.mapIndexed { index, (label, value) ->
                    SettingsRow.info("shortcut$index", label) { value }
                }, shortcutNote),
                RowGroup(activity.getString(R.string.kairo_about_legal_group), listOf(
                    SettingsRow.nav("licenses", activity.getString(R.string.kairo_about_licenses)) {
                        pages.push(document(activity, activity.getString(R.string.kairo_about_licenses),
                            noticesAssets + FRONTEND_NOTICES, Ui.LABEL))
                    },
                    SettingsRow.nav("privacy", activity.getString(R.string.kairo_about_privacy)) {
                        pages.push(document(activity, activity.getString(R.string.kairo_about_privacy), listOf(privacyAsset), Ui.SECONDARY))
                    }), credits))
        })
    }

    fun document(activity: Activity, title: String, assets: List<String>, textSize: Float): DocumentPage {
        val content = try {
            assets.joinToString("\n\n") { asset ->
                activity.assets.open(asset).bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            activity.getString(R.string.kairo_about_document_failed)
        }
        return DocumentPage(title, content, textSize)
    }
}
