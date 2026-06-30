package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

class LongPressWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = LongPressWidgetHostView(context)
}

class LongPressWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private var longPressConsumed = false
    private var downX = 0f
    private var downY = 0f
    private val slop by lazy {
        val d = resources.displayMetrics.density
        (12 * d) * (12 * d)   // 12dp pohyb = zrušení long-pressu
    }

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressConsumed = true
        onLongPress?.invoke()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressConsumed = false
                handler.postDelayed(longPressRunnable, 1500L)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dx * dx + dy * dy > slop) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
            }
        }

        if (longPressConsumed) {
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressConsumed = false
            }
            val cancel = MotionEvent.obtain(ev).also { it.action = MotionEvent.ACTION_CANCEL }
            val result = super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return result
        }

        return super.dispatchTouchEvent(ev)
    }
}
