package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun getAll(): Flow<List<Note>>
    suspend fun getById(id: String): Note?
    suspend fun save(note: Note)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
