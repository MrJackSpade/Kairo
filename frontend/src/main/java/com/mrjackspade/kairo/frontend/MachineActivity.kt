package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The activity of a frame-driven Kairo app: the library and game page, the game session with
 * its flyout and pages, input from controllers, touch, keyboards and the second screen, save
 * states, and the app's lifecycle. A product supplies its machine through the abstract members;
 * the frontend owns everything a player sees and does around it.
 */
abstract class MachineActivity<E : LibraryItem> : Activity(), SurfaceHolder.Callback {
    /** A product's names, screen, and files. */
    class Identity(
        /** The pixel-font product name, such as KAIRO88. */
        val brand: String,
        /** The app's name, such as Kairo88. */
        val appName: String,
        /** The machine's name, such as PC-88. */
        val guestName: String,
        /** The shared preferences file. */
        val preferences: String,
        val screenWidth: Int,
        val screenHeight: Int,
        /** The displayed width over height. */
        val aspect: Double,
        /** The file a save state's directory holds; a slot without it is empty. */
        val stateFile: String,
        /** The About page's credits, such as which emulator the app runs. */
        val credits: String,
        /** What the library scans the ROM folder for, as first-run setup words it. */
        val media: String = "disk images and the ZIP and 7z archives holding them"
    )

    protected abstract val identity: Identity
    protected abstract val bridge: MachineBridge

    // The guest's keyboard and controls.
    protected abstract val keyboard: GuestKeyboardLayout
    /** The highest guest key code. */
    protected abstract val maxKeyCode: Int
    protected abstract fun keyLabel(code: Int): String
    /** The guest key an Android key types, or null for none. */
    protected abstract fun guestKey(androidKey: Int): Int?
    /** Keys a controller binding can send; codes the keyboard lacks go on an Other tab. */
    protected abstract val controllerKeyCodes: List<Int>
    protected abstract val bindingsCodec: ControllerBindingsCodec
    protected abstract fun defaultBindings(layout: ControllerLayout): List<ControllerBinding>
    /** One label per [JoystickInputRouter.DEFAULT_CONTROLS] control. */
    protected abstract val joystickLabels: List<String>
    protected abstract val joystickHelp: String
    /** What tapping the screen means in Keyboard mode, as the Screen tap choice describes it. */
    protected open val keyboardTapHelp: String get() = "Tap opens the ${identity.guestName} keyboard"

    // The library.
    protected abstract fun createCatalog(): LayeredGameCatalog
    protected abstract fun createLibrary(catalog: LayeredGameCatalog): MachineLibrary<E>

    /**
     * Runs on a worker thread: prepares [entry]'s media and starts the machine with [bridge],
     * reporting what it does through [progress]. Throws to fail the launch; the message is shown.
     */
    protected abstract fun startGame(entry: E, game: CatalogGame, cancelled: AtomicBoolean,
                                     progress: (String) -> Unit)

    /** The machine stopped and the session ended; forget what the product kept about it. */
    protected open fun sessionEnded() {}

    /** Rows in the game flyout's GAME group after Save state and Load state, such as Disks. */
    protected open fun gameRows(): List<SettingsRow> = emptyList()

    /** Game settings › MACHINE for [entry], built with [machineValue]; null for none. */
    protected abstract fun machineGroup(entry: E, enabled: () -> Boolean): RowGroup?

    /** Settings › Machine. */
    protected abstract fun machinePage(): Page

    /** First-run step 2. */
    protected abstract val firmware: MachineFirstRun.Firmware

    /** An activity result for one of the product's own requests; false when it is not one. */
    protected open fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false

    /** Why a save or load failed, for a nonzero code the bridge returned. */
    protected abstract fun stateError(code: Int): String

    /** A state loaded: the product rereads what the machine now holds, such as its disks. */
    protected open fun stateLoaded() {}

    /** The About page's input help: what each control does. */
    protected open val aboutHelp: List<Pair<String, String>> get() = listOf(
        "Open the menu" to "Mode or Home button, Back, Menu, or a swipe from the left edge",
        "Open the keyboard" to "Swipe in from the right edge",
        "Physical keyboard" to "Types into the ${identity.guestName} while the menu is closed")

    /** Whether this build may download catalog artwork (the product's build flavor decides). */
    protected abstract val artworkDownloadsEnabled: Boolean

    /** Whether the guest mouse can take an absolute position; without it, mouse touch is a touchpad. */
    protected open val directTouchAvailable: Boolean = false

    // ---------------------------------------------------------------------------------------

    protected lateinit var catalog: LayeredGameCatalog
        private set
    protected lateinit var library: MachineLibrary<E>
        private set
    protected val handler = Handler(Looper.getMainLooper())
    protected lateinit var pages: PageStack
        private set
    protected val preferences by lazy { getSharedPreferences(identity.preferences, MODE_PRIVATE) }

    /** The game running, or null in the library. */
    protected var currentEntry: E? = null
        private set
    protected var currentGame: CatalogGame? = null
        private set
    protected val libraryVisible: Boolean get() = sessionState.libraryVisible
    /** True while a machine starts or stops. */
    @Volatile protected var transitioning = false
        private set
    protected val libraryEntries: List<E> get() = libraryFlow.entries
    protected val machineRunning: Boolean get() = currentEntry != null && MachineStatus(bridge.status()).running

