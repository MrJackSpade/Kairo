package com.mrjackspade.kairo.frontend

import org.json.JSONObject

/** Validated field and subfield precedence for shipped, updated, and local catalog records. */
object CatalogFieldLayers {
    data class Source(val name: String, val record: JSONObject?)
    data class Result(val record: JSONObject, private val origins: Map<List<String>, String>) {
        fun sourceOf(vararg path: String): String? = origins[path.toList()]
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
