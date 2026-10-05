package com.aholic.fakegxzq.core.monitor

import com.aholic.fakegxzq.core.Json
import org.json.JSONObject

/** 把一次桥调用解析成可展示的监控记录。 */
object TztParser {

    fun describe(category: String, decoded: String, changed: Boolean = false): MonitorRecord {
        val json = Json.parse(decoded) as? JSONObject
        val action = text(json, "ACTION")
        val type = text(json, "TYPE")
        val rid = text(json, "RID")
        val errorNo = text(json, "ERRORNO")

        val subtitle = buildList {
            if (type.isNotBlank()) add("TYPE=" + type)
            if (rid.isNotBlank()) add("RID=" + rid)
            if (errorNo.isNotBlank()) add("ERRORNO=" + errorNo)
        }.joinToString("   ")

        val title = (action.ifBlank { "未知 ACTION" }) + if (changed) "   (已改写)" else ""
        return MonitorRecord(
            timeMillis = System.currentTimeMillis(),
            category = category,
            title = title,
            subtitle = subtitle,
            detail = Desensitizer.mask(decoded)
        )
    }

    private fun text(json: JSONObject?, key: String): String {
        val obj = json ?: return ""
        return obj.optString(key).ifBlank { obj.optString(key.lowercase()) }
    }
}
