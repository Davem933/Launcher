package com.example.carlauncher.data.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getById(id: Long): VoiceNoteEntity?

    @Query("SELECT * FROM voice_notes")
    suspend fun getAll(): List<VoiceNoteEntity>

    @Insert
    suspend fun insert(note: VoiceNoteEntity): Long

    @Update
    suspend fun update(note: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Cílený UPDATE jednoho sloupce — nepřepíše řádek, který zrovna zapisuje NoteProcessor. */
    @Query("UPDATE voice_notes SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)
}
