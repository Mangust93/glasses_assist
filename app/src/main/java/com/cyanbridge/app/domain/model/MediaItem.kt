package com.cyanbridge.app.domain.model

import java.time.Instant

enum class MediaType { PHOTO, AUDIO, VIDEO }
enum class SyncStatus { LOCAL_ONLY, UPLOADING, SYNCED, SYNC_ERROR }
enum class MediaSource { PHONE, GLASSES }

data class MediaItem(
    val id: String,
    val createdAt: Instant,
    val type: MediaType,
    val localPath: String?,
    val remoteId: String?,
    val syncStatus: SyncStatus,
    val source: MediaSource
)
