package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.VoiceInteraction
import com.cyanbridge.app.domain.model.VoiceInteractionStatus
import kotlinx.coroutines.flow.Flow

interface VoiceInteractionRepository {
    fun getAll(): Flow<List<VoiceInteraction>>
    suspend fun getById(id: String): VoiceInteraction?
    suspend fun save(interaction: VoiceInteraction)
    suspend fun updateStatus(id: String, status: VoiceInteractionStatus, error: String? = null)
    suspend fun updateAudioPath(id: String, path: String)
    suspend fun deleteAll()
}
