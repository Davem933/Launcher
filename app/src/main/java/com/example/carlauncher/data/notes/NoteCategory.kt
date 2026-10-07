package com.example.carlauncher.data.notes

/** Pevná sada kategorií hlasových poznámek. `name` je zároveň hodnota, kterou vrací LLM. */
enum class NoteCategory(val emoji: String, val label: String) {
    IDEA("💡", "Nápad"),
    TASK("✅", "Úkol"),
    SHOPPING("🛒", "Nákup"),
    CAR("🚗", "Auto"),
    OTHER("📝", "Ostatní");

    companion object {
        fun fromApi(value: String?): NoteCategory {
            val v = value?.trim() ?: return OTHER
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: OTHER
        }
    }
}
