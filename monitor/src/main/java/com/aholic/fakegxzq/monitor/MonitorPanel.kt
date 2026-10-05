package com.aholic.fakegxzq.monitor

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.aholic.fakegxzq.core.monitor.MonitorRecord
import com.aholic.fakegxzq.core.monitor.MonitorStore
import com.aholic.fakegxzq.core.ui.FloatingPanel
import com.aholic.fakegxzq.core.ui.dp

/** Monitor 的悬浮面板：列表 + 详情弹窗 + 清空/暂停。 */
class MonitorPanel(context: Context) : FloatingPanel(context) {

    private val listView = ListView(context)
    private val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1)
    private var records: List<MonitorRecord> = emptyList()

    init {
        titleText = "FakeGXZQ · Monitor"
        statusText = "等待桥消息…"

        addButton("清空") { MonitorStore.clear() }
        addButton("暂停") {
            MonitorStore.paused = !MonitorStore.paused
            refreshStatus()
        }
        addButton("隐藏") { visibility = View.GONE }

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> showDetail(position) }
        body.addView(listView, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 260f)))

        MonitorStore.addListener { latest -> update(latest) }
        update(MonitorStore.snapshot())
    }

    private fun update(latest: List<MonitorRecord>) {
        records = latest
        adapter.clear()
        latest.forEach { adapter.add(render(it)) }
        adapter.notifyDataSetChanged()
        refreshStatus()
    }

    private fun refreshStatus() {
        statusText = (if (MonitorStore.paused) "已暂停 · " else "") + "共 " + records.size + " 条"
    }

    private fun render(record: MonitorRecord): String {
        val suffix = if (record.subtitle.isBlank()) "" else "\n" + record.subtitle
        return "[" + record.category + "] " + record.title + suffix
    }

    private fun showDetail(position: Int) {
        val record = records.getOrNull(position) ?: return
        val content = TextView(context).apply {
            text = record.detail
            setTextIsSelectable(true)
            textSize = 11f
            setPadding(dp(context, 16f), dp(context, 12f), dp(context, 16f), dp(context, 12f))
        }
        val scroll = ScrollView(context)
        scroll.addView(content)
        AlertDialog.Builder(context)
            .setTitle(record.title)
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }
}
