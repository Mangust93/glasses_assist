package com.cyanbridge.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cyanbridge.app.domain.model.VoiceInteraction
import com.cyanbridge.app.domain.model.VoiceInteractionStatus
import java.time.Instant

@Entity(tableName = "voice_interactions")
data class VoiceInteractionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val transcript: String?,
    val answer: String?,
    val audioLocalPath: String?,
    val status: String,
    val error: String?
)

fun VoiceInteractionEntity.toDomain() = VoiceInteraction(
    id = id,
    createdAt = Instant.ofEpochMilli(createdAt),
    transcript = transcript,
    answer = answer,
    audioLocalPath = audioLocalPath,
    status = VoiceInteractionStatus.valueOf(status),
    error = error
)

fun VoiceInteraction.toEntity() = VoiceInteractionEntity(
    id = id,
    createdAt = createdAt.toEpochMilli(),
    transcript = transcript,
    answer = answer,
    audioLocalPath = audioLocalPath,
    status = status.name,
    error = error
)
