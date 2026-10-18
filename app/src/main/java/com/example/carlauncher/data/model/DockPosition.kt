package com.example.carlauncher.data.model

/** Where the dock sits on screen. Persisted by [name] in `launcher_prefs`. */
enum class DockPosition {
    /** Horizontal bar along the bottom edge (original layout). */
    BOTTOM,

    /** Vertical bar along the left edge. */
    LEFT;

    companion object {
        fun fromName(name: String?): DockPosition =
            entries.firstOrNull { it.name == name } ?: BOTTOM
    }
}
