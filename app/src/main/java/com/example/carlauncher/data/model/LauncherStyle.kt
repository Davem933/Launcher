package com.example.carlauncher.data.model

/** Visual style of the launcher home page. Persisted by [name] in `launcher_prefs`. */
enum class LauncherStyle {
    /** Original layout — CarColors cards, horizontal volume/brightness strip. */
    CLASSIC,

    /** Apple-inspired layout — black canvas, glass cards, vertical sliders (Claude Design v2, 2a). */
    AURORA,

    /** Apple-inspired, map across the whole screen with floating cards + dock (Claude Design v2, 2b). */
    AURORA_MAP,

    /** Light mode — widgets left, map right, pill dock (Claude Design "Car Launcher", 2a). */
    LIGHT,

    /** Instrument panel — sharp edges, 1dp hairlines, dock on top, lime accent (2b). */
    DASH,

    /** Three columns — music, map, clock + weather + calendar (2c). */
    TRIO;

    /** Both Apple-inspired styles share the Aurora palette and dock look. */
    val isAurora: Boolean get() = this == AURORA || this == AURORA_MAP

    /** The three "Car Launcher" directions — each brings its own palette, dock and map chrome. */
    val isSkin: Boolean get() = this == LIGHT || this == DASH || this == TRIO

    companion object {
        fun fromName(name: String?): LauncherStyle =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
