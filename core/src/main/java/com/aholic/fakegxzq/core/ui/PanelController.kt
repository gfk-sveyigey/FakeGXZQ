package com.aholic.fakegxzq.core.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** 把面板挂到当前 Activity 的 content 上，并管理显隐。 */
class PanelController(private val factory: (Context) -> FloatingPanel) {

    private var panel: FloatingPanel? = null

    val view: FloatingPanel? get() = panel

    var status: String
        get() = panel?.statusText.orEmpty()
        set(value) { panel?.statusText = value }

    val isVisible: Boolean get() = panel?.visibility == View.VISIBLE

    fun attach(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = panel ?: factory(activity).also { panel = it }
        if (view.parent === root) {
            view.visibility = View.VISIBLE
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        val params = FrameLayout.LayoutParams(dp(activity, 250f), ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = dp(activity, 12f)
        params.topMargin = dp(activity, 96f)
        root.addView(view, params)
    }

    fun detach() {
        (panel?.parent as? ViewGroup)?.removeView(panel)
    }

    fun show() {
        panel?.visibility = View.VISIBLE
    }

    fun hide() {
        panel?.visibility = View.GONE
    }

    fun toggle() {
        val target = panel ?: return
        target.visibility = if (target.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
}
