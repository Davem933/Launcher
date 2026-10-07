package com.example.carlauncher.data.notes

import org.json.JSONObject

data class RefinedNote(val title: String, val text: String, val category: NoteCategory)

/**
 * Převádí `message.content` z LLM na [RefinedNote]. Cokoliv neúplného → [fallback] ze surového
 * přepisu, protože i neučesaný přepis má pro řidiče větší cenu než chyba.
 */
object RefinedNoteParser {
    private const val FALLBACK_TITLE_WORDS = 5

    fun parse(content: String?, rawTranscript: String): RefinedNote {
        val json = content?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return fallback(rawTranscript)
        val title = json.optString("title").trim()
        val text = json.optString("text").trim()
        if (title.isEmpty() || text.isEmpty()) return fallback(rawTranscript)
        return RefinedNote(title, text, NoteCategory.fromApi(json.optString("category")))
    }

    fun fallback(rawTranscript: String): RefinedNote {
        val clean = rawTranscript.trim()
        val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val title = words.take(FALLBACK_TITLE_WORDS).joinToString(" ") +
            if (words.size > FALLBACK_TITLE_WORDS) "…" else ""
        return RefinedNote(title, clean, NoteCategory.OTHER)
    }
}
