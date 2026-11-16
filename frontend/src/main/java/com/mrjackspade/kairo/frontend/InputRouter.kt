package com.mrjackspade.kairo.frontend

/**
 * One guest key can be held by several independent input sources. [lockCodes] are keys the
 * guest toggles on each press, such as Caps Lock; the router tracks their state for every source.
 * [sendAfterFrames] sends a key change once the guest has run that many more frames, after those
 * sent before it, and [dropDeferred] drops the ones not yet due (MachineBridge.keyAfterFrames).
 */
class InputRouter(private val send: (Int, Boolean) -> Unit,
                  private val sendAfterFrames: (Int, Boolean, Int) -> Unit,
                  private val dropDeferred: () -> Unit,
                  maxKeyCode: Int = 127,
                  private var lockCodes: Set<Int> = emptySet()) {
    init { require(maxKeyCode in 0..65535) }
    private val owners = LinkedHashMap<String, List<Int>>()
    /** Owners whose presses were sent after frames, with those frames: their release is never sooner. */
    private val heldAfter = HashMap<String, Int>()
    private val counts = IntArray(maxKeyCode + 1)
    private val listeners = LinkedHashSet<() -> Unit>()
    private val locks = HashSet<Int>()

    @Synchronized fun addListener(listener: () -> Unit) { listeners.add(listener) }
    @Synchronized fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    @Synchronized fun pressedKeys(): Set<Int> = counts.indices.filterTo(mutableSetOf()) {
        counts[it] > 0
    }

    /** Each source's held keys, as it holds them together: a chord's Shift ([holdShifted]) with its key. */
    @Synchronized fun holds(): List<List<Int>> {
        val together = LinkedHashMap<String, MutableList<Int>>()
        for ((owner, scans) in owners) {
            val chord = owner.removeSuffix(SHIFT_OWNER_SUFFIX)
            val source = if (chord != owner && owners.containsKey(chord)) chord else owner
            together.getOrPut(source) { mutableListOf() }.addAll(scans)
        }
        return together.values.map { it.toList() }
    }

    /** The sources whose name starts with [prefix] holding any of [codes], with what each holds. */
    @Synchronized fun holding(prefix: String, codes: Set<Int>): List<Pair<String, List<Int>>> =
        owners.filter { (owner, scans) -> owner.startsWith(prefix) && scans.any { it in codes } }
            .map { (owner, scans) -> owner to scans.toList() }

    /**
     * [owner] holds [scans]. With [afterFrames], the guest gets the presses once it has run that
     * many more frames, as a key its keyboard scan must see apart from a Shift change (Kairo#136).
     */
    @Synchronized fun hold(owner: String, scans: List<Int>, afterFrames: Int = 0) {
        require(owner.isNotBlank() && scans.isNotEmpty() && scans.size <= 8)
        require(scans.distinct().size == scans.size && scans.all { it in counts.indices })
        require(afterFrames >= 0)
        if (owners[owner] == scans) return
        release(owner)
        owners[owner] = scans.toList()
        if (afterFrames > 0) heldAfter[owner] = afterFrames
        for (scan in scans) {
            if (counts[scan]++ == 0) {
                emit(scan, true, afterFrames)
                if (scan in lockCodes && !locks.remove(scan)) locks.add(scan)
            }
        }
        listeners.toList().forEach { it() }
    }

    /** Lock keys the guest currently has on, as its presses toggled them. */
    @Synchronized fun lockedKeys(): Set<Int> = locks.toSet()

    /** One momentary press, which is how a lock key toggles. */
    @Synchronized fun tap(scan: Int) {
        hold("tap:$scan", listOf(scan))
        release("tap:$scan")
    }

    /** The lock keys of the keyboard in use. */
    @Synchronized fun lockCodes(): Set<Int> = lockCodes.toSet()

    /** Another keyboard's lock keys (KairoActivity.useKeyboard); a lock it lacks is off. */
    @Synchronized fun setLockCodes(codes: Set<Int>) {
        lockCodes = codes.toSet()
        if (locks.retainAll(lockCodes)) listeners.toList().forEach { it() }
    }

    /** A new or reset guest machine starts with every lock off. */
    @Synchronized fun resetLocks() = syncLocks(emptySet())

    /** The guest's own lock state, as after loading a saved machine. */
    @Synchronized fun syncLocks(locked: Set<Int>) {
        require(locked.all { it in lockCodes })
        if (locks == locked) return
        locks.clear()
        locks.addAll(locked)
        listeners.toList().forEach { it() }
    }

    /**
     * [owner] lets go; with [afterFrames], the guest gets the releases once it has run that many more
     * frames. An owner whose presses were sent after frames lets go no sooner than that, so its
     * release never reaches the guest before its press.
     */
    @Synchronized fun release(owner: String, afterFrames: Int = 0) {
        require(afterFrames >= 0)
        val scans = owners.remove(owner) ?: return
        val frames = maxOf(afterFrames, heldAfter.remove(owner) ?: 0)
        for (scan in scans.asReversed()) {
            if (--counts[scan] == 0) emit(scan, false, frames)
        }
        listeners.toList().forEach { it() }
    }

    /**
     * [owner] holds [scans]: the Shift keys among them ([shifts]) at once, and the others once the
     * guest has run [SHIFT_FRAMES] frames with that Shift. A guest that scans its keyboard a row at a
     * time sees a Shift and the key it shifts in scan order when both change in one frame, so the
     * X68000 took a chorded * as : (Kairo#136 for hardware keys, Kairo#191 for the on-screen
     * keyboard). Without a Shift, or with only Shifts, all are held at once. [releaseShifted] lets go.
     */
    @Synchronized fun holdShifted(owner: String, scans: List<Int>, shifts: Set<Int>) {
        val shift = scans.filter { it in shifts }
        if (shift.isEmpty() || shift.size == scans.size) {
            hold(owner, scans)
            return
        }
        hold(shiftOwner(owner), shift)
        hold(owner, scans - shift.toSet(), SHIFT_FRAMES)
    }

    /**
     * Lets go of what [holdShifted] held for [owner]: a chord's key and Shift stay down
     * [SYMBOL_HOLD_FRAMES] once the guest has the key, so even a tap shorter than a frame reaches
     * it whole; what was held at once goes at once.
     */
    @Synchronized fun releaseShifted(owner: String) {
        if (!owners.containsKey(shiftOwner(owner))) {
            release(owner)
            return
        }
        release(owner, SHIFT_FRAMES + SYMBOL_HOLD_FRAMES)
        release(shiftOwner(owner), SHIFT_FRAMES + SYMBOL_HOLD_FRAMES)
    }

    private fun shiftOwner(owner: String) = owner + SHIFT_OWNER_SUFFIX

    private fun emit(scan: Int, down: Boolean, afterFrames: Int) =
        if (afterFrames == 0) send(scan, down) else sendAfterFrames(scan, down, afterFrames)

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach { release(it) }
    }

    @Synchronized fun releaseAll(except: Set<String> = emptySet()) {
        // A key change still queued for later would press a key after its release; with none
        // queued, every release goes at once.
        dropDeferred()
        heldAfter.clear()
        owners.keys.filter { it !in except }.toList().forEach { release(it) }
    }

    companion object {
        /** Guest frames between a Shift change and the key after it, as the startup typer types (startup_typer.h). */
        const val SHIFT_FRAMES = 2
        /** Guest frames a symbol's key stays down at the least, as the startup typer holds a key. */
        const val SYMBOL_HOLD_FRAMES = 5
        /** A chord's Shift is held by its owner's name with this after it ([holdShifted]). */
        const val SHIFT_OWNER_SUFFIX = ":shift"
    }
}
