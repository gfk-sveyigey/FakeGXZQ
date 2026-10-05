package com.aholic.fakegxzq.core.monitor

import java.util.concurrent.CopyOnWriteArrayList

/** 进程内环形缓冲，供悬浮面板实时展示。 */
object MonitorStore {

    private const val CAPACITY = 500

    private val records = ArrayDeque<MonitorRecord>()
    private val listeners = CopyOnWriteArrayList<(List<MonitorRecord>) -> Unit>()

    @Volatile
    var paused = false

    @Volatile
    var enabled = true

    fun add(record: MonitorRecord) {
        if (!enabled || paused) return
        synchronized(records) {
            records.addFirst(record)
            while (records.size > CAPACITY) records.removeLast()
        }
        notifyListeners()
    }

    fun snapshot(): List<MonitorRecord> = synchronized(records) { records.toList() }

    fun clear() {
        synchronized(records) { records.clear() }
        notifyListeners()
    }

    fun addListener(listener: (List<MonitorRecord>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<MonitorRecord>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) return
        val snap = snapshot()
        listeners.forEach { it(snap) }
    }
}
