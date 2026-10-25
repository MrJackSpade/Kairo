package com.mrjackspade.kairo.frontend

import android.content.Intent
import android.net.Uri
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [KairoActivity] for a machine that runs one guest frame at a time ([MachineBridge]), with a
 * layered catalog ([LayeredGameCatalog]), save states, and game settings kept as catalog
 * overrides. A product supplies its machine, media, keyboard and settings pages.
 */
abstract class MachineActivity<E : LibraryItem> : KairoActivity<E>() {
    /** The guest's screen and its save-state file. */
    class Machine(
        val screenWidth: Int,
        val screenHeight: Int,
        /** The displayed width over height. */
        val aspect: Double,
        /** The file a save state's directory holds; a slot without it is empty. */
        val stateFile: String
    )

    protected abstract val machine: Machine
    protected abstract val bridge: MachineBridge
    protected abstract fun createCatalog(): LayeredGameCatalog
    protected abstract fun createLibrary(catalog: LayeredGameCatalog): MachineLibrary<E>
    /** Whether this build may download catalog artwork (the product's build flavor decides). */
    protected abstract val artworkDownloadsEnabled: Boolean
    /** What tapping the screen does in Keyboard mode, as the Screen tap choice describes it. */
    protected open val keyboardTapHelp: String get() = "Tap opens the ${identity.guestName} keyboard"

    /**
     * Runs on a worker thread: prepares [entry]'s media and starts the machine with [bridge],
     * reporting what it does through [progress]. Throws to fail the launch; the message is shown.
     */
    protected abstract fun startGame(entry: E, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit)

    /** Rows in the game flyout's GAME group after Save state and Load state, such as Disks. */
    protected open fun machineRows(): List<SettingsRow> = emptyList()

    /** Why a save or load failed, for a nonzero code the bridge returned. */
    protected abstract fun stateError(code: Int): String

    /** A state loaded: the product rereads what the machine now holds, such as its disks. */
    protected open fun stateLoaded() {}

    /** A load the machine refused with [code], as when its media must be started again. */
    protected open fun loadRejected(code: Int) {}

    /** The session's game started; the product may begin its own work, such as typing a command. */
    protected open fun gameStarted(entry: E, game: CatalogGame) {}

    // What most machines share: firmware, media drives, and machine options -----------------------

    /** The machine's choices, each a game setting and an all-games default in Machine. */
    protected open val machineOptions: List<MachineOption> = emptyList()

    /** The machine's firmware, by set label: each file its own row (firmwareGroups); empty for none. */
    protected open val firmwareSets: List<Pair<String, FirmwareSet>> = emptyList()
    /** The first-run step's title and text, and the note under the firmware rows; the same in every app. */
    private val firmwareText: Triple<String, String, String>
        get() = Triple("${identity.guestName} BIOS", "Pick each file below, or import them all from a folder.",
            "Files marked Required are needed to start games.")

    /** The media slots a game's own disks go in, shown on the flyout's media page. */
    protected open val mediaSlots: List<MediaSlot<E>> = emptyList()
    protected open val mediaPageTitle = "Disks"

    /** A slot of the machine, such as a floppy drive, and the library entries it takes. */
    class MediaSlot<E>(val label: String, val accepts: (E) -> Boolean = { true })

    // ---------------------------------------------------------------------------------------

    final override val catalog: LayeredGameCatalog by lazy { createCatalog() }
    protected val library: MachineLibrary<E> by lazy { createLibrary(catalog) }
    /** The running game's catalog metadata. */
    protected var currentGame: CatalogGame? = null
        private set
    protected val machineRunning: Boolean get() = currentEntry != null && MachineStatus(bridge.status()).running

    override fun sendKey(code: Int, down: Boolean) = bridge.key(code, down)
    override fun sendJoystick(control: Int, down: Boolean) = bridge.joystick(control, down)
    override fun sendMouseMove(dx: Int, dy: Int) = bridge.mouseMove(dx, dy)
    override fun sendMouseButton(button: Int, down: Boolean) = bridge.mouseButton(button, down)
    override fun inputTelemetry(): LongArray? = bridge.inputTelemetry()
    override fun pauseGuest(paused: Boolean) = bridge.pause(paused)
    override fun stopGuest() = bridge.stop()
    override fun setMuted(muted: Boolean) = bridge.setMuted(muted)
    override fun setFastForward(enabled: Boolean) = bridge.setFastForward(enabled)
    override fun setGuestSurface(surface: Surface?) = bridge.setSurface(surface)
    override fun setShaderPreset(path: String?) = bridge.setShaderPreset(path)
    override fun videoSize() = Triple(machine.screenWidth, machine.screenHeight, machine.aspect)

