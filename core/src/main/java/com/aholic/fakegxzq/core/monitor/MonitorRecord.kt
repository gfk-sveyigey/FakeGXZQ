package com.aholic.fakegxzq.core.monitor

/** 一条监控记录：时间戳 / 类别 / 标题 / 副标题 / 详情。 */
data class MonitorRecord(
    val timeMillis: Long,
    val category: String,
    val title: String,
    val subtitle: String,
    val detail: String
)
