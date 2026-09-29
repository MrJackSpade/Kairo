package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.json.JSONArray

/** Validated field and subfield precedence for shipped, updated, and local catalog records. */
object CatalogFieldLayers {
    /** The last explicit boolean in catalog priority order controls visibility. */
    fun hidden(vararg values: Any?): Boolean =
        values.filterIsInstance<Boolean>().lastOrNull() ?: false

    data class Source(val name: String, val record: JSONObject?)
    data class Result(val record: JSONObject, private val origins: Map<List<String>, String>,
                      private val priorities: Map<List<String>, Int>) {
        fun sourceOf(vararg path: String): String? = origins[path.toList()]
        fun sourceIndexOf(vararg path: String): Int? = priorities[path.toList()]
    }

    /** The same field policy used by merge can reject a bad incoming record. */
    fun invalidPath(record: JSONObject, objectField: (List<String>) -> Boolean,
                    validValue: (List<String>, Any) -> Boolean): List<String>? {
        fun inspect(source: JSONObject, path: List<String>): List<String>? {
            for (key in source.keys()) {
                val nextPath = path + key
                val value = source.opt(key) ?: return nextPath
                val invalid = if (value is JSONObject && objectField(nextPath))
                    inspect(value, nextPath) else if (validValue(nextPath, value)) null
                    else nextPath
                if (invalid != null) return invalid
            }
            return null
        }
        return inspect(record, emptyList())
    }

    fun merge(sources: List<Source>, objectField: (List<String>) -> Boolean,
              validValue: (List<String>, Any) -> Boolean,
              validObject: (List<String>, JSONObject) -> Boolean = { _, _ -> true },
              replacedKeys: (List<String>, JSONObject) -> Set<String> = { _, _ -> emptySet() }): Result {
        val merged = JSONObject()
        val origins = HashMap<List<String>, String>()
        val priorities = HashMap<List<String>, Int>()
        fun apply(target: JSONObject, source: JSONObject, path: List<String>, name: String, index: Int) {
            for (key in source.keys()) {
                val nextPath = path + key
                val value = source.opt(key) ?: continue
                if (value is JSONObject && objectField(nextPath)) {
                    if (!validObject(nextPath, value)) continue
                    val child = target.optJSONObject(key) ?: JSONObject()
                    for (removed in replacedKeys(nextPath, value)) {
                        child.remove(removed)
                        val removedPath = nextPath + removed
                        origins.keys.removeAll { it.take(removedPath.size) == removedPath }
                        priorities.keys.removeAll { it.take(removedPath.size) == removedPath }
                    }
                    apply(child, value, nextPath, name, index)
                    if (child.length() > 0) {
                        target.put(key, child)
                        if (value.length() == 0 || priorities.any { (path, priority) ->
                                path.size > nextPath.size && path.take(nextPath.size) == nextPath && priority == index }) {
                            origins[nextPath] = name
                            priorities[nextPath] = index
                        }
                    }
                } else if (validValue(nextPath, value)) {
                    target.put(key, when (value) {
                        is JSONObject -> JSONObject(value.toString())
                        is JSONArray -> JSONArray(value.toString())
                        else -> value
                    })
                    origins[nextPath] = name
                    priorities[nextPath] = index
                }
            }
        }
        sources.forEachIndexed { index, source ->
            source.record?.let { apply(merged, it, emptyList(), source.name, index) }
        }
        return Result(merged, origins, priorities)
    }
}
