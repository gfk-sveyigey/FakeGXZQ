package com.aholic.fakegxzq.core

import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** 配置拉取。后端 Cloudflare Worker 跨平台复用，Android 端请求同一个地址。 */
object ConfigClient {

    const val DEFAULT_CONFIG_URL = "https://gxzq.yugenpu.com/api/config"

    fun fetch(url: String = DEFAULT_CONFIG_URL, timeoutMillis: Int = 8000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "FakeGXZQ/Android")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP " + code }
            val encoding = connection.contentEncoding
            val stream = connection.inputStream
            val reader = if (encoding != null && encoding.contains("gzip", true)) GZIPInputStream(stream) else stream
            return reader.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * 配置管理：启动拉取 + 定时刷新 + 手动重载 + 本地缓存兜底。
 */
class ConfigManager(
    private val cacheFile: File?,
    private val urls: List<String> = listOf(ConfigClient.DEFAULT_CONFIG_URL),
    private val refreshSeconds: Long = 300L,
    private val onUpdate: (FakerConfig) -> Unit = {}
) {

    @Volatile
    var config: FakerConfig = FakerConfig.disabled()
        private set

    private var executor: ScheduledExecutorService? = null

    fun start() {
        loadCache()
        refresh()
        val service = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fakegxzq-config").apply { isDaemon = true }
        }
        executor = service
        service.scheduleWithFixedDelay({ refresh() }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS)
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    /** 手动重载（悬浮面板上的「重载」按钮）。 */
    fun refresh() {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val raw = ConfigClient.fetch(url)
                val parsed = ConfigParser.parse(raw, url)
                config = parsed
                saveCache(raw)
                Log.i(TAG, "配置已更新：" + url + "，规则 " + parsed.rules.size + " 条")
                runCatching { onUpdate(parsed) }
                return
            } catch (error: Throwable) {
                lastError = error
                Log.w(TAG, "配置拉取失败 " + url + "：" + error.message)
            }
        }
        Log.w(TAG, "所有配置源均失败，继续使用上一条配置", lastError)
    }

    private fun loadCache() {
        val file = cacheFile ?: return
        if (!file.isFile) return
        runCatching {
            val parsed = ConfigParser.parse(file.readText(Charsets.UTF_8), "cache")
            config = parsed
            Log.i(TAG, "已加载本地缓存，规则 " + parsed.rules.size + " 条")
        }
    }

    private fun saveCache(raw: String) {
        val file = cacheFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(raw, Charsets.UTF_8)
        }
    }

    companion object {
        private const val TAG = "FakeGXZQ/Config"
    }
}
