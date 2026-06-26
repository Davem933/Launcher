package com.example.carlauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

class LongPressWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = LongPressWidgetHostView(context)
}

class LongPressWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    // Set to true once long press fires; cleared on ACTION_UP/CANCEL.
    // While true, we replace ACTION_UP with ACTION_CANCEL for children so
    // the widget doesn't interpret finger-lift as a click and launch its app.
    private var longPressConsumed = false

    private val gestureDetector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressConsumed = true
                onLongPress?.invoke()
            }
        }
    )

    // dispatchTouchEvent is called for EVERY event regardless of
    // requestDisallowInterceptTouchEvent — unlike onInterceptTouchEvent,
    // which stops receiving ACTION_MOVE once a child widget calls
    // requestDisallowInterceptTouchEvent(true) during its own gesture.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)

        if (longPressConsumed) {
            val action = ev.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                longPressConsumed = false
            }
            // Replace the event with ACTION_CANCEL so children don't fire a click.
            val cancel = MotionEvent.obtain(ev).also { it.action = MotionEvent.ACTION_CANCEL }
            val result = super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return result
        }

        return super.dispatchTouchEvent(ev)
    }
}
