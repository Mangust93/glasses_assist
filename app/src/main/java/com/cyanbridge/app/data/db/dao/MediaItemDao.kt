package com.cyanbridge.app.data.db.dao

import androidx.room.*
import com.cyanbridge.app.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaItemEntity)

    @Query("UPDATE media_items SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()
}
