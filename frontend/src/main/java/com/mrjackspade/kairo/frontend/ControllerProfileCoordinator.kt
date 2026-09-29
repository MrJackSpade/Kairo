package com.mrjackspade.kairo.frontend

/** Coordinates profile precedence, persistence, and the live mapper for either guest. */
class ControllerProfileCoordinator<Game : Any>(
    private val store: ControllerProfileStore,
    private val mapper: GamepadMapper,
    private val contentId: (Game) -> String?,
    private val gameBindings: (Game) -> List<ControllerBinding>,
    private val saveGame: (String, List<ControllerBinding>) -> Unit,
    private val resetGame: (String) -> Unit,
    private val activeGame: () -> Game?,
    private val reloadActiveGame: () -> Unit = {}
) {
    fun initialize() {
        mapper.physicalBindings = store.physical()
        mapper.bindings = store.global()
        mapper.deadZone = store.deadZone
    }

    fun global(): List<ControllerBinding> = store.global()

    fun physical(): List<PhysicalControllerBinding> = store.physical()

    fun savePhysical(bindings: List<PhysicalControllerBinding>) {
        store.savePhysical(bindings)
        mapper.physicalBindings = bindings
    }

    fun resetPhysical() {
        store.resetPhysical()
        mapper.physicalBindings = store.physical()
    }

    var deadZone: Float
        get() = mapper.deadZone
        set(value) {
            mapper.deadZone = value
            store.deadZone = mapper.deadZone
        }

    fun load(game: Game?): List<ControllerBinding> =
        if (game == null || contentId(game) == null) store.global() else gameBindings(game)

    fun save(game: Game?, bindings: List<ControllerBinding>) {
        val id = game?.let(contentId)
        if (id == null) store.saveGlobal(bindings) else saveGame(id, bindings)
        refresh(game)
    }

    fun reset(game: Game?) {
        val id = game?.let(contentId)
        if (id == null) store.resetGlobal() else resetGame(id)
        refresh(game)
    }

    fun refresh(editedGame: Game?) {
        // Editing another library entry must not replace the running game's mapping.
        val active = activeGame()
        if (editedGame != null && contentId(editedGame) != active?.let(contentId)) return
        reloadActiveGame()
        mapper.bindings = load(activeGame())
    }
}

/** Releases held guest input before switching between controller editing surfaces. */
class ControllerEditorFlow<Game : Any>(
    private val editor: ControllerEditor<Game>,
    private val contentId: (Game) -> String?,
    private val closeMenu: () -> Unit,
    private val releaseInputs: () -> Unit,
    private val hideKeyboard: () -> Unit,
    private val openOnScreenControls: () -> Unit,
    private val showMessage: (String) -> Unit
) {
    fun showScope(game: Game?) {
        if (game != null && contentId(game) == null) {
            showMessage("Hash this game before editing its controls")
            return
        }
        closeMenu()
        releaseInputs()
        hideKeyboard()
        editor.show(game)
    }

    fun showGame(game: Game) {
        if (contentId(game) == null) {
            showMessage("Hash this game before editing its controls")
            return
        }
        releaseInputs()
        editor.show(game)
    }

    fun showOnScreenControls() {
        closeMenu()
        releaseInputs()
        hideKeyboard()
        editor.close()
        openOnScreenControls()
    }
}