    private val inputRouter by lazy {
        InputRouter({ code, down -> bridge.key(code, down) }, maxKeyCode, keyboard.lockCodes)
    }
    private val joystickRouter by lazy { JoystickInputRouter({ control, down -> bridge.joystick(control, down) }) }
    private val mouseRouter by lazy { MouseInputRouter(::mouseMove, ::mouseButton) }
    private val gamepadMapper by lazy {
        GamepadMapper(inputRouter, joystickRouter, mouseRouter, ::controllerAction, ::controllerActionReleased,
            bindingsCodec.parse(null))
    }
    private val inputDispatch: InputDispatchCoordinator by lazy {
        InputDispatchCoordinator(FrontendInputScreens(
            { if (::pages.isInitialized) pages else null },
            { if (::libraryFlow.isInitialized) libraryScreen else null },
            { if (::overlay.isInitialized) overlay else null },
            { !libraryVisible }, { transitioning }, ::handleBack),
            gamepadMapper, inputRouter, ::guestKey, ::handleBack,
            { if (menuOpen) closeMenu() else openMenu() },
            {
                if (::root.isInitialized && root.isInTouchMode)
                    (currentFocus ?: screen).requestFocusFromTouch()
            }, ::releaseTouchInputs)
    }
    private val controllerDevices by lazy {
        ControllerDeviceMonitor(this, inputDispatch::releaseDevice) {
            controllerProfiles.configuration.devicesChanged()
            if (::onScreenControls.isInitialized) onScreenControls.devicesChanged()
            if (currentEntry == null) gamepadMapper.bindings = controllerFlow.global()
        }
    }
    private var controllerSetupPending = false
    private var pendingControllerExternalRequest = false
    private val guestLifecycle: GuestLifecycleCoordinator by lazy {
        GuestLifecycleCoordinator(fullscreen::restore, { releaseInputs() }, ::applyPauseState, {}, {},
            initiallyVisible = false,
            companionActive = { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isCompanionActive },
            companionHoldsStop = { ::secondaryKeyboard.isInitialized && secondaryKeyboard.holdsStoppedPrimary },
            resetGestures = edgeSwipes::reset,
            releaseOnFocusLoss = { releaseInputs(preserveAutomation = true) })
    }
    private val saveFolder by lazy {
        SaveFolderSettings(this, pages, preferences, AppSaveLocation(File(filesDir, "states")), FolderStateSlots.UNITS,
            ::saveTitle, { "Slot ${it.removePrefix("slot")}" },
            { if (currentEntry != null) "Return to the library to change the save folder." else null })
    }
    private val graphics by lazy { GraphicsOptions(this, preferences) { updateViewport(); refreshSettingValues() } }
    private val shaderOptions by lazy {
        ShaderOptions(this, preferences, { if (::pages.isInitialized) pages else null },
            { path -> bridge.setShaderPreset(path); refreshSettingValues() }, ::toast)
    }
    private val controllerProfiles by lazy {
        ControllerProfileStore(preferences, bindingsCodec::parse,
            { bindingsCodec.toJson(it).toString() }, ::defaultBindings)
    }
    private val controllerFlow by lazy {
        ControllerProfileCoordinator(controllerProfiles, gamepadMapper,
            { entry: E -> entry.key.takeIf { entry.contentId != null } },
            { entry: E -> effectiveControllerBindings(catalog.resolve(entry)) },
            { id, bindings -> catalog.setOverrideSubfield(id, "controller", "bindings", bindingsCodec.toJson(bindings)) },
            { id -> catalog.updateOverrideSubfields(id, "controller", mapOf("bindings" to null, "profile" to null)) },
            { currentEntry },
            { currentEntry?.let { currentGame = catalog.resolve(it) } })
    }
    private val keyMapping by lazy {
        KeyMappingMode(controllerFlow::activeBindings, controllerFlow::saveActive, controllerFlow::physical,
            { inputDispatch.guestCapture = it })
    }
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
    private lateinit var keyboardPanel: GuestKeyboardPanel
    private lateinit var swappedKeyboardPanel: GuestKeyboardPanel
    private lateinit var secondaryKeyboard: SecondaryDisplayCoordinator
    private lateinit var onScreenControls: OnScreenControls
    private lateinit var libraryFlow: LibraryFlow<E>
    private val libraryScreen: LibraryScreen<E> get() = libraryFlow.screen
    private val touchUi by lazy {
        TouchUiCoordinator(this, libraryScreen, { !libraryVisible },
            { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible },
            ::updateViewport, ::applyPauseState,
            { if (::screen.isInitialized) screen.requestFocus() })
    }
    private val touchSettingsCoordinator by lazy {
        TouchSettingsCoordinator(object : TouchSettingsStore<E> {
            override fun global() = TouchInputSelection(globalInputMode, globalTouchDirect)
            override fun saveGlobal(value: TouchInputSelection) {
                globalInputMode = value.mode
                globalTouchDirect = value.directTouch
                preferences.edit()
                    .putString("input_mode", InputModeDecider.storageValue(value.mode))
                    .putString("touch_mouse", touchStorage(value.directTouch)).apply()
            }
            override fun mode(game: E) = inputValue(game, "mode", { it.inputMode },
                InputModeDecider::parse, InputModeDecider::storageValue) { globalInputMode }
            override fun directTouch(game: E) = inputValue(game, "touch", { it.inputTouch },
                { it == "direct" }, ::touchStorage) { globalTouchDirect }
            override fun secondaryTouchpad(game: E) = inputValue(game, "secondary",
                { it.inputSecondary }, { it == "touchpad" }, { if (it) "touchpad" else "keyboard" },
                builtIn = true) { false }
        }, listOf(
            InputModeDecider.Mode.AUTO to ChoiceOption("auto", "Auto", "Keyboard unless the game reads the mouse"),
            InputModeDecider.Mode.KEYBOARD to ChoiceOption("keyboard", "Keyboard", keyboardTapHelp),
            InputModeDecider.Mode.MOUSE to ChoiceOption("mouse", "Mouse")),
            "Direct tap lands on the touched point in games that move the " +
                "cursor one pixel per mouse count and stop it at the screen’s top-left edge.",
            changed = { entry ->
                if (entry != null && currentEntry?.id == entry.id) {
                    currentGame = catalog.resolve(entry)
                    setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                }
            }, directTouchAvailable = directTouchAvailable)
    }

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

