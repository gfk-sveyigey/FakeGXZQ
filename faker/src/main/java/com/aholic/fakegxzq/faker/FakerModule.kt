package com.aholic.fakegxzq.faker

import android.app.Activity
import android.view.MotionEvent
import com.aholic.fakegxzq.core.ConfigManager
import com.aholic.fakegxzq.core.FakerConfig
import com.aholic.fakegxzq.core.TztBridge
import com.aholic.fakegxzq.core.ui.FloatingPanel
import com.aholic.fakegxzq.core.ui.PanelController
import com.aholic.fakegxzq.core.ui.TripleFingerLongPress
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * LSPosed 入口（等同 iOS 的越狱 dylib 注入）。
 * Hook 点：WebView.evaluateJavascript / WebView.loadUrl("javascript:...")。
 */
class FakerModule : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XposedBridge.log(FakerRuntime.TAG + " 模块已加载：" + startupParam.modulePath)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        FakerRuntime.ensureStarted(lpparam)
    }
}

object FakerRuntime {

    const val TAG = "FakeGXZQ/Faker"

    private const val MODULE_PACKAGE = "com.aholic.fakegxzq.faker"
    private const val JAVASCRIPT_PREFIX = "javascript:"

    @Volatile
    private var started = false

    @Volatile
    private var paused = false

    private var manager: ConfigManager? = null
    private var panel: PanelController? = null
    private var gesture: TripleFingerLongPress? = null

    fun ensureStarted(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) return
        synchronized(this) {
            if (started) return
            started = true
        }

        val dataDir = lpparam.appInfo?.dataDir ?: ("/data/data/" + lpparam.packageName)
        val cacheFile = File(File(dataDir, "files/fakegxzq"), "config.json")
        val configManager = ConfigManager(cacheFile) { config ->
            XposedBridge.log(TAG + " 配置更新：enabled=" + config.enabled + "，规则 " + config.rules.size + " 条")
            panel?.status = summarize(config)
        }
        manager = configManager
        configManager.start()

        hookWebView(lpparam)
        hookActivity(lpparam)
    }

    private fun hookWebView(lpparam: XC_LoadPackage.LoadPackageParam) {
        val webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader)

        XposedBridge.hookAllMethods(webViewClass, "evaluateJavascript", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val script = param.args.firstOrNull() as? String ?: return
                val rewritten = processScript(script)
                if (rewritten !== script) param.args[0] = rewritten
            }
        })

        XposedBridge.hookAllMethods(webViewClass, "loadUrl", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args.firstOrNull() as? String ?: return
                if (!url.startsWith(JAVASCRIPT_PREFIX)) return
                val body = url.substring(JAVASCRIPT_PREFIX.length)
                val rewritten = processScript(body)
                if (rewritten !== body) param.args[0] = JAVASCRIPT_PREFIX + rewritten
            }
        })
    }

    private fun hookActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)

        XposedBridge.hookAllMethods(activityClass, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (activity.packageName != lpparam.packageName) return
                attachPanel(activity)
            }
        })

        XposedBridge.hookAllMethods(activityClass, "dispatchTouchEvent", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args.firstOrNull() as? MotionEvent ?: return
                gesture?.onTouchEvent(event)
            }
        })
    }

    /** 核心改写：返回替换后的脚本；无命中或无配置时原样返回。 */
    fun processScript(script: String): String {
        if (paused) return script
        val config = currentConfig()
        if (!config.enabled || config.rules.isEmpty()) return script
        return try {
            TztBridge.apply(script, config) { record ->
                if (record.changed) {
                    XposedBridge.log(TAG + " 改写 " + record.action + "：" + record.firedRules)
                }
            }
        } catch (error: Throwable) {
            XposedBridge.log(error)
            script
        }
    }

    private fun currentConfig(): FakerConfig = manager?.config ?: FakerConfig.disabled()

    private fun summarize(config: FakerConfig): String {
        if (!config.enabled) return "已禁用 · 来源 " + config.source
        return "规则 " + config.rules.size + " 条 · 来源 " + config.source
    }

    private fun attachPanel(activity: Activity) {
        val controller = panel ?: PanelController { context ->
            FloatingPanel(context).apply {
                titleText = "FakeGXZQ · Faker"
                statusText = "初始化中…"
                addButton("重载") { manager?.refresh() }
                addButton("暂停") {
                    paused = !paused
                    statusText = if (paused) "已暂停改写" else summarize(currentConfig())
                }
                addButton("隐藏") { visibility = android.view.View.GONE }
            }
        }.also { panel = it }

        controller.attach(activity)
        controller.status = summarize(currentConfig())

        if (gesture == null) {
            gesture = TripleFingerLongPress { controller.toggle() }
        }
    }
}
