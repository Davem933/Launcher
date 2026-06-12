package com.example.carlauncher.data.model

import android.graphics.drawable.Drawable

// Used by SlotPicker to carry resolved app info temporarily (icon is not persisted)
data class DockItem(
    val packageName: String,
    val displayName: String,
    val icon: Drawable? = null
)

sealed class DockSlot {
    /** Single app slot — only package name is persisted, icon resolved at render time */
    data class App(val packageName: String) : DockSlot()

    /**
     * Split screen shortcut.
     * packageName1 = navigation app (left)   — FIXED RULE
     * packageName2 = music/secondary (right) — FIXED RULE
     */
    data class SplitScreen(
        val packageName1: String,
        val packageName2: String,
        val label: String
    ) : DockSlot()

    object Empty    : DockSlot()
    object Navigate : DockSlot()   // fixed last slot, not stored in DataStore

    fun encode(): String = when (this) {
        is App         -> packageName
        is SplitScreen -> "split:$packageName1:$packageName2:${label.replace(",", "|")}"
        Empty          -> "empty"
        Navigate       -> "nav"
    }
}