    private lateinit var firstRunSetup: MachineFirstRun
    private val artwork by lazy {
        ArtworkCoordinator(this, pages, catalog.artworkStore,
            { entry: E -> entry.key },
            { entry, kind ->
                val game = catalog.resolve(entry)
                val urlKind = if (kind == "preview") "previewUrl" else "boxArtUrl"
                val url = (if (kind == "preview") game.previewUrl else game.boxArtUrl)
                    ?.takeIf { catalog.sourceOf(entry, "artwork", kind) == catalog.sourceOf(entry, "artwork", urlKind) }
                ArtworkCoordinator.Record(if (kind == "preview") game.preview else game.boxArt, url,
                    catalog.sourceOf(entry, "artwork", kind))
            }, { ArtworkOverridePath.valid(it, "art/") },
            { id, kind, path ->
                if (path == null) catalog.resetArtworkOverride(id, kind)
                else catalog.setArtworkOverride(id, kind, path)
            }, { id -> libraryScreen.metadataChanged(id) },
            { entry -> catalog.resolve(entry).title },
            ::toast, catalog::validImageUrl)
    }
    private lateinit var controllerPages: ControllerPages<E>
    private val fullscreen by lazy { GameFullscreen(this) { !libraryVisible } }
    private val sessionState = SessionNavigationState()
    private val gameOrientation by lazy { GameOrientation(this, preferences) }
    private val sessionNavigation: SessionNavigationCoordinator by lazy {
        SessionNavigationCoordinator(sessionState, libraryScreen,
            { if (::sessionFlow.isInitialized) sessionFlow else null },
            { currentEntry != null }, { sessionFromFrontend }, ::exitApp, edgeSwipes::reset,
            {
                releaseInputs()
                hideKeyboard()
            }, {}, libraryFlow::show, ::applyPauseState, { screen.requestFocus() }, ::endSessionForLibrary,
            gameOrientation)
    }
    private val backCoordinator: FrontendBackCoordinator by lazy {
        FrontendBackCoordinator(this, { if (::pages.isInitialized) pages else null }, libraryScreen,
            { if (!sessionNavigation.resumeGame()) finish() },
            { if (::keyboardPanel.isInitialized) keyboardPanel else null }, { openMenu() },
            { transitioning }, ::hideKeyboard)
    }
    private val romTree: Uri? get() = libraryFlow.tree
    private val artworkDownloads by lazy {
        CatalogArtworkDownloadController(this, libraryScreen, { libraryEntries },
            catalog::missingArtworkFor, catalog::downloadArtwork) { pages.refresh() }
    }
    private val catalogUpdates by lazy {
        CatalogUpdateController(this, { task -> catalog.downloadUpdate(task) },
            { message -> if (!message.endsWith("…")) toast(message) }, { libraryScreen.refreshCatalog() },
            { if (libraryVisible) toast("Catalogs updated") }, { libraryMenu.showCatalogProgress(it) },
            catalog.installedCatalogs::update)
    }
    private var sessionFromFrontend = false
    private var externalEntries: List<E> = emptyList()
    private val externalDispatcher by lazy {
        ExternalGameDispatcher(this, contentResolver, { libraryEntries },
            { entry: E -> Uri.parse(entry.uri) },
            { file, cancelled -> library.inspectExternal(file, cancelled) },
            { entries, inspected ->
                if (inspected) externalEntries = entries
                chooseExternalGame(entries)
            }, libraryScreen::showStatus, "${identity.appName}-external-game")
    }

    private lateinit var overlay: GameOverlay
    private lateinit var libraryMenu: LibraryMenu<E>
    private lateinit var sessionFlow: SessionFlow
    /** What the progress strip says while a machine stops or starts. */
    private var progressText = ""
    private val menuOpen: Boolean get() = sessionFlow.isOpen
    private var userPaused: Boolean
        get() = sessionState.userPaused
        set(value) { sessionState.userPaused = value }
    private val activityVisible: Boolean get() = guestLifecycle.isVisible
    private var muted = false
    private var exiting = false
    private var relocating = false
    private lateinit var viewScale: ViewScale
    private val edgeSwipes by lazy { EdgeSwipeNavigation(resources.displayMetrics.density) }
    private val inputModeDecider = InputModeDecider()
    private var globalInputMode = InputModeDecider.Mode.AUTO
    private var globalTouchDirect = false
    private var mouseTouchActive = false
    private var mouseDragging = false
    private var mouseMoved = false
    private var mouseTouchStartX = 0f
    private var mouseTouchStartY = 0f
    private var mouseTouchLastX = 0f
    private var mouseTouchLastY = 0f
    private var mouseFractionX = 0f
    private var mouseFractionY = 0f
    private var pendingMouseHold: Runnable? = null
    private var pendingMouseRelease: Runnable? = null
    private val stateFlow by lazy {
        StateSlotCoordinator(this, pages, handler,
            { currentEntry?.takeIf { it.contentId != null && !libraryVisible }?.key }, { currentGame?.title },
            { id ->
                saveFolder.folder?.let { FolderStateSlots(it, id, identity.stateFile, File(cacheDir, "state-staging")) }
                    ?: StateSlotStore(saveFolder.app.root, id, identity.stateFile)
            },
            { (if (::secondaryKeyboard.isInitialized) secondaryKeyboard.activeGameSurface else null) ?: screen },
            { scratch -> bridge.saveState(scratch.absolutePath) },
            ::loadMachineState, ::describeStateError, ::toast,
            {
                releaseInputs()
                inputModeDecider.reset()
                userPaused = false
                closeMenu()
            }, {}, "Save states are available for games started from the library.")
    }
    @Volatile private var startGeneration = 0

