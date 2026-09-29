package com.mrjackspade.kairo.frontend

import org.json.JSONObject

/** Validated field and subfield precedence for shipped, updated, and local catalog records. */
object CatalogFieldLayers {
    /** The last explicit boolean in catalog priority order controls visibility. */
    fun hidden(vararg values: Any?): Boolean =
        values.filterIsInstance<Boolean>().lastOrNull() ?: false

    data class Source(val name: String, val record: JSONObject?)
    data class Result(val record: JSONObject, private val origins: Map<List<String>, String>) {
        fun sourceOf(vararg path: String): String? = origins[path.toList()]
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
              validValue: (List<String>, Any) -> Boolean): Result {
        val merged = JSONObject()
        val origins = HashMap<List<String>, String>()
        fun apply(target: JSONObject, source: JSONObject, path: List<String>, name: String) {
            for (key in source.keys()) {
                val nextPath = path + key
                val value = source.opt(key) ?: continue
                if (value is JSONObject && objectField(nextPath)) {
                    val child = target.optJSONObject(key) ?: JSONObject()
                    apply(child, value, nextPath, name)
                    if (child.length() > 0) target.put(key, child)
                } else if (validValue(nextPath, value)) {
                    target.put(key, value)
                    origins[nextPath] = name
                }
            }
        }
        sources.forEach { source ->
            source.record?.let { apply(merged, it, emptyList(), source.name) }
        }
        return Result(merged, origins)
    }
}
