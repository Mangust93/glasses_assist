package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.MediaType
import com.cyanbridge.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MediaRepository {
    fun getAll(): Flow<List<MediaItem>>
    suspend fun getById(id: String): MediaItem?
    suspend fun save(item: MediaItem)
    suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus)
    suspend fun getLocalFiles(type: MediaType): List<File>
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
