package com.aholic.fakegxzq.core

import android.util.Base64
import org.json.JSONObject

/** 一次桥调用的解析结果，供 Monitor 展示。 */
data class TztCallRecord(
    val rid: String?,
    val type: String?,
    val action: String?,
    val errorNo: String?,
    val decoded: String,
    val rewritten: String?,
    val firedRules: List<String>
) {
    val changed: Boolean get() = rewritten != null && rewritten != decoded
}

/** 一次脚本改写的结果。 */
data class ScriptRewrite(
    val script: String,
    val changed: Boolean,
    val calls: List<TztCallRecord>
)

/**
 * TZT 桥协议处理，对应 iOS 端的 RFProcessJavaScript：
 *
 *   tztCallWebFuctionFromNative(rid, type, base64)
 *
 * 取第 3 个参数 -> Base64 解码 -> 解析外层 JSON -> 改写 RESULT -> 重新编码，只替换原 JS 中该段。
 */
object TztBridge {

    private val CALL_PATTERN = Regex(
        "tztCallWebFuctionFromNative\\s*\\(\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*,\\s*" +
            "('(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")\\s*\\)"
    )

    fun rewrite(script: String, config: FakerConfig): ScriptRewrite {
        val matches = CALL_PATTERN.findAll(script).toList()
        if (matches.isEmpty()) return ScriptRewrite(script, false, emptyList())

        val builder = StringBuilder()
        val calls = ArrayList<TztCallRecord>()
        var cursor = 0
        var changed = false

        for (match in matches) {
            val literal = match.groupValues[3]
            val range = match.groups[3]!!.range
            builder.append(script, cursor, range.first)

            val record = decode(unquote(literal))?.let { rewritePayload(it, config) }
            if (record != null && record.rewritten != null && record.rewritten != record.decoded) {
                builder.append(quote(encode(unquote(literal), record.rewritten), literal))
                changed = true
            } else {
                builder.append(literal)
            }
            if (record != null) calls.add(record)
            cursor = range.last + 1
        }
        builder.append(script, cursor, script.length)
        return ScriptRewrite(builder.toString(), changed, calls)
    }

    /** 便捷入口：直接返回改写后的脚本；[recorder] 用于把每次桥调用交给 Monitor。 */
    fun apply(script: String, config: FakerConfig, recorder: ((TztCallRecord) -> Unit)? = null): String {
        val result = rewrite(script, config)
        if (recorder != null) result.calls.forEach(recorder)
        return result.script
    }

    fun decodeBase64(text: String): String? {
        val cleaned = text.replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length < 4) return null
        val flags = intArrayOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.URL_SAFE,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        for (flag in flags) {
            val bytes = runCatching { Base64.decode(cleaned, flag) }.getOrNull() ?: continue
            val value = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: continue
            val trimmed = value.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        }
        return null
    }

    private fun decode(payload: String): JSONObject? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null
        val decoded = decodeBase64(trimmed) ?: trimmed
        return Json.parse(decoded) as? JSONObject
    }

    private fun rewritePayload(outer: JSONObject, config: FakerConfig): TztCallRecord {
        val before = outer.toString()
        val fired = FakerEngine.rewrite(outer, config)
        val after = outer.toString()
        return TztCallRecord(
            rid = outer.optString("RID").ifBlank { outer.optString("rid").ifBlank { null } },
            type = outer.optString("TYPE").ifBlank { outer.optString("type").ifBlank { null } },
            action = outer.optString("ACTION").ifBlank { outer.optString("action").ifBlank { null } },
            errorNo = outer.optString("ERRORNO").ifBlank { null },
            decoded = Json.pretty(before),
            rewritten = if (fired.isEmpty()) null else Json.pretty(after),
            firedRules = fired.map { it.id }
        )
    }

    private fun encode(originalPayload: String, prettyJson: String): String {
        val compact = Json.parse(prettyJson)?.let { Json.stringify(it) } ?: prettyJson
        val wasBase64 = decodeBase64(originalPayload) != null
        return if (wasBase64) {
            Base64.encodeToString(compact.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } else {
            compact
        }
    }

    private fun unquote(literal: String): String {
        val body = literal.substring(1, literal.length - 1)
        return body.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun quote(value: String, template: String): String {
        return if (template.startsWith("\"")) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
        }
    }
}
