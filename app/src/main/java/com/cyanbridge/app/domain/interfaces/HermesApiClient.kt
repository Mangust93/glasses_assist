package com.cyanbridge.app.domain.interfaces

import com.cyanbridge.app.domain.model.HermesHealth
import com.cyanbridge.app.domain.model.MediaItem
import com.cyanbridge.app.domain.model.Note
import com.cyanbridge.app.domain.model.VoiceResponse
import java.io.File

interface HermesApiClient {
    suspend fun checkHealth(): Result<HermesHealth>

    suspend fun sendVoiceQuery(
        audioFile: File,
        language: String = "ru",
        sessionId: String? = null
    ): Result<VoiceResponse>

    suspend fun getNotes(): Result<List<Note>>
    suspend fun getNoteById(id: String): Result<Note>
    suspend fun createNote(title: String, text: String): Result<Note>

    suspend fun getMedia(): Result<List<MediaItem>>
    suspend fun uploadMedia(file: File, type: String): Result<MediaItem>

    suspend fun downloadAudio(audioUrl: String, destFile: File): Result<File>
}
