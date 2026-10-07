package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RefinedNoteParserTest {
    private val raw = "ehm tak jedna dva tři čtyři pět šest"

    @Test fun validJson_isParsed() {
        val r = RefinedNoteParser.parse(
            """{"title":"Servis","text":"Zavolat do servisu.","category":"TASK"}""", raw,
        )
        assertEquals(RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK), r)
    }

    @Test fun unknownCategory_becomesOtherButKeepsText() {
        val r = RefinedNoteParser.parse("""{"title":"A","text":"B","category":"FOOD"}""", raw)
        assertEquals(RefinedNote("A", "B", NoteCategory.OTHER), r)
    }

    @Test fun missingText_usesFallback() {
        val r = RefinedNoteParser.parse("""{"title":"A","category":"TASK"}""", raw)
        assertEquals(RefinedNoteParser.fallback(raw), r)
    }

    @Test fun invalidJsonOrNull_usesFallback() {
        assertEquals(RefinedNoteParser.fallback(raw), RefinedNoteParser.parse("not json", raw))
        assertEquals(RefinedNoteParser.fallback(raw), RefinedNoteParser.parse(null, raw))
    }

    @Test fun fallback_titleIsFirstFiveWordsWithEllipsis() {
        val r = RefinedNoteParser.fallback("  jedna dva  tři čtyři pět šest ")
        assertEquals("jedna dva tři čtyři pět…", r.title)
        assertEquals("jedna dva  tři čtyři pět šest", r.text)
        assertEquals(NoteCategory.OTHER, r.category)
    }

    @Test fun fallback_shortTranscriptHasNoEllipsis() {
        assertEquals("krátká věta", RefinedNoteParser.fallback("krátká věta").title)
    }
}
