package com.aholic.fakegxzq.core

import org.json.JSONObject

/** 改写动作。 */
enum class RuleOp(val id: String) {
    REPLACE("replace"),
    SET("set"),
    FILL("fill"),
    MULTIPLY("multiply"),
    ADD("add"),
    APPEND("append"),
    REMOVE("remove");

    companion object {
        fun from(raw: String?): RuleOp {
            if (raw == null) return REPLACE
            val key = raw.trim().lowercase()
            values().firstOrNull { it.id == key }?.let { return it }
            return when (key) {
                "json", "jsonpath", "override", "rewrite", "value" -> REPLACE
                "plus", "inc", "increase", "offset" -> ADD
                "times", "scale", "mul", "factor" -> MULTIPLY
                "delete", "del", "drop" -> REMOVE
                "default" -> FILL
                "push", "concat" -> APPEND
                else -> REPLACE
            }
        }
    }
}

/**
 * 单条改写规则：ACTION 命中后，对 RESULT 里的 [path] 施加 [op]。
 */
data class Rule(
    val id: String,
    val action: String?,
    val actionPattern: Regex?,
    val path: String,
    val op: RuleOp,
    val value: Any?,
    val enabled: Boolean,
    val note: String?
) {
    fun matches(action: String?): Boolean {
        if (!enabled) return false
        val pattern = actionPattern ?: return true
        return action != null && pattern.containsMatchIn(action)
    }
}

/** 一份完整配置。 */
data class FakerConfig(
    val enabled: Boolean,
    val rules: List<Rule>,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun rulesFor(action: String?): List<Rule> {
        if (!enabled) return emptyList()
        return rules.filter { it.matches(action) }
    }

    companion object {
        fun disabled(): FakerConfig = FakerConfig(false, emptyList(), "none", 0L)
    }
}

/**
 * 配置解析：兼容 {enabled,rules} / {data:{...}} / {config:{...}} 三种外层。
 */
object ConfigParser {

    fun parse(raw: String, source: String): FakerConfig {
        val shell = unwrap(Json.parse(raw)) ?: return FakerConfig(false, emptyList(), source)
        val enabled = when (val value = shell.opt("enabled")) {
            null -> true
            is Boolean -> value
            is String -> value.equals("true", true) || value == "1"
            is Number -> value.toInt() != 0
            else -> true
        }
        val array = shell.optJSONArray("rules")
            ?: shell.optJSONArray("items")
            ?: shell.optJSONArray("list")
        val rules = ArrayList<Rule>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseRule(item, index)?.let { rules.add(it) }
            }
        }
        return FakerConfig(enabled, rules, source)
    }

    private fun unwrap(root: Any?): JSONObject? {
        val obj = root as? JSONObject ?: return null
        val shells = listOf("data", "config", "result", "payload")
        for (key in shells) {
            val nested = obj.optJSONObject(key)
            if (nested != null && (nested.has("rules") || nested.has("items") || nested.has("enabled"))) return nested
        }
        if (obj.has("rules") || obj.has("items")) return obj
        for (key in shells) {
            obj.optJSONObject(key)?.let { return it }
        }
        return obj
    }

    private fun parseRule(item: JSONObject, index: Int): Rule? {
        var path = item.optString("path")
        if (path.isBlank()) path = item.optString("jsonPath")
        if (path.isBlank()) return null

        val action = item.optString("action").ifBlank { item.optString("match") }
        val regex = item.optString("actionRegex").ifBlank { item.optString("regex") }
        val pattern = when {
            regex.isNotBlank() -> runCatching { Regex(regex) }.getOrNull()
            action.isNotBlank() -> globToRegex(action)
            else -> null
        }

        val enabled = when (val value = item.opt("enabled")) {
            null -> true
            is Boolean -> value
            is String -> !value.equals("false", true)
            else -> true
        }
        var id = item.optString("id")
        if (id.isBlank()) id = item.optString("name")
        if (id.isBlank()) id = "rule-" + index

        return Rule(
            id = id,
            action = action.ifBlank { null },
            actionPattern = pattern,
            path = path,
            op = RuleOp.from(item.optString("op").ifBlank { item.optString("type") }),
            value = item.opt("value"),
            enabled = enabled,
            note = item.optString("note").ifBlank { null }
        )
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = StringBuilder()
        for (char in glob) {
            when (char) {
                '*' -> pattern.append(".*")
                '?' -> pattern.append('.')
                else -> pattern.append(Regex.escape(char.toString()))
            }
        }
        return Regex(pattern.toString(), RegexOption.IGNORE_CASE)
    }
}
