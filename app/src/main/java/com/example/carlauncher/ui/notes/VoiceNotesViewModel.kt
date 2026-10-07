package com.example.carlauncher.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.RecorderState
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.data.notes.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    private val repository: VoiceNoteRepository,
) : ViewModel() {

    val hasApiKey: Boolean = BuildConfig.GROQ_API_KEY.isNotBlank()

    val notes: StateFlow<List<VoiceNoteEntity>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filter = MutableStateFlow<NoteCategory?>(null)
    val filter: StateFlow<NoteCategory?> = _filter.asStateFlow()

    val filteredNotes: StateFlow<List<VoiceNoteEntity>> = combine(notes, _filter) { list, f ->
        if (f == null) list else list.filter { it.category == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recorderState: StateFlow<RecorderState> = repository.recorderState

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    init {
        // Recording → Idle = nahrávání skončilo → krátce ukázat "Uloženo ✓"
        viewModelScope.launch {
            var wasRecording = false
            recorderState.collect { state ->
                if (wasRecording && state is RecorderState.Idle) {
                    _justSaved.value = true
                    delay(JUST_SAVED_MS)
                    _justSaved.value = false
                }
                wasRecording = state is RecorderState.Recording
            }
        }
    }

    /** @return false, když se nahrávání nepodařilo spustit. */
    fun onMicTap(): Boolean {
        if (recorderState.value is RecorderState.Recording) {
            repository.stopRecording()
            return true
        }
        if (!hasApiKey) return false
        return repository.startRecording()
    }

    fun setFilter(category: NoteCategory?) { _filter.value = category }

    fun retry(id: Long) { viewModelScope.launch { repository.retry(id) } }

    fun delete(id: Long) { viewModelScope.launch { repository.delete(id) } }

    fun toggleCompleted(note: VoiceNoteEntity) {
        viewModelScope.launch { repository.setCompleted(note.id, !note.completed) }
    }

    private companion object {
        const val JUST_SAVED_MS = 1_500L
    }
}
