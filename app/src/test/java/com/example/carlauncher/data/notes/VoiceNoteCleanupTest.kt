package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VoiceNoteCleanupTest {
    @Test fun returnsOnlyUnknownM4aFiles() {
        val known = File("/x/note_1.m4a")
        val orphan = File("/x/note_2.m4a")
        val other = File("/x/readme.txt")

        val result = findOrphanAudio(listOf(known, orphan, other), setOf(known.absolutePath))

        assertEquals(listOf(orphan), result)
    }
}
