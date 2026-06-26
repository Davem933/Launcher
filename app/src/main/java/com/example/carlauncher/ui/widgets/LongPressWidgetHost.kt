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

    private val gestureDetector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                onLongPress?.invoke()
            }
        }
    )

    // dispatchTouchEvent is called for EVERY event regardless of
    // requestDisallowInterceptTouchEvent — unlike onInterceptTouchEvent,
    // which stops receiving ACTION_MOVE once a child widget calls
    // requestDisallowInterceptTouchEvent(true) during its own gesture.
    // This guarantees the GestureDetector sees ACTION_MOVE and can cancel
    // the long-press timer when the user starts scrolling.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
