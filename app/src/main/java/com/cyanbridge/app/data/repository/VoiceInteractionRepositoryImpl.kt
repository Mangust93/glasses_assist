package com.cyanbridge.app.data.repository

import com.cyanbridge.app.data.db.dao.VoiceInteractionDao
import com.cyanbridge.app.data.db.entity.toDomain
import com.cyanbridge.app.data.db.entity.toEntity
import com.cyanbridge.app.domain.interfaces.VoiceInteractionRepository
import com.cyanbridge.app.domain.model.VoiceInteraction
import com.cyanbridge.app.domain.model.VoiceInteractionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceInteractionRepositoryImpl @Inject constructor(
    private val dao: VoiceInteractionDao
) : VoiceInteractionRepository {

    override fun getAll(): Flow<List<VoiceInteraction>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): VoiceInteraction? =
        dao.getById(id)?.toDomain()

    override suspend fun save(interaction: VoiceInteraction) {
        dao.insert(interaction.toEntity())
    }

    override suspend fun updateStatus(id: String, status: VoiceInteractionStatus, error: String?) {
        dao.updateStatus(id, status.name, error)
    }

    override suspend fun updateAudioPath(id: String, path: String) {
        dao.updateAudioPath(id, path)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
