package com.mrjackspade.kairo.frontend

import android.content.Context

/** The menus every Kairo app shows. Apps supply their pages and actions, never the rows. */
object SessionMenus {
    /** The SETTINGS rows of the game flyout and the library flyout. */
    class Settings(
        val graphics: () -> Unit,
        val controls: () -> Unit,
        val machine: () -> Unit,
        val configurations: () -> Unit,
        val soundOn: () -> Boolean,
        val setSound: (Boolean) -> Unit,
        val about: () -> Unit
    )

    fun settingsRows(context: Context, settings: Settings) = listOf(
        SettingsRow.nav("f-graphics", context.getString(R.string.kairo_graphics), open = settings.graphics),
        SettingsRow.nav("f-controls", context.getString(R.string.kairo_controls), open = settings.controls),
        SettingsRow.nav("f-machine", context.getString(R.string.kairo_machine), open = settings.machine),
        SettingsRow.nav("f-configurations", context.getString(R.string.kairo_configurations), open = settings.configurations),
        SettingsRow.switch("f-sound", context.getString(R.string.kairo_sound), settings.soundOn, set = settings.setSound),
        SettingsRow.nav("f-about", context.getString(R.string.kairo_about), open = settings.about))

    /** [saves] names what the save folder holds, such as SAVED GAMES or SAVE STATES. */
    fun configurationsPage(context: Context, orientation: GameOrientation, saveFolder: SaveFolderSettings, saves: String) =
        GroupsPage(context.getString(R.string.kairo_configurations), { GameSource.ALL_GAMES }, {
            listOf(RowGroup(context.getString(R.string.kairo_display_group), listOf(orientation.row())),
                RowGroup(saves, listOf(saveFolder.row())))
        })

    /**
     * The flyout over a running game. [gameRows] come before Game settings, which needs a
     * library game: [openGameSettings] returns false when there is none.
     */
    fun gameFlyout(context: Context, product: String, appName: String, title: () -> String, status: () -> String,
                   navigation: SessionNavigationCoordinator, gameRows: () -> List<SettingsRow>,
                   hasGameSettings: () -> Boolean, openGameSettings: () -> Unit,
                   settingsRows: () -> List<SettingsRow>, restart: () -> Unit, exit: () -> Unit) =
        FlyoutModel(product, title, status) {
            listOf(
                RowGroup(context.getString(R.string.kairo_flyout_game_group), gameRows() +
                    SettingsRow.nav("f-game", context.getString(R.string.kairo_game_settings),
                    enabled = hasGameSettings,
                    disabledReason = { context.getString(R.string.kairo_flyout_game_settings_library_only) },
                    open = openGameSettings)),
                RowGroup(context.getString(R.string.kairo_flyout_settings_group), settingsRows()),
                RowGroup(null, listOf(
                    SettingsRow.action("f-restart", context.getString(R.string.kairo_flyout_restart),
                        confirm = Confirm(context.getString(R.string.kairo_flyout_restart_confirm),
                            context.getString(R.string.kairo_action_restart), run = restart)),
                    navigation.returnRow(context, appName),
                    SettingsRow.action("f-exit", context.getString(R.string.kairo_flyout_exit, appName),
                        confirm = Confirm(context.getString(R.string.kairo_flyout_exit_confirm),
                            context.getString(R.string.kairo_action_exit),
                            title = context.getString(R.string.kairo_flyout_exit_title, appName), run = exit)))))
        }
}
