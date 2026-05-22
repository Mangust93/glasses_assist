package com.cyanbridge.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cyanbridge.app.domain.model.Note
import java.time.Instant

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val text: String,
    val summary: String?,
    val tagsJson: String
)

fun NoteEntity.toDomain() = Note(
    id = id,
    createdAt = Instant.ofEpochMilli(createdAt),
    title = title,
    text = text,
    summary = summary,
    tags = if (tagsJson.isBlank()) emptyList()
    else tagsJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }
)

fun Note.toEntity() = NoteEntity(
    id = id,
    createdAt = createdAt.toEpochMilli(),
    title = title,
    text = text,
    summary = summary,
    tagsJson = tags.joinToString(",")
)