    private val updateStatus = object : Runnable {
        override fun run() {
            if (::pages.isInitialized && pages.isActive) pages.tick()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RgDsDisplayRouter.routeToUpper(this)) {
            relocating = true
            return
        }
        catalog = createCatalog()
        library = createLibrary(catalog)
        graphics.load()
        bridge.setShaderPreset(shaderOptions.selectedPath())
        muted = preferences.getBoolean("muted", false)
        globalInputMode = InputModeDecider.parse(preferences.getString("input_mode", "auto"))
        globalTouchDirect = directTouchAvailable && preferences.getString("touch_mouse", "touchpad") == "direct"
        bridge.setMuted(muted)
        controllerFlow.initialize()
        controllerDevices.register(handler)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ImmersiveWindow.apply(this)
        viewScale = ViewScale(this)
        buildUi()
        secondaryKeyboard = SecondaryDisplayCoordinator(this, inputRouter, mouseRouter, keyboard, identity.appName,
            { available ->
                if (available && secondaryKeyboard.isKeyboardVisible && keyboardPanel.visibility == View.VISIBLE) {
                    keyboardPanel.close()
                    updateViewport()
                    applyPauseState()
                }
            },
            { surface, _, _ -> bridge.setSurface(surface) },
            { event, width, height -> handleScreenTouch(event, width, height) },
            ::onSecondarySwapChanged, mapping = keyMapping)
        artwork.restoreInstanceState(savedInstanceState)
        controllerPages = ControllerPages(this, pages,
            controllerFlow::load, controllerFlow::save, controllerFlow::reset,
            { controllerProfiles.globalCustom },
            controllerFlow::physical, controllerFlow::savePhysical, controllerFlow::resetPhysical,
            { controllerProfiles.physicalCustom },
            { controllerFlow.deadZone }, { controllerFlow.deadZone = it },
            ControllerGuestSpec(identity.guestName, controllerKeyCodes, ::keyLabel, keyboard.modifiers,
                JoystickInputRouter.DEFAULT_CONTROLS.zip(joystickLabels), joystickHelp,
                listOf("menu" to "Open menu", "pause" to "Pause or resume",
                    "fastForward" to "Fast forward while held", "restart" to "Restart",
                    "exit" to "Exit"), keyboard),
            { bindingsCodec.toJson(it) }, controllerProfiles.configuration,
            { releaseInputs(); controllerFlow.refresh(null) }, { entry -> controllerMappingStatus(entry) },
            { entry -> catalog.resolve(entry).title },
            onScreenControls, touchSettingsCoordinator) { releaseInputs() }
        val strings = LibraryStrings(identity.brand, identity.appName)
        val libraryPage = LibraryScreen(this, catalog, strings, pages,
            ::chooseRomFolder, { preferences.getString("last_played_entry", null) },
            { launchEntry(it) }, ::showDetailPreview, ::showGameDetails, ::showLibrarySelection)
        libraryFlow = LibraryFlow(this, preferences, libraryPage, ROM_FOLDER_REQUEST,
            { folder -> library.cached(folder) },
            { folder, force, cancelled, progress -> library.scan(folder, force, cancelled, progress) },
            "Choose a ROM folder to find disk images and the ZIP and 7z archives holding them",
            "Folder access expired. Select the ROM folder again.",
            { entries ->
                val errors = entries.count { it.error != null }
                "${entries.count { it.playable }} games" +
                    (if (errors == 0) "" else " · $errors unreadable") +
                    " · ${library.hashCount} hashes this scan"
            },
            { _ -> },
            { _ ->
                if (firstRunSetup.isChoosingRomFolder) advanceFirstRunFirmware(folderChosen = true)
                else libraryFlow.refresh(false)
            },
            { message -> if (firstRunSetup.isOpen) toast(message) }, store = library::store)
        libraryMenu = LibraryMenu(this, pages, libraryFlow, catalog, strings,
            { catalogUpdates.check(false) },
            { if (artworkDownloadsEnabled) artworkDownloads else null },
            ::settingsRows, ::exitApp)
        libraryPage.openMenuAction = libraryMenu::open
        // Pages and the game overlay sit above the library; pages come forward when they open.
        root.addView(libraryScreen, root.indexOfChild(overlay), FrameLayout.LayoutParams(-1, -1))
        touchUi.bind(keyboardPanel)
        firstRunSetup = MachineFirstRun(pages, identity.brand, identity.appName, identity.guestName,
            identity.media, ::chooseRomFolder, ::advanceFirstRunFirmware, ::finishFirstRun, firmware)
        backCoordinator.register()
        handler.post(updateStatus)
        controllerSetupPending = controllerProfiles.configuration.needsSetup
        root.post {
            if (isFinishing || isDestroyed) return@post
            if (controllerSetupPending) {
                pages.push(controllerProfiles.configuration.setupPage(identity.brand, ::finish) {
                    controllerSetupPending = false
                    controllerFlow.initialize()
                    pages.closeAll()
                    continueStartup(savedInstanceState)
                })
            } else continueStartup(savedInstanceState)
        }
    }

