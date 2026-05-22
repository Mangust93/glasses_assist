package com.cyanbridge.app.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyanbridge.app.domain.interfaces.HermesApiClient
import com.cyanbridge.app.domain.interfaces.NotesRepository
import com.cyanbridge.app.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class NotesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedNote: Note? = null
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val hermesApiClient: HermesApiClient
) : ViewModel() {

    val notes: StateFlow<List<Note>> = notesRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    fun selectNote(note: Note?) {
        _uiState.value = _uiState.value.copy(selectedNote = note)
    }

    fun syncNotesFromHermes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            hermesApiClient.getNotes()
                .onSuccess { remoteNotes ->
                    remoteNotes.forEach { note -> notesRepository.save(note) }
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { e ->
                    Timber.e(e, "syncNotesFromHermes failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Ошибка синхронизации: ${e.message}"
                    )
                }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            notesRepository.delete(noteId)
            if (_uiState.value.selectedNote?.id == noteId) {
                _uiState.value = _uiState.value.copy(selectedNote = null)
            }
        }
    }
}
