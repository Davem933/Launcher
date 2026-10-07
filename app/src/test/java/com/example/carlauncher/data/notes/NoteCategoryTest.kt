package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCategoryTest {
    @Test fun fromApi_matchesNameCaseInsensitive() {
        assertEquals(NoteCategory.TASK, NoteCategory.fromApi("task"))
        assertEquals(NoteCategory.SHOPPING, NoteCategory.fromApi(" SHOPPING "))
    }

    @Test fun fromApi_unknownOrNull_isOther() {
        assertEquals(NoteCategory.OTHER, NoteCategory.fromApi("nonsense"))
        assertEquals(NoteCategory.OTHER, NoteCategory.fromApi(null))
    }
}
