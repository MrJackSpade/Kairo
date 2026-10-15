package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject

/** Structural copies of catalog JSON, without serializing and reparsing it. */
object JsonCopy {
    fun deep(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { copy ->
            for (key in value.keys()) copy.put(key, deep(value.opt(key)))
        }
        is JSONArray -> JSONArray().also { copy ->
            for (index in 0 until value.length()) copy.put(deep(value.opt(index)))
        }
        else -> value
    }

    /** A new top-level object sharing [source]'s values, so replacing a field leaves [source] intact. */
    fun shallow(source: JSONObject): JSONObject = JSONObject().also { copy ->
        for (key in source.keys()) copy.put(key, source.opt(key))
    }
}
