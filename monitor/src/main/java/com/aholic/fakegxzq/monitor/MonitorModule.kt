package com.aholic.fakegxzq.monitor

import android.app.Activity
import android.view.MotionEvent
import com.aholic.fakegxzq.core.FakerConfig
import com.aholic.fakegxzq.core.TztBridge
import com.aholic.fakegxzq.core.monitor.MonitorRecord
import com.aholic.fakegxzq.core.monitor.MonitorStore
import com.aholic.fakegxzq.core.monitor.TztParser
import com.aholic.fakegxzq.core.ui.PanelController
import com.aholic.fakegxzq.core.ui.TripleFingerLongPress
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 入口（等同 iOS 的越狱 dylib 注入）。
 * Hook 点：上述两个 JS 执行点 + WebView.addJavascriptInterface + WebChromeClient.onConsoleMessage。
 */
class MonitorModule : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        XposedBridge.log(MonitorRuntime.TAG + " 模块已加载：" + startupParam.modulePath)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        MonitorRuntime.ensureStarted(lpparam)
    }
}

object MonitorRuntime {

    const val TAG = "FakeGXZQ/Monitor"

    private const val MODULE_PACKAGE = "com.aholic.fakegxzq.monitor"
    private const val JAVASCRIPT_PREFIX = "javascript:"
    private const val MAX_RAW_SCRIPT = 4000

    @Volatile
    private var started = false

    private var panel: PanelController? = null
    private var gesture: TripleFingerLongPress? = null

    fun ensureStarted(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) return
        synchronized(this) {
            if (started) return
            started = true
        }
        hookWebView(lpparam)
        hookBridge(lpparam)
        hookConsole(lpparam)
        hookActivity(lpparam)
    }

    private fun hookWebView(lpparam: XC_LoadPackage.LoadPackageParam) {
        val webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader)

        XposedBridge.hookAllMethods(webViewClass, "evaluateJavascript", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val script = param.args.firstOrNull() as? String ?: return
                recordScript("JS→Native", script)
            }
        })

        XposedBridge.hookAllMethods(webViewClass, "loadUrl", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args.firstOrNull() as? String ?: return
                if (!url.startsWith(JAVASCRIPT_PREFIX)) return
                recordScript("Native→JS", url.substring(JAVASCRIPT_PREFIX.length))
            }
        })
    }

    private fun hookBridge(lpparam: XC_LoadPackage.LoadPackageParam) {
        val webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader)
        XposedBridge.hookAllMethods(webViewClass, "addJavascriptInterface", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val target = param.args.firstOrNull()
                val name = param.args.getOrNull(1) as? String ?: "未命名"
                MonitorStore.add(
                    MonitorRecord(
                        timeMillis = System.currentTimeMillis(),
                        category = "Bridge",
                        title = "addJavascriptInterface(" + name + ")",
                        subtitle = target?.javaClass?.name.orEmpty(),
                        detail = describeInterface(target)
                    )
                )
            }
        })
    }

    private fun hookConsole(lpparam: XC_LoadPackage.LoadPackageParam) {
        val chromeClass = XposedHelpers.findClass("android.webkit.WebChromeClient", lpparam.classLoader)
        XposedBridge.hookAllMethods(chromeClass, "onConsoleMessage", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val message = param.args.firstOrNull() ?: return
                val text: String
                val subtitle: String
                if (message is String) {
                    text = message
                    subtitle = "行 " + param.args.getOrNull(1) + " · " + param.args.getOrNull(2)
                } else {
                    text = XposedHelpers.callMethod(message, "message") as? String ?: message.toString()
                    subtitle = "行 " + XposedHelpers.callMethod(message, "lineNumber")
                }
                MonitorStore.add(
                    MonitorRecord(System.currentTimeMillis(), "Console", "控制台", subtitle, text)
                )
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

    /** 解析一次 JS 执行：命中 TZT 桥就按协议展示，否则退化成原始脚本。 */
    fun recordScript(category: String, script: String) {
        if (!MonitorStore.enabled) return
        val result = try {
            TztBridge.rewrite(script, FakerConfig.disabled())
        } catch (error: Throwable) {
            XposedBridge.log(error)
            null
        }
        if (result != null && result.calls.isNotEmpty()) {
            result.calls.forEach { call ->
                MonitorStore.add(TztParser.describe(category, call.decoded, call.changed))
            }
            return
        }
        if (script.length <= MAX_RAW_SCRIPT) {
            MonitorStore.add(MonitorRecord(System.currentTimeMillis(), category, "原始脚本", "", script))
        }
    }

    private fun describeInterface(target: Any?): String {
        val clazz = target?.javaClass ?: return ""
        val methods = clazz.declaredMethods.take(40).joinToString("\n") { method ->
            method.name + "(" + method.parameterTypes.joinToString(", ") { it.simpleName } + ")"
        }
        return clazz.name + "\n\n" + methods
    }

    private fun attachPanel(activity: Activity) {
        val controller = panel ?: PanelController { context -> MonitorPanel(context) }.also { panel = it }
        controller.attach(activity)
        if (gesture == null) {
            gesture = TripleFingerLongPress { controller.toggle() }
        }
    }
}
