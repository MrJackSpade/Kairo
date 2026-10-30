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
    /**
     * Whether the machine saves and loads states (Save state and Load state in the game's flyout).
     * A machine without them (KairoDos's DOSBox Staging) keeps its saves in [saveKinds] of its own.
     */
    protected open val saveStates: Boolean = true
    protected abstract val bridge: MachineBridge
    protected abstract fun createCatalog(): LayeredGameCatalog
    protected abstract fun createLibrary(catalog: LayeredGameCatalog): MachineLibrary<E>
    /** Whether this build may download catalog artwork (the product's build flavor decides). */
    protected abstract val artworkDownloadsEnabled: Boolean
    /** What tapping the screen does in Keyboard mode, as the Screen tap choice describes it. */
    protected open val keyboardTapHelp: String get() = getString(R.string.kairo_touch_tap_keyboard_help, identity.guestName)

    /**
     * Runs on a worker thread: prepares [entry]'s media and starts the machine with [bridge],
     * reporting what it does through [progress]. Throws to fail the launch; the message is shown.
     */
    protected abstract fun startGame(entry: E, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit)

    /** Rows in the game flyout's GAME group after Save state and Load state, such as Disks. */
    protected open fun machineRows(): List<SettingsRow> = emptyList()

    /**
     * Why a save or load failed, for a nonzero code the bridge returned: the shared machine host's
     * codes (MachineCore's kFailed, with the emulator's reason, and kUnsupported). A product with a
     * bridge of its own codes maps them here instead.
     */
    protected open fun stateError(code: Int): String = when (code) {
        MachineBridge.FAILED, MachineBridge.LOST -> bridge.stateErrorReason().let { reason ->
            if (reason.isEmpty()) getString(R.string.kairo_state_error_failed)
            else getString(R.string.kairo_state_error_failed_reason, reason)
        }
        MachineBridge.UNSUPPORTED -> getString(R.string.kairo_state_error_unsupported)
        else -> getString(R.string.kairo_state_error_code, code)
    }

    /** A state loaded: the product rereads what the machine now holds, such as its disks. */
    protected open fun stateLoaded() {}

    /** A load the machine refused with [code], as when its media must be started again. */
    protected open fun loadRejected(code: Int) {}

    /** A load that left the machine unable to go on (MachineBridge.LOST) starts the game again. */
    private fun loadFailed(code: Int) {
        if (code == MachineBridge.LOST) restartGame()
        loadRejected(code)
    }

    /** The session's game started; the product may begin its own work, such as typing a command. */
    protected open fun gameStarted(entry: E, game: CatalogGame) {}

    // What most machines share: firmware, media drives, and machine options -----------------------

    /** The machine's choices, each a game setting and an all-games default in Machine. */
    protected open val machineOptions: List<MachineOption> = emptyList()

    /** The machine's firmware, by set label: each file its own row (firmwareGroups); empty for none. */
    protected open val firmwareSets: List<Pair<UiText, FirmwareSet>> = emptyList()
    /** The first-run step's title and text, and the note under the firmware rows; the same in every app. */
    private val firmwareText: Triple<String, String, String>
        get() = Triple(getString(R.string.kairo_firmware_title, identity.guestName), getString(R.string.kairo_firmware_description),
            getString(R.string.kairo_firmware_required_note))

    /** The media slots a game's own disks go in, shown on the flyout's media page. */
    protected open val mediaSlots: List<MediaSlot<E>> = emptyList()
    protected open val mediaPageTitle: UiText = UiText.res(R.string.kairo_media_disks)

    /** A slot of the machine, such as a floppy drive, and the library entries it takes. */
    /** A drive of the Disks page; [folders]: it also takes a folder on the device (Choose folder). */
    class MediaSlot<E>(val label: UiText, val folders: Boolean, val accepts: (E) -> Boolean) {
        constructor(label: UiText, accepts: (E) -> Boolean = { true }) : this(label, false, accepts)
    }

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
    /** The point in [videoSize] terms goes to the core as a fraction of the picture, which the view shows whole. */
    override fun warpMouse(x: Int, y: Int) {
        val (width, height) = videoSize()
        bridge.warpMouse(((x + 0.5) * 65536 / width).toInt(), ((y + 0.5) * 65536 / height).toInt())
    }
    override fun mouseWarpDone(): Boolean = bridge.mouseWarpDone()
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
    override fun withMember(entry: E, member: String) = library.withMember(entry, member)
    override val hashCount: Int get() = library.hashCount
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean) = library.inspectExternal(file, cancelled)
    override fun withError(entry: E, message: String) = library.withError(entry, message)
    override fun v1DataStores(): List<GamePathMigration.Store<E>> = listOf(library.hashNamedWorkingCopies(), catalog.v1Overrides())

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

    // Read each time: a catalog update can bring a new system mapping while the app runs.
    override fun systemControllerBindings() = bindingsCodec.parse(catalog.systemControllerBindings().toString())

    override fun catalogControllerLayer(entry: E): List<ControllerBinding> {
        if (entry.contentId == null) return emptyList()
        val defaults = catalog.resolveDefault(entry).controllerDefaultBindings?.let { org.json.JSONObject(it) }
            ?: return emptyList()
        val bindings = defaults.optJSONArray(controllerProfiles.configuration.layout.key)
            ?: defaults.optJSONArray("withoutSticks") ?: return emptyList()
        return bindingsCodec.parse(bindings.toString())
    }
    override fun playerControllerLayer(entry: E): List<ControllerBinding> {
        val own = playerController(entry) ?: return emptyList()
        own.optJSONArray("layer")?.let { return bindingsCodec.parse(it.toString()) }
        // Saved before mappings were layers: the whole mapping as it played.
        return own.optJSONArray("bindings")?.let { ControllerDefaults.legacyLayer(bindingsCodec.parse(it.toString())) }
            ?: emptyList()
    }
    private fun playerController(entry: E) = entry.takeIf { it.contentId != null }?.let { catalog.overrideOf(it.key, "controller") }
    override fun savePlayerControllerLayer(game: GameKey, layer: List<ControllerBinding>) =
        catalog.updateOverrideSubfields(game, "controller", mapOf("layer" to bindingsCodec.toJson(layer), "bindings" to null))
    override fun clearGameBindings(game: GameKey) =
        catalog.updateOverrideSubfields(game, "controller", mapOf("bindings" to null, "profile" to null, "layer" to null))
    override fun controllerRefreshed() { currentEntry?.let { currentGame = catalog.resolve(it) } }
    override fun touchChanged() { currentEntry?.let { currentGame = catalog.resolve(it) } }
    override fun controllerSource(entry: E): String {
        val own = playerController(entry)
        val subfield = when {
            own?.has("layer") == true -> "layer"
            own?.has("bindings") == true -> "bindings"
            else -> null
        }
        if (subfield == null && catalogControllerLayer(entry).isEmpty()) return GameSource.ALL_GAMES
        return GameSource.label(entry.key.takeIf { entry.contentId != null }
            ?.let { catalog.sourceOf(entry, "controller", subfield ?: "bindings") } ?: GameSource.ALL_GAMES)
    }
    override fun controllerMappingStatus(entry: E?): String? {
        if (controllerProfiles.configuration.layout != ControllerLayout.WITH_STICKS) return null
        val game = entry?.takeIf { it.contentId != null }?.let(catalog::resolve) ?: return null
        if (game.overriddenFields.contains("controller") || game.controllerProfile == "custom-v1") return null
        return if (game.controllerDefaultBindings != null &&
            org.json.JSONObject(game.controllerDefaultBindings).optJSONArray("withSticks") == null)
            getString(R.string.kairo_mapping_without_sticks_fallback) else null
    }

    override val touchModes get() = listOf(
        InputModeDecider.Mode.AUTO to ChoiceOption("auto", UiText.res(R.string.kairo_auto), UiText.res(R.string.kairo_touch_auto_help)),
        InputModeDecider.Mode.KEYBOARD to ChoiceOption("keyboard", UiText.res(R.string.kairo_keyboard), UiText.fixed(keyboardTapHelp)),
        InputModeDecider.Mode.MOUSE to ChoiceOption("mouse", UiText.res(R.string.kairo_mouse)))
    override val directTouchExplanation: UiText = UiText.res(R.string.kairo_touch_direct_explanation)
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

    /**
     * A blank disk the core makes ([MachineBridge.createBlankDisk] with [kind]): written to a file
     * in the app's cache, then copied into the games folder, and the file removed.
     */
    protected fun coreBlankDisk(kind: String, label: UiText, extension: String) = BlankDisk.Image(kind, label, extension) { output ->
        val file = java.io.File.createTempFile("blank-", ".$extension", cacheDir)
        try {
            bridge.createBlankDisk(kind, file.absolutePath)
            file.inputStream().use { it.copyTo(output) }
        } finally {
            file.delete()
        }
    }

    /**
     * The game's disks as the player puts them together (Kairo#134): a switch for each disk of
     * [entry]'s folder or archive, as the Drive rows offer, and any already in its set. The game
     * starts from [entry]; the disks switched on are its `setDisks`, so they leave the library and
     * are its own disks in drives and disk swaps.
     */
    override fun gameDisksRow(entry: E, enabled: () -> Boolean, disabledReason: () -> String): SettingsRow? {
        val own = entry.contentId?.let { catalog.matchId(entry) }
        fun set() = catalog.setDisks(entry).toSet()
        fun candidates() = libraryEntries.filter { other ->
            val id = other.takeIf { it.playable && it.id != entry.id }?.let(catalog::matchId)
            id != null && id != own && (sameSource(entry, other) || id in set())
        }.distinctBy(catalog::matchId)
        if (own != null && candidates().isEmpty()) return null
        val title = getString(R.string.kairo_game_disks)
        fun count() = set().size + 1
        return SettingsRow.nav("disks", title, { resources.getQuantityString(R.plurals.kairo_game_disks_count, count(), count()) },
            enabled = enabled, disabledReason = disabledReason) {
            pages.push(GroupsPage(title, { titleOf(entry) }, {
                listOf(RowGroup(null, candidates().map { other ->
                    val id = catalog.matchId(other)!!
                    SettingsRow.switch("disk-$id", other.displayName, { id in set() }) { on ->
                        saveGameSetting(entry, getString(R.string.kairo_saved)) {
                            val next = if (on) set() + id else set() - id
                            if (next.isEmpty()) catalog.resetOverride(entry.key, "setDisks")
                            else catalog.setOverride(entry.key, "setDisks", org.json.JSONArray(next.sorted()))
                        }
                    }
                }))
            }))
        }
    }
    override fun gameAnchor(game: E, orientation: String, allGames: () -> Anchor) =
        machineValue(game, "display", "${orientation}Anchor",
            { it.section("display")?.optString("${orientation}Anchor")?.takeIf(String::isNotEmpty) },
            { allGames().id }, GameSource.ALL_GAMES, saved = getString(R.string.kairo_saved))
    override fun gameSettingChanged(entry: E) {
        if (currentEntry?.id == entry.id) currentGame = catalog.resolve(entry)
    }
    override fun deletePrompt(entry: E): GameDeletionFlow.Prompt? = GameDeletionFlow.Prompt(entry.path)
    override fun deleteSource(entry: E) = library.deleteSource(entry, libraryEntries)

    /**
     * The game plays from a working copy of its disk, which holds the saves the game writes to
     * it. Resetting replaces that copy with the current disk image, as after editing the image.
     */
    override fun fileRows(entry: E): List<SettingsRow> {
        if (!library.hasWorkingImage(entry)) return emptyList()
        return listOf(SettingsRow.action("reset-disk", getString(R.string.kairo_disk_reset), danger = true,
            enabled = { libraryVisible || currentEntry?.id != entry.id },
            disabledReason = { getString(R.string.kairo_disk_reset_exit_first) },
            tray = {
                Confirm(getString(R.string.kairo_disk_reset_confirm, entry.displayName), getString(R.string.kairo_reset), danger = true) {
                    try {
                        library.resetWorkingImage(entry)
                        toast(getString(R.string.kairo_disk_reset_done))
                    } catch (error: Exception) {
                        toast(error.message ?: getString(R.string.kairo_disk_reset_failed))
                    }
                    pages.refresh()
                }.tray(this)
            }))
    }

    /**
     * A machine setting for [entry]: the user's override of [section].[subfield] over the catalog,
     * which falls back to [fallback] from [appDefault]. Values are stored as [toStored] makes them.
     */
    protected fun machineValue(entry: E, section: String, subfield: String, read: (CatalogGame) -> String?,
                               fallback: () -> String, appDefault: String,
                               saved: String = getString(R.string.kairo_saved_machine),
                               toStored: (String) -> Any = { it }): GameValue<String> {
        val id = entry.key.takeIf { entry.contentId != null }
        fun custom() = id != null && catalog.sourceOf(entry, section, subfield) == "User override"
        return GameValue({ if (custom()) read(catalog.resolve(entry)) else null },
            { id?.let { read(catalog.resolveDefault(entry)) } ?: fallback() },
            { id?.let { catalog.defaultSourceOf(entry, section, subfield) }?.takeIf { it != "App default" }
                ?.let(GameSource::label) ?: appDefault },
            { value -> saveGameSetting(entry, saved) {
                if (value == null) catalog.resetOverrideSubfield(id!!, section, subfield)
                else catalog.setOverrideSubfield(id!!, section, subfield, toStored(value))
            } })
    }

    override var firstRunStep: Int
        get() = preferences.getInt("onboarding_step_v1", if (tree == null) 0 else 2)
        set(value) { preferences.edit().putInt("onboarding_step_v1", value).apply() }
    override val saveKinds: Map<String, String> = StateSlotStore.KINDS
    override val appSaves: File get() = File(filesDir, "states")
    override fun saveUnitLabel(kind: String) = getString(R.string.kairo_save_slot, kind.removePrefix("state"))

    override fun sessionStatus() = MachineStatus(bridge.status()).state
    override fun diagnosticsRows(running: Boolean): List<SettingsRow> {
        if (!running) return listOf(SettingsRow.info("state", getString(R.string.kairo_diagnostics_state)) {
            getString(R.string.kairo_diagnostics_stopped)
        })
        val status = MachineStatus(bridge.status())
        return listOfNotNull(
            SettingsRow.info("state", getString(R.string.kairo_diagnostics_state)) { MachineStatus(bridge.status()).state },
            status.error.takeIf { it.isNotEmpty() }?.let { SettingsRow.info("error", getString(R.string.kairo_diagnostics_error)) { it } },
            SettingsRow.info("frames", getString(R.string.kairo_diagnostics_frames)) { MachineStatus(bridge.status()).frames },
            SettingsRow.info("audio", getString(R.string.kairo_diagnostics_audio)) { MachineStatus(bridge.status()).audio },
            SettingsRow.info("xruns", getString(R.string.kairo_diagnostics_xruns)) { MachineStatus(bridge.status()).xruns },
            SettingsRow.info("audio-config", getString(R.string.kairo_diagnostics_audio_config)) {
                MachineStatus(bridge.status()).audioConfig
            })
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
            }, ::loadFailed, getString(R.string.kairo_states_library_only))
    }

    override fun gameRows() = (if (saveStates) stateFlow.rows() else emptyList()) + machineRows() + listOfNotNull(mediaSlots.takeIf { it.isNotEmpty() }?.let {
        SettingsRow.nav("f-media", text(mediaPageTitle)) { pages.push(mediaPage()) }
    })

    /** A loaded state restores the lock keys it was saved with. */
    private fun loadMachineState(directory: File): Int =
        bridge.loadState(directory.absolutePath, library.statePaths(launchEntries)).also { result ->
        if (result == 0) {
            // A machine reports every lock it has; the keyboard in use shows those it has keys for
            // (an international MSX has no Kana key, though its machine has the light).
            inputRouter.syncLocks(bridge.lockedKeys() intersect inputRouter.lockCodes())
            runOnUiThread {
                mediaAfterLoad()
                stateLoaded()
                refreshSettingValues()
            }
        }
    }

    private fun describeStateError(code: Int) =
        if (code == MachineBridge.NOT_RUNNING) getString(R.string.kairo_state_error_not_running) else stateError(code)

    /**
     * Asks the game's startup choices, if any (one page each; Play manually starts without the rest),
     * then starts it. Malformed startup automation in its catalog data fails the launch.
     */
    override fun launch(entry: E, fromFrontend: Boolean) {
        val game = catalog.resolve(entry)
        val choices = try {
            game.diskSwaps
            game.startupChoices
        } catch (failure: RuntimeException) {
            toast(getString(R.string.kairo_launch_data_unreadable, game.title, failure.message))
            return
        }
        debugStartupOption?.let { requested ->
            val picked = choices.mapNotNull { choice -> choice.options.firstOrNull { it.id == requested }?.let { choice to it } }
            if (picked.size == choices.size) startWithChoices(entry, game, picked, fromFrontend)
            else startupOptionMissing(game, requested)
            return
        }
        if (choices.isNotEmpty() && !skipStartupChoices) chooseStartupOptions(entry, game, choices, 0, emptyList(), fromFrontend)
        else startWithChoices(entry, game, emptyList(), fromFrontend)
    }

    private fun startWithChoices(entry: E, game: CatalogGame, choices: List<Pair<StartupChoice, StartupOption>>,
                                 fromFrontend: Boolean) {
        selectedChoices = choices
        startSession(entry, game, fromFrontend)
    }

    /** One page per startup choice; Play manually starts without the rest, and Back cancels. */
    private fun chooseStartupOptions(entry: E, game: CatalogGame, choices: List<StartupChoice>, index: Int,
                                     selected: List<Pair<StartupChoice, StartupOption>>, fromFrontend: Boolean,
                                     replace: Boolean = false) {
        if (index == choices.size) {
            startWithChoices(entry, game, selected, fromFrontend)
            return
        }
        val choice = choices[index]
        lateinit var page: ChoicePage
        page = ChoicePage(ChoiceSpec(choice.title, { game.title }, {
            listOf(ChoiceGroup(null, choice.options.map { ChoiceOption(it.id, it.label) },
                getString(R.string.kairo_startup_choice_foot, identity.appName)))
        }, { null }, { id ->
            val option = choice.options.first { it.id == id }
            pages.postDelayed({
                // Back during the pause cancels, as it does on the page.
                if (pages.top === page) chooseStartupOptions(entry, game, choices, index + 1,
                    selected + (choice to option), fromFrontend, replace = true)
            }, 150)
        }, actions = {
            listOf(SettingsRow.action("manual", getString(R.string.kairo_play_manually)) {
                startWithChoices(entry, game, selected, fromFrontend)
            })
        }, stay = true, cycles = false))
        if (replace) pages.replace(page) else pages.push(page)
    }

    /**
     * Whether the machine has a command line a game can open at (Kairo#89), such as BASIC's prompt:
     * the library then offers it, and [startAtPrompt] starts it.
     */
    protected open val commandLine: Boolean = false

    /**
     * Starts the machine at its command line with [entry]'s media in its drives: nothing started from
     * them and no startup commands typed. For a product with a [commandLine].
     */
    protected open fun startAtPrompt(entry: E, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit): Unit =
        error("${identity.appName} has no command line")

    override val commandLaunch: ((E) -> Unit)?
        get() = if (commandLine) { entry -> startSession(entry, catalog.resolve(entry), false, atPrompt = true) } else null

    /**
     * Puts each of [entries], by media slot, in its drive of the running machine, as the Disks page
     * does: for [startAtPrompt], which boots with the drives empty so nothing starts from them.
     */
    protected fun insertMedia(entries: List<E?>, cancelled: AtomicBoolean, progress: (String) -> Unit) {
        entries.forEachIndexed { slot, entry ->
            entry?.let {
                val image = library.prepareMedia(it, cancelled, progress)
                check(bridge.media(slot, image.absolutePath)) { getString(R.string.kairo_media_insert_failed) }
            }
        }
        mediaStarted(entries)
    }

    /** Starts [entry] on the machine, as [launch] does once the product has what it needs; at its prompt with [atPrompt]. */
    protected fun startSession(entry: E, game: CatalogGame, fromFrontend: Boolean, atPrompt: Boolean = false) {
        val generation = beginLaunch(getString(R.string.kairo_launch_preparing, entry.displayName))
        val cancelled = AtomicBoolean(false)
        Thread {
            val failure = try {
                bridge.stop()
                if (current(generation)) {
                    inputRouter.resetLocks()
                    // A game opened at its prompt is the player's: no automation.
                    swaps = if (atPrompt) emptyMap() else swapSources(entry, game)
                    val progress = { message: String -> runOnUiThread { if (current(generation)) showProgress(message) } }
                    if (atPrompt) startAtPrompt(entry, game, cancelled, progress) else startGame(entry, game, cancelled, progress)
                }
                null
            } catch (error: Exception) {
                if (error is MissingDisk) text(error.text) else error.message ?: error.javaClass.simpleName
            }
            runOnUiThread {
                if (!current(generation)) return@runOnUiThread
                if (failure == null) {
                    currentGame = game
                    // A machine that boots to a black screen (Kairo68#2) keeps the launch's message up
                    // until it shows something.
                    val blank = MachineStatus(bridge.status()).pictured == false
                    enterSession(entry, fromFrontend, if (blank) getString(R.string.kairo_launch_starting, entry.displayName) else null)
                    if (blank) awaitPicture(generation)
                    watchEnd(generation, game.title)
                    if (atPrompt) selectedChoices = emptyList()
                    startAutomation(game, atPrompt, swaps)
                    gameStarted(entry, game)
                    keptMedia?.let { kept ->
                        keptMedia = null
                        restoreMedia(kept)
                    }
                } else {
                    keptMedia = null
                    launchFailed(failure)
                }
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
                libraryScreen.showStatus(if (end.state == "Error" && end.error.isNotEmpty())
                    getString(R.string.kairo_session_stopped_error, title, end.error)
                    else getString(R.string.kairo_session_stopped, title))
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

    /**
     * Restart boots the game again with any machine changes since it started, and with the disks
     * its drives hold now, as a reset leaves a real machine's disks in (restoreMedia).
     */
    override fun restartGame() {
        val entry = currentEntry ?: return
        keptMedia = mounted.toList()
        launch(entry, sessionFromFrontend)
    }

    override fun sessionEnded() {
        currentGame = null
        mounted.fill(null)
        selectedChoices = emptyList()
    }

    /**
     * Frames, error, each media slot by its label ("media.Drive 1"), each machine option as the
     * game's settings give it ("option.basic"), what the native machine started with
     * ("machine.basic"), and what the core reports of itself ("core.tape").
     */
    override fun probeDetails(): Map<String, String> {
        val status = MachineStatus(bridge.status())
        val details = linkedMapOf("frames" to status.frames, "error" to status.error)
        mediaSlots.forEachIndexed { slot, media -> details["media.${text(media.label)}"] = mounted[slot]?.displayName ?: "" }
        currentGame?.let { game -> optionValues(game).forEach { (id, value) -> details["option.$id"] = value } }
        (bridge as? NativeMachine)?.startedSettings?.forEach { (name, value) -> details["machine.$name"] = value }
        status.core.forEach { (name, value) -> details["core.$name"] = value }
        return details
    }

    // Machine options ---------------------------------------------------------------------------------

    /**
     * Whether the player has imported the firmware [option]'s [MachineOption.ownFirmware] choice
     * starts on, for a game whose other options are [values]. An app with such an option says.
     */
    protected open fun ownFirmwareInstalled(option: MachineOption, values: Map<String, String>): Boolean =
        error("${option.id} names its own firmware, but ${this::class.simpleName} cannot tell whether it is imported")

    /**
     * The all-games default of [option]: the player's all-games choice; else the player's own
     * firmware where they imported it (for a game whose other options are [values]); else the
     * option's default.
     */
    protected fun optionDefault(option: MachineOption, values: Map<String, String> = emptyMap()): String {
        val stored = if (option.numeric) preferences.getInt(option.id, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?.toString() else preferences.getString(option.id, null)
        return stored?.takeIf { value -> option.choices.any { it.id == value } }
            ?: option.ownFirmware?.takeIf { ownFirmwareInstalled(option, values) }
            ?: option.default
    }

    /** What [game] boots with for [option]: its own choice, else the all-games default. */
    protected fun optionValue(game: CatalogGame, option: MachineOption): String = optionValues(game).getValue(option.id)

    /**
     * Every option's value for [game], by option ID: its own choice, else the all-games default. The
     * options without firmware of their own come first, as whether the player's firmware is
     * imported can depend on them (the TOS for the game's model).
     */
    protected fun optionValues(game: CatalogGame): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (option in machineOptions.sortedBy { it.ownFirmware != null })
            values[option.id] = option.read(game) ?: optionDefault(option, values)
        return machineOptions.associate { it.id to values.getValue(it.id) }
    }

    /** Direct touch is a game setting where the catalog can choose it. */
    override val directTouchAvailable: Boolean get() = catalog.schema.directTouch

    /** A row for each setting the catalog can give a game ([CatalogSetting]) that matters to [entry]. */
    override fun machineGroup(entry: E, enabled: () -> Boolean): RowGroup? {
        val scope = { titleOf(entry) }
        val noHash = { GameSettingsRows.noHash(this) }
        val rows = catalog.schema.settings.filter { setting -> entry.contentId?.let(setting.appliesTo) ?: true }
            .mapNotNull { setting ->
                when (setting) {
                    is CatalogSetting.Choice -> {
                        // The app's own option, which may offer more than the catalog's (such as importing a file).
                        val option = machineOptions.firstOrNull { it.id == setting.option.id } ?: setting.option
                        GameSettingsRows.choice(this, option.id, text(option.label), scope, { option.choices },
                            machineValue(entry, MachineOption.SECTION, option.id, { option.read(it) },
                                { optionDefault(option, optionValues(catalog.resolve(entry))) }, GameSource.ALL_GAMES, toStored = option::stored),
                            enabled, noHash, actions = option.actions)
                    }
                    is CatalogSetting.Drive -> driveRow(entry, setting, scope, enabled, noHash)
                    is CatalogSetting.Lines -> linesRow(entry, setting, enabled, noHash)
                    is CatalogSetting.Text -> textRow(entry, setting, enabled, noHash)
                    is CatalogSetting.Custom -> customSettingRow(entry, setting, enabled)
                    CatalogSetting.StartupChoices -> automationRow(entry, setting.field, R.string.kairo_startup_choices,
                        R.string.kairo_startup_choices_on, enabled, noHash)
                    is CatalogSetting.DiskSwaps -> automationRow(entry, setting.field, R.string.kairo_disk_swaps,
                        R.string.kairo_disk_swaps_on, enabled, noHash)
                }
            }
        if (rows.isEmpty()) return null
        return RowGroup(getString(R.string.kairo_machine_group), rows, getString(R.string.kairo_machine_group_foot))
    }

    /**
     * On or Off for the catalog's startup automation in [field], for a game the catalog gives some;
     * Off keeps an empty list as the game's own.
     */
    private fun automationRow(entry: E, field: String, label: Int, on: Int, enabled: () -> Boolean,
                              noHash: () -> String): SettingsRow? {
        val id = entry.key.takeIf { entry.contentId != null }
        fun custom() = id != null && catalog.sourceOf(entry, field) == "User override"
        if ((catalog.resolveDefault(entry).record.optJSONArray(field)?.length() ?: 0) == 0 && !custom()) return null
        val value = GameValue({ if (custom()) AUTOMATION_OFF else null }, { AUTOMATION_ON },
            { GameSource.label(catalog.defaultSourceOf(entry, field)) },
            { picked -> saveGameSetting(entry) {
                if (picked == AUTOMATION_OFF) catalog.setOverride(id!!, field, org.json.JSONArray())
                else catalog.resetOverride(id!!, field)
            } })
        return GameSettingsRows.choice(this, field, getString(label), { titleOf(entry) },
            listOf(ChoiceOption(AUTOMATION_ON, UiText.res(on)), ChoiceOption(AUTOMATION_OFF, UiText.res(R.string.kairo_off))),
            value, enabled, noHash)
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
        fun name(contentId: String) = if (contentId == EMPTY_DRIVE) getString(R.string.kairo_empty)
            else libraryEntries.firstOrNull { it.contentId == contentId || it.catalogId == contentId }?.displayName
                ?: getString(R.string.kairo_drive_not_in_library)
        val value = GameValue({ if (custom()) catalog.resolve(entry).mediaId(role) ?: EMPTY_DRIVE else null },
            { catalog.resolveDefault(entry).mediaId(role) ?: EMPTY_DRIVE },
            { catalog.defaultSourceOf(entry, CatalogSetting.MEDIA).takeIf { it != "App default" }
                ?.let(GameSource::label) ?: GameSource.BUILT_IN },
            { picked -> saveGameSetting(entry) { setDrive(entry, id!!, role, picked) } })
        val disks = {
            listOf(ChoiceOption(EMPTY_DRIVE, UiText.res(R.string.kairo_empty))) + libraryEntries.filter { other ->
                other.playable && other.id != entry.id && sameSource(entry, other) &&
                    catalog.schema.validMedia(role, other.contentId!!)
            }.map { ChoiceOption(it.contentId!!, it.displayName) }
        }
        return GameSettingsRows.choice(this, "drive-$role", text(setting.label), scope, disks, value, enabled, noHash,
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
        return GameSettingsRows.text(this, "lines-$field-$subfield", text(setting.label), text(setting.hint),
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
        return GameSettingsRows.text(this, "text-$field-$subfield", text(setting.label), text(setting.hint),
            { setting.value(catalog.resolve(entry)).takeIf { it.isNotEmpty() } }, ::custom,
            { catalog.defaultSourceOf(entry, field, subfield).takeIf { it != "App default" }
                ?.let(GameSource::label) ?: GameSource.BUILT_IN },
            { typed ->
                val value = setting.normalize(typed)
                if (!setting.valid(value)) toast(getString(R.string.kairo_setting_invalid, text(setting.label).lowercase(), typed))
                else saveGameSetting(entry) { catalog.setOverrideSubfield(id!!, field, subfield, value) }
            },
            { saveGameSetting(entry) { catalog.resetOverrideSubfield(id!!, field, subfield) } },
            empty = text(setting.empty), enabled = enabled, disabledReason = noHash)
    }

    /** Machine: the all-games defaults of the options, then firmware. */
    override fun machinePage(): Page = GroupsPage(getString(R.string.kairo_machine), { GameSource.ALL_GAMES }, {
        listOfNotNull(
            machineOptions.takeIf { it.isNotEmpty() }?.let { options ->
                RowGroup(getString(R.string.kairo_machine_all_games_group), options.map { option ->
                    val select = { value: String ->
                        preferences.edit().apply {
                            if (option.numeric) putInt(option.id, value.toInt()) else putString(option.id, value)
                        }.apply()
                        refreshSettingValues()
                    }
                    SettingsRow.choice("default-${option.id}", text(option.label), ChoiceSpec(text(option.label),
                        { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, option.choices)) }, { optionDefault(option) },
                        select = select, actions = { option.actions(select) }))
                }, getString(R.string.kairo_machine_all_games_foot))
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
        val reason = { getString(if (firmwareImporting) R.string.kairo_firmware_wait else R.string.kairo_firmware_return_to_library) }
        val usable = { firmwareConfigurable() && !firmwareImporting }
        val folder = SettingsRow.action("firmware-folder", getString(R.string.kairo_firmware_import_folder),
            description = getString(R.string.kairo_firmware_import_folder_description),
            busy = { firmwareImporting }, enabled = usable, disabledReason = reason) {
            firmwareSets.first().second.chooseFolder(this, FIRMWARE_REQUEST, firmwareFolderStart(), ::toast)
        }
        val sets = firmwareSets.mapIndexed { index, (label, set) ->
            RowGroup(text(label).uppercase(), set.parts.map { part -> firmwareRow(index, set, part, usable, reason) },
                set.note?.let(::text))
        }
        return listOf(RowGroup(getString(R.string.kairo_firmware_group), listOf(folder),
            if (firmwareConfigurable()) firmwareText.third else getString(R.string.kairo_firmware_return_to_library))) + sets
    }

    /** One firmware file: what it is for, Required or Optional, its state, and a tray to pick or remove it. */
    private fun firmwareRow(index: Int, set: FirmwareSet, part: FirmwareSet.Part, usable: () -> Boolean,
                            reason: () -> String) =
        SettingsRow.action("firmware$index-${part.name}", text(part.label), value = { text(set.state(part)) },
            tone = { if (set.installed(part) || part.status?.invoke() != null) ValueTone.CURRENT else ValueTone.SUMMARY },
            tag = { getString(if (set.isRequired(part)) R.string.kairo_firmware_required else R.string.kairo_firmware_optional) },
            description = text(part.description), busy = { firmwareImporting }, enabled = usable, disabledReason = reason,
            tray = {
                val choose = TrayButton(getString(if (set.installed(part)) R.string.kairo_firmware_replace else R.string.kairo_choose_file),
                    ButtonTone.PRIMARY) {
                    pickingPart = set to part
                    DocumentPicker.open(this, FIRMWARE_FILE_REQUEST, failed = ::toast)
                }
                TraySpec(buttons = if (set.installed(part)) listOf(TrayButton(text(part.removeLabel)) {
                    set.remove(this, part)
                    firmwareChanged()
                }, choose) else listOf(choose))
            })

    /** The firmware folder picker opens at the folder chosen last, or beside the games folder. */
    /** Where Choose folder opens: the folder picked last, else beside the games folder. */
    private fun mediaFolderStart(): Uri? =
        preferences.getString(MEDIA_FOLDER, null)?.let { FolderStart.at(Uri.parse(it)) } ?: tree?.let(FolderStart::beside)

    private fun firmwareFolderStart(): Uri? =
        preferences.getString(FIRMWARE_FOLDER, null)?.let { FolderStart.at(Uri.parse(it)) } ?: tree?.let(FolderStart::beside)

    /** The machine reads its firmware when a game boots, so it changes only in the library. */
    private fun firmwareConfigurable() = currentEntry == null && !transitioning

    /** Fails a launch whose required firmware in [sets] is missing, naming what to import. */
    protected fun requireFirmware(sets: List<FirmwareSet> = firmwareSets.map { it.second }) {
        val missing = sets.filter { it.missingRequirements.isNotEmpty() }
        if (missing.isNotEmpty()) error(getString(R.string.kairo_firmware_missing, identity.guestName,
            missing.joinToString(getString(R.string.kairo_list_separator)) { it.missingText(this) }))
    }

    override fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            FIRMWARE_REQUEST -> {
                val tree = data?.data?.takeIf { resultCode == RESULT_OK } ?: return true
                preferences.edit().putString(FIRMWARE_FOLDER, tree.toString()).apply()
                importFirmware {
                    val cancelled = AtomicBoolean(false)
                    val imported = firmwareSets.flatMap { it.second.importFolder(this, tree, cancelled) }
                    if (imported.isEmpty()) getString(R.string.kairo_firmware_none_in_folder, identity.guestName)
                    else resources.getQuantityString(R.plurals.kairo_firmware_imported, imported.size, imported.size)
                }
            }
            MEDIA_FILE_REQUEST -> {
                val slot = pickingMediaSlot ?: return true
                pickingMediaSlot = null
                DocumentPicker.selectedUri(resultCode, data)?.let { insertPickedFile(slot, it) }
            }
            MEDIA_FOLDER_REQUEST -> {
                val slot = pickingMediaSlot ?: return true
                pickingMediaSlot = null
                DocumentPicker.selectedUri(resultCode, data)?.let { folder ->
                    preferences.edit().putString(MEDIA_FOLDER, folder.toString()).apply()
                    insertPickedFolder(slot, folder)
                }
            }
            FIRMWARE_FILE_REQUEST -> {
                val (set, part) = pickingPart ?: return true
                pickingPart = null
                val uri = DocumentPicker.selectedUri(resultCode, data) ?: return true
                importFirmware {
                    set.importPart(this, uri, part)
                    getString(R.string.kairo_firmware_part_imported, text(part.label))
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
                getString(R.string.kairo_firmware_import_failed, error.message ?: error.javaClass.simpleName)
            }
            runOnUiThread {
                firmwareImporting = false
                firmwareChanged()
                toast(message)
            }
        }.apply { name = "${identity.appName}-firmware"; start() }
    }

    // Startup automation: choices typed on recognized screens, and disk swaps (StartupAutomation) -----

    /**
     * A debug build started with the extra `<preferences>.traceScreenHashes` (kairo98.traceScreenHashes)
     * writes each new screen signature the game shows, with the time and the machine's status, to
     * files/startup-hash-trace.txt every 100 ms: how a catalog's startup choices and disk swaps
     * record their screens.
     */
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("${identity.preferences}.traceScreenHashes", false)) traceScreenHashes()
    }

    private fun traceScreenHashes() = Thread {
        val trace = File(filesDir, "startup-hash-trace.txt").apply { writeText("") }
        var last: Long? = null
        while (!isDestroyed) {
            val signature = if (libraryVisible) null else bridge.screenSignature()
            if (signature != null && signature != last) {
                last = signature
                trace.appendText("${android.os.SystemClock.elapsedRealtime()} " +
                    "${java.lang.Long.toUnsignedString(signature, 16).padStart(16, '0')} ${bridge.status()}\n")
            }
            Thread.sleep(100)
        }
    }.apply { name = "${identity.appName}-screen-trace"; isDaemon = true; start() }

    /** A debug launch's startup option, picked for every choice instead of asking. */
    protected open val debugStartupOption: String? get() = null
    /** Whether a debug launch starts without asking the startup choices. */
    protected open val skipStartupChoices: Boolean get() = false
    /** A debug launch asked for [requested], which some startup choice lacks; the game does not start. */
    protected open fun startupOptionMissing(game: CatalogGame, requested: String) {
        toast(getString(R.string.kairo_launch_data_unreadable, game.title, requested))
    }

    /** A step the frontend has the machine type, and what a notice calls it when the typer stalls on it. */
    class LabeledStep(val label: String, val step: StartupStep)

    /** Steps typed as the game starts, before its startup choices, such as Kairo98's launch commands. */
    protected open fun launchSteps(game: CatalogGame): List<LabeledStep> = emptyList()

    /** The typer typed every step the frontend gave it for the session started at [generation]. */
    protected open fun startupTyped(generation: Int) {}

    private var selectedChoices = emptyList<Pair<StartupChoice, StartupOption>>()
    /** The disks the session's swaps put in, by content ID, found as it launched. */
    @Volatile private var swaps: Map<String, E> = emptyMap()
    @Volatile private var automationCancelled = AtomicBoolean(false)
    /** Whether the typer still has steps the frontend gave it, which cancelling takes back. */
    @Volatile private var typingSteps = false

    /** The disk each of [game]'s swaps needs: the library must have it. */
    private fun swapSources(entry: E, game: CatalogGame): Map<String, E> = game.diskSwaps.associate { rule ->
        val slot = mediaSlots.getOrNull(rule.drive) ?: error("${identity.appName} has no drive ${rule.drive} for a disk swap")
        rule.contentId to (mediaFor(rule.contentId, entry, launchEntries) { slot.accepts(it) }
            ?: throw MissingDisk(missingDisk(slot.label)))
    }

    /** The player took over: the typer stops, and so does the session's watching. */
    override fun cancelAutomation() {
        automationCancelled.set(true)
        if (typingSteps) {
            typingSteps = false
            bridge.startupSteps(emptyList(), replace = true)
        }
    }

    /** At the prompt ([atPrompt]) the game is the player's: nothing typed, no disk swapped. */
    private fun startAutomation(game: CatalogGame, atPrompt: Boolean, sources: Map<String, E>) {
        automationCancelled.set(true)
        val cancelled = AtomicBoolean(false)
        automationCancelled = cancelled
        val generation = sessionGeneration
        val steps = if (atPrompt) emptyList() else launchSteps(game) + selectedChoices.flatMap { (choice, option) ->
            option.inputs.mapIndexed { index, input ->
                LabeledStep(getString(R.string.kairo_startup_step_choice, choice.title, option.label, index + 1, option.inputs.size),
                    StartupStep(input.key.toString(), input.enter, input.screenHashes, CHOICE_TIMEOUT_MS))
            }
        }
        if (steps.isEmpty()) startupTyped(generation)
        else {
            typingSteps = true
            bridge.startupSteps(steps.map { it.step }, replace = false)
            watchStartup(steps, cancelled, generation)
        }
        scheduleDiskSwaps(if (atPrompt) emptyList() else game.diskSwaps, sources, generation)
    }

    /** Follows the typer until it is done or stalls on a step, when a notice offers Retry. */
    private fun watchStartup(steps: List<LabeledStep>, cancelled: AtomicBoolean, generation: Int) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (cancelled.get() || !current(generation)) return
                when (val status = bridge.startupStatus()) {
                    StartupStatus.Typing -> handler.postDelayed(this, STARTUP_POLL_MILLIS)
                    StartupStatus.Idle -> {
                        typingSteps = false
                        startupTyped(generation)
                    }
                    is StartupStatus.Stalled -> showStartupStalled(steps, steps[status.index], cancelled, generation)
                }
            }
        }, STARTUP_POLL_MILLIS)
    }

    private fun showStartupStalled(steps: List<LabeledStep>, step: LabeledStep, cancelled: AtomicBoolean, generation: Int) {
        overlay.showNotice(GameNotice(getString(R.string.kairo_startup_not_recognized),
            getString(R.string.kairo_startup_waiting, step.label),
            listOf(TrayButton(getString(R.string.kairo_action_restart)) { openMenu("f-restart", confirm = true) },
                TrayButton(getString(R.string.kairo_open_keyboard)) { showKeyboard() },
                TrayButton(getString(R.string.kairo_retry), ButtonTone.PRIMARY) {
                    bridge.startupResume()
                    watchStartup(steps, cancelled, generation)
                })))
    }

    /**
     * Watches for [rules]' screens for the whole session: a swap is asked for mid-game, so the player
     * taking over (cancelAutomation, as opening the menu) stops only the typed startup steps.
     */
    private fun scheduleDiskSwaps(rules: List<DiskSwap>, sources: Map<String, E>, generation: Int) {
        if (rules.isEmpty()) return
        Thread {
            var failedRule: DiskSwap? = null
            try {
                DiskSwapMatcher(bridge::screenSignature, { MachineStatus(bridge.status()).state == "Running" },
                    { !current(generation) },
                    { rule ->
                        failedRule = rule
                        performDiskSwap(rule, sources.getValue(rule.contentId), generation)
                    }).run(rules)
            } catch (error: Exception) {
                runOnUiThread { if (current(generation)) showDiskSwapFailure(failedRule, error, rules, sources, generation) }
            }
        }.apply { name = "${identity.appName}-disk-swaps"; start() }
    }

    /** The information notice for a swap in progress, replaced as its progress text changes. */
    private var swapping: GameNotice? = null

    private fun performDiskSwap(rule: DiskSwap, entry: E, generation: Int) {
        while (!beginMediaChange()) {
            if (!current(generation)) return
            Thread.sleep(100)
        }
        val notice = GameNotice(getString(R.string.kairo_disk_swapping, text(mediaSlots[rule.drive].label), entry.displayName), "")
        var changed = false
        try {
            runOnUiThread { if (current(generation)) overlay.showNotice(notice) }
            val disk = library.prepare(entry, AtomicBoolean(false)) { message ->
                runOnUiThread {
                    if (current(generation) && overlay.showing(notice))
                        overlay.showNotice(GameNotice(notice.title, message).also { swapping = it })
                }
            }
            if (!current(generation)) return
            check(bridge.media(rule.drive, disk.absolutePath)) { getString(R.string.kairo_media_insert_failed) }
            changed = true
            if (rule.key.isNotEmpty() || rule.enter) {
                Thread.sleep(150)
                if (current(generation)) bridge.startupKeys(rule.key, rule.enter)
            }
        } finally {
            endMediaChange(rule.drive, entry, changed)
            runOnUiThread {
                if (overlay.showing(notice) || swapping?.let(overlay::showing) == true) overlay.hideNotice()
                swapping = null
            }
        }
    }

    /** The game keeps running, so the notice docks at the top instead of blocking it. */
    private fun showDiskSwapFailure(rule: DiskSwap?, error: Exception, rules: List<DiskSwap>, sources: Map<String, E>,
                                    generation: Int) {
        val why = error.message ?: getString(R.string.kairo_unknown_error)
        overlay.showNotice(GameNotice(getString(R.string.kairo_disk_swap_failed),
            rule?.let { getString(R.string.kairo_disk_swap_failed_drive_message, text(mediaSlots[it.drive].label), why) }
                ?: getString(R.string.kairo_disk_swap_failed_message, why),
            listOf(TrayButton(getString(R.string.kairo_continue_manually)),
                TrayButton(getString(R.string.kairo_choose_disk)) { chooseMedia(rule?.drive ?: 0) },
                TrayButton(getString(R.string.kairo_retry), ButtonTone.PRIMARY) { scheduleDiskSwaps(rules, sources, generation) })))
    }

    // Media ------------------------------------------------------------------------------------------

    /** The drives' disks when the player restarted (restartGame), which the restarted machine gets back. */
    private var keptMedia: List<E?>? = null

    /**
     * Whether the machine starts again once [restoreMedia] has put the kept disks back, so it boots
     * from them: true where the machine boots from its drives.
     */
    protected open val resetWithKeptMedia = true

    /**
     * After a restart, the disks [kept] held that the launch did not put in go back in their drives,
     * and the machine starts again with them ([resetWithKeptMedia]).
     */
    private fun restoreMedia(kept: List<E?>) {
        val changes = kept.withIndex().filter { (slot, entry) -> slot < mounted.size && entry?.id != mounted[slot]?.id }
        if (changes.isEmpty() || !beginMediaChange()) return
        Thread {
            val failure = try {
                for ((slot, entry) in changes) {
                    val image = entry?.let { library.prepareMedia(it, AtomicBoolean(false)) { } }
                    check(bridge.media(slot, image?.absolutePath)) { getString(R.string.kairo_media_insert_failed) }
                    runOnUiThread { mounted[slot] = entry }
                }
                if (resetWithKeptMedia) bridge.reset()
                null
            } catch (error: Exception) { error.message ?: getString(R.string.kairo_media_change_failed) }
            runOnUiThread {
                mediaBusy = false
                refreshSettingValues()
                failure?.let { toast(it) }
            }
        }.apply { name = "${identity.appName}-media-kept"; start() }
    }

    /** What each media slot holds, by library entry; null for an empty slot or media the library lacks. */
    private val mounted: MutableList<E?> by lazy { MutableList(mediaSlots.size) { null } }
    @Volatile private var mediaBusy = false

    /** Called from [startGame] once the machine runs: what each slot booted with. */
    protected fun mediaStarted(entries: List<E?>) = runOnUiThread {
        mounted.fill(null)
        entries.forEachIndexed { slot, entry -> if (slot < mounted.size) mounted[slot] = entry }
    }

    /**
     * Starts a media change the product makes itself, as a disk swap at a game's prompt: false while
     * another change runs. The product changes the slot with [bridge] and then calls [endMediaChange].
     */
    @Synchronized protected fun beginMediaChange(): Boolean {
        if (mediaBusy) return false
        mediaBusy = true
        return true
    }

    /** Ends a change [beginMediaChange] started; [slot] now holds [entry] when [changed]. */
    protected fun endMediaChange(slot: Int, entry: E?, changed: Boolean) = runOnUiThread {
        mediaBusy = false
        if (changed) mounted[slot] = entry
        refreshSettingValues()
    }

    /** Opens the flyout at [slot]'s disk choice, as when the player must pick a disk a game asks for. */
    protected fun chooseMedia(slot: Int) {
        openMenu()
        pages.push(mediaPage())
        pages.push(ChoicePage(mediaChoice(slot), "media$slot"))
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

    private fun mediaPage() = GroupsPage(text(mediaPageTitle), { currentGame?.title }, {
        listOf(RowGroup(getString(R.string.kairo_media_drives_group), mediaSlots.indices.map(::mediaRow),
            getString(R.string.kairo_media_drives_foot)))
    })

    private fun mediaRow(slot: Int) = SettingsRow.choice("media$slot", text(mediaSlots[slot].label), mediaChoice(slot),
        value = { mounted[slot]?.displayName ?: getString(R.string.kairo_empty) },
        tone = { if (mounted[slot] == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !mediaBusy },
        disabledReason = { getString(if (mediaBusy) R.string.kairo_media_wait else R.string.kairo_media_start_first) })

    /**
     * Empty ejects; then the running game's own disks ([ownMedia]); then Other, every disk of the
     * library the slot takes, on a page of its own: a player rarely needs one, and a library can
     * hold thousands. Choose file takes a disk image on the device, outside the library.
     */
    private fun mediaChoice(slot: Int): ChoiceSpec {
        fun takes(entry: E) = entry.playable && mediaSlots[slot].accepts(entry)
        return GameDisks.choice(this, text(mediaSlots[slot].label), { currentGame?.title }, {
            val current = currentEntry ?: return@choice emptyList()
            GameDisks.own(current, libraryEntries, mounted, catalog.setDisks(current).toSet(), catalog::matchId, ::takes)
        }, { libraryEntries.filter(::takes) }, { mounted[slot]?.id ?: GameDisks.EMPTY }, insert = { changeMedia(slot, it) }) {
            listOfNotNull(SettingsRow.action("choose", getString(R.string.kairo_choose_file)) {
                pickingMediaSlot = slot
                DocumentPicker.open(this, MEDIA_FILE_REQUEST, failed = ::toast)
            }, if (!mediaSlots[slot].folders) null else SettingsRow.action("choose-folder", getString(R.string.kairo_choose_folder)) {
                pickingMediaSlot = slot
                DocumentPicker.openFolder(this, MEDIA_FOLDER_REQUEST, mediaFolderStart(), failed = ::toast)
            })
        }
    }

    /** The drive a disk image picked on the device goes into. */
    private var pickingMediaSlot: Int? = null

    /**
     * A file picked for [slot], read as a file opened from outside the library is (an image, or a
     * ZIP or 7z archive of them): the one image the drive takes goes in; with several, the player
     * picks one.
     */
    private fun insertPickedFile(slot: Int, uri: Uri) {
        val file = ExternalGameIntent.file(Intent(Intent.ACTION_VIEW, uri), this, tree) ?: return
        Thread {
            val result = runCatching {
                library.inspectExternal(file, AtomicBoolean(false)).filter { it.playable && mediaSlots[slot].accepts(it) }
            }
            runOnUiThread {
                result.onFailure { toast(it.message ?: getString(R.string.kairo_media_change_failed)) }
                result.onSuccess { found ->
                    when (found.size) {
                        0 -> toast(getString(R.string.kairo_media_file_none, file.name, text(mediaSlots[slot].label)))
                        1 -> changeMedia(slot, found.single())
                        else -> pages.push(ChoicePage(ChoiceSpec(getString(R.string.kairo_media_file_choose), { file.name }, {
                            listOf(ChoiceGroup(null, GameDisks.options(found)))
                        }, { null }, { id -> found.firstOrNull { it.id == id }?.let { changeMedia(slot, it) } }, cycles = false)))
                    }
                }
            }
        }.apply { name = "${identity.appName}-media-file"; start() }
    }

    /** A folder picked for [slot] (MediaSlot.folders), read as the library reads a folder game. */
    private fun insertPickedFolder(slot: Int, tree: Uri) {
        Thread {
            val result = runCatching { library.inspectExternalFolder(tree, AtomicBoolean(false)) }
            runOnUiThread {
                result.onFailure { toast(it.message ?: getString(R.string.kairo_media_change_failed)) }
                result.onSuccess { changeMedia(slot, it) }
            }
        }.apply { name = "${identity.appName}-media-folder"; start() }
    }

    private fun changeMedia(slot: Int, entry: E?) {
        if (!beginMediaChange()) return
        refreshSettingValues()
        Thread {
            val failure = try {
                val image = entry?.let { library.prepareMedia(it, AtomicBoolean(false)) { } }
                check(bridge.media(slot, image?.absolutePath)) { getString(R.string.kairo_media_insert_failed) }
                null
            } catch (error: Exception) { error.message ?: getString(R.string.kairo_media_change_failed) }
            endMediaChange(slot, entry, failure == null)
            runOnUiThread {
                toast(failure ?: getString(R.string.kairo_media_changed, text(mediaSlots[slot].label),
                    entry?.displayName ?: getString(R.string.kairo_empty)))
            }
        }.apply { name = "${identity.appName}-media"; start() }
    }

    companion object {
        private const val FIRMWARE_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 1
        private const val FIRMWARE_FILE_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 2
        private const val MEDIA_FILE_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 3
        private const val MEDIA_FOLDER_REQUEST = KairoActivity.FIRST_PRODUCT_REQUEST - 4
        /** The folder firmware was last imported from. */
        private const val FIRMWARE_FOLDER = "firmware_folder_v1"
        /** The folder a Disks drive last took (Choose folder). */
        private const val MEDIA_FOLDER = "media_folder_v1"
        /** The drive choice that leaves a drive empty. */
        private const val EMPTY_DRIVE = GameDisks.EMPTY
        /** How often a loading session checks whether the machine shows a picture yet. */
        private const val PICTURE_POLL_MILLIS = 100L
        /** How often the frontend checks on the startup typer. */
        private const val STARTUP_POLL_MILLIS = 100L
        /** How long a startup choice waits for its screen before the typer stalls on it. */
        private const val CHOICE_TIMEOUT_MS = 120000
        private const val AUTOMATION_ON = "on"
        private const val AUTOMATION_OFF = "off"
    }
}

