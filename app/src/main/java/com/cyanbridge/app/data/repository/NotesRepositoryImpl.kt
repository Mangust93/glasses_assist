package com.cyanbridge.app.data.repository

import com.cyanbridge.app.data.db.dao.NoteDao
import com.cyanbridge.app.data.db.entity.toDomain
import com.cyanbridge.app.data.db.entity.toEntity
import com.cyanbridge.app.domain.interfaces.NotesRepository
import com.cyanbridge.app.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val dao: NoteDao
) : NotesRepository {

    override fun getAll(): Flow<List<Note>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Note? =
        dao.getById(id)?.toDomain()

    override suspend fun save(note: Note) {
        dao.insert(note.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
