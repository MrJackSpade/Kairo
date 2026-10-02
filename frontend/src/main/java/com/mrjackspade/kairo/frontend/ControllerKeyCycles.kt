package com.mrjackspade.kairo.frontend

/** Supported previous/next pairs. Different pairs or sequences have independent state. */
object ControllerKeyCycles {
    val pairs = listOf(
        "virtual:l1" to "virtual:r1",
        "virtual:l2" to "virtual:r2",
        "virtual:left" to "virtual:right"
    )
    val inputs = pairs.flatMap { listOf(it.first, it.second) }.toSet()
    fun pair(input: String): Pair<String, String>? = pairs.firstOrNull {
        input == it.first || input == it.second
    }
}
