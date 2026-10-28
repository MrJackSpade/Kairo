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
        if (state?.busy == true) screen.tasks.show(CHECK_TASK, activity.getString(state.label), 2) else screen.tasks.hide(CHECK_TASK)
        pages.refresh()
    }

    fun open() {
        if (pages.isActive) return
        screen.dismissSearchKeyboard()
        pages.push(FlyoutPage(FlyoutModel(strings.productName,
            { screen.folderName ?: strings.noFolder.resolve(activity) }, ::status, { flow.scanning }) {
            listOf(
                RowGroup(activity.getString(R.string.kairo_library_group), listOf(
                    SettingsRow.action("f-refresh", activity.getString(R.string.kairo_library_refresh), busy = { flow.scanning && !flow.rehashing }) {
                        if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FOLDER, announce = true)
                    },
                    SettingsRow.nav("f-manage", activity.getString(R.string.kairo_library_manage)) { pages.push(managePage()) })),
                RowGroup(activity.getString(R.string.kairo_flyout_settings_group), settings()),
                RowGroup(null, listOf(SettingsRow.action("f-exit", activity.getString(R.string.kairo_flyout_exit, strings.appName), run = exit))))
        }))
    }

    private fun status(): String {
        if (flow.tree == null) return activity.getString(R.string.kairo_library_choose_in_manage)
        if (flow.scanning || screen.working) return screen.status
        if (screen.needsFolder()) return screen.status
        val entries = flow.entries
        val unreadable = entries.count { it.error != null }
        val folder = strings.folder.resolve(activity)
        val games = LibraryScanSummary.games(activity, entries)
        return if (unreadable == 0) activity.getString(R.string.kairo_library_status, folder, games)
            else activity.getString(R.string.kairo_library_status_unreadable, folder, games,
                LibraryScanSummary.unreadable(activity, unreadable))
    }

    private fun managePage() = GroupsPage(activity.getString(R.string.kairo_library_manage), content = {
        val groups = mutableListOf(
            RowGroup(activity.getString(R.string.kairo_library_source_group), listOf(
                SettingsRow.action("folder", strings.folder.resolve(activity),
                    value = { screen.folderName ?: activity.getString(R.string.kairo_none) },
                    tone = { if (screen.folderName == null) ValueTone.EMPTY else ValueTone.CURRENT },
                    tray = { TraySpec(buttons = listOf(TrayButton(activity.getString(R.string.kairo_library_choose_folder), ButtonTone.PRIMARY) {
                        flow.chooseFolder()
                    })) }),
                SettingsRow.action("refresh", activity.getString(R.string.kairo_library_refresh),
                    value = { if (flow.scanning && !flow.rehashing) activity.getString(R.string.kairo_library_scanning)
                        else if (flow.tree == null) "" else LibraryScanSummary.games(activity, flow.entries) },
                    busy = { flow.scanning && !flow.rehashing }) {
                    if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FOLDER, announce = true)
                },
                SettingsRow.action("recheck", activity.getString(R.string.kairo_library_recheck),
                    value = { if (flow.rehashing) activity.getString(R.string.kairo_checking) else "" },
                    busy = { flow.rehashing }) {
                    if (!flow.scanning) flow.refresh(ScanDepth.EVERY_FILE, announce = true)
                }),
                activity.getString(R.string.kairo_library_source_foot)),
            RowGroup(activity.getString(R.string.kairo_catalogs_group), listOfNotNull(
                SettingsRow.action("catalogs", activity.getString(R.string.kairo_catalogs_update),
                    value = {
                        val state = catalogState
                        when {
                            state == null -> ""
                            state.busy -> activity.getString(R.string.kairo_checking)
                            state == CatalogUpdateState.FAILED -> activity.getString(R.string.kairo_catalogs_failed)
                            state == CatalogUpdateState.UPDATED -> activity.getString(R.string.kairo_catalogs_updated_value)
                            else -> activity.getString(R.string.kairo_catalogs_up_to_date)
                        }
                    },
                    busy = { catalogState?.busy == true }, run = updateCatalogs),
                installer?.let { install ->
                    SettingsRow.action("catalog-import", activity.getString(R.string.kairo_catalogs_import),
                        enabled = { !install.busy }, disabledReason = { activity.getString(R.string.kairo_catalogs_wait) }) {
                        install.chooseFile()
                    }
                },
                installer?.let { install ->
                    SettingsRow.nav("catalog-remove", activity.getString(R.string.kairo_catalogs_remove),
                        value = { install.catalogs().size.let { if (it == 0) activity.getString(R.string.kairo_catalogs_none_installed)
                            else activity.resources.getQuantityString(R.plurals.kairo_catalogs_installed, it, it) } },
                        tone = { if (install.catalogs().isEmpty()) ValueTone.EMPTY else ValueTone.SUMMARY },
                        enabled = { install.catalogs().isNotEmpty() },
                        disabledReason = { activity.getString(R.string.kairo_catalogs_none) }) { pages.push(removePage()) }
                })))
        artwork()?.let { images ->
            groups += RowGroup(activity.getString(R.string.kairo_artwork_group), listOf(
                SettingsRow.action("images", activity.getString(R.string.kairo_artwork_download),
                    value = { images.progress }, busy = { images.running },
                    tray = if (images.running) ({ TraySpec(buttons = listOf(TrayButton(activity.getString(R.string.kairo_task_stop)) { images.cancel() })) })
                        else null) {
                    images.start()
                }))
        }
        groups
    })

    private fun removePage(): GroupsPage {
        lateinit var page: GroupsPage
        page = GroupsPage(activity.getString(R.string.kairo_catalogs_remove), { GameSource.ALL_GAMES }, {
            val install = installer ?: return@GroupsPage emptyList()
            listOf(RowGroup(activity.getString(R.string.kairo_catalogs_installed_group), install.catalogs().map { item ->
                SettingsRow.action("catalog:${item.id}", item.name,
                    enabled = { !install.busy }, disabledReason = { activity.getString(R.string.kairo_catalogs_wait) },
                    confirm = Confirm(activity.getString(R.string.kairo_catalogs_remove_confirm),
                        activity.getString(R.string.kairo_remove)) {
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
