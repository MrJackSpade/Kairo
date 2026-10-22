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
import android.view.Surface
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
 * The activity every Kairo app runs: the library and game page, the game session's screen,
 * flyout and pages, input from controllers, touch, keyboards and the second screen, and the
 * app's lifecycle. A product supplies its games, its catalog, and its guest machine through the
 * abstract members, and starts and stops its sessions; the frontend owns everything a player sees
 * and does around them. [MachineActivity] supplies the session of a frame-driven machine.
 */
abstract class KairoActivity<E : LibraryItem> : Activity(), SurfaceHolder.Callback {
    /** A product's names and words. */
    class Identity(
        /** The pixel-font product name, such as KAIRO88. */
        val brand: String,
        /** The color of the icon's wordmark after "Kairo", which titles draw the rest of [brand] in. */
        val brandAccent: Int,
        /** The app's name, such as Kairo88. */
        val appName: String,
        /** The machine's name, such as PC-88. */
        val guestName: String,
        /** The shared preferences file. */
        val preferences: String,
        val strings: LibraryStrings,
        /** The About page's credits, such as which emulator the app runs. */
        val credits: String,
        /** The library's empty and expired-folder messages. */
        val emptyStatus: String,
        val expiredStatus: String,
        /** First-run step 1. */
        val folderStep: MachineFirstRun.Folder,
        /** The save folder's section in Configurations, such as SAVE STATES. */
        val savesLabel: String,
        /** The app's GitHub repository, such as MrJackSpade/Kairo88, whose releases are offered as updates. */
        val repository: String
    )

    protected abstract val identity: Identity

    // The guest's input -------------------------------------------------------------------------

    protected abstract val keyboard: GuestKeyboardLayout
    /** The highest guest key code. */
    protected abstract val maxKeyCode: Int
    /** The guest key an Android key types, or null for none. */
    protected abstract fun guestKey(androidKey: Int): Int?
    protected abstract val controllerSpec: ControllerGuestSpec
    protected abstract val bindingsCodec: ControllerBindingsCodec
    protected abstract fun defaultBindings(layout: ControllerLayout): List<ControllerBinding>
    /** The guest's joystick controls, in the order [sendJoystick] numbers them. */
    protected open val joystickControls: List<String> get() = JoystickInputRouter.DEFAULT_CONTROLS
    protected abstract fun sendKey(code: Int, down: Boolean)
    protected abstract fun sendJoystick(control: Int, down: Boolean)
    protected abstract fun sendMouseMove(dx: Int, dy: Int)
    protected abstract fun sendMouseButton(button: Int, down: Boolean)
    /** The guest's keyboard and mouse reads for Auto touch mode, as [InputModeDecider.GuestInput.fromNative] takes them. */
    protected open fun inputTelemetry(): LongArray? = null
    /** The touch keyboard's height, and whether its touchpad tab drives the guest mouse. */
    protected open val keyboardHeightDp: Int = 260
    protected open val keyboardHasTouchpad: Boolean = false
    /** The frame size the keyboard's touchpad scales its movement to. */
    protected open fun touchpadReferenceSize(): Pair<Int, Int> = videoSize().let { it.first to it.second }
    /** Whether the screen stays on in the library as well as in a game. */
    protected open val keepScreenOnInLibrary: Boolean = true

    // The library and its catalog ---------------------------------------------------------------

    protected abstract val catalog: LibraryCatalog
    protected abstract fun cachedLibrary(tree: Uri): List<E>
    protected abstract fun scanLibrary(tree: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
                                       progress: (String) -> Unit): List<E>
    protected abstract fun storeLibrary(tree: Uri, entries: List<E>)
    protected abstract val hashCount: Int
    /** Whether the game folder must be writable, as for games that save into it. */
    protected open val writableFolder: Boolean = false
    /** Reads a file a frontend app sent, which need not be in the game folder. */
    protected abstract fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<E>
    /** The library loaded or a scan finished. */
    protected open fun libraryLoaded(entries: List<E>) {}
    /** A launch that opens the guest's command line instead of starting the game, when the product has one. */
    protected open val commandLaunch: ((E) -> Unit)? = null

    protected abstract val artworkStore: CatalogArtworkStore
    protected abstract fun artworkRecord(entry: E, kind: String): ArtworkCoordinator.Record
    protected abstract fun validArtworkPath(path: String): Boolean
    protected abstract fun saveArtworkOverride(game: GameKey, kind: String, path: String?)
    protected open fun validImageUrl(url: String): Boolean = false
    /** The artwork download controller, made once the library screen exists; null when the build has none. */
    protected abstract fun createArtworkDownloads(screen: LibraryScreen<E>, entries: () -> List<E>):
        CatalogArtworkDownloadController<E, *>?
    protected abstract fun downloadCatalogUpdate(task: CatalogUpdateTask): Boolean
    protected abstract val installedCatalogs: InstalledCatalogs
    /** A catalog update arrived; the library already redraws. */
    protected open fun catalogUpdated() {}
    protected abstract fun titleOf(entry: E): String

    // Controls a game has as its own -------------------------------------------------------------

    /** The bindings [entry] plays with: its own, the catalog's, or all games'. */
    protected abstract fun gameBindings(entry: E): List<ControllerBinding>
    protected abstract fun saveGameBindings(game: GameKey, bindings: List<ControllerBinding>)
    protected abstract fun clearGameBindings(game: GameKey)
    /** Where [entry]'s bindings come from, as a source tag. */
    protected abstract fun controllerSource(entry: E): String
    /** A note on the Button mapping page, such as a fallback the catalog chose. */
    protected open fun controllerMappingStatus(entry: E?): String? = null
    protected abstract val touchStore: TouchSettingsStore<E>
    /** What tapping the screen means in each mode, for the Screen tap choice. */
    protected abstract val touchModes: List<Pair<InputModeDecider.Mode, ChoiceOption>>
    protected abstract val directTouchExplanation: String
    /** Whether the guest mouse can take a touched point; without it, mouse touch is a touchpad. */
    protected open val directTouchAvailable: Boolean = false

