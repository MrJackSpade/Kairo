package com.mrjackspade.kairo.frontend

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

    fun settingsRows(settings: Settings) = listOf(
        SettingsRow.nav("f-graphics", "Graphics", open = settings.graphics),
        SettingsRow.nav("f-controls", "Controls", open = settings.controls),
        SettingsRow.nav("f-machine", "Machine", open = settings.machine),
        SettingsRow.nav("f-configurations", "Configurations", open = settings.configurations),
        SettingsRow.switch("f-sound", "Sound", settings.soundOn, set = settings.setSound),
        SettingsRow.nav("f-about", "About", open = settings.about))

    /** [saves] names what the save folder holds, such as SAVED GAMES or SAVE STATES. */
    fun configurationsPage(orientation: GameOrientation, saveFolder: SaveFolderSettings, saves: String) =
        GroupsPage("Configurations", { GameSource.ALL_GAMES }, {
            listOf(RowGroup("DISPLAY", listOf(orientation.row())),
                RowGroup(saves, listOf(saveFolder.row())))
        })

    /**
     * The flyout over a running game. [gameRows] come before Game settings, which needs a
     * library game: [openGameSettings] returns false when there is none.
     */
    fun gameFlyout(product: String, appName: String, title: () -> String, status: () -> String,
                   navigation: SessionNavigationCoordinator, gameRows: () -> List<SettingsRow>,
                   hasGameSettings: () -> Boolean, openGameSettings: () -> Unit,
                   settingsRows: () -> List<SettingsRow>, restart: () -> Unit, exit: () -> Unit) =
        FlyoutModel(product, title, status) {
            listOf(
                RowGroup("GAME", gameRows() + SettingsRow.nav("f-game", "Game settings",
                    enabled = hasGameSettings,
                    disabledReason = { "Game settings are available for games started from the library." },
                    open = openGameSettings)),
                RowGroup("SETTINGS", settingsRows()),
                RowGroup(null, listOf(
                    SettingsRow.action("f-restart", "Restart game",
                        confirm = Confirm("Progress since your last save is lost.", "Restart", run = restart)),
                    navigation.returnRow(appName),
                    SettingsRow.action("f-exit", "Exit $appName",
                        confirm = Confirm("The machine stops. Progress since your last save is lost.", "Exit",
                            title = "Exit $appName?", run = exit)))))
        }
}
