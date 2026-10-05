package com.aholic.fakegxzq.core.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

/**
 * 三指长按手势。iOS 端用 UILongPressGestureRecognizer，
 * Android 端 hook Activity.dispatchTouchEvent 后把事件喂进来即可。
 */
class TripleFingerLongPress(
    private val holdMillis: Long = 1200L,
    private val onTrigger: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable {
        armed = false
        onTrigger()
    }

    @Volatile
    private var armed = false

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 3) arm() else cancel()
            MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount <= 3) cancel()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> cancel()
        }
    }

    fun cancel() {
        if (!armed) return
        armed = false
        handler.removeCallbacks(fire)
    }

    fun release() {
        armed = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun arm() {
        if (armed) return
        armed = true
        handler.postDelayed(fire, holdMillis)
    }
}