    override fun cachedLibrary(tree: Uri) = library.cached(tree)
    override fun scanLibrary(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean, progress: (String) -> Unit) =
        library.scan(tree, depth, cancelled, progress)
    override fun storeLibrary(tree: Uri, entries: List<E>) = library.store(tree, entries)
    override val hashCount: Int get() = library.hashCount
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean) = library.inspectExternal(file, cancelled)

    override val artworkStore: CatalogArtworkStore get() = catalog.artworkStore
    override fun artworkRecord(entry: E, kind: String): ArtworkCoordinator.Record {
        val game = catalog.resolve(entry)
        val urlKind = if (kind == "preview") "previewUrl" else "boxArtUrl"
        val url = (if (kind == "preview") game.previewUrl else game.boxArtUrl)
            ?.takeIf { catalog.sourceOf(entry, "artwork", kind) == catalog.sourceOf(entry, "artwork", urlKind) }
        return ArtworkCoordinator.Record(if (kind == "preview") game.preview else game.boxArt, url,
            catalog.sourceOf(entry, "artwork", kind))
    }
    override fun validArtworkPath(path: String) = ArtworkOverridePath.valid(path, "art/")
    override fun saveArtworkOverride(game: GameKey, kind: String, path: String?) =
        if (path == null) catalog.resetArtworkOverride(game, kind) else catalog.setArtworkOverride(game, kind, path)
    override fun validImageUrl(url: String) = catalog.validImageUrl(url)
    override fun createArtworkDownloads(screen: LibraryScreen<E>, entries: () -> List<E>) =
        if (!artworkDownloadsEnabled) null
        else CatalogArtworkDownloadController(this, screen, entries, catalog::missingArtworkFor,
            catalog::downloadArtwork) { pages.refresh() }
    override fun downloadCatalogUpdate(task: CatalogUpdateTask) = catalog.downloadUpdate(task)
    override val installedCatalogs: InstalledCatalogs get() = catalog.installedCatalogs
    override fun titleOf(entry: E) = catalog.resolve(entry).title

    override fun gameBindings(entry: E): List<ControllerBinding> = effectiveControllerBindings(catalog.resolve(entry))
    override fun saveGameBindings(game: GameKey, bindings: List<ControllerBinding>) =
        catalog.setOverrideSubfield(game, "controller", "bindings", bindingsCodec.toJson(bindings))
    override fun clearGameBindings(game: GameKey) =
        catalog.updateOverrideSubfields(game, "controller", mapOf("bindings" to null, "profile" to null))
    override fun controllerRefreshed() { currentEntry?.let { currentGame = catalog.resolve(it) } }
    override fun touchChanged() { currentEntry?.let { currentGame = catalog.resolve(it) } }
    override fun controllerSource(entry: E): String {
        val game = catalog.resolve(entry)
        return if (game.controllerBindings == null ||
            (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) GameSource.ALL_GAMES
        else GameSource.label(entry.key.takeIf { entry.contentId != null }
            ?.let { catalog.sourceOf(entry, "controller", "bindings") } ?: GameSource.ALL_GAMES)
    }
    override fun controllerMappingStatus(entry: E?): String? {
        if (controllerProfiles.configuration.layout != ControllerLayout.WITH_STICKS) return null
        val game = entry?.takeIf { it.contentId != null }?.let(catalog::resolve) ?: return null
        if (game.overriddenFields.contains("controller") || game.controllerProfile == "custom-v1") return null
        return if (game.controllerDefaultBindings != null &&
            org.json.JSONObject(game.controllerDefaultBindings).optJSONArray("withSticks") == null)
            "Using Without Sticks fallback" else null
    }

    /** A game's bindings: the catalog's defaults for the controller layout, its own, or all games'. */
    private fun effectiveControllerBindings(game: CatalogGame?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        if (game != null && !game.overriddenFields.contains("controller") && game.controllerDefaultBindings != null) {
            val defaults = org.json.JSONObject(game.controllerDefaultBindings)
            val bindings = defaults.optJSONArray(controllerProfiles.configuration.layout.key)
                ?: defaults.optJSONArray("withoutSticks")
            if (bindings != null)
                return ControllerDefaults.layered(bindingsCodec.parse(bindings.toString()), controllerFlow.global())
        }
        return when {
            // The player's own mapping, exactly as they set it.
            game?.overriddenFields?.contains("controller") == true -> bindingsCodec.parse(configured)
            configured != null && (configured != "[]" || game.controllerProfile == "custom-v1") ->
                ControllerDefaults.layered(bindingsCodec.parse(configured), controllerFlow.global())
            else -> controllerFlow.global()
        }
    }

    override val touchModes get() = listOf(
        InputModeDecider.Mode.AUTO to ChoiceOption("auto", "Auto", "Follows what the game reads"),
        InputModeDecider.Mode.KEYBOARD to ChoiceOption("keyboard", "Keyboard", keyboardTapHelp),
        InputModeDecider.Mode.MOUSE to ChoiceOption("mouse", "Mouse"))
    override val directTouchExplanation = "Direct tap lands on the touched point in games that move the " +
        "cursor one pixel per mouse count and stop it at the screen’s top-left edge."
    override val touchStore: TouchSettingsStore<E> by lazy {
        object : TouchSettingsStore<E> {
            override fun global() = TouchInputSelection(
                InputModeDecider.parse(preferences.getString("input_mode", "auto")),
                preferences.getString("touch_mouse", "touchpad") == "direct")
            override fun saveGlobal(value: TouchInputSelection) {
                preferences.edit().putString("input_mode", InputModeDecider.storageValue(value.mode))
                    .putString("touch_mouse", touchStorage(value.directTouch)).apply()
            }
            override fun mode(game: E) = inputValue(game, "mode", { it.inputMode },
                InputModeDecider::parse, InputModeDecider::storageValue) { global().mode }
            override fun directTouch(game: E) = inputValue(game, "touch", { it.inputTouch },
                { it == "direct" }, ::touchStorage) { global().directTouch }
            override fun secondaryTouchpad(game: E) = inputValue(game, "secondary",
                { it.inputSecondary }, { it == "touchpad" }, { if (it) "touchpad" else "keyboard" },
                builtIn = true) { false }
        }
    }

    private fun touchStorage(direct: Boolean) = if (direct) "direct" else "touchpad"

    /**
     * A per-game input choice: the user's override over the catalog, which falls back to the
     * all-games setting, or to the app for the second screen.
     */
    private fun <V> inputValue(game: E, subfield: String, read: (CatalogGame) -> String?,
                               parse: (String) -> V, store: (V) -> String, builtIn: Boolean = false,
                               global: () -> V): GameValue<V> {
        val id = game.key
        fun custom() = catalog.sourceOf(game, "input", subfield) == "User override"
        return GameValue({ if (custom()) read(catalog.resolve(game))?.let(parse) else null },
            { read(catalog.resolveDefault(game))?.let(parse) ?: global() },
            { catalog.defaultSourceOf(game, "input", subfield).let { source ->
                if (source == "App default") if (builtIn) GameSource.BUILT_IN else GameSource.ALL_GAMES
                else GameSource.label(source)
            } },
            { value ->
                if (value == null) catalog.resetOverrideSubfield(id, "input", subfield)
                else catalog.setOverrideSubfield(id, "input", subfield, store(value))
            })
    }

    override fun titleCustom(entry: E) = entry.contentId != null && catalog.sourceOf(entry, "title") == "User override"
    override fun titleSource(entry: E) =
        entry.key.takeIf { entry.contentId != null }?.let { GameSource.label(catalog.sourceOf(entry, "title")) } ?: "Filename"
    override fun setTitle(game: GameKey, title: String?) =
        if (title == null) catalog.resetOverride(game, "title") else catalog.setOverride(game, "title", title)
    override fun resetGameSettings(game: GameKey) = catalog.resetOverride(game)
    override fun gameSettingChanged(entry: E) {
        if (currentEntry?.id == entry.id) currentGame = catalog.resolve(entry)
    }
    override fun deletePrompt(entry: E) = GameDeletionFlow.Prompt(entry.path, "file")
    override fun deleteSource(entry: E) = library.deleteSource(entry, libraryEntries)

    /**
     * The game plays from a working copy of its disk, which holds the saves the game writes to
     * it. Resetting replaces that copy with the current disk image, as after editing the image.
     */
    override fun fileRows(entry: E): List<SettingsRow> {
        if (!library.hasWorkingImage(entry)) return emptyList()
        return listOf(SettingsRow.action("reset-disk", "Reset disk", danger = true,
            enabled = { libraryVisible || currentEntry?.id != entry.id },
            disabledReason = { "Exit this game before resetting its disk" },
            tray = {
                Confirm("Replace ${entry.displayName}'s working copy with its disk image? " +
                    "Saves the game wrote to the disk are removed.", "Reset", danger = true) {
                    try {
                        library.resetWorkingImage(entry)
                        toast("Disk reset")
                    } catch (error: Exception) {
                        toast(error.message ?: "Could not reset the disk")
                    }
                    pages.refresh()
                }.tray()
            }))
    }

    /**
     * A machine setting for [entry]: the user's override of [section].[subfield] over the catalog,
     * which falls back to [fallback] from [appDefault]. Values are stored as [toStored] makes them.
     */
    protected fun machineValue(entry: E, section: String, subfield: String, read: (CatalogGame) -> String?,
                               fallback: () -> String, appDefault: String,
                               toStored: (String) -> Any = { it }): GameValue<String> {
        val id = entry.key.takeIf { entry.contentId != null }
        fun custom() = id != null && catalog.sourceOf(entry, section, subfield) == "User override"
        return GameValue({ if (custom()) read(catalog.resolve(entry)) else null },
            { id?.let { read(catalog.resolveDefault(entry)) } ?: fallback() },
            { id?.let { catalog.defaultSourceOf(entry, section, subfield) }?.takeIf { it != "App default" }
                ?.let(GameSource::label) ?: appDefault },
            { value -> saveGameSetting(entry) {
                if (value == null) catalog.resetOverrideSubfield(id!!, section, subfield)
                else catalog.setOverrideSubfield(id!!, section, subfield, toStored(value))
            } })
    }

    override var firstRunStep: Int
        get() = preferences.getInt("onboarding_step_v1", if (tree == null) 0 else 2)
        set(value) { preferences.edit().putInt("onboarding_step_v1", value).apply() }
    override val saveUnits: Set<String> = FolderStateSlots.UNITS
    override val appSaves: File get() = File(filesDir, "states")
    override fun saveUnitLabel(unit: String) = "Slot ${unit.removePrefix("slot")}"

    override fun sessionStatus() = MachineStatus(bridge.status()).state
    override fun diagnosticsRows(running: Boolean): List<SettingsRow> {
        if (!running) return listOf(SettingsRow.info("state", "State") { "Stopped" })
        val status = MachineStatus(bridge.status())
        return listOfNotNull(
            SettingsRow.info("state", "State") { MachineStatus(bridge.status()).state },
            status.error.takeIf { it.isNotEmpty() }?.let { SettingsRow.info("error", "Error") { it } },
            SettingsRow.info("frames", "Frames") { MachineStatus(bridge.status()).frames },
            SettingsRow.info("audio", "Audio") { MachineStatus(bridge.status()).audio },
            SettingsRow.info("xruns", "Xruns") { MachineStatus(bridge.status()).xruns },
            SettingsRow.info("audio-config", "Audio config") { MachineStatus(bridge.status()).audioConfig })
    }

    private val stateFlow by lazy {
        StateSlotCoordinator(this, pages, handler,
            { currentEntry?.takeIf { it.contentId != null && !libraryVisible }?.key }, { currentGame?.title },
            { id ->
                saveFolder.folder?.let { FolderStateSlots(it, id, machine.stateFile, File(cacheDir, "state-staging")) }
                    ?: StateSlotStore(saveFolder.app.root, id, machine.stateFile)
            },
            { activeGameSurface },
            { scratch -> bridge.saveState(scratch.absolutePath) },
            ::loadMachineState, ::describeStateError, ::toast,
            {
                releaseInputs()
                inputModeDecider.reset()
                userPaused = false
                closeMenu()
            }, ::loadRejected, "Save states are available for games started from the library.")
    }

    override fun gameRows() = stateFlow.rows() + machineRows() + listOfNotNull(mediaSlots.takeIf { it.isNotEmpty() }?.let {
        SettingsRow.nav("f-media", mediaPageTitle) { pages.push(mediaPage()) }
    })

    /** A loaded state restores the lock keys it was saved with. */
    private fun loadMachineState(directory: File): Int = bridge.loadState(directory.absolutePath).also { result ->
        if (result == 0) {
            inputRouter.syncLocks(bridge.lockedKeys())
            runOnUiThread {
                mediaAfterLoad()
                stateLoaded()
                refreshSettingValues()
            }
        }
    }

    private fun describeStateError(code: Int) =
        if (code == MachineBridge.NOT_RUNNING) "the machine is not running" else stateError(code)

    override fun launch(entry: E, fromFrontend: Boolean) = startSession(entry, catalog.resolve(entry), fromFrontend)

    /** Starts [entry] on the machine, as [launch] does once the product has what it needs. */
    protected fun startSession(entry: E, game: CatalogGame, fromFrontend: Boolean) {
        val generation = beginLaunch("Preparing ${entry.displayName}…")
        val cancelled = AtomicBoolean(false)
        Thread {
            val failure = try {
                bridge.stop()
                if (current(generation)) {
                    inputRouter.resetLocks()
                    startGame(entry, game, cancelled) { message ->
                        runOnUiThread { if (current(generation)) showProgress(message) }
                    }
                }
                null
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            }
            runOnUiThread {
                if (!current(generation)) return@runOnUiThread
                if (failure == null) {
                    currentGame = game
                    // A machine that boots to a black screen (Kairo68#2) keeps the launch's message up
                    // until it shows something.
                    val blank = MachineStatus(bridge.status()).pictured == false
                    enterSession(entry, fromFrontend, if (blank) "Starting ${entry.displayName}…" else null)
                    if (blank) awaitPicture(generation)
                    watchEnd(generation, game.title)
                    gameStarted(entry, game)
                } else launchFailed(failure)
            }
        }.apply { name = "${identity.appName}-launch"; start() }
    }

    /**
     * A machine that ends by itself, as a core whose CPU halted, returns to the library and says
     * why, rather than leaving its last frame on screen. An end the frontend caused (stopping,
     * restarting or replacing the game) has moved [sessionGeneration] on, and is not reported.
     */
    private fun watchEnd(generation: Int, title: String) {
        Thread {
            val end = bridge.awaitEnd() ?: return@Thread
            runOnUiThread {
                if (!current(generation) || currentEntry == null || isDestroyed) return@runOnUiThread
                guestFinished(completed = false)
                libraryScreen.showStatus(if (end.state == "Error" && end.error.isNotEmpty()) "$title stopped: ${end.error}"
                    else "$title stopped")
            }
        }.apply { name = "${identity.appName}-session-end"; start() }
    }

    /** Ends the session's loading once the machine shows a picture, or stops without one. */
    private fun awaitPicture(generation: Int) {
        handler.postDelayed({
            if (!current(generation) || currentEntry == null) return@postDelayed
            val status = MachineStatus(bridge.status())
            if (status.pictured == true || !status.running) sessionRunning() else awaitPicture(generation)
        }, PICTURE_POLL_MILLIS)
    }

    /** Restart boots the game again from its disks, with any machine changes since it started. */
    override fun restartGame() {
        val entry = currentEntry ?: return
        launch(entry, sessionFromFrontend)
    }

    override fun sessionEnded() {
        currentGame = null
        mounted.fill(null)
    }

    /**
     * Frames, error, each media slot by its label ("media.Drive 1"), each machine option as the
     * game's settings give it ("option.basic"), what the native machine started with
     * ("machine.basic"), and what the core reports of itself ("core.tape").
     */
    override fun probeDetails(): Map<String, String> {
        val status = MachineStatus(bridge.status())
        val details = linkedMapOf("frames" to status.frames, "error" to status.error)
        mediaSlots.forEachIndexed { slot, media -> details["media.${media.label}"] = mounted[slot]?.displayName ?: "" }
        currentGame?.let { game -> optionValues(game).forEach { (id, value) -> details["option.$id"] = value } }
        (bridge as? NativeMachine)?.startedSettings?.forEach { (name, value) -> details["machine.$name"] = value }
        status.core.forEach { (name, value) -> details["core.$name"] = value }
        return details
    }

    // Machine options ---------------------------------------------------------------------------------

    /** The all-games default of [option]. */
    protected fun optionDefault(option: MachineOption): String {
        val stored = if (option.numeric) preferences.getInt(option.id, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?.toString() else preferences.getString(option.id, null)
        return stored?.takeIf { value -> option.choices.any { it.id == value } } ?: option.default
    }

    /** What [game] boots with for [option]: its own choice, else the all-games default. */
    protected fun optionValue(game: CatalogGame, option: MachineOption): String = option.read(game) ?: optionDefault(option)

    /** Every option's value for [game], by option ID. */
    protected fun optionValues(game: CatalogGame): Map<String, String> =
        machineOptions.associate { it.id to optionValue(game, it) }

    /** Direct touch is a game setting where the catalog can choose it. */
    override val directTouchAvailable: Boolean get() = catalog.schema.directTouch

    /** A row for each setting the catalog can give a game ([CatalogSetting]) that matters to [entry]. */
    override fun machineGroup(entry: E, enabled: () -> Boolean): RowGroup? {
        val scope = { titleOf(entry) }
        val noHash = { GameSettingsRows.NO_HASH }
        val rows = catalog.schema.settings.filter { setting -> entry.contentId?.let(setting.appliesTo) ?: true }
            .mapNotNull { setting ->
                when (setting) {
                    is CatalogSetting.Choice -> {
                        // The app's own option, which may offer more than the catalog's (such as importing a file).
                        val option = machineOptions.firstOrNull { it.id == setting.option.id } ?: setting.option
                        GameSettingsRows.choice(option.id, option.label, scope, { option.choices },
                            machineValue(entry, MachineOption.SECTION, option.id, { option.read(it) },
                                { optionDefault(option) }, GameSource.ALL_GAMES, option::stored),
                            enabled, noHash, actions = option.actions)
                    }
                    is CatalogSetting.Drive -> driveRow(entry, setting, scope, enabled, noHash)
                    is CatalogSetting.Lines -> linesRow(entry, setting, enabled, noHash)
                    is CatalogSetting.Text -> textRow(entry, setting, enabled, noHash)
                    is CatalogSetting.Custom -> customSettingRow(entry, setting, enabled)
                }
            }
        if (rows.isEmpty()) return null
        return RowGroup("MACHINE", rows, "Machine changes apply on the next launch or restart.")
    }

    /**
     * The row of a setting the product shows its own way, or null when [entry] has nothing to change
     * there (the catalog gives the game none of it and the player has not set it).
     */
    protected open fun customSettingRow(entry: E, setting: CatalogSetting.Custom, enabled: () -> Boolean): SettingsRow? =
        error("${identity.appName} has no row for its catalog setting ${setting.id}")

    /**
     * The disk in a drive beside [entry]'s own: Default (the catalog's), Empty, or another disk of
     * the same archive or folder the drive takes.
     */
    private fun driveRow(entry: E, setting: CatalogSetting.Drive, scope: () -> String, enabled: () -> Boolean,
                         noHash: () -> String): SettingsRow {
        val id = entry.key.takeIf { entry.contentId != null }
        val role = setting.role
        fun custom() = id != null && catalog.sourceOf(entry, CatalogSetting.MEDIA) == "User override"
        fun name(contentId: String) = if (contentId == EMPTY_DRIVE) "Empty"
            else libraryEntries.firstOrNull { it.contentId == contentId || it.catalogId == contentId }?.displayName
                ?: "Not in library"
        val value = GameValue({ if (custom()) catalog.resolve(entry).mediaId(role) ?: EMPTY_DRIVE else null },
            { catalog.resolveDefault(entry).mediaId(role) ?: EMPTY_DRIVE },
            { catalog.defaultSourceOf(entry, CatalogSetting.MEDIA).takeIf { it != "App default" }
                ?.let(GameSource::label) ?: GameSource.BUILT_IN },
            { picked -> saveGameSetting(entry) { setDrive(entry, id!!, role, picked) } })
        val disks = {
            listOf(ChoiceOption(EMPTY_DRIVE, "Empty")) + libraryEntries.filter { other ->
                other.playable && other.id != entry.id && sameSource(entry, other) &&
                    catalog.schema.validMedia(role, other.contentId!!)
            }.map { ChoiceOption(it.contentId!!, it.displayName) }
        }
        return GameSettingsRows.choice("drive-$role", setting.label, scope, disks, value, enabled, noHash,
            defaultLabel = { name(value.fallback()) })
    }

    /** [role]'s disk set to [picked] (null for the catalog's), keeping the game's other drives. */
    private fun setDrive(entry: E, game: GameKey, role: String, picked: String?) {
        fun drives(record: org.json.JSONObject) = record.optJSONArray(CatalogSetting.MEDIA)?.let { items ->
            (0 until items.length()).mapNotNull(items::optJSONObject)
                .associate { it.optString("role") to it.optString("contentId") }
        } ?: emptyMap()
        val defaults = drives(catalog.resolveDefault(entry).record)
        val next = drives(catalog.resolve(entry).record).toMutableMap()
        when (picked) {
            null -> defaults[role]?.let { next[role] = it } ?: next.remove(role)
            EMPTY_DRIVE -> next.remove(role)
            else -> next[role] = picked
        }
        if (next == defaults) catalog.resetOverride(game, CatalogSetting.MEDIA)
        else catalog.setOverride(game, CatalogSetting.MEDIA, org.json.JSONArray(next.map { (itemRole, contentId) ->
            org.json.JSONObject().put("role", itemRole).put("contentId", contentId)
        }))
    }

    /** Lines the machine types for [entry], edited as one line of text. */
    private fun linesRow(entry: E, setting: CatalogSetting.Lines, enabled: () -> Boolean,
                         noHash: () -> String): SettingsRow {
        val id = entry.key.takeIf { entry.contentId != null }
        val field = setting.field
        val subfield = setting.subfield
        fun custom() = id != null && catalog.sourceOf(entry, field, subfield) == "User override"
        return GameSettingsRows.text("lines-$field-$subfield", setting.label, setting.hint,
            { catalog.resolve(entry).let { game -> setting.value(game, optionValues(game)).takeIf { it.isNotEmpty() }?.let(setting::text) } }, ::custom,
            { catalog.defaultSourceOf(entry, field, subfield).takeIf { it != "App default" }
                ?.let(GameSource::label) ?: GameSource.BUILT_IN },
            { text ->
                saveGameSetting(entry) {
                    val lines = setting.parse(text)
                    if (lines.isEmpty()) catalog.resetOverrideSubfield(id!!, field, subfield)
                    else catalog.setOverrideSubfield(id!!, field, subfield, org.json.JSONArray(lines))
                }
            },
            { saveGameSetting(entry) { catalog.resetOverrideSubfield(id!!, field, subfield) } },
            enabled = enabled, disabledReason = noHash)
    }

    /**
     * A [CatalogSetting.Text]: saving nothing chooses the machine's own even where the catalog names
     * one; Reset returns to the catalog's.
     */
    private fun textRow(entry: E, setting: CatalogSetting.Text, enabled: () -> Boolean,
                        noHash: () -> String): SettingsRow {
        val id = entry.key.takeIf { entry.contentId != null }
        val field = setting.field
        val subfield = setting.subfield
        fun custom() = id != null && catalog.sourceOf(entry, field, subfield) == "User override"
        return GameSettingsRows.text("text-$field-$subfield", setting.label, setting.hint,
            { setting.value(catalog.resolve(entry)).takeIf { it.isNotEmpty() } }, ::custom,
            { catalog.defaultSourceOf(entry, field, subfield).takeIf { it != "App default" }
                ?.let(GameSource::label) ?: GameSource.BUILT_IN },
            { typed ->
                val value = setting.normalize(typed)
                if (!setting.valid(value)) toast("Not a valid ${setting.label.lowercase()}: $typed")
                else saveGameSetting(entry) { catalog.setOverrideSubfield(id!!, field, subfield, value) }
            },
            { saveGameSetting(entry) { catalog.resetOverrideSubfield(id!!, field, subfield) } },
            empty = setting.empty, enabled = enabled, disabledReason = noHash)
    }

    /** Machine: the all-games defaults of the options, then firmware. */
    override fun machinePage(): Page = GroupsPage("Machine", { GameSource.ALL_GAMES }, {
        listOfNotNull(
            machineOptions.takeIf { it.isNotEmpty() }?.let { options ->
                RowGroup("ALL GAMES", options.map { option ->
                    val select = { value: String ->
                        preferences.edit().apply {
                            if (option.numeric) putInt(option.id, value.toInt()) else putString(option.id, value)
                        }.apply()
                        refreshSettingValues()
                    }
                    SettingsRow.choice("default-${option.id}", option.label, ChoiceSpec(option.label,
                        { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, option.choices)) }, { optionDefault(option) },
                        select = select, actions = { option.actions(select) }))
                }, "A game's own settings come first. Changes apply on the next launch or restart.")
            },
        ) + firmwareGroups()
    })

    // Firmware ---------------------------------------------------------------------------------------

    @Volatile private var firmwareImporting = false

    /** The firmware file picked on its own, waiting for the picker's result. */
    private var pickingPart: Pair<FirmwareSet, FirmwareSet.Part>? = null

    override val firmware: MachineFirstRun.Firmware?
        get() = if (firmwareSets.isEmpty()) null else MachineFirstRun.Firmware(firmwareText.first, firmwareText.second,
            ::firmwareGroups, { firmwareImporting })

    /**
     * Firmware, the same in every app: one row imports everything it recognizes from a folder,
     * then each set's files, each its own row marked Required or Optional with what it is for.
     */
    protected fun firmwareGroups(): List<RowGroup> {
        if (firmwareSets.isEmpty()) return emptyList()
        val reason = { if (firmwareImporting) "Wait for the current import to finish" else RETURN_FOR_FIRMWARE }
        val usable = { firmwareConfigurable() && !firmwareImporting }
        val folder = SettingsRow.action("firmware-folder", "Import all from a folder…",
            description = "Finds the files below in a folder you choose and imports every one it recognizes",
            busy = { firmwareImporting }, enabled = usable, disabledReason = reason) {
            firmwareSets.first().second.chooseFolder(this, FIRMWARE_REQUEST, firmwareFolderStart(), ::toast)
        }
        val sets = firmwareSets.mapIndexed { index, (label, set) ->
            RowGroup(label.uppercase(), set.parts.map { part -> firmwareRow(index, set, part, usable, reason) }, set.note)
        }
        return listOf(RowGroup("FIRMWARE", listOf(folder),
            if (firmwareConfigurable()) firmwareText.third else RETURN_FOR_FIRMWARE)) + sets
    }

    /** One firmware file: what it is for, Required or Optional, its state, and a tray to pick or remove it. */
    private fun firmwareRow(index: Int, set: FirmwareSet, part: FirmwareSet.Part, usable: () -> Boolean,
                            reason: () -> String) =
        SettingsRow.action("firmware$index-${part.name}", part.label, value = { set.state(part) },
            tone = { if (set.installed(part) || part.status?.invoke() != null) ValueTone.CURRENT else ValueTone.SUMMARY },
            tag = { if (set.isRequired(part)) "Required" else "Optional" },
            description = part.description, busy = { firmwareImporting }, enabled = usable, disabledReason = reason,
            tray = {
                val choose = TrayButton(if (set.installed(part)) "Replace…" else "Choose file…", ButtonTone.PRIMARY) {
                    pickingPart = set to part
                    DocumentPicker.open(this, FIRMWARE_FILE_REQUEST, failed = ::toast)
                }
                TraySpec(buttons = if (set.installed(part)) listOf(TrayButton(part.removeLabel) {
                    set.remove(part)
                    firmwareChanged()
                }, choose) else listOf(choose))
            })

    /** The firmware folder picker opens at the folder chosen last, or beside the games folder. */
    private fun firmwareFolderStart(): Uri? =
        preferences.getString(FIRMWARE_FOLDER, null)?.let { FolderStart.at(Uri.parse(it)) } ?: tree?.let(FolderStart::beside)

    /** The machine reads its firmware when a game boots, so it changes only in the library. */
    private fun firmwareConfigurable() = currentEntry == null && !transitioning

    /** Fails a launch whose required firmware in [sets] is missing, naming what to import. */
    protected fun requireFirmware(sets: List<FirmwareSet> = firmwareSets.map { it.second }) {
        val missing = sets.filter { it.missingRequirements.isNotEmpty() }
        if (missing.isNotEmpty()) error("Import the ${identity.guestName} firmware in Menu › Machine first " +
            "(missing ${missing.joinToString(", ") { it.missingText }})")
    }

    override fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            FIRMWARE_REQUEST -> {
                val tree = data?.data?.takeIf { resultCode == RESULT_OK } ?: return true
                preferences.edit().putString(FIRMWARE_FOLDER, tree.toString()).apply()
                importFirmware {
                    val cancelled = AtomicBoolean(false)
                    val imported = firmwareSets.flatMap { it.second.importFolder(contentResolver, tree, cancelled) }
                    if (imported.isEmpty()) "No ${identity.guestName} firmware files in that folder"
                    else "Imported ${imported.size} firmware ${if (imported.size == 1) "file" else "files"}"
                }
            }
            FIRMWARE_FILE_REQUEST -> {
                val (set, part) = pickingPart ?: return true
                pickingPart = null
                val uri = DocumentPicker.selectedUri(resultCode, data) ?: return true
                importFirmware {
                    set.importPart(contentResolver, uri, part)
                    "${part.label} imported"
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * After a firmware import that succeeded, on its worker thread: for a product that keeps its
     * own record of the firmware files, such as KairoST's library of every TOS it has seen.
     */
    protected open fun firmwareImported() {}

    /** Runs a firmware import off the UI thread and reports its result. */
    private fun importFirmware(run: () -> String) {
        firmwareImporting = true
        firmwareChanged()
        Thread {
            val message = try {
                run().also { firmwareImported() }
            } catch (error: Exception) {
                "Firmware import failed: ${error.message ?: error.javaClass.simpleName}"
            }
            runOnUiThread {
                firmwareImporting = false
                firmwareChanged()
                toast(message)
            }
        }.apply { name = "${identity.appName}-firmware"; start() }
    }

    // Media ------------------------------------------------------------------------------------------

    /** What each media slot holds, by library entry; null for an empty slot or media the library lacks. */
    private val mounted: MutableList<E?> by lazy { MutableList(mediaSlots.size) { null } }
    @Volatile private var mediaBusy = false

    /** Called from [startGame] once the machine runs: what each slot booted with. */
    protected fun mediaStarted(entries: List<E?>) = runOnUiThread {
        mounted.fill(null)
        entries.forEachIndexed { slot, entry -> if (slot < mounted.size) mounted[slot] = entry }
    }

    /** Waits on the launch thread until the machine runs, failing the launch with its error. */
    protected fun awaitRunning() {
        while (true) {
            val status = MachineStatus(bridge.status())
            if (status.state == "Error") error(status.error)
            if (status.running) return
            Thread.sleep(20)
        }
    }

    private fun mediaAfterLoad() {
        if (mediaSlots.isEmpty()) return
        val paths = bridge.mediaPaths() ?: return
        for (slot in mounted.indices) mounted[slot] = paths.getOrNull(slot)?.let { library.entryForWorkingImage(it, libraryEntries) }
    }

    private fun mediaPage() = GroupsPage(mediaPageTitle, { currentGame?.title }, {
        listOf(RowGroup("DRIVES", mediaSlots.indices.map(::mediaRow), "A disk keeps what the game writes to it."))
    })

    private fun mediaRow(slot: Int) = SettingsRow.choice("media$slot", mediaSlots[slot].label, mediaChoice(slot),
        value = { mounted[slot]?.displayName ?: "Empty" },
        tone = { if (mounted[slot] == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !mediaBusy },
        disabledReason = { if (mediaBusy) "Wait for the disk change to finish" else "Start a game before changing disks" })

    /**
     * Empty ejects; then the running game's own disks ([ownMedia]); then Other, every disk of the
     * library the slot takes, on a page of its own: a player rarely needs one, and a library can
     * hold thousands.
     */
    private fun mediaChoice(slot: Int): ChoiceSpec {
        fun takes(entry: E) = entry.playable && mediaSlots[slot].accepts(entry)
        return GameDisks.choice(mediaSlots[slot].label, { currentGame?.title }, {
            val current = currentEntry ?: return@choice emptyList()
            GameDisks.own(current, libraryEntries, mounted, catalog.setDisks(current).toSet(), catalog::matchId, ::takes)
        }, { libraryEntries.filter(::takes) }, { mounted[slot]?.id ?: GameDisks.EMPTY }, insert = { changeMedia(slot, it) })
    }

    private fun changeMedia(slot: Int, entry: E?) {
        if (mediaBusy) return
        mediaBusy = true
        refreshSettingValues()
        Thread {
            val failure = try {
                val image = entry?.let { library.prepare(it, AtomicBoolean(false)) { } }
                check(bridge.media(slot, image?.absolutePath)) { "The disk could not be inserted" }
                null
            } catch (error: Exception) { error.message ?: "Disk change failed" }
            runOnUiThread {
                mediaBusy = false
                if (failure == null) mounted[slot] = entry
                toast(failure ?: "${mediaSlots[slot].label}: ${entry?.displayName ?: "Empty"}")
                refreshSettingValues()
            }
        }.apply { name = "${identity.appName}-media"; start() }
    }

    companion object {
        private const val FIRMWARE_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 1
        private const val FIRMWARE_FILE_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 2
        private const val RETURN_FOR_FIRMWARE = "Return to the library to change firmware."
        /** The folder firmware was last imported from. */
        private const val FIRMWARE_FOLDER = "firmware_folder_v1"
        /** The drive choice that leaves a drive empty. */
        private const val EMPTY_DRIVE = GameDisks.EMPTY
        /** How often a loading session checks whether the machine shows a picture yet. */
        private const val PICTURE_POLL_MILLIS = 100L
    }
}

