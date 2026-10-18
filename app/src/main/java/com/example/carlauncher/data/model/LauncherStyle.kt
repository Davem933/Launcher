package com.example.carlauncher.data.model

/** Visual style of the launcher home page. Persisted by [name] in `launcher_prefs`. */
enum class LauncherStyle {
    /** Original layout — CarColors cards, horizontal volume/brightness strip. */
    CLASSIC,

    /** Apple-inspired layout — black canvas, glass cards, vertical sliders (Claude Design v2, 2a). */
    AURORA,

    /** Apple-inspired, map across the whole screen with floating cards + dock (Claude Design v2, 2b). */
    AURORA_MAP;

    /** Both Apple-inspired styles share the Aurora palette and dock look. */
    val isAurora: Boolean get() = this != CLASSIC

    companion object {
        fun fromName(name: String?): LauncherStyle =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
