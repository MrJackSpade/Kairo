package com.mrjackspade.kairo.frontend

/** Coordinates profile precedence, persistence, and the live mapper for either guest. */
class ControllerProfileCoordinator<Game : Any>(
    private val store: ControllerProfileStore,
    private val mapper: GamepadMapper,
    /** The game whose own mapping applies, or null when [Game] uses the global one. */
    private val key: (Game) -> GameKey?,
    /** What [Game] inherits: the global mapping with its catalog layer over it. */
    private val inherited: (Game) -> List<ControllerBinding>,
    /** The player's own layer for [Game]: what they changed, with explicit nulls for what they unbound. */
    private val playerLayer: (Game) -> List<ControllerBinding>,
    private val saveGame: (GameKey, List<ControllerBinding>) -> Unit,
    private val resetGame: (GameKey) -> Unit,
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
        if (game == null || key(game) == null) store.global() else gameBindings(game)

    /** What [game] plays with: the layers it inherits with the player's own over them. */
    fun gameBindings(game: Game): List<ControllerBinding> = ControllerDefaults.cascade(inherited(game), playerLayer(game))

    /** Saves [bindings] as the player's layer: what differs from what the mapping inherits. */
    fun save(game: Game?, bindings: List<ControllerBinding>) {
        val id = game?.let(key)
        if (id == null) store.saveGlobal(bindings) else saveGame(id, ControllerDefaults.layerOver(inherited(game), bindings))
        refresh(game)
    }

    /** The running game's mapping, or the global one in the library. */
    fun activeBindings(): List<ControllerBinding> = load(activeGame())

    fun saveActive(bindings: List<ControllerBinding>) = save(activeGame(), bindings)

    fun reset(game: Game?) {
        val id = game?.let(key)
        if (id == null) store.resetGlobal() else resetGame(id)
        refresh(game)
    }

    fun refresh(editedGame: Game?) {
        // Editing another library entry must not replace the running game's mapping.
        val active = activeGame()
        if (editedGame != null && key(editedGame) != active?.let(key)) return
        reloadActiveGame()
        mapper.bindings = load(activeGame())
    }
}
