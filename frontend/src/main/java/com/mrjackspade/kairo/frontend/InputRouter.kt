package com.mrjackspade.kairo.frontend

/** One guest key can be held by several independent input sources. */
class InputRouter(private val send: (Int, Boolean) -> Unit, maxKeyCode: Int = 127) {
    init { require(maxKeyCode in 0..65535) }
    private val owners = LinkedHashMap<String, List<Int>>()
    private val counts = IntArray(maxKeyCode + 1)
    private val listeners = LinkedHashSet<() -> Unit>()

    @Synchronized fun addListener(listener: () -> Unit) { listeners.add(listener) }
    @Synchronized fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    @Synchronized fun pressedScans(): Set<Int> = counts.indices.filterTo(mutableSetOf()) {
        counts[it] > 0
    }

    @Synchronized fun hold(owner: String, scans: List<Int>) {
        require(owner.isNotBlank() && scans.isNotEmpty() && scans.size <= 8)
        require(scans.distinct().size == scans.size && scans.all { it in counts.indices })
        if (owners[owner] == scans) return
        release(owner)
        owners[owner] = scans.toList()
        for (scan in scans) {
            if (counts[scan]++ == 0) send(scan, true)
        }
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
