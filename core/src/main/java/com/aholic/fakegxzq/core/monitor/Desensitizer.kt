package com.aholic.fakegxzq.core.monitor

import com.aholic.fakegxzq.core.Json
import org.json.JSONArray
import org.json.JSONObject

/** 敏感字段脱敏：token / cookie / authorization 等只保留首尾各 2 位。 */
object Desensitizer {

    private val SENSITIVE = listOf(
        "token", "accesstoken", "refreshtoken", "cookie", "authorization", "auth",
        "password", "passwd", "pwd", "secret", "session", "sign", "signature",
        "privatekey", "apikey", "credential", "idcard", "bankcard"
    )

    fun isSensitive(key: String): Boolean {
        val normalized = key.lowercase().replace("_", "").replace("-", "")
        return SENSITIVE.any { normalized.contains(it) }
    }

    /** 输入 JSON 文本，输出脱敏后的美化 JSON；解析失败时原样返回。 */
    fun mask(raw: String): String {
        val parsed = Json.parse(raw) ?: return raw
        return Json.pretty(maskNode(parsed))
    }

    fun maskNode(node: Any?): Any? = when (node) {
        is JSONObject -> {
            val out = JSONObject()
            val iterator = node.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val value = node.opt(key)
                if (isSensitive(key)) out.put(key, maskValue(value)) else out.put(key, maskNode(value))
            }
            out
        }
        is JSONArray -> {
            val out = JSONArray()
            for (index in 0 until node.length()) out.put(maskNode(node.opt(index)))
            out
        }
        else -> node
    }

    fun maskValue(value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return value
        val text = value.toString()
        if (text.isEmpty()) return text
        if (text.length <= 4) return "****"
        return text.substring(0, 2) + "****" + text.substring(text.length - 2)
    }
}
