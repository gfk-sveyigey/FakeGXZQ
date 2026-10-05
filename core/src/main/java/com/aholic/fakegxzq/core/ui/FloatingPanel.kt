package com.aholic.fakegxzq.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** 便捷 dp -> px，供面板与子模块复用。 */
fun dp(context: Context, value: Float): Int =
    (value * context.resources.displayMetrics.density + 0.5f).toInt()

/**
 * 悬浮面板（叠加在 android.R.id.content 上，免悬浮窗权限）。
 * 标题栏可拖动，提供一组按钮，子类可往 [body] 里塞自定义内容。
 */
open class FloatingPanel(context: Context) : LinearLayout(context) {

    private val titleView = TextView(context)
    private val statusView = TextView(context)
    private val buttonRow = LinearLayout(context)

    /** 子类自定义区域。 */
    val body = LinearLayout(context)

    var titleText: String
        get() = titleView.text?.toString().orEmpty()
        set(value) { titleView.text = value }

    var statusText: String
        get() = statusView.text?.toString().orEmpty()
        set(value) { statusView.text = value }

    init {
        orientation = VERTICAL
        background = rounded(Color.argb(232, 17, 24, 39))
        elevation = dp(context, 8f).toFloat()
        setPadding(dp(context, 12f), dp(context, 10f), dp(context, 12f), dp(context, 10f))

        titleView.text = "FakeGXZQ"
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 13f
        titleView.setTypeface(Typeface.DEFAULT_BOLD)
        titleView.setPadding(0, 0, 0, dp(context, 2f))

        statusView.setTextColor(Color.parseColor("#9CA3AF"))
        statusView.textSize = 11f
        statusView.setPadding(0, 0, 0, dp(context, 6f))

        body.orientation = VERTICAL
        buttonRow.orientation = HORIZONTAL
        buttonRow.gravity = Gravity.END
        buttonRow.setPadding(0, dp(context, 6f), 0, 0)

        addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(buttonRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        installDrag(titleView)
        installDrag(statusView)
    }

    fun addButton(label: String, onClick: () -> Unit): TextView {
        val button = TextView(context)
        button.text = label
        button.setTextColor(Color.parseColor("#E5E7EB"))
        button.textSize = 12f
        button.setPadding(dp(context, 10f), dp(context, 6f), dp(context, 10f), dp(context, 6f))
        button.background = rounded(Color.argb(255, 31, 41, 55))
        button.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.leftMargin = dp(context, 6f)
        buttonRow.addView(button, params)
        return button
    }

    private fun rounded(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, 12f).toFloat()
        }

    private fun installDrag(handle: View) {
        handle.setOnTouchListener(object : OnTouchListener {
            private var lastRawX = 0f
            private var lastRawY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        translationX += event.rawX - lastRawX
                        translationY += event.rawY - lastRawY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        return true
                    }
                }
                return false
            }
        })
    }
}
