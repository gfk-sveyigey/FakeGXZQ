package com.aholic.fakegxzq.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 核心改写引擎，等价于 iOS 端的 RFProcessJavaScript：
 *
 *   Base64 解码 -> 解析外层 JSON -> 按 ACTION 匹配规则 -> 用 JSON path 改写 RESULT -> 重新编码
 *
 * 与平台无关，Faker/Monitor 两个模块共用。
 */
object FakerEngine {

    /** 改写外层 JSON 中的 RESULT，返回真正命中的规则。 */
    fun rewrite(outer: JSONObject, config: FakerConfig): List<Rule> {
        if (!config.enabled) return emptyList()

        var action = outer.optString("ACTION")
        if (action.isBlank()) action = outer.optString("action")
        val key = when {
            outer.has("RESULT") -> "RESULT"
            outer.has("result") -> "result"
            else -> return emptyList()
        }
        val rules = config.rulesFor(action.ifBlank { null })
        if (rules.isEmpty()) return emptyList()

        val fired = ArrayList<Rule>()
        when (val raw = outer.opt(key)) {
            is String -> {
                val trimmed = raw.trim()
                val parsed = if (trimmed.startsWith("{") || trimmed.startsWith("[")) Json.parse(trimmed) else null
                if (parsed != null) {
                    fired.addAll(applyRules(parsed, rules))
                    outer.put(key, Json.stringify(parsed))
                }
            }
            else -> if (Json.isContainer(raw)) fired.addAll(applyRules(raw, rules))
        }
        return fired
    }

    /** 对 RESULT 根节点施加规则，RESULT 允许是字典或数组两种形态。 */
    fun applyRules(root: Any?, rules: List<Rule>): List<Rule> {
        val fired = ArrayList<Rule>()
        for (rule in rules) {
            val hits = when (rule.op) {
                RuleOp.REPLACE, RuleOp.SET -> JsonPath.update(root, rule.path) { rule.value }
                RuleOp.FILL -> JsonPath.update(root, rule.path) { old -> if (isEmpty(old)) rule.value else old }
                RuleOp.MULTIPLY -> JsonPath.update(root, rule.path) { old ->
                    val base = Json.numberOrNull(old)
                    val factor = Json.numberOrNull(rule.value)
                    if (base == null || factor == null) old else base * factor
                }
                RuleOp.ADD -> JsonPath.update(root, rule.path) { old ->
                    val base = Json.numberOrNull(old)
                    val delta = Json.numberOrNull(rule.value)
                    if (base == null || delta == null) old else base + delta
                }
                RuleOp.APPEND -> JsonPath.update(root, rule.path) { old -> append(old, rule.value) }
                RuleOp.REMOVE -> JsonPath.remove(root, rule.path)
            }
            if (hits > 0) fired.add(rule)
        }
        return fired
    }

    private fun isEmpty(value: Any?): Boolean {
        if (value == null || value === JSONObject.NULL) return true
        return value is String && value.isEmpty()
    }

    private fun append(old: Any?, extra: Any?): Any? = when (old) {
        null -> extra
        is JSONArray -> {
            val merged = JSONArray()
            for (index in 0 until old.length()) merged.put(old.opt(index))
            merged.put(extra)
            merged
        }
        is String -> old + (extra?.toString() ?: "")
        else -> old
    }
}
