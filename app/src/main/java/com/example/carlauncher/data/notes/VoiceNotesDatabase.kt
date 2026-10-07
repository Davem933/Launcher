package com.example.carlauncher.data.notes

import androidx.room.Database
import androidx.room.RoomDatabase

/** Samostatná DB — nesahá na verzi/migrace TripDatabase. */
@Database(entities = [VoiceNoteEntity::class], version = 1, exportSchema = false)
abstract class VoiceNotesDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
}
