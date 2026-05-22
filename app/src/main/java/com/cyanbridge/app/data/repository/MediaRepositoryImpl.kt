package com.cyanbridge.app.data.repository

import android.content.Context
import com.cyanbridge.app.data.db.dao.MediaItemDao
import com.cyanbridge.app.data.db.entity.toDomain
import com.cyanbridge.app.data.db.entity.toEntity
import com.cyanbridge.app.domain.interfaces.MediaRepository
import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.MediaType
import com.cyanbridge.app.domain.model.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val dao: MediaItemDao,
    @ApplicationContext private val context: Context
) : MediaRepository {

    override fun getAll(): Flow<List<MediaItem>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): MediaItem? =
        dao.getById(id)?.toDomain()

    override suspend fun save(item: MediaItem) {
        dao.insert(item.toEntity())
    }

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus) {
        dao.updateSyncStatus(id, syncStatus.name)
    }

    override suspend fun getLocalFiles(type: MediaType): List<File> {
        val dir = when (type) {
            MediaType.PHOTO -> File(context.filesDir, "photos")
            MediaType.AUDIO -> File(context.filesDir, "audio")
            MediaType.VIDEO -> File(context.filesDir, "videos")
        }
        return if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
