package com.cyanbridge.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.MediaSource
import com.cyanbridge.app.domain.model.MediaType
import com.cyanbridge.app.domain.model.SyncStatus
import java.time.Instant

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val type: String,
    val localPath: String?,
    val remoteId: String?,
    val syncStatus: String,
    val source: String
)

fun MediaItemEntity.toDomain() = MediaItem(
    id = id,
    createdAt = Instant.ofEpochMilli(createdAt),
    type = MediaType.valueOf(type),
    localPath = localPath,
    remoteId = remoteId,
    syncStatus = SyncStatus.valueOf(syncStatus),
    source = MediaSource.valueOf(source)
)

fun MediaItem.toEntity() = MediaItemEntity(
    id = id,
    createdAt = createdAt.toEpochMilli(),
    type = type.name,
    localPath = localPath,
    remoteId = remoteId,
    syncStatus = syncStatus.name,
    source = source.name
)
