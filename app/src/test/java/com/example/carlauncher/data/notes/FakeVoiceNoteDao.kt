package com.example.carlauncher.data.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVoiceNoteDao : VoiceNoteDao {
    val rows = linkedMapOf<Long, VoiceNoteEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<VoiceNoteEntity>>(emptyList())

    private fun emit() { flow.value = rows.values.sortedByDescending { it.createdAt } }

    override fun observeAll(): Flow<List<VoiceNoteEntity>> = flow
    override suspend fun getById(id: Long) = rows[id]
    override suspend fun getAll() = rows.values.toList()
    override suspend fun insert(note: VoiceNoteEntity): Long {
        val id = nextId++
        rows[id] = note.copy(id = id); emit(); return id
    }
    override suspend fun update(note: VoiceNoteEntity) {
        if (rows.containsKey(note.id)) { rows[note.id] = note; emit() }
    }
    override suspend fun deleteById(id: Long) { rows.remove(id); emit() }
}