    // Game settings -----------------------------------------------------------------------------

    /** Game settings › MACHINE for [entry]; null for none. */
    protected abstract fun machineGroup(entry: E, enabled: () -> Boolean): RowGroup?
    /** Whether the title is the user's, where the default comes from, and setting or clearing it. */
    protected abstract fun titleCustom(entry: E): Boolean
    protected abstract fun titleSource(entry: E): String?
    protected abstract fun setTitle(game: GameKey, title: String?)
    /** FILE rows besides the file's own, such as resetting a working copy. */
    protected open fun fileRows(entry: E): List<SettingsRow> = emptyList()
    /** What deleting [entry] removes, or null when it cannot be deleted from the library. */
    protected abstract fun deletePrompt(entry: E): GameDeletionFlow.Prompt?
    protected abstract fun deleteSource(entry: E)
    /** Resets every game setting of [game]. */
    protected abstract fun resetGameSettings(game: GameKey)
    protected open val resetMessage: String = "Game settings reset. Machine changes apply on next launch or restart."

    // Pages and the session ---------------------------------------------------------------------

    /** Settings › Machine. */
    protected abstract fun machinePage(): Page
    /** Graphics groups after the shared ones, such as an emulated video card. */
    protected open fun graphicsGroups(): List<RowGroup> = emptyList()
    /** Rows in the game flyout's GAME group, before Game settings. */
    protected open fun gameRows(): List<SettingsRow> = emptyList()
    /** The guest's state for the game flyout's status line: Running, Paused, Stopped and the like. */
    protected abstract fun sessionStatus(): String
    /** More of the flyout's status line after the state, such as the media in the machine. */
    protected open fun sessionDetails(): List<String> = emptyList()
    protected open fun diagnosticsRows(running: Boolean): List<SettingsRow> = emptyList()
    protected open val aboutHelp: List<Pair<String, String>> get() = listOf(
        "Open the menu" to "Mode or Home button, Back, Menu, or a swipe from the left edge",
        "Open the keyboard" to "Swipe in from the right edge",
        "Physical keyboard" to "Types into the ${identity.guestName} while the menu is closed")
    /** First-run step 2, or null for an app with no firmware to import. */
    protected abstract val firmware: MachineFirstRun.Firmware?
    /** Where first-run setup stopped: 0 before the folder, 1 at firmware, 2 done. */
    protected abstract var firstRunStep: Int
    /** The save folder's units, its game titles and unit labels, and any work before saves move. */
    protected abstract val saveUnits: Set<String>
    protected abstract val appSaves: File
    protected abstract fun saveUnitLabel(unit: String): String
    protected open fun prepareSaveMove(progress: (String) -> Unit) {}

    /**
     * Starts [entry]. The product shows its own pages first if it needs a choice, prepares the game
     * with [beginLaunch], and calls [enterSession] once the guest runs.
     */
    protected abstract fun launch(entry: E, fromFrontend: Boolean)
    /** Restart game in the flyout: closes the menu first. */
    protected abstract fun restartGame()
    /** Stops the session's guest off the UI thread; the shell clears its state after. */
    protected abstract fun stopGuest()
    protected abstract fun pauseGuest(paused: Boolean)
    /** The session ended, after [stopGuest]; forget what the product kept about it. */
    protected open fun sessionEnded() {}
    protected open fun pauseAudio() {}
    protected open fun resumeAudio() {}
    protected abstract fun setMuted(muted: Boolean)
    protected abstract fun setFastForward(enabled: Boolean)
    protected abstract fun setGuestSurface(surface: Surface?)
    protected abstract fun setShaderPreset(path: String?)
    /** Whether a new preset needs a new surface, as when the renderer cannot reuse the old one's EGL state. */
    protected open val shaderNeedsNewSurface: Boolean = false
    /** The guest's frame size and displayed width over height. */
    protected abstract fun videoSize(): Triple<Int, Int, Double>
    /** An activity result for one of the product's own requests; false when it is not one. */
    protected open fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false

    // ---------------------------------------------------------------------------------------

    protected val handler = Handler(Looper.getMainLooper())
    protected lateinit var pages: PageStack
        private set
    protected lateinit var overlay: GameOverlay
        private set
    protected val preferences by lazy { getSharedPreferences(identity.preferences, MODE_PRIVATE) }

    /** The game running, or null in the library. */
    protected var currentEntry: E? = null
        private set
    protected var sessionFromFrontend = false
        private set
    protected val libraryVisible: Boolean get() = sessionState.libraryVisible
    /** True while a session starts or stops. */
    @Volatile protected var transitioning = false
    protected val libraryEntries: List<E> get() = libraryFlow.entries
    protected val tree: Uri? get() = libraryFlow.tree
    protected val muted: Boolean get() = preferences.getBoolean("muted", false)
    /** Advances with each launch and each end of a session; work for an earlier one stops. */
    @Volatile protected var sessionGeneration = 0
        private set

