package com.aholic.fakegxzq.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 极简 JSON path 实现，支持点号与中括号两种写法：
 *
 *   $.data.list[0].price
 *   data.list[*].price
 *   $['data']['list'][0]['price']
 *
 * 不支持递归下降与过滤表达式，够改写规则用即可。
 */
object JsonPath {

    private class Token private constructor(val field: String?, val index: Int, val wildcard: Boolean) {
        companion object {
            fun ofField(name: String) = Token(name, -1, false)
            fun ofIndex(index: Int) = Token(null, index, false)
            val ANY = Token(null, -1, true)
        }
    }

    fun get(root: Any?, path: String): List<Any?> {
        val tokens = tokenize(path) ?: return emptyList()
        val out = ArrayList<Any?>()
        collect(root, tokens, 0, out)
        return out
    }

    /** 命中即赋值为 value，返回命中节点数。 */
    fun set(root: Any?, path: String, value: Any?): Int = update(root, path) { value }

    /** 命中即用 fn(旧值) 的结果覆盖，返回命中节点数。 */
    fun update(root: Any?, path: String, fn: (Any?) -> Any?): Int {
        val tokens = tokenize(path) ?: return 0
        return mutate(root, tokens, 0, fn)
    }

    /** 删除命中的键/下标，返回删除数量。 */
    fun remove(root: Any?, path: String): Int {
        val tokens = tokenize(path) ?: return 0
        return drop(root, tokens, 0)
    }

    private fun collect(node: Any?, tokens: List<Token>, depth: Int, out: MutableList<Any?>) {
        if (node == null || depth >= tokens.size) return
        val token = tokens[depth]
        val last = depth == tokens.size - 1
        when (node) {
            is JSONObject -> {
                if (token.wildcard) {
                    for (key in keys(node)) {
                        if (last) out.add(node.opt(key)) else collect(node.opt(key), tokens, depth + 1, out)
                    }
                } else {
                    val key = token.field
                    if (last) {
                        if (key != null && node.has(key)) out.add(node.opt(key))
                    } else {
                        collect(key?.let { node.opt(it) }, tokens, depth + 1, out)
                    }
                }
            }
            is JSONArray -> {
                for (index in indices(node, token)) {
                    if (last) out.add(node.opt(index)) else collect(node.opt(index), tokens, depth + 1, out)
                }
            }
        }
    }

    private fun mutate(node: Any?, tokens: List<Token>, depth: Int, fn: (Any?) -> Any?): Int {
        if (node == null || depth >= tokens.size) return 0
        val token = tokens[depth]
        val last = depth == tokens.size - 1
        var hits = 0
        when (node) {
            is JSONObject -> {
                if (token.wildcard) {
                    for (key in keys(node)) {
                        if (last) {
                            node.put(key, fn(node.opt(key)))
                            hits++
                        } else {
                            hits += mutate(node.opt(key), tokens, depth + 1, fn)
                        }
                    }
                } else {
                    val key = token.field
                    if (key != null) {
                        if (last) {
                            node.put(key, fn(node.opt(key)))
                            hits++
                        } else {
                            hits += mutate(node.opt(key), tokens, depth + 1, fn)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (index in indices(node, token)) {
                    if (last) {
                        node.put(index, fn(node.opt(index)))
                        hits++
                    } else {
                        hits += mutate(node.opt(index), tokens, depth + 1, fn)
                    }
                }
            }
        }
        return hits
    }

    private fun drop(node: Any?, tokens: List<Token>, depth: Int): Int {
        if (node == null || depth >= tokens.size) return 0
        val token = tokens[depth]
        val last = depth == tokens.size - 1
        var hits = 0
        when (node) {
            is JSONObject -> {
                if (token.wildcard) {
                    for (key in keys(node)) {
                        if (last) {
                            node.remove(key)
                            hits++
                        } else {
                            hits += drop(node.opt(key), tokens, depth + 1)
                        }
                    }
                } else {
                    val key = token.field
                    if (key != null) {
                        if (last) {
                            if (node.has(key)) {
                                node.remove(key)
                                hits++
                            }
                        } else {
                            hits += drop(node.opt(key), tokens, depth + 1)
                        }
                    }
                }
            }
            is JSONArray -> {
                if (last) {
                    val targets = indices(node, token).distinct().sortedDescending()
                    for (index in targets) {
                        if (index >= 0 && index < node.length()) {
                            node.remove(index)
                            hits++
                        }
                    }
                } else {
                    for (index in indices(node, token)) hits += drop(node.opt(index), tokens, depth + 1)
                }
            }
        }
        return hits
    }

    private fun keys(obj: JSONObject): List<String> {
        val out = ArrayList<String>()
        val iterator = obj.keys()
        while (iterator.hasNext()) out.add(iterator.next())
        return out
    }

    private fun indices(array: JSONArray, token: Token): List<Int> {
        if (token.wildcard) return (0 until array.length()).toList()
        val index = if (token.index >= 0) token.index else token.field?.toIntOrNull() ?: -1
        return if (index in 0 until array.length()) listOf(index) else emptyList()
    }

    private fun tokenize(path: String): List<Token>? {
        var text = path.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("$")) text = text.substring(1)

        val tokens = ArrayList<Token>()
        var cursor = 0
        while (cursor < text.length) {
            when (text[cursor]) {
                '.' -> {
                    cursor++
                    if (cursor < text.length && text[cursor] == '.') return null
                }
                '[' -> {
                    val end = text.indexOf(']', cursor)
                    if (end < 0) return null
                    val inner = text.substring(cursor + 1, end).trim()
                    cursor = end + 1
                    if (inner == "*") {
                        tokens.add(Token.ANY)
                        continue
                    }
                    val quoted = inner.length >= 2 &&
                        ((inner.startsWith("'") && inner.endsWith("'")) ||
                            (inner.startsWith("\"") && inner.endsWith("\"")))
                    if (quoted) {
                        tokens.add(Token.ofField(inner.substring(1, inner.length - 1)))
                    } else {
                        val index = inner.toIntOrNull() ?: return null
                        tokens.add(Token.ofIndex(index))
                    }
                }
                else -> {
                    var end = cursor
                    while (end < text.length && text[end] != '.' && text[end] != '[') end++
                    val name = text.substring(cursor, end)
                    if (name.isEmpty()) return null
                    tokens.add(Token.ofField(name))
                    cursor = end
                }
            }
        }
        return tokens
    }
}
