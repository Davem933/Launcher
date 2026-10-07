package com.example.carlauncher.data.notes

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Samostatná DB — nesahá na verzi/migrace TripDatabase. */
@Database(entities = [VoiceNoteEntity::class], version = 2, exportSchema = false)
abstract class VoiceNotesDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        /** v2: příznak "splněno" (swipe doleva v seznamu). Existující poznámky zůstanou. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE voice_notes ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
