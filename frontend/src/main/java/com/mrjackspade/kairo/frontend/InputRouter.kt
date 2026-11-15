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
    private val counts = IntArray(maxKeyCode + 1)
    private val listeners = LinkedHashSet<() -> Unit>()
    private val locks = HashSet<Int>()

    @Synchronized fun addListener(listener: () -> Unit) { listeners.add(listener) }
    @Synchronized fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    @Synchronized fun pressedKeys(): Set<Int> = counts.indices.filterTo(mutableSetOf()) {
        counts[it] > 0
    }

    /** Each source's held keys, as it holds them together. */
    @Synchronized fun holds(): List<List<Int>> = owners.values.map { it.toList() }

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

    /** [owner] lets go; with [afterFrames], the guest gets the releases once it has run that many more frames. */
    @Synchronized fun release(owner: String, afterFrames: Int = 0) {
        require(afterFrames >= 0)
        val scans = owners.remove(owner) ?: return
        for (scan in scans.asReversed()) {
            if (--counts[scan] == 0) emit(scan, false, afterFrames)
        }
        listeners.toList().forEach { it() }
    }

    private fun emit(scan: Int, down: Boolean, afterFrames: Int) =
        if (afterFrames == 0) send(scan, down) else sendAfterFrames(scan, down, afterFrames)

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach { release(it) }
    }

    @Synchronized fun releaseAll(except: Set<String> = emptySet()) {
        // A key change still queued for later would press a key after its release.
        dropDeferred()
        owners.keys.filter { it !in except }.toList().forEach { release(it) }
    }
}
