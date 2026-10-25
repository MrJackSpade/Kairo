package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent

/**
 * The library flyout and its Manage library pages, shared by every Kairo app. Each app
 * supplies its SETTINGS rows and how it exits.
 */
class LibraryMenu<T : LibraryItem>(
    private val activity: Activity,
    private val pages: PageStack,
    private val flow: LibraryFlow<T>,
    private val catalog: LibraryCatalog,
    private val strings: LibraryStrings,
    private val updateCatalogs: () -> Unit,
    private val artwork: () -> CatalogArtworkDownloadController<T, *>?,
    private val settings: () -> List<SettingsRow>,
    private val exit: () -> Unit
) {
    private val screen get() = flow.screen
    private var catalogState: CatalogUpdateState? = null
    private val installer by lazy {
        catalog.installedCatalogs?.let { store ->
            CatalogInstallController(activity, store, { screen.refreshCatalog(); pages.refresh() }, {}) { text ->
                if (text == null) screen.tasks.hide(FILE_TASK) else screen.tasks.show(FILE_TASK, text, 2)
                pages.refresh()
            }
        }
    }

    fun handleActivityResult(request: Int, result: Int, data: Intent?): Boolean =
        installer?.handleActivityResult(request, result, data) ?: false

    /** The catalog check reports here, from the Update catalogs row or the launch-time check. */
    fun showCatalogProgress(state: CatalogUpdateState?) {
        catalogState = state
        if (state?.busy == true) screen.tasks.show(CHECK_TASK, state.label, 2) else screen.tasks.hide(CHECK_TASK)
        pages.refresh()
    }

    fun open() {
        if (pages.isActive) return
        screen.dismissSearchKeyboard()
        pages.push(FlyoutPage(FlyoutModel(strings.productName,
            { screen.folderName ?: strings.noFolder }, ::status, { flow.scanning }) {
            listOf(
                RowGroup("LIBRARY", listOf(
                    SettingsRow.action("f-refresh", "Refresh", busy = { flow.scanning && !flow.rehashing }) {
                        if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FOLDER, announce = true)
                    },
                    SettingsRow.nav("f-manage", "Manage library") { pages.push(managePage()) })),
                RowGroup("SETTINGS", settings()),
                RowGroup(null, listOf(SettingsRow.action("f-exit", "Exit ${strings.appName}", run = exit))))
        }))
    }

    private fun status(): String {
        if (flow.tree == null) return "Choose one in Manage library"
        if (flow.scanning || screen.working) return screen.status
        if (screen.needsFolder()) return screen.status
        val entries = flow.entries
        val unreadable = entries.count { it.error != null }
        return "${strings.folder} · ${entries.count { it.playable }} games" +
            if (unreadable == 0) "" else " · $unreadable unreadable"
    }

    private fun managePage() = GroupsPage("Manage library", content = {
        val groups = mutableListOf(
            RowGroup("SOURCE", listOf(
                SettingsRow.action("folder", strings.folder,
                    value = { screen.folderName ?: "None" },
                    tone = { if (screen.folderName == null) ValueTone.EMPTY else ValueTone.CURRENT },
                    tray = { TraySpec(buttons = listOf(TrayButton("Choose folder…", ButtonTone.PRIMARY) {
                        flow.chooseFolder()
                    })) }),
                SettingsRow.action("refresh", "Refresh",
                    value = { if (flow.scanning && !flow.rehashing) "Scanning…"
                        else if (flow.tree == null) "" else "${flow.entries.count { it.playable }} games" },
                    busy = { flow.scanning && !flow.rehashing }) {
                    if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FOLDER, announce = true)
                },
                SettingsRow.action("recheck", "Recheck all files",
                    value = { if (flow.rehashing) "Checking…" else "" },
                    busy = { flow.rehashing }) {
                    if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FILE, announce = true)
                }),
                "Refresh finds added and removed games. Recheck all files identifies every file again."),
            RowGroup("CATALOGS", listOfNotNull(
                SettingsRow.action("catalogs", "Update catalogs",
                    value = {
                        val state = catalogState
                        when {
                            state == null -> ""
                            state.busy -> "Checking…"
                            state == CatalogUpdateState.FAILED -> "Failed"
                            state == CatalogUpdateState.UPDATED -> "Updated"
                            else -> "Up to date"
                        }
                    },
                    busy = { catalogState?.busy == true }, run = updateCatalogs),
                installer?.let { install ->
                    SettingsRow.action("catalog-import", "Import catalog file…",
                        enabled = { !install.busy }, disabledReason = { "Wait for the current catalog change to finish" }) {
                        install.chooseFile()
                    }
                },
                installer?.let { install ->
                    SettingsRow.nav("catalog-remove", "Remove catalog",
                        value = { install.catalogs().size.let { if (it == 0) "None installed" else "$it installed" } },
                        tone = { if (install.catalogs().isEmpty()) ValueTone.EMPTY else ValueTone.SUMMARY },
                        enabled = { install.catalogs().isNotEmpty() },
                        disabledReason = { "No installed catalogs" }) { pages.push(removePage()) }
                })))
        artwork()?.let { images ->
            groups += RowGroup("ARTWORK", listOf(
                SettingsRow.action("images", "Download missing images",
                    value = { images.progress }, busy = { images.running },
                    tray = if (images.running) ({ TraySpec(buttons = listOf(TrayButton("Stop") { images.cancel() })) })
                        else null) {
                    images.start()
                }))
        }
        groups
    })

    private fun removePage(): GroupsPage {
        lateinit var page: GroupsPage
        page = GroupsPage("Remove catalog", { "All games" }, {
            val install = installer ?: return@GroupsPage emptyList()
            listOf(RowGroup("INSTALLED", install.catalogs().map { item ->
                SettingsRow.action("catalog:${item.id}", item.name,
                    enabled = { !install.busy }, disabledReason = { "Wait for the current catalog change to finish" },
                    confirm = Confirm("Removes this catalog’s metadata and artwork. Your games and personal settings are kept.",
                        "Remove") {
                        install.remove(item) {
                            if (install.catalogs().isEmpty() && pages.top === page) pages.pop()
                        }
                    })
            }))
        })
        return page
    }

    private companion object {
        const val CHECK_TASK = "catalog"
        const val FILE_TASK = "catalog-file"
    }
}
