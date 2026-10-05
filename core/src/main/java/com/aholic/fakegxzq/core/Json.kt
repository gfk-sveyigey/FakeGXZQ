package com.aholic.fakegxzq.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * JSON 小工具：统一 JSONObject / JSONArray 的解析、序列化与美化输出。
 */
object Json {

    fun parse(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        return JSONTokener(trimmed).nextValue().let { value ->
            when (value) {
                is JSONObject, is JSONArray -> value
                else -> null
            }
        }
    }

    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is JSONObject, is JSONArray -> value.toString()
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    fun pretty(raw: String): String = parse(raw)?.let { pretty(it) } ?: raw

    fun pretty(value: Any?, indent: Int = 2): String = when (value) {
        is JSONObject -> value.toString(indent)
        is JSONArray -> value.toString(indent)
        null -> "null"
        else -> value.toString()
    }

    fun isContainer(value: Any?): Boolean = value is JSONObject || value is JSONArray

    fun numberOrNull(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    fun opt(root: Any?, key: String): Any? = (root as? JSONObject)?.opt(key)

    fun put(root: Any?, key: String, value: Any?): Boolean {
        val obj = root as? JSONObject ?: return false
        obj.put(key, value)
        return true
    }
}
