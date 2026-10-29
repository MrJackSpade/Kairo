package com.mrjackspade.kairo.frontend

/**
 * One guest key can be held by several independent input sources. [lockCodes] are keys the
 * guest toggles on each press, such as Caps Lock; the router tracks their state for every source.
 */
class InputRouter(private val send: (Int, Boolean) -> Unit, maxKeyCode: Int = 127,
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

    @Synchronized fun hold(owner: String, scans: List<Int>) {
        require(owner.isNotBlank() && scans.isNotEmpty() && scans.size <= 8)
        require(scans.distinct().size == scans.size && scans.all { it in counts.indices })
        if (owners[owner] == scans) return
        release(owner)
        owners[owner] = scans.toList()
        for (scan in scans) {
            if (counts[scan]++ == 0) {
                send(scan, true)
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

    @Synchronized fun release(owner: String) {
        val scans = owners.remove(owner) ?: return
        for (scan in scans.asReversed()) {
            if (--counts[scan] == 0) send(scan, false)
        }
        listeners.toList().forEach { it() }
    }

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach(::release)
    }

    @Synchronized fun releaseAll(except: Set<String> = emptySet()) {
        owners.keys.filter { it !in except }.toList().forEach(::release)
    }
}