    protected val inputRouter by lazy { InputRouter(::sendKey, maxKeyCode, keyboard.lockCodes) }
    protected val joystickRouter by lazy { JoystickInputRouter(::sendJoystick, joystickControls) }
    protected val mouseRouter by lazy { MouseInputRouter(::sendMouseMove, ::sendMouseButton) }
    protected val gamepadMapper by lazy {
        GamepadMapper(inputRouter, joystickRouter, mouseRouter, ::controllerAction, ::controllerActionReleased,
            bindingsCodec.parse(null))
    }
    protected val inputModeDecider = InputModeDecider()
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
            }, ::releaseTouchInputs, ::cancelAutomation, automationOwners)
    }
    /** Input owners of automation, such as typed startup commands, kept while a controller is released. */
    protected open val automationOwners: Set<String> = emptySet()
    protected open fun cancelAutomation() {}
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
        GuestLifecycleCoordinator(fullscreen::restore, { releaseInputs() }, ::applyPauseState, ::pauseAudio,
            { if (currentEntry != null) resumeAudio() },
            initiallyVisible = false,
            companionActive = { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isCompanionActive },
            companionHoldsStop = { ::secondaryKeyboard.isInitialized && secondaryKeyboard.holdsStoppedPrimary },
            beforeSuspend = ::cancelAutomation,
            resetGestures = edgeSwipes::reset,
            releaseOnFocusLoss = { releaseInputs(preserveAutomation = true) })
    }
    protected val saveFolder by lazy {
        SaveFolderSettings(this, pages, preferences, AppSaveLocation(appSaves), saveUnits,
            ::saveTitle, ::saveUnitLabel,
            { if (currentEntry != null) "Return to the library to change the save folder." else null },
            ::prepareSaveMove)
    }
    private val graphics by lazy { GraphicsOptions(this, preferences) { updateViewport(); refreshSettingValues() } }
    /** The shader settings; null in an app built without the shader runtime (BuildConfig.SHADERS). */
    private val shaderOptions by lazy {
        if (!BuildConfig.SHADERS) null
        else ShaderOptions(this, preferences, { if (::pages.isInitialized) pages else null },
            { path ->
                setShaderPreset(path)
                if (shaderNeedsNewSurface) recreateScreen()
                refreshSettingValues()
            }, ::toast)
    }
    protected val controllerProfiles by lazy {
        ControllerProfileStore(preferences, bindingsCodec::parse,
            { bindingsCodec.toJson(it).toString() }, ::defaultBindings)
    }
    protected val controllerFlow by lazy {
        ControllerProfileCoordinator(controllerProfiles, gamepadMapper,
            { entry: E -> entry.key.takeIf { entry.contentId != null } },
            ::gameBindings, ::saveGameBindings, ::clearGameBindings,
            { currentEntry }, ::controllerRefreshed)
    }
    /** A game's bindings changed through the controller pages. */
    protected open fun controllerRefreshed() {}
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
    protected val libraryScreen: LibraryScreen<E> get() = libraryFlow.screen
    private val touchUi by lazy {
        TouchUiCoordinator(this, libraryScreen, { !libraryVisible },
            { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible },
            ::updateViewport, ::applyPauseState,
            { if (::screen.isInitialized) screen.requestFocus() })
    }
    protected val touchSettingsCoordinator by lazy {
        TouchSettingsCoordinator(touchStore, touchModes, directTouchExplanation,
            changed = { entry ->
                if (entry == null || currentEntry?.id == entry.id) {
                    inputModeDecider.reset()
                    touchChanged()
                    if (entry != null) touchStore.secondaryTouchpad(entry)?.let {
                        setSecondaryInitialMode(it.custom() ?: it.fallback())
                    }
                }
            }, directTouchAvailable = directTouchAvailable)
    }
    /** The running game's touch settings changed. */
    protected open fun touchChanged() {}

    private lateinit var firstRunSetup: MachineFirstRun
    private val artwork by lazy {
        ArtworkCoordinator(this, pages, artworkStore, { entry: E -> entry.key }, ::artworkRecord, ::validArtworkPath,
            ::saveArtworkOverride, { id -> libraryScreen.metadataChanged(id) }, ::titleOf, ::toast, ::validImageUrl)
    }
    protected lateinit var controllerPages: ControllerPages<E>
        private set
    private val fullscreen by lazy { GameFullscreen(this) { !libraryVisible } }
    private val sessionState = SessionNavigationState()
    private val gameOrientation by lazy { GameOrientation(this, preferences) }
    private val sessionNavigation: SessionNavigationCoordinator by lazy {
        SessionNavigationCoordinator(sessionState, libraryScreen,
            { if (::sessionFlow.isInitialized) sessionFlow else null },
            { currentEntry != null }, { sessionFromFrontend }, ::exitApp, edgeSwipes::reset,
            {
                cancelAutomation()
                releaseInputs()
                hideKeyboard()
                swappedKeyboardPanel.close()
                if (!keepScreenOnInLibrary) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }, {
                secondaryKeyboard.setLibraryInfo(null)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }, libraryFlow::show, ::applyPauseState, { screen.requestFocus() }, ::endSessionForLibrary,
            gameOrientation)
    }
    private val backCoordinator: FrontendBackCoordinator by lazy {
        FrontendBackCoordinator(this, { if (::pages.isInitialized) pages else null }, libraryScreen,
            { if (!sessionNavigation.resumeGame()) finish() },
            { if (::keyboardPanel.isInitialized) keyboardPanel else null }, { openMenu() },
            { transitioning }, ::hideKeyboard)
    }
    private var artworkDownloads: CatalogArtworkDownloadController<E, *>? = null
    /** Offers a newer GitHub release over the library, never over first-run setup or a game. */
    private val appUpdates by lazy {
        AppUpdateCheck(this, identity, preferences, pages,
            { libraryVisible && activityVisible && !(::firstRunSetup.isInitialized && firstRunSetup.isOpen) },
            libraryScreen::showStatus)
    }
    private val catalogUpdates by lazy {
        CatalogUpdateController(this, ::downloadCatalogUpdate,
            { message -> if (!message.endsWith("…")) toast(message) },
            { libraryScreen.refreshCatalog(); catalogUpdated() },
            { if (libraryVisible) toast("Catalogs updated") }, { libraryMenu.showCatalogProgress(it) },
            installedCatalogs::update)
    }
    private var externalEntries: List<E> = emptyList()
    /** Whether the external file being opened came from a frontend app, rather than the player's pick in this app. */
    private var externalFromFrontend = true
    private val externalDispatcher by lazy {
        ExternalGameDispatcher(this, contentResolver, { libraryEntries },
            { entry: E -> Uri.parse(entry.uri) }, ::inspectExternal,
            { entries, inspected ->
                if (inspected) externalEntries = entries
                chooseExternalGame(entries)
            }, libraryScreen::showStatus, "${identity.appName}-external-game")
    }

    private lateinit var libraryMenu: LibraryMenu<E>
    private lateinit var sessionFlow: SessionFlow
    /** What the progress strip says while a session stops or starts. */
    private var progressText = ""
    /** What the progress strip says while a session's guest loads, or null once it runs. */
    private var sessionProgress: String? = null
    protected val menuOpen: Boolean get() = sessionFlow.isOpen
    protected var userPaused: Boolean
        get() = sessionState.userPaused
        set(value) { sessionState.userPaused = value }
    private val activityVisible: Boolean get() = guestLifecycle.isVisible
    private var exiting = false
    private var relocating = false
    private lateinit var viewScale: ViewScale
    private val edgeSwipes by lazy { EdgeSwipeNavigation(resources.displayMetrics.density) }
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

    private val updateStatus = object : Runnable {
        override fun run() {
            if (::pages.isInitialized && pages.isActive) pages.tick()
            if (currentEntry != null && !libraryVisible && !transitioning) inputTelemetry()?.let { values ->
                InputModeDecider.GuestInput.fromNative(values)?.let {
                    inputModeDecider.observe(it, android.os.SystemClock.elapsedRealtime())
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.brandAccent = identity.brandAccent
        if (RgDsDisplayRouter.routeToUpper(this)) {
            relocating = true
            return
        }
        graphics.load()
        setShaderPreset(shaderOptions?.selectedPath())
        setMuted(muted)
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
            { surface, _, _ -> setGuestSurface(surface) },
            { event, width, height -> handleScreenTouch(event, width, height) },
            ::onSecondarySwapChanged, { videoSize().third }, keyMapping)
        artwork.restoreInstanceState(savedInstanceState)
        controllerPages = ControllerPages(this, pages,
            controllerFlow::load, controllerFlow::save, controllerFlow::reset,
            { controllerProfiles.globalCustom },
            controllerFlow::physical, controllerFlow::savePhysical, controllerFlow::resetPhysical,
            { controllerProfiles.physicalCustom },
            { controllerFlow.deadZone }, { controllerFlow.deadZone = it },
            controllerSpec, { bindingsCodec.toJson(it) }, controllerProfiles.configuration,
            { releaseInputs(); controllerFlow.refresh(null) }, ::controllerMappingStatus, ::titleOf,
            onScreenControls, touchSettingsCoordinator) { releaseInputs() }
        val libraryPage = LibraryScreen(this, catalog, identity.strings, pages,
            ::chooseRomFolder, { preferences.getString("last_played_entry", null) },
            { launchFromLibrary(it, false) }, ::showDetailPreview, ::showGameDetails, ::showLibrarySelection,
            commandLaunch)
        libraryFlow = LibraryFlow(this, preferences, libraryPage, FOLDER_REQUEST,
            ::cachedLibrary, ::scanLibrary, identity.emptyStatus, identity.expiredStatus,
            { entries -> LibraryScanSummary.format(entries, hashCount) },
            ::libraryLoaded,
            { _ ->
                if (firstRunSetup.isChoosingRomFolder && firmware != null) advanceFirstRunFirmware(folderChosen = true)
                else if (firstRunSetup.isOpen) finishFirstRun()
                else libraryFlow.refresh(false)
            },
            { message -> if (firstRunSetup.isOpen) toast(message) }, writableFolder, ::storeLibrary)
        artworkDownloads = createArtworkDownloads(libraryPage) { libraryEntries }
        libraryMenu = LibraryMenu(this, pages, libraryFlow, catalog, identity.strings,
            { catalogUpdates.check(false) }, { artworkDownloads }, ::settingsRows, ::exitApp)
        libraryPage.openMenuAction = libraryMenu::open
        // Pages and the game overlay sit above the library; pages come forward when they open.
        root.addView(libraryScreen, root.indexOfChild(overlay), FrameLayout.LayoutParams(-1, -1))
        touchUi.bind(keyboardPanel)
        firstRunSetup = MachineFirstRun(pages, identity.brand, identity.folderStep,
            ::chooseRomFolder, ::skipFirstRunFolder, ::finishFirstRun, firmware)
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
        val externallyRequested = (savedInstanceState == null || pendingControllerExternalRequest ||
            savedInstanceState.getBoolean("controller_setup_external_v1")) &&
            ExternalGameIntent.hasRequest(intent)
        if (externallyRequested) dispatchExternalGame(intent)
        else if (tree != null && libraryFlow.hasGrant(tree!!) && firstRunStep >= 2) libraryFlow.refresh(false)
        if (firstRunStep < 2 && !externallyRequested) firstRunSetup.show(if (firstRunStep == 0 || firmware == null)
            MachineFirstRun.Step.ROM_FOLDER else MachineFirstRun.Step.FIRMWARE, tree != null)
        applyPauseState()
        catalogUpdates.check(true)
        appUpdates.start()
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
    private fun dispatchExternalGame(intent: Intent) {
        if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) firstRunSetup.close()
        libraryFlow.whenLoaded {
            externalFromFrontend = true
            externalDispatcher.dispatch(intent)
        }
    }

    /** Opens a file the player picked in this app as a game outside the library. */
    protected fun openExternalFile(uri: Uri) {
        externalFromFrontend = false
        externalDispatcher.dispatch(Intent(Intent.ACTION_VIEW, uri))
    }

    /** The game to start from a frontend's file: the product's preference, else the one playable entry. */
    protected open fun preferredExternal(playable: List<E>): E? = playable.singleOrNull()

    private fun chooseExternalGame(entries: List<E>) {
        val playable = entries.filter { it.playable }
        if (playable.isEmpty()) {
            libraryScreen.showStatus("No playable ${identity.guestName} game found")
            return
        }
        val fromFrontend = externalFromFrontend
        preferredExternal(playable)?.let { launchFromLibrary(it, fromFrontend); return }
        // Back cancels and leaves the app open.
        pages.push(ChoicePage(ChoiceSpec("Choose a disk to start", { null }, {
            listOf(ChoiceGroup(null, playable.map { ChoiceOption(it.id, it.displayName) }))
        }, { null }, { id ->
            playable.firstOrNull { it.id == id }?.let { launchFromLibrary(it, fromFrontend) }
        }, stay = true, cycles = false)))
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = newScreen()
        root.setOnTouchListener { _, event ->
            !libraryVisible && !menuOpen &&
                touchUi.handleKeyboardTouch(event,
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD)
        }
        val (width, height) = videoSize()
        root.addView(screen, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
        onScreenControls = OnScreenControls(this, root, gamepadMapper, preferences, ::applyPauseState,
            { controllerProfiles.configuration.layout })

        keyboardPanel = GuestKeyboardPanel(this, inputRouter, keyboard, ::hideKeyboard,
            mouse = if (keyboardHasTouchpad) mouseRouter else null,
            mouseReferenceSize = ::touchpadReferenceSize, mapping = keyMapping)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(keyboardHeightDp), Gravity.BOTTOM))
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
                cancelAutomation()
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

    private fun newScreen() = SurfaceView(this).apply {
        holder.addCallback(this@KairoActivity)
        isFocusableInTouchMode = true
        contentDescription = "${identity.guestName} display"
        setOnTouchListener { view, event -> handleScreenTouch(event, view.width, view.height) }
    }

    /** Replaces the game's surface with a new one in the same place. */
    private fun recreateScreen() {
        if (!::screen.isInitialized) return
        val old = screen
        val params = old.layoutParams
        setGuestSurface(null)
        old.holder.removeCallback(this)
        val index = root.indexOfChild(old)
        root.removeView(old)
        screen = newScreen()
        root.addView(screen, index, params)
        updateViewport()
    }

    /** One settings list, shown the same way in the game flyout and the library flyout. */
    private fun settingsRows() = SessionMenus.settingsRows(SessionMenus.Settings(
        graphics = { pages.push(graphics.page(shaderOptions, ::graphicsGroups)) },
        controls = {
            pages.push(controllerPages.controlsPage(currentEntry?.takeIf { !libraryVisible && it.contentId != null }))
        },
        machine = { pages.push(machinePage()) },
        configurations = { pages.push(SessionMenus.configurationsPage(gameOrientation, saveFolder, identity.savesLabel)) },
        soundOn = { !muted },
        setSound = { on -> if (on == muted) toggleMute() },
        about = { pages.push(aboutPage()) }))

    /** A save folder's game name, as a library title when the game is still in the library. */
    private fun saveTitle(game: String): String = libraryEntries.firstOrNull { it.key.storageName == game }
        ?.let(::titleOf) ?: game

    /** The game flyout: the game's own pages, settings, then leaving. */
    private fun gameFlyout() = SessionMenus.gameFlyout(identity.brand, identity.appName,
        { currentEntry?.let(::titleOf) ?: "No game" }, ::menuStatusText, sessionNavigation,
        ::gameRows, { currentEntry != null }, { currentEntry?.let(::showGameDetails) },
        ::settingsRows, restart = {
            cancelAutomation()
            releaseInputs()
            userPaused = false
            closeMenu()
            restartGame()
        }, exit = ::exitApp)

    /** The menu itself pauses the guest, so it reads Paused only when the player paused the game. */
    private fun menuStatusText(): String {
        val state = sessionStatus().let { if (it == "Paused" && !userPaused) "Running" else it }
        return (listOf(state) + sessionDetails() + listOfNotNull(if (muted) "Muted" else null)).joinToString(" · ")
    }

    protected fun refreshSettingValues() {
        if (::pages.isInitialized) pages.refresh()
    }

    private fun toggleMute() {
        preferences.edit().putBoolean("muted", !muted).apply()
        setMuted(muted)
        refreshSettingValues()
    }

    /** Sizes the game's surface for its frame, the screen and the keyboard. */
    protected fun updateViewport() {
        if (!::root.isInitialized || root.width <= 0 || root.height <= 0) return
        val keyboardHeight = if (::keyboardPanel.isInitialized &&
            keyboardPanel.visibility == View.VISIBLE) keyboardPanel.layoutParams.height else 0
        val (width, height, aspect) = videoSize()
        val viewport = graphics.viewport(root.width, root.height, keyboardHeight, width, height, aspect) ?: return
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

    protected fun applyPauseState() {
        if (relocating) return
        if (!libraryVisible && ::secondaryKeyboard.isInitialized) secondaryKeyboard.setLibraryInfo(null)
        val presentation = sessionState.presentation(activityVisible, menuOpen, false, transitioning)
        val showingGuest = presentation.showGuest
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setShown(
            ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped && showingGuest)
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setAppearance(showingGuest,
            if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) Ui.BG else Color.BLACK)
        if (currentEntry != null || transitioning) pauseGuest(presentation.pauseGuest)
        if (showingGuest) ImmersiveWindow.hideBars(this)
        if (::overlay.isInitialized) {
            val progress = if (transitioning) progressText else sessionProgress
            if (progress != null) overlay.showProgress(progress) else overlay.hideProgress()
            overlay.setPaused(userPaused && !libraryVisible && !menuOpen && !transitioning)
            if (libraryVisible && !keepsNotice()) overlay.hideNotice()
        }
        if (::libraryFlow.isInitialized) appUpdates.showIfReady()
        if (::onScreenControls.isInitialized) {
            if (::libraryFlow.isInitialized) touchUi.refreshControls(onScreenControls,
                showingGuest, ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped)
            else onScreenControls.refreshVisibility(false)
        }
    }

    private fun chooseRomFolder() = libraryFlow.chooseFolder()

    private fun skipFirstRunFolder(folderChosen: Boolean) {
        if (firmware == null) finishFirstRun() else advanceFirstRunFirmware(folderChosen)
    }

    private fun advanceFirstRunFirmware(folderChosen: Boolean) {
        firstRunStep = 1
        firstRunSetup.show(MachineFirstRun.Step.FIRMWARE, folderChosen)
    }

    private fun finishFirstRun() {
        firstRunStep = 2
        firstRunSetup.close()
        applyPauseState()
        val folder = tree
        if (folder != null && libraryFlow.hasGrant(folder)) libraryFlow.refresh(false)
        else libraryScreen.showStatus("Choose a ${identity.strings.folder} from the library menu when you’re ready")
    }

    /** The first-run firmware page shows what an import changed. */
    protected fun firmwareChanged() {
        if (::firstRunSetup.isInitialized) firstRunSetup.refreshFirmware()
        refreshSettingValues()
    }

    protected fun refreshLibrary(forceHash: Boolean) = libraryFlow.refresh(forceHash)

    /** Edits the library list without a scan, as after a launch installs a game. */
    protected fun editLibrary(change: (List<E>) -> List<E>) = libraryFlow.edit(change)
    protected fun removeLibrarySource(entry: E) = libraryFlow.removeSource(entry)

    private fun launchFromLibrary(entry: E, fromFrontend: Boolean) {
        cancelAutomation()
        releaseInputs()
        if (saveFolder.busy) {
            toast("Wait for the saves to finish moving.")
            return
        }
        if (!entry.playable) {
            toast(entry.error ?: "Refresh this entry before playing")
            return
        }
        if (entry !in externalEntries && (tree == null || !libraryFlow.hasGrant(tree!!))) {
            libraryScreen.showStatus(identity.expiredStatus)
            return
        }
        launch(entry, fromFrontend)
    }

    /** Starts a launch from [launch]: closes pages and shows [message] while the session prepares. */
    protected fun beginLaunch(message: String): Int {
        val generation = ++sessionGeneration
        if (::pages.isInitialized) pages.closeAll()
        transitioning = true
        showProgress(message)
        gameOrientation.launching()
        return generation
    }

    /** Whether [generation] is still the session being prepared or played. */
    protected fun current(generation: Int) = generation == sessionGeneration

    protected fun showProgress(text: String) {
        progressText = text
        applyPauseState()
    }

    /**
     * The session of [entry] begins, with the game's controls. With [loading], the game's screen
     * shows that progress until [sessionRunning]; otherwise the guest already runs.
     */
    protected fun enterSession(entry: E, fromFrontend: Boolean, loading: String? = null) {
        transitioning = false
        sessionProgress = loading
        controllerProfiles.configuration.endSession()
        controllerProfiles.configuration.beginSession()
        currentEntry = entry
        sessionFromFrontend = fromFrontend
        preferences.edit().putString("last_played_entry", entry.id).apply()
        touchStore.secondaryTouchpad(entry)?.let { setSecondaryInitialMode(it.custom() ?: it.fallback()) }
        inputModeDecider.reset()
        gamepadMapper.bindings = gameBindings(entry)
        userPaused = false
        sessionFlow.reset()
        sessionNavigation.enterGame(notify = false)
        if (loading == null) gameOrientation.launched()
        applyPauseState()
    }

    /** A session that entered loading shows what it does next. */
    protected fun showSessionProgress(text: String) {
        if (currentEntry == null) return
        sessionProgress = text
        applyPauseState()
    }

    /** The loading guest runs. */
    protected fun sessionRunning() {
        if (sessionProgress == null) return
        sessionProgress = null
        gameOrientation.launched()
        applyPauseState()
    }

    /** A launch that did not start; [message] says why. */
    protected fun launchFailed(message: String) {
        transitioning = false
        gameOrientation.launchFailed()
        libraryScreen.showStatus("Launch failed: $message")
        applyPauseState()
    }

    /** The session's game is now [entry], as when an installer's game replaces it. */
    protected fun replaceSessionEntry(entry: E) {
        if (currentEntry != null) currentEntry = entry
    }

    private fun endSessionForLibrary(done: () -> Unit) {
        endSession { done(); rebuildIfRescaled() }
    }

    /** Ends the session: stops the guest off the UI thread, then clears it. */
    protected fun endSession(done: () -> Unit) {
        sessionGeneration++
        cancelAutomation()
        transitioning = true
        releaseInputs()
        hideKeyboard()
        showProgress("Stopping machine…")
        Thread {
            stopGuest()
            runOnUiThread {
                clearSession()
                done()
            }
        }.apply { name = "${identity.appName}-end-session"; start() }
    }

    /** Clears the session after its guest stopped on its own, as when a DOS game quits. */
    protected fun clearSession() {
        currentEntry = null
        sessionFromFrontend = false
        sessionProgress = null
        userPaused = false
        sessionEnded()
        controllerProfiles.configuration.endSession()
        gamepadMapper.bindings = controllerFlow.global()
        onScreenControls.refreshVisibility(false)
        sessionFlow.reset()
        transitioning = false
        applyPauseState()
    }

    /**
     * The guest stopped by itself: the frontend that started a game that [completed] gets it back,
     * and otherwise the library returns.
     */
    protected fun guestFinished(completed: Boolean) {
        sessionGeneration++
        val toFrontend = completed && sessionFromFrontend
        clearSession()
        if (!toFrontend || !finishApp()) sessionNavigation.showLibrary()
    }

    /** Closes the app, or returns false when the product holds it open for now. */
    protected open fun finishApp(): Boolean {
        finish()
        return true
    }

    /** How a touch on [entry]'s screen works: the game's own choices over the catalog's and all games'. */
    protected fun touchSelection(entry: E? = currentEntry): TouchInputSelection {
        val global = touchStore.global()
        if (entry == null || entry.contentId == null)
            return global.copy(directTouch = directTouchAvailable && global.directTouch)
        val mode = touchStore.mode(entry)
        val direct = touchStore.directTouch(entry)
        return TouchInputSelection(mode.custom() ?: mode.fallback(),
            directTouchAvailable && (direct.custom() ?: direct.fallback()))
    }

    private fun configuredInputMode(): InputModeDecider.Mode = touchSelection().mode

    protected fun showKeyboard() { touchUi.showKeyboard() }

    protected fun hideKeyboard() { touchUi.hideKeyboard() }

    /**
     * A touch on the game's screen in Mouse mode. The default is a touchpad: drag moves the
     * mouse, a tap clicks, and a hold drags. A product with its own gestures overrides it.
     */
    protected open fun handleMouseTouch(event: MotionEvent, width: Int, height: Int) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingMouseRelease?.let(handler::removeCallbacks)
                pendingMouseRelease = null
                releaseMouseTouch()
                mouseRouter.release("touch")
                mouseDragging = false
                mouseMoved = false
                mouseTouchStartX = event.x
                mouseTouchStartY = event.y
                mouseTouchLastX = event.x
                mouseTouchLastY = event.y
                mouseFractionX = 0f
                mouseFractionY = 0f
                if (touchSelection().directTouch && width > 0 && height > 0) {
                    val (frameWidth, frameHeight) = videoSize()
                    warpMouse((event.x * frameWidth / width).toInt().coerceIn(0, frameWidth - 1),
                        (event.y * frameHeight / height).toInt().coerceIn(0, frameHeight - 1))
                }
                val hold = Runnable {
                    if (mouseTouchActive && !mouseMoved) {
                        mouseDragging = true
                        afterMouseWarp { mouseRouter.hold("touch", "leftButton") }
                    }
                    pendingMouseHold = null
                }
                pendingMouseHold = hold
                handler.postDelayed(hold, 500)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mouseMoved && (abs(event.x - mouseTouchStartX) > dp(12) ||
                        abs(event.y - mouseTouchStartY) > dp(12))) {
                    mouseMoved = true
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                }
                moveGuestMouse(event, width, height)
            }
            MotionEvent.ACTION_UP -> {
                pendingMouseHold?.let(handler::removeCallbacks)
                pendingMouseHold = null
                moveGuestMouse(event, width, height)
                if (mouseDragging) mouseRouter.release("touch")
                else if (!mouseMoved) afterMouseWarp {
                    mouseRouter.hold("touch", "leftButton")
                    val release = Runnable {
                        mouseRouter.release("touch")
                        pendingMouseRelease = null
                    }
                    pendingMouseRelease = release
                    handler.postDelayed(release, 700)
                }
                mouseDragging = false
                mouseMoved = false
            }
            MotionEvent.ACTION_CANCEL -> {
                pendingMouseHold?.let(handler::removeCallbacks)
                pendingMouseHold = null
                releaseMouseTouch()
                mouseRouter.release("touch")
                mouseDragging = false
                mouseMoved = false
            }
        }
    }

    /** Moves the guest mouse to a frame point for a direct tap; only with [directTouchAvailable]. */
    protected open fun warpMouse(x: Int, y: Int) {
        error("${identity.appName} has no direct mouse touch")
    }

    /** Runs a click once a direct-tap warp has reached the guest; touchpad clicks run now. */
    protected open fun afterMouseWarp(action: () -> Unit) = action()

    private fun moveGuestMouse(event: MotionEvent, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val (frameWidth, frameHeight) = videoSize()
        mouseFractionX += (event.x - mouseTouchLastX) * frameWidth / width
        mouseFractionY += (event.y - mouseTouchLastY) * frameHeight / height
        mouseTouchLastX = event.x
        mouseTouchLastY = event.y
        val dx = mouseFractionX.toInt().coerceIn(-frameWidth, frameWidth)
        val dy = mouseFractionY.toInt().coerceIn(-frameHeight, frameHeight)
        mouseFractionX -= dx
        mouseFractionY -= dy
        if (dx != 0 || dy != 0) sendMouseMove(dx, dy)
    }

    /** A screen touch drives the guest mouse in Mouse mode, and opens the keyboard otherwise. */
    private fun handleScreenTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (libraryVisible || menuOpen) return false
        if (touchUi.handleKeyboardTouch(event,
                inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD))
            return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // With the keyboard on the second screen, the main screen touch is always the mouse.
            mouseTouchActive = (::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible) ||
                inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.MOUSE
        }
        if (mouseTouchActive) {
            handleMouseTouch(event, width, height)
            if (event.actionMasked == MotionEvent.ACTION_UP) hideKeyboard()
        } else if (event.actionMasked == MotionEvent.ACTION_UP) showKeyboard()
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            mouseTouchActive = false
        return true
    }

    /** Game settings over the game page, or over the game flyout while the game runs. */
    protected fun showGameDetails(entry: E) {
        pages.push(gameSettingsPage({ titleOf(entry) }) { gameSettings(entry) })
    }

    private fun gameSettings(entry: E): List<RowGroup> {
        // Settings belong to the game's key, once its files have been read.
        val id = entry.key.takeIf { entry.contentId != null }
        val hashed = { id != null }
        val noHash = { GameSettingsRows.NO_HASH }
        val scope = { titleOf(entry) }
        val inLibrary = libraryEntries.any { it.uri == entry.uri }
        return listOfNotNull(
            RowGroup("CONTROLS", listOf(controllerPages.gameRow(entry, hashed, { controllerSource(entry) })) +
                (id?.let { touchSettingsCoordinator.gameRows(entry, scope, hashed) } ?: listOf(
                    SettingsRow.choice("game-tap", "Screen tap", ChoiceSpec("Screen tap", scope, { emptyList() },
                        { null }, select = {}), enabled = { false }, disabledReason = noHash)))),
            machineGroup(entry, hashed),
            RowGroup("LIBRARY", listOf(
                GameSettingsRows.text("title", "Title", "Game title", { titleOf(entry) }, { titleCustom(entry) },
                    { titleSource(entry) },
                    { value -> saveGameSetting(entry, "Saved") { setTitle(id!!, value) } },
                    { saveGameSetting(entry, "Saved") { setTitle(id!!, null) } },
                    enabled = hashed, disabledReason = noHash),
                artwork.row(entry, "boxArt", hashed),
                artwork.row(entry, "preview", hashed)),
                "Chosen images are copied into your library."),
            RowGroup("FILE", GameSettingsRows.file(entry.path, entry.zipEntry, entry.contentId, entry.error) +
                fileRows(entry) + listOfNotNull(deletePrompt(entry)?.takeIf { inLibrary }?.let { prompt ->
                    GameDeletionFlow.row(this, libraryFlow, entry, prompt, identity.strings.folder,
                        { !libraryVisible && currentEntry?.uri == entry.uri }, "${identity.appName}-delete-game") {
                        deleteSource(entry)
                    }
                })),
            RowGroup(null, listOf(GameSettingsRows.reset(hashed) {
                saveGameSetting(entry, resetMessage, "Could not reset game settings") {
                    resetGameSettings(id!!)
                    if (currentEntry?.id == entry.id) {
                        touchStore.secondaryTouchpad(entry)?.let { setSecondaryInitialMode(it.custom() ?: it.fallback()) }
                        gamepadMapper.bindings = gameBindings(entry)
                        inputModeDecider.reset()
                        touchChanged()
                    }
                }
            })))
    }

    /** Runs a game setting change and says how it went; the library and pages show the new value. */
    protected fun saveGameSetting(entry: E, message: String = "Saved. Machine changes apply on next launch or restart.",
                                  failure: String? = null, action: () -> Unit) {
        try {
            action()
            gameSettingChanged(entry)
            libraryScreen.metadataChanged(entry.key)
            toast(message)
        } catch (error: Exception) {
            toast(failure ?: "Could not save: ${error.message ?: "Invalid value"}")
        }
        pages.refresh()
    }

    /** A setting of [entry] changed; the product rereads what it keeps of it. */
    protected open fun gameSettingChanged(entry: E) {}

    private fun showDetailPreview(entry: E) = artwork.view(entry)

    private fun aboutPage() = AboutDocuments.page(this, pages, aboutHelp, "Android reserves the system Home key.",
        identity.credits, "PRIVACY_POLICY.txt", "THIRD_PARTY_NOTICES.txt", ::diagnosticsPage)

    /** Live values, updated four times a second. */
    private fun diagnosticsPage() = object : GroupsPage("Diagnostics", { currentEntry?.let(::titleOf) }, {
        val running = currentEntry != null
        listOf(RowGroup("MACHINE STATUS", diagnosticsRows(running),
            if (running) "Live values from the emulator, updated four times a second." else "Start a game to see live values."))
    }) {
        override fun tick() = refresh()
    }

    protected fun releaseInputs(preserveAutomation: Boolean = false) = inputDispatch.releaseInputs(preserveAutomation)

    private fun releaseTouchInputs() {
        pendingMouseHold?.let(handler::removeCallbacks)
        pendingMouseHold = null
        pendingMouseRelease?.let(handler::removeCallbacks)
        pendingMouseRelease = null
        mouseRouter.releasePrefix("touch")
        releaseMouseTouch()
        mouseTouchActive = false
        mouseDragging = false
        mouseMoved = false
    }

    /** Releases a product's own touch gestures, such as an absolute pointer. */
    protected open fun releaseMouseTouch() {}

    private fun controllerAction(action: String) {
        if (sessionFlow.controllerAction(action, sessionState, ::applyPauseState)) return
        if (action == "fastForward") setFastForward(true)
    }

    private fun controllerActionReleased(action: String) {
        if (action == "fastForward") setFastForward(false)
    }

    protected fun exitApp() {
        cancelAutomation()
        releaseInputs()
        if (exiting) return
        exiting = true
        controllerProfiles.configuration.endSession()
        sessionGeneration++
        if (currentEntry != null) {
            sessionFlow.close()
            transitioning = true
            showProgress("Stopping machine…")
        }
        Thread {
            stopGuest()
            runOnUiThread {
                if (finishApp()) return@runOnUiThread
                exiting = false
                clearSession()
                sessionNavigation.showLibrary()
            }
        }.start()
    }

    protected fun toast(message: String, long: Boolean = false) = Ui.message(this, message, long)

    protected fun setSecondaryInitialMode(touchpad: Boolean) {
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setInitialMode(touchpad)
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setInitialMode(touchpad)
    }

    /** The surface the guest draws to, for a save state's thumbnail. */
    protected val activeGameSurface: SurfaceView
        get() = (if (::secondaryKeyboard.isInitialized) secondaryKeyboard.activeGameSurface else null) ?: screen

    private fun onSecondarySwapChanged(swapped: Boolean) {
        keyboardPanel.close()
        updateViewport()
        if (!swapped) {
            val holder = screen.holder
            if (holder.surface.isValid) setGuestSurface(holder.surface)
        }
        applyPauseState()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (holder === screen.holder && (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped))
            setGuestSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (holder === screen.holder && (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped))
            setGuestSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (holder === screen.holder && (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped))
            setGuestSurface(null)
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

    /** Rebuilds views sized for earlier density or font settings once no session is running or starting. */
    protected fun rebuildIfRescaled(): Boolean {
        if (exiting || isFinishing || currentEntry != null || transitioning || !holdsRebuild() ||
            !viewScale.changed) return false
        recreate()
        return true
    }

    /** Whether the notice on screen outlives its game into the library. */
    protected open fun keepsNotice(): Boolean = false

    /** False while the product needs the views to stay, as for a prompt that outlives its game. */
    protected open fun holdsRebuild(): Boolean = true

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (relocating) return
        guestLifecycle.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        catalogUpdates.cancel()
        installedCatalogs.close()
        backCoordinator.unregister()
        sessionGeneration++
        libraryFlow.cancel()
        externalDispatcher.cancel()
        artworkDownloads?.cancel()
        cancelAutomation()
        releaseInputs()
        secondaryKeyboard.stop()
        controllerDevices.unregister()
        handler.removeCallbacks(updateStatus)
        setGuestSurface(null)
        if (!exiting) Thread { stopGuest() }.start()
        super.onDestroy()
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (shaderOptions?.handleActivityResult(requestCode, resultCode, data) == true) return
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
        private const val FOLDER_REQUEST = 99
        /** The first request code a product may use for its own pickers. */
        const val FIRST_PRODUCT_REQUEST = 200
    }
}