    private fun continueStartup(savedInstanceState: Bundle?) {
        libraryFlow.restore()
        val setupStep = preferences.getInt("onboarding_step_v1", if (romTree == null) 0 else 2)
        val externallyRequested = (savedInstanceState == null || pendingControllerExternalRequest ||
            savedInstanceState.getBoolean("controller_setup_external_v1")) &&
            ExternalGameIntent.hasRequest(intent)
        if (externallyRequested) dispatchExternalGame(intent)
        else if (romTree != null && libraryFlow.hasGrant(romTree!!) && setupStep >= 2) libraryFlow.refresh(false)
        if (setupStep < 2 && !externallyRequested) firstRunSetup.show(if (setupStep == 0)
            MachineFirstRun.Step.ROM_FOLDER else MachineFirstRun.Step.FIRMWARE, romTree != null)
        applyPauseState()
        catalogUpdates.check(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("controller_setup_external_v1", controllerSetupPending && ExternalGameIntent.hasRequest(intent))
        artwork.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (RgDsDisplayRouter.isReturnToUpper(intent)) return
        setIntent(intent)
        if (controllerSetupPending) {
            pendingControllerExternalRequest = true
            return
        }
        dispatchExternalGame(intent)
    }

    /** A frontend launch matches the game against the library, so it waits for the library to load. */
    private fun dispatchExternalGame(intent: Intent) = libraryFlow.whenLoaded { externalDispatcher.dispatch(intent) }

    private fun chooseExternalGame(entries: List<E>) {
        val playable = entries.filter { it.playable }
        if (playable.isEmpty()) {
            libraryScreen.showStatus("No playable ${identity.guestName} disk found")
            return
        }
        playable.singleOrNull()?.let { launchEntry(it, true); return }
        // Back cancels and leaves the app open.
        pages.push(ChoicePage(ChoiceSpec("Choose a disk to start", { null }, {
            listOf(ChoiceGroup(null, playable.map { ChoiceOption(it.id, it.displayName) }))
        }, { null }, { id ->
            playable.firstOrNull { it.id == id }?.let { launchEntry(it, true) }
        }, stay = true, cycles = false)))
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = SurfaceView(this).apply {
            holder.addCallback(this@MachineActivity)
            isFocusableInTouchMode = true
            contentDescription = "${identity.guestName} display"
            setOnTouchListener { view, event -> handleScreenTouch(event, view.width, view.height) }
        }
        root.setOnTouchListener { _, event ->
            !libraryVisible && !menuOpen &&
                touchUi.handleKeyboardTouch(event,
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD)
        }
        root.addView(screen, FrameLayout.LayoutParams(identity.screenWidth, identity.screenHeight, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
        onScreenControls = OnScreenControls(this, root, gamepadMapper, preferences, ::applyPauseState,
            { controllerProfiles.configuration.layout })

        keyboardPanel = GuestKeyboardPanel(this, inputRouter, keyboard, ::hideKeyboard, mapping = keyMapping)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        onScreenControls.bindGuestKeyboard(keyboardPanel)
        swappedKeyboardPanel = GuestKeyboardPanel(this, inputRouter, keyboard, {}, showClose = false,
            onSwap = { secondaryKeyboard.toggleSwap() }, mouse = mouseRouter, mapping = keyMapping)
        root.addView(swappedKeyboardPanel, FrameLayout.LayoutParams(-1, -1))

        overlay = GameOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        pages = PageStack(this, root)
        pages.menuKey = { if (libraryVisible) libraryScreen.menuKey() else closeMenu() }
        pages.base = { if (libraryVisible && ::libraryFlow.isInitialized) libraryScreen else null }
        onScreenControls.pages = pages
        sessionFlow = SessionFlow(pages, ::gameFlyout, { !libraryVisible },
            {
                releaseInputs()
                hideKeyboard()
            },
            { open ->
                if (open) onScreenControls.refreshVisibility(false)
                applyPauseState()
            },
            { screen.requestFocus() },
            {
                handler.postDelayed({
                    if (menuOpen && Build.VERSION.SDK_INT >= 30) {
                        window.insetsController?.hide(WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars())
                    }
                }, 300)
            })

        setContentView(root)
        screen.requestFocus()
    }

    /** One settings list, shown the same way in the game flyout and the library flyout. */
    private fun settingsRows() = SessionMenus.settingsRows(SessionMenus.Settings(
        graphics = { pages.push(graphics.page(shaderOptions)) },
        controls = {
            pages.push(controllerPages.controlsPage(currentEntry?.takeIf { !libraryVisible && it.contentId != null }))
        },
        machine = { pages.push(machinePage()) },
        configurations = { pages.push(SessionMenus.configurationsPage(gameOrientation, saveFolder, "SAVE STATES")) },
        soundOn = { !muted },
        setSound = { on -> if (on == muted) toggleMute() },
        about = { pages.push(aboutPage()) }))

    /** A save folder's game name, as a library title when the game is still in the library. */
    private fun saveTitle(game: String): String = libraryEntries.firstOrNull { it.key.storageName == game }
        ?.let { catalog.resolve(it).title } ?: game

    /** The game flyout: the game's own pages, settings, then leaving. */
    private fun gameFlyout() = SessionMenus.gameFlyout(identity.brand, identity.appName,
        { currentGame?.title ?: "No game" }, ::menuStatusText, sessionNavigation,
        { stateFlow.rows() + gameRows() }, { currentEntry != null }, { currentEntry?.let(::showGameDetails) },
        ::settingsRows, restart = ::restartMachine, exit = ::exitApp)

    /** The menu itself pauses the machine, so it reads Paused only when the player paused the game. */
    private fun menuStatusText(): String {
        val state = MachineStatus(bridge.status()).state.let { if (it == "Paused" && !userPaused) "Running" else it }
        return listOfNotNull(state, if (muted) "Muted" else null).joinToString(" · ")
    }

    protected fun refreshSettingValues() {
        if (::pages.isInitialized) pages.refresh()
    }

    private fun toggleMute() {
        muted = !muted
        preferences.edit().putBoolean("muted", muted).apply()
        bridge.setMuted(muted)
        refreshSettingValues()
    }

    private fun updateViewport() {
        if (!::root.isInitialized || root.width <= 0 || root.height <= 0) return
        val keyboardHeight = if (::keyboardPanel.isInitialized &&
            keyboardPanel.visibility == View.VISIBLE) keyboardPanel.layoutParams.height else 0
        val viewport = graphics.viewport(root.width, root.height, keyboardHeight,
            identity.screenWidth, identity.screenHeight, identity.aspect) ?: return
        val params = screen.layoutParams as FrameLayout.LayoutParams
        if (params.width != viewport.width || params.height != viewport.height ||
            params.topMargin != viewport.topMargin ||
            params.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            screen.layoutParams = FrameLayout.LayoutParams(viewport.width, viewport.height,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = viewport.topMargin
            }
        }
    }

    protected fun openMenu(start: String? = null, confirm: Boolean = false) = sessionFlow.open(start, confirm)

    protected fun closeMenu() = sessionFlow.close()

    /** Shows the selected library game on the second screen, if there is one. */
    private fun showLibrarySelection(entry: E?) {
        if (!::secondaryKeyboard.isInitialized) return
        secondaryKeyboard.showLibrarySelection(entry, entry?.let(libraryScreen::metadata), catalog)
    }

    private fun applyPauseState() {
        if (!libraryVisible && ::secondaryKeyboard.isInitialized) secondaryKeyboard.setLibraryInfo(null)
        val presentation = sessionState.presentation(activityVisible, menuOpen, false, transitioning)
        val showingGuest = presentation.showGuest
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setShown(
            ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped && showingGuest)
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setAppearance(showingGuest,
            if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) Ui.BG else Color.BLACK)
        bridge.pause(presentation.pauseGuest)
        if (::overlay.isInitialized) {
            if (transitioning) overlay.showProgress(progressText) else overlay.hideProgress()
            overlay.setPaused(userPaused && !libraryVisible && !menuOpen && !transitioning)
            if (libraryVisible) overlay.hideNotice()
        }
        if (::onScreenControls.isInitialized) {
            if (::libraryFlow.isInitialized) touchUi.refreshControls(onScreenControls,
                showingGuest, ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped)
            else onScreenControls.refreshVisibility(false)
        }
    }

    private fun chooseRomFolder() = libraryFlow.chooseFolder()

    private fun advanceFirstRunFirmware(folderChosen: Boolean) {
        preferences.edit().putInt("onboarding_step_v1", 1).apply()
        firstRunSetup.show(MachineFirstRun.Step.FIRMWARE, folderChosen)
    }

    private fun finishFirstRun() {
        preferences.edit().putInt("onboarding_step_v1", 2).apply()
        firstRunSetup.close()
        applyPauseState()
        val tree = romTree
        if (tree != null && libraryFlow.hasGrant(tree)) libraryFlow.refresh(false)
        else libraryScreen.showStatus("Choose a ROM folder from the library menu when you’re ready")
    }

    /** The first-run firmware page shows what an import changed. */
    protected fun firmwareChanged() {
        if (::firstRunSetup.isInitialized) firstRunSetup.refreshFirmware()
        refreshSettingValues()
    }

    private fun launchEntry(entry: E, fromFrontend: Boolean = false) {
        releaseInputs()
        if (saveFolder.busy) {
            toast("Wait for the saves to finish moving.")
            return
        }
        if (!entry.playable) {
            toast(entry.error ?: "Refresh this entry before playing")
            return
        }
        if (entry !in externalEntries && (romTree == null || !libraryFlow.hasGrant(romTree!!))) {
            libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            return
        }
        val game = catalog.resolve(entry)
        val generation = ++startGeneration
        val cancelled = AtomicBoolean(false)
        if (::pages.isInitialized) pages.closeAll()
        transitioning = true
        showProgress("Preparing ${entry.displayName}…")
        gameOrientation.launching()
        Thread {
            val failure = try {
                bridge.stop()
                if (generation == startGeneration) {
                    inputRouter.resetLocks()
                    startGame(entry, game, cancelled) { message ->
                        runOnUiThread { if (generation == startGeneration) showProgress(message) }
                    }
                }
                null
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            }
            runOnUiThread {
                if (generation != startGeneration) return@runOnUiThread
                transitioning = false
                if (failure == null) {
                    controllerProfiles.configuration.endSession()
                    controllerProfiles.configuration.beginSession()
                    currentEntry = entry
                    currentGame = game
                    sessionFromFrontend = fromFrontend
                    preferences.edit().putString("last_played_entry", entry.id).apply()
                    setSecondaryInitialMode(game.inputSecondary == "touchpad")
                    inputModeDecider.reset()
                    gamepadMapper.bindings = effectiveControllerBindings(game)
                    userPaused = false
                    sessionFlow.reset()
                    sessionNavigation.enterGame(notify = false)
                    gameOrientation.launched()
                } else {
                    gameOrientation.launchFailed()
                    libraryScreen.showStatus("Launch failed: $failure")
                }
                applyPauseState()
            }
        }.apply { name = "${identity.appName}-launch"; start() }
    }

    private fun endSessionForLibrary(done: () -> Unit) {
        startGeneration++
        transitioning = true
        releaseInputs()
        hideKeyboard()
        showProgress("Stopping machine…")
        Thread {
            bridge.stop()
            runOnUiThread {
                currentEntry = null
                currentGame = null
                sessionFromFrontend = false
                userPaused = false
                sessionEnded()
                controllerProfiles.configuration.endSession()
                gamepadMapper.bindings = controllerFlow.global()
                onScreenControls.refreshVisibility(false)
                sessionFlow.reset()
                transitioning = false
                done()
                rebuildIfRescaled()
            }
        }.apply { name = "${identity.appName}-end-session"; start() }
    }

    private fun touchSelection(game: CatalogGame? = currentGame) =
        TouchInputPolicy.resolve(TouchInputSelection(globalInputMode, globalTouchDirect),
            game?.inputMode?.let(InputModeDecider::parse),
            game?.inputTouch?.let { it == "direct" && directTouchAvailable })

    private fun configuredInputMode(): InputModeDecider.Mode = touchSelection().selection.mode

    protected fun showKeyboard() { touchUi.showKeyboard() }

    protected fun hideKeyboard() { touchUi.hideKeyboard() }

    private fun mouseMove(dx: Int, dy: Int) = bridge.mouseMove(dx, dy)

    private fun mouseButton(button: Int, down: Boolean) = bridge.mouseButton(button, down)

    private fun moveGuestMouse(event: MotionEvent, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        mouseFractionX += (event.x - mouseTouchLastX) * identity.screenWidth / width
        mouseFractionY += (event.y - mouseTouchLastY) * identity.screenHeight / height
        mouseTouchLastX = event.x
        mouseTouchLastY = event.y
        val dx = mouseFractionX.toInt().coerceIn(-identity.screenWidth, identity.screenWidth)
        val dy = mouseFractionY.toInt().coerceIn(-identity.screenHeight, identity.screenHeight)
        mouseFractionX -= dx
        mouseFractionY -= dy
        if (dx != 0 || dy != 0) bridge.mouseMove(dx, dy)
    }

    /** A screen touch drives the guest mouse as a touchpad in Mouse mode, and opens the keyboard otherwise. */
    private fun handleScreenTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (libraryVisible || menuOpen) return false
        if (touchUi.handleKeyboardTouch(event,
                inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD))
            return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // With the keyboard on the second screen, the main screen touch is always the mouse.
                mouseTouchActive = (::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible) ||
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.MOUSE
                if (mouseTouchActive) {
                    pendingMouseRelease?.let(handler::removeCallbacks)
                    pendingMouseRelease = null
                    mouseRouter.release("touch")
                    mouseDragging = false
                    mouseMoved = false
                    mouseTouchStartX = event.x
                    mouseTouchStartY = event.y
                    mouseTouchLastX = event.x
                    mouseTouchLastY = event.y
                    mouseFractionX = 0f
                    mouseFractionY = 0f
                    val hold = Runnable {
                        if (mouseTouchActive && !mouseMoved) {
                            mouseDragging = true
                            mouseRouter.hold("touch", "leftButton")
                        }
                        pendingMouseHold = null
                    }
                    pendingMouseHold = hold
                    handler.postDelayed(hold, 500)
                }
            }
            MotionEvent.ACTION_MOVE -> if (mouseTouchActive) {
                if (!mouseMoved && (abs(event.x - mouseTouchStartX) > dp(12) ||
                        abs(event.y - mouseTouchStartY) > dp(12))) {
                    mouseMoved = true
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                }
                moveGuestMouse(event, width, height)
            }
            MotionEvent.ACTION_UP -> {
                if (mouseTouchActive) {
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                    moveGuestMouse(event, width, height)
                    if (mouseDragging) mouseRouter.release("touch")
                    else if (!mouseMoved) {
                        mouseRouter.hold("touch", "leftButton")
                        val release = Runnable {
                            mouseRouter.release("touch")
                            pendingMouseRelease = null
                        }
                        pendingMouseRelease = release
                        handler.postDelayed(release, 700)
                    }
                    hideKeyboard()
                } else showKeyboard()
                mouseTouchActive = false
                mouseDragging = false
                mouseMoved = false
            }
            MotionEvent.ACTION_CANCEL -> {
                pendingMouseHold?.let(handler::removeCallbacks)
                pendingMouseHold = null
                if (mouseTouchActive) mouseRouter.release("touch")
                mouseTouchActive = false
                mouseDragging = false
                mouseMoved = false
            }
        }
        return true
    }

    /** Game settings over the game page, or over the game flyout while the game runs. */
    private fun showGameDetails(entry: E) {
        pages.push(gameSettingsPage({ titleOf(entry) }) { gameSettings(entry) })
    }

    protected fun titleOf(entry: E) = catalog.resolve(entry).title

    private fun gameSettings(entry: E): List<RowGroup> {
        // Settings belong to the game's key, once its disk image has been read.
        val id = entry.key.takeIf { entry.contentId != null }
        val hashed = { id != null }
        val noHash = { GameSettingsRows.NO_HASH }
        val scope = { titleOf(entry) }
        fun game() = catalog.resolve(entry)
        fun custom(field: String) = id != null && catalog.sourceOf(entry, field) == "User override"
        val controllerSource = {
            val game = game()
            if (game.controllerBindings == null ||
                (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) GameSource.ALL_GAMES
            else GameSource.label(id?.let { catalog.sourceOf(entry, "controller", "bindings") } ?: GameSource.ALL_GAMES)
        }
        val inLibrary = libraryEntries.any { it.uri == entry.uri }
        return listOfNotNull(
            RowGroup("CONTROLS", listOf(controllerPages.gameRow(entry, hashed, controllerSource)) +
                (id?.let { touchSettingsCoordinator.gameRows(entry, scope, hashed) } ?: listOf(
                    SettingsRow.choice("game-tap", "Screen tap", ChoiceSpec("Screen tap", scope, { emptyList() },
                        { null }, select = {}), enabled = { false }, disabledReason = noHash)))),
            machineGroup(entry, hashed),
            RowGroup("LIBRARY", listOf(
                GameSettingsRows.text("title", "Title", "Game title", { game().title }, { custom("title") },
                    { id?.let { GameSource.label(catalog.sourceOf(entry, "title")) } ?: "Filename" },
                    { value -> saveGameSetting(entry, "Saved") { catalog.setOverride(id!!, "title", value) } },
                    { saveGameSetting(entry, "Saved") { catalog.resetOverride(id!!, "title") } },
                    enabled = hashed, disabledReason = noHash),
                artwork.row(entry, "boxArt", hashed),
                artwork.row(entry, "preview", hashed)),
                "Chosen images are copied into your library."),
            RowGroup("FILE", GameSettingsRows.file(entry.path, entry.zipEntry, entry.contentId, entry.error) +
                listOfNotNull(resetDiskRow(entry), if (!inLibrary) null else GameDeletionFlow.row(this, libraryFlow, entry,
                    GameDeletionFlow.Prompt(entry.path, "file"), "ROM folder",
                    { !libraryVisible && currentEntry?.uri == entry.uri }, "${identity.appName}-delete-game") {
                    library.deleteSource(entry, libraryEntries)
                })),
            RowGroup(null, listOf(GameSettingsRows.reset(hashed) {
                saveGameSetting(entry, "Game settings reset. Machine changes apply on next launch or restart.",
                    "Could not reset game settings") {
                    catalog.resetOverride(id!!)
                    if (currentEntry?.id == entry.id) {
                        currentGame = catalog.resolve(entry)
                        setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                        gamepadMapper.bindings = effectiveControllerBindings(currentGame)
                    }
                }
            })))
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

    /**
     * The game plays from a working copy of its disk, which holds the saves the game writes to
     * it. Resetting replaces that copy with the current disk image, as after editing the image.
     */
    private fun resetDiskRow(entry: E): SettingsRow? {
        if (!library.hasWorkingImage(entry)) return null
        return SettingsRow.action("reset-disk", "Reset disk", danger = true,
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
            })
    }

    private fun showDetailPreview(entry: E) = artwork.view(entry)

    private fun saveGameSetting(entry: E, message: String = "Saved. Machine changes apply on next launch or restart.",
                                failure: String? = null, action: () -> Unit) {
        try {
            action()
            if (currentEntry?.id == entry.id) currentGame = catalog.resolve(entry)
            libraryScreen.metadataChanged(entry.key)
            toast(message)
        } catch (error: Exception) {
            toast(failure ?: "Could not save: ${error.message ?: "Invalid value"}")
        }
        pages.refresh()
    }

    /** Restart boots the game again from its disks, with any machine changes since it started. */
    private fun restartMachine() {
        releaseInputs()
        val entry = currentEntry ?: return
        userPaused = false
        closeMenu()
        launchEntry(entry, sessionFromFrontend)
    }

    private fun aboutPage() = AboutDocuments.page(this, pages, aboutHelp, "Android reserves the system Home key.",
        identity.credits, "PRIVACY_POLICY.txt", "THIRD_PARTY_NOTICES.txt", ::diagnosticsPage)

    /** Live machine values, updated four times a second. */
    private fun diagnosticsPage() = object : GroupsPage("Diagnostics", { currentGame?.title }, {
        val status = MachineStatus(bridge.status())
        val running = currentEntry != null
        listOf(RowGroup("MACHINE STATUS", if (!running) listOf(SettingsRow.info("state", "State") { "Stopped" })
            else listOfNotNull(
                SettingsRow.info("state", "State") { MachineStatus(bridge.status()).state },
                status.error.takeIf { it.isNotEmpty() }?.let { SettingsRow.info("error", "Error") { it } },
                SettingsRow.info("frames", "Frames") { MachineStatus(bridge.status()).frames },
                SettingsRow.info("audio", "Audio") { MachineStatus(bridge.status()).audio },
                SettingsRow.info("xruns", "Xruns") { MachineStatus(bridge.status()).xruns },
                SettingsRow.info("audio-config", "Audio config") { MachineStatus(bridge.status()).audioConfig }),
            if (running) "Live values from the emulator, updated four times a second." else "Start a game to see live values."))
    }) {
        override fun tick() = refresh()
    }

    private fun effectiveControllerBindings(game: CatalogGame?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        if (game != null && !game.overriddenFields.contains("controller") && game.controllerDefaultBindings != null) {
            val defaults = org.json.JSONObject(game.controllerDefaultBindings)
            val bindings = defaults.optJSONArray(controllerProfiles.configuration.layout.key)
                ?: defaults.optJSONArray("withoutSticks")
            if (bindings != null) return bindingsCodec.parse(bindings.toString())
        }
        return if (game?.overriddenFields?.contains("controller") == true ||
            (configured != null && (configured != "[]" || game.controllerProfile == "custom-v1")))
            bindingsCodec.parse(configured)
        else controllerFlow.global()
    }

    private fun controllerMappingStatus(entry: E?): String? {
        if (controllerProfiles.configuration.layout != ControllerLayout.WITH_STICKS) return null
        val game = entry?.takeIf { it.contentId != null }?.let(catalog::resolve) ?: return null
        if (game.overriddenFields.contains("controller") || game.controllerProfile == "custom-v1") return null
        return if (game.controllerDefaultBindings != null &&
            org.json.JSONObject(game.controllerDefaultBindings).optJSONArray("withSticks") == null)
            "Using Without Sticks fallback" else null
    }

    protected fun releaseInputs(preserveAutomation: Boolean = false) = inputDispatch.releaseInputs(preserveAutomation)

    private fun releaseTouchInputs() {
        pendingMouseHold?.let(handler::removeCallbacks)
        pendingMouseHold = null
        pendingMouseRelease?.let(handler::removeCallbacks)
        pendingMouseRelease = null
        mouseRouter.release("touch")
        mouseTouchActive = false
        mouseDragging = false
        mouseMoved = false
    }

    private fun controllerAction(action: String) {
        if (sessionFlow.controllerAction(action, sessionState, ::applyPauseState)) return
        if (action == "fastForward") bridge.setFastForward(true)
    }

    private fun controllerActionReleased(action: String) {
        if (action == "fastForward") bridge.setFastForward(false)
    }

    /** A loaded state restores the lock keys it was saved with. */
    private fun loadMachineState(directory: File): Int = bridge.loadState(directory.absolutePath).also { result ->
        if (result == 0) {
            inputRouter.syncLocks(bridge.lockedKeys())
            runOnUiThread { stateLoaded() }
        }
    }

    private fun describeStateError(code: Int) =
        if (code == MachineBridge.NOT_RUNNING) "the machine is not running" else stateError(code)

    private fun exitApp() {
        releaseInputs()
        if (exiting) return
        exiting = true
        controllerProfiles.configuration.endSession()
        startGeneration++
        if (currentEntry != null) {
            sessionFlow.close()
            transitioning = true
            showProgress("Stopping machine…")
        }
        Thread {
            bridge.stop()
            runOnUiThread { finish() }
        }.start()
    }

    protected fun toast(message: String) = Ui.message(this, message)

    private fun showProgress(text: String) {
        progressText = text
        applyPauseState()
    }

    private fun touchStorage(direct: Boolean) = if (direct) "direct" else "touchpad"

    private fun setSecondaryInitialMode(touchpad: Boolean) {
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setInitialMode(touchpad)
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setInitialMode(touchpad)
    }

    private fun onSecondarySwapChanged(swapped: Boolean) {
        keyboardPanel.close()
        updateViewport()
        if (!swapped) {
            val holder = screen.holder
            if (holder.surface.isValid) bridge.setSurface(holder.surface)
        }
        applyPauseState()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped) bridge.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped) bridge.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped) bridge.setSurface(null)
    }

    override fun onPause() {
        if (!relocating && guestLifecycle.onPause()) secondaryKeyboard.stop()
        super.onPause()
    }

    override fun onStop() {
        if (!relocating && guestLifecycle.onStop()) secondaryKeyboard.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (relocating) return
        secondaryKeyboard.start(handler)
        guestLifecycle.onResume()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (relocating || rebuildIfRescaled()) return
        fullscreen.restore()
        graphics.load()
        root.post { updateViewport() }
        gameOrientation.configurationChanged()
    }

    /** Rebuilds views sized for earlier density or font settings once no machine is running or starting. */
    private fun rebuildIfRescaled(): Boolean {
        if (exiting || isFinishing || currentEntry != null || transitioning || !viewScale.changed) return false
        recreate()
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (relocating) return
        guestLifecycle.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        catalogUpdates.cancel()
        catalog.installedCatalogs.close()
        backCoordinator.unregister()
        startGeneration++
        libraryFlow.cancel()
        externalDispatcher.cancel()
        artworkDownloads.cancel()
        releaseInputs()
        secondaryKeyboard.stop()
        controllerDevices.unregister()
        handler.removeCallbacks(updateStatus)
        bridge.setSurface(null)
        if (!exiting) Thread { bridge.stop() }.start()
        super.onDestroy()
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (shaderOptions.handleActivityResult(requestCode, resultCode, data)) return
        if (libraryMenu.handleActivityResult(requestCode, resultCode, data)) return
        if (artwork.handleActivityResult(requestCode, resultCode, data)) return
        if (libraryFlow.handleActivityResult(requestCode, resultCode, data)) return
        if (saveFolder.handleActivityResult(requestCode, resultCode, data)) return
        handleProductResult(requestCode, resultCode, data)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        inputDispatch.dispatchKey(event) { super.dispatchKeyEvent(it) }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        inputDispatch.dispatchMotion(event) { super.dispatchGenericMotionEvent(it) }

    private fun dispatchGuestTouch(event: MotionEvent) = super.dispatchTouchEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!::firstRunSetup.isInitialized || !::sessionFlow.isInitialized || !::keyboardPanel.isInitialized)
            return super.dispatchTouchEvent(event)
        return edgeSwipes.dispatch(event, root.width, pages, !libraryVisible, menuOpen,
            { keyboardPanel.visibility != View.VISIBLE &&
                (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.isKeyboardVisible) },
            { onScreenControls.hitTest(event.x, event.y) },
            { super.dispatchTouchEvent(it) }, ::dispatchGuestTouch, { openMenu() }, ::showKeyboard, ::closeMenu)
    }

    @Deprecated("Legacy Back path; API 33+ also uses OnBackInvokedDispatcher")
    override fun onBackPressed() = handleBack()

    private fun handleBack() = backCoordinator.handle()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        inputDispatch.physicalKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        inputDispatch.physicalKey(event) || super.onKeyUp(keyCode, event)

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ROM_FOLDER_REQUEST = 99
        /** The first request code a product may use for its own pickers. */
        const val FIRST_PRODUCT_REQUEST = 200
    }
}
