package com.example.carlauncher.data.model

/** Visual style of the launcher home page. Persisted by [name] in `launcher_prefs`. */
enum class LauncherStyle {
    /** Original layout — CarColors cards, horizontal volume/brightness strip. */
    CLASSIC,

    /** Apple-inspired layout — black canvas, glass cards, vertical sliders (Claude Design v2). */
    AURORA;

    companion object {
        fun fromName(name: String?): LauncherStyle =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
