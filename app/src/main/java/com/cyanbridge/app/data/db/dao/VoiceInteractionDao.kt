package com.cyanbridge.app.data.db.dao

import androidx.room.*
import com.cyanbridge.app.data.db.entity.VoiceInteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceInteractionDao {
    @Query("SELECT * FROM voice_interactions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VoiceInteractionEntity>>

    @Query("SELECT * FROM voice_interactions WHERE id = :id")
    suspend fun getById(id: String): VoiceInteractionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VoiceInteractionEntity)

    @Query("UPDATE voice_interactions SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("UPDATE voice_interactions SET audioLocalPath = :path WHERE id = :id")
    suspend fun updateAudioPath(id: String, path: String)

    @Query("DELETE FROM voice_interactions")
    suspend fun deleteAll()
}
