package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences
import java.lang.reflect.Proxy

/** An in-memory Android interface fake; exercises persistence without an emulator/device. */
internal fun memoryPreferences(initial: Map<String, Any> = emptyMap()): SharedPreferences {
    val values = initial.toMutableMap()
    return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when (method.name) {
            "contains" -> values.containsKey(args!![0])
            "getString", "getBoolean" -> values[args!![0]] ?: args[1]
            "edit" -> {
                val writes = mutableMapOf<String, Any?>()
                lateinit var editor: SharedPreferences.Editor
                editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { _, edit, arguments ->
                    when (edit.name) {
                        "putString", "putBoolean" -> { writes[arguments!![0] as String] = arguments[1]; editor }
                        "remove" -> { writes[arguments!![0] as String] = null; editor }
                        "apply", "commit" -> {
                            writes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                            if (edit.name == "commit") true else null
                        }
                        else -> error("Unexpected editor call ${edit.name}")
                    }
                } as SharedPreferences.Editor
                editor
            }
            else -> error("Unexpected preference call ${method.name}")
        }
    } as SharedPreferences
}
